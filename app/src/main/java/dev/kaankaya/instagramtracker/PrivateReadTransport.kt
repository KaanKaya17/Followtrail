package dev.kaankaya.instagramtracker

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** One bounded exchange owner for both credential formats. No gateway, redirect or replay. */
class PrivateReadTransport<S>(private val gate: RequestGate, private val sessions: ReadSessionStore<S>,
    private val client: Call.Factory, private val hosts: Set<String>,
    private val rotate: (S, Map<String, List<String>>, Long) -> S) {
    private class AttemptBudget { val sent = AtomicBoolean(false) }
    companion object {
        fun client(): OkHttpClient = OkHttpClient.Builder().retryOnConnectionFailure(false).fastFallback(false)
            .followRedirects(false).followSslRedirects(false).connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().tag(AttemptBudget::class.java, AttemptBudget()).build())
            }.addNetworkInterceptor { chain ->
                if (chain.connection()?.protocol() != Protocol.HTTP_2) throw IOException("HTTP/2 required")
                if (chain.request().tag(AttemptBudget::class.java)?.sent?.compareAndSet(false, true) != true)
                    throw IOException("Request replay disabled")
                chain.proceed(chain.request())
            }.build()
    }
    private val lock = Any()
    private var generation = 0L
    private var active: Call? = null
    fun cancel() { synchronized(lock) { generation++; active?.cancel(); active = null } }
    fun <T> read(reference: Session, connectionTest: Boolean = false, resumeAutomation: Boolean = true,
        request: (S, Long) -> Request, parse: (JSONObject, Long) -> T): T {
        val ticket = synchronized(lock) { generation }
        fun current() {
            if (Thread.currentThread().isInterrupted || synchronized(lock) { generation != ticket })
                throw ApiFailure(ApiFailure.Kind.CANCELLED)
        }
        var call: Call? = null
        var lease: AutoCloseable? = null
        try {
            current(); lease = gate.claim()
            val state = sessions.read(reference)
            val prepared = request(state, gate.now())
            require(prepared.url.isHttps && prepared.url.host in hosts && prepared.url.port == 443 &&
                prepared.url.username.isEmpty() && prepared.url.password.isEmpty())
            if (!AccountRequestPolicy.readAllowed(prepared.method, prepared.url.toString(), prepared.body != null)) throw ApiFailure(ApiFailure.Kind.POLICY)
            if (connectionTest) gate.beginPrototype() else gate.begin()
            call = client.newCall(prepared)
            synchronized(lock) { current(); active = call }
            call.execute().use { response ->
                current(); gate.observedStatus(response.code)
                InstagramResponsePolicy.checkHeaders(gate, response.code, response.header("Retry-After"))
                val body = response.body ?: throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
                if (body.contentLength() > 1024 * 1024) throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
                val source = body.source(); source.request(1024 * 1024 + 1L)
                if (source.buffer.size > 1024 * 1024) throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
                val json = try { JSONObject(source.readUtf8()) } catch (_: Exception) { null }
                InstagramResponsePolicy.checkBody(gate, response.code, json, response.header("Retry-After"))
                val result = parse(json ?: throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE), gate.now())
                val updated = try { rotate(state, response.headers.toMultimap(), gate.now()) }
                    catch (failure: ApiFailure) { throw failure }
                    catch (_: Exception) { throw ApiFailure(ApiFailure.Kind.WRONG_ACCOUNT) }
                synchronized(lock) {
                    current()
                    try { sessions.save(reference, updated) } catch (failure: ApiFailure) { throw failure }
                    catch (_: Exception) { throw ApiFailure(ApiFailure.Kind.STORAGE) }
                    if (!connectionTest && resumeAutomation) gate.succeeded()
                }
                return result
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
