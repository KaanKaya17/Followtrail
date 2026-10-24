package dev.kaankaya.instagramtracker

import org.json.JSONObject

/** Owns server error interpretation for both native transports; no raw messages reach storage. */
object InstagramResponsePolicy {
    enum class Restriction(val label: String) {
        HTTP_429("HTTP 429"), WAIT("Instagram bekleme yanıtı"), FEEDBACK("Instagram eylem kısıtlaması")
    }
    data class Issue(val kind: ApiFailure.Kind, val restriction: Restriction? = null)
    fun issue(status: Int, body: JSONObject?): Issue? {
        if (status == 429) return Issue(ApiFailure.Kind.LIMITED, Restriction.HTTP_429)
        if (status in 300..399 || status == 401) return Issue(ApiFailure.Kind.SESSION_EXPIRED)
        when (body?.optString("message")) {
            "login_required" -> return Issue(ApiFailure.Kind.SESSION_EXPIRED)
            "challenge_required", "checkpoint_required", "consent_required" -> return Issue(ApiFailure.Kind.VERIFICATION)
            "feedback_required" -> return Issue(ApiFailure.Kind.LIMITED, Restriction.FEEDBACK)
            "please_wait_a_few_minutes", "Please wait a few minutes before you try again." -> return Issue(ApiFailure.Kind.LIMITED, Restriction.WAIT)
        }
        return when {
            status == 403 -> Issue(ApiFailure.Kind.VERIFICATION)
            status == 404 -> Issue(ApiFailure.Kind.NOT_FOUND)
            status !in 200..299 -> Issue(ApiFailure.Kind.CONNECTION)
            else -> null
        }
    }
    fun checkHeaders(gate: RequestGate, status: Int, retry: String?) {
        if (status == 429 || status == 401 || status in 300..399) checkBody(gate, status, null, retry)
    }
    fun checkBody(gate: RequestGate, status: Int, body: JSONObject?, retry: String?) {
        val issue = issue(status, body) ?: return
        issue.restriction?.let { gate.limited(retry, reason = it.label) }
        throw ApiFailure(issue.kind)
    }
}
