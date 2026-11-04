package dev.kaankaya.instagramtracker

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.Executors

/** A pending login is not an account. Only an explicit, gated profile read can commit it. */
class WebPendingConnectionActivity : LocalizedActivity() {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var gate: RequestGate
    private lateinit var sessions: WebSessions
    private var attempt: WebConnectionAttempt? = null
    private var busy = false
    private var generation = 0
    private var status = ""
    private val wake = Runnable { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppUi.systemBars(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        gate = RequestGate(this); sessions = WebSessions(this)
        if (android.os.Build.VERSION.SDK_INT >= 33)
            onBackInvokedDispatcher.registerOnBackInvokedCallback(0) { finish() }
    }
    override fun onResume() {
        super.onResume()
        if (recreatingForInterface) return
        try { gate.pauseAutomatic(); gate.setPrototypeActive(true); MonitorScheduler.cancel(this) }
        catch (_: Exception) { status = ApiFailure(ApiFailure.Kind.STORAGE).userMessage(this) }
        render()
    }
    private fun render() {
        if (isDestroyed || isFinishing) return
        main.removeCallbacks(wake)
        val reference = try { sessions.pending() } catch (_: Exception) {
            status = ApiFailure(ApiFailure.Kind.STORAGE).userMessage(this); null
        }
        val next = try { gate.nextPrototypeAt() } catch (_: Exception) {
            status = ApiFailure(ApiFailure.Kind.STORAGE).userMessage(this); Long.MAX_VALUE
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding); setBackgroundColor(AppUi.background)
        }
        fun label(value: String, size: Float = 16f) {
            page.addView(AppUi.text(this, value, size, AppUi.ink).apply { setPadding(0, 12, 0, 24) })
        }
        fun action(resource: Int, enabled: Boolean = true, primary: Boolean = true, run: () -> Unit) {
            page.addView(AppUi.button(this, getString(resource), primary, enabled && !busy, action = run),
                LinearLayout.LayoutParams(-1, -2))
        }
        label(getString(R.string.web_pending_title), 28f)
        label(getString(if (reference == null) R.string.web_pending_missing else R.string.web_pending_body))
        if (busy) label(getString(R.string.connection_checking))
        else if (status.isNotEmpty()) label(status)
        if (reference != null) {
            if (next > gate.now() && next != Long.MAX_VALUE) label(getString(R.string.desktop_link_pause,
                SimpleDateFormat("d MMM · HH:mm", AppLanguage.locale(this)).format(Date(next))))
            action(R.string.web_pending_verify, next <= gate.now()) { connect(reference) }
            action(R.string.web_pending_forget, primary = false) {
                AlertDialog.Builder(this).setTitle(R.string.web_pending_forget)
                    .setMessage(R.string.web_pending_forget_body)
                    .setPositiveButton(R.string.web_pending_forget) { _, _ ->
                        try { sessions.discardPending(reference); finish() }
                        catch (_: Exception) { status = ApiFailure(ApiFailure.Kind.STORAGE).userMessage(this); render() }
                    }.setNegativeButton(R.string.ui_cancel, null).show()
            }
            if (!busy && next > gate.now() && next != Long.MAX_VALUE)
                main.postDelayed(wake, (next - gate.now()).coerceAtLeast(1))
        }
        action(R.string.ui_request_information, primary = false) { requestInfo() }
        action(R.string.ui_close, primary = false) { finish() }
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(page) })
    }
    private fun requestInfo() {
        fun date(value: Long) = SimpleDateFormat("d MMM · HH:mm:ss", AppLanguage.locale(this)).format(Date(value))
        val lines = mutableListOf(
            getString(R.string.ui_read_attempts_started, java.text.NumberFormat.getIntegerInstance(AppLanguage.locale(this)).format(gate.attempts())),
            getString(R.string.web_compare_last_counted_http, gate.lastHttpStatus()?.toString() ?: getString(R.string.ui_no_response_yet)),
            getString(R.string.web_transport_selected, getString(if (WebRequestClients.platformAvailable()) R.string.web_transport_android else R.string.web_transport_standard)),
            getString(R.string.web_transport_app_id, WebRequests.APP_ID))
        if (gate.lastAttemptAt() > 0) lines += getString(R.string.ui_last_attempt, date(gate.lastAttemptAt()))
        if (gate.restrictionPending()) {
            lines += getString(R.string.ui_signal, gate.limitReasonMessage(this))
            lines += getString(R.string.ui_last_limit, date(gate.lastLimitedAt()))
            lines += getString(R.string.ui_server_wait_information, gate.serverRetryAt()?.let(::date) ?: getString(R.string.ui_not_specified))
            lines += if (gate.blockedUntil() > gate.now()) getString(R.string.ui_app_cooldown_until, date(gate.blockedUntil()))
                else getString(R.string.ui_the_local_cooldown_has_ended)
        }
        AlertDialog.Builder(this).setTitle(R.string.ui_request_information)
            .setMessage(lines.joinToString("\n\n")).setPositiveButton(R.string.ui_ok, null).show()
    }
    private fun connect(reference: Session) {
        if (busy) return
        val expected = try { SessionVault(this).read()?.session } catch (_: Exception) {
            status = ApiFailure(ApiFailure.Kind.STORAGE).userMessage(this); render(); return
        }
        val connection = WebConnectionAttempt(this, gate)
        attempt = connection; busy = true; val ticket = ++generation; render()
        worker.execute {
            val result = try { Result.success(connection.resume(reference, expected)) }
                catch (failure: ApiFailure) { Result.failure(failure) }
                catch (_: Exception) { Result.failure(ApiFailure(ApiFailure.Kind.STORAGE)) }
            main.post {
                if (isDestroyed || isFinishing || ticket != generation) return@post
                attempt = null; busy = false
                result.onSuccess {
                    android.widget.Toast.makeText(this, R.string.desktop_link_success, android.widget.Toast.LENGTH_SHORT).show()
                    finish()
                }.onFailure { status = (it as ApiFailure).userMessage(this); render() }
            }
        }
    }
    override fun onStop() {
        ++generation; attempt?.cancel(); attempt = null; busy = false
        main.removeCallbacks(wake)
        try { gate.setPrototypeActive(false) } catch (_: Exception) { }
        super.onStop()
    }
    override fun onDestroy() {
        attempt?.cancel(); main.removeCallbacksAndMessages(null); worker.shutdownNow()
        super.onDestroy()
    }
}
