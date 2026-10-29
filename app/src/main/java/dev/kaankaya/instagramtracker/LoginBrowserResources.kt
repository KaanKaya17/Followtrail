package dev.kaankaya.instagramtracker

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

object LoginBrowserResources {
    // Desktop presentation uses this installed engine's version, never a pinned official-app identity.
    fun desktopUserAgent(current: String): String {
        val chrome = Regex("Chrome/[0-9]+(?:\\.[0-9]+){3}").find(current)?.value ?: return current
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) $chrome Safari/537.36"
    }
    fun restrictWorkers() {
        // Service-worker fetches have their own path outside WebViewClient.
        android.webkit.ServiceWorkerController.getInstance().serviceWorkerWebSettings.blockNetworkLoads = true
    }
    fun landing(): WebResourceResponse = WebResourceResponse("text/html", "UTF-8", ByteArrayInputStream(
        "<!doctype html><meta name=viewport content='width=device-width'><title>InstagramTracker</title><p>InstagramTracker</p>".toByteArray()))
    fun blocked(): WebResourceResponse = WebResourceResponse("text/plain", "UTF-8", 403, "Blocked by local read policy", emptyMap(), ByteArrayInputStream(byteArrayOf()))
}
