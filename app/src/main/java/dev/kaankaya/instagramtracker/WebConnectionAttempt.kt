package dev.kaankaya.instagramtracker

import android.content.Context
import okhttp3.Call

/** A verified web connection is committed once; cancellation preserves the previous account. */
class WebConnectionAttempt(context: Context, private val gate: RequestGate,
    private val sessions: WebSessions = WebSessions(context), private val vault: SessionVault = SessionVault(context),
    client: Call.Factory = WebRequestClients.create(gate.context)) {
    private val reader = WebReadTransport(gate, sessions, client)
    private val lock = Any()
    @Volatile private var cancelled = false
    fun cancel() { synchronized(lock) { cancelled = true; reader.cancel() } }
    private fun current() {
        if (cancelled || Thread.currentThread().isInterrupted) throw ApiFailure(ApiFailure.Kind.CANCELLED)
    }
    fun connect(state: WebSession, expected: Session?, proof: BrowserConnectionProof? = null): SavedAccount {
        var staged: Session? = null
        try {
            current()
            proof?.consume(gate,state)
            val reference = synchronized(AccountCatalog.lock) {
                current()
                if (sessions.pending() != null) throw ApiFailure(ApiFailure.Kind.POLICY)
                sessions.stage(state).also { staged = it }
            }
            return verify(reference, expected, proof == null)
        } catch (failure: ApiFailure) {
            if (!recoverable(failure.kind)) staged?.let(::discard)
            throw failure
        }
        catch (_: Exception) { current(); throw ApiFailure(ApiFailure.Kind.STORAGE) }
    }
    /** Borrow the existing encrypted pending session; never repeat the password/code exchange. */
    fun resume(reference: Session, expected: Session?): SavedAccount {
        try { return verify(reference, expected, true) }
        catch (failure: ApiFailure) {
            if (!recoverable(failure.kind)) discard(reference)
            throw failure
        } catch (_: Exception) { current(); throw ApiFailure(ApiFailure.Kind.STORAGE) }
    }
    private fun verify(reference: Session, expected: Session?, connectionTest: Boolean): SavedAccount {
        synchronized(AccountCatalog.lock) {
            current()
            if (sessions.pending() != reference) throw ApiFailure(ApiFailure.Kind.CANCELLED)
        }
        val profile = reader.read(reference, connectionTest = connectionTest, resumeAutomation = false,
            request = { fresh, _ -> WebRequests.profile(fresh, reference.userId) }) { body, time ->
            ProfileParser.fromBody(body, reference.userId, time)
        }
        synchronized(lock) {
            current()
            synchronized(AccountCatalog.lock) {
                if (sessions.pending() != reference) throw ApiFailure(ApiFailure.Kind.CANCELLED)
                if (gate.blockedUntil() > gate.now()) throw ApiFailure(ApiFailure.Kind.LIMITED)
                val linked = vault.linkIfUnchanged(expected, reference, profile) { cancelled || Thread.currentThread().isInterrupted }
                gate.forAccount(reference.userId).succeeded()
                // Once committed, cleanup only removes the pending pointer, never the linked bank.
                discard(reference)
                return linked
            }
        }
    }
    private fun discard(reference: Session) { try { sessions.discardPending(reference) } catch (_: Exception) { /* Store retains cleanup ownership. */ } }
    private fun recoverable(kind: ApiFailure.Kind) = kind in setOf(ApiFailure.Kind.LIMITED,
        ApiFailure.Kind.TOO_SOON, ApiFailure.Kind.BUDGET, ApiFailure.Kind.CONNECTION, ApiFailure.Kind.STORAGE,
        ApiFailure.Kind.CANCELLED, ApiFailure.Kind.CAPACITY)
}
