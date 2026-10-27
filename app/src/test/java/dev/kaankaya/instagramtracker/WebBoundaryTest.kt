package dev.kaankaya.instagramtracker

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WebBoundaryTest {
    private fun state() = WebSessionCodec.fromHeader("sessionid=synthetic%3Asession; ds_user_id=123; csrftoken=csrf; ig_did=device; unknown=discard", "Synthetic WebView")
    @Test fun credentialsAreBoundedAllowlistedAndNeverCopiedIntoTheAccountReference() {
        val state = state()
        assertEquals(state, WebSessionCodec.decode(WebSessionCodec.encode(state)))
        assertFalse(state.cookies.containsKey("unknown"))
        assertEquals("", state.reference().cookie); assertNull(state.reference().mobileBinding)
        assertEquals("web:${state.binding}", state.reference().connectionBinding)
        assertFalse(state.toString().contains("synthetic"))
        for (raw in listOf("sessionid=x", "sessionid=x; ds_user_id=true", "sessionid=x; ds_user_id=123\r\nHost: other",
            "sessionid=x; sessionid=other; ds_user_id=123", "sessionid=" + "a".repeat(17000) + "; ds_user_id=123"))
            assertThrows(Exception::class.java) { WebSessionCodec.fromHeader(raw, "ua") }
        assertThrows(Exception::class.java) { WebSessionCodec.fromHeader("sessionid=x; ds_user_id=123", "ua\nCookie:bad") }
    }
    @Test fun webRequestsUseOnlyTheFixedFirstPartyOriginWithoutMobileCredentials() {
        val state = state()
        val requests = listOf(WebRequests.profile(state, "123"), WebRequests.username(state, "someone.demo"),
            WebRequests.list(state, "456", FollowDirection.FOLLOWERS, "opaque+/=?&next=https://foreign.invalid"),
            WebRequests.list(state, "456", FollowDirection.FOLLOWING, null))
        requests.forEach {
            assertEquals("https", it.url.scheme); assertEquals("www.instagram.com", it.url.host); assertEquals(443, it.url.port)
            assertEquals("1217981644879628", it.header("X-IG-App-ID")); assertEquals("csrf", it.header("X-CSRFToken"))
            assertNull(it.header("Authorization")); assertNull(it.body); assertEquals("GET", it.method)
        }
        assertEquals("opaque+/=?&next=https://foreign.invalid", requests[2].url.queryParameter("max_id"))
        assertEquals("/api/v1/friendships/456/followers/", requests[2].url.encodedPath)
        assertEquals("50", requests[2].url.queryParameter("count"))
        assertThrows(Exception::class.java) { WebRequests.profile(state, "../accounts") }
        assertThrows(Exception::class.java) { WebRequests.username(state, "x?redirect=evil") }
    }
    @Test fun responseCookieRotationPreservesIdentityAndRejectsLogoutOrAccountSwitch() {
        val state = state()
        val next = state.updated(mapOf("Set-Cookie" to listOf("sessionid=rotated; Path=/; Secure",
            "csrftoken=newcsrf; Path=/; Secure", "mid=foreign; Domain=foreign.invalid; Path=/",
            "rur=narrow; Path=/unrelated/"), "x-ig-set-www-claim" to listOf("newclaim")), 1000)
        assertEquals(state.reference(), next.reference())
        assertEquals("rotated", next.cookies["sessionid"]); assertEquals("newcsrf", next.cookies["csrftoken"])
        assertNull(next.cookies["mid"]); assertNull(next.cookies["rur"]); assertEquals("newclaim", next.claim)
        assertEquals(ApiFailure.Kind.WRONG_ACCOUNT, assertThrows(ApiFailure::class.java) {
            state.updated(mapOf("Set-Cookie" to listOf("ds_user_id=456; Path=/")), 1000)
        }.kind)
        assertEquals(ApiFailure.Kind.SESSION_EXPIRED, assertThrows(ApiFailure::class.java) {
            state.updated(mapOf("Set-Cookie" to listOf("sessionid=gone; Max-Age=0; Path=/")), 1000)
        }.kind)
    }
    @Test fun accountV5KeepsConnectionKindsSeparateAndReadsOlderAccounts() {
        val state = state()
        val profile = Profile("123", "synthetic", 0, 7, 1000)
        val saved = SavedAccount(state.reference(), profile)
        val encoded = AccountCodec.encode(saved)
        assertEquals(saved, AccountCodec.decode(encoded))
        assertFalse(encoded.contains("synthetic%3A")); assertFalse(encoded.contains("csrftoken"))
        val poisoned = JSONObject(encoded).put("mobileBinding", "a".repeat(32))
        assertThrows(Exception::class.java) { AccountCodec.decode(poisoned.toString()) }
        val legacy = SavedAccount(Session("123", "sessionid=old; ds_user_id=123", "ua"), profile)
        val oldJson = JSONObject(AccountCodec.encode(legacy)).put("version", 4).toString()
        assertEquals(legacy, AccountCodec.decode(oldJson))
        val mobile = Session("123", "", "ua", mobileBinding = state.binding)
        assertNotEquals(mobile.connectionBinding, state.reference().connectionBinding)
        assertFalse(FollowSnapshot("123", mobile.connectionBinding, "456").matches(state.reference(), "456"))
    }
}
