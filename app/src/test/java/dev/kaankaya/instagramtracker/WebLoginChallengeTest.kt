package dev.kaankaya.instagramtracker

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WebLoginChallengeTest {
    @Test fun observedAuthPlatformRedirectNeedsConsentAndGrantsOnlyItsExactFirstPartyGet() {
        val link="/auth_platform/private-token/?context=private-context"
        val body=JSONObject().put("data",JSONObject().put("caa_login_web",JSONObject().put("recaptcha_needed",true)
            .put("is_ig_login_recaptcha",false).put("ig_authenticated",false).put("redirect_uri",link)))
        val facts=WebLoginResponseFacts.inspect(200,body,emptyList(),true,1)
        val result=WebLoginChallenge.assess(body,facts)
        assertEquals("AUTH_PLATFORM",result.route?.name)
        val c=result.challenge!!;val url="https://www.instagram.com$link"
        assertFalse(c.resource("GET",url));assertFalse(c.navigation("GET",url))
        val redirected="https://www.instagram.com/auth_platform/next/?context=redirected-private"
        assertFalse(c.followRedirect("GET",redirected,true,true))
        c.authorize()
        assertTrue(c.resource("GET",url));assertTrue(c.navigation("GET",url))
        for (other in listOf("https://www.instagram.com/auth_platform/other-token/?context=private-context",
            "https://www.instagram.com/auth_platform/private-token/?context=other-context","https://evil.test$link",
            "https://www.instagram.com/api/graphql","https://www.instagram.com/api/v1/challenge/web/action/")) {
            assertFalse(c.resource("GET",other));assertFalse(c.resource("POST",other));assertFalse(c.navigation("GET",other))
        }
        assertFalse(c.resource("POST",url));assertFalse(c.navigation("POST",url))
        assertFalse(c.followRedirect("GET",redirected,true,false))
        assertFalse(c.followRedirect("GET",redirected,false,true))
        assertFalse(c.followRedirect("POST",redirected,true,true))
        assertFalse(c.followRedirect("GET","https://evil.test/auth_platform/next/",true,true))
        assertFalse(c.followRedirect("GET","https://www.instagram.com/api/v1/friendships/123/follow/",true,true))
        assertTrue(c.followRedirect("GET",redirected,true,true));assertTrue(c.navigation("GET",redirected))
        assertTrue(c.resource("GET",redirected));assertFalse(c.resource("POST",redirected))
        assertFalse(c.toString().contains("private-"));assertFalse(result.toString().contains("private-"))
        c.close();assertFalse(c.resource("GET",url));assertFalse(c.navigation("GET",url));assertFalse(c.resource("GET",redirected))
    }
    @Test fun redirectFamiliesDoNotAuthorizeNewPagesOrRetainIdentity() {
        val routes=mapOf("/" to "ROOT", "/accounts/login/" to "LOGIN", "/accounts/login/two_factor/" to "TWO_FACTOR",
            "/auth_platform/private-token/" to "AUTH_PLATFORM", "/checkpoint/private-token/" to "CHECKPOINT", "/other/private-token/" to "OTHER")
        for ((link,route) in routes) {
            val body=JSONObject().put("data",JSONObject().put("caa_login_web",JSONObject().put("recaptcha_needed",true).put("redirect_uri",link)))
            val facts=WebLoginResponseFacts.inspect(200,body,emptyList(),true,1)
            val result=WebLoginChallenge.assess(body,facts)
            assertEquals(route,result.route?.name);assertNull(result.challenge)
            val saved=facts.copy(challengeStatus=result.status,challengeRoute=result.route).json().toString()
            assertFalse(saved.contains("private-token"));assertFalse(saved.contains("auth_platform/"))
        }
    }
    @Test fun unavailableReasonsPreserveShapeWithoutRetainingTheLinkOrToken() {
        val cases = listOf(
            JSONObject.NULL to WebLoginChallenge.Companion.Status.MISSING_LINK,
            JSONObject().put("private-token",true) to WebLoginChallenge.Companion.Status.INVALID_TYPE,
            "" to WebLoginChallenge.Companion.Status.EMPTY_LINK,
            "https://other.invalid/private-token" to WebLoginChallenge.Companion.Status.OTHER_HOST,
            "/unrecognized/private-token/" to WebLoginChallenge.Companion.Status.UNSUPPORTED_PATH,
            "/challenge/" to WebLoginChallenge.Companion.Status.INVALID_TOKEN,
            "/challenge/a%2fb/" to WebLoginChallenge.Companion.Status.INVALID_URL,
            "/challenge/private-token/" to WebLoginChallenge.Companion.Status.SUPPORTED)
        for ((link,status) in cases) {
            val body=JSONObject().put("data",JSONObject().put("caa_login_web",JSONObject().put("recaptcha_needed",true).put("ig_authenticated",false).put("redirect_uri",link)))
            val facts=WebLoginResponseFacts.inspect(200,body,emptyList(),true,1)
            val assessment=WebLoginChallenge.assess(body,facts)
            assertEquals(status,assessment.status)
            assertFalse(assessment.toString().contains("private-token"))
            val diagnostic=facts.copy(challengeStatus=assessment.status).json().toString()
            assertFalse(diagnostic.contains("private-token"));assertFalse(diagnostic.contains("other.invalid"))
        }
    }
    private fun challenge(link: String, captcha: Boolean = true, authenticated: Boolean = false): WebLoginChallenge? {
        val body=JSONObject().put("data",JSONObject().put("caa_login_web",JSONObject()
            .put("redirect_uri",link).put("recaptcha_needed",captcha).put("ig_authenticated",authenticated)))
        return WebLoginChallenge.fromResponse(body,WebLoginResponseFacts.inspect(200,body,emptyList(),true,1))
    }
    @Test fun explicitConsentIsRequiredAndCloseRevokesOnlyThisChallenge() {
        val c=challenge("/challenge/action/synthetic-token/?context=synthetic")!!
        val read="https://www.instagram.com/api/v1/challenge/web/synthetic-token/"
        val frame="https://www.fbsbx.com/captcha/recaptcha/iframe/"
        assertFalse(c.resource("GET",read));assertFalse(c.subframe(frame))
        c.authorize()
        assertTrue(c.resource("GET",read));assertTrue(c.subframe(frame))
        assertTrue(c.resource("POST","https://www.instagram.com/api/v1/challenge/web/action/"))
        assertTrue(c.resource("GET","https://www.google.com/recaptcha/api2/anchor?k=synthetic"))
        assertTrue(c.resource("POST","https://www.google.com/recaptcha/api2/reload?k=synthetic"))
        assertTrue(c.resource("GET","https://www.gstatic.com/recaptcha/releases/synthetic/recaptcha__en.js"))
        assertFalse(c.toString().contains("token"));assertFalse(c.toString().contains("context"))
        c.close();assertFalse(c.resource("GET",read));assertFalse(c.subframe(frame))
    }
    @Test fun redirectsRequireARealCaptchaResultAndAnExactInstagramSecurityPage() {
        assertNull(challenge("/challenge/action/token/",captcha=false))
        assertNull(challenge("/challenge/action/token/",authenticated=true))
        for (url in listOf("https://evil.test/challenge/token/","//evil.test/challenge/token/",
            "https://www.instagram.com.evil.test/challenge/token/","https://user@www.instagram.com/challenge/token/",
            "http://www.instagram.com/challenge/token/","https://www.instagram.com:444/challenge/token/",
            "/accounts/login/","/challenge/","/challenge/token/#secret","/challenge/a%2fb/")) assertNull(url,challenge(url))
    }
    @Test fun consentDoesNotGrantSocialRequestsOtherChallengesOrGenericThirdPartyTraffic() {
        val c=challenge("/challenge/synthetic-token/")!!;c.authorize()
        for ((method,url) in listOf("POST" to "https://www.instagram.com/api/v1/friendships/123/follow/",
            "POST" to "https://www.instagram.com/api/graphql","GET" to "https://www.instagram.com/api/v1/challenge/web/other/",
            "GET" to "https://www.fbsbx.com/other/","POST" to "https://www.gstatic.com/recaptcha/api.js",
            "GET" to "https://www.google.com/search?q=secret","GET" to "https://www.google.com.evil.test/recaptcha/api.js",
            "GET" to "https://www.google.com/recaptcha/%2e%2e/search")) assertFalse(url,c.resource(method,url))
        assertFalse(c.subframe("https://www.instagram.com/api/v1/challenge/web/synthetic-token/"))
    }
}
