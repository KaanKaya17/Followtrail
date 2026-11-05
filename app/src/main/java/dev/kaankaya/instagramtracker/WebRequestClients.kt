package dev.kaankaya.instagramtracker

import android.content.Context
import android.os.Build
import android.os.ext.SdkExtensions
import okhttp3.Call
import okhttp3.Request

/** Select by local capability before sending. No application fallback; login uploads cannot rewind. */
internal object WebRequestClients {
    @Volatile private var client: Call.Factory? = null
    fun platformAvailable(): Boolean = (Build.VERSION.SDK_INT >= 34 ||
        Build.VERSION.SDK_INT >= 31 && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 7) &&
        try { Class.forName("android.net.http.HttpEngine", false, WebRequestClients::class.java.classLoader); true }
        catch (_: ClassNotFoundException) { false }
    @Synchronized fun create(context: Context): Call.Factory = client ?: (
        if (platformAvailable()) PlatformWebClient(context.applicationContext) else PrivateReadTransport.client()
    ).also { client = it }
}

/** The login producer owns the approved immutable body; transport consumes that same object. */
internal class WebLoginAuthorization(val body: okhttp3.FormBody) {
    fun apply(builder: Request.Builder): Request.Builder = builder.post(body).tag(WebLoginAuthorization::class.java, this)
}

internal object WebPlatformRequestPolicy {
    fun allowed(request: Request): Boolean {
        val u=request.url
        if (!u.isHttps || u.host!="www.instagram.com" || u.port!=443 || u.username.isNotEmpty() || u.password.isNotEmpty()) return false
        if (request.method=="POST") {
            val captcha = request.tag(WebCaptchaAuthorization::class.java)
            if(captcha!=null)return u.toString()==captcha.url && captcha.body===request.body
            val code = request.tag(WebCodeSubmissionAuthorization::class.java)
            if (code != null) return u.toString() == code.url && code.body === request.body
            val security=request.tag(WebSecurityViewAuthorization::class.java)
            if(security!=null)return u.toString()==security.url && security.body===request.body
            return u.toString()==WebLoginContract.URL && request.tag(WebLoginAuthorization::class.java)?.body?.let {it===request.body}==true
        }
        if (request.method!="GET" || request.body!=null) return false
        if (AccountRequestPolicy.readAllowed("GET",u.toString())) return true
        // Credential-free, opt-in public transport smoke; account readers still reject this route.
        return u.toString()=="https://www.instagram.com/accounts/login/" &&
            request.headers.names().all { it.lowercase() in setOf("accept","user-agent") }
    }
}
