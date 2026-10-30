package dev.kaankaya.instagramtracker

import okhttp3.Cookie
import org.json.JSONObject

/** Numeric/error-shape evidence only. Never retains response text, cookie values or account identifiers. */
data class WebLoginResponseFacts(val status: Int, val jsonParsed: Boolean, val dataShape: Shape,
    val errorsShape: Shape, val errorCount: Int?, val errorCodes: List<Int>, val codesIncomplete: Boolean,
    val sessionCookieReceived: Boolean, val ownerCookieReceived: Boolean, val cookiesParsed: Boolean,
    val cookiesApplied: Boolean? = null, val caaResultPresent: Boolean = false,
    val authenticated: Boolean? = null, val captchaNeeded: Boolean? = null,
    val twoFactorPayloadPresent: Boolean? = null, val redirectPresent: Boolean? = null,
    val messageClass: MessageClass = MessageClass.UNKNOWN,
    val challengeStatus: WebLoginChallenge.Companion.Status? = null,
    val challengeRoute: WebLoginChallenge.Companion.Route? = null,
    val igLoginRecaptcha: Boolean? = null, val captchaDataPresent: Boolean? = null) {
    enum class Shape { MISSING, NULL, OBJECT, ARRAY, OTHER, UNKNOWN }
    enum class MessageClass { NONE, BAD_LOGIN_INFORMATION, OTHER, UNKNOWN }
    fun json() = JSONObject().put("status", status).put("jsonParsed", jsonParsed).put("dataShape", dataShape.name)
        .put("errorsShape", errorsShape.name).put("errorCount", errorCount ?: JSONObject.NULL)
        .put("errorCodes", org.json.JSONArray(errorCodes)).put("codesIncomplete", codesIncomplete)
        .put("sessionCookieReceived", sessionCookieReceived).put("ownerCookieReceived", ownerCookieReceived)
        .put("cookiesParsed", cookiesParsed).put("cookiesApplied", cookiesApplied ?: JSONObject.NULL)
        .put("caaResultPresent", caaResultPresent).put("authenticated", authenticated ?: JSONObject.NULL)
        .put("captchaNeeded", captchaNeeded ?: JSONObject.NULL).put("twoFactorPayloadPresent", twoFactorPayloadPresent ?: JSONObject.NULL)
        .put("redirectPresent", redirectPresent ?: JSONObject.NULL).put("messageClass", messageClass.name)
        .put("challengeStatus", challengeStatus?.name ?: JSONObject.NULL)
        .put("challengeRoute", challengeRoute?.name ?: JSONObject.NULL)
        .put("igLoginRecaptcha", igLoginRecaptcha ?: JSONObject.NULL).put("captchaDataPresent", captchaDataPresent ?: JSONObject.NULL)
    companion object {
        fun inspect(status: Int, body: JSONObject?, cookies: List<Cookie>, allCookiesParsed: Boolean, now: Long): WebLoginResponseFacts {
            require(status in 100..599)
            fun shape(key: String) = when {
                body == null -> Shape.UNKNOWN
                !body.has(key) -> Shape.MISSING
                body.isNull(key) -> Shape.NULL
                body.opt(key) is JSONObject -> Shape.OBJECT
                body.opt(key) is org.json.JSONArray -> Shape.ARRAY
                else -> Shape.OTHER
            }
            val codes = linkedSetOf<Int>(); var incomplete = false
            fun code(value: Any?) {
                if (value == null || value == JSONObject.NULL) return
                val number = when (value) {
                    is Int -> value.toLong()
                    is Long -> value
                    is String -> value.takeIf { it.matches(Regex("-?[0-9]{1,10}")) }?.toLongOrNull()
                    else -> null
                }
                if (number == null || number !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() || codes.size >= 8) incomplete = true
                else codes.add(number.toInt())
            }
            code(body?.opt("error"))
            // Field names come from the public persisted login document's selection tree.
            val caa = body?.optJSONObject("data")?.optJSONObject("caa_login_web")
            code(caa?.opt("error_code"))
            val errors = body?.optJSONArray("errors")
            if (errors != null) {
                if (errors.length() > 8) incomplete = true
                for (index in 0 until minOf(errors.length(), 8)) {
                    val error = errors.optJSONObject(index)
                    if (error == null) incomplete = true else { code(error.opt("code")); code(error.opt("api_error_code")) }
                }
            }
            val usable = cookies.filter { it.value.isNotEmpty() && it.expiresAt > now }
            fun present(key: String): Boolean? = caa?.let { it.has(key) && !it.isNull(key) && it.opt(key) != "" }
            val message = caa?.optJSONObject("error_message")?.opt("text") as? String
            val messageClass = when {
                caa == null -> MessageClass.UNKNOWN
                !caa.has("error_message") || caa.isNull("error_message") -> MessageClass.NONE
                message == null -> MessageClass.UNKNOWN
                message.startsWith("The login information you entered is incorrect") -> MessageClass.BAD_LOGIN_INFORMATION
                else -> MessageClass.OTHER
            }
            return WebLoginResponseFacts(status, body != null, shape("data"), shape("errors"), errors?.length(), codes.toList(), incomplete,
                usable.any { it.name == "sessionid" }, usable.any { it.name == "ds_user_id" }, allCookiesParsed,
                caaResultPresent = caa != null, authenticated = caa?.opt("ig_authenticated") as? Boolean,
                captchaNeeded = caa?.opt("recaptcha_needed") as? Boolean, twoFactorPayloadPresent = present("two_factor_result"),
                redirectPresent = present("redirect_uri"), messageClass = messageClass,
                igLoginRecaptcha = caa?.opt("is_ig_login_recaptcha") as? Boolean,
                captchaDataPresent = present("captcha_persist_data"))
        }
    }
}
