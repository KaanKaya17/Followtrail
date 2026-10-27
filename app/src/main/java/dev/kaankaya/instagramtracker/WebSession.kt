package dev.kaankaya.instagramtracker

import android.content.Context
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.util.UUID

/** Credentials for one fixed HTTPS web origin; never an Instagram mobile bearer. */
data class WebSession(val binding: String, val owner: String, val userAgent: String,
    val cookies: Map<String, String>, val claim: String = "0") {
    override fun toString() = "WebSession(redacted)"
    fun reference() = Session(owner, "", userAgent, webBinding = binding)
    fun cookieHeader() = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
    fun updated(headers: Map<String, List<String>>, now: Long): WebSession {
        val next = cookies.toMutableMap()
        var nextClaim = claim
        headers.forEach { (name, values) ->
            if (name.equals("Set-Cookie", true)) values.forEach { line ->
                Cookie.parse(WebSessionCodec.origin, line)?.takeIf { it.matches(WebSessionCodec.origin) && it.name in WebSessionCodec.cookieNames }?.let { c ->
                    if (c.expiresAt <= now) next.remove(c.name) else next[c.name] = c.value
                }
            }
            if (name.equals("X-IG-Set-WWW-Claim", true)) values.lastOrNull()?.let { nextClaim = WebSessionCodec.header(it) }
        }
        if (next["ds_user_id"] != owner) throw ApiFailure(ApiFailure.Kind.WRONG_ACCOUNT)
        if (next["sessionid"].isNullOrEmpty()) throw ApiFailure(ApiFailure.Kind.SESSION_EXPIRED)
        return copy(cookies = next, claim = nextClaim).also { WebSessionCodec.validate(it) }
    }
}

object WebSessionCodec {
    val origin = "https://www.instagram.com/".toHttpUrl()
    val cookieNames = setOf("sessionid", "ds_user_id", "csrftoken", "mid", "ig_did", "rur", "datr", "ig_nrcb", "ps_l", "ps_n")
    fun header(value: String): String {
        require(value.length in 1..2048 && value.all { it.code in 32..126 })
        return value
    }
    fun fromHeader(raw: String?, userAgent: String): WebSession {
        require(raw != null && raw.toByteArray(Charsets.UTF_8).size <= 16384 && raw.all { it.code in 32..126 })
        val cookies = linkedMapOf<String, String>()
        raw.split(';').forEach { part ->
            val pair = part.trim().split('=', limit = 2)
            if (pair.size == 2 && pair[0] in cookieNames) {
                require(cookies[pair[0]] == null || cookies[pair[0]] == pair[1])
                cookies[pair[0]] = pair[1]
            }
        }
        val owner = InstagramIdentity.id(cookies["ds_user_id"])
        return WebSession(UUID.randomUUID().toString().replace("-", ""), owner, header(userAgent), cookies).also(::validate)
    }
    fun validate(state: WebSession) {
        require(state.binding.matches(Regex("[a-f0-9]{32}")))
        require(InstagramIdentity.id(state.owner) == state.owner)
        header(state.userAgent); header(state.claim)
        require(state.cookies.keys.all { it in cookieNames })
        state.cookies.values.forEach { require(it.length <= 8192 && it.all { c -> c.code in 32..126 && c != ';' }) }
        require(state.cookies["ds_user_id"] == state.owner && !state.cookies["sessionid"].isNullOrBlank())
        require(state.cookieHeader().toByteArray(Charsets.UTF_8).size <= 16384)
    }
    fun encode(state: WebSession): String {
        validate(state)
        return JSONObject().put("version", 1).put("binding", state.binding).put("owner", state.owner)
            .put("ua", state.userAgent).put("cookies", JSONObject(state.cookies)).put("claim", state.claim).toString()
    }
    fun decode(raw: String): WebSession {
        val j = JSONObject(raw); require(j.getInt("version") == 1)
        val cookies = j.getJSONObject("cookies")
        return WebSession(j.getString("binding"), j.getString("owner"), j.getString("ua"),
            cookies.keys().asSequence().associateWith { cookies.getString(it) }, j.getString("claim")).also(::validate)
    }
}

class WebSessions(context: Context, namespace: String = "web") : SessionBanks<WebSession>(context, namespace, "web",
    WebSessionCodec::decode, WebSessionCodec::encode, WebSession::reference, { it.webBinding })
