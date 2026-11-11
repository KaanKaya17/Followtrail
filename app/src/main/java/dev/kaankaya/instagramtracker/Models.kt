package dev.kaankaya.instagramtracker

import org.json.JSONObject
import java.net.URI
import java.util.Locale

data class Session(val userId: String, val cookie: String, val userAgent: String, val mobileBinding: String? = null,
    val webBinding: String? = null) {
    init { require(mobileBinding == null || webBinding == null) }
    val connectionBinding: String? get() = mobileBinding ?: webBinding?.let { "web:$it" }
    val supportsLists: Boolean get() = connectionBinding != null
}
data class Profile(val userId: String, val username: String, val followers: Long, val following: Long, val observedAt: Long,
    val posts: Long? = null, val details: ProfileDetails = ProfileDetails(), val name: String? = null)
data class SavedAccount(val session: Session, val profile: Profile, val people: List<TrackedPerson> = emptyList(),
    val automation: ListAutomation = ListAutomation(), val about: ProfileAbout? = null)

data class TrackedPerson(val history: List<Profile>, val note: String = "", val group: String = "",
    val watching: Boolean = false, val intervalMinutes: Int = RequestDefaults.DEFAULT_INTERVAL_MINUTES, val nextCheckAt: Long = 0, val favorite: Boolean = false, val about: ProfileAbout? = null) {
    val latest: Profile get() = history.last()
}

object PeoplePolicy {
    const val MAX_PEOPLE = 100
    const val MAX_HISTORY = 90
    const val FRESH_MS = 15 * 60 * 1000L
    fun favoritesFirst(people: List<TrackedPerson>): List<TrackedPerson> = people.filter { it.favorite } + people.filterNot { it.favorite }
    fun needsRefresh(profile: Profile, now: Long): Boolean = now >= profile.observedAt && now - profile.observedAt >= FRESH_MS
    fun record(people: List<TrackedPerson>, profile: Profile): List<TrackedPerson> {
        val existing = people.firstOrNull { it.latest.userId == profile.userId }
        require(existing != null || people.size < MAX_PEOPLE)
        require(existing == null || profile.observedAt > existing.latest.observedAt)
        val updated = (existing ?: TrackedPerson(emptyList())).copy(
            history = (existing?.history.orEmpty() + profile).takeLast(MAX_HISTORY),
            about = existing?.about,
            nextCheckAt = if (existing?.watching == true) profile.observedAt + WatchIntervals.durationMs(existing.intervalMinutes) else 0)
        return if (existing == null) people + updated else people.map { if (it === existing) updated else it }
    }
}

object ProfileInput {
    fun validUsername(value: String) = value.matches(Regex("[A-Za-z0-9._]{1,30}"))
    fun username(input: String): String? = try {
        val value = input.trim()
        val name = if (value.startsWith("https://")) {
            val uri = URI(value)
            if (!NavigationPolicy.isInstagram(value) || uri.host?.lowercase(Locale.ROOT) !in setOf("instagram.com", "www.instagram.com")) null
            else uri.path.trim('/').takeIf { !it.contains('/') && !uri.rawPath.contains('%') }
        } else value.removePrefix("@")
        name?.takeIf { validUsername(it) && it.lowercase(Locale.ROOT) !in setOf("accounts", "explore", "reels", "stories", "direct", "p") }
            ?.lowercase(Locale.ROOT)
    } catch (_: Exception) { null }
}

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
    fun parse(text: String, expectedId: String, time: Long): Profile = fromBody(JSONObject(text), expectedId, time)
    fun fromBody(body: JSONObject, expectedId: String, time: Long): Profile {
        if (body.optString("status") != "ok") throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
        val user = body.optJSONObject("user") ?: throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
        return user(user, time).also { if (it.userId != expectedId) throw ApiFailure(ApiFailure.Kind.WRONG_ACCOUNT) }
    }
    fun byUsername(text: String, expectedUsername: String, time: Long): Profile {
        val body = JSONObject(text)
        if (body.optString("status") != "ok") throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
        val user = body.optJSONObject("data")?.optJSONObject("user") ?: throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
        val id = InstagramIdentity.id(user.opt("id"))
        val profile = Profile(id, username(user), count(user.optJSONObject("edge_followed_by")?.opt("count")),
            count(user.optJSONObject("edge_follow")?.opt("count")), time,
            optionalCount(user.optJSONObject("edge_owner_to_timeline_media")?.opt("count")), ProfileDetailsParser.parse(user))
        if (!profile.username.equals(expectedUsername, ignoreCase = true)) throw ApiFailure(ApiFailure.Kind.WRONG_ACCOUNT)
        return profile.copy(name = displayName(user))
    }
    fun user(user: JSONObject, time: Long): Profile = Profile(InstagramIdentity.id(user.opt("pk")), username(user),
        count(user.opt("follower_count")), count(user.opt("following_count")), time, optionalCount(user.opt("media_count")), ProfileDetailsParser.parse(user), displayName(user))
    private fun displayName(user: JSONObject) = (user.opt("full_name") as? String)?.takeIf { it.length <= 1000 }
    private fun username(user: JSONObject): String = (user.opt("username") as? String)
        ?.takeIf { ProfileInput.validUsername(it) } ?: throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
    private fun count(value: Any?): Long = when (value) {
        is Int -> value.toLong(); is Long -> value
        else -> throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
    }.also { if (it < 0) throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE) }
    private fun optionalCount(value: Any?): Long? = if (value == null || value === JSONObject.NULL) null else count(value)
}

object InstagramIdentity {
    fun id(value: Any?): String {
        val id = when (value) { is String -> value; is Int -> value.toString(); is Long -> value.toString(); else -> "" }
        if (!id.matches(Regex("[1-9][0-9]{0,29}"))) throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
        return id
    }
}

class ApiFailure(val kind: Kind, val http: Int? = null) : Exception(kind.name) {
    enum class Kind { SESSION_EXPIRED, VERIFICATION, LIMITED, TOO_SOON, CONNECTION, CANCELLED, INVALID_RESPONSE, WRONG_ACCOUNT, NOT_FOUND, STORAGE, MOBILE_REQUIRED, CAPACITY, POLICY, BUDGET }
}
