package dev.kaankaya.instagramtracker

import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class InstagramApi {
    @Volatile private var active: HttpsURLConnection? = null
    fun cancel() { active?.disconnect() }
    fun read(session: Session): Profile {
        val connection = URL("https://www.instagram.com/api/v1/users/${session.userId}/info/")
            .openConnection() as HttpsURLConnection
        active = connection
        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.useCaches = false
            connection.setRequestProperty("Cookie", session.cookie)
            connection.setRequestProperty("User-Agent", session.userAgent)
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("X-IG-App-ID", "936619743392459")
            connection.setRequestProperty("X-Requested-With", "XMLHttpRequest")
            connection.setRequestProperty("Referer", "https://www.instagram.com/")
            SessionCookies.csrf(session)?.let { connection.setRequestProperty("X-CSRFToken", it) }
            val status = connection.responseCode
            if (status == 429) throw ApiFailure(ApiFailure.Kind.LIMITED)
            if (status in 300..399 || status == 401) throw ApiFailure(ApiFailure.Kind.SESSION_EXPIRED)
            val input = if (status in 200..299) connection.inputStream else connection.errorStream
            val data = input?.use { it.readNBytesBounded(1024 * 1024) } ?: byteArrayOf()
            val text = String(data, Charsets.UTF_8)
            val json = try { JSONObject(text) } catch (_: Exception) { null }
            when (json?.optString("message")) {
                "login_required" -> throw ApiFailure(ApiFailure.Kind.SESSION_EXPIRED)
                "challenge_required", "checkpoint_required", "consent_required" -> throw ApiFailure(ApiFailure.Kind.VERIFICATION)
                "feedback_required", "please_wait_a_few_minutes" -> throw ApiFailure(ApiFailure.Kind.LIMITED)
            }
            if (status == 403) throw ApiFailure(ApiFailure.Kind.VERIFICATION)
            if (status !in 200..299) throw ApiFailure(ApiFailure.Kind.CONNECTION)
            return ProfileParser.parse(text, session.userId, System.currentTimeMillis())
        } catch (failure: ApiFailure) { throw failure }
        catch (_: java.io.IOException) { throw ApiFailure(ApiFailure.Kind.CONNECTION) }
        catch (_: Exception) { throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE) }
        finally { connection.disconnect(); if (active === connection) active = null }
    }
    private fun java.io.InputStream.readNBytesBounded(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) return output.toByteArray()
            if (output.size() > limit - count) throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
            output.write(buffer, 0, count)
        }
    }
}
