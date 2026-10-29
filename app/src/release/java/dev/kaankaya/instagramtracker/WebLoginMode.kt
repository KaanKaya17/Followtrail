package dev.kaankaya.instagramtracker

import android.app.Activity
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse

/** Production cannot enable the offline WebView or a synthetic account response. */
object WebLoginMode {
    fun enabled(activity: Activity) = false
    fun intercept(request: WebResourceRequest, enabled: Boolean, activity: Activity): WebResourceResponse? = null
    fun attempt(activity: Activity, gate: RequestGate, enabled: Boolean) = WebConnectionAttempt(activity, gate)
    fun relayClient(activity: Activity, enabled: Boolean): okhttp3.Call.Factory = PrivateReadTransport.client()
}
