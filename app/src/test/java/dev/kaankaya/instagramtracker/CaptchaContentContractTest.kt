package dev.kaankaya.instagramtracker

import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Synthetic schemas model the boundary; they do not establish the current live module's exact fields. */
class CaptchaContentContractTest {
    private val root = "xfb_auth_platform_anti_scripting_content"
    private fun schema() = JSONObject().put("document",AuthPlatformCaptchaContent.DOCUMENT)
        .put("operation",AuthPlatformCaptchaContent.OPERATION).put("kind","query").put("incomplete",false)
        .put("arguments",JSONArray())
        .put("rootFields",JSONArray(listOf(root)))
    private fun body() = "doc_id=${AuthPlatformCaptchaContent.DOCUMENT}&fb_api_req_friendly_name=${AuthPlatformCaptchaContent.OPERATION}&variables=%7B%7D"
    @Test fun exactQueryRequiresCurrentContextAndMatchingCompleteRuntimeQuerySchema() {
        for(path in WebGraphqlForm.PATHS) {
            val capability = WebSecurityViewContract.authorize(body(),path,"private-context",schema())
            val request = capability.apply(Request.Builder()).build()
            assertTrue(WebPlatformRequestPolicy.allowed(request))
            assertFalse(WebPlatformRequestPolicy.allowed(request.newBuilder().post(FormBody.Builder().add("other","never").build()).build()))
            assertFalse(WebPlatformRequestPolicy.allowed(request.newBuilder().url("https://example.com/api/graphql").build()))
            assertFalse(capability.toString().contains("private-context"))
            assertTrue(capability.validData(JSONObject().put("data",JSONObject().put(root,JSONObject()))))
        }
    }
    @Test fun missingWrongIncompleteOrMutationSchemasCannotAuthorizeTheCaptchaPost() {
        for(proof in listOf(null,JSONObject(),schema().put("document","123"),schema().put("operation","OtherQuery"),
            schema().put("kind","mutation"),schema().put("incomplete",true),schema().put("rootFields",JSONArray(listOf("caa_login_web"))),
            schema().put("arguments",JSONArray(listOf("apc","code"))))) {
            assertThrows(IllegalArgumentException::class.java) { WebSecurityViewContract.authorize(body(),"/api/graphql","private-context",proof) }
        }
        assertThrows(IllegalArgumentException::class.java) { WebSecurityViewContract.authorize(body(),"/api/graphql",null,schema()) }
        assertThrows(IllegalArgumentException::class.java) { WebSecurityViewContract.authorize(body().replace("%7B%7D","%7B%22apc%22%3A%22unexpected%22%7D"),"/api/graphql","private-context",schema()) }
    }
    @Test fun captchaResponseCannotBeReplacedWithLoginCodeViewOrUnexpectedGraphqlFields() {
        val capability=WebSecurityViewContract.authorize(body(),"/api/graphql","private-context",schema())
        for(data in listOf(JSONObject().put(root,JSONObject.NULL),JSONObject().put("caa_login_web",JSONObject()),
            JSONObject().put(root,JSONObject()).put("other",true))) assertFalse(capability.validData(JSONObject().put("data",data)))
        assertFalse(capability.validData(JSONObject().put("data",JSONObject().put(root,JSONObject())).put("errors",JSONArray().put(JSONObject()))))
    }
    @Test fun captchaAndUnknownAuthPlatformDiagnosticsKeepOnlyBoundedNamesAndTypes() {
        val facts=WebDocumentFacts.inspect(body()).json().toString()
        assertTrue(facts.contains("variableTypes"));assertFalse(facts.contains("private-context"))
        val unknown="doc_id=123&fb_api_req_friendly_name=AuthPlatformUnknownMutation&variables="+
            java.net.URLEncoder.encode("""{"input":{"code":"private-code","context":"private-context"}}""","UTF-8")
        val metadata=WebDocumentFacts.inspect(unknown).json().toString()
        assertTrue(metadata.contains("input.code"));assertFalse(metadata.contains("private-"))
        assertThrows(IllegalArgumentException::class.java) { WebSecurityViewContract.authorize(unknown,"/api/graphql","private-context",schema()) }
    }
}
