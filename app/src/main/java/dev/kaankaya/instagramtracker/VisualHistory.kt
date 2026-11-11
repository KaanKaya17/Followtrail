package dev.kaankaya.instagramtracker

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Change-only archive; independent of the rolling counter history. No network operations. */
object VisualHistory {
    const val MAX_ENTRIES = 200
    const val MAX_IMAGES = 24
    const val MAX_BYTES = 1800 * 1024
    fun empty(owner: String) = JSONObject().put("owner", owner).put("profiles", JSONObject()).put("images", JSONObject())
    fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    fun target(data: JSONObject, id: String): JSONObject = data.getJSONObject("profiles").optJSONObject(id)
        ?: JSONObject().put("enabled", false).put("full", false).put("entries", JSONArray()).also { data.getJSONObject("profiles").put(id, it) }
    fun observe(data: JSONObject, p: Profile, image: Pair<String, String>? = null) {
        val t = data.getJSONObject("profiles").optJSONObject(p.userId) ?: return
        if (!t.getBoolean("enabled")) return
        val entries = t.getJSONArray("entries")
        val old = if (entries.length() > 0) entries.getJSONObject(entries.length()-1) else null
        if (old != null && old.getLong("lastSeen") > p.observedAt) return
        val next = JSONObject().put("at", p.observedAt).put("lastSeen", p.observedAt)
        for ((key, value) in listOf("bio" to p.details.biography.value, "picture" to ProfileChanges.pictureIdentity(p))) {
            val known = value ?: old?.optString(key)?.takeIf { old.has(key) }
            if (known != null) next.put(key, known)
        }
        if (old != null && next.optString("picture") == old.optString("picture") && old.has("image")) next.put("image", old.getString("image"))
        val images = data.getJSONObject("images")
        if (image != null && next.optString("picture") != "anonymous") {
            val known = (0 until entries.length()).mapNotNull { entries.getJSONObject(it).optString("image").takeIf(String::isNotEmpty) }.toSet()
            if (image.first in known || known.size < MAX_IMAGES) next.put("image", image.first) else t.put("full", true)
        }
        if (next.optString("picture") == "anonymous") next.remove("image")
        val changed = old == null || listOf("bio", "picture", "image").any { old.opt(it) != next.opt(it) }
        if (!changed) { old!!.put("lastSeen", maxOf(old.getLong("lastSeen"), p.observedAt)); return }
        if (entries.length() >= MAX_ENTRIES) { t.put("full", true); return }
        val key = image?.first?.takeIf { next.optString("image") == it }
        val addedImage = key != null && !images.has(key)
        if (addedImage) images.put(key, image!!.second)
        entries.put(next)
        if (data.toString().toByteArray(Charsets.UTF_8).size > MAX_BYTES) {
            entries.remove(entries.length()-1)
            if (addedImage) images.remove(key)
            t.put("full", true)
        }
    }
    fun clear(data: JSONObject, id: String) {
        data.getJSONObject("profiles").remove(id)
        collect(data)
    }
    fun collect(data: JSONObject) {
        val used = mutableSetOf<String>()
        val profiles = data.getJSONObject("profiles")
        profiles.keys().forEach { owner -> profiles.getJSONObject(owner).getJSONArray("entries").let { a ->
            for (i in 0 until a.length()) a.getJSONObject(i).optString("image").takeIf(String::isNotEmpty)?.let(used::add)
        } }
        val images = data.getJSONObject("images")
        images.keys().asSequence().toList().filterNot(used::contains).forEach(images::remove)
    }
    fun prune(data: JSONObject, before: Long) {
        if (before == 0L) return
        val profiles = data.getJSONObject("profiles")
        profiles.keys().forEach { id ->
            val target = profiles.getJSONObject(id); val old = target.getJSONArray("entries")
            val kept = (0 until old.length()).map(old::getJSONObject).filter { it.getLong("lastSeen") >= before }
            if (kept.size != old.length()) target.put("full",false).put("entries",JSONArray(kept))
        }
        collect(data)
    }
    fun validate(data: JSONObject) {
        require(data.getString("owner").matches(Regex("[1-9][0-9]{0,29}")))
        require(data.toString().toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        val profiles = data.getJSONObject("profiles")
        require(profiles.length() <= PeoplePolicy.MAX_PEOPLE + 1)
        val images = data.getJSONObject("images")
        images.keys().forEach { key -> require(key.matches(Regex("[a-f0-9]{64}")) && images.getString(key).length <= 90_000) }
        profiles.keys().forEach { id ->
            require(id.matches(Regex("[1-9][0-9]{0,29}")))
            val t = profiles.getJSONObject(id); require(t.get("enabled") is Boolean && t.get("full") is Boolean)
            val a = t.getJSONArray("entries"); require(a.length() <= MAX_ENTRIES)
            var time = 0L
            for (i in 0 until a.length()) {
                val e = a.getJSONObject(i); require(e.getLong("at") >= time && e.getLong("lastSeen") >= e.getLong("at")); time = e.getLong("at")
                if (e.has("bio")) require(e.getString("bio").toByteArray().size <= ProfileDetailsParser.TEXT_BYTES)
                if (e.has("image")) require(images.has(e.getString("image")))
            }
        }
    }
}

object BiographyDiff {
    data class Part(val kind: String, val text: String)
    private fun tokens(value: String) = Regex("\\s+|https?://[^\\s]+|[\\p{L}\\p{N}_]+|[^\\s]").findAll(value).map { it.value }.toList()
    fun compare(before: String, after: String): List<Part> {
        val a = tokens(before); val b = tokens(after); val result = mutableListOf<Part>()
        fun add(kind: String, text: String) { if (text.isEmpty()) return; if (result.lastOrNull()?.kind == kind) result[result.lastIndex] = Part(kind, result.last().text + text) else result += Part(kind,text) }
        if (a.size.toLong() * b.size > 160_000) {
            var start = 0; var end = 0
            while (start < minOf(a.size,b.size) && a[start] == b[start]) start++
            while (end < minOf(a.size,b.size)-start && a[a.lastIndex-end] == b[b.lastIndex-end]) end++
            add("same",a.take(start).joinToString("")); add("remove",a.subList(start,a.size-end).joinToString(""))
            add("add",b.subList(start,b.size-end).joinToString("")); add("same",a.takeLast(end).joinToString("")); return result
        }
        val dp = Array(a.size+1) { IntArray(b.size+1) }
        for (i in a.indices.reversed()) for (j in b.indices.reversed()) dp[i][j] = if (a[i] == b[j]) dp[i+1][j+1]+1 else maxOf(dp[i+1][j],dp[i][j+1])
        var i=0; var j=0
        while (i<a.size || j<b.size) when {
            i<a.size && j<b.size && a[i]==b[j] -> { add("same",a[i++]); j++ }
            i<a.size && (j==b.size || dp[i+1][j]>=dp[i][j+1]) -> add("remove",a[i++])
            else -> add("add",b[j++])
        }
        return result
    }
}
