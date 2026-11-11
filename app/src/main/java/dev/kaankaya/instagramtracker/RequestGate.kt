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

class RequestGate internal constructor(context: Context, private val name: String, private val clock: () -> Long,
    private val delayMillis: () -> Long) {
    // Preserve the original third/trailing lambda as the caller's clock.
    constructor(context: Context, name: String = "request_gate", clock: () -> Long = System::currentTimeMillis) :
        this(context, name, clock, { kotlin.random.Random.nextLong(RequestDefaults.MIN_SPACING_MS, RequestDefaults.MAX_SPACING_MS + 1) })
    private var owner: String?=null
    fun now(): Long = clock()
    internal val context: Context = context.applicationContext
    // Activity recreation can create multiple owners of the same preferences.
    // Reserve requests across those instances, not only within one instance.
    companion object {
        private val lock = Any(); private val active = mutableSetOf<String>(); private val quickOwners = mutableMapOf<String, Any>(); private val preparationOwners = mutableMapOf<String, Any>()
        const val AUTH_INTERVAL_MS = 15 * 60 * 1000L
    }
    private val identity = "${context.packageName}:$name"
    private val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private fun settings() = if(name!="request_gate")preferences else this.context.getSharedPreferences(
        "account_flags_"+((owner ?: AccountCatalog.selected(this.context))?.let(AccountCatalog::slot) ?: "empty"),Context.MODE_PRIVATE)
    fun forAccount(id: String)=RequestGate(context,name,clock,delayMillis).also {it.owner=InstagramIdentity.id(id)}
    fun readDiagnosis(kind: ReadKind): ReadDiagnosis? = synchronized(lock) {
        val parts = settings().getString("read_health_${kind.name}", null)?.split('|') ?: return@synchronized null
        if (parts.size != 4) return@synchronized null
        val reason = ReadReason.entries.firstOrNull { it.name == parts[0] } ?: return@synchronized null
        val count = parts[1].toIntOrNull()?.takeIf { it in 0..3 } ?: return@synchronized null
        val at = parts[3].toLongOrNull()?.takeIf { it >= 0 } ?: return@synchronized null
        ReadDiagnosis(reason, count, parts[2].toIntOrNull()?.takeIf { it in 100..599 }, at)
    }
    fun pausedReads() = ReadKind.entries.filter { readDiagnosis(it)?.paused == true }.toSet()
    fun recordRead(kind: ReadKind, reason: ReadReason, http: Int? = null) = synchronized(lock) {
        val current = readDiagnosis(kind)
        val next = ReadDiagnosis.next(current, reason, http, now())
        if (next != current) settings().edit().putString("read_health_${kind.name}",
            "${next.reason.name}|${next.count}|${next.http ?: ""}|${next.at}").apply()
    }
    fun readSucceeded(kind: ReadKind) = synchronized(lock) {
        if (readDiagnosis(kind) != null) settings().edit().remove("read_health_${kind.name}").apply()
    }
    private fun budgetOwner() = owner ?: if (name == "request_gate") AccountCatalog.selected(context) else null
    fun blockedUntil(): Long = synchronized(lock) { preferences.getLong("blocked_until", 0) }
    private fun budgetTimes(): List<Long> = try {
        RequestBudgetCodec.times(preferences.getString("budget_times", "[]")!!)
    } catch (_: Exception) { throw ApiFailure(ApiFailure.Kind.STORAGE) }
    private fun accountBudgets() = try { RequestBudgetCodec.accounts(preferences.getString("budget_accounts", "{}")!!) }
        catch (_: Exception) { throw ApiFailure(ApiFailure.Kind.STORAGE) }
    fun budgetNextAt(now: Long = clock(), account: String? = budgetOwner()): Long = synchronized(lock) {
        try { RequestBudgetPolicy.nextAt(budgetTimes(), now, account?.let { accountBudgets()[RequestBudgetPolicy.ownerKey(it)] }.orEmpty()) }
        catch (_: Exception) { throw ApiFailure(ApiFailure.Kind.STORAGE) }
    }
    fun nextAllowedAt(account: String? = budgetOwner()): Long = synchronized(lock) { maxOf(blockedUntil(), preferences.getLong("next_read_at", 0), budgetNextAt(account = account)) }
    fun profileBudgetNextAt(now: Long = clock(), account: String? = budgetOwner()): Long = synchronized(lock) {
        try { RequestBudgetPolicy.profileNextAt(budgetTimes(), now, account?.let { accountBudgets()[RequestBudgetPolicy.ownerKey(it)] }.orEmpty()) }
        catch (_: Exception) { throw ApiFailure(ApiFailure.Kind.STORAGE) }
    }
    fun monitorIntervalMinutes(): Int = synchronized(lock) {
        settings().getInt("monitor_interval_minutes", RequestDefaults.DEFAULT_INTERVAL_MINUTES).also {
            if (it !in RequestDefaults.INTERVAL_MINUTES) throw ApiFailure(ApiFailure.Kind.STORAGE)
        }
    }
    fun monitorIntervalMillis(): Long = monitorIntervalMinutes() * 60_000L
    fun setMonitorIntervalMinutes(minutes: Int) = synchronized(lock) {
        require(minutes in RequestDefaults.INTERVAL_MINUTES)
        if (!settings().edit().putInt("monitor_interval_minutes", minutes).commit()) throw ApiFailure(ApiFailure.Kind.STORAGE)
    }
    fun automaticNextAllowedAt(): Long = synchronized(lock) {
        maxOf(nextAllowedAt(), settings().getLong("auto_after", 0), preferences.getLong("auto_after", 0))
    }
    fun quickWatchActive(): Boolean = synchronized(lock) { identity in quickOwners }
    fun preparationActive(): Boolean = synchronized(lock) { identity in preparationOwners }
    fun claimPreparation(): AutoCloseable = synchronized(lock) {
        if (identity in preparationOwners || identity in quickOwners) throw ApiFailure(ApiFailure.Kind.TOO_SOON)
        val owner = Any(); preparationOwners[identity] = owner
        AutoCloseable { synchronized(lock) { if (preparationOwners[identity] === owner) preparationOwners.remove(identity); Unit } }
    }
    fun claimQuickWatch(): AutoCloseable = synchronized(lock) {
        if (identity in quickOwners || identity in preparationOwners) throw ApiFailure(ApiFailure.Kind.TOO_SOON)
        val owner = Any(); quickOwners[identity] = owner
        AutoCloseable { synchronized(lock) { if (quickOwners[identity] === owner) quickOwners.remove(identity); Unit } }
    }
    fun manualOnly(): Boolean = synchronized(lock) { preferences.getBoolean("manual_only", false) || blockedUntil() > 0 || listCapacityPaused() }
    fun listCapacityPaused(): Boolean = synchronized(lock) { settings().getBoolean("list_capacity_pause", false) }
    fun pauseForListCapacity() = synchronized(lock) {
        if (!settings().edit().putBoolean("list_capacity_pause", true).commit()) throw ApiFailure(ApiFailure.Kind.STORAGE)
    }
    fun clearListCapacityPause() = synchronized(lock) {
        if (!settings().edit().remove("list_capacity_pause").commit()) throw ApiFailure(ApiFailure.Kind.STORAGE)
    }
    fun restrictionPending(): Boolean = blockedUntil() > 0
    fun serverRetryAt(): Long? = synchronized(lock) { preferences.getLong("server_retry_at", 0).takeIf { it > 0 } }
    fun ownAutoRefresh(): Boolean = synchronized(lock) { settings().getBoolean("own_auto_refresh", false) }
    fun backgroundOwnEnabled(): Boolean = synchronized(lock) { settings().getBoolean("own_background", false) }
    fun setBackgroundOwnEnabled(enabled: Boolean) = synchronized(lock) {
        if (!settings().edit().putBoolean("own_background", enabled).commit()) throw ApiFailure(ApiFailure.Kind.STORAGE)
    }
    fun setOwnAutoRefresh(enabled: Boolean) = synchronized(lock) {
        if (!settings().edit().putBoolean("own_auto_refresh", enabled).commit()) throw ApiFailure(ApiFailure.Kind.STORAGE)
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
    fun requiresConnection(): Boolean = synchronized(lock) { settings().getBoolean("requires_connection", false) }
    fun prototypeActive(): Boolean = synchronized(lock) { preferences.getBoolean("prototype_active", false) }
    fun mobileLinkActive(): Boolean = synchronized(lock) { preferences.getBoolean("mobile_link_active", false) }
    fun phoneLoginActive(): Boolean = synchronized(lock) { preferences.getBoolean("phone_login_active", false) }
    fun setPhoneLoginActive(value: Boolean) = synchronized(lock) {
        if (!preferences.edit().putBoolean("phone_login_active", value).commit()) throw ApiFailure(ApiFailure.Kind.STORAGE)
    }
    fun automationHeld(): Boolean = prototypeActive() || mobileLinkActive() || phoneLoginActive()
    fun setMobileLinkActive(active: Boolean) = synchronized(lock) {
        if (!preferences.edit().putBoolean("mobile_link_active", active).commit()) throw ApiFailure(ApiFailure.Kind.STORAGE)
    }
    fun pauseAutomatic() = synchronized(lock) {
        if (!preferences.edit().putBoolean("manual_only", true).commit()) throw ApiFailure(ApiFailure.Kind.STORAGE)
    }
    fun setPrototypeActive(active: Boolean) = synchronized(lock) {
        if (!preferences.edit().putBoolean("prototype_active", active).commit()) throw ApiFailure(ApiFailure.Kind.STORAGE)
    }
    fun nextPrototypeAt(account: String? = budgetOwner()): Long = synchronized(lock) { maxOf(nextAllowedAt(account), preferences.getLong("prototype_after", 0)) }
    fun nextWebAuthenticationAt(): Long = synchronized(lock) { maxOf(nextPrototypeAt(null), preferences.getLong("web_auth_after", 0)) }
    fun beginWebAuthentication(now: Long = clock()) = synchronized(lock) {
        if (blockedUntil() > now) throw ApiFailure(ApiFailure.Kind.LIMITED)
        if (budgetNextAt(now, null) > now) throw ApiFailure(ApiFailure.Kind.BUDGET)
        if (nextWebAuthenticationAt() > now) throw ApiFailure(ApiFailure.Kind.TOO_SOON)
        begin(now, null)
        if (!preferences.edit().putLong("web_auth_after", now + AUTH_INTERVAL_MS).commit()) throw ApiFailure(ApiFailure.Kind.STORAGE)
    }
    fun beginPrototype(now: Long = clock(), account: String? = budgetOwner()) = synchronized(lock) {
        if (blockedUntil() > now) throw ApiFailure(ApiFailure.Kind.LIMITED)
        if (budgetNextAt(now, account) > now) throw ApiFailure(ApiFailure.Kind.BUDGET)
        if (nextPrototypeAt(account) > now) throw ApiFailure(ApiFailure.Kind.TOO_SOON)
        begin(now, account)
        if (!preferences.edit().putLong("prototype_after", now + AUTH_INTERVAL_MS).commit()) throw ApiFailure(ApiFailure.Kind.STORAGE)
    }
    fun pauseForConnection() = synchronized(lock) {
        if (!settings().edit().putBoolean("requires_connection", true).commit()) throw ApiFailure(ApiFailure.Kind.STORAGE)
    }
    fun claim(): AutoCloseable = synchronized(lock) {
        if (!active.add(identity)) throw ApiFailure(ApiFailure.Kind.TOO_SOON)
        var closed = false
        AutoCloseable { synchronized(lock) { if (!closed) { active.remove(identity); closed = true }; Unit } }
    }
    fun autoAllowed(profile: Profile, now: Long = clock()): Boolean = synchronized(lock) {
        ownAutoRefresh() && !automationHeld() && !manualOnly() && !requiresConnection() && now >= profile.observedAt + monitorIntervalMillis() && automaticNextAllowedAt() <= now && profileBudgetNextAt(now) <= now
    }
    fun reserveAutomatic(now: Long = clock()): Boolean = synchronized(lock) {
        if (quickWatchActive() || automationHeld() || manualOnly() || requiresConnection() || automaticNextAllowedAt() > now) return false
        if (!settings().edit().putLong("auto_after", now + monitorIntervalMillis()).commit()) throw ApiFailure(ApiFailure.Kind.STORAGE)
        if (preferences.getLong("auto_after", 0) <= now && !preferences.edit().remove("auto_after").commit()) throw ApiFailure(ApiFailure.Kind.STORAGE)
        true
    }
    fun begin(now: Long = clock(), account: String? = budgetOwner()) = synchronized(lock) {
        if (blockedUntil() > now) throw ApiFailure(ApiFailure.Kind.LIMITED)
        if (budgetNextAt(now, account) > now) throw ApiFailure(ApiFailure.Kind.BUDGET)
        if (nextAllowedAt(account) > now) throw ApiFailure(ApiFailure.Kind.TOO_SOON)
        val delay = delayMillis()
        require(delay in RequestDefaults.MIN_SPACING_MS..RequestDefaults.MAX_SPACING_MS)
        val count = attempts()
        val accounts = accountBudgets().mapValues { (_, times) -> times.filter { it > now - RequestBudgetPolicy.DAY } }
            .filterValues { it.isNotEmpty() }.toMutableMap()
        account?.let { val key = RequestBudgetPolicy.ownerKey(it); accounts[key] = RequestBudgetPolicy.reserve(accounts[key].orEmpty(), now) }
        if (!preferences.edit().putLong("next_read_at", now + delay)
                .putString("budget_times", org.json.JSONArray(RequestBudgetPolicy.reserve(budgetTimes(), now)).toString())
                .putString("budget_accounts", RequestBudgetCodec.encode(accounts))
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
                .putInt("limit_streak", 0).commit()) throw ApiFailure(ApiFailure.Kind.STORAGE)
        if(!settings().edit().putBoolean("requires_connection",false).commit())throw ApiFailure(ApiFailure.Kind.STORAGE)
    }
}
