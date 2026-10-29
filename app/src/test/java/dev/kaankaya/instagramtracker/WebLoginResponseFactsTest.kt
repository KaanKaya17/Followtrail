package dev.kaankaya.instagramtracker

import okhttp3.Cookie
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WebLoginResponseFactsTest {
    @Test fun loginDocumentBusinessResultStaysDistinctFromHttpAndGraphqlTransport() {
        val body=JSONObject("""{"data":{"caa_login_web":{"error_code":103,"ig_authenticated":false,"recaptcha_needed":false,"two_factor_result":null,"redirect_uri":null,"crypted_uid":"private-user","error_message":{"text":"The login information you entered is incorrect. private-secret"}}}}""")
        val facts=WebLoginResponseFacts.inspect(200,body,emptyList(),true,1)
        assertTrue(facts.caaResultPresent);assertEquals(false,facts.authenticated);assertEquals(listOf(103),facts.errorCodes)
        assertEquals(WebLoginResponseFacts.MessageClass.BAD_LOGIN_INFORMATION,facts.messageClass)
        assertEquals(false,facts.twoFactorPayloadPresent);assertEquals(false,facts.captchaNeeded)
        assertFalse(facts.json().toString().contains("private-"))
    }
    @Test fun preservesNumericFailuresWithoutKeepingMessagesIdentityOrCookieValues() {
        val body=JSONObject("""{"data":null,"errors":[{"code":1357001,"api_error_code":"300","message":"private-secret user@example.invalid"}]}""")
        val cookie=Cookie.Builder().name("sessionid").value("private-cookie").domain("www.instagram.com").build()
        val facts=WebLoginResponseFacts.inspect(200,body,listOf(cookie),true,1)
        assertEquals(listOf(1357001,300),facts.errorCodes);assertEquals(WebLoginResponseFacts.Shape.NULL,facts.dataShape)
        assertTrue(facts.sessionCookieReceived);assertFalse(facts.ownerCookieReceived);assertNull(facts.cookiesApplied)
        val saved=facts.json().toString()
        for(secret in listOf("private-secret","example.invalid","private-cookie"))assertFalse(saved.contains(secret))
        assertFalse(facts.json().has("message"));assertFalse(facts.json().has("error_message"))
    }
    @Test fun malformedJsonUnknownCodesAndExpiredCookiesRemainExplicit() {
        val unknown=WebLoginResponseFacts.inspect(200,null,emptyList(),false,1)
        assertFalse(unknown.jsonParsed);assertEquals(WebLoginResponseFacts.Shape.UNKNOWN,unknown.dataShape)
        val invalid=WebLoginResponseFacts.inspect(200,JSONObject("""{"error":"not-a-numeric-code","errors":[{"code":999999999999999}]}"""),emptyList(),true,1)
        assertTrue(invalid.codesIncomplete);assertTrue(invalid.errorCodes.isEmpty())
        val expired=Cookie.Builder().name("sessionid").value("expired").domain("www.instagram.com").expiresAt(1).build()
        assertFalse(WebLoginResponseFacts.inspect(200,JSONObject(),listOf(expired),true,2).sessionCookieReceived)
    }
}
