package dev.kaankaya.instagramtracker

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WebLoginChallengeTest {
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
