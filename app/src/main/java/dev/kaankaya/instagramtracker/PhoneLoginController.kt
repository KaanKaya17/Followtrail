package dev.kaankaya.instagramtracker

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import java.util.concurrent.Future

enum class PhoneLoginPhase { FORM, BUSY, METHOD, CODE, LINKED, ERROR }
data class PhoneLoginUi(val phase: PhoneLoginPhase = PhoneLoginPhase.FORM,
    val step: PhoneLoginStep = PhoneLoginStep.PREPARING, val waiting: Long = 0,
    val failure: ApiFailure.Kind? = null, val reason: PhoneLoginFailure.Reason? = null)

/** Retained across rotation; only process memory holds pending authentication contexts. */
class PhoneLoginController(context: Context,
    private val factory: (Context, RequestGate, (PhoneLoginStep, Long) -> Unit) -> PhoneLoginClient = { c, g, p -> PhoneLoginClient(c, g, progress = p) }) {
    private val context = context.applicationContext
    private val gate = RequestGate(this.context)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var generation = 0
    @Volatile private var client: PhoneLoginClient? = null
    private var pending: Future<*>? = null
    private var original: Session? = null
    var ui = PhoneLoginUi()
        private set
    var listener: (() -> Unit)? = null
    private fun publish(value: PhoneLoginUi) { ui = value; listener?.invoke() }
    fun start(contact: String, password: CharArray) {
        if (ui.phase == PhoneLoginPhase.BUSY) { password.fill('\u0000'); return }
        client?.cancel(); val ticket = ++generation
        publish(PhoneLoginUi(PhoneLoginPhase.BUSY))
        pending = worker.submit {
            try {
                original = try { SessionVault(context).read()?.session } catch (_: Exception) { throw ApiFailure(ApiFailure.Kind.STORAGE) }
                val fresh = factory(context, gate) { step, wait -> main.post {
                    if (ticket == generation && ui.phase == PhoneLoginPhase.BUSY) publish(ui.copy(step = step, waiting = wait))
                } }
                client = fresh
                if (ticket != generation || Thread.currentThread().isInterrupted) { fresh.cancel(); throw ApiFailure(ApiFailure.Kind.CANCELLED) }
                complete(ticket, fresh.login(contact, password))
            } catch (failure: Exception) { fail(ticket, failure) }
            finally { password.fill('\u0000') }
        }
    }
    fun chooseMethod(method: String) = operate { it.chooseMethod(method) }
    fun submitCode(code: String) = operate { it.submitCode(code) }
    private fun operate(operation: (PhoneLoginClient) -> PhoneLoginResult) {
        if (ui.phase !in setOf(PhoneLoginPhase.METHOD, PhoneLoginPhase.CODE)) return
        val current = client ?: return
        val ticket = generation; publish(PhoneLoginUi(PhoneLoginPhase.BUSY, PhoneLoginStep.VERIFYING))
        pending = worker.submit { try { complete(ticket, operation(current)) } catch (failure: Exception) { fail(ticket, failure) } }
    }
    private fun complete(ticket: Int, result: PhoneLoginResult) {
        if (ticket != generation || Thread.currentThread().isInterrupted) throw ApiFailure(ApiFailure.Kind.CANCELLED)
        val value = when (result) {
            PhoneLoginResult.Method -> PhoneLoginUi(PhoneLoginPhase.METHOD)
            PhoneLoginResult.Code -> PhoneLoginUi(PhoneLoginPhase.CODE)
            is PhoneLoginResult.Ready -> {
                if (result.profile.userId != result.session.owner) throw ApiFailure(ApiFailure.Kind.WRONG_ACCOUNT)
                val sessions = MobileSessions(context)
                val reference = try { sessions.stage(result.session) } catch (_: Exception) { throw ApiFailure(ApiFailure.Kind.STORAGE) }
                try {
                    SessionVault(context).linkIfUnchanged(original, reference, result.profile) {
                        ticket != generation || Thread.currentThread().isInterrupted
                    }
                } catch (failure: Exception) {
                    try { sessions.discardPending(reference) } catch (_: Exception) { /* Retain encrypted pending state for recovery. */ }
                    throw failure
                }
                gate.succeeded()
                PhoneLoginUi(PhoneLoginPhase.LINKED)
            }
        }
        main.post { if (ticket == generation) publish(value) }
    }
    private fun fail(ticket: Int, failure: Exception) {
        val reason = (failure as? PhoneLoginFailure)?.reason
        val kind = (failure as? ApiFailure)?.kind
        main.post {
            if (ticket == generation) publish(PhoneLoginUi(
                if (reason == PhoneLoginFailure.Reason.CODE) PhoneLoginPhase.CODE else PhoneLoginPhase.ERROR,
                step = ui.step, failure = kind ?: if (reason == null) ApiFailure.Kind.INVALID_RESPONSE else null, reason = reason))
        }
    }
    fun cancelWork() {
        if (ui.phase != PhoneLoginPhase.BUSY) return
        generation++; client?.cancel(); pending?.cancel(true)
        publish(PhoneLoginUi(PhoneLoginPhase.ERROR, failure = ApiFailure.Kind.CANCELLED))
    }
    fun close() {
        generation++; listener = null; client?.cancel(); client = null; pending?.cancel(true)
        worker.shutdownNow(); main.removeCallbacksAndMessages(null)
    }
}
