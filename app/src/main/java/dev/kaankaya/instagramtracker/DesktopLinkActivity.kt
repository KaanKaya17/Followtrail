package dev.kaankaya.instagramtracker

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.WindowManager
import android.widget.Button
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
    private val mint = Color.rgb(186, 243, 213)
    private val gateWake = Runnable { if (!isDestroyed && !busy) render() }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        gate = RequestGate(this); api = InstagramApi(gate); link = DesktopLink(this)
        gate.setMobileLinkActive(true); MonitorScheduler.cancel(this)
        attempted = savedInstanceState?.getBoolean("attempted") ?: false
        savedInstanceState?.getString("binding")?.let { binding ->
            staged = Session(savedInstanceState.getString("owner")!!, "", savedInstanceState.getString("ua")!!, binding)
        }
        if (staged == null) staged = try { MobileSessions(this).pending() } catch (_: Exception) { null }
        status = getString(if (staged == null) R.string.desktop_link_idle else R.string.desktop_link_imported)
        if (android.os.Build.VERSION.SDK_INT >= 33) onBackInvokedDispatcher.registerOnBackInvokedCallback(0) { close() }
        render()
    }
    private fun render() {
        main.removeCallbacks(gateWake)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(36), dp(24), dp(32))
            setBackgroundColor(Color.rgb(16, 19, 27))
        }
        fun label(value: String, size: Float, color: Int = Color.rgb(242, 244, 235)) {
            page.addView(TextView(this).apply { text = value; textSize = size; setTextColor(color); setPadding(0, dp(10), 0, dp(18)) })
        }
        fun action(resource: Int, enabled: Boolean = true, allowDuringWork: Boolean = false, run: () -> Unit) {
            page.addView(Button(this).apply {
                setText(resource); isAllCaps = false; textSize = 16f; setTextColor(Color.rgb(16, 19, 27))
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                background = GradientDrawable().apply { setColor(mint); cornerRadius = dp(18).toFloat() }
                isEnabled = enabled && (!busy || allowDuringWork); alpha = if (isEnabled) 1f else .45f
                setPadding(dp(16), dp(14), dp(16), dp(14)); setOnClickListener { run() }
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
        }
        label(getString(R.string.desktop_link_title), 32f, mint)
        label(getString(R.string.desktop_link_intro), 17f)
        label(status, 15f)
        val remaining = gate.nextPrototypeAt() - System.currentTimeMillis()
        if (remaining > 0) label(getString(R.string.desktop_link_pause,
            SimpleDateFormat("d MMM · HH:mm:ss", Locale.forLanguageTag("tr-TR")).format(Date(gate.nextPrototypeAt()))), 14f, Color.rgb(216, 204, 250))
        action(R.string.desktop_link_prepare, staged == null) {
            execute({ link.prepare() }) { getString(R.string.desktop_link_ready) }
        }
        action(R.string.desktop_link_import, staged == null) {
            execute({
                val state = link.importBundle()
                gate.pauseAutomatic()
                val reference = MobileSessions(this).stage(state)
                link.clear()
                reference
            }) { reference ->
                staged = reference
                getString(R.string.desktop_link_imported)
            }
        }
        action(R.string.desktop_link_read, staged != null && !attempted && remaining <= 0) {
            val reference = staged ?: return@action
            attempted = true
            status = getString(R.string.desktop_link_busy)
            execute({ api.testMobileConnection(reference) }) { profile ->
                SessionVault(this).link(reference, profile)
                gate.succeeded()
                getString(R.string.desktop_link_success)
            }
        }
        action(R.string.desktop_link_close, allowDuringWork = true) { close() }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.rgb(16, 19, 27)); isFillViewport = true
            setOnApplyWindowInsetsListener { view, insets ->
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                insets
            }; addView(page)
        })
        if (remaining > 0 && !attempted) main.postDelayed(gateWake, minOf(remaining, 60000))
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
                    }, onFailure = { failure -> (failure as? ApiFailure)?.userMessage() ?: getString(R.string.desktop_link_error) })
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
