package dev.kaankaya.instagramtracker

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** One mobile profile request; no web fallback, redirects, login, or automatic request replay. */
class MobileProfileReader(private val gate: RequestGate, private val sessions: MobileSessions,
    private val client: Call.Factory = client()) {
    private class AttemptBudget { val sent = AtomicBoolean(false) }
    companion object {
        fun client(): OkHttpClient = OkHttpClient.Builder().retryOnConnectionFailure(false).fastFallback(false)
            .followRedirects(false).followSslRedirects(false).connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().tag(AttemptBudget::class.java, AttemptBudget()).build())
            }
            .addNetworkInterceptor { chain ->
                // Reject HTTP/1 before transmitting the authenticated request.
                if (chain.connection()?.protocol() != Protocol.HTTP_2) throw IOException("HTTP/2 required")
                // Also stop status-driven follow-ups (e.g. 503 Retry-After: 0), not just connection retries.
                if (chain.request().tag(AttemptBudget::class.java)?.sent?.compareAndSet(false, true) != true)
                    throw IOException("Request replay disabled")
                chain.proceed(chain.request())
            }.build()
        fun request(state: MobileSession, userId: String?, username: String?, now: Long): Request {
            require((userId == null) != (username == null))
            if (userId != null) require(userId.matches(Regex("[1-9][0-9]{0,29}")))
            if (username != null) require(ProfileInput.validUsername(username))
            val path = if (userId != null) "users/$userId/info/" else "users/$username/usernameinfo/"
            val address = "https://i.instagram.com/api/v1/$path".toHttpUrl().newBuilder()
            if (userId == state.owner) address.addQueryParameter("is_prefetch", "false")
                .addQueryParameter("entry_point", "self_profile").addQueryParameter("from_module", "self_profile")
                .addQueryParameter("is_app_start", "False")
            val request = Request.Builder().url(address.build()).get().header("Accept", "application/json")
            state.requestHeaders(now).forEach { (name, value) -> request.header(name, value) }
            return request.build()
        }
    }
    private val lock = Any()
    private var generation = 0L
    private var active: Call? = null
    fun cancel() { synchronized(lock) { generation++; active?.cancel(); active = null } }
    fun read(reference: Session, userId: String?, username: String?, connectionTest: Boolean = false): Profile {
        val ticket = synchronized(lock) { generation }
        fun current() {
            if (Thread.currentThread().isInterrupted || synchronized(lock) { generation != ticket })
                throw ApiFailure(ApiFailure.Kind.CANCELLED)
        }
        var call: Call? = null
        var lease: AutoCloseable? = null
        try {
            current(); lease = gate.claim()
            if (connectionTest) gate.beginPrototype() else gate.begin()
            val state = sessions.read(reference)
            call = client.newCall(request(state, userId, username, System.currentTimeMillis()))
            synchronized(lock) { current(); active = call }
            call.execute().use { response ->
                current()
                if (response.code == 429) {
                    gate.limited(response.header("Retry-After"), reason = "Mobil API · HTTP 429")
                    throw ApiFailure(ApiFailure.Kind.LIMITED)
                }
                if (response.isRedirect || response.code == 401) throw ApiFailure(ApiFailure.Kind.SESSION_EXPIRED)
                val body = response.body ?: throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
                if (body.contentLength() > 1024 * 1024) throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
                val source = body.source()
                source.request(1024 * 1024 + 1L)
                if (source.buffer.size > 1024 * 1024) throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
                val text = source.readUtf8()
                val json = try { JSONObject(text) } catch (_: Exception) { null }
                when (json?.optString("message")) {
                    "login_required" -> throw ApiFailure(ApiFailure.Kind.SESSION_EXPIRED)
                    "challenge_required", "checkpoint_required", "consent_required" -> throw ApiFailure(ApiFailure.Kind.VERIFICATION)
                    "feedback_required", "please_wait_a_few_minutes", "Please wait a few minutes before you try again." -> {
                        gate.limited(response.header("Retry-After"), reason = "Mobil API · bekleme yanıtı")
                        throw ApiFailure(ApiFailure.Kind.LIMITED)
                    }
                }
                if (response.code == 403) throw ApiFailure(ApiFailure.Kind.VERIFICATION)
                if (response.code == 404) throw ApiFailure(ApiFailure.Kind.NOT_FOUND)
                if (!response.isSuccessful) throw ApiFailure(ApiFailure.Kind.CONNECTION)
                val profile = if (userId != null) ProfileParser.parse(text, userId, System.currentTimeMillis())
                    else ProfileParser.user(json?.getJSONObject("user") ?: throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE), System.currentTimeMillis()).also {
                        if (json.optString("status") != "ok" || !it.username.equals(username, true)) throw ApiFailure(ApiFailure.Kind.WRONG_ACCOUNT)
                    }
                val updated = try { state.updated(response.headers.toMultimap()) } catch (_: Exception) { throw ApiFailure(ApiFailure.Kind.WRONG_ACCOUNT) }
                synchronized(lock) {
                    current()
                    try { sessions.save(reference, updated) } catch (failure: ApiFailure) { throw failure }
                    catch (_: Exception) { throw ApiFailure(ApiFailure.Kind.STORAGE) }
                    // A setup read cannot resume an older adapter before the UI commits the new binding.
                    if (!connectionTest) gate.succeeded()
                }
                return profile
            }
        } catch (failure: ApiFailure) {
            if (failure.kind in setOf(ApiFailure.Kind.SESSION_EXPIRED, ApiFailure.Kind.VERIFICATION, ApiFailure.Kind.WRONG_ACCOUNT)) gate.pauseForConnection()
            throw failure
        } catch (_: IOException) { current(); throw ApiFailure(ApiFailure.Kind.CONNECTION) }
        catch (_: Exception) { current(); throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE) }
        finally {
            synchronized(lock) { if (active === call) active = null }
            lease?.close()
        }
    }
}
