package dev.kaankaya.instagramtracker

import android.content.Context
import java.text.SimpleDateFormat
import java.text.ParsePosition
import java.util.Locale
import java.util.TimeZone

object RetryPolicy {
    /** A missing or invalid header is unknown, never a server promise of a local default. */
    fun serverDeadline(header: String?, now: Long): Long? {
        header?.trim()?.let { value ->
            try {
                if (value.matches(Regex("[0-9]+"))) {
                    val result = Math.addExact(now, Math.multiplyExact(value.toLong(), 1000L))
                    if (result >= now) return result
                } else {
                    val position = ParsePosition(0)
                    val date = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("GMT"); isLenient = false
                    }.parse(value, position)?.time
                    if (date != null && position.index == value.length) return date
                }
            } catch (_: Exception) { /* Unknown server deadline. */ }
        }
        return null
    }
}

class RequestGate(context: Context, name: String = "request_gate", private val clock: () -> Long = System::currentTimeMillis) {
    fun now(): Long = clock()
    internal val context: Context = context.applicationContext
    // Activity recreation can create multiple owners of the same preferences.
    // Reserve requests across those instances, not only within one instance.
    companion object {
        private val lock = Any(); private val active = mutableSetOf<String>(); private val quickOwners = mutableMapOf<String, Any>()
        const val AUTO_INTERVAL_MS = MonitorPolicy.POLL_MS
    }
    private val identity = "${context.packageName}:$name"
    private val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    fun blockedUntil(): Long = synchronized(lock) { preferences.getLong("blocked_until", 0) }
    fun nextAllowedAt(): Long = synchronized(lock) { maxOf(blockedUntil(), preferences.getLong("next_read_at", 0)) }
    fun automaticNextAllowedAt(): Long = synchronized(lock) { maxOf(nextAllowedAt(), preferences.getLong("auto_after", 0)) }
    fun quickWatchActive(): Boolean = synchronized(lock) { identity in quickOwners }
    fun claimQuickWatch(): AutoCloseable = synchronized(lock) {
        if (identity in quickOwners) throw ApiFailure(ApiFailure.Kind.TOO_SOON)
        val owner = Any(); quickOwners[identity] = owner
        AutoCloseable { synchronized(lock) { if (quickOwners[identity] === owner) quickOwners.remove(identity); Unit } }
    }
    fun manualOnly(): Boolean = synchronized(lock) { preferences.getBoolean("manual_only", false) || blockedUntil() > 0 || listCapacityPaused() }
    fun listCapacityPaused(): Boolean = synchronized(lock) { preferences.getBoolean("list_capacity_pause", false) }
    fun pauseForListCapacity() = synchronized(lock) {
        if (!preferences.edit().putBoolean("list_capacity_pause", true).commit()) throw ApiFailure(ApiFailure.Kind.STORAGE)
    }
    fun clearListCapacityPause() = synchronized(lock) {
        if (!preferences.edit().remove("list_capacity_pause").commit()) throw ApiFailure(ApiFailure.Kind.STORAGE)
    }
    fun restrictionPending(): Boolean = blockedUntil() > 0
    fun serverRetryAt(): Long? = synchronized(lock) { preferences.getLong("server_retry_at", 0).takeIf { it > 0 } }
    fun ownAutoRefresh(): Boolean = synchronized(lock) { preferences.getBoolean("own_auto_refresh", false) }
    fun setOwnAutoRefresh(enabled: Boolean) = synchronized(lock) {
        if (!preferences.edit().putBoolean("own_auto_refresh", enabled).commit()) throw ApiFailure(ApiFailure.Kind.STORAGE)
    }
    fun attempts(): Long = synchronized(lock) { preferences.getLong("attempt_count", 0) }
    fun lastAttemptAt(): Long = synchronized(lock) { preferences.getLong("last_attempt_at", 0) }
    fun lastHttpStatus(): Int? = synchronized(lock) { preferences.getInt("last_http_status", 0).takeIf { it in 100..599 } }
    fun observedStatus(status: Int) = synchronized(lock) {
        require(status in 100..599)
        if (!preferences.edit().putInt("last_http_status", status).commit()) throw ApiFailure(ApiFailure.Kind.STORAGE)
    }
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
    fun beginPrototype(now: Long = clock()) = synchronized(lock) {
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
    fun autoAllowed(profile: Profile, now: Long = clock()): Boolean = synchronized(lock) {
        ownAutoRefresh() && !automationHeld() && !manualOnly() && !requiresConnection() && PeoplePolicy.needsRefresh(profile, now) && automaticNextAllowedAt() <= now
    }
    fun reserveAutomatic(now: Long = clock()): Boolean = synchronized(lock) {
        if (quickWatchActive() || automationHeld() || manualOnly() || requiresConnection() || automaticNextAllowedAt() > now) return false
        if (!preferences.edit().putLong("auto_after", now + AUTO_INTERVAL_MS).commit()) throw ApiFailure(ApiFailure.Kind.STORAGE)
        true
    }
    fun begin(now: Long = clock()) = synchronized(lock) {
        if (blockedUntil() > now) throw ApiFailure(ApiFailure.Kind.LIMITED)
        if (nextAllowedAt() > now) throw ApiFailure(ApiFailure.Kind.TOO_SOON)
        val count = attempts()
        if (!preferences.edit().putLong("next_read_at", now + 15000)
                .putLong("attempt_count", if (count == Long.MAX_VALUE) count else count + 1)
                .putLong("last_attempt_at", now).remove("last_http_status").commit()) {
            throw ApiFailure(ApiFailure.Kind.STORAGE)
        }
    }
    fun limited(header: String?, now: Long = clock(), reason: String = "Instagram bekleme yanıtı") = synchronized(lock) {
        val repeats = (preferences.getInt("limit_streak", 0) + 1).coerceAtMost(6)
        val localPause = minOf(24 * 60 * 60 * 1000L, 60 * 60 * 1000L * (1L shl (repeats - 1)))
        val server = RetryPolicy.serverDeadline(header, now)
        if (!preferences.edit().putLong("blocked_until", maxOf(blockedUntil(), server ?: 0, now + localPause))
                .apply { if (server != null) putLong("server_retry_at", server) else remove("server_retry_at") }
                .putLong("limited_at", now).putString("limit_reason", reason).putInt("limit_streak", repeats)
                .putBoolean("manual_only", true).commit()) {
            throw ApiFailure(ApiFailure.Kind.STORAGE)
        }
    }
    fun succeeded() = synchronized(lock) {
        // A successful manual read resumes normal cached own-account refreshes.
        if (!preferences.edit().putBoolean("manual_only", false).putLong("blocked_until", 0).remove("server_retry_at")
                .putBoolean("requires_connection", false)
                .putInt("limit_streak", 0).commit()) throw ApiFailure(ApiFailure.Kind.STORAGE)
    }
}
