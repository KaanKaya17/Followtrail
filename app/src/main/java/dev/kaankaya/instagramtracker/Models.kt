package dev.kaankaya.instagramtracker

import org.json.JSONObject
import java.net.URI

data class Session(val userId: String, val cookie: String, val userAgent: String)
data class Profile(val userId: String, val username: String, val followers: Long, val following: Long, val observedAt: Long)
data class SavedAccount(val session: Session, val profile: Profile)

object SessionCookies {
    private val names = setOf("sessionid", "ds_user_id", "csrftoken", "mid", "ig_did", "rur")
    fun fromHeader(header: String?, userAgent: String): Session? {
        if (header.isNullOrBlank() || header.any { it.code < 32 || it.code == 127 }) return null
        val values = linkedMapOf<String, String>()
        for (part in header.split(';')) {
            val pair = part.trim().split('=', limit = 2)
            if (pair.size == 2 && pair[0] in names) values[pair[0]] = pair[1]
        }
        val id = values["ds_user_id"] ?: return null
        if (!id.matches(Regex("[1-9][0-9]{0,29}")) || values["sessionid"].isNullOrBlank()) return null
        if (userAgent.isBlank() || userAgent.any { it.code < 32 || it.code == 127 }) return null
        return Session(id, values.entries.joinToString("; ") { "${it.key}=${it.value}" }, userAgent)
    }
    fun csrf(session: Session): String? = session.cookie.split(';').map { it.trim().split('=', limit = 2) }
        .firstOrNull { it.size == 2 && it[0] == "csrftoken" }?.get(1)
}

object NavigationPolicy {
    private fun host(url: String): String? = try {
        val uri = URI(url)
        if (uri.scheme != "https" || uri.rawUserInfo != null || uri.port !in listOf(-1, 443)) null
        else uri.host?.lowercase()
    } catch (_: Exception) { null }
    private fun within(host: String, base: String) = host == base || host.endsWith(".$base")
    fun isInstagram(url: String): Boolean = host(url)?.let { within(it, "instagram.com") } == true
    fun isLoginResource(url: String): Boolean = host(url)?.let {
        within(it, "instagram.com") || within(it, "cdninstagram.com") || within(it, "fbcdn.net")
    } == true
}

object ProfileParser {
    fun parse(text: String, expectedId: String, time: Long): Profile {
        val body = JSONObject(text)
        if (body.optString("status") != "ok") throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
        val user = body.optJSONObject("user") ?: throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
        val pk = user.opt("pk")
        val id = when (pk) { is String -> pk; is Int -> pk.toString(); is Long -> pk.toString(); else -> "" }
        if (id != expectedId) throw ApiFailure(ApiFailure.Kind.WRONG_ACCOUNT)
        val username = user.opt("username") as? String ?: throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
        if (username.isBlank()) throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
        fun count(key: String): Long = when (val value = user.opt(key)) {
            is Int -> value.toLong()
            is Long -> value
            else -> throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
        }.also { if (it < 0) throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE) }
        return Profile(id, username, count("follower_count"), count("following_count"), time)
    }
}

class ApiFailure(val kind: Kind) : Exception(kind.name) {
    enum class Kind { SESSION_EXPIRED, VERIFICATION, LIMITED, TOO_SOON, CONNECTION, INVALID_RESPONSE, WRONG_ACCOUNT, STORAGE }
    fun userMessage(): String = when (kind) {
        Kind.SESSION_EXPIRED -> "Bağlantının süresi dolmuş. Hesabını yeniden bağla."
        Kind.VERIFICATION -> "Instagram ek doğrulama istiyor. Bağlantıyı yenileyerek tamamlayabilirsin."
        Kind.LIMITED -> "Instagram şu anda yenilemeyi sınırlıyor. Biraz sonra tekrar dene."
        Kind.TOO_SOON -> "Yeni bir istek için birkaç saniye bekle. Tekrar giriş yapmana gerek yok."
        Kind.CONNECTION -> "Bağlantı kurulamadı. İnternetini kontrol edip yeniden dene."
        Kind.INVALID_RESPONSE -> "Instagram sayıları beklenen biçimde döndürmedi. Daha sonra tekrar dene."
        Kind.WRONG_ACCOUNT -> "Hesap kimliği eşleşmedi. Bağlantıyı kaldırıp yeniden bağla."
        Kind.STORAGE -> "Bu cihazda güvenli kayıt açılamadı. Bağlantıyı kaldırıp yeniden bağla."
    }
}
