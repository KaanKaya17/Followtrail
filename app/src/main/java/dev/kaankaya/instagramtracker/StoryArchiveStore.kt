package dev.kaankaya.instagramtracker

import android.content.Context
import android.graphics.BitmapFactory
import android.util.AtomicFile
import java.io.File
import java.security.MessageDigest

object StoryMedia {
    fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    fun header(bytes: ByteArray, kind: StoryKind): String? = when {
        kind == StoryKind.VIDEO && bytes.size >= 12 && String(bytes, 4, 4, Charsets.US_ASCII) == "ftyp" -> "video/mp4"
        kind != StoryKind.IMAGE -> null
        bytes.size >= 8 && bytes.take(8) == listOf(137,80,78,71,13,10,26,10).map { it.toByte() } -> "image/png"
        bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte() -> "image/jpeg"
        bytes.size >= 12 && String(bytes,0,4,Charsets.US_ASCII) == "RIFF" && String(bytes,8,4,Charsets.US_ASCII) == "WEBP" -> "image/webp"
        else -> null
    }
    fun validate(bytes: ByteArray, kind: StoryKind): String {
        if (bytes.isEmpty() || bytes.size > StoryLimits.MAX_MEDIA) throw ApiFailure(ApiFailure.Kind.CAPACITY)
        val mime = header(bytes, kind) ?: throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
        if (kind == StoryKind.IMAGE) {
            bitmap(bytes,1024).recycle()
        }
        return mime
    }
    fun bitmap(bytes: ByteArray, maxSide: Int = 2048): android.graphics.Bitmap {
        require(maxSide in 1..2048)
        if (bytes.isEmpty() || bytes.size > StoryLimits.MAX_MEDIA) throw ApiFailure(ApiFailure.Kind.CAPACITY)
        val mime = header(bytes,StoryKind.IMAGE) ?: throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        if (bounds.outWidth !in 1..StoryLimits.MAX_SIDE || bounds.outHeight !in 1..StoryLimits.MAX_SIDE ||
            bounds.outWidth.toLong() * bounds.outHeight > StoryLimits.MAX_PIXELS || bounds.outMimeType != mime)
            throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
        var sample = 1
        while (maxOf(bounds.outWidth,bounds.outHeight) / sample > maxSide) sample *= 2
        return BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
    }
}

/** Private, account-owned encrypted index and media. Plaintext media never touches disk. */
class StoryArchiveStore(private val context: Context, val owner: String, private val slot: String = AccountCatalog(context).storage(owner)) {
    companion object {
        private val lock = Any()
        private data class Cached(val stamp: String, val value: StoryArchive)
        private val cache = object : LinkedHashMap<String,Cached>(2,0.75f,true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String,Cached>?) = size > 2
        }
        /** SessionVault owns this namespace even if its account/index can no longer be decrypted. */
        internal fun clearNamespace(context: Context, slot: String) = synchronized(lock) {
            require(slot.matches(Regex("[a-z]+")))
            val prefix = context.noBackupFilesDir.absolutePath + "/$slot/"
            cache.keys.removeAll { it.startsWith(prefix) }
            val root = File(context.noBackupFilesDir,"storymedia/$slot")
            root.listFiles().orEmpty().forEach { file ->
                if (!file.name.matches(Regex("story-[a-f0-9]{64}\\.vault(\\.bak|\\.new)?"))) throw ApiFailure(ApiFailure.Kind.STORAGE)
                if (!file.delete() && file.exists()) throw ApiFailure(ApiFailure.Kind.STORAGE)
            }
            EncryptedRecord(context,slot + "stories",StoryLimits.MAX_INDEX).clear()
            LocalCipher("instagramtracker.$slot.storymedia.v1",StoryLimits.MAX_MEDIA).destroy()
            if (root.exists() && !root.delete()) throw ApiFailure(ApiFailure.Kind.STORAGE)
        }
    }
    init { InstagramIdentity.id(owner); require(slot.matches(Regex("[a-z]+"))) }
    private val root = File(context.noBackupFilesDir, "storymedia/$slot")
    private val index = EncryptedRecord(context, slot + "stories", StoryLimits.MAX_INDEX)
    private val cacheKey = context.noBackupFilesDir.absolutePath + "/$slot/$owner"
    private val cipher = LocalCipher("instagramtracker.$slot.storymedia.v1", StoryLimits.MAX_MEDIA)
    private fun load(): StoryArchive = try {
        val stamp = index.stamp()
        cache[cacheKey]?.takeIf { it.stamp == stamp }?.value ?: (
            index.read()?.let(StoryArchiveCodec::decode)?.also { require(it.owner == owner) } ?: StoryArchive(owner)
        ).also { cache[cacheKey] = Cached(index.stamp(),it) }
    } catch (_: Exception) { throw ApiFailure(ApiFailure.Kind.STORAGE) }
    private fun write(value: StoryArchive) {
        try {
            require(value.owner == owner)
            val (raw, canonical) = StoryArchiveCodec.prepare(value)
            index.write(raw); cache[cacheKey] = Cached(index.stamp(),canonical)
        }
        catch (failure: ApiFailure) { throw failure }
        catch (_: Exception) { throw ApiFailure(ApiFailure.Kind.STORAGE) }
    }
    private fun key(target: String, id: String): String {
        InstagramIdentity.id(target); InstagramIdentity.id(id)
        return "story-" + StoryMedia.digest("$owner/$target/$id".toByteArray())
    }
    private fun asset(target: String, id: String) = AtomicFile(File(root, key(target,id) + ".vault"))
    private fun aad(target: String, id: String) = "InstagramTracker/story/v1/$owner/$target/$id".toByteArray(Charsets.UTF_8)
    private fun exists(file: AtomicFile) = file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()
    private fun remove(file: AtomicFile) {
        file.delete()
        if (exists(file) || File(file.baseFile.path + ".new").exists()) throw ApiFailure(ApiFailure.Kind.STORAGE)
    }
    fun read(target: String): StoryTarget = synchronized(lock) { InstagramIdentity.id(target); load().targets[target] ?: StoryTarget() }
    fun contains(target: String, id: String): Boolean = synchronized(lock) {
        val row = load().targets[target]?.items?.firstOrNull { it.facts.id == id }
        row?.saved == true && exists(asset(target,id))
    }
    fun metadata(facts: StoryFacts, now: Long) = synchronized(lock) {
        val value = load(); val target = value.targets[facts.target] ?: StoryTarget()
        val previous = target.items.firstOrNull { it.facts.id == facts.id }
        if (previous?.saved == true && exists(asset(facts.target,facts.id))) return@synchronized
        if (previous == null && value.targets.values.sumOf { it.items.size } >= StoryLimits.MAX_ITEMS) throw ApiFailure(ApiFailure.Kind.CAPACITY)
        val next = ArchivedStory(facts, previous?.capturedAt ?: now)
        write(value.copy(targets = value.targets + (facts.target to target.copy(items = target.items.filterNot { it.facts.id == facts.id } + next))))
    }
    fun saveMedia(facts: StoryFacts, bytes: ByteArray) = synchronized(lock) {
        val value = load(); val target = value.targets[facts.target] ?: throw ApiFailure(ApiFailure.Kind.CANCELLED)
        val prior = target.items.firstOrNull { it.facts.id == facts.id } ?: throw ApiFailure(ApiFailure.Kind.CANCELLED)
        if (prior.saved && exists(asset(facts.target,facts.id))) return@synchronized
        if (bytes.isEmpty() || bytes.size > StoryLimits.MAX_MEDIA ||
            root.listFiles().orEmpty().sumOf { it.length() } + bytes.size.toLong() + 28 > StoryLimits.MAX_BYTES) throw ApiFailure(ApiFailure.Kind.CAPACITY)
        val mime = StoryMedia.validate(bytes, facts.kind)
        val encrypted = cipher.seal(bytes, aad(facts.target,facts.id))
        val file = asset(facts.target,facts.id)
        if (!root.exists() && !root.mkdirs()) throw ApiFailure(ApiFailure.Kind.STORAGE)
        val stream = file.startWrite()
        try { stream.write(encrypted); file.finishWrite(stream) }
        catch (_: Exception) { file.failWrite(stream); throw ApiFailure(ApiFailure.Kind.STORAGE) }
        val row = ArchivedStory(facts, prior.capturedAt, mime, bytes.size, StoryMedia.digest(bytes))
        try { write(value.copy(targets = value.targets + (facts.target to target.copy(items = target.items.map { if (it.facts.id == facts.id) row else it })))) }
        catch (failure: Exception) { remove(file); throw failure }
    }
    fun checked(target: String, now: Long, summary: StorySummary) = synchronized(lock) {
        val value = load(); val current = value.targets[target] ?: StoryTarget()
        write(value.copy(targets = value.targets + (target to current.copy(checkedAt = now, summary = summary))))
    }
    fun media(target: String, id: String): Pair<ArchivedStory, ByteArray> = synchronized(lock) {
        val row = load().targets[target]?.items?.firstOrNull { it.facts.id == id && it.saved } ?: throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
        val file = asset(target,id)
        val bytes = try { file.openRead().use { input ->
            val raw = input.readBytesBounded(StoryLimits.MAX_MEDIA + 28)
            cipher.open(raw, aad(target,id))
        } } catch (_: Exception) { throw ApiFailure(ApiFailure.Kind.STORAGE) }
        if (bytes.size != row.bytes || StoryMedia.digest(bytes) != row.digest || StoryMedia.header(bytes,row.facts.kind) != row.mime)
            throw ApiFailure(ApiFailure.Kind.STORAGE)
        row to bytes
    }
    fun clearTarget(target: String) = synchronized(lock) {
        InstagramIdentity.id(target)
        val value = load()
        if (target !in value.targets) return@synchronized
        value.targets[target]?.items.orEmpty().forEach { remove(asset(target,it.facts.id)) }
        write(value.copy(targets = value.targets - target))
    }
    fun prune(before: Long) = synchronized(lock) {
        if (before == 0L || !index.exists()) return@synchronized
        val value = load()
        val expired = value.targets.flatMap { (target,data) -> data.items.filter { it.capturedAt < before }.map { target to it } }
        if (expired.isEmpty()) return@synchronized
        expired.forEach { (target,row) -> remove(asset(target,row.facts.id)) }
        write(value.copy(targets = value.targets.mapValues { (_, data) -> data.copy(items = data.items.filter { it.capturedAt >= before }) }))
    }
    fun clearAll() = clearNamespace(context,slot)
}

internal fun java.io.InputStream.readBytesBounded(max: Int): ByteArray {
    val result = java.io.ByteArrayOutputStream(); val buffer = ByteArray(65536)
    while (true) {
        val count = read(buffer); if (count < 0) break
        if (count > max - result.size()) throw ApiFailure(ApiFailure.Kind.CAPACITY)
        result.write(buffer,0,count)
    }
    return result.toByteArray()
}
