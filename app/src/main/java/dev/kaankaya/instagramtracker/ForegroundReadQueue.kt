package dev.kaankaya.instagramtracker

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.TextView

/** One explicit, cancellable user action. Never resumes a restriction or survives leaving its screen. */
class ForegroundReadQueue(private val context: Context, private val gate: RequestGate, private val stopped: (String?) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var action: (() -> Unit)? = null
    private var started = 0L
    private var label: TextView? = null
    val pending get() = action != null
    private fun held() = gate.restrictionPending() || gate.requiresConnection() || gate.automationHeld() || gate.manualOnly()
    fun canWait(now: Long = System.currentTimeMillis()) = !held() && gate.nextAllowedAt() - now in 1..15_000L
    fun enqueue(action: () -> Unit): Boolean {
        if (pending) return true
        if (!canWait()) return false
        this.action = action; started = android.os.SystemClock.elapsedRealtime(); tick(); return true
    }
    private val wake = Runnable { tick() }
    private fun tick() {
        val run = action ?: return
        val now = System.currentTimeMillis()
        if (held() || android.os.SystemClock.elapsedRealtime() - started >= 30_000) { cancel(false); stopped(context.getString(R.string.ui_the_queued_action_was_stopped_check_the)); return }
        val wait = gate.nextAllowedAt() - now
        if (wait <= 0) { cancel(false); run(); return }
        label?.text = countdown(wait)
        main.removeCallbacks(wake); main.postDelayed(wake, minOf(1000L, wait))
    }
    fun view(context: Context): LinearLayout = AppUi.card(context).apply {
        addView(AppUi.text(context, context.getString(R.string.ui_your_action_is_queued), 15f, AppUi.violet, true))
        val text = AppUi.text(context, countdown((gate.nextAllowedAt() - System.currentTimeMillis()).coerceAtLeast(0)), 13f, AppUi.muted)
        label = text; addView(text)
        addView(AppUi.button(context, context.getString(R.string.ui_cancel_queued_action), false) { cancel(); })
    }
    private fun countdown(wait: Long): String {
        val seconds = (wait + 999) / 1000
        return context.resources.getQuantityString(R.plurals.ui_starting_in_seconds, seconds.toInt(), seconds)
    }
    fun cancel(notify: Boolean = true) {
        val wasPending = pending; action = null; label = null; main.removeCallbacks(wake)
        if (notify && wasPending) stopped(context.getString(R.string.ui_the_queued_action_was_cancelled))
    }
}
