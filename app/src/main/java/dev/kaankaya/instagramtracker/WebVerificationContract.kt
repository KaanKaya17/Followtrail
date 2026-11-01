package dev.kaankaya.instagramtracker

import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject

/** Observed email-code submission, bound to the currently consented auth_platform context. */
internal object WebVerificationContract {
    fun authorizeCode(raw: String, path: String, context: String?): WebCodeSubmissionAuthorization {
        require(path in WebGraphqlForm.PATHS && context != null)
        val pairs = WebGraphqlForm.parse(raw)
        require(pairs["doc_id"] == AuthPlatformVerification.submitCode.document &&
            pairs["fb_api_req_friendly_name"] == AuthPlatformVerification.submitCode.name)
        val variables = JSONObject(requireNotNull(pairs["variables"]))
        require(variables.keys().asSequence().toSet() == setOf("input"))
        val input = variables.getJSONObject("input")
        require(input.keys().asSequence().toSet() == setOf("actor_id", "client_mutation_id", "code", "device_id", "encrypted_ap_context"))
        require(input.get("actor_id") == "0" && input.get("device_id") == JSONObject.NULL)
        val mutation = input.get("client_mutation_id")
        require(mutation is String && mutation.length in 1..64 && mutation.all { it.code in 32..126 })
        val code = input.get("code")
        require(code is String && code.matches(Regex("[0-9]{6,10}")))
        val apc = input.get("encrypted_ap_context")
        require(apc is String && apc.length in 1..8192 && apc == context && apc.none { it.code < 32 || it.code == 127 })
        pairs["variables"] = variables.toString()
        return WebCodeSubmissionAuthorization("https://www.instagram.com$path", WebGraphqlForm.body(pairs))
    }

    fun validData(json: JSONObject): Boolean {
        val data = json.optJSONObject("data") ?: return false
        return data.keys().asSequence().toSet() == setOf("xfb_auth_platform_submit_code") &&
            !data.isNull("xfb_auth_platform_submit_code") &&
            (!json.has("errors") || json.isNull("errors") || json.optJSONArray("errors")?.length() == 0)
    }
}

internal class WebCodeSubmissionAuthorization(val url: String, val body: FormBody) {
    fun apply(builder: Request.Builder) = builder.url(url).post(body).tag(WebCodeSubmissionAuthorization::class.java, this)
    override fun toString() = "WebCodeSubmissionAuthorization(redacted)"
}
