package dev.kaankaya.instagramtracker

import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject

/** Exact observed verification views, bound to the current consented page's APC. */
internal object WebSecurityViewContract {
    fun authorize(raw: String,path: String,context: String?,schema: JSONObject? = null): WebSecurityViewAuthorization {
        require(path in WebGraphqlForm.PATHS && context!=null)
        val pairs=WebGraphqlForm.parse(raw)
        val captcha = AuthPlatformCaptchaContent.matches(pairs["doc_id"],pairs["fb_api_req_friendly_name"])
        val shape = if (captcha) AuthPlatformCaptchaContent.shape(schema) else {
            require(pairs["doc_id"]==AuthPlatformCodeEntry.DOCUMENT && pairs["fb_api_req_friendly_name"]==AuthPlatformCodeEntry.OPERATION)
            AuthPlatformCaptchaContent.Shape(setOf("apc","device_id"),emptySet())
        }
        val variables=JSONObject(requireNotNull(pairs["variables"]))
        require(variables.keys().asSequence().toSet()==shape.arguments)
        val apc=variables.get("apc")
        require(apc is String && apc.length in 1..8192 && apc.none {it.code<32||it.code==127} && apc==context)
        if("device_id" in shape.arguments) require(variables.get("device_id")==JSONObject.NULL)
        pairs["variables"]=variables.toString()
        return WebSecurityViewAuthorization("https://www.instagram.com$path",WebGraphqlForm.body(pairs),requireNotNull(pairs["doc_id"]),shape.roots)
    }
    fun validData(json: JSONObject): Boolean {
        val data=json.optJSONObject("data") ?: return false
        return data.keys().asSequence().all {it in setOf("xfb_auth_platform_enter_code_content","xfb_auth_platform_afad_polling_interval")} &&
            data.has("xfb_auth_platform_enter_code_content") && !data.isNull("xfb_auth_platform_enter_code_content") &&
            (!json.has("errors") || json.isNull("errors") || json.optJSONArray("errors")?.length()==0)
    }
}

/** Immutable URL/body identity is checked again by the platform transport. */
internal class WebSecurityViewAuthorization(val url: String,val body: FormBody,val document: String,private val roots: Set<String>) {
    fun apply(builder: Request.Builder)=builder.url(url).post(body).tag(WebSecurityViewAuthorization::class.java,this)
    fun validData(json: JSONObject) = if(document==AuthPlatformCaptchaContent.DOCUMENT) AuthPlatformCaptchaContent.validData(json,roots)
        else WebSecurityViewContract.validData(json)
    override fun toString()="WebSecurityViewAuthorization(redacted)"
}
