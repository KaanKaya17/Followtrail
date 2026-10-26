package dev.kaankaya.instagramtracker

import org.json.JSONArray
import org.json.JSONObject
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class PhoneLoginFailure(val reason: Reason) : Exception(reason.name) {
    enum class Reason { INPUT, CREDENTIALS, UNSUPPORTED, CODE, EXPIRED }
}

/** Literal-only projection of the pinned instagrapi 3.2.0 Bloks contract (MIT). Never executes server code. */
object PhoneBloks {
    const val AP_ENTRY = "com.bloks.www.ap.two_step_verification.entrypoint_async"
    const val AP_CODE = "com.bloks.www.ap.two_step_verification.code_entry"
    const val AP_SUBMIT = "com.bloks.www.ap.two_step_verification.code_entry_async"
    const val TWO_ENTRY = "com.bloks.www.two_step_verification.entrypoint"
    fun strings(root: Any): List<String> {
        val pending = java.util.ArrayDeque<Any>(); pending.add(root)
        val result = mutableListOf<String>(); var nodes = 0
        while (pending.isNotEmpty()) {
            if (++nodes > 65_536) throw PhoneLoginFailure(PhoneLoginFailure.Reason.UNSUPPORTED)
            when (val value = pending.removeLast()) {
                is String -> result += value
                is JSONObject -> value.keys().forEach { key -> value.opt(key)?.let { pending.add(it) } }
                is JSONArray -> for (i in 0 until value.length()) value.opt(i)?.let { pending.add(it) }
            }
        }
        return result
    }
    private fun quotedEnd(text: String, start: Int): Int {
        var escaped = false
        for (i in start + 1 until text.length) {
            if (escaped) escaped = false
            else if (text[i] == '\\') escaped = true
            else if (text[i] == '"') return i + 1
        }
        return -1
    }
    fun literals(text: String): List<String> {
        val result = mutableListOf<String>(); var i = 0
        while (i < text.length) {
            if (text[i] != '"') { i++; continue }
            val end = quotedEnd(text, i); if (end < 0) break
            try { result += JSONArray("[${text.substring(i, end)}]").getString(0) } catch (_: Exception) { }
            i = end
        }
        return result
    }
    private fun expression(text: String, start: Int): String? {
        var i = start; var depth = 0
        while (i < text.length) {
            when (text[i]) {
                '"' -> { i = quotedEnd(text, i); if (i < 0) return null; continue }
                '(' -> { depth++; if (depth > 64) return null }
                ')' -> { depth--; if (depth == 0) return text.substring(start, i + 1) }
            }; i++
        }
        return null
    }
    private fun items(text: String): List<String> {
        val result = mutableListOf<String>(); var i = 1
        while (i < text.length - 1) {
            if (text[i].isWhitespace()) { i++; continue }
            val token = when (text[i]) {
                '(' -> expression(text, i) ?: return emptyList()
                '"' -> { val end = quotedEnd(text, i); if (end < 0) return emptyList(); text.substring(i, end) }
                else -> text.substring(i).takeWhile { !it.isWhitespace() && it !in "()\"" }
            }
            if (token.isEmpty()) return emptyList()
            result += token; i += token.length
        }
        return result
    }
    fun context(root: JSONObject, app: String, key: String = "context_data"): String? {
        val anchor = JSONObject.quote(app)
        for (text in strings(root)) {
            var offset = text.indexOf(anchor)
            while (offset >= 0) {
                val end = offset + anchor.length
                val start = text.indexOf("(f4i", end)
                val nextApp = Regex("com\\.bloks\\.[_a-zA-Z0-9.]+").find(text, end)?.range?.first
                if (start >= 0 && (nextApp == null || nextApp >= start)) {
                    var map = expression(text, start)
                    repeat(8) {
                        val parts = map?.let(::items).orEmpty()
                        if (parts.size != 3 || parts[0] != "f4i") return@repeat
                        val keys = items(parts[1]); val values = items(parts[2])
                        if (keys.firstOrNull() != "dkc" || values.firstOrNull() != "dkc" || keys.size != values.size) return@repeat
                        map = null
                        for (i in 1 until keys.size) {
                            val name = if (keys[i].startsWith('"')) literals(keys[i]).singleOrNull() else null
                            if (name == key) return if (values[i].startsWith('"')) literals(values[i]).singleOrNull()?.takeIf { it.isNotBlank() } else null
                            if (name == "server_params") map = values[i]
                        }
                    }
                }
                offset = text.indexOf(anchor, end)
            }
        }
        return null
    }
    fun aac(root: JSONObject): String? {
        val data = root.optJSONObject("layout")?.optJSONObject("bloks_payload")?.optJSONArray("data") ?: return null
        for (i in 0 until data.length()) {
            val entry = data.optJSONObject(i)?.optJSONObject("data") ?: continue
            if (entry.optString("key") != "CAA_ACCOUNT_ACCESS_CONTEXT:aac") continue
            return (entry.opt("initial") as? String)?.takeIf { it.isNotBlank() }
                ?: (entry.opt("initial_lispy") as? String)?.let(::literals)?.firstOrNull()?.takeIf { it.isNotBlank() }
        }
        return null
    }
    fun twoContext(root: JSONObject): String? {
        val queue = java.util.ArrayDeque<Any>(); queue.add(root); var nodes = 0
        while (queue.isNotEmpty()) {
            if (++nodes > 65_536) return null
            when (val v = queue.removeLast()) {
                is JSONObject -> {
                    (v.opt("two_step_verification_context") as? String)?.takeIf { it.isNotBlank() }?.let { return it }
                    v.keys().forEach { v.opt(it)?.let(queue::add) }
                }
                is JSONArray -> for (i in 0 until v.length()) v.opt(i)?.let(queue::add)
                is String -> if (v.length < 16_384 && v.trimStart().startsWith('{') && v.contains("two_step_verification_context"))
                    try { queue.add(JSONObject(v)) } catch (_: Exception) { }
            }
        }
        return context(root, TWO_ENTRY, "two_step_verification_context")
    }
    class Login(val body: JSONObject, val headers: JSONObject, val cookies: String)
    fun login(root: JSONObject): Login? {
        val action = root.optJSONObject("layout")?.optJSONObject("bloks_payload")?.opt("action") as? String ?: return null
        for (text in literals(action)) {
            if (!text.contains("login_response")) continue
            try {
                val raw = JSONObject(text)
                return Login(JSONObject(raw.getString("login_response")), JSONObject(raw.optString("headers", "{}")), raw.optString("cookies"))
            } catch (_: Exception) { }
        }
        return null
    }
}

object PhonePassword {
    /** Upstream #PWD_INSTAGRAM:4 envelope, encrypted to Instagram's current response key. */
    fun encrypt(password: CharArray, keyId: Int, publicKey: String, now: Long): String {
        require(password.isNotEmpty() && password.size <= 1024 && keyId in 0..255)
        val decoded = publicKey.decodeBase64()?.toByteArray() ?: throw PhoneLoginFailure(PhoneLoginFailure.Reason.UNSUPPORTED)
        val pem = decoded.toString(Charsets.US_ASCII)
        val der = if (pem.startsWith("-----BEGIN PUBLIC KEY-----")) pem.replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "").replace(Regex("\\s"), "").decodeBase64()?.toByteArray() ?: error("Invalid public key") else decoded
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der))
        val secret = ByteArray(32).also(SecureRandom()::nextBytes)
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val timestamp = (now / 1000).toString()
        val clear = String(password).toByteArray(Charsets.UTF_8)
        try {
            val wrapped = Cipher.getInstance("RSA/ECB/PKCS1Padding").apply { init(Cipher.ENCRYPT_MODE, key) }.doFinal(secret)
            require(wrapped.size <= 65535)
            val encrypted = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(secret, "AES"), GCMParameterSpec(128, iv)); updateAAD(timestamp.toByteArray())
            }.doFinal(clear)
            val bytes = byteArrayOf(1, keyId.toByte()) + iv + byteArrayOf(wrapped.size.toByte(), (wrapped.size shr 8).toByte()) +
                wrapped + encrypted.takeLast(16).toByteArray() + encrypted.dropLast(16).toByteArray()
            return "#PWD_INSTAGRAM:4:$timestamp:${bytes.toByteString().base64()}"
        } finally { secret.fill(0); clear.fill(0) }
    }
}
