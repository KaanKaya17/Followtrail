package dev.kaankaya.instagramtracker

import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject

/** Opaque values from the first approved request on this consented page; never persisted or logged. */
internal class CaptchaBinding(private val pageContext: String,private val encryptedContext: String,private val device: String) {
    fun matches(page: String,encrypted: String,id: String) = page == pageContext && encrypted == encryptedContext && id == device
    override fun toString() = "CaptchaBinding(redacted)"
}

/** Only the two CAPTCHA operations observed on the user's verification page. No generic mutation authority. */
internal object WebCaptchaContract {
    fun authorize(raw: String,path: String,pageContext: String?,proof: JSONObject?,binding: CaptchaBinding?): WebCaptchaAuthorization {
        require(path in WebGraphqlForm.PATHS && !pageContext.isNullOrEmpty())
        val pairs = WebGraphqlForm.parse(raw)
        val operation = AuthPlatformVerification.find(pairs["doc_id"],pairs["fb_api_req_friendly_name"])
        require(operation in AuthPlatformVerification.captchaOperations && operation != null && proof != null)
        val schema = AuthPlatformSchema.inspect(proof.toString(),operation.document,operation.name,"mutation")
        require(schema.opt("available") == true && schema.opt("incomplete") == false)
        val variables = JSONObject(requireNotNull(pairs["variables"]))
        val expected = if(operation == AuthPlatformVerification.captchaRender)
            setOf("render_succeeded","encrypted_context","failure_reason","device_id") else
            setOf("captcha_response","encrypted_context","device_id")
        fun names(key: String) = schema.getJSONArray(key).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
        require(names("arguments") == expected && variables.keys().asSequence().toSet() == expected)
        val roots = names("rootFields")
        require(roots.isNotEmpty() && roots.all { it.startsWith("xfb_auth_platform_") })
        fun text(key: String,max: Int,min: Int=1) = (variables.get(key) as? String)?.also { value ->
            require(value.length in min..max && value.all { it.code in 32..126 })
        } ?: throw IllegalArgumentException("Invalid verification input")
        val encrypted = text("encrypted_context",8192);val device = text("device_id",1024,0)
        if(operation == AuthPlatformVerification.captchaRender) {
            require(variables.get("render_succeeded") is Boolean && variables.get("failure_reason") == JSONObject.NULL)
        } else text("captcha_response",16384)
        require(binding == null || binding.matches(pageContext,encrypted,device))
        pairs["variables"] = variables.toString()
        return WebCaptchaAuthorization("https://www.instagram.com$path",WebGraphqlForm.body(pairs),operation,
            binding ?: CaptchaBinding(pageContext,encrypted,device),roots)
    }
}

internal class WebCaptchaAuthorization(val url: String,val body: FormBody,val operation: AuthPlatformVerification.Operation,
    val binding: CaptchaBinding,private val roots: Set<String>) {
    fun apply(builder: Request.Builder) = builder.url(url).post(body).tag(WebCaptchaAuthorization::class.java,this)
    fun validData(json: JSONObject): Boolean {
        val data = json.optJSONObject("data") ?: return false
        return data.keys().asSequence().toSet() == roots &&
            (!json.has("errors") || json.isNull("errors") || json.optJSONArray("errors")?.length() == 0)
    }
    override fun toString() = "WebCaptchaAuthorization(redacted)"
}
