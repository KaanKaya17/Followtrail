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
        enum class Status { NOT_REQUIRED, MISSING_LINK, INVALID_TYPE, EMPTY_LINK, INVALID_URL, OTHER_HOST, UNSUPPORTED_PATH, INVALID_TOKEN, SUPPORTED }
        class Assessment(val status: Status, val challenge: WebLoginChallenge? = null) {
            override fun toString() = "ChallengeAssessment($status)"
        }
        private fun secure(url: String): URI? = try {
            URI(url).takeIf { it.scheme == "https" && it.rawUserInfo == null && it.port in setOf(-1,443) &&
                it.rawFragment == null && it.rawPath != null && !it.rawPath.contains('%') &&
                !it.rawPath.contains("..") && url.length <= 8192 }
        } catch (_: Exception) { null }

        fun fromResponse(body: JSONObject, facts: WebLoginResponseFacts): WebLoginChallenge? {
            return assess(body, facts).challenge
        }
        /** Fixed reason codes only; the link and its query/token remain in memory. */
        fun assess(body: JSONObject, facts: WebLoginResponseFacts): Assessment {
            if (facts.captchaNeeded != true || facts.authenticated == true) return Assessment(Status.NOT_REQUIRED)
            val caa = body.optJSONObject("data")?.optJSONObject("caa_login_web")
            if (caa == null || !caa.has("redirect_uri") || caa.isNull("redirect_uri")) return Assessment(Status.MISSING_LINK)
            val raw = caa.opt("redirect_uri") as? String ?: return Assessment(Status.INVALID_TYPE)
            if (raw.isBlank()) return Assessment(Status.EMPTY_LINK)
            if (raw.length > 4096 || raw.any { it.code < 32 || it.code == 127 }) return Assessment(Status.INVALID_URL)
            val url = try { URI("https://www.instagram.com/").resolve(raw).toString() } catch (_: Exception) { return Assessment(Status.INVALID_URL) }
            val u = secure(url) ?: return Assessment(Status.INVALID_URL)
            if (u.host != "www.instagram.com") return Assessment(Status.OTHER_HOST)
            val tokens = when {
                u.rawPath.startsWith("/challenge/action/") -> u.rawPath.removePrefix("/challenge/action/")
                u.rawPath.startsWith("/challenge/") -> u.rawPath.removePrefix("/challenge/")
                else -> return Assessment(Status.UNSUPPORTED_PATH)
            }
            if (tokens.length !in 1..2048 || !tokens.matches(Regex("[A-Za-z0-9_=-]+(?:/[A-Za-z0-9_=-]+)*/?"))) return Assessment(Status.INVALID_TOKEN)
            return Assessment(Status.SUPPORTED, WebLoginChallenge(url, "/api/v1/challenge/web/$tokens"))
        }
    }
}
