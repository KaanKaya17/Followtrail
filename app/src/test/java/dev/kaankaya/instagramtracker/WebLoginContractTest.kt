package dev.kaankaya.instagramtracker

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.URLEncoder

class WebLoginContractTest {
    private fun variables() = JSONObject().put("scale", 1).put("input", JSONObject()
        .put("actor_id", "0").put("identifier", "synthetic.account").put("app", "instagram").put("credential_type", "password")
        .put("use_uid_to_login", false).put("passkey_payload", JSONObject.NULL)
        .put("password", JSONObject().put("sensitive_string_value", "#PWD_BROWSER:5:1790000000:" + "A".repeat(96)))
        .put("enc_password", JSONObject().put("sensitive_string_value", "#PWD_BROWSER:5:1790000000:" + "A".repeat(96))))
    private fun body(v: JSONObject = variables(), doc: String = WebLoginContract.DOCUMENT,
        operation: String = WebLoginContract.OPERATION) = "doc_id=$doc&fb_api_req_friendly_name=$operation&variables=" + URLEncoder.encode(v.toString(), "UTF-8")
    @Test fun exactLoginDocumentRoundTripsOnlyEncryptedCredentials() {
        val form = WebLoginContract.authorize(body()).body
        val fields = (0 until form.size).associate { form.name(it) to form.value(it) }
        assertEquals(WebLoginContract.DOCUMENT, fields["doc_id"])
        assertEquals("synthetic.account", JSONObject(fields.getValue("variables")).getJSONObject("input").getString("identifier"))
    }
    @Test fun unknownAndSocialDocumentsOrAdditionalQueryCannotPass() {
        for (raw in listOf(body(doc="123"), body(operation="FollowMutation"), body()+"&query=mutation", body()+"&doc_id=123",
            body()+"&variables=%7B%7D", "%xx=broken", "x".repeat(65537)))
            assertThrows(Exception::class.java) { WebLoginContract.authorize(raw) }
    }
    @Test fun plaintextZeroEncryptionAndExtraVariableOperationsCannotPass() {
        for (password in listOf("plain-password", "#PWD_BROWSER:0:1790000000:"+"A".repeat(96))) {
            val v=variables(); v.getJSONObject("input").getJSONObject("password").put("sensitive_string_value", password)
            assertThrows(Exception::class.java) { WebLoginContract.authorize(body(v)) }
        }
        val extra=variables().put("query", "mutation"); assertThrows(Exception::class.java) { WebLoginContract.authorize(body(extra)) }
        val nested=variables(); nested.getJSONObject("input").put("action", "follow")
        assertThrows(Exception::class.java) { WebLoginContract.authorize(body(nested)) }
        val actor=variables(); actor.getJSONObject("input").put("actor_id", "123")
        assertThrows(Exception::class.java) { WebLoginContract.authorize(body(actor)) }
    }
    @Test fun transportConsumesTheExactApprovedBodyAndRejectsReplacementOrSocialTargets() {
        val approved=WebLoginContract.authorize(body())
        val request=approved.apply(okhttp3.Request.Builder().url(WebLoginContract.URL)).build()
        assertTrue(WebPlatformRequestPolicy.allowed(request))
        assertFalse(WebPlatformRequestPolicy.allowed(request.newBuilder().post(WebLoginContract.authorize(body()).body).build()))
        assertFalse(WebPlatformRequestPolicy.allowed(request.newBuilder().url("https://www.instagram.com/api/v1/friendships/123/follow/").build()))
        assertFalse(WebPlatformRequestPolicy.allowed(okhttp3.Request.Builder().url(WebLoginContract.URL).post(approved.body).build()))
        assertFalse(WebPlatformRequestPolicy.allowed(request.newBuilder().url("https://evil.test/api/graphql").build()))
    }
    @Test fun publicTransportSmokeCannotCarryCredentialsOrQueryRedirects() {
        val request=okhttp3.Request.Builder().url("https://www.instagram.com/accounts/login/").header("User-Agent","test").build()
        assertTrue(WebPlatformRequestPolicy.allowed(request))
        assertFalse(AccountRequestPolicy.readAllowed("GET",request.url.toString()))
        assertFalse(WebPlatformRequestPolicy.allowed(request.newBuilder().header("Cookie","sessionid=synthetic").build()))
        assertFalse(WebPlatformRequestPolicy.allowed(request.newBuilder().header("X-CSRFToken","synthetic").build()))
        assertFalse(WebPlatformRequestPolicy.allowed(request.newBuilder().url(request.url.newBuilder().addQueryParameter("next","/").build()).build()))
    }
    @Test fun transportKeepsTheFixedWebReadHostAndMethodBoundary() {
        assertTrue(WebPlatformRequestPolicy.allowed(okhttp3.Request.Builder().url("https://www.instagram.com/api/v1/users/123/info/").build()))
        for(url in listOf("https://i.instagram.com/api/v1/users/123/info/","http://www.instagram.com/api/v1/users/123/info/",
            "https://www.instagram.com/direct/inbox/","https://www.instagram.com/api/graphql"))
            assertFalse(WebPlatformRequestPolicy.allowed(okhttp3.Request.Builder().url(url).build()))
    }
}
