package dev.kaankaya.instagramtracker

import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Real Keystore, staging and production readers; every response is synthetic and no network opens. */
class WebConnectionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val clock = AtomicLong(1_000_000)
    private val sessions = WebSessions(context, "webtest")
    private val vault = SessionVault(context, "webtestaccount")
    private val prefs = context.getSharedPreferences("web_test_gate", 0)
    private fun gate() = RequestGate(context, "web_test_gate", clock::get)
    private fun state() = WebSessionCodec.fromHeader("sessionid=synthetic-session; ds_user_id=123; csrftoken=csrf", "Synthetic WebView")
    private val profileBody = """{"status":"ok","user":{"pk":"123","username":"synthetic","follower_count":0,"following_count":7}}"""
    @Before fun setup() { check(context.packageName.endsWith(".debug")); cleanup(); clock.set(1_000_000) }
    @After fun cleanup() { sessions.clear(); vault.clear(); prefs.edit().clear().commit() }
    private fun fails(kind: ApiFailure.Kind, operation: () -> Unit) {
        assertEquals(kind, assertThrows(ApiFailure::class.java) { operation() }.kind)
    }
    @Test fun profileAndTwoListDirectionsUseRotatedEncryptedWebSessionWithoutMobileBinding() {
        val reference = sessions.stage(state())
        val transport = Transport(body = profileBody, headers = mapOf("Set-Cookie" to "csrftoken=rotated; Path=/; Secure"))
        val reader = WebReadTransport(gate(), sessions, transport)
        val profile = reader.read(reference, connectionTest = true,
            request = { s, _ -> WebRequests.profile(s, "123") }) { b, t -> ProfileParser.fromBody(b, "123", t) }
        assertEquals(0L, profile.followers); assertEquals(7L, profile.following)
        assertEquals("rotated", WebSessions(context, "webtest").read(reference).cookies["csrftoken"])
        val encrypted = context.noBackupFilesDir.resolve("webtesta.enc").readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(encrypted.contains("synthetic-session")); assertFalse(encrypted.contains("csrftoken"))
        assertNull(reference.mobileBinding); assertNull(vault.read())
        clock.addAndGet(15001)
        transport.body = """{"status":"ok","users":[{"pk":"456","username":"member"}],"next_max_id":"opaque+/="}"""
        val lists = WebFollowListReader(gate(), sessions, transport)
        val first = lists.read(reference, "123", FollowDirection.FOLLOWERS, null)
        assertEquals(FollowCoverage.MORE, first.coverage()); assertEquals("456", first.users.single().id)
        assertEquals("rotated", transport.last!!.header("X-CSRFToken"))
        assertEquals("www.instagram.com", transport.last!!.url.host); assertNull(transport.last!!.header("Authorization"))
        clock.addAndGet(15001)
        transport.body = """{"status":"ok","users":[],"next_max_id":null}"""
        assertEquals(FollowCoverage.END_OBSERVED, lists.read(reference, "123", FollowDirection.FOLLOWING, null).coverage())
        assertEquals(3, transport.calls)
    }
    @Test fun rateLimitPreventsAnotherDirectionOrProtocolAndPreservesSession() {
        val initial = state(); val reference = sessions.stage(initial); val transport = Transport(429, "{}")
        val web = WebFollowListReader(gate(), sessions, transport)
        val mobile = object : FollowListSource {
            override fun cancel() = Unit
            override fun read(session: Session, target: String, direction: FollowDirection, cursor: String?): FollowPage = error("No protocol fallback")
        }
        val selected = AccountFollowListReader(gate(), mobile, web)
        fails(ApiFailure.Kind.LIMITED) { selected.read(reference, "456", FollowDirection.FOLLOWERS, null) }
        fails(ApiFailure.Kind.LIMITED) { selected.read(reference, "456", FollowDirection.FOLLOWING, null) }
        assertEquals(1, transport.calls); assertEquals(1L, gate().attempts())
        assertTrue(gate().blockedUntil() > clock.get()); assertTrue(gate().manualOnly())
        assertEquals(initial, sessions.read(reference))
    }
    @Test fun arbitraryHostAndRedirectCannotReceiveCredentials() {
        val reference = sessions.stage(state()); val transport = Transport(body = profileBody)
        val reader = PrivateReadTransport(gate(), sessions, transport, setOf("www.instagram.com"),
            { s: WebSession, h, t -> s.updated(h, t) })
        fails(ApiFailure.Kind.INVALID_RESPONSE) {
            reader.read(reference, request = { s, _ -> Request.Builder().url("https://foreign.invalid/")
                .header("Cookie", s.cookieHeader()).build() }, parse = { _, _ -> Unit })
        }
        assertEquals(0, transport.calls); assertEquals(0L, gate().attempts())
        transport.status = 302; transport.headers = mapOf("Location" to "https://foreign.invalid/")
        fails(ApiFailure.Kind.SESSION_EXPIRED) {
            WebReadTransport(gate(), sessions, transport).read(reference,
                request = { s, _ -> WebRequests.profile(s, "123") }, parse = { _, _ -> Unit })
        }
        assertEquals(1, transport.calls)
    }
    @Test fun wrongIdentityAndOversizedBodiesCannotRotateTheStoredSession() {
        val initial = state(); val reference = sessions.stage(initial)
        val transport = Transport(body = profileBody.replace("\"123\"", "\"456\""),
            headers = mapOf("Set-Cookie" to "sessionid=must-not-save; Path=/"))
        val reader = WebReadTransport(gate(), sessions, transport)
        fails(ApiFailure.Kind.WRONG_ACCOUNT) { reader.read(reference,
            request = { s, _ -> WebRequests.profile(s, "123") }, parse = { b, t -> ProfileParser.fromBody(b, "123", t) }) }
        assertEquals(initial, sessions.read(reference)); assertTrue(gate().requiresConnection())
        clock.addAndGet(15001); transport.body = "x".repeat(1024 * 1024 + 1)
        fails(ApiFailure.Kind.INVALID_RESPONSE) { reader.read(reference,
            request = { s, _ -> WebRequests.profile(s, "123") }, parse = { _, _ -> Unit }) }
        assertEquals(initial, sessions.read(reference))
    }
    @Test fun completedConnectionKeepsHistoryAndSurvivesNewStoreObjects() {
        val old = state(); val oldReference = sessions.stage(old)
        val person = TrackedPerson(listOf(Profile("456", "friend", 1, 2, 999)))
        vault.write(SavedAccount(oldReference, Profile("123", "synthetic", 0, 7, 999), listOf(person)))
        val transport = Transport(body = profileBody, headers = mapOf("Set-Cookie" to "sessionid=rotated; Path=/"))
        val linked = WebConnectionAttempt(context, gate(), sessions, vault, transport).connect(state(), oldReference)
        assertEquals(listOf(person), linked.people); assertEquals(linked, SessionVault(context, "webtestaccount").read())
        assertEquals("rotated", WebSessions(context, "webtest").read(linked.session).cookies["sessionid"])
        assertEquals(old, sessions.read(oldReference)); assertNull(sessions.pending())
        assertFalse(AccountCodec.encode(linked).contains("sessionid"))
    }
    @Test fun failedReplacementPreservesPreviousAccountAndEncryptedBank() {
        val old = state(); val oldReference = sessions.stage(old)
        val saved = SavedAccount(oldReference, Profile("123", "synthetic", 0, 7, 999)); vault.write(saved)
        fails(ApiFailure.Kind.LIMITED) { WebConnectionAttempt(context, gate(), sessions, vault, Transport(429, "{}"))
            .connect(state(), oldReference) }
        assertEquals(saved, vault.read()); assertEquals(old, sessions.read(oldReference)); assertNull(sessions.pending())
    }
    @Test fun cancellationDuringLateResponseCannotCommitOrRotateCredentials() {
        val old = state(); val oldReference = sessions.stage(old)
        val saved = SavedAccount(oldReference, Profile("123", "synthetic", 0, 7, 999)); vault.write(saved)
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val transport = Transport(body = profileBody, entered = entered, release = release,
            headers = mapOf("Set-Cookie" to "sessionid=must-not-save; Path=/"))
        val attempt = WebConnectionAttempt(context, gate(), sessions, vault, transport)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val future = executor.submit<ApiFailure.Kind?> { try { attempt.connect(state(), oldReference); null } catch (e: ApiFailure) { e.kind } }
            assertTrue(entered.await(5, TimeUnit.SECONDS)); attempt.cancel(); release.countDown()
            assertEquals(ApiFailure.Kind.CANCELLED, future.get(5, TimeUnit.SECONDS))
            assertEquals(saved, vault.read()); assertEquals(old, sessions.read(oldReference)); assertNull(sessions.pending())
        } finally { release.countDown(); executor.shutdownNow() }
    }
    private class Transport(var status: Int = 200, var body: String = "{}", var headers: Map<String, String> = emptyMap(),
        val entered: CountDownLatch? = null, val release: CountDownLatch? = null) : Call.Factory {
        var calls = 0; var last: Request? = null
        override fun newCall(request: Request): Call { calls++; last = request; return object : Call by PrivateReadTransport.client().newCall(request) {
            private var cancelled = false
            override fun request() = request
            override fun execute(): Response {
                entered?.countDown(); check(release?.await(5, TimeUnit.SECONDS) != false)
                return Response.Builder().request(request).protocol(Protocol.HTTP_2).code(status).message("synthetic")
                    .body(body.toResponseBody()).apply { headers.forEach { (k, v) -> addHeader(k, v) } }.build()
            }
            override fun enqueue(responseCallback: Callback) = error("Synchronous fixture only")
            override fun cancel() { cancelled = true }
            override fun isExecuted() = true
            override fun isCanceled() = cancelled
            override fun timeout() = Timeout()
            override fun clone(): Call = error("No clone/retry")
        } }
    }
}
