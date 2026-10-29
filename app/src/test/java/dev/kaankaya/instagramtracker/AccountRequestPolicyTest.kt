package dev.kaankaya.instagramtracker

import okhttp3.FormBody
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test

class AccountRequestPolicyTest {
    @Test fun readPathsAllowProfilesAndBothListsOnly() {
        for (path in listOf("users/123/info/","users/qa.person/usernameinfo/","friendships/123/followers/","friendships/123/following/")) {
            val url="https://i.instagram.com/api/v1/$path"
            assertTrue(url,AccountRequestPolicy.readAllowed("GET",url))
            assertFalse(AccountRequestPolicy.readAllowed("POST",url))
            assertFalse(AccountRequestPolicy.readAllowed("GET",url,true))
        }
        for(path in listOf("friendships/create/123/","media/123/like/","direct_v2/threads/","accounts/logout/","unknown/","users/123%2finfo/"))
            assertFalse(AccountRequestPolicy.readAllowed("GET","https://i.instagram.com/api/v1/$path"))
        assertFalse(AccountRequestPolicy.readAllowed("GET","https://i.instagram.com.evil.invalid/api/v1/users/123/info/"))
    }
    @Test fun deviceRegistrationHasAnExactDocumentAndScopedAuthenticationException() {
        fun registration(id:String="124930351917786857261002920888",extra:Boolean=false):Request {
            val body=FormBody.Builder().add("client_doc_id",id).add("fb_api_req_friendly_name","IGUSDIDRegistrationMutation")
            if(extra)body.add("query","mutation unknown")
            return Request.Builder().url("https://b.i.instagram.com/graphql_www").post(body.build()).build()
        }
        assertTrue(AccountRequestPolicy.loginAllowed(registration(),PhoneLoginOperation.DEVICE))
        assertFalse(AccountRequestPolicy.loginAllowed(registration("unknown"),PhoneLoginOperation.DEVICE))
        assertFalse(AccountRequestPolicy.loginAllowed(registration(extra=true),PhoneLoginOperation.DEVICE))
        assertFalse(AccountRequestPolicy.readAllowed("POST",registration().url.toString(),true))
        assertFalse(AccountRequestPolicy.loginAllowed(Request.Builder().url("https://i.instagram.com/api/v1/friendships/create/123/")
            .post(FormBody.Builder().build()).build(),PhoneLoginOperation.PASSWORD))
    }
    @Test fun webBrowserAllowsOnlyAuthenticationPagesAndReadResources() {
        assertTrue(LoginBrowserPolicy.navigation("https://www.instagram.com/accounts/login/"))
        assertTrue(LoginBrowserPolicy.resource("GET","https://static.cdninstagram.com/static/login.js"))
        assertTrue(LoginBrowserPolicy.resource("POST","https://www.instagram.com/api/v1/web/accounts/login/ajax/"))
        assertTrue(LoginBrowserPolicy.resource("POST","https://www.instagram.com/challenge/test/"))
        for(path in listOf("direct/inbox/","stories/qa/","qa.person/","accounts/password/change/"))
            assertFalse(LoginBrowserPolicy.navigation("https://www.instagram.com/$path"))
        for(path in listOf("graphql/query/","api/graphql","api/v1/web/friendships/123/follow/","api/v1/media/123/like/","api/v1/direct_v2/threads/broadcast/text/","accounts/edit/"))
            assertFalse(path,LoginBrowserPolicy.resource("POST","https://www.instagram.com/$path"))
        assertFalse(LoginBrowserPolicy.resource("POST","https://static.cdninstagram.com/mutation"))
        assertFalse(LoginBrowserPolicy.resource("GET","https://www.instagram.com/api/v1/feed/timeline/"))
        assertTrue(LoginBrowserPolicy.landing("https://www.instagram.com/"))
        assertTrue(LoginBrowserPolicy.landing("https://www.instagram.com/accounts/onetap/"))
    }
    @Test fun rollingBudgetHonorsHourDayBoundariesAndClockRollback() {
        val now=2_000_000_000_000L
        assertEquals(now+RequestBudgetPolicy.HOUR,RequestBudgetPolicy.nextAt(List(120){now},now))
        assertEquals(0L,RequestBudgetPolicy.nextAt(List(120){now-RequestBudgetPolicy.HOUR},now))
        assertEquals(now+22*RequestBudgetPolicy.HOUR,RequestBudgetPolicy.nextAt(List(1200){now-2*RequestBudgetPolicy.HOUR},now))
        assertEquals(now+RequestBudgetPolicy.HOUR,RequestBudgetPolicy.nextAt(List(120){now},now-1000))
        assertEquals(listOf(now),RequestBudgetPolicy.reserve(listOf(now-RequestBudgetPolicy.DAY),now))
        assertThrows(IllegalArgumentException::class.java){RequestBudgetPolicy.nextAt(listOf(now,now-1),now)}
        assertThrows(IllegalArgumentException::class.java){RequestBudgetPolicy.nextAt(List(1201){now},now)}
    }
}
