package dev.kaankaya.instagramtracker

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URI
import java.nio.ByteBuffer
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

internal data class InstagramArchive(val followers: List<String>?, val following: List<String>?) {
    companion object {
        private const val MAX_ZIP = 512L * 1024 * 1024
        private const val MAX_JSON = 16 * 1024 * 1024
        private const val MAX_TOTAL_JSON = 64L * 1024 * 1024
        private const val MAX_NAMES = 100_000
        private val entryName = Regex("(followers|following)(?:_([1-9][0-9]{0,3}))?\\.json")
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
                val parts = mapOf("followers" to sortedMapOf<Int, ZipEntry>(),
                    "following" to sortedMapOf<Int, ZipEntry>())
                var total = 0L
                for (entry in zip.entries()) {
                    val path = entry.name.split('/')
                    val match = entryName.matchEntire(path.last()) ?: continue
                    if (path.getOrNull(path.lastIndex - 1) != "followers_and_following") continue
                    require(!entry.isDirectory && path.none { it.isEmpty() || it == "." || it == ".." } && '\\' !in entry.name)
                    require(entry.size in 1..MAX_JSON.toLong())
                    total += entry.size
                    require(total <= MAX_TOTAL_JSON)
                    val (direction, number) = match.destructured
                    val part = number.toIntOrNull() ?: 1
                    require(parts.getValue(direction).put(part, entry) == null)
                    parents.add(path.dropLast(1).joinToString("/"))
                }
                require(parents.size == 1)
                return InstagramArchive(readParts(zip, "followers", parts.getValue("followers")),
                    readParts(zip, "following", parts.getValue("following")))
            }
        }

        private fun readParts(zip: ZipFile, direction: String, parts: Map<Int, ZipEntry>): List<String>? {
            if (parts.isEmpty()) return null
            require(parts.keys.toList() == (1..parts.size).toList())
            val names = mutableSetOf<String>()
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
                val decoder = JSONTokener(text.removePrefix("\uFEFF"))
                val document = decoder.nextValue()
                require(decoder.nextClean() == '\u0000')
                val rows = if (document is JSONObject) document.getJSONArray("relationships_$direction") else document as JSONArray
                count += rows.length()
                require(count <= MAX_NAMES)
                for (index in 0 until rows.length()) {
                    val row = rows.getJSONObject(index)
                    val items = row.getJSONArray("string_list_data")
                    require(items.length() == 1)
                    val item = items.getJSONObject(0)
                    val candidates = setOf(name(item.opt("value")), name(row.opt("title")), linkName(item.opt("href"))).filterNotNull()
                    require(candidates.size == 1)
                    names.add(candidates.single())
                }
            }
            return names.sorted()
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
