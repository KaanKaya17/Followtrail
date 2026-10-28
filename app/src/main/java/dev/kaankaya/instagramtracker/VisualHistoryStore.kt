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
    fun exists() = record.exists()
    private fun load(owner: String): JSONObject = record.read()?.let { JSONObject(it).also(VisualHistory::validate) }
        ?.takeIf { it.getString("owner") == owner } ?: VisualHistory.empty(owner)
    private fun save(data: JSONObject) { VisualHistory.validate(data); record.write(data.toString()); writeFailed = false }
    fun read(owner: String) = synchronized(lock) { load(owner) }
    fun configure(account: SavedAccount, id: String, enabled: Boolean) = synchronized(lock) {
        require(id == account.profile.userId || account.people.any { it.latest.userId == id })
        val data = load(account.session.userId)
        VisualHistory.target(data,id).put("enabled",enabled)
        if (enabled) VisualHistory.observe(data,if (id == account.profile.userId) account.profile else account.people.first { it.latest.userId == id }.latest)
        save(data)
    }
    fun clear(owner: String, id: String) = synchronized(lock) { val data=load(owner); VisualHistory.clear(data,id); save(data) }
    fun clearAll() = synchronized(lock) { record.clear() }
    fun observe(account: SavedAccount) = synchronized(lock) {
        try {
            val data = load(account.session.userId)
            if (data.getJSONObject("profiles").length() == 0) return@synchronized
            val before = data.toString()
            (listOf(account.profile)+account.people.map { it.latest }).forEach { VisualHistory.observe(data,it) }
            if (before != data.toString()) save(data)
        } catch (_: Exception) { writeFailed = true }
    }
    fun avatar(owner: String, profile: Profile, bitmap: Bitmap) = synchronized(lock) {
        try {
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
