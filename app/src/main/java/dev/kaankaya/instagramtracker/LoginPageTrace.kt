package dev.kaankaya.instagramtracker

import android.content.Context
import org.json.JSONObject

/** Page-load evidence only: finite categories, counts and numeric errors; no URL/header/body/input. */
class LoginPageTrace(context: Context, private val clock: () -> Long = System::currentTimeMillis) {
    private val preferences = context.getSharedPreferences("login_page_trace", Context.MODE_PRIVATE)
    private val resources = linkedMapOf<String, Int>()
    private val http = linkedMapOf<String, Int>()
    private val errors = linkedMapOf<String, Int>()
    private val startedAt = clock()
    private var documentsStarted = 0
    private var documentsFinished = 0
    private var formVisible: Boolean? = null
    private var authentication: WebLoginResponseFacts? = null
    private var active = true
    @Synchronized fun resource(method: String, decision: LoginBrowserPolicy.ResourceDecision) {
        if (!active) return
        val verb = when (method) { "GET" -> "GET"; "POST" -> "POST"; else -> "OTHER" }
        increment(resources, "${if (decision.allowed) "ALLOW" else "BLOCK"}:$verb:${decision.kind.name}")
    }
    @Synchronized fun started() { if (active) { documentsStarted++; formVisible = null } }
    @Synchronized fun finished() { if (active) { documentsFinished++; flush() } }
    @Synchronized fun http(status: Int, mainFrame: Boolean, allowed: Boolean) {
        require(status in 400..599)
        if (active) increment(http, "${if (allowed) "ALLOWED_RESOURCE" else "LOCAL_BLOCK"}:${if (mainFrame) "PAGE" else "PART"}:$status")
    }
    @Synchronized fun error(code: Int, mainFrame: Boolean) {
        if (active) increment(errors, "${if (mainFrame) "PAGE" else "PART"}:$code")
    }
    @Synchronized fun form(visible: Boolean?) { if (active) { formVisible = visible; flush() } }
    @Synchronized fun authentication(facts: WebLoginResponseFacts) { if (active) { authentication = facts; flush() } }
    @Synchronized fun flush() {
        if (!active) return
        val report = JSONObject().put("format", 2).put("startedAt", startedAt)
            .put("documentsStarted", documentsStarted).put("documentsFinished", documentsFinished)
            .put("formVisible", formVisible ?: JSONObject.NULL)
            .put("resources", JSONObject(resources as Map<*, *>))
            .put("http", JSONObject(http as Map<*, *>)).put("errors", JSONObject(errors as Map<*, *>))
            .put("authentication", authentication?.json() ?: JSONObject.NULL)
        check(preferences.edit().putString("report", report.toString()).commit())
    }
    @Synchronized fun close() { if (active) { try { flush() } finally { active = false } } }
    private fun increment(values: MutableMap<String, Int>, key: String) {
        // Bound unknown HTTP combinations too; all methods and resource categories are fixed labels.
        if (key !in values && values.size >= 32) return
        values[key] = ((values[key] ?: 0) + 1).coerceAtMost(999)
    }
    companion object {
        fun read(context: Context): String? = context.getSharedPreferences("login_page_trace", Context.MODE_PRIVATE)
            .getString("report", null)?.also { require(it.length <= 8192); JSONObject(it) }
    }
}
