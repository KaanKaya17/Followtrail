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
        val form = WebLoginContract.normalize(body())
        val fields = (0 until form.size).associate { form.name(it) to form.value(it) }
        assertEquals(WebLoginContract.DOCUMENT, fields["doc_id"])
        assertEquals("synthetic.account", JSONObject(fields.getValue("variables")).getJSONObject("input").getString("identifier"))
    }
    @Test fun unknownAndSocialDocumentsOrAdditionalQueryCannotPass() {
        for (raw in listOf(body(doc="123"), body(operation="FollowMutation"), body()+"&query=mutation", body()+"&doc_id=123",
            body()+"&variables=%7B%7D", "%xx=broken", "x".repeat(65537)))
            assertThrows(Exception::class.java) { WebLoginContract.normalize(raw) }
    }
    @Test fun plaintextZeroEncryptionAndExtraVariableOperationsCannotPass() {
        for (password in listOf("plain-password", "#PWD_BROWSER:0:1790000000:"+"A".repeat(96))) {
            val v=variables(); v.getJSONObject("input").getJSONObject("password").put("sensitive_string_value", password)
            assertThrows(Exception::class.java) { WebLoginContract.normalize(body(v)) }
        }
        val extra=variables().put("query", "mutation"); assertThrows(Exception::class.java) { WebLoginContract.normalize(body(extra)) }
        val nested=variables(); nested.getJSONObject("input").put("action", "follow")
        assertThrows(Exception::class.java) { WebLoginContract.normalize(body(nested)) }
        val actor=variables(); actor.getJSONObject("input").put("actor_id", "123")
        assertThrows(Exception::class.java) { WebLoginContract.normalize(body(actor)) }
    }
}
