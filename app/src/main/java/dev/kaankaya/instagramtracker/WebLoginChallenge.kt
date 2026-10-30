package dev.kaankaya.instagramtracker

import java.net.URI
import org.json.JSONObject

/** An in-memory verification link from this login response. No link/token is persisted. */
class WebLoginChallenge private constructor(val page: String, private val readPath: String?, private val securityEntry: URI? = null) {
    @Volatile private var enabled = false
    @Volatile private var redirectedEntry: URI? = null
    val securityPage get() = securityEntry != null
    val authorizedSecurityPage get() = enabled && securityPage
    fun authorize() { enabled = true }
    fun close() { enabled = false; redirectedEntry = null }
    override fun toString() = "WebLoginChallenge(redacted)"

    fun resource(method: String, url: String): Boolean {
        if (!enabled) return false
        val u = secure(url) ?: return false
        return when (u.host) {
            "www.instagram.com" -> if (securityEntry != null) method == "GET" &&
                (matches(u,securityEntry) || redirectedEntry?.let {matches(u,it)} == true)
                else method == "GET" && u.rawPath == readPath || method == "POST" && u.rawPath == "/api/v1/challenge/web/action/"
            "www.fbsbx.com" -> method == "GET" && u.rawPath == "/captcha/recaptcha/iframe/"
            "www.google.com", "recaptcha.google.com" -> method in setOf("GET", "POST") && u.rawPath.startsWith("/recaptcha/")
            "www.gstatic.com" -> method == "GET" && u.rawPath.startsWith("/recaptcha/")
            else -> false
        }
    }
    fun subframe(url: String): Boolean = resource("GET", url) && secure(url)?.host != "www.instagram.com"
    fun navigation(method: String, url: String): Boolean = securityEntry != null && method == "GET" &&
        secure(url)?.host == "www.instagram.com" && resource(method,url)
    fun viewContext(url: String): String? {
        if(!authorizedSecurityPage || !navigation("GET",url))return null
        return try {
            val values=secure(url)?.rawQuery.orEmpty().split('&').mapNotNull {part->
                val pair=part.split('=',limit=2)
                if(java.net.URLDecoder.decode(pair[0],"UTF-8")!="apc")null else java.net.URLDecoder.decode(pair.getOrElse(1){""},"UTF-8")
            }
            values.singleOrNull()?.takeIf {it.length in 1..8192 && it.none {c->c.code<32||c.code==127}}
        }catch(_: Exception){null}
    }
    /** Authority comes from WebView's HTTP-redirect fact, not a page-supplied target. */
    fun followRedirect(method: String, url: String, mainFrame: Boolean, redirect: Boolean): Boolean {
        if (!authorizedSecurityPage || !mainFrame || !redirect || method != "GET") return false
        val u=secure(url) ?: return false
        if (u.host!="www.instagram.com" || !u.rawPath.startsWith("/auth_platform/")) return false
        redirectedEntry=u
        return true
    }

    companion object {
        enum class Status { NOT_REQUIRED, MISSING_LINK, INVALID_TYPE, EMPTY_LINK, INVALID_URL, OTHER_HOST, UNSUPPORTED_PATH, INVALID_TOKEN, SUPPORTED }
        enum class Route { BLANK, ROOT, LOGIN, TWO_FACTOR, AUTH_PLATFORM, CHALLENGE, CHECKPOINT, OTHER }
        class Assessment(val status: Status, val challenge: WebLoginChallenge? = null, val route: Route? = null) {
            override fun toString() = "ChallengeAssessment($status, $route)"
        }
        private fun secure(url: String): URI? = try {
            URI(url).takeIf { it.scheme == "https" && it.rawUserInfo == null && it.port in setOf(-1,443) &&
                it.rawFragment == null && it.rawPath != null && !it.rawPath.contains('%') &&
                !it.rawPath.contains("..") && url.length <= 8192 }
        } catch (_: Exception) { null }
        private fun matches(a: URI,b: URI)=a.rawPath==b.rawPath && a.rawQuery==b.rawQuery
        fun pageRoute(url: String): Route? = if(url=="about:blank") Route.BLANK else
            secure(url)?.takeIf {it.host=="www.instagram.com"}?.let(::route)
        private fun route(u: URI): Route = when {
            u.rawPath == "/" -> Route.ROOT
            u.rawPath in setOf("/accounts/login/two_factor/", "/accounts/two_factor_authentication/") -> Route.TWO_FACTOR
            u.rawPath == "/accounts/login/" -> Route.LOGIN
            u.rawPath.startsWith("/auth_platform/") -> Route.AUTH_PLATFORM
            u.rawPath.startsWith("/challenge/") -> Route.CHALLENGE
            u.rawPath.startsWith("/checkpoint/") -> Route.CHECKPOINT
            else -> Route.OTHER
        }

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
            val route = route(u)
            // Observed server redirect, with the public Meta login handler's full-page branch.
            // This grants only the exact first-party GET, never arbitrary Bloks/GraphQL POSTs.
            if (route == Route.AUTH_PLATFORM && facts.igLoginRecaptcha == false)
                return Assessment(Status.SUPPORTED, WebLoginChallenge(url, null, u), route)
            val tokens = when {
                u.rawPath.startsWith("/challenge/action/") -> u.rawPath.removePrefix("/challenge/action/")
                u.rawPath.startsWith("/challenge/") -> u.rawPath.removePrefix("/challenge/")
                else -> return Assessment(Status.UNSUPPORTED_PATH, route = route)
            }
            if (tokens.length !in 1..2048 || !tokens.matches(Regex("[A-Za-z0-9_=-]+(?:/[A-Za-z0-9_=-]+)*/?"))) return Assessment(Status.INVALID_TOKEN, route = route)
            return Assessment(Status.SUPPORTED, WebLoginChallenge(url, "/api/v1/challenge/web/$tokens"), route)
        }
    }
}
