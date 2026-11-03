package dev.kaankaya.instagramtracker

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

data class DailyPoint(val bucket: Long, val day: Long, val at: Long, val first: Long, val value: Long,
    val minimum: Long, val maximum: Long, val observedDays: Int, val samples: Long)
data class DailySeries(val points: List<DailyPoint>, val bucketDays: Long, val observedDays: Int, val samples: Long)

/** Shared semantics with desktop daily_stats.py; metrics are observed facts, never reconstructed days. */
object DailyStats {
    const val DAY = 86400000L
    const val MAX_NUMBER = 9007199254740991L
    val metrics = listOf("followers", "following", "posts")
    fun year(at: Long): Int {
        require(at in 0..253402300799999L)
        return GregorianCalendar(TimeZone.getTimeZone("UTC")).apply { timeInMillis = at }.let {
            require(it.get(Calendar.ERA) == GregorianCalendar.AD)
            it.get(Calendar.YEAR).also { y -> require(y in 1970..9999) }
        }
    }
    fun empty(owner: String, target: String, year: Int) = JSONObject().put("version",1).put("owner",owner)
        .put("target",target).put("year",year).put("days",JSONObject())
    private fun number(value: Any): Long {
        require(value is Int || value is Long)
        return (value as Number).toLong().also { require(it in 0..MAX_NUMBER) }
    }
    fun observe(document: JSONObject, profile: Profile, after: Long = 0): Boolean {
        require(document.getString("target") == profile.userId && document.getInt("year") == year(profile.observedAt))
        val at = profile.observedAt
        if (at <= after) return false
        val day = at / DAY * DAY
        val days = document.getJSONObject("days")
        val old = days.optJSONObject(day.toString())
        if (old != null && at <= old.getLong("lastAt")) return false
        val row = if (old != null && old.getLong("firstAt") > after) JSONObject(old.toString()) else
            JSONObject().put("day",day).put("firstAt",at).put("samples",0)
        row.put("lastAt",at).put("samples",row.getLong("samples")+1)
        for ((metric,value) in listOf("followers" to profile.followers, "following" to profile.following, "posts" to profile.posts)) {
            if (value == null) continue
            require(value in 0..MAX_NUMBER)
            val previous = row.optJSONArray(metric)
            row.put(metric,JSONArray(if (previous == null) listOf(value,value,value,value,at,at) else
                listOf(previous.getLong(0),value,minOf(previous.getLong(2),value),maxOf(previous.getLong(3),value),previous.getLong(4),at)))
        }
        days.put(day.toString(),row)
        return true
    }
    fun validate(document: JSONObject, owner: String, target: String) {
        require(number(document.get("version")) == 1L && document.getString("owner") == InstagramIdentity.id(owner) &&
            document.getString("target") == InstagramIdentity.id(target))
        val expectedYear = number(document.get("year")).toInt(); require(expectedYear in 1970..9999)
        val days = document.getJSONObject("days"); require(days.length() <= 366)
        for (key in days.keys()) {
            val row = days.getJSONObject(key)
            require(row.keys().asSequence().all { it in setOf("day","firstAt","lastAt","samples") + metrics })
            val day = number(row.get("day")); require(key == day.toString() && day % DAY == 0L && year(day) == expectedYear)
            val first = number(row.get("firstAt")); val last = number(row.get("lastAt"))
            require(first in day until day+DAY && last in first until day+DAY && number(row.get("samples")) > 0)
            require(metrics.any(row::has))
            for (metric in metrics.filter(row::has)) {
                val values = row.getJSONArray(metric); require(values.length() == 6)
                val n = (0..5).map { number(values.get(it)) }
                require(n[2] <= minOf(n[0],n[1]) && maxOf(n[0],n[1]) <= n[3] && n[4] in first..n[5] && n[5] <= last)
            }
        }
    }
    fun series(documents: List<JSONObject>, metric: String, span: Int, now: Long, after: Long = 0): DailySeries {
        require(metric in metrics && span in listOf(0,7,30,365))
        val cutoff = if (span == 0) 0 else (now / DAY - span + 1) * DAY
        val rows = documents.flatMap { doc -> doc.getJSONObject("days").let { days -> days.keys().asSequence().map(days::getJSONObject).toList() } }
            .filter { it.has(metric) && it.getLong("day") >= cutoff && it.getLong("firstAt") > after }.sortedBy { it.getLong("day") }
        val step = if (rows.isEmpty()) 1 else maxOf(1,((rows.last().getLong("day")-rows.first().getLong("day"))/DAY+720)/720)
        val groups = linkedMapOf<Long,DailyPoint>()
        val base = rows.firstOrNull()?.getLong("day")?.div(DAY) ?: 0L
        for (row in rows) {
            val day = row.getLong("day"); val key = (day / DAY - base) / step; val field = row.getJSONArray(metric)
            val old = groups[key]
            groups[key] = DailyPoint(key,old?.day ?: day,field.getLong(5),old?.first ?: field.getLong(0),field.getLong(1),
                minOf(old?.minimum ?: field.getLong(2),field.getLong(2)),maxOf(old?.maximum ?: field.getLong(3),field.getLong(3)),
                (old?.observedDays ?: 0)+1,(old?.samples ?: 0)+row.getLong("samples"))
        }
        return DailySeries(groups.values.toList(),step,rows.size,rows.sumOf { it.getLong("samples") })
    }
}
