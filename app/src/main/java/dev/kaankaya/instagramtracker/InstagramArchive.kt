package dev.kaankaya.instagramtracker

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URI
import java.nio.ByteBuffer
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

internal data class InstagramArchiveRow(val username: String, val timestamp: Long? = null)

internal data class InstagramArchive(val followers: List<InstagramArchiveRow>?, val following: List<InstagramArchiveRow>?) {
    companion object {
        private const val MAX_ZIP = 512L * 1024 * 1024
        private const val MAX_ENTRY = 16 * 1024 * 1024
        private const val MAX_TOTAL_ENTRIES = 64L * 1024 * 1024
        private const val MAX_NAMES = 100_000
        private val entryName = Regex("(followers|following)(?:_([1-9][0-9]{0,3}))?\\.(json|html)")
        private val username = Regex("[A-Za-z0-9._]{1,30}")

        fun read(input: InputStream, cache: File): InstagramArchive {
            // The document provider can supply a non-seekable stream. The ZIP
            // reader uses a private temporary file to skip unrelated entries.
            val file = File.createTempFile("instagram-archive-", ".zip", cache)
            try {
                file.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        check(!Thread.currentThread().isInterrupted)
                        val size = input.read(buffer)
                        if (size < 0) break
                        total += size
                        require(total <= MAX_ZIP)
                        output.write(buffer, 0, size)
                    }
                }
                return read(file)
            } finally {
                file.delete()
            }
        }

        fun read(file: File): InstagramArchive {
            require(file.length() in 1..MAX_ZIP)
            ZipFile(file).use { zip ->
                val parents = mutableSetOf<String>()
                val formats = mutableSetOf<String>()
                val parts = mapOf("followers" to sortedMapOf<Int, ZipEntry>(),
                    "following" to sortedMapOf<Int, ZipEntry>())
                var total = 0L
                for (entry in zip.entries()) {
                    val path = entry.name.split('/')
                    val match = entryName.matchEntire(path.last()) ?: continue
                    if (path.getOrNull(path.lastIndex - 1) != "followers_and_following") continue
                    require(!entry.isDirectory && path.none { it.isEmpty() || it == "." || it == ".." } && '\\' !in entry.name)
                    require(entry.size in 1..MAX_ENTRY.toLong())
                    total += entry.size
                    require(total <= MAX_TOTAL_ENTRIES)
                    val (direction, number, format) = match.destructured
                    formats.add(format)
                    val part = number.toIntOrNull() ?: 1
                    require(parts.getValue(direction).put(part, entry) == null)
                    parents.add(path.dropLast(1).joinToString("/"))
                }
                require(parents.size == 1 && formats.size == 1)
                val html = formats.single() == "html"
                return InstagramArchive(readParts(zip, "followers", parts.getValue("followers"), html),
                    readParts(zip, "following", parts.getValue("following"), html))
            }
        }

        private fun readParts(zip: ZipFile, direction: String, parts: Map<Int, ZipEntry>, html: Boolean): List<InstagramArchiveRow>? {
            if (parts.isEmpty()) return null
            require(parts.keys.toList() == (1..parts.size).toList())
            val dates = sortedMapOf<String, Long?>()
            var count = 0
            for (entry in parts.values) {
                check(!Thread.currentThread().isInterrupted)
                val output = ByteArrayOutputStream()
                val crc = CRC32()
                zip.getInputStream(entry).use { input ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val size = input.read(buffer)
                        if (size < 0) break
                        require(output.size() + size <= entry.size)
                        output.write(buffer, 0, size)
                        crc.update(buffer, 0, size)
                    }
                }
                require(output.size().toLong() == entry.size && crc.value == entry.crc)
                val text = Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(output.toByteArray())).toString()
                val clean = text.removePrefix("\uFEFF")
                val rows = if (html) htmlNames(clean).map { InstagramArchiveRow(it) } else jsonRows(clean, direction)
                for ((name, timestamp) in rows) {
                    count++
                    require(count <= MAX_NAMES)
                    dates[name] = if (name in dates && dates[name] != timestamp) null else timestamp
                }
            }
            return dates.map { (name, timestamp) -> InstagramArchiveRow(name, timestamp) }
        }

        private fun htmlNames(text: String): Sequence<String> {
            val main = Jsoup.parse(text).getElementsByTag("main").single()
            return main.getElementsByTag("a").asSequence().map { requireNotNull(linkName(it.attr("href"))) }
        }

        private fun jsonRows(text: String, direction: String): Sequence<InstagramArchiveRow> {
            val decoder = JSONTokener(text)
            val document = decoder.nextValue()
            require(decoder.nextClean() == '\u0000')
            val rows = if (document is JSONObject) document.getJSONArray("relationships_$direction") else document as JSONArray
            require(rows.length() <= MAX_NAMES)
            return (0 until rows.length()).asSequence().map { index ->
                val row = rows.getJSONObject(index)
                val items = row.getJSONArray("string_list_data")
                require(items.length() == 1)
                val item = items.getJSONObject(0)
                val candidates = setOf(name(item.opt("value")), name(row.opt("title")), linkName(item.opt("href"))).filterNotNull()
                require(candidates.size == 1)
                val value = item.opt("timestamp")
                val timestamp = if (value is Int || value is Long) (value as Number).toLong() else null
                InstagramArchiveRow(candidates.single(), timestamp?.takeIf { it in 1..253402300799L })
            }
        }

        private fun name(value: Any?): String? =
            (value as? String)?.takeIf { username.matches(it) && it.any { c -> c != '.' } }?.lowercase(Locale.ROOT)

        private fun linkName(value: Any?): String? {
            if (value !is String) return null
            val uri = URI(value)
            if (uri.scheme != "https" || uri.rawAuthority?.lowercase(Locale.ROOT) !in setOf("instagram.com", "www.instagram.com")) return null
            return name(uri.rawPath.trim('/').removePrefix("_u/"))
        }
    }
}
