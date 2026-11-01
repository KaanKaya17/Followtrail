package dev.kaankaya.instagramtracker

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.ScriptHandler
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import okhttp3.Call
import okhttp3.Cookie
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

/** One encrypted login and one context-bound security view. No arbitrary document, replay or social operation. */
class WebLoginRelay(private val context: Context, private val browser: WebView, private val gate: RequestGate,
    private val worker: ExecutorService, private val trace: LoginPageTrace,
    private val failure: (ApiFailure, Int?) -> Unit,
    private val client: Call.Factory = WebRequestClients.create(context),
    private val started: () -> Unit = {},
    private val completed: (WebLoginResponseFacts, WebLoginChallenge?) -> Unit = { _, _ -> },
    private val securityScope: (String) -> Boolean = {false},
    private val securityBlocked: () -> Unit = {},
    private val securityContext: (String) -> String? = {null},
    private val securityStarted: () -> Unit = {},
    private val securityCompleted: () -> Unit = {}) : AutoCloseable {
    private val main = Handler(Looper.getMainLooper())
    private var script: ScriptHandler? = null
    private var installed = false
    private var closed = false
    private var submitted = false
    private var securitySubmitted=false
    private var schemaInspected=false
    private val inspectedVerification = hashSetOf<String>()
    @Volatile private var active: Pending? = null
    private class Pending(val id: String, val reply: JavaScriptReplyProxy, val lease: AutoCloseable, val securityView: Boolean) {
        @Volatile var call: Call? = null
        var future: Future<*>? = null
    }
    fun install(): Boolean {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return false
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return false
        val origins = setOf("https://www.instagram.com")
        WebViewCompat.addWebMessageListener(browser, "trackerLoginRelay", origins) { view, message, origin, mainFrame, reply ->
            if (!closed && view === browser) receive(message, origin, mainFrame, reply)
        }
        installed = true
        try {
            val source = context.assets.open("web_login_relay.js").bufferedReader().use { it.readText() }
            script = WebViewCompat.addDocumentStartJavaScript(browser, source, origins)
            return true
        } catch (_: Exception) { close(); return false }
    }
    private fun reply(proxy: JavaScriptReplyProxy, id: String, value: JSONObject) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return
        try { proxy.postMessage(value.put("id", id).toString()) } catch (_: Exception) { /* Closed renderer. */ }
    }
    private fun record(facts: WebLoginResponseFacts) {
        try { trace.authentication(facts) } catch (_: Exception) { throw ApiFailure(ApiFailure.Kind.STORAGE) }
    }
    private fun recordSecurity(status: Int,parsed: Boolean,valid: Boolean,applied: Boolean?=null) {
        try{trace.securityView(status,parsed,valid,applied)}catch(_: Exception){throw ApiFailure(ApiFailure.Kind.STORAGE)}
    }
    private fun inspectSecuritySchema(page: String) {
        if(schemaInspected)return
        schemaInspected=true
        val script=context.assets.open("web_security_schema.js").bufferedReader().use {it.readText()}
            .replace("__DOCUMENT__",AuthPlatformCodeEntry.DOCUMENT).replace("__OPERATION__",AuthPlatformCodeEntry.OPERATION).replace("__KIND__", "query")
        browser.evaluateJavascript(script) {raw->
            if(!closed && browser.url==page)try{trace.securitySchema(raw ?: "null")}catch(_: Exception){}
        }
    }
    private fun inspectVerificationSchema(page: String, operation: AuthPlatformVerification.Operation) {
        if (!inspectedVerification.add(operation.name)) return
        val script = context.assets.open("web_security_schema.js").bufferedReader().use { it.readText() }
            .replace("__DOCUMENT__", operation.document).replace("__OPERATION__", operation.name).replace("__KIND__", "mutation")
        browser.evaluateJavascript(script) { raw ->
            if (!closed && browser.url == page && securityScope(page))
                try { trace.verificationSchema(operation, raw ?: "null") } catch (_: Exception) { }
        }
    }
    private fun receive(message: WebMessageCompat, origin: Uri, mainFrame: Boolean, proxy: JavaScriptReplyProxy) {
        var id = "0"
        try {
            require(mainFrame && origin.scheme == "https" && origin.host == "www.instagram.com" && origin.port in listOf(-1,443) && origin.userInfo == null)
            val page=browser.url ?: ""
            val security=securityScope(page)
            require(LoginBrowserPolicy.navigation(page) || security)
            require(message.type == WebMessageCompat.TYPE_STRING)
            val raw = requireNotNull(message.data); require(raw.length <= 131072)
            val envelope = JSONObject(raw)
            if (envelope.length() == 1 && envelope.has("cancel")) {
                if (envelope.get("cancel") == active?.id) cancel()
                return
            }
            id = envelope.getString("id"); require(id.matches(Regex("[1-9][0-9]{0,5}")))
            val fields=envelope.keys().asSequence().toSet()
            require(fields==setOf("id","body","headers") || fields==setOf("id","body","headers","path"))
            val path=if(envelope.has("path")) envelope.get("path") as? String else "/api/graphql"
            require(path in WebGraphqlForm.PATHS)
            val body=envelope.getString("body")
            val securityAuthorization=if(security) {
                inspectSecuritySchema(page)
                val approved=try{WebSecurityViewContract.authorize(body,requireNotNull(path),securityContext(page))}catch(_: Exception){null}
                if(approved==null) {
                    val facts = WebDocumentFacts.inspect(body)
                    AuthPlatformVerification.find(facts.document, facts.operation)?.let { inspectVerificationSchema(page, it) }
                    trace.blockedDocument(facts)
                    trace.resource("POST",LoginBrowserPolicy.ResourceDecision(false,LoginBrowserPolicy.ResourceKind.GRAPHQL));trace.flush()
                    reply(proxy,id,JSONObject().put("error",true));securityBlocked();return
                }
                if(securitySubmitted || active!=null){reply(proxy,id,JSONObject().put("error",true));return}
                approved
            } else null
            val authorized=if(!security){require(path=="/api/graphql");WebLoginContract.authorize(body)}else null
            val headers = envelope.getJSONObject("headers"); require(headers.length() <= 24)
            val accepted = linkedMapOf<String, String>()
            for (key in headers.keys()) {
                val value = headers.get(key); require(value is String && value.length <= 2048 && value.all { it.code in 32..126 })
                if (key.lowercase() in setOf("accept", "accept-language", "x-fb-lsd", "x-csrftoken", "x-asbd-id", "x-ig-app-id", "x-fb-friendly-name", "x-root-field-name", "x-ig-max-touch-points"))
                    accepted[key.lowercase()] = value
            }
            if (!security && (submitted || active != null)) throw ApiFailure(ApiFailure.Kind.TOO_SOON)
            val cookies = CookieManager.getInstance().getCookie("https://www.instagram.com/").orEmpty()
            require(cookies.length <= 16384 && cookies.all { it.code in 32..126 })
            val builder=securityAuthorization?.apply(Request.Builder()) ?: requireNotNull(authorized).apply(Request.Builder().url(WebLoginContract.URL))
            val request = WebRequestContext.sameOrigin(builder
                .header("User-Agent", browser.settings.userAgentString).header("Origin", "https://www.instagram.com")
                .header("Referer", if(security)"https://www.instagram.com/auth_platform/" else "https://www.instagram.com/accounts/login/")
                // Derived only after the actual source origin/main frame has been verified above.
                .header("Accept", "*/*").apply {
                    if (cookies.isNotEmpty()) header("Cookie", cookies)
                    accepted.forEach { (key, value) -> header(key, value) }
                }.build())
            val lease = gate.claim()
            if(!security)try { gate.beginWebAuthentication() } catch (error: Exception) { lease.close(); throw error }
            if(security)securitySubmitted=true else submitted=true
            val pending = Pending(id, proxy, lease,security); active = pending
            try {
                if(security)securityStarted() else {
                    trace.resource("POST", LoginBrowserPolicy.ResourceDecision(true, LoginBrowserPolicy.ResourceKind.AUTH)); trace.flush();started()
                }
                pending.future = worker.submit { exchange(pending, request) }
            }
            catch (error: Exception) { active = null; lease.close(); throw error }
        } catch (error: Exception) {
            val problem = error as? ApiFailure ?: ApiFailure(ApiFailure.Kind.POLICY)
            try { trace.resource("POST", LoginBrowserPolicy.ResourceDecision(false, LoginBrowserPolicy.ResourceKind.GRAPHQL)); trace.flush() } catch (_: Exception) { }
            reply(proxy, id, JSONObject().put("error", true))
            failure(problem, null)
        }
    }
    private fun exchange(pending: Pending, request: Request) {
        var status: Int? = null
        try {
            if (active !== pending) return
            if(pending.securityView) {
                val delay=gate.nextAllowedAt()-gate.now()
                if(delay>15000)throw ApiFailure(when {
                    gate.blockedUntil()>gate.now()->ApiFailure.Kind.LIMITED
                    gate.budgetNextAt()>gate.now()->ApiFailure.Kind.BUDGET
                    else->ApiFailure.Kind.TOO_SOON
                })
                if(delay>0)Thread.sleep(delay)
                if(active!==pending)return
                gate.begin()
                trace.resource("POST",LoginBrowserPolicy.ResourceDecision(true,LoginBrowserPolicy.ResourceKind.SECURITY_VIEW));trace.flush()
            }
            val call = client.newCall(request); pending.call = call
            if (active !== pending) { call.cancel(); return }
            call.execute().use { response ->
                status = response.code
                if (active !== pending) return
                gate.observedStatus(response.code)
                if (response.code in 400..599) { trace.http(response.code, false, true); trace.flush() }
                if (response.code == 429) {
                    if(pending.securityView)recordSecurity(429,false,false,false)
                    gate.limited(response.header("Retry-After"), reason = "HTTP 429 / Instagram web giriş isteği")
                    throw ApiFailure(ApiFailure.Kind.LIMITED)
                }
                if (response.code in 300..399) throw ApiFailure(ApiFailure.Kind.VERIFICATION)
                val source = response.body?.source() ?: throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
                source.request(131073)
                if (source.buffer.size > 131072) throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
                val text = source.readUtf8()
                // Only a JSON response is returned to the same document; no body is logged or persisted.
                val json = try { JSONObject(text.removePrefix("for (;;);")) } catch (_: Exception) { null }
                val origin = request.url
                val cookieLines = response.headers("Set-Cookie")
                val parsedCookies = cookieLines.map { value ->
                    if (value.length <= 16384) Cookie.parse(origin, value)?.takeIf { it.matches(origin) } else null
                }
                val inspected = if(!pending.securityView)WebLoginResponseFacts.inspect(response.code, json, parsedCookies.filterNotNull(), parsedCookies.all { it != null }, gate.now()) else null
                val assessment = if(inspected!=null && json!=null)WebLoginChallenge.assess(json, inspected) else null
                val facts = inspected?.copy(challengeStatus = assessment?.status, challengeRoute = assessment?.route)
                if(facts!=null)record(facts) else recordSecurity(response.code,json!=null,json?.let(WebSecurityViewContract::validData)==true)
                if (json == null) throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
                val issue = InstagramResponsePolicy.issue(response.code, json)
                if (issue?.restriction != null) {
                    gate.limited(response.header("Retry-After"), reason = issue.restriction.label)
                    throw ApiFailure(ApiFailure.Kind.LIMITED)
                }
                if (issue != null) throw ApiFailure(issue.kind)
                if (facts!=null && !facts.caaResultPresent) {
                    record(facts.copy(cookiesApplied = false))
                    throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
                }
                if(pending.securityView && !WebSecurityViewContract.validData(json))throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
                val cookies = cookieLines.filterIndexed { index, _ -> parsedCookies[index] != null }
                val result = JSONObject().put("status", response.code).put("body", text)
                    .put("headers", JSONObject().put("content-type", response.header("Content-Type") ?: "application/json"))
                main.post {
                    if (closed || active !== pending) return@post
                    fun finish() {
                        if (closed || active !== pending) return
                        val finalFacts = facts?.copy(cookiesApplied = cookies.isNotEmpty())
                        try { if(finalFacts!=null)record(finalFacts) else recordSecurity(response.code,true,true,cookies.isNotEmpty()) }
                        catch (problem: ApiFailure) {
                            active = null; reply(pending.reply, pending.id, JSONObject().put("error", true)); failure(problem, null); return
                        }
                        active = null; reply(pending.reply, pending.id, result)
                        if(finalFacts!=null)completed(finalFacts, assessment?.challenge)else securityCompleted()
                    }
                    if (cookies.isEmpty()) finish() else {
                        var remaining = cookies.size
                        var rejected = false
                        cookies.forEach { cookie -> CookieManager.getInstance().setCookie("https://www.instagram.com/", cookie) { accepted ->
                            if (!accepted) rejected = true
                            remaining--
                            if (remaining == 0) {
                                if (!rejected) finish() else if (!closed && active === pending) {
                                    try { if(facts!=null)record(facts.copy(cookiesApplied = false))else recordSecurity(response.code,true,true,false) } catch (_: ApiFailure) { }
                                    active = null; reply(pending.reply, pending.id, JSONObject().put("error", true))
                                    failure(ApiFailure(ApiFailure.Kind.STORAGE), null)
                                }
                            }
                        } }
                    }
                }
            }
        } catch (error: Exception) {
            val problem = error as? ApiFailure ?: ApiFailure(ApiFailure.Kind.CONNECTION)
            main.post {
                if (!closed && active === pending) {
                    active = null; reply(pending.reply, pending.id, JSONObject().put("error", true)); failure(problem, status)
                }
            }
        } finally { pending.lease.close() }
    }
    /** Navigation/background cancellation owns both queued work and the active HTTP exchange. */
    fun cancel() = cancel(replyToPage = true)
    private fun cancel(replyToPage: Boolean) {
        val pending = active ?: return
        active = null; pending.call?.cancel(); pending.future?.cancel(true); pending.lease.close()
        if(replyToPage) reply(pending.reply, pending.id, JSONObject().put("error", true))
        if (!closed) failure(ApiFailure(ApiFailure.Kind.CANCELLED), null)
    }
    override fun close() = dispose(browserAvailable = true)
    fun rendererGone() = dispose(browserAvailable = false)
    private fun dispose(browserAvailable: Boolean) {
        if (closed) return
        closed = true; cancel(replyToPage = browserAvailable)
        if (browserAvailable && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) script?.remove()
        script = null
        if (browserAvailable && installed && WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))
            WebViewCompat.removeWebMessageListener(browser, "trackerLoginRelay")
        installed = false
    }
}
