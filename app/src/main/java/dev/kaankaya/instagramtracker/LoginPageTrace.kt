package dev.kaankaya.instagramtracker

import android.content.Context
import org.json.JSONObject

/** Page-load evidence only: finite categories, counts and numeric errors; no URL/header/body/input. */
class LoginPageTrace(context: Context, private val clock: () -> Long = System::currentTimeMillis) {
    private val preferences = context.getSharedPreferences("login_page_trace", Context.MODE_PRIVATE)
    private val resources = linkedMapOf<String, Int>()
    private val http = linkedMapOf<String, Int>()
    private val errors = linkedMapOf<String, Int>()
    private val navigation = linkedMapOf<String, Int>()
    private val blockedDocuments = linkedMapOf<String, WebDocumentFacts>()
    private var securitySchema: JSONObject?=null
    private var securityView: JSONObject?=null
    private var captchaSchema: JSONObject?=null
    private var codeSubmission: JSONObject?=null
    private val verificationSchemas = linkedMapOf<String, JSONObject>()
    private val startedAt = clock()
    private var documentsStarted = 0
    private var documentsFinished = 0
    private var formVisible: Boolean? = null
    private var authentication: WebLoginResponseFacts? = null
    private val exchangeFailures = linkedMapOf<String, JSONObject>()
    private var active = true
    @Synchronized fun resource(method: String, decision: LoginBrowserPolicy.ResourceDecision) {
        if (!active) return
        val verb = when (method) { "GET" -> "GET"; "POST" -> "POST"; else -> "OTHER" }
        increment(resources, "${if (decision.allowed) "ALLOW" else "BLOCK"}:$verb:${decision.kind.name}")
    }
    @Synchronized fun started(route: WebLoginChallenge.Companion.Route? = null) { if (active) {
        documentsStarted++; formVisible = null; increment(navigation,"STARTED:${route?.name ?: "UNKNOWN"}")
    } }
    @Synchronized fun finished(route: WebLoginChallenge.Companion.Route? = null) { if (active) {
        documentsFinished++; increment(navigation,"FINISHED:${route?.name ?: "UNKNOWN"}"); flush()
    } }
    @Synchronized fun navigated(allowed: Boolean, redirect: Boolean, route: WebLoginChallenge.Companion.Route?) {
        if(active) increment(navigation,"${if(allowed)"ALLOW" else "BLOCK"}:${if(redirect)"HTTP_REDIRECT" else "PAGE"}:${route?.name ?: "UNKNOWN"}")
    }
    @Synchronized fun http(status: Int, mainFrame: Boolean, allowed: Boolean) {
        require(status in 400..599)
        if (active) increment(http, "${if (allowed) "ALLOWED_RESOURCE" else "LOCAL_BLOCK"}:${if (mainFrame) "PAGE" else "PART"}:$status")
    }
    @Synchronized fun error(code: Int, mainFrame: Boolean) {
        if (active) increment(errors, "${if (mainFrame) "PAGE" else "PART"}:$code")
    }
    @Synchronized fun form(visible: Boolean?) { if (active) { formVisible = visible; flush() } }
    @Synchronized fun authentication(facts: WebLoginResponseFacts) { if (active) { authentication = facts; flush() } }
    @Synchronized internal fun exchangeFailure(stage: String, kind: ApiFailure.Kind, error: Exception) {
        require(stage in setOf("AUTH", "SECURITY_VIEW", "CODE_SUBMISSION"))
        if (!active) return
        val transport = error as? WebTransportFailure
        exchangeFailures[stage] = JSONObject().put("kind", kind.name)
            .put("transport", transport?.reason?.name ?: JSONObject.NULL)
            .put("networkCode", transport?.networkCode ?: JSONObject.NULL)
        flush()
    }
    @Synchronized fun blockedDocument(facts: WebDocumentFacts) { if(active && blockedDocuments.size<4) {
        blockedDocuments[facts.json().toString()]=facts
    } }
    @Synchronized fun securitySchema(raw: String) {if(active){securitySchema=AuthPlatformCodeEntry.schema(raw);flush()}}
    @Synchronized fun captchaSchema(raw: String) {if(active){
        captchaSchema=AuthPlatformSchema.inspect(raw,AuthPlatformCaptchaContent.DOCUMENT,AuthPlatformCaptchaContent.OPERATION,"query");flush()
    }}
    @Synchronized internal fun verificationSchema(operation: AuthPlatformVerification.Operation, raw: String) {
        if (active) {
            verificationSchemas[operation.name] = AuthPlatformSchema.inspect(raw, operation.document, operation.name, "mutation")
            flush()
        }
    }
    @Synchronized fun securityView(status: Int,parsed: Boolean,validData: Boolean,applied: Boolean?,document: String? = null) {if(active){
        require(status in 100..599)
        require(document == null || document in setOf(AuthPlatformCodeEntry.DOCUMENT,AuthPlatformCaptchaContent.DOCUMENT))
        securityView=JSONObject().put("status",status).put("jsonParsed",parsed).put("validData",validData).put("cookiesApplied",applied ?: JSONObject.NULL)
            .put("document",document ?: JSONObject.NULL)
        flush()
    }}
    @Synchronized fun codeSubmission(status: Int, parsed: Boolean, validData: Boolean, applied: Boolean?) { if(active) {
        require(status in 100..599)
        codeSubmission=JSONObject().put("status",status).put("jsonParsed",parsed).put("validData",validData).put("cookiesApplied",applied ?: JSONObject.NULL)
        flush()
    } }
    @Synchronized fun flush() {
        if (!active) return
        val report = JSONObject().put("format", 3).put("startedAt", startedAt)
            .put("documentsStarted", documentsStarted).put("documentsFinished", documentsFinished)
            .put("formVisible", formVisible ?: JSONObject.NULL)
            .put("resources", JSONObject(resources as Map<*, *>))
            .put("http", JSONObject(http as Map<*, *>)).put("errors", JSONObject(errors as Map<*, *>))
            .put("navigation", JSONObject(navigation as Map<*, *>))
            .put("blockedDocuments",org.json.JSONArray(blockedDocuments.values.map {it.json()}))
            .put("securitySchema",securitySchema ?: JSONObject.NULL)
            .put("securityView",securityView ?: JSONObject.NULL)
            .put("captchaSchema",captchaSchema ?: JSONObject.NULL)
            .put("codeSubmission",codeSubmission ?: JSONObject.NULL)
            .put("verificationSchemas", JSONObject(verificationSchemas as Map<*, *>))
            .put("authentication", authentication?.json() ?: JSONObject.NULL)
            .put("exchangeFailures", JSONObject(exchangeFailures as Map<*, *>))
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
            // Four bounded document facts now include 16 one-level input names each, plus two verification schemas.
            .getString("report", null)?.also { require(it.length <= 32768); JSONObject(it) }
    }
}
