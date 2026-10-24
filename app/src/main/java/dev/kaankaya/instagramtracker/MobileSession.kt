package dev.kaankaya.instagramtracker

import org.json.JSONObject
import java.util.UUID
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

/** Focused, versioned port of the saved-session profile contract in instagrapi 3.2.0. */
data class MobileSession(val binding: String, val owner: String, val headers: Map<String, String>,
    val cookies: Map<String, String>, val usdid: String = "", val signingKey: String = "") {
    fun reference() = Session(owner, "", headers.getValue("User-Agent"), binding)
    fun requestHeaders(now: Long): Map<String, String> = headers + mapOf(
        "X-Pigeon-Session-Id" to "UFS-$binding-1",
        "X-Pigeon-Rawclienttime" to (now / 1000.0).toString(),
        "Cookie" to cookies.entries.joinToString("; ") { "${it.key}=${it.value}" },
    ) + if (signingKey.isEmpty()) emptyMap() else mapOf("X-Meta-Usdid" to signedDevice(now))

    private fun signedDevice(now: Long): String {
        val der = signingKey.replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "").replace(Regex("\\s"), "").decodeBase64()?.toByteArray() ?: error("Invalid key")
        val key = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(der))
        val part = "$usdid.${now / 1000 + 3600}"
        val signature = Signature.getInstance("SHA256withECDSA").apply { initSign(key); update(part.toByteArray()) }.sign()
        return "$part.${signature.toByteString().base64Url().trimEnd('=')}"
    }

    /** Only rotate validated state belonging to this account. Reject an identity change. */
    fun updated(responseHeaders: Map<String, List<String>>): MobileSession {
        val next = headers.toMutableMap()
        val nextCookies = cookies.toMutableMap()
        responseHeaders.forEach { (key, values) ->
            val value = values.lastOrNull() ?: return@forEach
            when (key.lowercase(java.util.Locale.ROOT)) {
                "ig-set-authorization" -> { MobileSessionCodec.authorization(value, owner); next["Authorization"] = value }
                "ig-set-x-mid" -> next["X-MID"] = MobileSessionCodec.header(value)
                "ig-set-ig-u-rur" -> next["IG-U-RUR"] = MobileSessionCodec.header(value)
                "ig-set-ig-u-shbid" -> next["IG-U-SHBID"] = MobileSessionCodec.header(value)
                "ig-set-ig-u-shbts" -> next["IG-U-SHBTS"] = MobileSessionCodec.header(value)
                "ig-set-ig-u-ig-direct-region-hint" -> next["IG-U-IG-DIRECT-REGION-HINT"] = MobileSessionCodec.header(value)
                "ig-set-ig-u-ds-user-id" -> require(value == owner)
                "x-ig-set-www-claim" -> next["X-IG-WWW-Claim"] = MobileSessionCodec.header(value)
                "set-cookie" -> values.forEach { line ->
                    // Use the HTTP cookie parser at the wire boundary, including deletion attributes.
                    val parsed = okhttp3.Cookie.parse(okhttp3.HttpUrl.Builder().scheme("https").host("i.instagram.com").build(), line)
                    if (parsed != null && parsed.name in MobileSessionCodec.cookieNames) {
                        if (parsed.expiresAt <= System.currentTimeMillis()) nextCookies.remove(parsed.name)
                        else nextCookies[parsed.name] = MobileSessionCodec.cookie(parsed.value)
                    }
                }
            }
        }
        require(nextCookies["ds_user_id"] == null || nextCookies["ds_user_id"] == owner)
        return copy(headers = next, cookies = nextCookies)
    }
}

object MobileSessionCodec {
    val cookieNames = setOf("sessionid", "ds_user_id", "csrftoken", "mid", "ig_did", "rur")
    val headerNames = setOf("Authorization", "User-Agent", "X-IG-App-Locale", "X-IG-Device-Locale", "X-IG-Mapped-Locale",
        "X-IG-App-Startup-Country", "X-Bloks-Version-Id", "X-IG-WWW-Claim", "X-Bloks-Is-Layout-RTL",
        "X-Bloks-Is-Panorama-Enabled", "X-IG-Device-ID", "X-IG-Family-Device-ID", "X-IG-Android-ID",
        "X-IG-Timezone-Offset", "X-IG-Connection-Type", "X-IG-Capabilities", "X-IG-App-ID", "Priority", "Accept-Language",
        "X-MID", "X-FB-HTTP-Engine", "X-Tigon-Is-Retry", "X-FB-Client-IP", "X-FB-Server-Cluster", "IG-INTENDED-USER-ID",
        "IG-U-DS-USER-ID", "IG-U-RUR", "IG-U-SHBID", "IG-U-SHBTS", "IG-U-IG-DIRECT-REGION-HINT", "X-IG-Nav-Chain")
    fun header(value: String): String {
        require(value.length <= 16384 && value.none { it.code < 32 || it.code > 126 })
        return value
    }
    fun cookie(value: String): String = header(value).also { require(it.none { c -> c == ';' || c == '\r' || c == '\n' }) }
    fun authorization(value: String, owner: String) {
        header(value); require(value.startsWith("Bearer IGT:2:"))
        val data = JSONObject(value.removePrefix("Bearer IGT:2:").decodeBase64()?.utf8() ?: error("Invalid authorization"))
        require(data.optString("ds_user_id") == owner && data.optString("sessionid").isNotBlank())
    }
    private fun map(json: JSONObject): Map<String, String> = json.keys().asSequence().associateWith { json.get(it) as? String ?: error("Invalid value") }
    fun decode(text: String): MobileSession {
        require(text.toByteArray().size <= 128 * 1024)
        val json = JSONObject(text); require(json.getInt("version") == 1)
        val owner = json.getString("owner"); require(owner.matches(Regex("[1-9][0-9]{0,29}")))
        val binding = json.getString("binding"); require(binding.matches(Regex("[a-f0-9]{32}")))
        val headers = map(json.getJSONObject("headers")); require(headers.keys.all { it in headerNames })
        headers.values.forEach(::header)
        authorization(headers.getValue("Authorization"), owner)
        require(headers["IG-INTENDED-USER-ID"] == owner && headers["IG-U-DS-USER-ID"] == owner)
        for (name in listOf("User-Agent", "X-IG-Device-ID", "X-IG-Family-Device-ID", "X-IG-Android-ID", "X-IG-App-ID", "X-Bloks-Version-Id"))
            require(!headers[name].isNullOrBlank())
        require(headers.getValue("User-Agent").startsWith("Instagram "))
        val cookies = map(json.getJSONObject("cookies")); require(cookies.keys.all { it in cookieNames })
        cookies.values.forEach(::cookie); require(cookies["ds_user_id"] == null || cookies["ds_user_id"] == owner)
        val usdid = json.getString("usdid"); val key = json.getString("signingKey")
        require((usdid.isEmpty() && key.isEmpty()) || (usdid.length <= 128 && key.length <= 4096 && key.startsWith("-----BEGIN PRIVATE KEY-----")))
        return MobileSession(binding, owner, headers, cookies, usdid, key)
    }
    fun encode(state: MobileSession): String = JSONObject().put("version", 1).put("owner", state.owner).put("binding", state.binding)
        .put("headers", JSONObject(state.headers)).put("cookies", JSONObject(state.cookies)).put("usdid", state.usdid)
        .put("signingKey", state.signingKey).toString().also { decode(it) }
    fun imported(text: String): MobileSession {
        val json = JSONObject(text)
        require(json.getLong("expiresAt") * 1000 > System.currentTimeMillis())
        json.put("binding", UUID.randomUUID().toString().replace("-", ""))
        return decode(json.toString())
    }
}
