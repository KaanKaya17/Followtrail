package dev.kaankaya.instagramtracker

import java.net.URI
import okhttp3.FormBody
import okhttp3.Request

/** Exact transport contracts. A trusted host alone never authorizes a write. */
object AccountRequestPolicy {
    private val read = Regex("/api/v1/(users/([1-9][0-9]{0,29}/info|[A-Za-z0-9._]{1,30}/usernameinfo)|friendships/[1-9][0-9]{0,29}/(followers|following))/")
    private fun address(url: String): URI? = try {
        URI(url).takeIf { it.scheme == "https" && it.rawUserInfo == null && it.port in listOf(-1, 443) &&
            it.fragment == null && !it.rawPath.contains('%') && !it.rawPath.contains("..") && !it.rawPath.contains('\\') }
    } catch (_: Exception) { null }
    fun readAllowed(method: String, url: String, hasBody: Boolean = false): Boolean {
        val u = address(url) ?: return false
        return method == "GET" && !hasBody && u.host in setOf("i.instagram.com", "www.instagram.com") &&
            (read.matches(u.rawPath) || u.host == "www.instagram.com" && u.rawPath == "/api/v1/users/web_profile_info/")
    }
    private val actions = setOf("com.bloks.www.bloks.caa.login.process_client_data_and_redirect",
        "com.bloks.www.caa.login.oauth.token.fetch.async", "com.bloks.www.bloks.caa.login.async.send_login_request",
        "com.bloks.www.two_step_verification.method_picker.navigation.async", "com.bloks.www.two_step_verification.verify_code.async",
        PhoneBloks.AP_ENTRY, PhoneBloks.AP_SUBMIT)
    private val apps = setOf(PhoneBloks.TWO_ENTRY, "com.bloks.www.two_step_verification.method_picker",
        "com.bloks.www.two_factor_login.enter_backup_code", PhoneBloks.AP_CODE)
    fun loginAllowed(request: Request, operation: PhoneLoginOperation): Boolean {
        val u = address(request.url.toString()) ?: return false
        if (operation == PhoneLoginOperation.PROFILE) return readAllowed(request.method, request.url.toString(), request.body != null) && u.host == "i.instagram.com"
        if (u.host !in setOf("i.instagram.com", "b.i.instagram.com")) return false
        if (operation == PhoneLoginOperation.PASSWORD_KEY)
            return request.method == "GET" && request.body == null && u.host == "i.instagram.com" && u.rawPath == "/api/v1/qe/sync/" && u.rawQuery == null
        if (request.method != "POST" || u.rawQuery != null || request.body !is FormBody) return false
        if (operation == PhoneLoginOperation.DEVICE) {
            val body = request.body as FormBody
            val fields = (0 until body.size).associate { body.name(it) to body.value(it) }
            return u.host == "b.i.instagram.com" && u.rawPath == "/graphql_www" && fields.size == body.size &&
                fields.keys.all { it in setOf("method","pretty","format","server_timestamps","locale","fb_api_req_friendly_name",
                    "enable_canonical_naming","enable_canonical_variable_overrides","enable_canonical_naming_ambiguous_type_prefixing",
                    "purpose","client_doc_id","variables") } && fields["client_doc_id"] == "124930351917786857261002920888" &&
                fields["fb_api_req_friendly_name"] == "IGUSDIDRegistrationMutation"
        }
        return when (operation) {
            PhoneLoginOperation.CHALLENGE -> u.rawPath == "/api/v1/attestation/create_android_keystore/"
            PhoneLoginOperation.LEGACY -> u.host == "i.instagram.com" && u.rawPath == "/api/v1/accounts/login/"
            else -> actions.any { u.rawPath == "/api/v1/bloks/async_action/$it/" } || apps.any { u.rawPath == "/api/v1/bloks/apps/$it/" }
        }
    }
}

/** Browser POST bodies are opaque to WebViewClient; generic GraphQL is denied. */
object LoginBrowserPolicy {
    private fun uri(url: String) = try { URI(url).takeIf { it.scheme == "https" && it.rawUserInfo == null && it.port in listOf(-1,443) && !it.rawPath.contains('%') && !it.rawPath.contains("..") } } catch (_: Exception) { null }
    private fun page(path: String) = path in setOf("/", "/accounts/login/", "/accounts/login/two_factor/", "/accounts/two_factor_authentication/", "/accounts/onetap/") || path.startsWith("/challenge/") || path.startsWith("/checkpoint/")
    fun navigation(url: String): Boolean = uri(url)?.let { it.host in setOf("www.instagram.com","instagram.com") && page(it.rawPath) } == true
    enum class ResourceKind { PAGE, STATIC, BOOTSTRAP, PROFILE, CDN, AUTH, CAPTCHA, GRAPHQL, INSTAGRAM_OTHER, OTHER_HOST, INVALID }
    data class ResourceDecision(val allowed: Boolean, val kind: ResourceKind)
    fun resource(method: String, url: String): Boolean = resourceDecision(method, url).allowed
    fun resourceDecision(method: String, url: String): ResourceDecision {
        val u = uri(url) ?: return ResourceDecision(false, ResourceKind.INVALID)
        val instagram = u.host in setOf("www.instagram.com", "instagram.com")
        val kind = when {
            instagram && u.rawPath in setOf("/api/graphql", "/api/graphql/", "/graphql/query/", "/graphql_www") -> ResourceKind.GRAPHQL
            instagram && page(u.rawPath) -> ResourceKind.PAGE
            instagram && (u.rawPath.startsWith("/static/") || u.rawPath.startsWith("/rsrc.php/")) -> ResourceKind.STATIC
            instagram && u.rawPath in setOf("/data/shared_data/", "/api/v1/web/data/shared_data/", "/ajax/bootloader-endpoint/", "/ajax/bulk-route-definitions/") -> ResourceKind.BOOTSTRAP
            instagram && AccountRequestPolicy.readAllowed("GET", url) -> ResourceKind.PROFILE
            instagram -> ResourceKind.INSTAGRAM_OTHER
            listOf("cdninstagram.com", "fbcdn.net").any { u.host == it || u.host?.endsWith(".$it") == true } -> ResourceKind.CDN
            else -> ResourceKind.OTHER_HOST
        }
        if (u.rawPath == "/ajax/bulk-route-definitions/") return ResourceDecision(
            method == "POST" && u.host == "www.instagram.com", kind)
        if (u.rawPath == "/ajax/bootloader-endpoint/" && u.host != "www.instagram.com") return ResourceDecision(false, kind)
        if (method == "GET") return ResourceDecision(kind in setOf(ResourceKind.PAGE, ResourceKind.STATIC,
            ResourceKind.BOOTSTRAP, ResourceKind.PROFILE, ResourceKind.CDN), kind)
        val auth = method == "POST" && u.host == "www.instagram.com" &&
            (u.rawPath in setOf("/accounts/login/ajax/", "/accounts/login/ajax/two_factor/", "/accounts/two_factor_login/",
                "/api/v1/web/accounts/login/ajax/", "/api/v1/web/accounts/login/ajax/two_factor/") ||
                u.rawPath.startsWith("/challenge/") || u.rawPath.startsWith("/checkpoint/"))
        return ResourceDecision(auth, if (auth) ResourceKind.AUTH else kind)
    }
    fun landing(url: String): Boolean = uri(url)?.let { it.host in setOf("www.instagram.com","instagram.com") && it.rawPath in setOf("/","/accounts/onetap/") } == true
}
