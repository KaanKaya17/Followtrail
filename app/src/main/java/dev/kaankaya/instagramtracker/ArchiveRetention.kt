package dev.kaankaya.instagramtracker

import android.content.Context
import org.json.JSONObject

data class ArchiveRetention(val historyDays: Int = 0, val dailyDays: Int = 0,
    val attemptedAt: Long = 0, val failed: Boolean = false) {
    init { require(historyDays in CHOICES && dailyDays in CHOICES && attemptedAt >= 0) }
    companion object { val CHOICES = listOf(0, 30, 90, 365); const val DAY = 86_400_000L }
    fun historyBefore(now: Long) = if (historyDays == 0) 0 else maxOf(0, now - historyDays * DAY)
    fun dailyBefore(now: Long) = if (dailyDays == 0) 0 else maxOf(0, (now / DAY - dailyDays + 1) * DAY)
    fun due(now: Long) = (historyDays != 0 || dailyDays != 0) && (attemptedAt == 0L || now - attemptedAt >= DAY)
    fun retain(account: SavedAccount, now: Long): SavedAccount {
        val before = historyBefore(now)
        if (before == 0L) return account
        return account.copy(people = account.people.map { person ->
            person.copy(history = person.history.filterIndexed { index, profile ->
                index == person.history.lastIndex || profile.observedAt >= before
            })
        })
    }
}

/** Account-owned policy, separate from the session format. Missing preferences preserve existing archives. */
class PrivacyStore(context: Context, slot: String) {
    companion object {
        private val lock=Any()
        private data class Cached(val stamp: String,val owner: String,val value: ArchiveRetention)
        private val cache=object : LinkedHashMap<String,Cached>(4,.75f,true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String,Cached>?)=size>2
        }
    }
    private val identity="${context.packageName}:$slot"
    private val record = EncryptedRecord(context, slot + "privacy")
    fun read(owner: String): ArchiveRetention = synchronized(lock) {
        val stamp=record.stamp()
        cache[identity]?.takeIf {it.stamp==stamp && it.owner==owner}?.let {return@synchronized it.value}
        val body = record.read()?.let(::JSONObject) ?: return@synchronized ArchiveRetention()
        require(body.getInt("version") == 1 && body.getString("owner") == owner)
        fun number(key: String): Long = (body.get(key).also { require(it is Int || it is Long) } as Number).toLong()
        val history = number("historyDays"); val daily = number("dailyDays")
        require(history in ArchiveRetention.CHOICES.map(Int::toLong) && daily in ArchiveRetention.CHOICES.map(Int::toLong))
        require(body.get("failed") is Boolean)
        ArchiveRetention(history.toInt(), daily.toInt(), number("attemptedAt"), body.getBoolean("failed"))
            .also {cache[identity]=Cached(record.stamp(),owner,it)}
    }
    fun write(owner: String, value: ArchiveRetention) = synchronized(lock) {
        InstagramIdentity.id(owner)
        record.write(JSONObject().put("version",1).put("owner",owner).put("historyDays",value.historyDays)
            .put("dailyDays",value.dailyDays).put("attemptedAt",value.attemptedAt).put("failed",value.failed).toString())
        cache[identity]=Cached(record.stamp(),owner,value)
        Unit
    }
    fun clear() = synchronized(lock) {record.clear();cache.remove(identity);Unit}
}
