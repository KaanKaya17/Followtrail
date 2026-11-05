package dev.kaankaya.instagramtracker

import org.json.JSONObject

/** Observed verification documents. These identities grant no network authority. */
internal object AuthPlatformVerification {
    data class Operation(val document: String, val name: String)
    val submitCode = Operation("25017097917894476", "useAuthPlatformSubmitCodeMutation")
    val deviceApproval = Operation("24626773746933809", "AuthPlatformApproveFromAnotherDevicePollingMutation")
    val captchaRender = Operation("28372617359013204", "AuthPlatformReCaptchaRenderOutcomeMutation")
    val captchaSubmit = Operation("9960725864052044", "AuthPlatformReCaptchaSubmitCaptchaMutation")
    val captchaOperations = listOf(captchaRender,captchaSubmit)
    val operations = listOf(submitCode, deviceApproval) + captchaOperations
    fun find(document: String?, name: String?) = operations.singleOrNull { it.document == document && it.name == name }
}

/** Names and kinds only; discard every unselected field and all argument values. */
internal object AuthPlatformSchema {
    fun inspect(raw: String, document: String, operation: String, kind: String): JSONObject {
        val unavailable = JSONObject().put("available", false)
        if (raw.length > 2048) return unavailable
        return try {
            val value = JSONObject(raw)
            if (value.opt("document") != document || value.opt("operation") != operation || value.opt("kind") != kind) return unavailable
            val incomplete = value.get("incomplete"); require(incomplete is Boolean)
            fun names(key: String): org.json.JSONArray {
                val input = value.getJSONArray(key); require(input.length() <= 8)
                return org.json.JSONArray((0 until input.length()).map { index ->
                    val name = input.get(index); require(name is String && AuthPlatformCodeEntry.fieldName.matches(name)); name
                }.distinct())
            }
            JSONObject().put("available", true).put("document", document).put("operation", operation).put("kind", kind)
                .put("arguments", names("arguments")).put("rootFields", names("rootFields")).put("incomplete", incomplete)
        } catch (_: Exception) { unavailable }
    }
}
