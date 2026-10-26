package dev.kaankaya.instagramtracker

import android.content.Context

data class SavedPhoneDiagnostic(val operation: PhoneLoginOperation, val attemptedAt: Long, val status: Int?)

/** One local diagnostic: only a fixed operation, timestamp and HTTP status. No request/response content. */
class PhoneLoginJournal(context: Context) {
    companion object { private val lock = Any() }
    private val preferences = context.getSharedPreferences("phone_login_diagnostic", Context.MODE_PRIVATE)
    fun write(value: SavedPhoneDiagnostic) = synchronized(lock) {
        require(value.attemptedAt > 0 && (value.status == null || value.status in 100..599))
        val edit = preferences.edit().putString("operation", value.operation.name).putLong("at", value.attemptedAt)
        if (value.status == null) edit.remove("status") else edit.putInt("status", value.status)
        if (!edit.commit()) throw ApiFailure(ApiFailure.Kind.STORAGE)
    }
    fun read(): SavedPhoneDiagnostic? = synchronized(lock) {
        if (!preferences.contains("operation")) {
            check(!preferences.contains("at") && !preferences.contains("status"))
            return@synchronized null
        }
        val operation = PhoneLoginOperation.valueOf(preferences.getString("operation", null) ?: error("Missing operation"))
        val at = preferences.getLong("at", 0)
        val status = if (preferences.contains("status")) preferences.getInt("status", 0) else null
        require(at > 0 && (status == null || status in 100..599))
        SavedPhoneDiagnostic(operation, at, status)
    }
    fun clear() = synchronized(lock) {
        if (!preferences.edit().clear().commit()) throw ApiFailure(ApiFailure.Kind.STORAGE)
    }
}
