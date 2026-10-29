package dev.kaankaya.instagramtracker

import okhttp3.FormBody
import org.json.JSONObject
import java.net.URLDecoder

/** A single observed, persisted login document. This never authorizes arbitrary GraphQL. */
object WebLoginContract {
    const val URL = "https://www.instagram.com/api/graphql"
    const val DOCUMENT = "28610511871940857"
    const val OPERATION = "useCDSWebLoginMutation"
    const val MAX_BODY = 65536
    private val fields = setOf("av", "__d", "__user", "__a", "__req", "__hs", "dpr", "__ccg", "__rev", "__s", "__hsi",
        "__dyn", "__csr", "__hsdp", "__hblp", "__sjsp", "__comet_req", "lsd", "jazoest", "__spin_r", "__spin_b",
        "__spin_t", "__crn", "qpl_active_flow_ids", "fb_api_caller_class", "fb_api_req_friendly_name", "server_timestamps",
        "variables", "doc_id", "fb_api_analytics_tags")
    private val inputFields = setOf("actor_id", "client_mutation_id", "access_flow_version", "account_recovery_entry_point",
        "app", "auth_domain_data_key", "caa_login_request_extra_info", "credential_type", "dyi_job_id", "enc_password",
        "event_request_id", "identifier", "ig_web_device_id", "initial_request_id", "lids", "login_source", "next",
        "passkey_payload", "password", "persistent", "query_params", "trusted_device_records", "use_uid_to_login", "waterfall_id")
    private val extraFields = setOf("ab_test_data", "shared_prefs_data", "cuid", "guid", "jazoest", "lgndim", "lgnjs", "lgnrnd",
        "locale", "login_source", "lsd", "next", "prefill_contact_point", "prefill_source", "prefill_type", "skstamp", "timezone")
    private val cipher = Regex("#PWD_BROWSER:[1-9][0-9]{0,2}:[0-9]{10,13}:[A-Za-z0-9+/=]{64,20000}")
    private fun JSONObject.fieldNames() = keys().asSequence().toSet()
    internal fun authorize(raw: String): WebLoginAuthorization {
        require(raw.length <= MAX_BODY && raw.toByteArray(Charsets.UTF_8).size <= MAX_BODY)
        val pairs = linkedMapOf<String, String>()
        for (part in raw.split('&')) {
            val pair = part.split('=', limit = 2); require(pair.size == 2)
            val key = URLDecoder.decode(pair[0], "UTF-8"); val value = URLDecoder.decode(pair[1], "UTF-8")
            require(key in fields && key !in pairs); pairs[key] = value
        }
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
        return WebLoginAuthorization(FormBody.Builder().apply { pairs.forEach { (key, value) -> add(key, value) } }.build())
    }
}
