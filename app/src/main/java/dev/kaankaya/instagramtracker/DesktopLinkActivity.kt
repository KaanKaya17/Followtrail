package dev.kaankaya.instagramtracker

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.Future

class DesktopLinkActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var gate: RequestGate
    private lateinit var api: InstagramApi
    private lateinit var link: DesktopLink
    private var staged: Session? = null
    private var busy = false
    private var attempted = false
    private var generation = 0
    private var pending: Future<*>? = null
    private var status = ""
    private var prepared = false
    private var visibleScroll: ScrollView? = null
    private val accent = AppUi.violet
    private val gateWake = Runnable { if (!isDestroyed && !busy) render() }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppUi.systemBars(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        gate = RequestGate(this); api = InstagramApi(gate); link = DesktopLink(this)
        gate.setMobileLinkActive(true); MonitorScheduler.cancel(this)
        attempted = savedInstanceState?.getBoolean("attempted") ?: false
        savedInstanceState?.getString("binding")?.let { binding ->
            staged = Session(savedInstanceState.getString("owner")!!, "", savedInstanceState.getString("ua")!!, binding)
        }
        if (staged == null) staged = try { MobileSessions(this).pending() } catch (_: Exception) { null }
        prepared = link.isPrepared()
        if (android.os.Build.VERSION.SDK_INT >= 33) onBackInvokedDispatcher.registerOnBackInvokedCallback(0) { close() }
        render()
    }
    private fun render() {
        if (isDestroyed || isFinishing) return
        main.removeCallbacks(gateWake)
        if (prepared && !link.isPrepared()) prepared = false
        val position = visibleScroll?.scrollY ?: 0
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(20), dp(24), dp(28))
            setBackgroundColor(AppUi.background)
        }
        fun label(value: String, size: Float, color: Int = AppUi.ink) {
            page.addView(TextView(this).apply { text = value; textSize = size; setTextColor(color); setPadding(0, dp(8), 0, dp(12)) })
        }
        fun action(resource: Int, enabled: Boolean = true, primary: Boolean = true, allowDuringWork: Boolean = false, run: () -> Unit) {
            page.addView(AppUi.button(this, getString(resource), primary, enabled && (!busy || allowDuringWork), action = run),
                LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
        val header = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        header.addView(AppUi.iconButton(this, "back", getString(R.string.desktop_link_close)) { close() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(TextView(this).apply { setText(R.string.desktop_link_header); textSize = 16f; setTextColor(AppUi.muted) })
        page.addView(header, LinearLayout.LayoutParams(-1, dp(56)).apply { bottomMargin = dp(16) })
        val step = if (staged != null) 3 else if (prepared) 2 else 1
        val steps = LinearLayout(this)
        listOf("1 · Hazırla", "2 · Aktar", "3 · Bağlan").forEachIndexed { index, title ->
            steps.addView(AppUi.text(this, title, 11f, if (step == index + 1) AppUi.violet else AppUi.muted, step == index + 1).apply {
                gravity = android.view.Gravity.CENTER; setPadding(dp(6), dp(12), dp(6), dp(12))
                background = AppUi.shape(this@DesktopLinkActivity, if (step == index + 1) AppUi.violetSoft else AppUi.background, 14)
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        page.addView(steps, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(22) })
        label(if (staged != null) "Aktarım hazır." else if (prepared) "Sıra bilgisayarında." else getString(R.string.desktop_link_title), 32f, accent)
        label(if (staged != null) "Hazır olduğunda sayılarını al." else if (prepared) "Aktarımı çalıştır, sonra buradan devam et." else getString(R.string.desktop_link_intro), 16f)
        val mark = TextView(this).apply {
            text = ""; setPadding(dp(32), dp(32), dp(32), dp(32))
            setCompoundDrawablesWithIntrinsicBounds(null, AppUi.Icon(this@DesktopLinkActivity, if (staged != null) "check" else "link", accent, 48), null, null)
            gravity = android.view.Gravity.CENTER
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            background = AppUi.shape(this@DesktopLinkActivity, AppUi.violetSoft, 28)
        }
        page.addView(mark, LinearLayout.LayoutParams(-1, dp(120)).apply { topMargin = dp(16); bottomMargin = dp(24) })
        if (busy) label(getString(R.string.desktop_link_busy), 14f)
        else if (status.isNotEmpty()) label(status, 14f, AppUi.violet)
        val remaining = gate.nextPrototypeAt() - System.currentTimeMillis()
        if (staged != null && remaining > 0) label(getString(R.string.desktop_link_pause,
            SimpleDateFormat("d MMM · HH:mm", Locale.forLanguageTag("tr-TR")).format(Date(gate.nextPrototypeAt()))), 14f, AppUi.violet)
        when {
            staged != null -> action(R.string.desktop_link_read, !attempted && remaining <= 0) {
                val reference = staged ?: return@action
                attempted = true
                execute({ api.testMobileConnection(reference) }) { profile ->
                    SessionVault(this).link(reference, profile); gate.succeeded()
                    android.widget.Toast.makeText(this, R.string.desktop_link_success, android.widget.Toast.LENGTH_SHORT).show()
                    close(); ""
                }
            }
            prepared -> action(R.string.desktop_link_import) {
                execute({
                    val state = link.importBundle(); gate.pauseAutomatic()
                    val reference = MobileSessions(this).stage(state); link.clear(); reference
                }) { reference -> staged = reference; prepared = false; "" }
            }
            else -> action(R.string.desktop_link_prepare) {
                execute({ link.prepare() }) { prepared = true; "" }
            }
        }
        if (staged != null) action(R.string.desktop_link_switch, primary = false) {
            android.app.AlertDialog.Builder(this).setTitle("Başka hesap bağlansın mı?")
                .setMessage("Bekleyen aktarım kaldırılır. Bağlı hesabın ve bekleme süren korunur.")
                .setNegativeButton("Vazgeç", null).setPositiveButton("Devam et") { _, _ ->
                    execute({ MobileSessions(this).discardPending(); link.clear() }) {
                        staged = null; prepared = false; attempted = false; ""
                    }
                }.show()
        }
        action(R.string.desktop_link_help, primary = false) {
            android.app.AlertDialog.Builder(this).setTitle("Bilgisayardan bağlan")
                .setMessage("1. Bilgisayardaki InstagramCounter uygulamasında test hesabını bağla.\n\n2. InstagramTracker klasöründe scripts/link-desktop.ps1 dosyasını çalıştır.\n\n3. Burada Aktarımı al'a dokun. Parola veya oturum bilgisi kopyalaman gerekmez.")
                .setPositiveButton("Anladım", null).show()
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(AppUi.background); isFillViewport = true
            setOnApplyWindowInsetsListener { view, insets ->
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                insets
            }; addView(page)
        }
        visibleScroll = scroll; setContentView(scroll); scroll.post { scroll.scrollTo(0, position) }
        if (staged != null && remaining > 0 && !attempted) main.postDelayed(gateWake, remaining)
    }
    private fun <T> execute(action: () -> T, success: (T) -> String) {
        if (busy) return
        busy = true; val ticket = ++generation; render()
        pending = worker.submit {
            val result = try {
                if (Thread.currentThread().isInterrupted) throw ApiFailure(ApiFailure.Kind.CANCELLED)
                Result.success(action())
            } catch (failure: Exception) { Result.failure(failure) }
            main.post {
                if (!isDestroyed && ticket == generation) {
                    busy = false
                    status = result.fold(onSuccess = { value ->
                        try { success(value) } catch (_: Exception) { ApiFailure(ApiFailure.Kind.STORAGE).userMessage() }
                    }, onFailure = { failure -> if ((failure as? ApiFailure)?.kind == ApiFailure.Kind.LIMITED) "" else (failure as? ApiFailure)?.userMessage() ?: getString(R.string.desktop_link_error) })
                    render()
                }
            }
        }
    }
    private fun close() {
        ++generation; pending?.cancel(true); api.cancel()
        try { link.clear(); gate.setMobileLinkActive(false); finish() }
        catch (_: Exception) { busy = false; status = ApiFailure(ApiFailure.Kind.STORAGE).userMessage(); render() }
    }
    override fun onStop() {
        super.onStop()
        if (busy) { ++generation; pending?.cancel(true); api.cancel(); busy = false; status = ApiFailure(ApiFailure.Kind.CANCELLED).userMessage() }
    }
    override fun onResume() { super.onResume(); if (::gate.isInitialized && !busy) render() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("attempted", attempted)
        staged?.let { outState.putString("binding", it.mobileBinding); outState.putString("owner", it.userId); outState.putString("ua", it.userAgent) }
        super.onSaveInstanceState(outState)
    }
    @Deprecated("API 24-32 back callback")
    @android.annotation.SuppressLint("GestureBackNavigation") // API 33+ uses OnBackInvokedDispatcher.
    @Suppress("DEPRECATION")
    override fun onBackPressed() { close() }
    override fun onDestroy() {
        ++generation; pending?.cancel(true); api.cancel(); main.removeCallbacksAndMessages(null); worker.shutdownNow(); super.onDestroy()
    }
}
