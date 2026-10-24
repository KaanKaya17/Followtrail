package dev.kaankaya.instagramtracker

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.net.URL
import java.security.cert.Certificate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

/** Exercises the production reader with a fake transport; never contacts Instagram. */
class ApiContractTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences = context.getSharedPreferences("api_test_gate", 0)
    private val session = SessionCookies.fromHeader("ds_user_id=123; sessionid=synthetic; csrftoken=test", "test-UA")!!
    private fun gate() = RequestGate(context, "api_test_gate")
    @Before fun reset() { check(context.packageName.endsWith(".debug")); preferences.edit().clear().commit() }
    @After fun cleanup() { preferences.edit().clear().commit() }
    private fun failure(kind: ApiFailure.Kind, action: () -> Unit) {
        try { action(); fail("Expected $kind") } catch (error: ApiFailure) { assertEquals(kind, error.kind) }
    }
    private class Connection(
        private val status: Int = 200,
        private val body: String = """{"status":"ok","user":{"pk":"123","username":"synthetic","follower_count":0,"following_count":7}}""",
        private val retryAfter: String? = null,
        private val response: (() -> Int)? = null,
        private val stream: InputStream? = null,
    ) : HttpsURLConnection(URL("https://www.instagram.com/api/v1/users/123/info/")) {
        var disconnected = false
        var responseReads = 0
        override fun getResponseCode(): Int { responseReads++; return response?.invoke() ?: status }
        override fun getInputStream(): InputStream = stream ?: ByteArrayInputStream(body.toByteArray())
        override fun getErrorStream(): InputStream = inputStream
        override fun getHeaderField(name: String): String? = if (name == "Retry-After") retryAfter else null
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun connect() = Unit
        override fun getCipherSuite() = "test-only"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate>? = null
    }
    @Test fun ownProfileUsesOneFixedHttpsRequestWithoutRedirects() {
        val connection = Connection()
        var opened: URL? = null
        val profile = InstagramApi(gate()) { url -> opened = url; connection }.read(session)
        assertEquals("https://www.instagram.com/api/v1/users/123/info/", opened.toString())
        assertEquals(0L, profile.followers); assertEquals(7L, profile.following)
        assertEquals("GET", connection.requestMethod)
        assertEquals(session.cookie, connection.getRequestProperty("Cookie"))
        assertEquals("test", connection.getRequestProperty("X-CSRFToken"))
        assertFalse(connection.instanceFollowRedirects)
        assertFalse(connection.useCaches)
        assertEquals(1, connection.responseReads)
        assertTrue(connection.disconnected)
    }
    @Test fun webConnectionReadIsSingleAndCannotResumeOldAccountBeforeUiCommit() {
        val connection = Connection()
        val gate = gate(); gate.pauseAutomatic()
        val api = InstagramApi(gate) { connection }
        api.testWebConnection(session)
        assertTrue(gate.manualOnly())
        assertEquals(1L, gate.attempts()); assertEquals(200, gate.lastHttpStatus())
        failure(ApiFailure.Kind.TOO_SOON) { InstagramApi(gate()) { connection }.testWebConnection(session) }
        assertEquals(1, connection.responseReads)
        assertTrue(gate.nextPrototypeAt() >= System.currentTimeMillis() + 890000)
    }
    @Test fun selectedPersonLookupIsSingleRequestAndRefreshPinsStableIdentity() {
        val web = Connection(body = """{"status":"ok","data":{"user":{"id":"456","username":"deniz","edge_followed_by":{"count":12},"edge_follow":{"count":7}}}}""")
        var opened: URL? = null
        val profile = InstagramApi(gate()) { url -> opened = url; web }.readUsername(session, "deniz")
        assertEquals("https://www.instagram.com/api/v1/users/web_profile_info/?username=deniz", opened.toString())
        assertEquals("456", profile.userId); assertNull(profile.posts); assertEquals(1, web.responseReads)
        preferences.edit().clear().commit()
        val renamed = Connection(body = """{"status":"ok","user":{"pk":"456","username":"renamed","follower_count":14,"following_count":7,"media_count":0}}""")
        val refreshed = InstagramApi(gate()) { url -> opened = url; renamed }.readId(session, profile.userId)
        assertEquals("https://www.instagram.com/api/v1/users/456/info/", opened.toString())
        assertEquals("renamed", refreshed.username); assertEquals(0L, refreshed.posts)
    }
    @Test fun missingTargetDoesNotRetryAndMismatchCannotReplaceAnotherPerson() {
        val connection = Connection(404)
        failure(ApiFailure.Kind.NOT_FOUND) { InstagramApi(gate()) { connection }.readUsername(session, "absent") }
        assertEquals(1, connection.responseReads); assertTrue(connection.disconnected)
        preferences.edit().clear().commit()
        failure(ApiFailure.Kind.WRONG_ACCOUNT) { InstagramApi(gate()) { Connection() }.readId(session, "456") }
        preferences.edit().clear().commit()
        failure(ApiFailure.Kind.INVALID_RESPONSE) { InstagramApi(gate()) { Connection(body = """{"status":"ok","data":{"user":null}}""") }.readUsername(session, "absent") }
    }
    @Test fun server429PersistsWaitAndNextReaderNeverOpensConnection() {
        val connection = Connection(429, retryAfter = "120")
        val now = System.currentTimeMillis()
        failure(ApiFailure.Kind.LIMITED) { InstagramApi(gate()) { connection }.read(session) }
        assertTrue(gate().blockedUntil() >= now + 120000)
        failure(ApiFailure.Kind.LIMITED) {
            InstagramApi(gate()) { throw AssertionError("No connection allowed during pause") }.read(session)
        }
        assertTrue(connection.disconnected)
    }
    @Test fun authenticationAndRedirectFailuresNeverFollowOrRelogin() {
        for ((status, body, expected) in listOf(
            Triple(302, "", ApiFailure.Kind.SESSION_EXPIRED), Triple(401, "", ApiFailure.Kind.SESSION_EXPIRED),
            Triple(403, "", ApiFailure.Kind.VERIFICATION),
            Triple(400, """{"message":"challenge_required"}""", ApiFailure.Kind.VERIFICATION),
            Triple(400, """{"message":"login_required"}""", ApiFailure.Kind.SESSION_EXPIRED),
            Triple(500, "", ApiFailure.Kind.CONNECTION),
        )) {
            preferences.edit().clear().commit()
            val connection = Connection(status, body)
            failure(expected) { InstagramApi(gate()) { connection }.read(session) }
            assertEquals(1, connection.responseReads); assertTrue(connection.disconnected)
        }
    }
    @Test fun malformedAndOversizedBodiesFailWithoutKeepingConnection() {
        for (connection in listOf(Connection(body = "<html>not a profile</html>"),
            Connection(stream = ByteArrayInputStream(ByteArray(1024 * 1024 + 1) { 32 })))) {
            preferences.edit().clear().commit()
            failure(ApiFailure.Kind.INVALID_RESPONSE) { InstagramApi(gate()) { connection }.read(session) }
            assertTrue(connection.disconnected)
        }
    }
    @Test fun setupAndReadTimeoutsBecomeTypedFailures() {
        failure(ApiFailure.Kind.CONNECTION) { InstagramApi(gate()) { throw IOException("synthetic setup failure") }.read(session) }
        preferences.edit().clear().commit()
        val connection = Connection(response = { throw SocketTimeoutException("synthetic timeout") })
        failure(ApiFailure.Kind.CONNECTION) { InstagramApi(gate()) { connection }.read(session) }
        assertTrue(connection.disconnected)
    }
    @Test fun cancellationDuringConnectionCreationDisconnectsBeforeRequest() {
        val entering = CountDownLatch(1); val resume = CountDownLatch(1)
        val connection = Connection()
        val api = InstagramApi(gate()) { entering.countDown(); check(resume.await(5, TimeUnit.SECONDS)); connection }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val work = executor.submit<ApiFailure.Kind> {
                try { api.read(session); error("Cancelled request succeeded") } catch (error: ApiFailure) { error.kind }
            }
            assertTrue(entering.await(5, TimeUnit.SECONDS))
            api.cancel(); resume.countDown()
            assertEquals(ApiFailure.Kind.CANCELLED, work.get(5, TimeUnit.SECONDS))
            assertTrue(connection.disconnected); assertEquals(0, connection.responseReads)
        } finally { resume.countDown(); executor.shutdownNow() }
    }
    @Test fun cancellationDuringResponseDiscardsLateSuccess() {
        val entering = CountDownLatch(1); val resume = CountDownLatch(1)
        val connection = Connection(response = { entering.countDown(); check(resume.await(5, TimeUnit.SECONDS)); 200 })
        val api = InstagramApi(gate()) { connection }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val work = executor.submit<ApiFailure.Kind> {
                try { api.read(session); error("Late success must not escape") } catch (error: ApiFailure) { error.kind }
            }
            assertTrue(entering.await(5, TimeUnit.SECONDS))
            api.cancel(); resume.countDown()
            assertEquals(ApiFailure.Kind.CANCELLED, work.get(5, TimeUnit.SECONDS))
            assertTrue(connection.disconnected)
        } finally { resume.countDown(); executor.shutdownNow() }
    }
    @Test fun concurrentActivityInstancesCanReserveOnlyOneRead() {
        val executor = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        try {
            val work = (1..2).map {
                executor.submit<String> {
                    check(start.await(5, TimeUnit.SECONDS))
                    try { gate().begin(); "accepted" } catch (error: ApiFailure) { error.kind.name }
                }
            }
            start.countDown()
            assertEquals(listOf("TOO_SOON", "accepted"), work.map { it.get(5, TimeUnit.SECONDS) }.sorted())
        } finally { executor.shutdownNow() }
    }
}
