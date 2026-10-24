package dev.kaankaya.instagramtracker

import android.app.Activity
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/** Complete offline origin: no request from this fixture falls through to a network. */
object ConnectionLabMode {
    @Volatile var delayedResponseReady: java.util.concurrent.CountDownLatch? = null
    @Volatile var delayedRequestEntered: java.util.concurrent.CountDownLatch? = null
    @Volatile var releaseDelayedResponse: java.util.concurrent.CountDownLatch? = null
    const val FIXTURE_ORIGIN = "https://tracker-fixture.invalid"
    fun enabled(activity: Activity) = activity.intent.getBooleanExtra("offline_lab", false)
    fun origin(preview: Boolean) = if (preview) FIXTURE_ORIGIN else ProbePolicy.ORIGIN
    fun startUrl(preview: Boolean) = origin(preview) + "/"
    fun intercept(activity: Activity, request: WebResourceRequest, preview: Boolean): WebResourceResponse? {
        if (!preview) return null
        val path = request.url.path
        if (path == "/api/v1/users/web_profile_info/" && activity.intent.getBooleanExtra("fixture_delay", false)) {
            delayedRequestEntered?.countDown()
            try { check(releaseDelayedResponse?.await(5, java.util.concurrent.TimeUnit.SECONDS) != false) }
            finally { delayedResponseReady?.countDown() }
        }
        val status = if (path == "/api/v1/users/web_profile_info/") activity.intent.getIntExtra("fixture_status", 200) else 200
        val body = if (path == "/api/v1/users/web_profile_info/") {
            if (status == 200) """{"status":"ok","data":{"user":{"id":"100","username":"lab.demo","edge_followed_by":{"count":875},"edge_follow":{"count":412}}}}"""
            else """{"message":"synthetic"}"""
        } else """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body style="background:#10131b;color:#c0f5dc;font:20px sans-serif;padding:24px"><h2>Örnek tarayıcı oturumu</h2><p>Bu sayfa Instagram'a bağlanmaz. Üstteki Tek okumayı başlat düğmesi gerçek WebView fetch akışını sentetik yanıtla sınar.</p></body></html>"""
        return WebResourceResponse(if (path == "/api/v1/users/web_profile_info/") "application/json" else "text/html", "UTF-8", status, if (status == 200) "OK" else "Synthetic Error",
            if (status == 429) mapOf("Retry-After" to "7200") else emptyMap(), ByteArrayInputStream(body.toByteArray(Charsets.UTF_8)))
    }
}
