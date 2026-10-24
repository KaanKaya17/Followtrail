package dev.kaankaya.instagramtracker

import android.content.Context
import java.text.SimpleDateFormat
import java.text.ParsePosition
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
                    val position = ParsePosition(0)
                    val date = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("GMT"); isLenient = false
                    }.parse(value, position)?.time
                    if (date != null && position.index == value.length && date > now) return date
                }
            } catch (_: Exception) { /* An invalid header uses the local pause. */ }
        }
        return now + FALLBACK_PAUSE_MS
    }
}

class RequestGate(context: Context, name: String = "request_gate") {
    internal val context: Context = context.applicationContext
    // Activity recreation can create multiple owners of the same preferences.
    // Reserve requests across those instances, not only within one instance.
    companion object { private val lock = Any(); private val active = mutableSetOf<String>(); const val AUTO_INTERVAL_MS = 15 * 60 * 1000L }
    private val identity = "${context.packageName}:$name"
    private val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    fun blockedUntil(): Long = synchronized(lock) { preferences.getLong("blocked_until", 0) }
    fun nextAllowedAt(): Long = synchronized(lock) { maxOf(blockedUntil(), preferences.getLong("next_read_at", 0)) }
    fun manualOnly(): Boolean = synchronized(lock) { preferences.getBoolean("manual_only", false) || blockedUntil() > 0 }
    fun lastLimitedAt(): Long = synchronized(lock) { preferences.getLong("limited_at", 0) }
    fun limitReason(): String = synchronized(lock) { preferences.getString("limit_reason", null) ?: "Önceki sürümde kaydedilen sınırlama" }
    fun requiresConnection(): Boolean = synchronized(lock) { preferences.getBoolean("requires_connection", false) }
    fun prototypeActive(): Boolean = synchronized(lock) { preferences.getBoolean("prototype_active", false) }
    fun mobileLinkActive(): Boolean = synchronized(lock) { preferences.getBoolean("mobile_link_active", false) }
    fun automationHeld(): Boolean = prototypeActive() || mobileLinkActive()
    fun setMobileLinkActive(active: Boolean) = synchronized(lock) {
        if (!preferences.edit().putBoolean("mobile_link_active", active).commit()) throw ApiFailure(ApiFailure.Kind.STORAGE)
    }
    fun pauseAutomatic() = synchronized(lock) {
        if (!preferences.edit().putBoolean("manual_only", true).commit()) throw ApiFailure(ApiFailure.Kind.STORAGE)
    }
    fun setPrototypeActive(active: Boolean) = synchronized(lock) {
        if (!preferences.edit().putBoolean("prototype_active", active).commit()) throw ApiFailure(ApiFailure.Kind.STORAGE)
    }
    fun nextPrototypeAt(): Long = synchronized(lock) { maxOf(nextAllowedAt(), preferences.getLong("prototype_after", 0)) }
    fun beginPrototype(now: Long = System.currentTimeMillis()) = synchronized(lock) {
        if (blockedUntil() > now) throw ApiFailure(ApiFailure.Kind.LIMITED)
        if (nextPrototypeAt() > now) throw ApiFailure(ApiFailure.Kind.TOO_SOON)
        begin(now)
        if (!preferences.edit().putLong("prototype_after", now + AUTO_INTERVAL_MS).commit()) throw ApiFailure(ApiFailure.Kind.STORAGE)
    }
    fun pauseForConnection() = synchronized(lock) {
        if (!preferences.edit().putBoolean("requires_connection", true).commit()) throw ApiFailure(ApiFailure.Kind.STORAGE)
    }
    fun claim(): AutoCloseable = synchronized(lock) {
        if (!active.add(identity)) throw ApiFailure(ApiFailure.Kind.TOO_SOON)
        var closed = false
        AutoCloseable { synchronized(lock) { if (!closed) { active.remove(identity); closed = true }; Unit } }
    }
    fun autoAllowed(profile: Profile, now: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        !automationHeld() && !manualOnly() && !requiresConnection() && PeoplePolicy.needsRefresh(profile, now) && nextAllowedAt() <= now &&
            preferences.getLong("auto_after", 0) <= now
    }
    fun reserveAutomatic(now: Long = System.currentTimeMillis()): Boolean = synchronized(lock) {
        if (automationHeld() || manualOnly() || requiresConnection() || nextAllowedAt() > now || preferences.getLong("auto_after", 0) > now) return false
        if (!preferences.edit().putLong("auto_after", now + AUTO_INTERVAL_MS).commit()) throw ApiFailure(ApiFailure.Kind.STORAGE)
        true
    }
    fun begin(now: Long = System.currentTimeMillis()) = synchronized(lock) {
        if (blockedUntil() > now) throw ApiFailure(ApiFailure.Kind.LIMITED)
        if (nextAllowedAt() > now) throw ApiFailure(ApiFailure.Kind.TOO_SOON)
        if (!preferences.edit().putLong("next_read_at", now + 15000).commit()) {
            throw ApiFailure(ApiFailure.Kind.STORAGE)
        }
    }
    fun limited(header: String?, now: Long = System.currentTimeMillis(), reason: String = "Instagram bekleme yanıtı") = synchronized(lock) {
        val repeats = (preferences.getInt("limit_streak", 0) + 1).coerceAtMost(6)
        val localPause = minOf(24 * 60 * 60 * 1000L, 60 * 60 * 1000L * (1L shl (repeats - 1)))
        if (!preferences.edit().putLong("blocked_until", maxOf(blockedUntil(), RetryPolicy.until(header, now), now + localPause))
                .putLong("limited_at", now).putString("limit_reason", reason).putInt("limit_streak", repeats)
                .putBoolean("manual_only", true).commit()) {
            throw ApiFailure(ApiFailure.Kind.STORAGE)
        }
    }
    fun succeeded() = synchronized(lock) {
        // A successful manual read resumes normal cached own-account refreshes.
        if (!preferences.edit().putBoolean("manual_only", false).putLong("blocked_until", 0)
                .putBoolean("requires_connection", false)
                .putInt("limit_streak", 0).commit()) throw ApiFailure(ApiFailure.Kind.STORAGE)
    }
}
