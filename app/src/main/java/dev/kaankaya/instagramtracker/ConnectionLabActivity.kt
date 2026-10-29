package dev.kaankaya.instagramtracker

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID

/** One foreground browser-context experiment. It never exports browser credentials or updates the main vault. */
class ConnectionLabActivity : LocalizedActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var gate: RequestGate
    private lateinit var diagnostics: ProbeDiagnostics
    private lateinit var attempt: ProbeAttempt
    private lateinit var browser: WebView
    private lateinit var username: EditText
    private lateinit var status: TextView
    private lateinit var summary: TextView
    private lateinit var counts: TextView
    private lateinit var startBrowser: Button
    private lateinit var read: Button
    private lateinit var close: Button
    private var ready = false
    private var supported = false
    private var opened = false
    private var closing = false
    private var cleaned = false
    private var pageLimited = false
    private var browserDisposed = false
    private var slot: String? = null
    private var requestedUsername: String? = null
    private var startedAt = 0L
    private var uiGeneration = 0L
    private var consumed = false
    private val preview by lazy { ConnectionLabMode.enabled(this) }
    private val origin by lazy { ConnectionLabMode.origin(preview) }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun label(value: String, size: Float = 15f, color: Int = AppUi.ink) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); setPadding(0, dp(5), 0, dp(5))
    }
    private fun action(value: String, callback: () -> Unit) = AppUi.button(this, value, action = callback)
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppUi.systemBars(this)
        if (!preview) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        gate = RequestGate(this, if (preview) "probe_preview_gate" else "request_gate")
        diagnostics = ProbeDiagnostics(this, if (preview) "probe_preview_diagnostics" else "connection_probe")
        attempt = ProbeAttempt(gate, diagnostics)
        consumed = savedInstanceState?.getBoolean("consumed") ?: false
        try {
            gate.setPrototypeActive(true)
            if (!preview) MonitorScheduler.cancel(this)
            diagnostics.record(ProbeDiagnostics.Event.OPENED)
        } catch (_: Exception) { finish(); return }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(16), dp(24), dp(24)) }
        body.addView(label(getString(R.string.ui_connection_prototype), 28f).apply { typeface = Typeface.DEFAULT_BOLD })
        body.addView(label(if (preview) getString(R.string.ui_demo_sample_data_no_network_requests) else getString(R.string.ui_one_controlled_profile_read_background_checks_paused), 13f, AppUi.violet))
        body.addView(label(getString(R.string.ui_open_instagram_sign_in_complete_sign_in), 14f))
        username = EditText(this).apply {
            hint = getString(R.string.ui_username); setTextColor(AppUi.ink); setHintTextColor(AppUi.muted)
            setSingleLine(true); filters = arrayOf(android.text.InputFilter.LengthFilter(31))
            setText(savedInstanceState?.getString("username") ?: if (preview) "lab.demo" else "")
        }; body.addView(username)
        startBrowser = action(if (preview) getString(R.string.ui_open_sample_session) else getString(R.string.ui_open_instagram_sign_in), ::openBrowser)
        read = action(getString(R.string.ui_start_single_read), ::startProbe)
        close = action(getString(R.string.ui_end_experiment), ::closeLab)
        for (button in listOf(startBrowser, read)) body.addView(button, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        status = label(getString(R.string.ui_no_profile_request_has_been_sent_yet), 14f, AppUi.violet); body.addView(status)
        counts = label("", 21f, AppUi.teal); body.addView(counts)
        summary = label("", 12f, AppUi.muted); body.addView(summary)
        browser = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            settings.apply {
                javaScriptEnabled = true; domStorageEnabled = true
                allowFileAccess = false; allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                setSupportMultipleWindows(false); javaScriptCanOpenWindowsAutomatically = false
                @Suppress("DEPRECATION") setSaveFormData(false)
            }
        }
        WebView.setWebContentsDebuggingEnabled(false)
        LoginBrowserResources.restrictWorkers()
        CookieManager.getInstance().apply { setAcceptCookie(true); setAcceptThirdPartyCookies(browser, false) }
        browser.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = !allowedNavigation(request.url.toString())
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (!allowedResource(request.url.toString()) || !preview && !LoginBrowserPolicy.resource(request.method, request.url.toString())) return LoginBrowserResources.blocked()
                val profile = request.url.path == "/api/v1/users/web_profile_info/"
                try { diagnostics.record(if (profile) ProbeDiagnostics.Event.PROFILE_RESOURCE else ProbeDiagnostics.Event.PAGE_RESOURCE) } catch (_: Exception) { }
                return ConnectionLabMode.intercept(this@ConnectionLabActivity, request, preview)
                    ?: if (!preview && request.method == "GET" && LoginBrowserPolicy.landing(request.url.toString())) LoginBrowserResources.landing() else null
            }
            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                if (browserDisposed) return
                ready = false; supported = false; ++uiGeneration
                if (attempt.running) cancelProbe(getString(R.string.ui_the_page_changed_the_operation_was_cancelled))
                updateControls()
            }
            override fun onPageFinished(view: WebView, url: String) {
                if (browserDisposed) return
                ready = ProbePolicy.usablePage(url, origin)
                updateControls()
                if (!ready) {
                    if (!attempt.running && !consumed) status.setText(R.string.lab_login_instruction)
                    return
                }
                val ticket = uiGeneration
                view.evaluateJavascript("Boolean(window.fetch && window.AbortController && window.TextDecoder && window.ReadableStream && window.Response && ('body' in Response.prototype))") { result ->
                    if (ticket != uiGeneration || isDestroyed || closing) return@evaluateJavascript
                    supported = result == "true"
                    if (!attempt.running && !consumed) {
                        if (supported) status.setText(R.string.lab_ready)
                        else status.setText(R.string.lab_update_webview)
                    }
                    updateControls()
                }
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                if (browserDisposed) return
                if (!request.isForMainFrame) {
                    // Subresource errors belong to the page. The explicit fetch reports its own status through JS.
                    try { diagnostics.record(ProbeDiagnostics.Event.PAGE_HTTP_ERROR, errorResponse.statusCode) } catch (_: Exception) { }
                    return
                }
                if (errorResponse.statusCode == 429 && !pageLimited) {
                    pageLimited = true
                    val retry = errorResponse.responseHeaders?.entries?.firstOrNull { it.key.equals("Retry-After", true) }?.value
                    if (attempt.running) finishProbe(JSONObject().put("status", 429).put("reason", "limited").put("retryAfter", retry))
                    else {
                        try { gate.limited(retry, reason = "HTTP 429 / prototip sayfası"); diagnostics.record(ProbeDiagnostics.Event.LIMITED, 429) }
                        catch (_: Exception) { }
                        status.setText(R.string.lab_limited)
                    }
                    disposeBrowser(); updateControls()
                }
            }
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel(); ready = false
                if (attempt.running) finishProbe(JSONObject().put("reason", "connection"))
                status.setText(R.string.lab_tls_failure); updateControls()
            }
        }
        body.addView(browser, LinearLayout.LayoutParams(-1, dp(420)).apply { topMargin = dp(12) })
        body.addView(close, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        body.addView(label(getString(R.string.ui_results_stay_in_this_experiment_screen_browser), 12f, AppUi.muted))
        val scroll = ScrollView(this).apply { setBackgroundColor(AppUi.background); addView(body) }
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            @Suppress("DEPRECATION") view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        setContentView(scroll); scroll.requestApplyInsets()
        username.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updateControls() }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        if (android.os.Build.VERSION.SDK_INT >= 33) onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, ::closeLab)
        updateControls()
    }
    private fun allowedNavigation(url: String) = if (preview) try { java.net.URI(url).let { it.scheme == "https" && it.host == java.net.URI(origin).host && it.rawUserInfo == null && it.port == -1 } } catch (_: Exception) { false }
        else LoginBrowserPolicy.navigation(url)
    private fun allowedResource(url: String) = if (preview) allowedNavigation(url) else NavigationPolicy.isLoginResource(url)
    private fun openBrowser() {
        if (opened || closing || gate.nextAllowedAt() > System.currentTimeMillis()) return
        opened = true; ready = false; browser.loadUrl(ConnectionLabMode.startUrl(preview)); updateControls()
    }
    private fun updateControls() {
        if (!::read.isInitialized || closing) return
        val now = System.currentTimeMillis()
        startBrowser.isEnabled = !opened && gate.nextAllowedAt() <= now
        read.isEnabled = ready && supported && !consumed && !attempt.running && ProfileInput.username(username.text.toString()) != null && gate.nextPrototypeAt() <= now
        username.isEnabled = !attempt.running
        val wait = gate.nextPrototypeAt().takeIf { it > now }?.let {
            getString(R.string.lab_wait, SimpleDateFormat("d MMM HH:mm:ss", AppLanguage.locale(this)).format(Date(it)))
        }.orEmpty()
        summary.text = getString(R.string.lab_summary, diagnostics.count(ProbeDiagnostics.Event.STARTED), diagnostics.count(ProbeDiagnostics.Event.PROFILE_RESOURCE), diagnostics.last(), wait)
    }
    private fun startProbe() {
        if (closing || consumed || attempt.running || !ready || !supported || !ProbePolicy.usablePage(browser.url, origin)) return
        val name = ProfileInput.username(username.text.toString()) ?: return
        try { attempt.start() } catch (failure: ApiFailure) { status.text = failure.userMessage(this@ConnectionLabActivity); updateControls(); return }
        catch (_: Exception) { status.setText(R.string.lab_start_failure); updateControls(); return }
        consumed = true; requestedUsername = name; startedAt = android.os.SystemClock.elapsedRealtime()
        val key = "__tracker_probe_${UUID.randomUUID().toString().replace("-", "")}"; slot = key
        val ticket = ++uiGeneration
        val settings = JSONObject().put("slot", key).put("origin", origin).put("username", name)
        status.setText(R.string.lab_waiting); updateControls()
        try {
            val script = assets.open("connection_probe.js").bufferedReader().use { it.readText() }
            browser.evaluateJavascript("$script($settings)") { started ->
                if (ticket != uiGeneration || !attempt.running || isDestroyed) return@evaluateJavascript
                if (started != "true") finishProbe(JSONObject().put("reason", "invalid")) else handler.post(poll)
            }
        } catch (_: Exception) { finishProbe(JSONObject().put("reason", "invalid")) }
    }
    private val poll = object : Runnable {
        override fun run() {
            val key = slot ?: return
            if (!attempt.running || closing) return
            if (!ProbePolicy.usablePage(browser.url, origin)) { cancelProbe(getString(R.string.ui_the_page_changed_the_operation_was_cancelled)); return }
            if (android.os.SystemClock.elapsedRealtime() - startedAt > 25000) { finishProbe(JSONObject().put("reason", "timeout")); return }
            val ticket = uiGeneration
            browser.evaluateJavascript("JSON.stringify(window[${JSONObject.quote(key)}] || {stage:'missing'})") { raw ->
                if (ticket != uiGeneration || !attempt.running || isDestroyed) return@evaluateJavascript
                try {
                    val result = ProbePolicy.decode(raw)
                    when (result.optString("stage")) {
                        "done" -> finishProbe(result)
                        "waiting" -> handler.postDelayed(this, 200)
                        else -> cancelProbe(getString(R.string.ui_the_page_state_changed_the_operation_was))
                    }
                } catch (_: Exception) { finishProbe(JSONObject().put("reason", "invalid")) }
            }
        }
    }
    private fun finishProbe(result: JSONObject) {
        if (!attempt.running) return
        try {
            val profile = attempt.complete(result, requireNotNull(requestedUsername))
            if (profile != null) {
                val format = java.text.NumberFormat.getIntegerInstance(AppLanguage.locale(this))
                counts.text = getString(R.string.lab_counts, profile.username, format.format(profile.followers), format.format(profile.following))
                status.setText(R.string.lab_success)
            }
        } catch (failure: ApiFailure) { status.text = failure.userMessage(this@ConnectionLabActivity) }
        catch (_: Exception) { status.setText(R.string.lab_invalid) }
        finally { stopScript(); disposeBrowser(); updateControls() }
    }
    private fun stopScript() {
        ++uiGeneration; handler.removeCallbacks(poll)
        if (!browserDisposed) slot?.let { key -> browser.evaluateJavascript("(()=>{const s=window[${JSONObject.quote(key)}];if(s&&s.cancel)s.cancel();delete window[${JSONObject.quote(key)}];})()", null) }
        slot = null
    }
    private fun cancelProbe(message: String) {
        stopScript(); disposeBrowser()
        try { attempt.cancel() } finally { status.text = message; updateControls() }
    }
    private fun closeLab() {
        if (closing) return
        closing = true; close.isEnabled = false; read.isEnabled = false; startBrowser.isEnabled = false
        cancelProbe(getString(R.string.ui_ending_the_experiment_clearing_browser_data))
        val complete = {
            cleaned = true
            try { gate.setPrototypeActive(false) } finally { finish() }
        }
        if (!opened) { complete(); return }
        WebStorage.getInstance().deleteAllData()
        CookieManager.getInstance().removeAllCookies { complete() }
    }
    override fun onStop() {
        if (::attempt.isInitialized && attempt.running) cancelProbe(getString(R.string.ui_the_app_went_into_the_background_the))
        if (::browser.isInitialized && opened && !browserDisposed) {
            disposeBrowser(); status.setText(R.string.lab_browser_closed); updateControls()
        }
        super.onStop()
    }
    private fun disposeBrowser() {
        if (browserDisposed || !::browser.isInitialized) return
        browserDisposed = true; ready = false; supported = false
        browser.stopLoading(); browser.clearCache(true)
        (browser.parent as? android.view.ViewGroup)?.removeView(browser); browser.destroy()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("consumed", consumed); outState.putString("username", username.text.toString())
        super.onSaveInstanceState(outState)
    }
    @Deprecated("Legacy Android back callback")
    @SuppressLint("GestureBackNavigation") // API 33+ uses the registered OnBackInvokedCallback above.
    @Suppress("DEPRECATION")
    override fun onBackPressed() { closeLab() }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::attempt.isInitialized) attempt.cancel()
        if (::browser.isInitialized) {
            disposeBrowser()
            if (opened && !cleaned && !isChangingConfigurations) {
                WebStorage.getInstance().deleteAllData(); CookieManager.getInstance().removeAllCookies(null)
            }
        }
        // A killed process leaves the persisted hold in place; reopening the lab provides explicit recovery.
        super.onDestroy()
    }
}
