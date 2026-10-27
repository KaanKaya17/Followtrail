package dev.kaankaya.instagramtracker

import android.content.Context
import okhttp3.Call

/** A verified web connection is committed once; cancellation preserves the previous account. */
class WebConnectionAttempt(context: Context, private val gate: RequestGate,
    private val sessions: WebSessions = WebSessions(context), private val vault: SessionVault = SessionVault(context),
    client: Call.Factory = PrivateReadTransport.client()) {
    private val reader = WebReadTransport(gate, sessions, client)
    private val lock = Any()
    @Volatile private var cancelled = false
    fun cancel() { synchronized(lock) { cancelled = true; reader.cancel() } }
    private fun current() {
        if (cancelled || Thread.currentThread().isInterrupted) throw ApiFailure(ApiFailure.Kind.CANCELLED)
    }
    fun connect(state: WebSession, expected: Session?): SavedAccount {
        var staged: Session? = null
        try {
            current()
            sessions.discardPending()
            current(); val reference = sessions.stage(state); staged = reference
            current()
            val profile = reader.read(reference, connectionTest = true,
                request = { fresh, _ -> WebRequests.profile(fresh, reference.userId) }) { body, time ->
                ProfileParser.fromBody(body, reference.userId, time)
            }
            synchronized(lock) {
                current()
                if (gate.blockedUntil() > gate.now()) throw ApiFailure(ApiFailure.Kind.LIMITED)
                val linked = vault.linkIfUnchanged(expected, reference, profile) { cancelled || Thread.currentThread().isInterrupted }
                gate.succeeded()
                return linked
            }
        } catch (failure: ApiFailure) { throw failure }
        catch (_: Exception) { current(); throw ApiFailure(ApiFailure.Kind.STORAGE) }
        finally {
            staged?.let { reference ->
                // Failure leaves an encrypted pending record owned by WebSessions for retry/cleanup.
                try { sessions.discardPending(reference) } catch (_: Exception) { }
            }
        }
    }
}
