package dev.kaankaya.instagramtracker

import org.json.JSONObject

/** A single observed, persisted login document. This never authorizes arbitrary GraphQL. */
object WebLoginContract {
    const val URL = "https://www.instagram.com/api/graphql"
    const val DOCUMENT = "28610511871940857"
    const val OPERATION = "useCDSWebLoginMutation"
    private val inputFields = setOf("actor_id", "client_mutation_id", "access_flow_version", "account_recovery_entry_point",
        "app", "auth_domain_data_key", "caa_login_request_extra_info", "credential_type", "dyi_job_id", "enc_password",
        "event_request_id", "identifier", "ig_web_device_id", "initial_request_id", "lids", "login_source", "next",
        "passkey_payload", "password", "persistent", "query_params", "trusted_device_records", "use_uid_to_login", "waterfall_id")
    private val extraFields = setOf("ab_test_data", "shared_prefs_data", "cuid", "guid", "jazoest", "lgndim", "lgnjs", "lgnrnd",
        "locale", "login_source", "lsd", "next", "prefill_contact_point", "prefill_source", "prefill_type", "skstamp", "timezone")
    private val cipher = Regex("#PWD_BROWSER:[1-9][0-9]{0,2}:[0-9]{10,13}:[A-Za-z0-9+/=]{64,20000}")
    private fun JSONObject.fieldNames() = keys().asSequence().toSet()
    internal fun authorize(raw: String): WebLoginAuthorization {
        val pairs = WebGraphqlForm.parse(raw)
        require(pairs["doc_id"] == DOCUMENT && pairs["fb_api_req_friendly_name"] == OPERATION)
        val variables = JSONObject(requireNotNull(pairs["variables"]))
        require(variables.fieldNames() == setOf("input", "scale"))
        val scale = variables.get("scale"); require(scale is Number && scale.toDouble().isFinite() && scale.toDouble() in 0.5..5.0)
        val input = variables.getJSONObject("input"); require(input.fieldNames().all { it in inputFields })
        require(input.get("actor_id") == "0")
        require(input.get("app") == "instagram" && input.get("credential_type") == "password")
        require(input.get("use_uid_to_login") == false && input.isNull("passkey_payload"))
        val identifier = input.get("identifier"); require(identifier is String && identifier.length in 1..320 && identifier.none { it == '\r' || it == '\n' })
        for (name in listOf("password", "enc_password")) {
            val secret = input.getJSONObject(name); require(secret.fieldNames() == setOf("sensitive_string_value"))
            val value = secret.get("sensitive_string_value"); require(value is String && cipher.matches(value))
        }
        require(input.getJSONObject("password").getString("sensitive_string_value") == input.getJSONObject("enc_password").getString("sensitive_string_value"))
        input.optJSONObject("caa_login_request_extra_info")?.let { extra ->
            require(extra.fieldNames().all { it in extraFields }); require(extra.fieldNames().all { extra.get(it) is String })
        }
        for (key in input.fieldNames() - setOf("password", "enc_password", "caa_login_request_extra_info"))
            require(input.isNull(key) || input.get(key) is String || input.get(key) is Boolean)
        // Canonicalize JSON once so duplicate JSON keys cannot be interpreted differently by the server.
        pairs["variables"] = variables.toString()
        return WebLoginAuthorization(WebGraphqlForm.body(pairs))
    }
}
