package dev.kaankaya.instagramtracker

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

enum class StoryKind { IMAGE, VIDEO, UNSUPPORTED }
data class StoryMention(val id: String, val username: String)
data class StoryShared(val id: String, val owner: String?, val code: String?, val type: String?)
data class StoryFacts(val id: String, val target: String, val publishedAt: Long?, val expiresAt: Long?, val kind: StoryKind,
    val mentions: List<StoryMention>?, val links: List<String>?, val hashtags: List<String>?, val locations: List<String>?,
    val shared: List<StoryShared>?, val incomplete: Set<String> = emptySet())
/** Expiring CDN addresses only live in the explicit fetch operation, never the index or logs. */
data class StoryCandidate(val facts: StoryFacts, val mediaUrl: String?) {
    override fun toString() = "StoryCandidate(redacted)"
}
data class ArchivedStory(val facts: StoryFacts, val capturedAt: Long, val mime: String? = null,
    val bytes: Int? = null, val digest: String? = null) {
    val saved get() = mime != null && bytes != null && digest != null
    val day get() = StoryCalendar.day(facts.publishedAt ?: capturedAt)
}
data class StorySummary(val returned: Int, val saved: Int, val existing: Int, val metadata: Int)
data class StoryTarget(val items: List<ArchivedStory> = emptyList(), val checkedAt: Long? = null, val summary: StorySummary? = null)
data class StoryArchive(val owner: String, val targets: Map<String, StoryTarget> = emptyMap())

object StoryParser {
    internal fun id(value: Any?): String? = when (value) {
        is String -> value
        is Long, is Int -> value.toString()
        else -> null
    }?.takeIf { it.matches(Regex("[1-9][0-9]{0,29}")) }
    private fun text(value: Any?, max: Int) = (value as? String)?.takeIf { it.length <= max }
    private fun time(value: Any?): Long? = (value as? Number)?.toDouble()?.takeIf {
        it.isFinite() && it > 0 && it < 253402300800.0
    }?.let { (it * 1000).toLong() }
    private fun <T> collection(item: JSONObject, key: String, incomplete: MutableSet<String>, parse: (JSONObject) -> T?): List<T>? {
        if (!item.has(key) || item.isNull(key)) return null
        val raw = item.optJSONArray(key)
        if (raw == null || raw.length() > StoryLimits.MAX_ITEMS) { incomplete += key; return null }
        return (0 until raw.length()).mapNotNull { index ->
            raw.optJSONObject(index)?.let(parse).also { if (it == null) incomplete += key }
        }
    }
    private fun media(item: JSONObject, kind: StoryKind): String? {
        val raw = when (kind) {
            StoryKind.IMAGE -> item.optJSONObject("image_versions2")?.optJSONArray("candidates")
            StoryKind.VIDEO -> item.optJSONArray("video_versions")
            StoryKind.UNSUPPORTED -> null
        } ?: return null
        if (raw.length() > StoryLimits.MAX_ITEMS) return null
        return (0 until raw.length()).mapNotNull { i ->
            val value = raw.optJSONObject(i) ?: return@mapNotNull null
            fun dimension(key: String) = when (val rawValue = value.opt(key)) {
                is Int -> rawValue.toLong()
                is Long -> rawValue
                else -> null
            }?.takeIf { it in 1..StoryLimits.MAX_SIDE.toLong() }
            val width = dimension("width") ?: return@mapNotNull null
            val height = dimension("height") ?: return@mapNotNull null
            if (width * height > StoryLimits.MAX_PIXELS) return@mapNotNull null
            val url = (value.opt("url") as? String)?.takeIf { it.length <= 8192 }?.let(AvatarPolicy::url)
                ?.takeIf { it.fragment == null } ?: return@mapNotNull null
            width.toLong() * height to url.toString()
        }.maxByOrNull { it.first }?.second
    }
    fun parse(body: JSONObject, target: String): List<StoryCandidate> {
        require(InstagramIdentity.id(target) == target)
        if (body.optString("status") != "ok" || !body.has("reel")) throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
        if (body.isNull("reel")) return emptyList()
        val reel = body.optJSONObject("reel") ?: throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
        if (id(reel.optJSONObject("user")?.opt("pk")) != target) throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
        val items = reel.optJSONArray("items") ?: throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
        if (items.length() > StoryLimits.MAX_ITEMS) throw ApiFailure(ApiFailure.Kind.CAPACITY)
        val seen = mutableSetOf<String>()
        return (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
            val pk = id(item.opt("pk")) ?: throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
            item.optJSONObject("user")?.let { if (id(it.opt("pk")) != target) throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE) }
            if (!seen.add(pk)) return@mapNotNull null
            val kind = when (item.opt("media_type")) { 1, 1L -> StoryKind.IMAGE; 2, 2L -> StoryKind.VIDEO; else -> StoryKind.UNSUPPORTED }
            val incomplete = mutableSetOf<String>()
            var links = collection(item, "story_link_stickers", incomplete) { entry ->
                ProfileLinks.webUrl(entry.optJSONObject("story_link")?.opt("url") as? String)
            }
            val cta = collection(item, "story_cta", incomplete) { it }
            if (cta != null) {
                val extra = cta.flatMap { entry ->
                    val list = entry.optJSONArray("links")
                    if (list != null && list.length() > StoryLimits.MAX_ITEMS) { incomplete += "story_cta"; emptyList() }
                    else (0 until (list?.length() ?: 0)).mapNotNull { i ->
                        ProfileLinks.webUrl(list?.optJSONObject(i)?.opt("webUri") as? String).also { if (it == null) incomplete += "story_cta" }
                    }
                }
                links = (links.orEmpty() + extra).distinct().take(StoryLimits.MAX_ITEMS)
            }
            StoryCandidate(StoryFacts(pk, target, time(item.opt("taken_at")), time(item.opt("expiring_at")), kind,
                collection(item, "reel_mentions", incomplete) { entry ->
                    val user = entry.optJSONObject("user"); val person = id(user?.opt("pk"))
                    val name = text(user?.opt("username"), 30)?.takeIf(ProfileInput::validUsername)
                    if (person == null || name == null) null else StoryMention(person, name)
                }, links,
                collection(item, "story_hashtags", incomplete) { text(it.optJSONObject("hashtag")?.opt("name"), 100) },
                collection(item, "story_locations", incomplete) { text(it.optJSONObject("location")?.opt("name"), 256) },
                collection(item, "story_feed_media", incomplete) { entry ->
                    id(entry.opt("media_id"))?.let { shared -> StoryShared(shared, id(entry.opt("user_id")),
                        text(entry.opt("media_code"), 64)?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,64}")) },
                        (entry.opt("product_type") as? String)?.takeIf { it in setOf("feed", "clips", "story") }) }
                }, incomplete), media(item, kind))
        }
    }
}

/** Same UTC day boundary as the Windows archive; locale only changes presentation. */
object StoryCalendar {
    private val zone = TimeZone.getTimeZone("UTC")
    fun day(time: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = zone }.format(java.util.Date(time))
    fun month(time: Long): String = day(time).take(7)
    fun validDay(value: String): Boolean = runCatching {
        require(value.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))
        val calendar = start(value.take(7)); calendar.set(Calendar.DAY_OF_MONTH,value.takeLast(2).toInt())
        require(day(calendar.timeInMillis) == value)
    }.isSuccess
    fun start(month: String): Calendar {
        require(month.matches(Regex("[0-9]{4}-(0[1-9]|1[0-2])")))
        val year = month.take(4).toInt(); require(year in 1970..9999)
        return Calendar.getInstance(zone, Locale.ROOT).apply { clear(); set(year, month.takeLast(2).toInt() - 1, 1, 0, 0, 0); isLenient = false }
    }
    fun shift(month: String, amount: Int): String {
        require(amount in -1..1)
        val date = start(month); date.add(Calendar.MONTH, amount)
        require(date.get(Calendar.YEAR) in 1970..9999 && date.get(Calendar.ERA) == java.util.GregorianCalendar.AD)
        return this.month(date.timeInMillis)
    }
    fun days(month: String): List<String?> {
        val date = start(month)
        val offset = (date.get(Calendar.DAY_OF_WEEK) + 5) % 7
        return List(offset) { null } + (1..date.getActualMaximum(Calendar.DAY_OF_MONTH)).map { "$month-${it.toString().padStart(2,'0')}" }
    }
}
