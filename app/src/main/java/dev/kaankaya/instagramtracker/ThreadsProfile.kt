package dev.kaankaya.instagramtracker

import okhttp3.Call
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.json.JSONArray
import org.json.JSONTokener
import java.util.concurrent.TimeUnit

/** Public Threads page only: no Instagram session, cookies, redirects, retry or inferred absence. */
object ThreadsProfile {
    data class Result(val status: String, val http: Int? = null, val retryAfter: String? = null)
    private val client = OkHttpClient.Builder().cookieJar(CookieJar.NO_COOKIES).followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).connectTimeout(5, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).callTimeout(15, TimeUnit.SECONDS).build()
    fun parse(html: String, username: String): Boolean {
        var examined = 0
        val scripts = Regex("""<script\b[^>]*\btype\s*=\s*["']application/(?:ld\+)?json["'][^>]*>([\s\S]*?)</script\s*>""", RegexOption.IGNORE_CASE)
        for (script in scripts.findAll(html)) {
            val stack = java.util.ArrayDeque<Any>()
            try { stack.add(JSONTokener(script.groupValues[1]).nextValue()) } catch (_: Exception) { continue } catch (_: StackOverflowError) { return false }
            while (stack.isNotEmpty()) {
                if (++examined > 30000) return false
                when (val value = stack.removeLast()) {
                    is JSONObject -> {
                        if (value.optString("@type") == "ProfilePage") {
                            val person = value.optJSONObject("mainEntity")
                            val name = ProfileLinks.threadsName(person?.optString("url"))
                            if (name.equals(username, true) && person?.optString("alternateName")?.removePrefix("@").equals(username, true)) return true
                        }
                        for (key in value.keys()) value.opt(key)?.takeIf { it is JSONObject || it is JSONArray }?.let(stack::add)
                    }
                    is JSONArray -> for (i in 0 until value.length()) value.opt(i)?.takeIf { it is JSONObject || it is JSONArray }?.let(stack::add)
                }
            }
        }
        return false
    }
    fun probe(url: String, callReady: (Call) -> Unit): Result {
        val username = ProfileLinks.threadsName(url) ?: return Result("unknown")
        val call = client.newCall(Request.Builder().url("https://www.threads.com/@$username")
            .header("Accept", "text/html").header("User-Agent", "InstagramTracker (Threads profile check)").get().build())
        callReady(call)
        return try { call.execute().use { response ->
            when {
                response.code in listOf(404, 410) -> Result("not_found", response.code)
                response.code != 200 -> Result("unknown", response.code, response.header("Retry-After"))
                response.header("Content-Type")?.lowercase()?.contains("text/html") != true -> Result("unknown", response.code)
                else -> {
                    val output = java.io.ByteArrayOutputStream()
                    val input = response.body.byteStream(); val buffer = ByteArray(65536)
                    while (true) {
                        val count = input.read(buffer); if (count < 0) break
                        if (output.size() + count > 2 * 1024 * 1024) return Result("unknown", response.code)
                        output.write(buffer, 0, count)
                    }
                    Result(if (parse(output.toString(Charsets.UTF_8.name()), username)) "visible" else "unknown", response.code)
                }
            }
        } } catch (_: java.io.IOException) { Result("unknown") }
    }
}
