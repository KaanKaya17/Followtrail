package dev.kaankaya.instagramtracker

import android.app.Activity
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject

/** Debug-only transport. Every response is synthetic; no sockets or real account credentials. */
object PreviewMode {
    fun enabled(activity: Activity) = activity.intent.getBooleanExtra("offline_preview", false)
    fun seed(enabled: Boolean): SavedAccount? {
        if (!enabled) return null
        val now = System.currentTimeMillis()
        val session = Session("100", "ds_user_id=100; sessionid=offline-demo", "offline-demo")
        val people = listOf("deniz.demo", "ada.demo").mapIndexed { index, name ->
            TrackedPerson((0..11).map { day -> Profile((200 + index).toString(), name,
                1200L + index * 700 + day * 13, 340L + day * 2, now - (11 - day) * 86400000L, 42L + day / 3) },
                "Bu bir örnek not. Gerçek hesap verisi değildir.", if (index == 0) "Arkadaşlar" else "İçerik üreticileri")
        }
        return SavedAccount(session, Profile("100", "benim.demo", 875, 412, now), people)
    }
    fun api(gate: RequestGate, enabled: Boolean): InstagramApi = if (!enabled) InstagramApi(gate)
        else InstagramApi(gate) { url -> DemoConnection(url) }
    private class DemoConnection(url: URL) : HttpsURLConnection(url) {
        private val name = url.query?.substringAfter("username=") ?: ""
        override fun getResponseCode() = if (name == "bulunamadi.demo") 404 else 200
        override fun getInputStream(): InputStream {
            val id = if (name.isNotBlank()) (300 + name.hashCode().toLong().let { kotlin.math.abs(it) } % 100000).toString()
                else url.path.split('/').getOrNull(4) ?: "100"
            val username = when (id) { "100" -> "benim.demo"; "200" -> "deniz.demo"; "201" -> "ada.demo"; else -> name.ifBlank { "ornek.demo" } }
            val followers = if (id == "200") 1356 else if (id == "201") 2056 else 988
            val user = if (name.isNotBlank()) JSONObject().put("id", id).put("username", username)
                .put("edge_followed_by", JSONObject().put("count", followers))
                .put("edge_follow", JSONObject().put("count", 365)).put("edge_owner_to_timeline_media", JSONObject().put("count", 46))
            else JSONObject().put("pk", id).put("username", username).put("follower_count", followers)
                .put("following_count", 365).put("media_count", 46)
            val body = JSONObject().put("status", "ok").apply {
                if (name.isNotBlank()) put("data", JSONObject().put("user", user)) else put("user", user)
            }
            return ByteArrayInputStream(body.toString().toByteArray())
        }
        override fun getErrorStream() = ByteArrayInputStream(byteArrayOf())
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun connect() = Unit
        override fun getCipherSuite() = "offline-demo"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate>? = null
    }
}
