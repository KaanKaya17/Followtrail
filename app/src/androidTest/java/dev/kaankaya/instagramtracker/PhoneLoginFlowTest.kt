package dev.kaankaya.instagramtracker

import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.Call
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.ByteString.Companion.toByteString
import okio.ByteString.Companion.decodeBase64
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.security.KeyPairGenerator
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Production login/crypto/gate/Keystore, synthetic transport only. No Instagram account requests. */
class PhoneLoginFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val vault = SessionVault(context)
    private val sessions = MobileSessions(context)
    private var now = 2_000_000_000_000L
    private val runtime = object : ReadRuntime {
        override fun now() = now
        override fun elapsed() = now
        override fun pause(milliseconds: Long, cancelled: () -> Boolean): Boolean { now += milliseconds; return !cancelled() }
    }
    private val gate = RequestGate(context) { now }
    private val controllers = mutableListOf<PhoneLoginController>()
    private val clients = mutableListOf<PhoneLoginClient>()
    private val transports = mutableListOf<Transport>()
    private val old = SavedAccount(Session("123", "ds_user_id=123; sessionid=synthetic-old", "test-UA"), Profile("123", "own", 5, 3, 1000),
        listOf(TrackedPerson(listOf(Profile("456", "friend", 9, 2, 1000)), "keep note", "keep group", favorite = true)))
    @Before fun before() {
        check(context.packageName.endsWith(".debug")); clean(); now = 2_000_000_000_000L; vault.write(old)
    }
    @After fun after() {
        instrumentation.runOnMainSync { controllers.forEach { it.close() } }
        clients.forEach { it.cancel() }; transports.forEach { it.close() }; clean()
    }
    private fun clean() {
        sessions.clear(); vault.clear(); PhoneIdentity.clear(context); PhoneLoginJournal(context).clear()
        context.getSharedPreferences("request_gate", 0).edit().clear().commit()
    }
    private fun program(app: String, token: String) = JSONObject().put("layout", JSONObject().put("bloks_payload", JSONObject()
        .put("action", "(navigate ${JSONObject.quote(app)} (f4i (dkc \"context_data\") (dkc ${JSONObject.quote(token)})))")))
    private fun loginEnvelope(mismatched: Boolean = false): JSONObject {
        val token = "Bearer IGT:2:" + JSONObject().put("ds_user_id", if (mismatched) "999" else "123").put("sessionid", "synthetic-new").toString().toByteArray().toByteString().base64()
        val envelope = JSONObject().put("login_response", JSONObject().put("logged_in_user", JSONObject().put("pk", "123")).toString())
            .put("headers", JSONObject().put("IG-Set-Authorization", token).put("X-IG-Set-WWW-Claim", "server-claim").toString()).put("cookies", "")
        return JSONObject().put("layout", JSONObject().put("bloks_payload", JSONObject().put("action", "(handle ${JSONObject.quote(envelope.toString())})")))
    }
    private inner class Transport(var mode: String = "success", val waitAtProfile: (() -> Unit)? = null) : Call.Factory {
        private val delegate = MobileReadTransport.client()
        private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val requests = mutableListOf<Request>(); val times = mutableListOf<Long>()
        override fun newCall(request: Request): Call = object : Call by delegate.newCall(request) {
            override fun execute(): Response {
                requests += request; times += now
                var status = 200; val headers = mutableMapOf<String, String>()
                val path = request.url.encodedPath
                val body = when {
                    mode == "limited" -> { status = 429; headers["Retry-After"] = "7200"; "{}" }
                    mode == "unsupported" -> "{}"
                    path == "/graphql_www" -> """{"data":{"usdid_registration":{"success":true}}}"""
                    path.endsWith("process_client_data_and_redirect/") -> JSONObject().put("layout", JSONObject().put("bloks_payload", JSONObject().put("data",
                        org.json.JSONArray().put(JSONObject().put("data", JSONObject().put("key", "CAA_ACCOUNT_ACCESS_CONTEXT:aac").put("initial", "server-aac")))))).toString()
                    path.endsWith("attestation/create_android_keystore/") -> """{"challenge_nonce":"server-nonce"}"""
                    path.endsWith("oauth.token.fetch.async/") -> {
                        if (mode == "key-isolated") {
                            headers["Set-Cookie"] = "csrftoken=private-csrf; Path=/; Secure"
                            headers["ig-set-x-mid"] = "private-mid"
                            headers["x-ig-set-www-claim"] = "private-claim"
                        }
                        "{}"
                    }
                    path.endsWith("qe/sync/") -> {
                        headers["ig-set-password-encryption-key-id"] = "7"
                        headers["ig-set-password-encryption-pub-key"] = keys.public.encoded.toByteString().base64()
                        if (mode == "key-isolated") {
                            headers["Set-Cookie"] = "csrftoken=public-csrf; Path=/; Secure"
                            headers["ig-set-x-mid"] = "public-mid"
                            headers["x-ig-set-www-claim"] = "public-claim"
                        }
                        status = 405
                        when (mode) {
                            "key-ok" -> { status = 200; "" }
                            "key-limited" -> { status = 429; headers["Retry-After"] = "7200"; "<html>Too many requests</html>" }
                            "key-forbidden" -> { status = 403; "<html>Forbidden</html>" }
                            "key-missing" -> { headers.remove("ig-set-password-encryption-pub-key"); "<html>Method Not Allowed</html>" }
                            "key-checkpoint" -> """{"message":"challenge_required"}"""
                            else -> "<html>Method Not Allowed</html>" // Upstream uses key headers without requiring a successful status/body.
                        }
                    }
                    path.endsWith("send_login_request/") -> when (mode) {
                        "password-method" -> {
                            status = 405
                            headers["ig-set-password-encryption-key-id"] = "7"
                            headers["ig-set-password-encryption-pub-key"] = keys.public.encoded.toByteString().base64()
                            "<html>Method Not Allowed</html>"
                        }
                        "ap" -> program(PhoneBloks.AP_ENTRY, "entry-context").toString()
                        "two" -> JSONObject().put("two_factor_required", true).put("two_factor_info", JSONObject().put("two_step_verification_context", "two-context")).toString()
                        "mismatch" -> loginEnvelope(true).toString()
                        else -> loginEnvelope().toString()
                    }
                    path.endsWith(PhoneBloks.AP_ENTRY + "/") -> program(PhoneBloks.AP_CODE, "code-context").toString()
                    path.endsWith(PhoneBloks.AP_CODE + "/") -> program(PhoneBloks.AP_SUBMIT, "submit-context").toString()
                    path.endsWith(PhoneBloks.AP_SUBMIT + "/") || path.endsWith("two_step_verification.verify_code.async/") -> loginEnvelope().toString()
                    path.contains("two_step_verification") -> "{}"
                    path == "/api/v1/users/123/info/" -> {
                        waitAtProfile?.invoke()
                        if (mode == "bad-profile") """{"status":"ok","user":{"pk":"123","username":"own","following_count":3}}"""
                        else """{"status":"ok","user":{"pk":"123","username":"own","follower_count":5,"following_count":3}}"""
                    }
                    path.contains("/friendships/") -> """{"status":"ok","users":[],"next_max_id":null}"""
                    else -> error("Unexpected fixture route: $path")
                }
                return Response.Builder().request(request).code(status).message("synthetic").protocol(Protocol.HTTP_2)
                    .body(body.toResponseBody()).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
            }
            public override fun clone(): Call = error("No replay")
        }
        fun close() { delegate.connectionPool.evictAll(); delegate.dispatcher.executorService.shutdownNow() }
    }
    private fun transport(mode: String = "success", wait: (() -> Unit)? = null) = Transport(mode, wait).also { transports += it }
    private fun client(t: Transport) = PhoneLoginClient(context, gate, t, runtime).also { clients += it }
    private fun await(predicate: () -> Boolean) {
        val end = android.os.SystemClock.elapsedRealtime() + 10_000
        while (!predicate() && android.os.SystemClock.elapsedRealtime() < end) Thread.sleep(25)
        assertTrue(predicate())
    }
    @Test fun passwordLoginProducesTheExistingMobileContractOnlyAfterARealProfileRead() {
        val t = transport(); val password = "synthetic-password".toCharArray(); val start = now
        val ready = client(t).login("own", password) as PhoneLoginResult.Ready
        assertTrue(password.all { it == '\u0000' }); assertEquals("123", ready.session.owner); assertEquals(5L, ready.profile.followers)
        MobileSessionCodec.decode(MobileSessionCodec.encode(ready.session))
        assertEquals(7, t.requests.size); assertTrue(t.times.zipWithNext().all { (a, b) -> b - a >= 15_000 })
        assertEquals("b.i.instagram.com", t.requests.first().url.host)
        assertEquals(start + MonitorPolicy.POLL_MS, gate.nextPrototypeAt())
        assertEquals(old, vault.read()); assertFalse(sessions.exists())
        assertEquals("server-claim", ready.session.headers["X-IG-WWW-Claim"])
        val bodies = t.requests.mapNotNull { it.body }.joinToString { body -> okio.Buffer().also(body::writeTo).readUtf8() }
        assertFalse(bodies.contains("synthetic-password")); assertTrue(bodies.contains("%23PWD_INSTAGRAM"))
        val reference = sessions.stage(ready.session)
        vault.linkIfUnchanged(old.session, reference, ready.profile) { false }
        now += 15_000
        MobileFollowListReader(gate, sessions, t).read(reference, "123", FollowDirection.FOLLOWERS, null)
        now += 15_000
        MobileFollowListReader(gate, sessions, t).read(reference, "123", FollowDirection.FOLLOWING, null)
        assertEquals(2, t.requests.count { it.url.encodedPath.contains("/friendships/") })
        assertEquals(old.people, vault.read()!!.people)
        assertEquals(ready.session.owner, MobileSessions(context).read(reference).owner)
        val identity = JSONObject(EncryptedRecord(context, "phoneidentity").read()!!)
        assertFalse(identity.toString().contains("synthetic-password")); assertTrue(identity.getBoolean("registered"))
    }
    @Test fun profileCodeAndAuthenticatorContinueWithoutSubmittingThePasswordAgain() {
        for (mode in listOf("ap", "two")) {
            clean(); vault.write(old); now = 2_000_000_000_000L
            val t = transport(mode); val flow = client(t)
            val initial = flow.login("own", "synthetic-password".toCharArray())
            if (mode == "ap") assertSame(PhoneLoginResult.Code, initial)
            else { assertSame(PhoneLoginResult.Method, initial); assertSame(PhoneLoginResult.Code, flow.chooseMethod("totp")) }
            val count = t.requests.size
            assertEquals(PhoneLoginFailure.Reason.CODE, assertThrows(PhoneLoginFailure::class.java) { flow.submitCode("") }.reason)
            assertEquals(count, t.requests.size)
            now += 30_000
            val ready = flow.submitCode("123456") as PhoneLoginResult.Ready
            assertEquals("123", ready.session.owner)
            assertEquals(1, t.requests.count { it.url.encodedPath.endsWith("send_login_request/") })
            assertEquals(old, vault.read()); assertFalse(sessions.exists())
        }
    }
    @Test fun passwordKeyUsesAnAnonymousPublicRequestAndCannotRotateThePrivateLoginContext() {
        val t = transport("key-isolated")
        val ready = client(t).login("own", "synthetic-password".toCharArray()) as PhoneLoginResult.Ready
        val key = t.requests.single { it.url.encodedPath == "/api/v1/qe/sync/" }
        assertEquals("https://i.instagram.com/api/v1/qe/sync/", key.url.toString())
        assertEquals("GET", key.method); assertNull(key.body)
        assertNull(key.header("Cookie")); assertNull(key.header("Authorization")); assertNull(key.header("X-Meta-Usdid"))
        assertTrue(key.headers.names().none { it.startsWith("X-IG-", true) || it.startsWith("X-Pigeon-", true) })
        assertEquals("*/*", key.header("Accept")); assertEquals("en-US", key.header("Accept-Language"))
        assertEquals("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_13_6) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/11.1.2 Safari/605.1.15", key.header("User-Agent"))
        val password = t.requests.single { it.url.encodedPath.endsWith("send_login_request/") }
        assertTrue(password.header("User-Agent")!!.startsWith("Instagram "))
        assertEquals("csrftoken=private-csrf", password.header("Cookie"))
        assertEquals("private-mid", password.header("X-MID")); assertEquals("private-claim", password.header("X-IG-WWW-Claim"))
        assertEquals("private-csrf", ready.session.cookies["csrftoken"])
        assertEquals(7, t.requests.size); assertTrue(t.times.zipWithNext().all { (a, b) -> b - a >= 15_000 })
        assertEquals(old, vault.read()); assertFalse(sessions.exists())
    }
    @Test fun restrictionAndUnsupportedResponseStopWithoutAlternateRoutesOrAccountChanges() {
        for (mode in listOf("limited", "unsupported")) {
            clean(); vault.write(old); now = 2_000_000_000_000L
            val t = transport(mode)
            val failure = try { client(t).login("own", "synthetic-password".toCharArray()); error("Expected failure") } catch (e: Exception) { e }
            if (mode == "limited") {
                assertEquals(ApiFailure.Kind.LIMITED, (failure as ApiFailure).kind)
                val blocked = gate.blockedUntil(); assertEquals(now + 7200_000, blocked)
                val diagnostic = PhoneLoginJournal(context).read()!!
                assertEquals(PhoneLoginOperation.DEVICE, diagnostic.operation); assertEquals(429, diagnostic.status)
                assertEquals(ApiFailure.Kind.LIMITED, assertThrows(ApiFailure::class.java) {
                    client(t).login("own", "synthetic-password".toCharArray()) }.kind)
                assertEquals(blocked, gate.blockedUntil())
                assertEquals(diagnostic, PhoneLoginJournal(context).read())
            } else assertEquals(PhoneLoginFailure.Reason.UNSUPPORTED, (failure as PhoneLoginFailure).reason)
            assertEquals(1, t.requests.size); assertEquals(old, vault.read()); assertFalse(sessions.exists())
        }
    }
    @Test fun malformedProfileAndMismatchedAuthorizationNeverBecomeALinkedAccount() {
        for (mode in listOf("bad-profile", "mismatch")) {
            clean(); vault.write(old); now = 2_000_000_000_000L
            val t = transport(mode)
            val failure = assertThrows(ApiFailure::class.java) { client(t).login("own", "synthetic-password".toCharArray()) }
            assertEquals(if (mode == "mismatch") ApiFailure.Kind.WRONG_ACCOUNT else ApiFailure.Kind.INVALID_RESPONSE, failure.kind)
            assertEquals(old, vault.read()); assertFalse(sessions.exists())
        }
    }
    @Test fun cancellationAfterLateProfileAndExpiredCodeDoNotCommitOrClearANewRestriction() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val t = transport(wait = { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) })
        val flow = client(t); val result = AtomicReference<Exception>()
        val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val pending = worker.submit { try { flow.login("own", "synthetic-password".toCharArray()) } catch (e: Exception) { result.set(e) } }
            assertTrue(entered.await(5, TimeUnit.SECONDS)); flow.cancel(); gate.limited(null); val until = gate.blockedUntil(); release.countDown()
            pending.get(5, TimeUnit.SECONDS)
            assertEquals(ApiFailure.Kind.CANCELLED, (result.get() as ApiFailure).kind)
            assertEquals(until, gate.blockedUntil()); assertEquals(old, vault.read()); assertFalse(sessions.exists())
        } finally { release.countDown(); worker.shutdownNow() }
        clean(); vault.write(old); now = 2_000_000_000_000L
        val next = transport("ap"); val waiting = client(next); waiting.login("own", "synthetic-password".toCharArray())
        val count = next.requests.size; now += 10 * 60_000
        assertEquals(PhoneLoginFailure.Reason.EXPIRED, assertThrows(PhoneLoginFailure::class.java) { waiting.submitCode("123456") }.reason)
        assertEquals(count, next.requests.size); assertEquals(old, vault.read())
    }
    @Test fun retainedControllerCommitsOnceAndKeepsSameOwnerHistoryWithoutSavingCredentials() {
        val t = transport()
        val controller = PhoneLoginController(context) { c, _, progress ->
            PhoneLoginClient(c, gate, t, runtime, progress = progress).also { clients += it }
        }.also { controllers += it }
        val secret = "synthetic-password".toCharArray()
        instrumentation.runOnMainSync { controller.start("own", secret) }
        await { controller.ui.phase == PhoneLoginPhase.LINKED }
        val saved = vault.read()!!
        assertNotEquals(old.session, saved.session); assertEquals(old.people, saved.people)
        assertNotNull(saved.session.mobileBinding); assertEquals(7, t.requests.size)
        assertTrue(secret.all { it == '\u0000' }); assertNull(sessions.pending())
        assertFalse(controller.ui.toString().contains("synthetic-password")); assertFalse(gate.manualOnly())
        assertEquals(saved.session, sessions.read(saved.session).reference())
    }
    @Test fun installationIdentityIsReusedAndSignedWithThePersistedPublicKey() {
        val first = PhoneIdentity.load(context); val body = JSONObject(first.registrationToken(now).decodeBase64()!!.utf8())
        val payload = JSONObject(body.getString("payload").decodeBase64()!!.utf8())
        val signature = body.getJSONArray("signatures").getJSONObject(0)
        val public = java.security.KeyFactory.getInstance("EC").generatePublic(java.security.spec.X509EncodedKeySpec(payload.getString("pub").decodeBase64()!!.toByteArray()))
        assertTrue(java.security.Signature.getInstance("SHA256withECDSA").run {
            initVerify(public); update((signature.getString("protected") + "." + body.getString("payload")).toByteArray())
            verify(signature.getString("signature").decodeBase64()!!.toByteArray())
        })
        first.registered = true; first.save(context)
        val again = PhoneIdentity.load(context)
        assertEquals(first.uuid, again.uuid); assertEquals(first.usdid, again.usdid); assertTrue(again.registered)
        val header = again.signedDevice(now).split('.')
        assertTrue(java.security.Signature.getInstance("SHA256withECDSA").run {
            initVerify(public); update("${header[0]}.${header[1]}".toByteArray()); verify(header[2].decodeBase64()!!.toByteArray())
        })
    }
    @Test fun passwordKeyHeadersDoNotExcuseRestrictionsMissingKeysOrFailedPasswordRequests() {
        for ((mode, expected) in listOf("key-limited" to ApiFailure.Kind.LIMITED, "key-forbidden" to ApiFailure.Kind.VERIFICATION,
            "key-missing" to ApiFailure.Kind.CONNECTION, "key-checkpoint" to ApiFailure.Kind.VERIFICATION,
            "password-method" to ApiFailure.Kind.CONNECTION)) {
            clean(); vault.write(old); now = 2_000_000_000_000L
            val t = transport(mode); val flow = client(t)
            assertEquals(expected, assertThrows(ApiFailure::class.java) { flow.login("own", "synthetic-password".toCharArray()) }.kind)
            val passwordAttempted = mode == "password-method"
            assertEquals(if (passwordAttempted) 6 else 5, t.requests.size)
            assertEquals(if (passwordAttempted) PhoneLoginOperation.PASSWORD else PhoneLoginOperation.PASSWORD_KEY, flow.diagnostic!!.operation)
            assertEquals(if (mode == "key-limited") 429 else if (mode == "key-forbidden") 403 else 405, flow.diagnostic!!.status)
            val stored = PhoneLoginJournal(context).read()!!
            assertEquals(flow.diagnostic!!.operation, stored.operation); assertEquals(flow.diagnostic!!.status, stored.status)
            assertEquals(old, vault.read()); assertFalse(sessions.exists())
            if (mode == "key-limited") {
                val until = gate.blockedUntil()
                assertEquals(now + 7200_000, until)
                assertTrue(t.requests.none { it.url.encodedPath.endsWith("send_login_request/") })
                assertEquals(ApiFailure.Kind.LIMITED, assertThrows(ApiFailure::class.java) {
                    client(t).login("own", "synthetic-password".toCharArray()) }.kind)
                assertEquals(5, t.requests.size); assertEquals(until, gate.blockedUntil())
                assertEquals(stored, PhoneLoginJournal(context).read())
            }
        }
        clean(); vault.write(old); now = 2_000_000_000_000L
        assertTrue(client(transport("key-ok")).login("own", "synthetic-password".toCharArray()) is PhoneLoginResult.Ready)
    }
    @Test fun aLocalInputFailureKeepsThePreviousDiagnosticAndClearingItKeepsTheRestriction() {
        val journal = PhoneLoginJournal(context)
        val before = SavedPhoneDiagnostic(PhoneLoginOperation.PASSWORD, now, 429)
        journal.write(before); gate.limited("7200"); val until = gate.blockedUntil()
        val t = transport()
        assertEquals(PhoneLoginFailure.Reason.INPUT, assertThrows(PhoneLoginFailure::class.java) {
            client(t).login("", charArrayOf()) }.reason)
        assertTrue(t.requests.isEmpty()); assertEquals(before, PhoneLoginJournal(context).read())
        assertEquals(setOf("operation", "at", "status"), context.getSharedPreferences("phone_login_diagnostic", 0).all.keys)
        journal.write(before.copy(status = null)); assertNull(PhoneLoginJournal(context).read()!!.status)
        journal.clear(); assertNull(PhoneLoginJournal(context).read()); assertEquals(until, gate.blockedUntil())
    }
    @Test fun lateLoginCannotReplaceAConnectionChangedDuringAuthentication() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val t = transport(wait = { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) })
        val controller = PhoneLoginController(context) { c, _, progress ->
            PhoneLoginClient(c, gate, t, runtime, progress = progress).also { clients += it }
        }.also { controllers += it }
        try {
            instrumentation.runOnMainSync { controller.start("own", "synthetic-password".toCharArray()) }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val other = SavedAccount(Session("999", "ds_user_id=999; sessionid=other", "test-UA"), Profile("999", "other", 1, 1, 1000))
            vault.write(other); release.countDown()
            await { controller.ui.phase == PhoneLoginPhase.ERROR }
            assertEquals(ApiFailure.Kind.CANCELLED, controller.ui.failure)
            assertEquals(PhoneLoginOperation.PROFILE, controller.ui.diagnostic!!.operation)
            assertEquals(200, controller.ui.diagnostic!!.status)
            assertEquals(other, vault.read()); assertNull(sessions.pending()); assertFalse(sessions.exists())
        } finally { release.countDown() }
    }
}
