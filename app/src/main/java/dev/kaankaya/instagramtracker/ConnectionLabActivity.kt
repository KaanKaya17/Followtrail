package dev.kaankaya.instagramtracker

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import java.util.Locale
import java.util.UUID

/** One foreground browser-context experiment. It never exports browser credentials or updates the main vault. */
class ConnectionLabActivity : Activity() {
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
    private fun label(value: String, size: Float = 15f, color: Int = Color.WHITE) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); setPadding(0, dp(5), 0, dp(5))
    }
    private fun action(value: String, callback: () -> Unit) = Button(this).apply {
        text = value; isAllCaps = false; setOnClickListener { callback() }
        setTextColor(Color.rgb(15, 18, 25)); background = GradientDrawable().apply {
            setColor(Color.rgb(181, 245, 218)); cornerRadius = dp(16).toFloat()
        }; setPadding(dp(12), dp(12), dp(12), dp(12)); minimumHeight = dp(52)
    }
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        body.addView(label("Bağlantı prototipi", 28f).apply { typeface = Typeface.DEFAULT_BOLD })
        body.addView(label(if (preview) "DEMO · ÖRNEK VERİ · AĞ İSTEĞİ YOK" else "Tek kontrollü profil okuması · Arka plan kontrolleri duraklatıldı.", 13f, Color.rgb(210, 196, 255)))
        body.addView(label("Instagram oturumunu aç. Giriş ve varsa doğrulamayı tarayıcıda tamamla; sonra profilin kullanıcı adını girip tek okumayı başlat.", 14f))
        username = EditText(this).apply {
            hint = "@kullanıcıadı"; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
            setSingleLine(true); filters = arrayOf(android.text.InputFilter.LengthFilter(31))
            setText(savedInstanceState?.getString("username") ?: if (preview) "lab.demo" else "")
        }; body.addView(username)
        startBrowser = action(if (preview) "Örnek oturumu aç" else "Instagram oturumunu aç", ::openBrowser)
        read = action("Tek okumayı başlat", ::startProbe)
        close = action("Denemeyi bitir", ::closeLab)
        for (button in listOf(startBrowser, read)) body.addView(button, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        status = label("Henüz profil isteği gönderilmedi.", 14f, Color.rgb(210, 196, 255)); body.addView(status)
        counts = label("", 21f, Color.rgb(181, 245, 218)); body.addView(counts)
        summary = label("", 12f, Color.LTGRAY); body.addView(summary)
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
        CookieManager.getInstance().apply { setAcceptCookie(true); setAcceptThirdPartyCookies(browser, false) }
        browser.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = !allowedNavigation(request.url.toString())
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (!allowedResource(request.url.toString())) return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(byteArrayOf()))
                val profile = request.url.path == "/api/v1/users/web_profile_info/"
                try { diagnostics.record(if (profile) ProbeDiagnostics.Event.PROFILE_RESOURCE else ProbeDiagnostics.Event.PAGE_RESOURCE) } catch (_: Exception) { }
                return ConnectionLabMode.intercept(this@ConnectionLabActivity, request, preview)
            }
            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                if (browserDisposed) return
                ready = false; supported = false; ++uiGeneration
                if (attempt.running) cancelProbe("Sayfa değişti; işlem iptal edildi.")
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
        body.addView(label("Sonuç bu deneme ekranında kalır. Tarayıcı çerezleri ayrı HTTP istemcisine aktarılmaz. Otomatik yeniden deneme yapılmaz.", 12f, Color.LTGRAY))
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(15, 18, 25)); addView(body) }
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
        else NavigationPolicy.isInstagram(url)
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
            getString(R.string.lab_wait, SimpleDateFormat("d MMM HH:mm:ss", Locale.forLanguageTag("tr-TR")).format(Date(it)))
        }.orEmpty()
        summary.text = getString(R.string.lab_summary, diagnostics.count(ProbeDiagnostics.Event.STARTED), diagnostics.count(ProbeDiagnostics.Event.PROFILE_RESOURCE), diagnostics.last(), wait)
    }
    private fun startProbe() {
        if (closing || consumed || attempt.running || !ready || !supported || !ProbePolicy.usablePage(browser.url, origin)) return
        val name = ProfileInput.username(username.text.toString()) ?: return
        try { attempt.start() } catch (failure: ApiFailure) { status.text = failure.userMessage(); updateControls(); return }
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
            if (!ProbePolicy.usablePage(browser.url, origin)) { cancelProbe("Sayfa değişti; işlem iptal edildi."); return }
            if (android.os.SystemClock.elapsedRealtime() - startedAt > 25000) { finishProbe(JSONObject().put("reason", "timeout")); return }
            val ticket = uiGeneration
            browser.evaluateJavascript("JSON.stringify(window[${JSONObject.quote(key)}] || {stage:'missing'})") { raw ->
                if (ticket != uiGeneration || !attempt.running || isDestroyed) return@evaluateJavascript
                try {
                    val result = ProbePolicy.decode(raw)
                    when (result.optString("stage")) {
                        "done" -> finishProbe(result)
                        "waiting" -> handler.postDelayed(this, 200)
                        else -> cancelProbe("Sayfa durumu değişti; işlem iptal edildi.")
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
                val format = java.text.NumberFormat.getIntegerInstance(Locale.forLanguageTag("tr-TR"))
                counts.text = getString(R.string.lab_counts, profile.username, format.format(profile.followers), format.format(profile.following))
                status.setText(R.string.lab_success)
            }
        } catch (failure: ApiFailure) { status.text = failure.userMessage() }
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
        cancelProbe("Deneme bitiriliyor; tarayıcı verileri temizleniyor.")
        val complete = {
            cleaned = true
            try { gate.setPrototypeActive(false) } finally { finish() }
        }
        if (!opened) { complete(); return }
        WebStorage.getInstance().deleteAllData()
        CookieManager.getInstance().removeAllCookies { complete() }
    }
    override fun onStop() {
        if (::attempt.isInitialized && attempt.running) cancelProbe("Uygulama arka plana geçti; deneme iptal edildi.")
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
