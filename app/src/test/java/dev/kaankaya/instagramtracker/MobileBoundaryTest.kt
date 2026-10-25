package dev.kaankaya.instagramtracker

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import okio.ByteString.Companion.decodeBase64

class MobileBoundaryTest {
    private fun state(): MobileSession {
        val auth = "Bearer IGT:2:" + """{"ds_user_id":"123","sessionid":"synthetic"}""".encodeUtf8().base64()
        return MobileSession("a".repeat(32), "123", mapOf("Authorization" to auth, "User-Agent" to "Instagram synthetic",
            "X-IG-Device-ID" to "test-device", "X-IG-Family-Device-ID" to "test-family", "X-IG-Android-ID" to "android-test",
            "X-IG-App-ID" to "567067343352427", "X-Bloks-Version-Id" to "test-hash", "IG-INTENDED-USER-ID" to "123",
            "IG-U-DS-USER-ID" to "123"), mapOf("sessionid" to "synthetic", "ds_user_id" to "123"))
    }
    @Test fun mobileSessionRequiresRealAuthorizationIdentityAndBoundedAllowlistedFields() {
        val state = state()
        assertEquals(state, MobileSessionCodec.decode(MobileSessionCodec.encode(state)))
        for (changed in listOf(state.copy(owner = "456"), state.copy(headers = state.headers + ("User-Agent" to "x\r\nCookie: evil")),
            state.copy(headers = state.headers + ("X-Unowned" to "x")), state.copy(cookies = state.cookies + ("sessionid" to "a;other=bad")),
            state.copy(headers = state.headers - "Authorization"))) {
            assertThrows(Exception::class.java) { MobileSessionCodec.encode(changed) }
        }
    }
    @Test fun mobileRequestKeepsDesktopHostAndOwnProfileParametersWithoutWebFallback() {
        val request = MobileProfileReader.request(state(), "123", null, 1000)
        assertEquals("i.instagram.com", request.url.host)
        assertEquals("/api/v1/users/123/info/", request.url.encodedPath)
        assertEquals(mapOf("is_prefetch" to "false", "entry_point" to "self_profile", "from_module" to "self_profile", "is_app_start" to "False"),
            request.url.queryParameterNames.associateWith { request.url.queryParameter(it) })
        assertEquals(state().headers["Authorization"], request.header("Authorization"))
        assertNull(request.header("Referer")); assertNull(request.header("X-Requested-With"))
        val person = MobileProfileReader.request(state(), null, "other.demo", 1000)
        assertEquals("/api/v1/users/other.demo/usernameinfo/", person.url.encodedPath)
        assertNull(person.url.query)
        assertThrows(Exception::class.java) { MobileProfileReader.request(state(), null, "../accounts", 1000) }
    }
    @Test fun rotationPreservesBindingAndRejectsChangedAccountAndDeletedCookies() {
        val state = state()
        val auth = "Bearer IGT:2:" + """{"ds_user_id":"123","sessionid":"rotated"}""".encodeUtf8().base64()
        val updated = state.updated(mapOf("IG-Set-Authorization" to listOf(auth), "IG-Set-X-MID" to listOf("new-mid"),
            "Set-Cookie" to listOf("sessionid=rotated; Path=/; Secure", "csrftoken=gone; Max-Age=0; Path=/")))
        assertEquals(state.reference(), updated.reference())
        assertEquals(auth, updated.headers["Authorization"]); assertEquals("rotated", updated.cookies["sessionid"])
        assertEquals("new-mid", updated.headers["X-MID"]); assertFalse(updated.cookies.containsKey("csrftoken"))
        val foreign = "Bearer IGT:2:" + """{"ds_user_id":"456","sessionid":"foreign"}""".encodeUtf8().base64()
        assertThrows(Exception::class.java) { state.updated(mapOf("IG-Set-Authorization" to listOf(foreign))) }
        assertThrows(Exception::class.java) { state.updated(mapOf("IG-Set-IG-U-DS-USER-ID" to listOf("456"))) }
    }
    @Test fun accountV4RetainsStableMobileBindingAndHistoryWithNoCookieCopy() {
        val saved = SavedAccount(state().reference(), Profile("123", "synthetic", 0, 7, 1000))
        val encoded = AccountCodec.encode(saved)
        assertEquals(saved, AccountCodec.decode(encoded))
        assertFalse(encoded.contains("Bearer")); assertFalse(encoded.contains("sessionid"))
        val broken = JSONObject(encoded).put("cookie", "sessionid=unexpected")
        assertThrows(Exception::class.java) { AccountCodec.decode(broken.toString()) }
    }
    @Test fun expiredTransfersRejectBeforeStagingAndImportedBindingIsFresh() {
        val raw = JSONObject(MobileSessionCodec.encode(state())).put("expiresAt", System.currentTimeMillis() / 1000 + 600)
        assertNotEquals(state().binding, MobileSessionCodec.imported(raw.toString()).binding)
        raw.put("expiresAt", 1)
        assertThrows(Exception::class.java) { MobileSessionCodec.imported(raw.toString()) }
    }
    @Test fun productionTransportDisablesReplaysRedirectsAndParallelFallback() {
        val client = MobileReadTransport.client()
        assertFalse(client.retryOnConnectionFailure); assertFalse(client.followRedirects); assertFalse(client.followSslRedirects)
        assertFalse(client.fastFallback); assertEquals(30000, client.callTimeoutMillis)
        assertTrue(client.networkInterceptors.isNotEmpty())
    }
    @Test fun savedDeviceKeyProducesVerifiableDerSignatureWithoutGeneratingANewIdentity() {
        val generator = java.security.KeyPairGenerator.getInstance("EC")
        generator.initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
        val pair = generator.generateKeyPair()
        val pem = "-----BEGIN PRIVATE KEY-----\n${pair.private.encoded.toByteString().base64()}\n-----END PRIVATE KEY-----"
        val state = state().copy(usdid = "synthetic-device", signingKey = pem)
        val header = state.requestHeaders(1000000).getValue("X-Meta-Usdid")
        val pieces = header.split('.')
        assertEquals("synthetic-device", pieces[0]); assertEquals("4600", pieces[1])
        val verifier = java.security.Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(pair.public); verifier.update("${pieces[0]}.${pieces[1]}".toByteArray())
        assertTrue(verifier.verify(pieces[2].decodeBase64()!!.toByteArray()))
        assertEquals(state.reference(), MobileSessionCodec.decode(MobileSessionCodec.encode(state)).reference())
    }
}
