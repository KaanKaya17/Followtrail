package dev.kaankaya.instagramtracker

import okhttp3.Request
import okhttp3.FormBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WebSecurityViewContractTest {
    private fun body(apc: Any="synthetic-apc",device: Any=JSONObject.NULL,doc: String=AuthPlatformCodeEntry.DOCUMENT,operation: String=AuthPlatformCodeEntry.OPERATION,
        extra: Boolean=false): String {
        val variables=JSONObject().put("apc",apc).put("device_id",device).apply {if(extra)put("code","never-send")}
        return "doc_id=$doc&fb_api_req_friendly_name=$operation&variables="+java.net.URLEncoder.encode(variables.toString(),"UTF-8")
    }
    @Test fun onlyTheObservedViewAndCurrentPageContextProduceATransportCapability() {
        for(path in WebGraphqlForm.PATHS) {
            val authorized=WebSecurityViewContract.authorize(body(),path,"synthetic-apc")
            val request=authorized.apply(Request.Builder()).build()
            assertTrue(WebPlatformRequestPolicy.allowed(request))
            assertFalse(WebPlatformRequestPolicy.allowed(request.newBuilder().url(authorized.url+"?other=1").build()))
            assertFalse(WebPlatformRequestPolicy.allowed(request.newBuilder().post(FormBody.Builder().add("doc_id","other").build()).build()))
            assertFalse(authorized.toString().contains("synthetic-apc"))
        }
    }
    @Test fun unknownDocumentsMutationsExtraFieldsAndContextMismatchAreRejected() {
        for(raw in listOf(body(apc="other-context"),body(device="different-device"),body(doc="123"),body(operation="FollowMutation"),body(extra=true),
            body()+"&query=mutation",body()+"&doc_id="+AuthPlatformCodeEntry.DOCUMENT))
            assertThrows(IllegalArgumentException::class.java){WebSecurityViewContract.authorize(raw,"/api/graphql","synthetic-apc")}
        assertThrows(IllegalArgumentException::class.java){WebSecurityViewContract.authorize(body(),"/api/graphql",null)}
        assertThrows(IllegalArgumentException::class.java){WebSecurityViewContract.authorize(body(),"/api/v1/web/friendships/123/follow/","synthetic-apc")}
    }
    @Test fun codeViewResponseCannotBeConfusedWithALoginResultOrAnotherRoot() {
        assertTrue(WebSecurityViewContract.validData(JSONObject("""{"data":{"xfb_auth_platform_enter_code_content":{"title":"fixture"},"xfb_auth_platform_afad_polling_interval":null}}""")))
        for(raw in listOf("{}","""{"data":{"caa_login_web":{"ig_authenticated":true}}}""",
            """{"data":{"xfb_auth_platform_enter_code_content":null}}""",
            """{"data":{"xfb_auth_platform_enter_code_content":{}},"errors":[{"message":"private-error"}]}"""))
            assertFalse(WebSecurityViewContract.validData(JSONObject(raw)))
    }
}
