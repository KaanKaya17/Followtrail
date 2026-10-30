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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

/** One origin-bound, encrypted login POST. No redirects/replays, arbitrary URL or social operation. */
class WebLoginRelay(private val context: Context, private val browser: WebView, private val gate: RequestGate,
    private val worker: ExecutorService, private val trace: LoginPageTrace,
    private val failure: (ApiFailure, Int?) -> Unit,
    private val client: Call.Factory = WebRequestClients.create(context),
    private val started: () -> Unit = {},
    private val completed: (WebLoginResponseFacts, WebLoginChallenge?) -> Unit = { _, _ -> }) : AutoCloseable {
    private val main = Handler(Looper.getMainLooper())
    private var script: ScriptHandler? = null
    private var installed = false
    private var closed = false
    private var submitted = false
    @Volatile private var active: Pending? = null
    private class Pending(val id: String, val reply: JavaScriptReplyProxy, val lease: AutoCloseable) {
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
    private fun receive(message: WebMessageCompat, origin: Uri, mainFrame: Boolean, proxy: JavaScriptReplyProxy) {
        var id = "0"
        try {
            require(mainFrame && origin.scheme == "https" && origin.host == "www.instagram.com" && origin.port in listOf(-1,443) && origin.userInfo == null)
            require(LoginBrowserPolicy.navigation(browser.url ?: ""))
            require(message.type == WebMessageCompat.TYPE_STRING)
            val raw = requireNotNull(message.data); require(raw.length <= 131072)
            val envelope = JSONObject(raw)
            if (envelope.length() == 1 && envelope.has("cancel")) {
                if (envelope.get("cancel") == active?.id) cancel()
                return
            }
            id = envelope.getString("id"); require(id.matches(Regex("[1-9][0-9]{0,5}")))
            require(envelope.keys().asSequence().toSet() == setOf("id", "body", "headers"))
            val authorized = WebLoginContract.authorize(envelope.getString("body"))
            val headers = envelope.getJSONObject("headers"); require(headers.length() <= 24)
            val accepted = linkedMapOf<String, String>()
            for (key in headers.keys()) {
                val value = headers.get(key); require(value is String && value.length <= 2048 && value.all { it.code in 32..126 })
                if (key.lowercase() in setOf("accept", "accept-language", "x-fb-lsd", "x-csrftoken", "x-asbd-id", "x-ig-app-id", "x-fb-friendly-name", "x-root-field-name", "x-ig-max-touch-points"))
                    accepted[key.lowercase()] = value
            }
            if (submitted || active != null) throw ApiFailure(ApiFailure.Kind.TOO_SOON)
            val cookies = CookieManager.getInstance().getCookie("https://www.instagram.com/").orEmpty()
            require(cookies.length <= 16384 && cookies.all { it.code in 32..126 })
            val request = WebRequestContext.sameOrigin(authorized.apply(Request.Builder().url(WebLoginContract.URL))
                .header("User-Agent", browser.settings.userAgentString).header("Origin", "https://www.instagram.com")
                .header("Referer", "https://www.instagram.com/accounts/login/")
                // Derived only after the actual source origin/main frame has been verified above.
                .header("Accept", "*/*").apply {
                    if (cookies.isNotEmpty()) header("Cookie", cookies)
                    accepted.forEach { (key, value) -> header(key, value) }
                }.build())
            val lease = gate.claim()
            try { gate.beginWebAuthentication() } catch (error: Exception) { lease.close(); throw error }
            submitted = true
            val pending = Pending(id, proxy, lease); active = pending
            try {
                trace.resource("POST", LoginBrowserPolicy.ResourceDecision(true, LoginBrowserPolicy.ResourceKind.AUTH)); trace.flush()
                started()
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
            val call = client.newCall(request); pending.call = call
            if (active !== pending) { call.cancel(); return }
            call.execute().use { response ->
                status = response.code
                if (active !== pending) return
                gate.observedStatus(response.code)
                if (response.code in 400..599) { trace.http(response.code, false, true); trace.flush() }
                if (response.code == 429) {
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
                val origin = WebLoginContract.URL.toHttpUrl()
                val cookieLines = response.headers("Set-Cookie")
                val parsedCookies = cookieLines.map { value ->
                    if (value.length <= 16384) Cookie.parse(origin, value)?.takeIf { it.matches(origin) } else null
                }
                val inspected = WebLoginResponseFacts.inspect(response.code, json, parsedCookies.filterNotNull(), parsedCookies.all { it != null }, gate.now())
                val assessment = json?.let { WebLoginChallenge.assess(it, inspected) }
                val facts = inspected.copy(challengeStatus = assessment?.status, challengeRoute = assessment?.route)
                record(facts)
                if (json == null) throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
                val issue = InstagramResponsePolicy.issue(response.code, json)
                if (issue?.restriction != null) {
                    gate.limited(response.header("Retry-After"), reason = issue.restriction.label)
                    throw ApiFailure(ApiFailure.Kind.LIMITED)
                }
                if (issue != null) throw ApiFailure(issue.kind)
                if (!facts.caaResultPresent) {
                    record(facts.copy(cookiesApplied = false))
                    throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
                }
                val cookies = cookieLines.filterIndexed { index, _ -> parsedCookies[index] != null }
                val result = JSONObject().put("status", response.code).put("body", text)
                    .put("headers", JSONObject().put("content-type", response.header("Content-Type") ?: "application/json"))
                main.post {
                    if (closed || active !== pending) return@post
                    fun finish() {
                        if (closed || active !== pending) return
                        val finalFacts = facts.copy(cookiesApplied = cookies.isNotEmpty())
                        try { record(finalFacts) }
                        catch (problem: ApiFailure) {
                            active = null; reply(pending.reply, pending.id, JSONObject().put("error", true)); failure(problem, null); return
                        }
                        active = null; reply(pending.reply, pending.id, result)
                        completed(finalFacts, assessment?.challenge)
                    }
                    if (cookies.isEmpty()) finish() else {
                        var remaining = cookies.size
                        var rejected = false
                        cookies.forEach { cookie -> CookieManager.getInstance().setCookie("https://www.instagram.com/", cookie) { accepted ->
                            if (!accepted) rejected = true
                            remaining--
                            if (remaining == 0) {
                                if (!rejected) finish() else if (!closed && active === pending) {
                                    try { record(facts.copy(cookiesApplied = false)) } catch (_: ApiFailure) { }
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
    fun cancel() {
        val pending = active ?: return
        active = null; pending.call?.cancel(); pending.future?.cancel(true); pending.lease.close()
        reply(pending.reply, pending.id, JSONObject().put("error", true))
        if (!closed) failure(ApiFailure(ApiFailure.Kind.CANCELLED), null)
    }
    override fun close() {
        if (closed) return
        closed = true; cancel()
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) script?.remove()
        script = null
        if (installed && WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))
            WebViewCompat.removeWebMessageListener(browser, "trackerLoginRelay")
        installed = false
    }
}
