package dev.kaankaya.instagramtracker

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/** Separate Keystore record; thumbnails and biographies never leave this device. */
class VisualHistoryStore(context: Context, slot: String = "account") {
    companion object { private val lock = Any(); @Volatile var writeFailed = false }
    private val record = EncryptedRecord(context, slot + "visual", 2 * 1024 * 1024)
    private val privacy = PrivacyStore(context,slot)
    private fun allowed(owner: String, profile: Profile) = profile.observedAt >= privacy.read(owner).historyBefore(System.currentTimeMillis())
    fun exists() = record.exists()
    private fun load(owner: String): JSONObject = record.read()?.let { JSONObject(it).also { data ->
        VisualHistory.validate(data);require(data.getString("owner")==owner)
    } } ?: VisualHistory.empty(owner)
    private fun save(data: JSONObject) { VisualHistory.validate(data); record.write(data.toString()); writeFailed = false }
    fun read(owner: String) = synchronized(lock) { load(owner) }
    fun configure(account: SavedAccount, id: String, enabled: Boolean) = synchronized(lock) {
        require(id == account.profile.userId || account.people.any { it.latest.userId == id })
        val data = load(account.session.userId)
        VisualHistory.target(data,id).put("enabled",enabled)
        val profile = if (id == account.profile.userId) account.profile else account.people.first { it.latest.userId == id }.latest
        if (enabled && allowed(account.session.userId,profile)) VisualHistory.observe(data,profile)
        save(data)
    }
    fun clear(owner: String, id: String) = synchronized(lock) { val data=load(owner); VisualHistory.clear(data,id); save(data) }
    fun clearAll() = synchronized(lock) { record.clear() }
    fun prune(owner: String, before: Long) = synchronized(lock) {
        if (before != 0L && record.exists()) { val data=load(owner); VisualHistory.prune(data,before); save(data) }
    }
    fun observe(account: SavedAccount) = synchronized(lock) {
        try {
            val data = load(account.session.userId)
            if (data.getJSONObject("profiles").length() == 0) return@synchronized
            val before = data.toString()
            val beforeTime=privacy.read(account.session.userId).historyBefore(System.currentTimeMillis())
            (listOf(account.profile)+account.people.map { it.latest }).filter {it.observedAt>=beforeTime}.forEach { VisualHistory.observe(data,it) }
            if (before != data.toString()) save(data)
        } catch (_: Exception) { writeFailed = true }
    }
    fun avatar(owner: String, profile: Profile, bitmap: Bitmap) = synchronized(lock) {
        try {
            if (!allowed(owner,profile)) return@synchronized
            val data = load(owner)
            if (data.getJSONObject("profiles").optJSONObject(profile.userId)?.optBoolean("enabled") != true) return@synchronized
            val small = Bitmap.createScaledBitmap(bitmap, minOf(bitmap.width,160), minOf(bitmap.height,160), true)
            val output=ByteArrayOutputStream(); small.compress(Bitmap.CompressFormat.JPEG,82,output)
            if (small !== bitmap) small.recycle()
            val bytes=output.toByteArray(); if (bytes.size > 64*1024) return@synchronized
            val before=data.toString()
            VisualHistory.observe(data,profile,VisualHistory.digest(bytes) to Base64.encodeToString(bytes,Base64.NO_WRAP))
            if (before != data.toString()) save(data)
        } catch (_: Exception) { writeFailed=true }
    }
}
