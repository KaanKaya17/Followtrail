package dev.kaankaya.instagramtracker

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import java.util.concurrent.Future

object QuickWatchTiming {
    const val INTERVAL_MS = 60_000L
    const val DURATION_MS = 15 * 60_000L
}

/** Activity-owned, visible-only session. No wake locks, background timer or automatic retry after an error. */
class QuickWatch(private val vault: SessionVault, private val lists: FollowListVault, private val gate: RequestGate,
    private val apiFactory: () -> InstagramApi, private val sourceFactory: () -> FollowListSource,
    private val notices: ChangeNotifications, private val changed: (ApiFailure?) -> Unit,
    private val runtime: ReadRuntime = AndroidReadRuntime) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var reference: Session? = null
    var target: String? = null
        private set
    private var started = 0L
    @Volatile private var until = 0L
    private var next = 0L
    private var visible = false
    @Volatile private var generation = 0
    private var lease: AutoCloseable? = null
    private var api: InstagramApi? = null
    private var source: FollowListSource? = null
    private var pending: Future<*>? = null
    var busy = false
        private set
    var label: android.widget.TextView? = null
    var status: (() -> String)? = null
    val active get() = target != null && runtime.elapsed() < until
    val secondsLeft get() = ((until - runtime.elapsed()).coerceAtLeast(0) + 999) / 1000
    val secondsToCheck get() = ((next - runtime.elapsed()).coerceAtLeast(0) + 999) / 1000
    val waitingForSlot get() = active && !busy && runtime.elapsed() >= next && gate.nextAllowedAt() > runtime.now()

    fun start(session: Session, target: String) {
        stop(false); reference = session; this.target = target
        started = runtime.elapsed(); until = started + QuickWatchTiming.DURATION_MS; next = started
        resume()
    }
    fun save(state: Bundle) {
        if (!active) return
        state.putString("quick_target", target); state.putString("quick_owner", reference?.userId)
        state.putString("quick_binding", reference?.mobileBinding)
        state.putLong("quick_started", started); state.putLong("quick_until", until); state.putLong("quick_next", next)
    }
    fun restore(state: Bundle?, saved: SavedAccount) {
        if (state == null || state.getString("quick_owner") != saved.session.userId || state.getString("quick_binding") != saved.session.mobileBinding) return
        val began = state.getLong("quick_started"); val end = state.getLong("quick_until"); val now = runtime.elapsed()
        val id = state.getString("quick_target") ?: return
        if (now < began || now >= end || end - began != QuickWatchTiming.DURATION_MS ||
            id !in saved.automation.targets || !saved.automation.enabled) return
        reference = saved.session; target = id; started = began; until = end
        next = state.getLong("quick_next").coerceAtLeast(now)
    }
    fun resume() {
        visible = true
        if (target != null && !active) { stop(); return }
        if (!active || lease != null) return
        try { lease = gate.claimQuickWatch() } catch (failure: ApiFailure) { stop(); changed(failure); return }
        main.post(tick)
    }
    fun pause() {
        visible = false; generation++; main.removeCallbacks(tick)
        api?.cancel(); source?.cancel(); pending?.cancel(true)
        api = null; source = null; pending = null; busy = false
        lease?.close(); lease = null; label = null
    }
    fun stop(notify: Boolean = true) {
        pause(); target = null; reference = null
        if (notify) changed(null)
    }
    private val tick = object : Runnable {
        override fun run() {
            if (!visible) return
            if (!active) { stop(); return }
            val session = reference ?: return
            if (!notices.allowed() || gate.manualOnly() || gate.requiresConnection() || gate.automationHeld()) {
                stop(false); changed(ApiFailure(if (gate.restrictionPending()) ApiFailure.Kind.LIMITED else ApiFailure.Kind.CANCELLED)); return
            }
            label?.text = status?.invoke()
            if (!busy && runtime.elapsed() >= next && gate.nextAllowedAt() <= runtime.now()) {
                val id = target ?: return
                val ticket = ++generation; busy = true
                // Reserve the next live attempt before transport, including cancellation and failures.
                next = runtime.elapsed() + QuickWatchTiming.INTERVAL_MS
                val transport = apiFactory(); val pages = sourceFactory(); api = transport; source = pages
                pending = worker.submit {
                    val failure = try {
                        AutomaticEngine(vault, lists, gate, transport, pages, notices::allowed, notices::post, notices::postList, runtime)
                            .quickCycle(session, id) { ticket != generation || Thread.currentThread().isInterrupted || runtime.elapsed() >= until }
                        null
                    } catch (error: ApiFailure) { error }
                    catch (_: Exception) { ApiFailure(ApiFailure.Kind.STORAGE) }
                    main.post {
                        if (!visible || ticket != generation) return@post
                        busy = false; api = null; source = null; pending = null
                        if (failure == null) {
                            val saved = try { vault.read() } catch (_: Exception) { null }
                            val profile = if (id == saved?.profile?.userId) saved.profile else saved?.people?.firstOrNull { it.latest.userId == id }?.latest
                            if (profile != null) next = maxOf(next, runtime.elapsed() +
                                (profile.observedAt + QuickWatchTiming.INTERVAL_MS - runtime.now()).coerceAtLeast(0))
                        }
                        if (failure != null && failure.kind != ApiFailure.Kind.TOO_SOON) stop(false)
                        changed(failure)
                    }
                }
            }
            main.postDelayed(this, 1000)
        }
    }
    fun close() { pause(); main.removeCallbacksAndMessages(null); worker.shutdownNow() }
}
