package dev.kaankaya.instagramtracker

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

object LoginBrowserResources {
    fun restrictWorkers() {
        // Service-worker fetches have their own path outside WebViewClient.
        android.webkit.ServiceWorkerController.getInstance().serviceWorkerWebSettings.blockNetworkLoads = true
    }
    fun landing(): WebResourceResponse = WebResourceResponse("text/html", "UTF-8", ByteArrayInputStream(
        "<!doctype html><meta name=viewport content='width=device-width'><title>InstagramTracker</title><p>InstagramTracker</p>".toByteArray()))
    fun blocked(): WebResourceResponse = WebResourceResponse("text/plain", "UTF-8", 403, "Blocked by local read policy", emptyMap(), ByteArrayInputStream(byteArrayOf()))
}
