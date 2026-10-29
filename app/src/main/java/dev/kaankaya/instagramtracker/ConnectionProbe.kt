package dev.kaankaya.instagramtracker

import android.content.Context
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URI

/** Only value-free diagnostics are persisted. No URL/query, username, cookie, header or body is accepted. */
class ProbeDiagnostics(private val context: Context, name: String = "connection_probe") {
    companion object { private val lock = Any() }
    private val preferences = context.getSharedPreferences(name, 0)
    enum class Event { OPENED, PAGE_RESOURCE, PROFILE_RESOURCE, PAGE_HTTP_ERROR, STARTED, SUCCEEDED, LIMITED, AUTH_REQUIRED, INVALID, CONNECTION, CANCELLED }
    fun record(event: Event, status: Int? = null): Unit = synchronized(lock) {
        require(status == null || status in 100..599)
        if (event == Event.PAGE_RESOURCE || event == Event.PROFILE_RESOURCE) {
            preferences.edit().putLong(event.name, preferences.getLong(event.name, 0) + 1).apply()
            return@synchronized
        }
        val old = preferences.getString("events", "[]")!!
        val array = org.json.JSONArray(old)
        val next = org.json.JSONArray()
        for (i in maxOf(0, array.length() - 29) until array.length()) next.put(array.getJSONObject(i))
        next.put(JSONObject().put("event", event.name).put("at", System.currentTimeMillis()).apply { status?.let { put("status", it) } })
        check(preferences.edit().putString("events", next.toString())
            .putLong(event.name, preferences.getLong(event.name, 0) + 1).commit())
    }
    fun count(event: Event): Long = synchronized(lock) { preferences.getLong(event.name, 0) }
    fun last(): String = synchronized(lock) { org.json.JSONArray(preferences.getString("events", "[]")!!).let { events ->
        if (events.length() == 0) context.getString(R.string.ui_no_attempts_yet) else events.getJSONObject(events.length() - 1).let {
            it.getString("event") + if (it.has("status")) " · HTTP ${it.getInt("status")}" else ""
        }
    } }
}

object ProbePolicy {
    const val ORIGIN = "https://www.instagram.com"
    const val MAX_RESULT_CHARS = 8192
    fun usablePage(url: String?, origin: String = ORIGIN): Boolean = try {
        val uri = URI(url ?: "")
        val expected = URI(origin)
        uri.scheme == "https" && uri.rawUserInfo == null && uri.host == expected.host && uri.port in listOf(-1, 443) &&
            (LoginBrowserPolicy.landing(url ?: "") ||
                uri.path.trim('/').substringBefore('/') !in setOf("accounts", "challenge", "checkpoint"))
    } catch (_: Exception) { false }
    fun decode(raw: String): JSONObject {
        require(raw.length <= MAX_RESULT_CHARS)
        val text = JSONTokener(raw).nextValue() as? String ?: error("Invalid WebView result")
        require(text.length <= MAX_RESULT_CHARS)
        return JSONObject(text)
    }
    fun failure(result: JSONObject): ApiFailure.Kind? {
        val status = result.optInt("status", 0)
        val reason = result.optString("reason")
        return when {
            status == 429 || reason in setOf("limited", "feedback", "wait") -> ApiFailure.Kind.LIMITED
            status == 401 || reason == "login_required" -> ApiFailure.Kind.SESSION_EXPIRED
            status == 403 || reason == "verification" -> ApiFailure.Kind.VERIFICATION
            status == 404 -> ApiFailure.Kind.NOT_FOUND
            reason == "cancelled" -> ApiFailure.Kind.CANCELLED
            reason == "connection" || reason == "timeout" -> ApiFailure.Kind.CONNECTION
            status != 200 || reason != "ok" -> ApiFailure.Kind.INVALID_RESPONSE
            else -> null
        }
    }
}

/** Owns a single explicit probe and the shared in-flight lease, including navigation/cancellation. */
class ProbeAttempt(private val gate: RequestGate, private val diagnostics: ProbeDiagnostics) {
    private var lease: AutoCloseable? = null
    var attempted = false; private set
    val running get() = lease != null
    fun start() {
        if (attempted || running) throw ApiFailure(ApiFailure.Kind.TOO_SOON)
        val claimed = gate.claim()
        try { gate.beginPrototype(); diagnostics.record(ProbeDiagnostics.Event.STARTED); lease = claimed; attempted = true }
        catch (failure: Exception) { claimed.close(); throw failure }
    }
    fun complete(result: JSONObject, username: String): Profile? {
        if (!running) return null
        try {
            result.optInt("status", 0).takeIf { it in 100..599 }?.let(gate::observedStatus)
            val failure = ProbePolicy.failure(result)
            if (failure != null) {
                when (failure) {
                    ApiFailure.Kind.LIMITED -> {
                        val signal = when {
                            result.optInt("status") == 429 -> InstagramResponsePolicy.Restriction.HTTP_429
                            result.optString("reason") == "feedback" -> InstagramResponsePolicy.Restriction.FEEDBACK
                            else -> InstagramResponsePolicy.Restriction.WAIT
                        }
                        gate.limited(result.optString("retryAfter").takeIf { it.length <= 160 }, reason = signal.label)
                        diagnostics.record(ProbeDiagnostics.Event.LIMITED, result.optInt("status").takeIf { it in 100..599 })
                    }
                    ApiFailure.Kind.SESSION_EXPIRED, ApiFailure.Kind.VERIFICATION -> {
                        gate.pauseForConnection(); diagnostics.record(ProbeDiagnostics.Event.AUTH_REQUIRED, result.optInt("status").takeIf { it in 100..599 })
                    }
                    ApiFailure.Kind.CONNECTION -> diagnostics.record(ProbeDiagnostics.Event.CONNECTION)
                    ApiFailure.Kind.CANCELLED -> diagnostics.record(ProbeDiagnostics.Event.CANCELLED)
                    else -> diagnostics.record(ProbeDiagnostics.Event.INVALID, result.optInt("status").takeIf { it in 100..599 })
                }
                throw ApiFailure(failure)
            }
            val profile = try { ProfileParser.byUsername(result.getJSONObject("payload").toString(), username, System.currentTimeMillis()) }
            catch (error: Exception) { diagnostics.record(ProbeDiagnostics.Event.INVALID); throw error }
            diagnostics.record(ProbeDiagnostics.Event.SUCCEEDED, 200)
            // The browser experiment does not validate the legacy HTTP adapter or resume its automation.
            return profile
        } finally { lease?.close(); lease = null }
    }
    fun cancel() {
        if (running) {
            try { diagnostics.record(ProbeDiagnostics.Event.CANCELLED) } finally { lease?.close(); lease = null }
        }
    }
}
