package dev.kaankaya.instagramtracker

import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WebVerificationContractTest {
    private fun body(edit: (JSONObject) -> Unit = {}): String {
        val input=JSONObject().put("actor_id","0").put("client_mutation_id","1").put("code","123456")
            .put("device_id",JSONObject.NULL).put("encrypted_ap_context","synthetic-context").apply(edit)
        return "doc_id=25017097917894476&fb_api_req_friendly_name=useAuthPlatformSubmitCodeMutation&variables="+
            java.net.URLEncoder.encode(JSONObject().put("input",input).toString(),"UTF-8")
    }
    @Test fun codeSubmissionHasItsOwnContextBoundTransportCapability() {
        for (path in WebGraphqlForm.PATHS) {
            val capability=WebVerificationContract.authorizeCode(body(),path,"synthetic-context")
            val request=capability.apply(Request.Builder()).build()
            assertTrue(WebPlatformRequestPolicy.allowed(request))
            assertFalse(WebPlatformRequestPolicy.allowed(request.newBuilder().url(capability.url+"?other=1").build()))
            assertFalse(WebPlatformRequestPolicy.allowed(request.newBuilder().post(FormBody.Builder().add("code","999999").build()).build()))
            assertFalse(capability.toString().contains("123456"));assertFalse(capability.toString().contains("synthetic-context"))
        }
    }
    @Test fun foreignContextCodeShapeExtraFieldsAndSocialDocumentsNeverGetAuthorized() {
        for(raw in listOf(body{it.put("encrypted_ap_context","foreign")},body{it.put("actor_id","7")},body{it.put("device_id","foreign")},
            body{it.put("code",123456)},body{it.put("code","bad-code")},body{it.put("follow",true)},
            body().replace("useAuthPlatformSubmitCodeMutation","FollowMutation"),body()+"&doc_id=123"))
            assertThrows(IllegalArgumentException::class.java){WebVerificationContract.authorizeCode(raw,"/api/graphql","synthetic-context")}
        assertThrows(IllegalArgumentException::class.java){WebVerificationContract.authorizeCode(body(),"/api/graphql",null)}
        assertThrows(IllegalArgumentException::class.java){WebVerificationContract.authorizeCode(body(),"/api/v1/friendships/1/follow/","synthetic-context")}
    }
    @Test fun codeResponseIsSeparateFromTheInitialLoginAndCodeView() {
        assertTrue(WebVerificationContract.validData(JSONObject("""{"data":{"xfb_auth_platform_submit_code":{}}}""")))
        for(raw in listOf("{}","""{"data":{"caa_login_web":{}}}""","""{"data":{"xfb_auth_platform_submit_code":null}}""",
            """{"data":{"xfb_auth_platform_submit_code":{},"other":{}}}""","""{"data":{"xfb_auth_platform_submit_code":{}},"errors":[{}]}"""))
            assertFalse(WebVerificationContract.validData(JSONObject(raw)))
    }
}
