package dev.kaankaya.instagramtracker

import org.json.JSONObject

/** Identity observed in the consented verification page on 2026-11-05; never a generic GraphQL grant. */
internal object AuthPlatformCaptchaContent {
    const val DOCUMENT = "34422716160661050"
    const val OPERATION = "AuthPlatformReCaptchaContentQuery"
    fun matches(document: String?, operation: String?) = document == DOCUMENT && operation == OPERATION

    /** The page's exact persisted-query module must confirm its query kind and argument/root names. */
    data class Shape(val arguments: Set<String>,val roots: Set<String>)
    fun shape(raw: JSONObject?): Shape {
        require(raw != null)
        val schema = AuthPlatformSchema.inspect(raw.toString(),DOCUMENT,OPERATION,"query")
        require(schema.opt("available") == true && schema.opt("incomplete") == false)
        fun names(key: String) = schema.getJSONArray(key).let { values ->
            (0 until values.length()).map { values.getString(it) }.toSet()
        }
        val arguments = names("arguments")
        require(arguments.isEmpty())
        val roots = names("rootFields").also { fields ->
            require(fields == setOf("xfb_auth_platform_anti_scripting_content"))
        }
        return Shape(arguments,roots)
    }
    fun validData(json: JSONObject, roots: Set<String>): Boolean {
        val data = json.optJSONObject("data") ?: return false
        val captcha = roots.singleOrNull()?.takeIf { it == "xfb_auth_platform_anti_scripting_content" } ?: return false
        return data.keys().asSequence().toSet() == roots && !data.isNull(captcha) &&
            (!json.has("errors") || json.isNull("errors") || json.optJSONArray("errors")?.length() == 0)
    }
}
