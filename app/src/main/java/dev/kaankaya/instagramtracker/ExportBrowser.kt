package dev.kaankaya.instagramtracker

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipFile

internal class ExportBrowser private constructor(private val zip: ZipFile) : Closeable {
    data class Entry(val index: Int, val name: String, val size: Long, val kind: String)
    data class Page(val section: String, val total: Int, val content: String, val next: Int?,
        val fields: List<Pair<String, String>>, val hasMetadata: Boolean)
    data class Coverage(val name: String?, val username: String?, val exportedAt: String?, val filenameDate: String?,
        val dateRange: Pair<String, String>?,
        val hasFollowers: Boolean, val hasFollowing: Boolean, val hasMessages: Boolean, val emptyMessages: Boolean)
    data class Content(val kind: String, val time: Long, val caption: String, val media: List<Entry>,
        val metrics: Map<String, Long>)
    data class ContentHistory(val items: List<Content>, val skipped: Int)
    data class SearchHit(val file: Entry, val record: Int?, val excerpt: String)
    data class SearchPage(val hits: List<SearchHit>, val nextFile: Int, val nextRecord: Int, val skipped: Int)
    private data class Document(val index: Int, val section: String, val rows: List<Any?>, val metadata: JSONObject? = null)
    private val entries = zip.entries().asSequence().filterNot { it.isDirectory }.toList()
    val files = entries.mapIndexed { index, entry ->
        val extension = entry.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val kind = when (extension) {
            "json" -> "json"
            "txt", "html", "htm", "csv", "srt", "vtt", "xml", "md" -> "text"
            "jpg", "jpeg", "png", "webp", "gif" -> "image"
            "mp4", "webm" -> "video"
            "ogg", "mp3", "m4a", "wav" -> "audio"
            else -> "file"
        }
        Entry(index, entry.name, entry.size, kind)
    }
    private var document: Document? = null
    private data class CachedRecord(val index: Int, val metadata: Boolean, val body: String)
    private var cachedRecord: CachedRecord? = null

    fun relationships(): InstagramArchive = InstagramArchive.read(zip)

    fun contentHistory(): ContentHistory {
        val sources = Regex("(your_instagram_activity/media|logged_information/past_instagram_insights)/(stories|reels|posts)(?:_[1-9]\\d{0,3})?\\.json")
        val mediaFiles = files.filter { it.kind in setOf("image", "video") && it.size <= MAX_PREVIEW }.associateBy { it.name }
        val rows = mutableListOf<Content>()
        val references = mutableMapOf<Pair<String, String>, Int>()
        var remaining = MAX_TEXT
        var skipped = 0
        val aliases = mapOf("views" to listOf("Instagram Views", "Views", "views", "view_count"),
            "plays" to listOf("Instagram Plays", "Plays", "play_count"),
            "likes" to listOf("Instagram Likes", "Likes", "like_count"),
            "comments" to listOf("Instagram Comments", "Comments", "comment_count"),
            "reactions" to listOf("Reactions"))
        for ((file, source) in files.mapNotNull { entry -> sources.matchEntire(entry.name)?.let { entry to it } }
            .sortedBy { it.second.groupValues[1].startsWith("logged_information") }) {
            if (file.size !in 1..remaining) { skipped++; continue }
            remaining -= file.size
            val document = runCatching { parse(file) }.getOrNull()
            if (document == null) { skipped++; continue }
            val kind = source.groupValues[2]
            val insights = source.groupValues[1].startsWith("logged_information")
            for (row in document.rows.filterIsInstance<JSONObject>()) {
                val fields = row.optJSONObject("string_map_data")
                val assets = buildList {
                    val list = row.optJSONArray("media")
                    if (list == null) add(row) else for (i in 0 until list.length()) list.optJSONObject(i)?.let(::add)
                    row.optJSONObject("media_map_data")?.optJSONObject("Media Thumbnail")?.let(::add)
                }
                val time = (listOf("Creation Timestamp", "Upload Timestamp").map {
                    fields?.optJSONObject(it)?.opt("timestamp")
                } + listOf(row.opt("creation_timestamp"), row.opt("timestamp")) + assets.map { it.opt("creation_timestamp") })
                    .firstNotNullOfOrNull {
                        val stamp = when (it) { is Long -> it; is Int -> it.toLong(); else -> return@firstNotNullOfOrNull null }
                        (if (stamp >= 1_000_000_000_000) stamp / 1000 else stamp).takeIf { value -> value in 946684800L until 4102444800L }
                    } ?: continue
                val refs = assets.mapNotNull { (it.opt("uri") as? String)?.takeIf { uri -> uri.length in 1..1024 } }
                    .distinct().take(16)
                val caption = (listOf(fields?.optJSONObject("Caption")?.opt("value"), row.opt("title")) +
                    assets.map { it.opt("title") }).filterIsInstance<String>().firstOrNull { it.isNotBlank() }.orEmpty().take(500)
                val metrics = aliases.mapNotNull { (key, names) ->
                    names.firstNotNullOfOrNull { alias ->
                        val value = fields?.optJSONObject(alias)?.opt("value") ?: row.opt(alias)
                        val number = when (value) {
                            is Long -> value
                            is Int -> value.toLong()
                            is String -> value.takeIf { it.matches(Regex("[0-9]{1,15}")) }?.toLongOrNull()
                            else -> null
                        }
                        number?.takeIf { it in 0 until 1_000_000_000_000_000 }
                    }?.let { key to it }
                }.toMap()
                val item = Content(kind, time, caption, refs.mapNotNull(mediaFiles::get), metrics)
                val matches = refs.mapNotNull { references[kind to it] }.toSet()
                val match = matches.singleOrNull()?.takeIf { it >= 0 }
                if (insights && match != null) {
                    val prior = rows[match]
                    rows[match] = prior.copy(caption = prior.caption.ifEmpty { caption },
                        metrics = prior.metrics + metrics, media = (prior.media + item.media).distinct().take(16))
                } else {
                    if (!insights) for (ref in refs) {
                        val key = kind to ref
                        references[key] = if (key in references) -1 else rows.size
                    }
                    rows.add(item)
                }
            }
        }
        return ContentHistory(rows.sortedByDescending { it.time }, skipped)
    }

    fun coverage(name: String?): Coverage {
        val names = files.mapTo(hashSetOf()) { it.name }
        val footer = metadataText("start_here.html")?.let {
            org.jsoup.Jsoup.parse(it).select("aside[role=contentinfo]").singleOrNull()
        }
        val time = footer?.selectFirst("time[datetime]")
        val before = time?.previousSibling()?.toString()?.trim().orEmpty()
        val footerUser = Regex("Generated by ([A-Za-z0-9._]{1,30}) on", RegexOption.IGNORE_CASE)
            .matchEntire(before)?.groupValues?.get(1)?.lowercase(Locale.ROOT)
        val jsonUser = runCatching {
            val profiles = JSONObject(requireNotNull(metadataText("personal_information/personal_information/personal_information.json")))
                .getJSONArray("profile_user")
            require(profiles.length() == 1)
            val username = profiles.getJSONObject(0).getJSONObject("string_map_data").getJSONObject("Username").getString("value")
            username.takeIf { it.matches(Regex("[A-Za-z0-9._]{1,30}")) }?.lowercase(Locale.ROOT)
        }.getOrNull()
        val account = if (footerUser != null && jsonUser != null && footerUser != jsonUser) null else footerUser ?: jsonUser
        val stamp = if (footerUser != null) runCatching {
            java.time.OffsetDateTime.parse(time?.attr("datetime")).toInstant().toString()
        }.getOrNull() else null
        val filenameDate = if (stamp == null && name != null) Regex(
            "instagram-[A-Za-z0-9._]{1,30}-(\\d{4}-\\d{2}-\\d{2})-[A-Za-z0-9]{8}\\.zip", RegexOption.IGNORE_CASE
        ).matchEntire(name)?.groupValues?.get(1)?.let { runCatching { java.time.LocalDate.parse(it).toString() }.getOrNull() } else null
        val dateRange = footer?.select("div")?.firstOrNull {
            it.ownText().startsWith("Contains data you requested from", ignoreCase = true)
        }?.select("time[datetime]")?.takeIf { it.size == 2 }?.let {
            runCatching {
                val from = java.time.OffsetDateTime.parse(it[0].attr("datetime"))
                val to = java.time.OffsetDateTime.parse(it[1].attr("datetime"))
                require(!from.isAfter(to))
                from.toLocalDate().toString() to to.toLocalDate().toString()
            }.getOrNull()
        }
        fun hasList(direction: String) = names.any {
            val parts = it.split('/')
            parts.getOrNull(parts.lastIndex - 1) == "followers_and_following" &&
                parts.last().matches(Regex("${direction}(?:_[1-9]\\d{0,3})?\\.(json|html)"))
        }
        val message = Regex("(?:your_instagram_activity/)?messages/(?:[^/]+/)+message_\\d+\\.(?:json|html)")
        return Coverage(name, account, stamp, filenameDate, dateRange, hasList("followers"), hasList("following"),
            names.any(message::matches), "your_instagram_activity/messages/no-data.txt" in names || "messages/no-data.txt" in names)
    }

    private fun metadataText(name: String): String? = runCatching {
        val file = files.singleOrNull { it.name == name && it.size in 1..262_144 } ?: return@runCatching null
        zip.getInputStream(entries[file.index]).use { input ->
            val bytes = ByteArray(file.size.toInt())
            java.io.DataInputStream(input).readFully(bytes)
            require(input.read() == -1)
            bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        }
    }.getOrNull()

    fun recordCount(fileName: String): Int? {
        val file = files.singleOrNull { it.name == fileName } ?: return null
        if (file.kind != "json" || file.size > MAX_TEXT) return null
        return parse(file).rows.size
    }

    fun likedMonths(): List<Pair<String, Int?>> {
        val pattern = Regex("your_instagram_activity/likes/liked_posts(?:_[1-9]\\d{0,3})?\\.json")
        val sources = files.filter { pattern.matches(it.name) }
        if (sources.sumOf { it.size } > MAX_TEXT) return emptyList()
        val counts = sortedMapOf<java.time.YearMonth, Int>()
        for (file in sources) {
            val rows = runCatching { parse(file).rows }.getOrNull() ?: return emptyList()
            for (row in rows.filterIsInstance<JSONObject>()) {
                val values = row.optJSONArray("string_list_data")
                val stamp = row.optLong("timestamp").takeIf { it > 0 }
                    ?: values?.optJSONObject(0)?.optLong("timestamp") ?: continue
                if (stamp !in 946684800L until 4102444800L) continue
                val month = java.time.YearMonth.from(java.time.Instant.ofEpochSecond(stamp).atOffset(java.time.ZoneOffset.UTC))
                counts[month] = (counts[month] ?: 0) + 1
            }
        }
        if (counts.size < 2) return emptyList()
        val last = counts.lastKey()
        val first = maxOf(counts.firstKey(), last.minusMonths(11))
        return generateSequence(first) { it.plusMonths(1).takeIf { next -> next <= last } }
            .map { it.toString() to counts[it] }.toList()
    }

    fun search(query: String, startFile: Int, startRecord: Int): SearchPage {
        require(query.length in 2..160 && startFile in 0..files.size && startRecord >= -1)
        var index = startFile
        var record = startRecord
        var examined = 0
        var budget = MAX_TEXT
        var skipped = 0
        val hits = mutableListOf<SearchHit>()
        while (index < files.size && index - startFile < 32 && examined < 20_000 && hits.size < 24) {
            val file = files[index]
            if (record == -1) {
                record = 0
                if (file.name.contains(query, ignoreCase = true)) hits.add(SearchHit(file, null, ""))
                if (hits.size == 24) break
            }
            if (file.kind == "json" && file.size <= MAX_TEXT && file.size > budget) break
            val rows = if (file.kind == "json") {
                if (file.size in 0..MAX_TEXT) budget -= file.size
                runCatching {
                    (document?.takeIf { it.index == file.index } ?: parse(file).also {
                        document = it
                        cachedRecord = null
                    }).rows
                }.getOrElse { skipped++; emptyList() }
            } else emptyList()
            while (record < rows.size && examined < 20_000 && hits.size < 24) {
                val value = matchValue(rows[record], query)
                if (value != null) {
                    val position = value.indexOf(query, ignoreCase = true)
                    val from = (position - 20).coerceAtLeast(0)
                    val end = minOf(value.length, from + 180)
                    hits.add(SearchHit(file, record, (if (from > 0) "…" else "") + value.substring(from, end) +
                        (if (end < value.length) "…" else "")))
                }
                record++
                examined++
            }
            if (record < rows.size) break
            index++
            record = -1
        }
        return SearchPage(hits, index, record, skipped)
    }

    private fun matchValue(value: Any?, query: String, depth: Int = 0): String? {
        if (depth > 48) return null
        return when (value) {
            is String -> value.takeIf { it.contains(query, ignoreCase = true) }
            is JSONObject -> value.keys().asSequence().firstNotNullOfOrNull { matchValue(value.opt(it), query, depth + 1) }
            is JSONArray -> (0 until value.length()).firstNotNullOfOrNull { matchValue(value.opt(it), query, depth + 1) }
            else -> null
        }
    }

    fun text(file: Entry, record: Int, offset: Int, metadata: Boolean = false): Page {
        require(file.kind == "json" || file.kind == "text")
        val source = document?.takeIf { it.index == file.index } ?: parse(file).also {
            document = it
            cachedRecord = null
        }
        val rows = if (metadata) listOf(requireNotNull(source.metadata)) else source.rows
        val section = if (metadata) "" else source.section
        if (rows.isEmpty()) return Page(section, 0, "", null, emptyList(), source.metadata != null)
        val value = rows[record]
        val body = cachedRecord?.takeIf { it.index == record && it.metadata == metadata }?.body ?: run {
            val text = when (value) {
                is JSONObject -> value.toString(2)
                is JSONArray -> value.toString(2)
                is String -> if (file.kind == "json") JSONObject.quote(value) else value
                else -> value.toString()
            }
            cachedRecord = CachedRecord(record, metadata, text)
            text
        }
        require(offset in 0..body.length)
        val end = minOf(offset + PAGE, body.length)
        // Keep a surrogate pair together when a page ends inside it.
        val boundary = if (end < body.length && end > offset && body[end - 1].isHighSurrogate()) end - 1 else end
        val fields = if (file.kind == "json" && offset == 0 && boundary == body.length) fields(value) else emptyList()
        return Page(section, rows.size, body.substring(offset, boundary), boundary.takeIf { it < body.length }, fields, source.metadata != null)
    }

    private fun fields(record: Any?): List<Pair<String, String>> = buildList {
        fun visit(value: Any?, path: String, depth: Int) {
            if (size >= 75 || depth > 12) return
            fun child(name: String) = if (path.isEmpty()) name else "$path › $name"
            when (value) {
                is JSONObject -> {
                    val keys = value.keys()
                    while (size < 75 && keys.hasNext()) {
                        val key = keys.next()
                        visit(value.opt(key), child(key), depth + 1)
                    }
                }
                is JSONArray -> {
                    for (index in 0 until value.length()) {
                        if (size >= 75) break
                        visit(value.opt(index), child(index.toString()), depth + 1)
                    }
                }
                null, JSONObject.NULL, "" -> Unit
                else -> add(path to value.toString())
            }
        }
        visit(record, "", 0)
    }

    private fun parse(file: Entry): Document {
        require(file.size in 0..MAX_TEXT)
        val output = java.io.ByteArrayOutputStream(file.size.toInt())
        copy(file, output, MAX_TEXT)
        val text = output.toString(Charsets.UTF_8.name()).removePrefix("\uFEFF")
        if (file.kind != "json") return Document(file.index, "", listOf(text))
        val input = JSONTokener(text)
        var root = input.nextValue()
        require(input.nextClean() == '\u0000')
        var section = ""
        var metadata: JSONObject? = null
        val messageRoot = (root as? JSONObject)?.takeIf { file.name.startsWith("your_instagram_activity/messages/") }
        val messages = messageRoot?.optJSONArray("messages")
        if (messages != null) {
            messageRoot.remove("messages")
            metadata = messageRoot
            section = "messages"
            root = messages
        }
        if (root is JSONObject && root.length() == 1) {
            val key = root.keys().next()
            val value = root.optJSONArray(key)
            if (value != null) {
                section = key
                root = value
            }
        }
        val rows = if (root is JSONArray) List(root.length()) { root.opt(it) } else listOf(root)
        return Document(file.index, section, rows, metadata)
    }

    fun copy(file: Entry, output: OutputStream, limit: Long = MAX_COPY) {
        val entry = entries[file.index]
        require(entry.size in 0..limit)
        val crc = CRC32()
        var total = 0L
        zip.getInputStream(entry).use { source ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                check(!Thread.currentThread().isInterrupted)
                val size = source.read(buffer)
                if (size < 0) break
                total += size
                require(total <= entry.size)
                crc.update(buffer, 0, size)
                output.write(buffer, 0, size)
            }
        }
        require(total == entry.size && crc.value == entry.crc)
    }

    override fun close() {
        document = null
        cachedRecord = null
        zip.close()
    }

    companion object {
        fun documentName(context: Context, uri: Uri): String? = runCatching {
            if (uri.scheme == "file") uri.lastPathSegment else
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }
        }.getOrNull()
        const val PAGE = 24_000
        const val MAX_TEXT = 24L * 1024 * 1024
        const val MAX_PREVIEW = 32L * 1024 * 1024
        const val MAX_COPY = 512L * 1024 * 1024
        const val MAX_ARCHIVE = 8L * 1024 * 1024 * 1024

        fun openDocument(context: Context, uri: Uri): Pair<ExportBrowser, File?> {
            val resolver = context.contentResolver
            resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                if (descriptor.statSize > 0) {
                    val direct = runCatching {
                        open(File("/proc/self/fd/${descriptor.fd}"), descriptor.statSize)
                    }.getOrNull()
                    if (direct != null) return direct to null
                }
            }
            val temporary = File.createTempFile("instagram-full-export-", ".zip", context.cacheDir)
            try {
                requireNotNull(resolver.openInputStream(uri)).use { spool(it, temporary) }
                return open(temporary) to temporary
            } catch (failure: Exception) {
                temporary.delete()
                throw failure
            }
        }

        fun open(file: File, size: Long = file.length()): ExportBrowser {
            require(size in 1..MAX_ARCHIVE)
            val zip = ZipFile(file)
            try {
                require(zip.size() in 1..50_000)
                return ExportBrowser(zip).also {
                    require(it.files.isNotEmpty() && it.files.sumOf { entry -> entry.name.length } <= 2_000_000)
                }
            } catch (failure: Exception) {
                zip.close()
                throw failure
            }
        }

        fun spool(source: InputStream, target: File) {
            target.outputStream().buffered().use { output ->
                val buffer = ByteArray(128 * 1024)
                var total = 0L
                while (true) {
                    check(!Thread.currentThread().isInterrupted)
                    val size = source.read(buffer)
                    if (size < 0) break
                    total += size
                    require(total <= MAX_ARCHIVE)
                    output.write(buffer, 0, size)
                }
            }
        }
    }
}
