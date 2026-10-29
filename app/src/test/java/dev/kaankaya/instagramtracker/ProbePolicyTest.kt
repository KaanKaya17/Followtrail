package dev.kaankaya.instagramtracker

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProbePolicyTest {
    @Test fun exactHttpsOriginAndReadyPageAreRequired() {
        assertTrue(ProbePolicy.usablePage("https://www.instagram.com/"))
        assertTrue(ProbePolicy.usablePage("https://www.instagram.com/accounts/onetap/"))
        for (url in listOf("https://www.instagram.com/accounts/login/", "https://www.instagram.com/accounts/onetap/other", "https://www.instagram.com/challenge", "https://www.instagram.com/checkpoint/",
            "https://www.instagram.com.evil.test/", "https://www.instagram.com@evil.test/", "http://www.instagram.com/", "https://www.instagram.com:444/")) assertFalse(url, ProbePolicy.usablePage(url))
    }
    @Test fun webViewJsonEnvelopeIsBoundedAndDoesNotDefaultInvalidResults() {
        val result = JSONObject().put("stage", "done").put("status", 429).put("reason", "limited")
        assertEquals(429, ProbePolicy.decode(JSONObject.quote(result.toString())).getInt("status"))
        assertThrows(Exception::class.java) { ProbePolicy.decode("null") }
        assertThrows(Exception::class.java) { ProbePolicy.decode("x".repeat(8193)) }
        assertEquals(ApiFailure.Kind.INVALID_RESPONSE, ProbePolicy.failure(JSONObject()))
        assertEquals(ApiFailure.Kind.LIMITED, ProbePolicy.failure(result))
        assertEquals(ApiFailure.Kind.VERIFICATION, ProbePolicy.failure(JSONObject().put("status", 403)))
        assertNull(ProbePolicy.failure(JSONObject().put("status", 200).put("reason", "ok")))
    }
}
