package dev.kaankaya.instagramtracker

/** A missing username cannot be replaced by a display name or a guessed account. */
object ProfileLinks {
    const val MAX_SITES = 5
    const val MAX_LINKS = MAX_SITES + 1
    fun url(username: String?): String? = username?.takeIf(ProfileInput::validUsername)?.let { "https://www.instagram.com/$it/" }
    fun webUrl(value: String?): String? {
        if (value == null || value.length > 4096 || value.any { it <= ' ' || it == '\u007f' || it == '\\' }) return null
        return try { java.net.URI(value).takeIf { it.scheme in listOf("http", "https") && it.host?.contains('.') == true && it.rawUserInfo == null }?.toASCIIString() } catch (_: Exception) { null }
    }
    fun threadsName(value: String?): String? = try {
        val uri = java.net.URI(webUrl(value) ?: "")
        if (uri.scheme != "https" || uri.host !in listOf("threads.com", "www.threads.com", "threads.net", "www.threads.net") || uri.port !in listOf(-1, 443)) null
        else Regex("/@([A-Za-z0-9._]{1,30})/?").matchEntire(uri.path)?.groupValues?.get(1)
    } catch (_: Exception) { null }
    fun threads(links: List<ProfileSite>): String? = links.firstOrNull { threadsName(it.url) != null }?.url
    fun parse(body: org.json.JSONObject): List<ProfileSite> {
        val array = body.optJSONArray("bio_links")
        val values = (0 until minOf(array?.length() ?: 0, MAX_SITES)).mapNotNull { array?.optJSONObject(it)?.opt("url") as? String } + listOfNotNull(body.opt("external_url") as? String)
        val normalized = values.mapNotNull(::webUrl).distinct()
        val sites = normalized.take(MAX_SITES)
        val label = (body.opt("text_post_app_badge_label") as? String)?.removePrefix("@")
        val badge = label?.takeIf { body.opt("show_text_post_app_badge") == true && ProfileInput.validUsername(it) }
            ?.let { "https://www.threads.com/@$it" }
        val linked = normalized.firstOrNull { threadsName(it) != null } ?: badge
        return (sites + listOfNotNull(linked)).distinct().map(::ProfileSite)
    }
    fun brand(value: String): Pair<String, String> {
        val host = java.net.URI(value).host.lowercase(java.util.Locale.ROOT).removePrefix("www.")
        val brands = listOf(Triple("youtube.com", "YouTube", "youtube"), Triple("youtu.be", "YouTube", "youtube"),
            Triple("instagram.com", "Instagram", "instagram"), Triple("threads.com", "Threads", "threads"), Triple("threads.net", "Threads", "threads"),
            Triple("facebook.com", "Facebook", "facebook"), Triple("x.com", "X", "x"), Triple("twitter.com", "X", "x"),
            Triple("linkedin.com", "LinkedIn", "linkedin"), Triple("tiktok.com", "TikTok", "tiktok"), Triple("github.com", "GitHub", "github"),
            Triple("linktr.ee", "Linktree", "linktree"), Triple("spotify.com", "Spotify", "spotify"), Triple("t.me", "Telegram", "telegram"),
            Triple("telegram.me", "Telegram", "telegram"), Triple("wa.me", "WhatsApp", "whatsapp"), Triple("whatsapp.com", "WhatsApp", "whatsapp"))
        val brand = brands.firstOrNull { host == it.first || host.endsWith(".${it.first}") }
        return (brand?.second ?: host) to (brand?.third ?: "globe")
    }
}

data class ProfileSite(val url: String) { init { require(ProfileLinks.webUrl(url) == url) } }
