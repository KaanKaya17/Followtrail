package dev.kaankaya.instagramtracker

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest

/** One bounded Keystore file per profile/year, independent of the rolling account record. */
class DailyStatsStore(private val context: Context, private val slot: String) {
    companion object { private val lock=Any(); private val failures=mutableSetOf<String>() }
    private val failureKey = context.packageName + ":" + slot
    private val privacy = PrivacyStore(context,slot)
    val failed get() = synchronized(lock) { failureKey in failures }
    private fun prefix(target: String): String {
        InstagramIdentity.id(target)
        val digest = MessageDigest.getInstance("SHA-256").digest(target.toByteArray(Charsets.UTF_8))
        return slot + "daily" + digest.joinToString("") { byte -> "${('a'.code+(byte.toInt() ushr 4 and 15)).toChar()}${('a'.code+(byte.toInt() and 15)).toChar()}" }
    }
    private fun suffix(year: Int): String {
        require(year in 1970..9999)
        return year.toString(16).padStart(4,'0').map { ('a'.code+it.digitToInt(16)).toChar() }.joinToString("")
    }
    private fun after(owner: String, target: String): Long {
        val value=EncryptedRecord(context,prefix(target)+"reset").read()?.let(::JSONObject) ?: return 0
        require(value.getInt("version")==1 && value.getString("owner")==owner && value.getString("target")==target)
        return (value.get("after").also { require(it is Long || it is Int) } as Number).toLong().also { require(it>=0) }
    }
    private fun load(owner: String, target: String, year: Int): JSONObject =
        EncryptedRecord(context,prefix(target)+suffix(year)).read()?.let(::JSONObject)?.also {
            DailyStats.validate(it,owner,target);require(it.getInt("year")==year)
        } ?: DailyStats.empty(owner,target,year)
    fun record(owner: String, target: String, profiles: List<Profile>) = synchronized(lock) {
        InstagramIdentity.id(owner)
        val after=maxOf(after(owner,target),privacy.read(owner).dailyBefore(System.currentTimeMillis())-1)
        for ((year,items) in profiles.filter { it.userId==target && it.observedAt>after }.sortedBy { it.observedAt }.groupBy { DailyStats.year(it.observedAt) }) {
            val document=load(owner,target,year)
            var changed=false
            for (profile in items) changed=DailyStats.observe(document,profile,after) || changed
            if (changed) {
                DailyStats.validate(document,owner,target)
                EncryptedRecord(context,prefix(target)+suffix(year)).write(document.toString())
            }
        }
        failures.remove(failureKey)
    }
    fun observe(account: SavedAccount, previous: SavedAccount?) = synchronized(lock) {
        try {
            val before = (listOfNotNull(previous?.profile)+previous?.people.orEmpty().map { it.latest }).associateBy { it.userId }
            val candidates = listOf(account.profile)+account.people.map { it.latest }
            for (profile in candidates) if (before[profile.userId]?.observedAt != profile.observedAt) {
                val older = previous?.people?.firstOrNull { it.latest.userId==profile.userId }?.history.orEmpty() + listOfNotNull(before[profile.userId])
                val recent = account.people.firstOrNull { it.latest.userId==profile.userId }?.history ?: listOf(profile)
                record(account.session.userId,profile.userId,(older+recent).distinctBy { it.observedAt })
            }
        } catch (_: Exception) { failures.add(failureKey) }
    }
    private fun years(target: String): List<Int> {
        val pattern=Regex("${prefix(target)}([a-p]{4})\\.enc(?:\\.bak|\\.new)?")
        return context.noBackupFilesDir.listFiles().orEmpty().mapNotNull { file ->
            pattern.matchEntire(file.name)?.groupValues?.get(1)?.map { (it-'a').toString(16) }?.joinToString("")?.toInt(16)
        }.distinct().sorted().also { list -> require(list.all { it in 1970..9999 }) }
    }
    fun query(account: SavedAccount, target: String, metric: String, span: Int, now: Long): DailySeries = synchronized(AccountCatalog.lock) {
        // Bind the read/migration to a still-owned account for the entire I/O operation.
        val current=EncryptedRecord(context,slot,SessionVault.ACCOUNT_BYTES).read()?.let(AccountCodec::decode)
            ?.takeIf {it.session==account.session} ?: throw ApiFailure(ApiFailure.Kind.CANCELLED)
        synchronized(lock) {
            val profile=if(target==current.profile.userId)current.profile else current.people.first {it.latest.userId==target}.latest
            record(current.session.userId,target,current.people.firstOrNull {it.latest.userId==target}?.history ?: listOf(profile))
            val minYear=if(span==0)1970 else DailyStats.year(maxOf(0,(now/DailyStats.DAY-span+1)*DailyStats.DAY))
            DailyStats.series(years(target).filter {it>=minYear}.map {load(current.session.userId,target,it)},metric,span,now,after(current.session.userId,target))
        }
    }
    fun clear(owner: String, target: String, now: Long) = synchronized(lock) {
        require(now>0)
        var cutoff=now
        for(year in years(target)) {
            try {
                val days=load(owner,target,year).getJSONObject("days")
                for(key in days.keys())cutoff=maxOf(cutoff,days.getJSONObject(key).getLong("lastAt"))
            } catch (_: Exception) { /* Explicit erasure owns malformed records too. */ }
        }
        EncryptedRecord(context,prefix(target)+"reset").write(JSONObject().put("version",1).put("owner",owner).put("target",target).put("after",cutoff).toString())
        years(target).forEach { EncryptedRecord(context,prefix(target)+suffix(it)).clear() }
        failures.remove(failureKey)
    }
    fun clearAll() = synchronized(lock) {
        val pattern=Regex("(${slot}daily[a-p]{64}(?:[a-p]{4}|reset))\\.enc(?:\\.bak|\\.new)?")
        context.noBackupFilesDir.listFiles().orEmpty().mapNotNull { pattern.matchEntire(it.name)?.groupValues?.get(1) }.distinct()
            .forEach { EncryptedRecord(context,it).clear() }
        failures.remove(failureKey)
    }
    fun prune(owner: String, before: Long) = synchronized(lock) {
        if (before == 0L) return@synchronized
        val pattern=Regex("(${slot}daily[a-p]{64}[a-p]{4})\\.enc(?:\\.bak|\\.new)?")
        val names=context.noBackupFilesDir.listFiles().orEmpty().mapNotNull {pattern.matchEntire(it.name)?.groupValues?.get(1)}.distinct()
        for(name in names) {
            val record=EncryptedRecord(context,name)
            val body=record.read()?.let(::JSONObject) ?: continue
            val target=body.getString("target"); DailyStats.validate(body,owner,target)
            val days=body.getJSONObject("days")
            days.keys().asSequence().toList().filter {it.toLong()<before}.forEach(days::remove)
            EncryptedRecord(context,prefix(target)+"reset").write(JSONObject().put("version",1).put("owner",owner)
                .put("target",target).put("after",maxOf(after(owner,target),before-1)).toString())
            if(days.length()==0)record.clear() else record.write(body.toString())
        }
        failures.remove(failureKey)
    }
}
