package dev.kaankaya.instagramtracker

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object RetryPolicy {
    // A local pause, not a claim about when Instagram will lift its restriction.
    const val FALLBACK_PAUSE_MS = 15 * 60 * 1000L
    fun until(header: String?, now: Long): Long {
        header?.trim()?.let { value ->
            try {
                if (value.matches(Regex("[0-9]+"))) {
                    val result = Math.addExact(now, Math.multiplyExact(value.toLong(), 1000L))
                    if (result > now) return result
                } else {
                    val date = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("GMT"); isLenient = false
                    }.parse(value)?.time
                    if (date != null && date > now) return date
                }
            } catch (_: Exception) { /* An invalid header uses the local pause. */ }
        }
        return now + FALLBACK_PAUSE_MS
    }
}

class RequestGate(context: Context, name: String = "request_gate") {
    private val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    @Synchronized fun blockedUntil(): Long = preferences.getLong("blocked_until", 0)
    @Synchronized fun nextAllowedAt(): Long = maxOf(blockedUntil(), preferences.getLong("next_read_at", 0))
    @Synchronized fun begin(now: Long = System.currentTimeMillis()) {
        if (blockedUntil() > now) throw ApiFailure(ApiFailure.Kind.LIMITED)
        if (nextAllowedAt() > now) throw ApiFailure(ApiFailure.Kind.TOO_SOON)
        if (!preferences.edit().putLong("next_read_at", now + 15000).commit()) {
            throw ApiFailure(ApiFailure.Kind.STORAGE)
        }
    }
    @Synchronized fun limited(header: String?, now: Long = System.currentTimeMillis()) {
        if (!preferences.edit().putLong("blocked_until", maxOf(blockedUntil(), RetryPolicy.until(header, now))).commit()) {
            throw ApiFailure(ApiFailure.Kind.STORAGE)
        }
    }
}
