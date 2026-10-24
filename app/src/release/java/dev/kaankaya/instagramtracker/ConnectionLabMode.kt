package dev.kaankaya.instagramtracker

import android.app.Activity
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse

object ConnectionLabMode {
    fun enabled(activity: Activity) = false
    fun origin(preview: Boolean) = ProbePolicy.ORIGIN
    fun startUrl(preview: Boolean) = ProbePolicy.ORIGIN + "/"
    fun intercept(activity: Activity, request: WebResourceRequest, preview: Boolean): WebResourceResponse? = null
}
