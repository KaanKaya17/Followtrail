package dev.kaankaya.instagramtracker

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

object MonitorScheduler {
    const val JOB_ID = 1401
    fun cancel(context: Context, jobId: Int = JOB_ID) { context.getSystemService(JobScheduler::class.java).cancel(jobId) }
    fun sync(context: Context, account: SavedAccount?, gate: RequestGate, jobId: Int = JOB_ID,
        service: Class<out JobService> = MonitorJobService::class.java): Boolean {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        val multiple=jobId==JOB_ID && service==MonitorJobService::class.java
        val available=if(multiple)AccountMonitor(context,gate).hasWork(ChangeNotifications(context).allowed()) else
            (account?.people?.any {it.watching}==true || account?.automation?.enabled==true)
        if (!available ||
            !multiple && account?.automation?.enabled != true && !ChangeNotifications(context).allowed() || gate.quickWatchActive() || gate.automationHeld() || gate.restrictionPending() ||
            !multiple && (gate.manualOnly() || gate.requiresConnection())) {
            scheduler.cancel(jobId); return false
        }
        if (scheduler.getPendingJob(jobId) != null) return true
        return scheduler.schedule(JobInfo.Builder(jobId, ComponentName(context, service))
            .setPeriodic(MonitorPolicy.POLL_MS).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).build()) == JobScheduler.RESULT_SUCCESS
    }
}

open class MonitorJobService : JobService() {
    protected open val vaultSlot = "account"
    protected open val gateName = "request_gate"
    protected open val jobId = MonitorScheduler.JOB_ID
    protected open val preview = false
    protected open fun createApi(gate: RequestGate) = InstagramApi(gate)
    protected open fun createListSource(gate: RequestGate): FollowListSource = AccountFollowListReader(gate)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private class Run(val parameters: JobParameters, @Volatile var api: InstagramApi, @Volatile var lists: FollowListSource) {
        val cancelled = AtomicBoolean(false)
        var future: Future<*>? = null
        fun cancel() { cancelled.set(true); api.cancel(); lists.cancel(); future?.cancel(true) }
    }
    private var active: Run? = null
    override fun onStartJob(params: JobParameters): Boolean {
        if(!preview && vaultSlot=="account")try {AccountCatalog(this).initialize()}
        catch (_: Exception) {MonitorScheduler.cancel(this,jobId);return false}
        val notices = ChangeNotifications(this, preview)
        val gate = RequestGate(this, gateName)
        val listWork = try { SessionVault(this, vaultSlot).read()?.automation?.enabled == true } catch (_: Exception) { false }
        val multiple=!preview && vaultSlot=="account" && gateName=="request_gate"
        val available=try {!multiple || AccountMonitor(this,gate).hasWork(notices.allowed())}
            catch (_: Exception) {MonitorScheduler.cancel(this,jobId);return false}
        if (!available ||
            !multiple && (!notices.allowed() && !listWork || gate.manualOnly() || gate.requiresConnection()) || gate.quickWatchActive() || gate.automationHeld() || gate.restrictionPending()) { MonitorScheduler.cancel(this, jobId); return false }
        // Foreground owns the current request; retain the periodic job so a
        // later Android activation can resume after the activity leaves.
        if (gate.preparationActive()) return false
        active?.cancel()
        val run = Run(params, createApi(gate), createListSource(gate)); active = run
        run.future = worker.submit {
            val vault = SessionVault(this, vaultSlot)
            try {
                if(multiple)AccountMonitor(this,gate).cycle(run.cancelled::get) {api,lists->run.api=api;run.lists=lists;if(run.cancelled.get()){api.cancel();lists.cancel()}}
                else AutomaticEngine(vault, FollowListVault(this, if (preview) "previewlists" else "followlists"), gate,
                    run.api, run.lists, { listWork || notices.allowed() }, notices::post, notices::postList).cycle(cancelled = run.cancelled::get)
            }
            catch (_: ApiFailure) { /* The gate preserves restrictions and auth pauses; no immediate retries. */ }
            catch (_: Exception) { gate.pauseForConnection() }
            finally {
                main.post {
                    if (active === run) {
                        active = null
                        val account = try { vault.read() } catch (_: Exception) { null }
                        try {MonitorScheduler.sync(this, account, gate, jobId, javaClass)}
                        catch (_: Exception) {MonitorScheduler.cancel(this,jobId)}
                        jobFinished(params, false)
                    }
                }
            }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean {
        active?.takeIf { it.parameters.jobId == params.jobId }?.cancel(); active = null
        return false
    }
    override fun onDestroy() {
        active?.cancel(); active = null; main.removeCallbacksAndMessages(null); worker.shutdownNow()
        super.onDestroy()
    }
}
