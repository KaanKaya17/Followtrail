package dev.kaankaya.instagramtracker

import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import okio.ByteString.Companion.decodeBase64
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Real Keystore/vault/reader boundaries; synthetic responses, no Instagram connections. */
class MobileConnectionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences = context.getSharedPreferences("mobile_test_gate", 0)
    private val sessions = MobileSessions(context, "mobiletest")
    private val vault = SessionVault(context, "mobiletestaccount")
    private val link = DesktopLink(context, "test")
    private fun gate() = RequestGate(context, "mobile_test_gate")
    private fun state(binding: String = "a".repeat(32)): MobileSession {
        val auth = "Bearer IGT:2:" + """{"ds_user_id":"123","sessionid":"synthetic"}""".encodeUtf8().base64()
        return MobileSession(binding, "123", mapOf("Authorization" to auth, "User-Agent" to "Instagram synthetic",
            "X-IG-Device-ID" to "test-device", "X-IG-Family-Device-ID" to "test-family", "X-IG-Android-ID" to "android-test",
            "X-IG-App-ID" to "567067343352427", "X-Bloks-Version-Id" to "test-hash", "IG-INTENDED-USER-ID" to "123",
            "IG-U-DS-USER-ID" to "123"), mapOf("sessionid" to "synthetic", "ds_user_id" to "123"))
    }
    @Before fun setup() { check(context.packageName.endsWith(".debug")); cleanup() }
    @After fun cleanup() { sessions.clear(); vault.clear(); link.clear(); preferences.edit().clear().commit() }
    private fun failure(kind: ApiFailure.Kind, operation: () -> Unit) {
        assertEquals(kind, assertThrows(ApiFailure::class.java) { operation() }.kind)
    }
    private class Transport(private val code: Int = 200,
        private val body: String = """{"status":"ok","user":{"pk":"123","username":"synthetic","follower_count":0,"following_count":7}}""",
        private val wait: (() -> Unit)? = null,
        private val headers: Map<String, String> = emptyMap()) : Call.Factory {
        var calls = 0
        var last: Request? = null
        var cancelled = false
        override fun newCall(request: Request): Call {
            last = request
            return object : Call by MobileProfileReader.client().newCall(request) {
                override fun request() = request
                override fun execute(): Response {
                    calls++; wait?.invoke()
                    val result = Response.Builder().request(request).protocol(Protocol.HTTP_2).code(code).message("fixture").body(body.toResponseBody())
                    headers.forEach { (name, value) -> result.header(name, value) }
                    return result.build()
                }
                override fun enqueue(responseCallback: Callback) = error("Async transport unused")
                override fun cancel() { cancelled = true }
                override fun isExecuted() = calls > 0
                override fun isCanceled() = cancelled
                override fun timeout() = Timeout.NONE
                public override fun clone(): Call = error("Request replay forbidden")
            }
        }
    }
    @Test fun successfulReadUsesMobileIdentityAndPersistsRotationBeforeReturningCounts() {
        val reference = sessions.stage(state())
        val transport = Transport(headers = mapOf("IG-Set-X-MID" to "rotated"))
        val profile = MobileProfileReader(gate(), sessions, transport).read(reference, "123", null)
        assertEquals(0L, profile.followers); assertEquals(7L, profile.following)
        assertEquals(1, transport.calls); assertEquals("i.instagram.com", transport.last!!.url.host)
        assertEquals("rotated", MobileSessions(context, "mobiletest").read(reference).headers["X-MID"])
        assertNull(vault.read()) // Reader cannot attach an account to the UI by itself.
        val encrypted = context.noBackupFilesDir.resolve("mobiletesta.enc").readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(encrypted.contains("Bearer IGT")); assertFalse(encrypted.contains("synthetic"))
    }
    @Test fun rateLimitStopsAfterOneRequestAndPersistsAcrossReaderRecreation() {
        val reference = sessions.stage(state()); val transport = Transport(429, headers = mapOf("Retry-After" to "7200"))
        failure(ApiFailure.Kind.LIMITED) { MobileProfileReader(gate(), sessions, transport).read(reference, "123", null) }
        assertEquals(1, transport.calls)
        assertTrue(gate().blockedUntil() >= System.currentTimeMillis() + 7100000)
        failure(ApiFailure.Kind.LIMITED) { MobileProfileReader(gate(), sessions, transport).read(reference, "123", null) }
        assertEquals(1, transport.calls); assertTrue(gate().manualOnly())
    }
    @Test fun malformedMissingAndWrongProfileResponsesNeverUpdateStoredState() {
        val reference = sessions.stage(state())
        val bodies = listOf("""{"status":"ok","user":{"pk":"456","username":"other","follower_count":1,"following_count":2}}""",
            """{"status":"ok","user":{"pk":"123","username":"synthetic","following_count":2}}""", "x".repeat(1024 * 1024 + 1))
        for (body in bodies) {
            preferences.edit().clear().commit()
            val transport = Transport(body = body, headers = mapOf("IG-Set-X-MID" to "must-not-save"))
            assertThrows(ApiFailure::class.java) { MobileProfileReader(gate(), sessions, transport).read(reference, "123", null) }
            assertNull(sessions.read(reference).headers["X-MID"]); assertEquals(1, transport.calls)
        }
    }
    @Test fun rejectedSessionPausesWithoutFollowingRedirectOrLoggingIn() {
        val reference = sessions.stage(state())
        val transport = Transport(302, headers = mapOf("Location" to "https://elsewhere.invalid/"))
        failure(ApiFailure.Kind.SESSION_EXPIRED) { MobileProfileReader(gate(), sessions, transport).read(reference, "123", null) }
        assertTrue(gate().requiresConnection()); assertEquals(1, transport.calls)
    }
    @Test fun cancelledLateResponseCannotRotateOrClearExistingPause() {
        val reference = sessions.stage(state())
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val transport = Transport(wait = { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }, headers = mapOf("IG-Set-X-MID" to "late"))
        val reader = MobileProfileReader(gate(), sessions, transport)
        val result = AtomicReference<ApiFailure.Kind>()
        val executor = Executors.newSingleThreadExecutor()
        try {
            val pending = executor.submit { try { reader.read(reference, "123", null) } catch (failure: ApiFailure) { result.set(failure.kind) } }
            assertTrue(entered.await(5, TimeUnit.SECONDS)); reader.cancel(); gate().pauseForConnection(); release.countDown()
            pending.get(5, TimeUnit.SECONDS)
            assertEquals(ApiFailure.Kind.CANCELLED, result.get()); assertTrue(transport.cancelled)
            assertNull(sessions.read(reference).headers["X-MID"]); assertTrue(gate().requiresConnection())
        } finally { release.countDown(); executor.shutdownNow() }
    }
    @Test fun stagingPreservesActiveBankAndOldPauseAndHoldBlocksAutomaticWork() {
        val original = sessions.stage(state()); vault.link(original, Profile("123", "synthetic", 1, 2, 1000))
        val gate = gate(); gate.limited("7200"); val until = gate.blockedUntil()
        val next = sessions.stage(state("b".repeat(32)))
        assertEquals(next, MobileSessions(context, "mobiletest").pending())
        assertNotEquals(original, next); assertEquals(original, vault.read()!!.session)
        assertEquals(original, sessions.read(original).reference()); assertEquals(until, gate.blockedUntil())
        preferences.edit().clear().commit(); gate.setMobileLinkActive(true)
        assertFalse(gate.reserveAutomatic()); assertTrue(gate.automationHeld())
        assertFalse(gate.autoAllowed(vault.read()!!.profile))
        vault.link(next, Profile("123", "synthetic", 1, 2, 2000))
        assertNull(MobileSessions(context, "mobiletest").pending())
    }
    @Test fun connectionTestRetainsFifteenMinuteBudgetAcrossReopening() {
        val reference = sessions.stage(state()); val transport = Transport()
        gate().pauseAutomatic(); gate().pauseForConnection()
        MobileProfileReader(gate(), sessions, transport).read(reference, "123", null, connectionTest = true)
        assertEquals(1, transport.calls)
        assertTrue(gate().manualOnly()); assertTrue(gate().requiresConnection())
        assertNull(vault.read())
        failure(ApiFailure.Kind.TOO_SOON) { MobileProfileReader(gate(), sessions, transport).read(reference, "123", null, connectionTest = true) }
        assertEquals(1, transport.calls)
        assertTrue(gate().nextPrototypeAt() >= System.currentTimeMillis() + 890000)
    }
    @Test fun switchingPendingAccountPreservesActiveBankAndRateRestriction() {
        val original = sessions.stage(state()); vault.link(original, Profile("123", "synthetic", 1, 2, 1000))
        val pending = sessions.stage(state("b".repeat(32)))
        gate().limited("7200"); val until = gate().blockedUntil()
        sessions.discardPending()
        assertNull(sessions.pending()); assertEquals(original, sessions.read(original).reference())
        failure(ApiFailure.Kind.SESSION_EXPIRED) { sessions.read(pending) }
        assertEquals(until, gate().blockedUntil()); assertEquals(original, vault.read()!!.session)
    }
    private fun envelope(request: JSONObject, payload: String): String {
        val public = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(request.getString("publicKey").decodeBase64()!!.toByteArray()))
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }; val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val aes = Cipher.getInstance("AES/GCM/NoPadding")
        aes.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        aes.updateAAD("InstagramTracker/mobile-link/v1/${request.getString("requestId")}".toByteArray())
        val rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        rsa.init(Cipher.ENCRYPT_MODE, public, OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT))
        return JSONObject().put("version", 1).put("requestId", request.getString("requestId"))
            .put("wrappedKey", rsa.doFinal(key).toByteString().base64()).put("iv", iv.toByteString().base64())
            .put("ciphertext", aes.doFinal(payload.toByteArray()).toByteString().base64()).toString()
    }
    @Test fun keystorePairingDecryptsOnlyOwnEnvelopeAndClearPreventsReplay() {
        link.prepare(); val request = JSONObject(link.publicFile.readText())
        val payload = JSONObject(MobileSessionCodec.encode(state())).put("expiresAt", System.currentTimeMillis() / 1000 + 600).toString()
        val sealed = envelope(request, payload); link.bundleFile.writeText(sealed)
        val imported = link.importBundle()
        assertEquals(state().owner, imported.owner); assertNotEquals(state().binding, imported.binding)
        val tampered = JSONObject(sealed).put("requestId", "f".repeat(32)); link.bundleFile.writeText(tampered.toString())
        assertThrows(Exception::class.java) { link.importBundle() }
        link.bundleFile.writeText(sealed)
        link.clear(); assertFalse(link.publicFile.exists()); assertFalse(link.bundleFile.exists())
        link.prepare(); link.bundleFile.writeText(sealed)
        assertThrows(Exception::class.java) { link.importBundle() }
    }
    @Test fun damagedAndExpiredEnvelopeNeverStagesOrReplacesAccount() {
        link.prepare(); val request = JSONObject(link.publicFile.readText())
        val payload = JSONObject(MobileSessionCodec.encode(state())).put("expiresAt", 1).toString()
        link.bundleFile.writeText(envelope(request, payload))
        assertThrows(Exception::class.java) { link.importBundle() }
        link.bundleFile.writeText("x".repeat(256 * 1024 + 1))
        assertThrows(Exception::class.java) { link.importBundle() }
        assertNull(vault.read())
    }
}
