package dev.kaankaya.instagramtracker

import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** IDs and input names/types are observed; mutation response roots here are deliberately synthetic. */
class WebCaptchaContractTest {
    private fun values(operation: AuthPlatformVerification.Operation) = JSONObject()
        .put("encrypted_context","private-encrypted-context").put("device_id","private-device")
        .apply { if(operation == AuthPlatformVerification.captchaRender) put("render_succeeded",true).put("failure_reason",JSONObject.NULL)
            else put("captcha_response","private-human-result") }
    private fun proof(operation: AuthPlatformVerification.Operation) = JSONObject().put("document",operation.document)
        .put("operation",operation.name).put("kind","mutation").put("incomplete",false)
        .put("arguments",JSONArray(values(operation).keys().asSequence().toList()))
        .put("rootFields",JSONArray(listOf("xfb_auth_platform_synthetic_captcha_result")))
    private fun body(operation: AuthPlatformVerification.Operation,variables: JSONObject=values(operation)) =
        "doc_id=${operation.document}&fb_api_req_friendly_name=${operation.name}&variables="+java.net.URLEncoder.encode(variables.toString(),"UTF-8")
    private fun authorize(operation: AuthPlatformVerification.Operation,binding: CaptchaBinding?=null,variables: JSONObject=values(operation)) =
        WebCaptchaContract.authorize(body(operation,variables),"/api/graphql","current-consented-page",proof(operation),binding)
    @Test fun bothObservedOperationsBindToTheSamePageAndOpaquePairWithoutGuessingItEqualsApc() {
        val render=authorize(AuthPlatformVerification.captchaRender)
        val submit=authorize(AuthPlatformVerification.captchaSubmit,render.binding)
        for(capability in listOf(render,submit)) {
            val request=capability.apply(Request.Builder()).build();assertTrue(WebPlatformRequestPolicy.allowed(request))
            assertFalse(WebPlatformRequestPolicy.allowed(request.newBuilder().url("https://other.invalid/api/graphql").build()))
            assertFalse(WebPlatformRequestPolicy.allowed(request.newBuilder().post(FormBody.Builder().add("other","never").build()).build()))
            assertFalse(capability.toString().contains("private-"));assertFalse(capability.binding.toString().contains("private-"))
            assertTrue(capability.validData(JSONObject().put("data",JSONObject().put("xfb_auth_platform_synthetic_captcha_result",JSONObject.NULL))))
            assertFalse(capability.validData(JSONObject().put("data",JSONObject().put("caa_login_web",JSONObject()))))
        }
    }
    @Test fun mismatchedBindingsExtraFieldsTypesAndMissingSchemaAreRejectedBeforeSending() {
        val render=authorize(AuthPlatformVerification.captchaRender);val operation=AuthPlatformVerification.captchaSubmit
        for(invalid in listOf(values(operation).put("device_id","other"),values(operation).put("encrypted_context","other"),
            values(operation).put("captcha_response",JSONObject.NULL),values(operation).put("like",true)))
            assertThrows(IllegalArgumentException::class.java) { authorize(operation,render.binding,invalid) }
        assertThrows(IllegalArgumentException::class.java) {
            WebCaptchaContract.authorize(body(operation),"/api/graphql","another-page",proof(operation),render.binding)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WebCaptchaContract.authorize(body(operation),"/api/graphql","current-consented-page",null,null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            authorize(AuthPlatformVerification.captchaRender,variables=values(AuthPlatformVerification.captchaRender).put("render_succeeded","true"))
        }
    }
    @Test fun arbitraryDocumentsAndQuerySchemasCannotGrantCaptchaMutationAuthority() {
        val operation=AuthPlatformVerification.captchaSubmit
        for(schema in listOf(proof(operation).put("kind","query"),proof(operation).put("incomplete",true),
            proof(operation).put("rootFields",JSONArray(listOf("follow_user"))))) {
            assertThrows(IllegalArgumentException::class.java) { WebCaptchaContract.authorize(body(operation),"/api/graphql","page",schema,null) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            WebCaptchaContract.authorize(body(operation).replace(operation.document,"123"),"/api/graphql","page",proof(operation),null)
        }
    }
}
