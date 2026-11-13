package dev.kaankaya.instagramtracker

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
        if (root is JSONObject && file.name.startsWith("your_instagram_activity/messages/")) {
            val messages = root.optJSONArray("messages")
            if (messages != null) {
                root.remove("messages")
                metadata = root
                section = "messages"
                root = messages
            }
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
        const val PAGE = 24_000
        const val MAX_TEXT = 24L * 1024 * 1024
        const val MAX_PREVIEW = 32L * 1024 * 1024
        const val MAX_COPY = 512L * 1024 * 1024
        const val MAX_ARCHIVE = 8L * 1024 * 1024 * 1024

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
