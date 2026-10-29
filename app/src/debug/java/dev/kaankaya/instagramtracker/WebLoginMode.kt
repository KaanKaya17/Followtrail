package dev.kaankaya.instagramtracker

import android.app.Activity
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import java.io.ByteArrayInputStream

/** Complete offline fixture used only by the isolated debug UI tests. */
object WebLoginMode {
    fun enabled(activity: Activity) = activity.intent.getBooleanExtra("offline_web_login", false)
    fun intercept(request: WebResourceRequest, enabled: Boolean, activity: Activity): WebResourceResponse? {
        if (!enabled) return null
        if(request.url.path?.matches(Regex("/api/v1/users/[1-9][0-9]{0,29}/info/")) == true) {
            val status=activity.intent.getIntExtra("web_browser_status",200)
            val body="""{"status":"ok","user":{"pk":"123","username":"web.demo","follower_count":0,"following_count":7}}"""
            return WebResourceResponse("application/json","UTF-8",status,"fixture",emptyMap(),ByteArrayInputStream(body.toByteArray()))
        }
        val html = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><script>history.replaceState({},"","/");</script><h2>tracker-web-login-fixture</h2><input name="username"><input name="password" type="password"></body></html>"""
        return WebResourceResponse("text/html", "UTF-8", ByteArrayInputStream(html.toByteArray()))
    }
    fun attempt(activity: Activity, gate: RequestGate, enabled: Boolean): WebConnectionAttempt {
        if (!enabled) return WebConnectionAttempt(activity, gate)
        val status = activity.intent.getIntExtra("web_login_status", 200)
        val factory = Call.Factory { request -> object : Call by PrivateReadTransport.client().newCall(request) {
            private var cancelled = false
            override fun request() = request
            override fun execute() = Response.Builder().request(request).protocol(Protocol.HTTP_2).code(status).message("fixture")
                .body("""{"status":"ok","user":{"pk":"123","username":"web.demo","follower_count":0,"following_count":7}}""".toResponseBody()).build()
            override fun enqueue(responseCallback: Callback) = error("Synchronous fixture")
            override fun cancel() { cancelled = true }
            override fun isExecuted() = true
            override fun isCanceled() = cancelled
            override fun timeout() = Timeout()
            override fun clone(): Call = error("No replay")
        } }
        return WebConnectionAttempt(activity, gate, client = factory)
    }
}
