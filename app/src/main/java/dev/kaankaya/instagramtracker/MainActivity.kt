package dev.kaankaya.instagramtracker

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import java.io.ByteArrayInputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val bg = Color.rgb(16, 19, 27)
    private val panel = Color.rgb(28, 33, 43)
    private val mint = Color.rgb(186, 243, 213)
    private val lilac = Color.rgb(216, 204, 250)
    private val white = Color.rgb(242, 244, 235)
    private val muted = Color.rgb(164, 174, 181)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var api: InstagramApi
    private lateinit var gate: RequestGate
    private lateinit var vault: SessionVault
    private lateinit var root: FrameLayout
    private var account: SavedAccount? = null
    private var loading = true
    private var message: String? = null
    private var needsConnection = false
    private var web: WebView? = null
    private var loginStatus: TextView? = null
    private var completionButton: Button? = null
    private var generation = 0
    private var loginChecking = false
    private var loginAttempted = false
    private var loginLoaded = false
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.DEBUG) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        root = FrameLayout(this).apply { setBackgroundColor(bg) }
        root.setOnApplyWindowInsetsListener { view, insets ->
            @Suppress("DEPRECATION")
            view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        setContentView(root)
        root.requestApplyInsets()
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                if (web != null) leaveLogin() else finish()
            }
        }
        vault = SessionVault(this)
        gate = RequestGate(this)
        api = InstagramApi(gate)
        render()
        worker.execute {
            val saved = try { vault.read() } catch (_: Exception) {
                main.post { message = ApiFailure(ApiFailure.Kind.STORAGE).userMessage(); needsConnection = true }
                null
            }
            main.post {
                if (isDestroyed) return@post
                account = saved; loading = false; render()
                // An outstanding pause is restored without issuing a network request.
                if (saved != null && gate.nextAllowedAt() <= System.currentTimeMillis()) refresh()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!::gate.isInitialized || web != null || loading) return
        if (account != null && !needsConnection && gate.nextAllowedAt() <= System.currentTimeMillis()) refresh()
        else render()
    }

    private fun shape(color: Int, radius: Int = 24, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }
    private fun text(value: String, size: Float, color: Int = white, medium: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        typeface = Typeface.create(if (medium) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
        includeFontPadding = false
    }
    private fun stack() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun LinearLayout.gap(value: Int) { addView(View(this@MainActivity), LinearLayout.LayoutParams(1, dp(value))) }
    private fun button(label: String, primary: Boolean = true, networkAction: Boolean = false, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 16f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(if (primary) bg else white)
        background = shape(if (primary) mint else panel, 18)
        minimumHeight = dp(56); setPadding(dp(16), dp(12), dp(16), dp(12))
        setOnClickListener { action() }
        isEnabled = !loading && (!networkAction || gate.nextAllowedAt() <= System.currentTimeMillis())
        alpha = if (isEnabled) 1f else .5f
    }
    private fun badge(label: String, color: Int = mint) = text(label, 10f, color, true).apply {
        letterSpacing = .10f; setPadding(dp(12), dp(8), dp(12), dp(8))
        background = shape(panel, 20)
    }
    private fun render() {
        if (web != null || isDestroyed) return
        root.removeAllViews()
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        val page = stack().apply { setPadding(dp(24), dp(24), dp(24), dp(24)) }
        scroll.addView(page, ViewGroup.LayoutParams(-1, -2)); root.addView(scroll)
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(text("it", 25f, mint, true).apply { contentDescription = "InstagramTracker" }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(badge("BU CİHAZDA"))
        page.addView(header); page.gap(34)
        val saved = account
        if (saved == null) {
            page.addView(text("Hesabın,\nbir bakışta.", 38f, white, true).apply { setLineSpacing(dp(3).toFloat(), 1f) })
            page.gap(14)
            page.addView(text("Takipçi ve takip edilen sayıların.\nHepsi bu.", 17f, muted).apply { setLineSpacing(dp(5).toFloat(), 1f) })
            page.addView(OrbitView(this), LinearLayout.LayoutParams(-1, dp(230)))
        } else {
            page.addView(text("HESAP ÖZETİ", 11f, muted, true).apply { letterSpacing = .18f })
            page.gap(12)
            page.addView(text("@${saved.profile.username}", 29f, white, true).apply {
                maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
            })
            page.gap(10)
            page.addView(text("Sayıların burada.\nKontrol sende.", 17f, muted))
            page.gap(28)
            val row = LinearLayout(this)
            fun stat(label: String, value: Long, color: Int): LinearLayout = stack().apply {
                background = shape(color); minimumHeight = dp(155); setPadding(dp(18), dp(22), dp(18), dp(22))
                addView(text(label, 13f, bg, true)); gap(26)
                addView(text(NumberFormat.getIntegerInstance(Locale.forLanguageTag("tr-TR")).format(value), 30f, bg, true).apply {
                    maxLines = 1
                    if (android.os.Build.VERSION.SDK_INT >= 26) setAutoSizeTextTypeUniformWithConfiguration(16, 30, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
                })
            }
            row.addView(stat("Takipçi", saved.profile.followers, mint), LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(10) })
            row.addView(stat("Takip edilen", saved.profile.following, lilac), LinearLayout.LayoutParams(0, -2, 1f))
            page.addView(row); page.gap(22)
            page.addView(text("SON BAŞARILI OKUMA", 10f, muted, true).apply { letterSpacing = .12f })
            page.gap(7)
            page.addView(text(SimpleDateFormat("d MMMM · HH:mm:ss", Locale.forLanguageTag("tr-TR")).format(Date(saved.profile.observedAt)), 14f))
            page.gap(8)
            page.addView(text(if (loading) "Kayıtlı sayılar gösteriliyor; güncel yanıt bekleniyor." else "Son başarılı gözlemin. Yenile ile güncelleyebilirsin.", 12f, muted))
            page.gap(26)
        }
        if (loading) {
            val progress = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            progress.addView(ProgressBar(this).apply { indeterminateTintList = android.content.res.ColorStateList.valueOf(mint) }, LinearLayout.LayoutParams(dp(22), dp(22)))
            progress.addView(text(if (saved == null) "Bağlantı hazırlanıyor…" else "Güncel sayılar alınıyor…", 14f, muted), LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(12) })
            page.addView(progress); page.gap(16)
        }
        val pause = pauseMessage()
        (pause ?: message)?.let { page.addView(text(it, 14f, lilac).apply { setLineSpacing(dp(4).toFloat(), 1f) }); page.gap(16) }
        if (saved == null || needsConnection) {
            page.addView(button(if (saved == null) "Instagram ile bağlan" else "Bağlantıyı yenile", networkAction = true) { startLogin() }, LinearLayout.LayoutParams(-1, -2))
            if (saved == null && vault.exists()) {
                page.gap(10); page.addView(button("Kayıtlı bağlantıyı kaldır", false) { confirmRemove() }, LinearLayout.LayoutParams(-1, -2))
            }
        } else page.addView(button("Sayıları yenile", networkAction = true) { refresh() }, LinearLayout.LayoutParams(-1, -2))
        if (saved != null) {
            page.gap(12)
            page.addView(button("Bağlantıyı kaldır", false) { confirmRemove() }, LinearLayout.LayoutParams(-1, -2))
        }
        page.gap(24)
        page.addView(text("Oturumun yalnızca bu cihazda saklanır.", 12f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
            setOnClickListener { privacy() }; contentDescription = "Gizlilik bilgilerini aç"
        })
        page.addView(text("INSTAGRAMTRACKER  /  ${BuildConfig.VERSION_NAME}", 9f, muted).apply { gravity = Gravity.CENTER; letterSpacing = .14f })
        scheduleGateWake()
    }

    private fun pauseMessage(): String? {
        if (gate.blockedUntil() <= System.currentTimeMillis()) return null
        val until = SimpleDateFormat("HH:mm", Locale.forLanguageTag("tr-TR")).format(Date(gate.blockedUntil()))
        return "Instagram isteği sınırladı. Uygulama $until saatine kadar yeni istek göndermeyecek. " +
            "Bu bekleme, Instagram'ın kısıtlamayı o saatte kaldıracağını garanti etmez."
    }
    private val gateWake = Runnable {
        if (!isDestroyed) {
            if (web == null) render()
            else {
                completionButton?.isEnabled = !loginChecking && gate.nextAllowedAt() <= System.currentTimeMillis()
                completionButton?.alpha = if (completionButton?.isEnabled == true) 1f else .5f
                // No automatic retry when a pause expires.
                loginStatus?.text = pauseMessage() ?: getString(R.string.connection_ready)
            }
        }
    }
    private fun scheduleGateWake() {
        main.removeCallbacks(gateWake)
        val wait = gate.nextAllowedAt() - System.currentTimeMillis()
        if (wait > 0) main.postDelayed(gateWake, wait)
    }

    private fun refresh() {
        val saved = account ?: return
        if (loading) return
        if (gate.nextAllowedAt() > System.currentTimeMillis()) {
            message = pauseMessage() ?: ApiFailure(ApiFailure.Kind.TOO_SOON).userMessage(); render(); return
        }
        loading = true; message = null; render()
        val ticket = ++generation
        worker.execute {
            val result = try {
                val updated = SavedAccount(saved.session, api.read(saved.session))
                Result.success(updated)
            } catch (failure: ApiFailure) { Result.failure(failure) }
            main.post {
                if (isDestroyed || ticket != generation) return@post
                loading = false
                result.onSuccess {
                    try { vault.write(it); account = it; needsConnection = false }
                    catch (_: Exception) { showFailure(ApiFailure(ApiFailure.Kind.STORAGE)) }
                }
                    .onFailure { showFailure(it as ApiFailure) }
                render()
            }
        }
    }

    private fun showFailure(failure: ApiFailure) {
        message = pauseMessage() ?: failure.userMessage()
        needsConnection = failure.kind in setOf(ApiFailure.Kind.SESSION_EXPIRED, ApiFailure.Kind.VERIFICATION,
            ApiFailure.Kind.WRONG_ACCOUNT, ApiFailure.Kind.STORAGE)
    }

    private fun privacy() {
        AlertDialog.Builder(this).setTitle("Gizlilik, sade haliyle")
            .setMessage("Şifren Instagram'ın kendi giriş sayfasına gider; uygulama şifre alanını okumaz.\n\n" +
                "Oturum ve son sayılar Android Keystore ile şifrelenir. Giriş sırasında WebView kendi geçici tarayıcı verilerini oluşturabilir; bağlantı tamamlanınca veya iptal edilince temizlenir.\n\n" +
                "İstekler Instagram'a gider. Kendi sunucumuz, reklam, analitik veya veri toplama SDK'mız yok. Bulut yedekleme ve cihaz aktarımı kapalıdır.\n\n" +
                "Bu bağımsız bir uygulamadır. Instagram giriş veya doğrulama isteyebilir; kesintisiz çalışma garantisi yoktur.")
            .setPositiveButton("Anladım", null).show()
    }

    private fun confirmRemove() {
        if (loading) return
        AlertDialog.Builder(this).setTitle("Bağlantı kaldırılsın mı?")
            .setMessage("Bu uygulamadaki kayıtlı oturum ve son sayılar silinir.")
            .setNegativeButton("Vazgeç", null).setPositiveButton("Kaldır") { _, _ ->
                loading = true; render()
                worker.execute {
                    val ok = try { vault.clear(); true } catch (_: Exception) { false }
                    main.post {
                        if (isDestroyed) return@post
                        loading = false
                        if (ok) { account = null; needsConnection = false; message = "Bu cihazdaki bağlantı kaldırıldı." }
                        else message = "Bağlantı silinemedi. Lütfen tekrar dene."
                        render()
                    }
                }
            }.show()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun startLogin() {
        if (loading || web != null) return
        if (gate.nextAllowedAt() > System.currentTimeMillis()) { render(); return }
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        loading = false; loginChecking = false; loginAttempted = false; loginLoaded = false
        val page = stack().apply { setBackgroundColor(bg) }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8)) }
        header.addView(button("Vazgeç", false) { leaveLogin() }, LinearLayout.LayoutParams(-2, dp(48)))
        header.addView(text("  instagram.com", 16f, mint, true))
        page.addView(header)
        loginStatus = text("Instagram'ın kendi giriş sayfası. Giriş tamamlanınca otomatik döneceksin.", 12f, muted).apply { setPadding(dp(18), dp(8), dp(18), dp(8)) }
        page.addView(loginStatus)
        val browser = try { WebView(this) } catch (_: Exception) {
            message = "Android System WebView açılamadı. WebView'i güncelleyip tekrar dene."
            if (BuildConfig.DEBUG) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            render(); return
        }
        web = browser
        WebView.setWebContentsDebuggingEnabled(false)
        browser.setBackgroundColor(Color.WHITE)
        browser.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true
            allowFileAccess = false; allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_NO_CACHE
            setSupportMultipleWindows(false); javaScriptCanOpenWindowsAutomatically = false
            @Suppress("DEPRECATION") setSaveFormData(false)
        }
        CookieManager.getInstance().apply { setAcceptCookie(true); setAcceptThirdPartyCookies(browser, false) }
        browser.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = !NavigationPolicy.isInstagram(request.url.toString())
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                if (NavigationPolicy.isLoginResource(request.url.toString())) null
                else WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(byteArrayOf()))
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                main.post { loginStatus?.setText(R.string.connection_tls_error) }
            }
            override fun onPageFinished(view: WebView, url: String) {
                if (NavigationPolicy.isInstagram(url)) { loginLoaded = true; checkLogin() }
            }
        }
        page.addView(browser, LinearLayout.LayoutParams(-1, 0, 1f))
        completionButton = button("Giriş yaptım, bağlantıyı tamamla", false, networkAction = true) { checkLogin(manual = true) }
        page.addView(completionButton, LinearLayout.LayoutParams(-1, dp(54)))
        root.removeAllViews(); root.addView(page)
        CookieManager.getInstance().removeAllCookies {
            if (web === browser) {
                WebStorage.getInstance().deleteAllData(); browser.clearCache(true)
                browser.loadUrl("https://www.instagram.com/accounts/login/")
                main.postDelayed(loginPoll, 1500)
            }
        }
    }

    private val loginPoll = object : Runnable {
        override fun run() { if (web != null) { checkLogin(); main.postDelayed(this, 1500) } }
    }
    private fun checkLogin(manual: Boolean = false) {
        val browser = web ?: return
        if (!loginLoaded || loginChecking || (!manual && loginAttempted)) return
        if (gate.nextAllowedAt() > System.currentTimeMillis()) {
            if (manual) loginStatus?.text = pauseMessage() ?: ApiFailure(ApiFailure.Kind.TOO_SOON).userMessage()
            scheduleGateWake(); return
        }
        val session = SessionCookies.fromHeader(CookieManager.getInstance().getCookie("https://www.instagram.com/"), browser.settings.userAgentString)
        if (session == null) {
            if (manual) loginStatus?.setText(R.string.connection_incomplete)
            return
        }
        loginChecking = true; loginAttempted = true
        completionButton?.isEnabled = false; completionButton?.alpha = .5f
        loginStatus?.setText(R.string.connection_checking)
        val ticket = ++generation
        worker.execute {
            val result = try {
                val linked = SavedAccount(session, api.read(session))
                Result.success(linked)
            } catch (failure: ApiFailure) { Result.failure(failure) }
            catch (_: Exception) { Result.failure(ApiFailure(ApiFailure.Kind.STORAGE)) }
            main.post {
                if (isDestroyed || ticket != generation || web !== browser) return@post
                loginChecking = false
                result.onSuccess {
                    // Commit on the UI owner after checking the active login ticket.
                    // A cancelled WebView result must never save a new session later.
                    try {
                        vault.write(it)
                        account = it; message = "Bağlandı. Sonraki açılışta sayılar otomatik yenilenecek."
                        needsConnection = false; leaveLogin()
                    } catch (_: Exception) { loginStatus?.text = ApiFailure(ApiFailure.Kind.STORAGE).userMessage() }
                }.onFailure { loginStatus?.text = pauseMessage() ?: (it as ApiFailure).userMessage() }
                completionButton?.isEnabled = !loginChecking && gate.nextAllowedAt() <= System.currentTimeMillis()
                completionButton?.alpha = if (completionButton?.isEnabled == true) 1f else .5f
                scheduleGateWake()
            }
        }
    }

    private fun leaveLogin() {
        ++generation; api.cancel()
        main.removeCallbacks(loginPoll)
        val browser = web
        web = null; loginStatus = null; completionButton = null; loginChecking = false
        browser?.apply {
            stopLoading(); loadUrl("about:blank"); clearHistory(); clearCache(true)
            (parent as? ViewGroup)?.removeView(this); removeAllViews(); destroy()
        }
        CookieManager.getInstance().removeAllCookies { CookieManager.getInstance().flush() }
        WebStorage.getInstance().deleteAllData()
        if (BuildConfig.DEBUG) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        loading = false; render()
    }

    @Deprecated("Back handling retained for API 24-32 compatibility")
    @SuppressLint("GestureBackNavigation") // API 33+ uses the registered platform callback; this is the legacy path.
    @Suppress("DEPRECATION")
    override fun onBackPressed() { if (web != null) leaveLogin() else super.onBackPressed() }

    override fun onDestroy() {
        ++generation; main.removeCallbacksAndMessages(null); api.cancel()
        if (web != null) {
            CookieManager.getInstance().removeAllCookies { CookieManager.getInstance().flush() }
            WebStorage.getInstance().deleteAllData()
        }
        web?.apply { stopLoading(); (parent as? ViewGroup)?.removeView(this); destroy() }
        web = null
        worker.shutdownNow()
        super.onDestroy()
    }
}
