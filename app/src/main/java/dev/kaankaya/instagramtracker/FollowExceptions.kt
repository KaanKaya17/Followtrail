package dev.kaankaya.instagramtracker

import android.content.Context
import org.json.JSONArray

/** Account-scoped presentation preferences for the current non-followers list. */
internal class FollowExceptions(context: Context, namespace: String) {
    private val record = EncryptedRecord(context, namespace + "followexceptions", 2 * 1024 * 1024)

    fun read(): Set<String> = synchronized(AccountCatalog.lock) {
        val array = record.read()?.let(::JSONArray) ?: return@synchronized emptySet()
        require(array.length() <= 40_000)
        (0 until array.length()).map { InstagramIdentity.id(array.get(it)) }.toSet()
    }

    fun set(id: String, enabled: Boolean) = synchronized(AccountCatalog.lock) {
        val identity = InstagramIdentity.id(id)
        val values = read()
        val updated = if (enabled) values + identity else values - identity
        require(updated.size <= 40_000)
        if (updated != values) record.write(JSONArray(updated.sorted()).toString())
    }

    fun clear() = synchronized(AccountCatalog.lock) { record.clear() }
}
