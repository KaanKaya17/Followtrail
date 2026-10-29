package dev.kaankaya.instagramtracker

import java.net.URI
import org.json.JSONObject

/** An in-memory verification link from this login response. No link/token is persisted. */
class WebLoginChallenge private constructor(val page: String, private val readPath: String) {
    @Volatile private var enabled = false
    fun authorize() { enabled = true }
    fun close() { enabled = false }
    override fun toString() = "WebLoginChallenge(redacted)"

    fun resource(method: String, url: String): Boolean {
        if (!enabled) return false
        val u = secure(url) ?: return false
        return when (u.host) {
            "www.instagram.com" -> method == "GET" && u.rawPath == readPath ||
                method == "POST" && u.rawPath == "/api/v1/challenge/web/action/"
            "www.fbsbx.com" -> method == "GET" && u.rawPath == "/captcha/recaptcha/iframe/"
            "www.google.com", "recaptcha.google.com" -> method in setOf("GET", "POST") && u.rawPath.startsWith("/recaptcha/")
            "www.gstatic.com" -> method == "GET" && u.rawPath.startsWith("/recaptcha/")
            else -> false
        }
    }
    fun subframe(url: String): Boolean = resource("GET", url) && secure(url)?.host != "www.instagram.com"

    companion object {
        private fun secure(url: String): URI? = try {
            URI(url).takeIf { it.scheme == "https" && it.rawUserInfo == null && it.port in setOf(-1,443) &&
                it.rawFragment == null && it.rawPath != null && !it.rawPath.contains('%') &&
                !it.rawPath.contains("..") && url.length <= 8192 }
        } catch (_: Exception) { null }

        fun fromResponse(body: JSONObject, facts: WebLoginResponseFacts): WebLoginChallenge? {
            if (facts.captchaNeeded != true || facts.authenticated == true) return null
            val caa = body.optJSONObject("data")?.optJSONObject("caa_login_web") ?: return null
            val raw = caa.opt("redirect_uri") as? String ?: return null
            if (raw.length > 4096 || raw.any { it.code < 32 || it.code == 127 }) return null
            val url = try { URI("https://www.instagram.com/").resolve(raw).toString() } catch (_: Exception) { return null }
            val u = secure(url)?.takeIf { it.host == "www.instagram.com" } ?: return null
            val tokens = when {
                u.rawPath.startsWith("/challenge/action/") -> u.rawPath.removePrefix("/challenge/action/")
                u.rawPath.startsWith("/challenge/") -> u.rawPath.removePrefix("/challenge/")
                else -> return null
            }
            if (tokens.length !in 1..2048 || !tokens.matches(Regex("[A-Za-z0-9_=-]+(?:/[A-Za-z0-9_=-]+)*/?"))) return null
            return WebLoginChallenge(url, "/api/v1/challenge/web/$tokens")
        }
    }
}
