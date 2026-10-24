package dev.kaankaya.instagramtracker

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProfileBoundaryTest {
    private fun body() = JSONObject("""{"status":"ok","user":{"pk":"123","username":"test","follower_count":0,"following_count":7}}""")
    @Test fun realZeroAndOwnIdentity() {
        val profile = ProfileParser.parse(body().toString(), "123", 1000)
        assertEquals(0L, profile.followers); assertEquals(7L, profile.following)
    }
    @Test fun missingMalformedAndOtherAccountRejected() {
        for (value in listOf<Any>("4", true, -1, 1.5, JSONObject.NULL)) {
            val body = body(); body.getJSONObject("user").put("follower_count", value)
            assertThrows(ApiFailure::class.java) { ProfileParser.parse(body.toString(), "123", 1000) }
        }
        val absent = body(); absent.getJSONObject("user").remove("following_count")
        assertThrows(ApiFailure::class.java) { ProfileParser.parse(absent.toString(), "123", 1000) }
        val failure = assertThrows(ApiFailure::class.java) { ProfileParser.parse(body().toString(), "456", 1000) }
        assertEquals(ApiFailure.Kind.WRONG_ACCOUNT, failure.kind)
    }
    @Test fun cookieBoundaryRejectsInjectionAndMissingIdentity() {
        val session = SessionCookies.fromHeader("ds_user_id=123; sessionid=synthetic; csrftoken=test; unwanted=secret", "test-UA")!!
        assertEquals("123", session.userId)
        assertFalse(session.cookie.contains("unwanted"))
        assertNull(SessionCookies.fromHeader("sessionid=synthetic", "test"))
        assertNull(SessionCookies.fromHeader("ds_user_id=../1; sessionid=synthetic", "test"))
        assertNull(SessionCookies.fromHeader("ds_user_id=123; sessionid=synthetic\r\nX-Fake: yes", "test"))
        assertNull(SessionCookies.fromHeader("ds_user_id=123; sessionid=synthetic", "bad\nUA"))
    }
    @Test fun navigationIsHttpsAndDomainBounded() {
        assertTrue(NavigationPolicy.isInstagram("https://www.instagram.com/accounts/login/"))
        assertTrue(NavigationPolicy.isLoginResource("https://scontent.cdninstagram.com/image"))
        for (url in listOf("http://instagram.com/", "https://instagram.com.evil.example/", "https://evilinstagram.com/",
            "https://instagram.com@evil.example/", "file:///data/data/", "intent://instagram/", "https://instagram.com:8080/")) {
            assertFalse(url, NavigationPolicy.isInstagram(url))
        }
        assertFalse(NavigationPolicy.isLoginResource("https://analytics.example/"))
    }
    @Test fun serverWaitIsRespectedAndUnknownWaitUsesLocalPause() {
        val now = 1700000000000L
        assertEquals(now + 120000L, RetryPolicy.until("120", now))
        assertEquals(1700000040000L, RetryPolicy.until("Tue, 14 Nov 2023 22:14:00 GMT", now))
        for (header in listOf(null, "garbage", "99999999999999999999999999", "0", "-1", "Tue, 14 Nov 2023 22:14:00 GMT ignored")) {
            assertEquals(now + RetryPolicy.FALLBACK_PAUSE_MS, RetryPolicy.until(header, now))
        }
    }
}
