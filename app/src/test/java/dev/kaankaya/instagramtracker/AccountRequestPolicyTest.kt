package dev.kaankaya.instagramtracker

import okhttp3.FormBody
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test

class AccountRequestPolicyTest {
    @Test fun bootstrapEndpointsHaveExactMethodsAndDoNotGrantGenericPostAccess() {
        assertTrue(LoginBrowserPolicy.resource("GET", "https://www.instagram.com/ajax/bootloader-endpoint/?modules=login"))
        assertTrue(LoginBrowserPolicy.resource("POST", "https://www.instagram.com/ajax/bulk-route-definitions/"))
        for (method in listOf("GET", "PUT", "DELETE", "PATCH"))
            assertFalse(LoginBrowserPolicy.resource(method, "https://www.instagram.com/ajax/bulk-route-definitions/"))
        assertFalse(LoginBrowserPolicy.resource("POST", "https://www.instagram.com/ajax/bootloader-endpoint/"))
        for (url in listOf("https://instagram.com/ajax/bulk-route-definitions/", "https://www.instagram.com/ajax/bulk-route-definitions/extra",
            "https://www.instagram.com/ajax/%62ulk-route-definitions/", "https://www.instagram.com.evil.invalid/ajax/bulk-route-definitions/",
            "https://www.instagram.com/api/graphql", "https://www.instagram.com/ajax/bz"))
            assertFalse(url, LoginBrowserPolicy.resource("POST", url))
        assertFalse(AccountRequestPolicy.readAllowed("POST", "https://www.instagram.com/ajax/bulk-route-definitions/", true))
    }
    @Test fun desktopPresentationKeepsInstalledChromeVersion() {
        val ua = LoginBrowserResources.desktopUserAgent("Mozilla/5.0 (Linux; Android 13; device; wv) AppleWebKit/537.36 Version/4.0 Chrome/156.0.8078.47 Mobile Safari/537.36")
        assertTrue(ua.contains("Chrome/156.0.8078.47")); assertFalse(ua.contains("Android")); assertFalse(ua.contains("Mobile"))
        assertEquals("Unknown engine", LoginBrowserResources.desktopUserAgent("Unknown engine"))
    }
    @Test fun browserDiagnosticsUseFixedCategoriesWithoutGrantingNewResourceAccess() {
        val cases = listOf(
            Triple("GET", "https://www.instagram.com/accounts/login/", true),
            Triple("GET", "https://www.instagram.com/api/v1/web/data/shared_data/", true),
            Triple("GET", "https://static.cdninstagram.com/rsrc.php/login.js", true),
            Triple("POST", "https://www.instagram.com/api/v1/web/accounts/login/ajax/", true),
            Triple("POST", "https://www.instagram.com/api/graphql", false),
            Triple("GET", "https://www.instagram.com/api/v1/feed/timeline/", false),
            Triple("GET", "https://www.instagram.com.evil.invalid/login.js", false),
            Triple("POST", "https://www.instagram.com/api/v1/web/friendships/123/follow/", false)
        )
        for ((method, url, expected) in cases) assertEquals(url, expected, LoginBrowserPolicy.resourceDecision(method, url).allowed)
        assertEquals(LoginBrowserPolicy.ResourceKind.GRAPHQL, LoginBrowserPolicy.resourceDecision("POST", "https://www.instagram.com/api/graphql").kind)
        assertEquals(LoginBrowserPolicy.ResourceKind.AUTH, LoginBrowserPolicy.resourceDecision("POST", "https://www.instagram.com/api/v1/web/accounts/login/ajax/").kind)
    }
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
        val account = List(RequestDefaults.ACCOUNT_HOURLY) { now }
        assertEquals(0L,RequestBudgetPolicy.nextAt(account,now))
        assertEquals(now+RequestBudgetPolicy.HOUR,RequestBudgetPolicy.nextAt(account,now,account))
        assertEquals(now+RequestBudgetPolicy.HOUR,RequestBudgetPolicy.nextAt(List(RequestDefaults.DEVICE_HOURLY){now},now))
        assertEquals(0L,RequestBudgetPolicy.nextAt(List(RequestDefaults.DEVICE_HOURLY){now-RequestBudgetPolicy.HOUR},now))
        assertEquals(now+22*RequestBudgetPolicy.HOUR,RequestBudgetPolicy.nextAt(List(RequestDefaults.DEVICE_DAILY){now-2*RequestBudgetPolicy.HOUR},now))
        assertEquals(now+RequestBudgetPolicy.HOUR,RequestBudgetPolicy.nextAt(account,now-1000,account))
        assertEquals(listOf(now),RequestBudgetPolicy.reserve(listOf(now-RequestBudgetPolicy.DAY),now))
        assertThrows(IllegalArgumentException::class.java){RequestBudgetPolicy.nextAt(listOf(now,now-1),now)}
        assertThrows(IllegalArgumentException::class.java){RequestBudgetPolicy.nextAt(List(RequestBudgetPolicy.MAX_STORED+1){now},now)}
    }
}
