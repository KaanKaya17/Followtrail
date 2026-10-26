package dev.kaankaya.instagramtracker

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.TextView

/** One explicit, cancellable user action. Never resumes a restriction or survives leaving its screen. */
class ForegroundReadQueue(private val gate: RequestGate, private val stopped: (String?) -> Unit) {
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
        if (held() || android.os.SystemClock.elapsedRealtime() - started >= 30_000) { cancel(false); stopped("Bekleyen işlem durduruldu. Mevcut mola ve bağlantı durumunu kontrol edebilirsin."); return }
        val wait = gate.nextAllowedAt() - now
        if (wait <= 0) { cancel(false); run(); return }
        label?.text = "${(wait + 999) / 1000} saniye sonra başlayacak"
        main.removeCallbacks(wake); main.postDelayed(wake, minOf(1000L, wait))
    }
    fun view(context: Context): LinearLayout = AppUi.card(context).apply {
        addView(AppUi.text(context, "İşlemin sırada", 15f, AppUi.violet, true))
        val text = AppUi.text(context, "${((gate.nextAllowedAt() - System.currentTimeMillis()).coerceAtLeast(0) + 999) / 1000} saniye sonra başlayacak", 13f, AppUi.muted)
        label = text; addView(text)
        addView(AppUi.button(context, "Bekleyen işlemi iptal et", false) { cancel(); })
    }
    fun cancel(notify: Boolean = true) {
        val wasPending = pending; action = null; label = null; main.removeCallbacks(wake)
        if (notify && wasPending) stopped("Bekleyen işlem iptal edildi.")
    }
}
