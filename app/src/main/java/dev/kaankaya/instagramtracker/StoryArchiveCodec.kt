package dev.kaankaya.instagramtracker

import org.json.JSONArray
import org.json.JSONObject

/** Persist normalized facts, never API bodies or expiring authenticated media addresses. */
object StoryArchiveCodec {
    private fun nullable(value: Any?) = value ?: JSONObject.NULL
    private fun array(values: List<*>?) = values?.let(::JSONArray) ?: JSONObject.NULL
    private fun optionalLong(value: JSONObject, key: String): Long? = if (value.isNull(key)) null else
        (value.get(key) as? Number)?.let { number ->
            require(number is Long || number is Int)
            number.toLong().also { require(it in 1..253402300800000L) }
        } ?: error("Invalid story time")
    private fun <T> list(value: JSONObject, key: String, parse: (Any) -> T): List<T>? {
        if (value.isNull(key)) return null
        val raw = value.getJSONArray(key); require(raw.length() <= StoryLimits.MAX_ITEMS)
        return java.util.Collections.unmodifiableList((0 until raw.length()).map { parse(raw.get(it)) })
    }
    private fun facts(item: StoryFacts) = JSONObject().put("id", item.id).put("target", item.target)
        .put("publishedAt", nullable(item.publishedAt)).put("expiresAt", nullable(item.expiresAt)).put("kind", item.kind.name)
        .put("mentions", item.mentions?.let { JSONArray(it.map { entry -> JSONObject().put("id", entry.id).put("username", entry.username) }) } ?: JSONObject.NULL)
        .put("links", array(item.links)).put("hashtags", array(item.hashtags)).put("locations", array(item.locations))
        .put("shared", item.shared?.let { JSONArray(it.map { entry -> JSONObject().put("id", entry.id).put("owner", nullable(entry.owner))
            .put("code", nullable(entry.code)).put("type", nullable(entry.type)) }) } ?: JSONObject.NULL)
        .put("incomplete", JSONArray(item.incomplete.sorted()))
    private fun facts(value: JSONObject, target: String): StoryFacts {
        val id = InstagramIdentity.id(value.get("id")); require(value.getString("target") == target)
        fun text(raw: Any, limit: Int) = (raw as? String)?.also { require(it.length <= limit) } ?: error("Invalid story text")
        fun optionalText(row: JSONObject, key: String, limit: Int) = if (row.isNull(key)) null else text(row.get(key), limit)
        return StoryFacts(id, target, optionalLong(value, "publishedAt"), optionalLong(value, "expiresAt"), StoryKind.valueOf(value.getString("kind")),
            list(value, "mentions") { raw -> (raw as JSONObject).let {
                val name = it.getString("username"); require(ProfileInput.validUsername(name))
                StoryMention(InstagramIdentity.id(it.get("id")), name)
            } }, list(value, "links") { ProfileLinks.webUrl(it as? String) ?: error("Invalid story link") },
            list(value, "hashtags") { text(it, 100) }, list(value, "locations") { text(it, 256) },
            list(value, "shared") { raw -> (raw as JSONObject).let {
                val owner = if (it.isNull("owner")) null else InstagramIdentity.id(it.get("owner"))
                val code = optionalText(it, "code", 64)?.also { c -> require(c.matches(Regex("[A-Za-z0-9_-]{1,64}"))) }
                val kind = optionalText(it, "type", 8)?.also { t -> require(t in setOf("feed", "clips", "story")) }
                StoryShared(InstagramIdentity.id(it.get("id")), owner, code, kind)
            } }, java.util.Collections.unmodifiableSet(list(value, "incomplete") { text(it, 64).also { k -> require(k in setOf("reel_mentions", "story_link_stickers", "story_cta", "story_hashtags", "story_locations", "story_feed_media")) } }!!.toSet()))
    }
    private fun json(archive: StoryArchive): String = JSONObject().put("version", 1).put("owner", archive.owner)
        .put("targets", JSONObject().apply { archive.targets.toSortedMap().forEach { (target, data) ->
            put(target, JSONObject().put("checkedAt", nullable(data.checkedAt)).put("summary", data.summary?.let {
                JSONObject().put("returned", it.returned).put("saved", it.saved).put("existing", it.existing).put("metadata", it.metadata)
            } ?: JSONObject.NULL).put("items", JSONArray(data.items.map { row -> JSONObject().put("facts", facts(row.facts))
                .put("capturedAt", row.capturedAt).put("mime", nullable(row.mime)).put("bytes", nullable(row.bytes)).put("digest", nullable(row.digest)) })))
        } }).toString()
    internal fun prepare(archive: StoryArchive): Pair<String,StoryArchive> {
        val raw = json(archive)
        if (raw.toByteArray(Charsets.UTF_8).size > StoryLimits.MAX_INDEX) throw ApiFailure(ApiFailure.Kind.CAPACITY)
        return raw to decode(raw)
    }
    fun encode(archive: StoryArchive): String = prepare(archive).first
    fun decode(raw: String): StoryArchive {
        require(raw.toByteArray(Charsets.UTF_8).size <= StoryLimits.MAX_INDEX)
        val value = JSONObject(raw); require(value.getInt("version") == 1)
        val owner = InstagramIdentity.id(value.get("owner")); val targets = value.getJSONObject("targets")
        require(targets.length() <= StoryLimits.MAX_ITEMS)
        var count = 0
        val result = targets.keys().asSequence().associateWith { target ->
            InstagramIdentity.id(target)
            val data = targets.getJSONObject(target); val array = data.getJSONArray("items")
            count += array.length(); require(count <= StoryLimits.MAX_ITEMS)
            val items = (0 until array.length()).map { i ->
                val item = array.getJSONObject(i); val facts = facts(item.getJSONObject("facts"), target)
                val captured = optionalLong(item, "capturedAt") ?: error("Missing capture time")
                val mime = if (item.isNull("mime")) null else item.getString("mime")
                val bytes = if (item.isNull("bytes")) null else item.get("bytes").let {
                    require(it is Long || it is Int)
                    (it as Number).toLong().also { n -> require(n in 1..StoryLimits.MAX_MEDIA.toLong()) }.toInt()
                }
                val digest = if (item.isNull("digest")) null else item.getString("digest").also { require(it.matches(Regex("[a-f0-9]{64}"))) }
                if (mime == null) require(bytes == null && digest == null) else {
                    require(bytes != null && digest != null)
                    require(if (facts.kind == StoryKind.IMAGE) mime in setOf("image/jpeg", "image/png", "image/webp") else facts.kind == StoryKind.VIDEO && mime == "video/mp4")
                }
                ArchivedStory(facts, captured, mime, bytes, digest)
            }
            require(items.map { it.facts.id }.distinct().size == items.size)
            val summary = data.optJSONObject("summary")?.let {
                val counts = listOf("returned", "saved", "existing", "metadata").map { key -> it.getInt(key).also { n -> require(n in 0..StoryLimits.MAX_ITEMS) } }
                require(counts[1] + counts[2] + counts[3] == counts[0])
                StorySummary(counts[0], counts[1], counts[2], counts[3])
            }
            StoryTarget(java.util.Collections.unmodifiableList(items), optionalLong(data, "checkedAt"), summary)
        }
        return StoryArchive(owner, java.util.Collections.unmodifiableMap(result))
    }
}
