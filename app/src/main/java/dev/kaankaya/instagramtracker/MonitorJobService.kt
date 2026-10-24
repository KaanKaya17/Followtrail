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
        if (account?.people?.any { it.watching } != true || !ChangeNotifications(context).allowed() || gate.prototypeActive() || gate.manualOnly() || gate.requiresConnection()) {
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
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private class Run(val parameters: JobParameters, val api: InstagramApi) {
        val cancelled = AtomicBoolean(false)
        var future: Future<*>? = null
        fun cancel() { cancelled.set(true); api.cancel(); future?.cancel(true) }
    }
    private var active: Run? = null
    override fun onStartJob(params: JobParameters): Boolean {
        val notices = ChangeNotifications(this, preview)
        val gate = RequestGate(this, gateName)
        if (!notices.allowed() || gate.prototypeActive() || gate.manualOnly() || gate.requiresConnection()) { MonitorScheduler.cancel(this, jobId); return false }
        active?.cancel()
        val run = Run(params, createApi(gate)); active = run
        run.future = worker.submit {
            val vault = SessionVault(this, vaultSlot)
            try { MonitorEngine(vault, gate, run.api, notices::allowed, notices::post).tick(cancelled = run.cancelled::get) }
            catch (_: ApiFailure) { /* The gate preserves restrictions and auth pauses; no immediate retries. */ }
            catch (_: Exception) { gate.pauseForConnection() }
            finally {
                main.post {
                    if (active === run) {
                        active = null
                        val account = try { vault.read() } catch (_: Exception) { null }
                        MonitorScheduler.sync(this, account, gate, jobId, javaClass)
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
