package dev.kaankaya.instagramtracker

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ResponsePolicyTest {
    @Test fun http429WaitAndFeedbackKeepDistinctSignalsWithoutServerText() {
        val cases = listOf(429 to (null to InstagramResponsePolicy.Restriction.HTTP_429),
            400 to ("feedback_required" to InstagramResponsePolicy.Restriction.FEEDBACK),
            200 to ("please_wait_a_few_minutes" to InstagramResponsePolicy.Restriction.WAIT))
        for ((status, data) in cases) {
            val body = data.first?.let { JSONObject().put("message", it) }
            val issue = InstagramResponsePolicy.issue(status, body)!!
            assertEquals(ApiFailure.Kind.LIMITED, issue.kind); assertEquals(data.second, issue.restriction)
        }
    }
    @Test fun authVerificationAndUnexpectedFailuresDoNotBecomeRateLimits() {
        for ((status, body, expected) in listOf(
            Triple(403, JSONObject().put("message", "login_required"), ApiFailure.Kind.SESSION_EXPIRED),
            Triple(400, JSONObject().put("message", "challenge_required"), ApiFailure.Kind.VERIFICATION),
            Triple(404, JSONObject(), ApiFailure.Kind.NOT_FOUND), Triple(500, JSONObject(), ApiFailure.Kind.CONNECTION))) {
            val issue = InstagramResponsePolicy.issue(status, body)!!
            assertEquals(expected, issue.kind); assertNull(issue.restriction)
        }
        assertNull(InstagramResponsePolicy.issue(200, JSONObject().put("message", "unrecognized")))
    }
}
