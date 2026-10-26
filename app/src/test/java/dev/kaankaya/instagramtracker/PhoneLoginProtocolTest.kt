package dev.kaankaya.instagramtracker

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import java.security.KeyPairGenerator
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class PhoneLoginProtocolTest {
    private fun action(value: String) = JSONObject().put("layout", JSONObject().put("bloks_payload", JSONObject().put("action", value)))
    @Test fun contextComesOnlyFromTheExactAppsLiteralParameterMap() {
        val token = "opaque (context) \\\" unchanged"
        val program = "(navigate ${JSONObject.quote(PhoneBloks.AP_ENTRY)} (f4i (dkc \"server_params\") (dkc (f4i (dkc \"context_data\") (dkc ${JSONObject.quote(token)})))))"
        assertEquals(token, PhoneBloks.context(action(program), PhoneBloks.AP_ENTRY))
        assertNull(PhoneBloks.context(action(program), PhoneBloks.AP_CODE))
        assertNull(PhoneBloks.context(action("(navigate ${JSONObject.quote(PhoneBloks.AP_ENTRY)} (f4i (dkc \"context_data\") (dkc (evaluate \"secret\"))))"), PhoneBloks.AP_ENTRY))
        assertNull(PhoneBloks.context(action("${JSONObject.quote(PhoneBloks.AP_ENTRY)} ${JSONObject.quote(PhoneBloks.AP_CODE)} (f4i (dkc \"context_data\") (dkc \"wrong\"))"), PhoneBloks.AP_ENTRY))
    }
    @Test fun malformedAndDeepProgramsAreNotExecutedOrRepaired() {
        assertNull(PhoneBloks.context(action("${JSONObject.quote(PhoneBloks.AP_ENTRY)} (f4i (dkc \"context_data\")"), PhoneBloks.AP_ENTRY))
        assertNull(PhoneBloks.context(action("${JSONObject.quote(PhoneBloks.AP_ENTRY)} (f4i " + "(".repeat(80) + ")".repeat(80) + ")"), PhoneBloks.AP_ENTRY))
    }
    @Test fun aacHasItsOwnNamedSourceAndMissingIsUnknown() {
        val data = JSONArray().put(JSONObject().put("data", JSONObject().put("key", "unrelated").put("initial", "wrong")))
            .put(JSONObject().put("data", JSONObject().put("key", "CAA_ACCOUNT_ACCESS_CONTEXT:aac").put("initial_lispy", "(literal \"opaque-aac\")")))
        assertEquals("opaque-aac", PhoneBloks.aac(JSONObject().put("layout", JSONObject().put("bloks_payload", JSONObject().put("data", data)))))
        assertNull(PhoneBloks.aac(JSONObject().put("aac", "unowned")))
    }
    @Test fun embeddedLoginMustBeACompleteLiteralEnvelope() {
        val login = JSONObject().put("logged_in_user", JSONObject().put("pk", "123"))
        val envelope = JSONObject().put("login_response", login.toString()).put("headers", JSONObject().put("IG-Set-Authorization", "synthetic").toString())
            .put("cookies", "sessionid=synthetic;")
        val parsed = PhoneBloks.login(action("(handle ${JSONObject.quote(envelope.toString())})"))!!
        assertEquals("123", parsed.body.getJSONObject("logged_in_user").getString("pk"))
        assertEquals("synthetic", parsed.headers.getString("IG-Set-Authorization"))
        assertNull(PhoneBloks.login(JSONObject().put("message", envelope.toString())))
        assertNull(PhoneBloks.login(action("(handle \"login_response but not JSON\")")))
    }
    @Test fun twoFactorContextSupportsStructuredAndExactStaticSources() {
        assertEquals("structured", PhoneBloks.twoContext(JSONObject().put("two_factor_info", JSONObject().put("two_step_verification_context", "structured"))))
        assertEquals("opaque", PhoneBloks.twoContext(action("(navigate ${JSONObject.quote(PhoneBloks.TWO_ENTRY)} (f4i (dkc \"two_step_verification_context\") (dkc \"opaque\")))")))
    }
    @Test fun passwordEnvelopeDecryptsWithTheServerKeyAndTimestampAuthentication() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val password = "non-ASCII şifre 🔒".toCharArray(); val now = 2_000_000_000_000L
        val encrypted = PhonePassword.encrypt(password, 7, pair.public.encoded.toByteString().base64(), now)
        assertFalse(encrypted.contains(String(password)))
        val parts = encrypted.split(':', limit = 4); assertEquals("#PWD_INSTAGRAM", parts[0]); assertEquals("4", parts[1])
        assertEquals((now / 1000).toString(), parts[2])
        val bytes = parts[3].decodeBase64()!!.toByteArray(); assertEquals(1, bytes[0].toInt()); assertEquals(7, bytes[1].toInt())
        val size = (bytes[14].toInt() and 255) or ((bytes[15].toInt() and 255) shl 8)
        val key = Cipher.getInstance("RSA/ECB/PKCS1Padding").apply { init(Cipher.DECRYPT_MODE, pair.private) }.doFinal(bytes.copyOfRange(16, 16 + size))
        val tagAt = 16 + size
        val decoded = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, bytes.copyOfRange(2, 14)))
            updateAAD(parts[2].toByteArray())
        }.doFinal(bytes.copyOfRange(tagAt + 16, bytes.size) + bytes.copyOfRange(tagAt, tagAt + 16))
        assertEquals(String(password), decoded.toString(Charsets.UTF_8))
        assertThrows(IllegalArgumentException::class.java) { PhonePassword.encrypt(password, 256, "", now) }
    }
}
