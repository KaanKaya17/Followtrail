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
    fun relayClient(activity: Activity, enabled: Boolean): Call.Factory {
        if (!enabled || !activity.intent.getBooleanExtra("web_auth_fixture", false)) return WebRequestClients.create(activity)
        return Call.Factory { request -> object : Call by PrivateReadTransport.client().newCall(request) {
            override fun execute(): Response {
                val captcha = activity.intent.getBooleanExtra("web_captcha_fixture", false)
                val builder = Response.Builder().request(request).protocol(Protocol.HTTP_2).code(200).message("fixture")
                val rejected = activity.intent.getBooleanExtra("web_rejected_fixture", false)
                val code = activity.intent.getBooleanExtra("web_code_fixture", false)
                if (!captcha && !rejected && !code) builder.addHeader("Set-Cookie", "sessionid=synthetic-session; Path=/; Secure; HttpOnly")
                    .addHeader("Set-Cookie", "ds_user_id=123; Path=/; Secure")
                val body = if (rejected) """{"data":{"caa_login_web":{"ig_authenticated":false,"recaptcha_needed":false,"error_code":1348009,"error_message":{"text":"The login information you entered is incorrect."}}}}"""
                    else if (code) """{"data":{"caa_login_web":{"ig_authenticated":false,"recaptcha_needed":false,"two_factor_result":"synthetic-pending-code"}}}"""
                    else if (captcha) org.json.JSONObject("""{"data":{"caa_login_web":{"ig_authenticated":false,"recaptcha_needed":true,"redirect_uri":"/challenge/action/synthetic-token/","error_message":{"text":"The login information you entered is incorrect."}}}}""").apply {
                        if (activity.intent.getBooleanExtra("web_unsupported_captcha_fixture",false)) getJSONObject("data").getJSONObject("caa_login_web").put("redirect_uri","/unsupported-verification/synthetic-secret/")
                    }.toString()
                    else """{"data":{"caa_login_web":{"ig_authenticated":true,"recaptcha_needed":false,"redirect_uri":"/accounts/onetap/"}}}"""
                return builder.body(body.toResponseBody()).build()
            }
        } }
    }
    fun enabled(activity: Activity) = activity.intent.getBooleanExtra("offline_web_login", false)
    fun intercept(request: WebResourceRequest, enabled: Boolean, activity: Activity): WebResourceResponse? {
        if (!enabled) return null
        if (LoginBrowserPolicy.landing(request.url.toString())) return LoginBrowserResources.landing()
        if (activity.intent.getBooleanExtra("web_bootstrap_fixture", false)) {
            if (request.url.path in setOf("/ajax/bootloader-endpoint/", "/ajax/bulk-route-definitions/"))
                return WebResourceResponse("application/json", "UTF-8", ByteArrayInputStream("{}".toByteArray()))
            if (request.url.path == "/accounts/login/") {
                val html = """<!doctype html><meta name="viewport" content="width=device-width"><script>
                    Promise.all([fetch('/ajax/bootloader-endpoint/'),fetch('/ajax/bulk-route-definitions/',{method:'POST',body:'synthetic-route-fields'})])
                    .then(rs=>{if(rs.every(r=>r.status===200))document.body.innerHTML='<input name="email"><input name="pass" type="password">';});
                    </script><body></body>"""
                return WebResourceResponse("text/html", "UTF-8", 200, "fixture", emptyMap(), ByteArrayInputStream(html.toByteArray()))
            }
        }
        if(request.url.path?.matches(Regex("/api/v1/users/[1-9][0-9]{0,29}/info/")) == true) {
            val status=activity.intent.getIntExtra("web_browser_status",200)
            val body="""{"status":"ok","user":{"pk":"123","username":"web.demo","follower_count":0,"following_count":7}}"""
            return WebResourceResponse("application/json","UTF-8",status,"fixture",emptyMap(),ByteArrayInputStream(body.toByteArray()))
        }
        val html = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><script>history.replaceState({},"","/");</script><h2>tracker-web-login-fixture</h2><input name="username"><input name="password" type="password"></body></html>"""
        return WebResourceResponse("text/html", "UTF-8", 200, "fixture", emptyMap(), ByteArrayInputStream(html.toByteArray()))
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
