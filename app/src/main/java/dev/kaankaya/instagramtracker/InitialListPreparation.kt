package dev.kaankaya.instagramtracker

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import java.util.concurrent.Executors

object InitialLists {
    fun selected(account: SavedAccount) = account.automation.enabled && account.session.userId in account.automation.targets
    fun ready(target: ListTarget?, direction: FollowDirection) = target?.snapshot?.run(direction)?.comparable() == true
    fun pending(target: ListTarget?) = FollowDirection.entries.filter { !ready(target, it) }
}

/** Visible preparation owns one transport, timer and lease; encrypted pages outlive it. */
class InitialListPreparation(private val context: Context, private val vault: SessionVault,
    private val lists: FollowListVault, private val gate: RequestGate,
    private val factory: () -> FollowListSource, private val allowed: () -> Boolean,
    private val changed: () -> Unit,
    private val notify: (SavedAccount, ListDifference) -> Unit = { _, _ -> }) : AutoCloseable {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var generation = 0
    @Volatile private var source: FollowListSource? = null
    private var visible = false
    private var closed = false
    var busy = false; private set
    var failure: ApiFailure.Kind? = null; private set
    private var label: TextView? = null
    private val wake = Runnable { pump() }
    fun bind(view: TextView) { label = view }
    fun resume() { if (!closed) { visible = true; main.removeCallbacks(wake); main.post(wake) } }
    fun pause() {
        visible = false; generation++; main.removeCallbacks(wake); label = null
        try { source?.cancel() } catch (_: Exception) { }
    }
    private fun pump() {
        if (!visible || closed || busy || failure != null || !allowed()) return
        try {
            val account = vault.read() ?: return
            if (!account.automation.enabled || gate.automationHeld() || gate.manualOnly() || gate.requiresConnection() || gate.quickWatchActive()) return
            val session = account.session
            val records = lists.archive(session).targets.associateBy { it.target }
            val ids = account.automation.initialTargets + account.automation.targets
            val profiles = (listOf(account.profile) + account.people.map { it.latest }).filter { it.userId in ids }
            val paused = gate.forAccount(session.userId).pausedReads()
            val now = System.currentTimeMillis()
            var nextAt: Long? = null
            val jobs = profiles.flatMap { profile ->
                val target = records[profile.userId] ?: ListTarget(profile.userId)
                FollowDirection.entries.mapNotNull { direction ->
                    if (ReadKind.list(direction) in paused || ListAutomationPolicy.source(target, direction)?.comparable() == true ||
                        target.scan(direction).failure == ApiFailure.Kind.CAPACITY || ListAutomationPolicy.coverageHeld(target, direction))
                        return@mapNotNull null
                    val state = target.scan(direction)
                    if (state.nextAt > now) nextAt = minOf(nextAt ?: state.nextAt, state.nextAt)
                    val continuation = target.snapshot?.run(direction)?.takeIf { state.continuing && state.failure == null }
                    AutomaticQueue.page(target, direction, now, continuation, ListBasis.of(profile, direction))
                }
            }
            // Every returned job is due; finish a list already in progress before starting the next one.
            val job = jobs.minWithOrNull(compareBy({ if (records[it.target]?.scan(it.direction)?.continuing == true) 0 else 1 },
                { it.dueAt }, { records[it.target]?.scan(it.direction)?.attemptAt ?: 0L }))
            if (job == null) {
                nextAt?.let { main.postDelayed(wake, it - now) }
                return
            }
            val wait = gate.nextAllowedAt() - now
            if (wait > 0) {
                val text = if (wait <= 15000) context.getString(R.string.preparation_seconds, (wait + 999) / 1000)
                    else context.getString(R.string.preparation_waiting)
                if (label?.text?.toString() != text) label?.text = text
                main.postDelayed(wake, if (wait <= 15000) minOf(wait, 1000) else wait)
                return
            }
            val previous = records[job.target]?.snapshot?.run(job.direction)
            val ticket = generation
            busy = true; changed()
            worker.execute {
                var failure: ApiFailure? = null
                var lease: AutoCloseable? = null
                try {
                    lease = gate.claimPreparation()
                    val reader = factory(); source = reader
                    val cancelled = { ticket != generation || Thread.currentThread().isInterrupted }
                    if (cancelled()) throw ApiFailure(ApiFailure.Kind.CANCELLED)
                    ForegroundListScan(vault, lists, gate, reader).read(session, job.target, job.direction, job.cursor,
                        pages = 1, cancelled = cancelled, progress = {}, notify = notify)
                } catch (error: ApiFailure) {
                    failure = error
                    if (ticket == generation && error.kind !in setOf(ApiFailure.Kind.CANCELLED, ApiFailure.Kind.TOO_SOON, ApiFailure.Kind.BUDGET)) {
                        try {
                            vault.inspect { current ->
                                if (current?.session == session && current.automation.enabled &&
                                    (current.profile.userId == job.target || current.people.any { it.latest.userId == job.target })) {
                                    lists.deferCurrent(session, job.target, job.direction, previous, error.kind,
                                        System.currentTimeMillis(), error.http)
                                }
                            }
                            if (error.kind == ApiFailure.Kind.CAPACITY) gate.pauseForListCapacity()
                        } catch (_: Exception) { failure = ApiFailure(ApiFailure.Kind.STORAGE) }
                    }
                } catch (_: Exception) { failure = ApiFailure(ApiFailure.Kind.STORAGE) }
                finally {
                    source = null; lease?.close()
                    main.post {
                        busy = false
                        if (failure?.kind == ApiFailure.Kind.STORAGE) this.failure = ApiFailure.Kind.STORAGE
                        if (!closed && visible) {
                            changed()
                            if (failure?.kind != ApiFailure.Kind.CANCELLED) {
                                main.removeCallbacks(wake)
                                main.postDelayed(wake, if (failure == null) 1 else 1000)
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) { failure = ApiFailure.Kind.STORAGE; changed() }
    }
    override fun close() { closed = true; pause(); worker.shutdownNow() }
}
