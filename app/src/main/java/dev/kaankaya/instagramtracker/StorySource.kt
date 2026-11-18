package dev.kaankaya.instagramtracker

import okhttp3.Call
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.Proxy
import java.util.concurrent.TimeUnit

interface StoryFeedSource {
    fun read(session: Session, target: String): List<StoryCandidate>
    fun cancel()
}

/** The session kind selects one fixed GET before sending. No fallback, seen action or social write. */
class AccountStorySource(gate: RequestGate,
    mobileClient: Call.Factory = MobileReadTransport.client(), webClient: Call.Factory = WebRequestClients.create(gate.context)) : StoryFeedSource {
    private val mobile = MobileReadTransport(gate, MobileSessions(gate.context), mobileClient)
    private val web = WebReadTransport(gate, WebSessions(gate.context), webClient)
    companion object {
        fun mobileRequest(state: MobileSession, target: String, now: Long): Request {
            InstagramIdentity.id(target)
            val url = "https://i.instagram.com/api/v1/feed/user/$target/story/".toHttpUrl().newBuilder()
                .addQueryParameter("supported_capabilities_new", StoryLimits.CAPABILITIES_JSON).build()
            return Request.Builder().url(url).get().header("Accept", "application/json").apply {
                state.requestHeaders(now).forEach { (name,value) -> header(name,value) }
            }.build()
        }
    }
    override fun read(session: Session, target: String): List<StoryCandidate> = when {
        session.mobileBinding != null -> mobile.read(session, resumeAutomation = false,
            request = { state, now -> mobileRequest(state,target,now) }) { body, _ -> StoryParser.parse(body,target) }
        session.webBinding != null -> web.read(session, resumeAutomation = false,
            request = { state, _ -> WebRequests.stories(state,target) }) { body, _ -> StoryParser.parse(body,target) }
        else -> throw ApiFailure(ApiFailure.Kind.MOBILE_REQUIRED)
    }
    override fun cancel() { mobile.cancel(); web.cancel() }
}

interface StoryMediaSource {
    fun read(session: Session, url: String): ByteArray
    fun cancel()
}

/** Credential-free CDN downloads use the paced media lane; they never spend the API queue or budget. A CDN 429 restricts both. */
class StoryCdnSource(private val gate: RequestGate, private val client: Call.Factory = sharedClient) : StoryMediaSource {
    companion object {
        private val sharedClient by lazy { OkHttpClient.Builder()
            .cookieJar(CookieJar.NO_COOKIES).proxy(Proxy.NO_PROXY).retryOnConnectionFailure(false).followRedirects(false)
            .followSslRedirects(false).connectTimeout(5,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS)
            .callTimeout(25,TimeUnit.SECONDS).build() }
    }
    private val lock = Any()
    private var generation = 0L
    private var active: Call? = null
    override fun cancel() = synchronized(lock) { generation++; active?.cancel(); active = null }
    override fun read(session: Session, url: String): ByteArray {
        val address = AvatarPolicy.url(url)?.takeIf { it.fragment == null } ?: throw ApiFailure(ApiFailure.Kind.POLICY)
        val limiter = gate.forAccount(session.userId)
        val ticket = synchronized(lock) { generation }
        fun current() {
            if (Thread.currentThread().isInterrupted || synchronized(lock) { generation != ticket }) throw ApiFailure(ApiFailure.Kind.CANCELLED)
        }
        var call: Call? = null
        try {
            limiter.claim().use {
                current(); limiter.beginMedia()
                val request = Request.Builder().url(address).get().header("Accept", "video/mp4,image/*")
                    .header("Accept-Encoding", "identity").build()
                call = client.newCall(request)
                synchronized(lock) { current(); active = call }
                call!!.execute().use { response ->
                    current(); limiter.observedStatus(response.code)
                    if (response.code == 429) { limiter.limited(response.header("Retry-After"), reason = "HTTP 429"); throw ApiFailure(ApiFailure.Kind.LIMITED) }
                    if (response.code != 200) throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
                    val body = response.body ?: throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
                    val length = body.contentLength()
                    if (length > StoryLimits.MAX_MEDIA) throw ApiFailure(ApiFailure.Kind.CAPACITY)
                    val bytes = body.byteStream().readBytesBounded(StoryLimits.MAX_MEDIA)
                    current()
                    if (bytes.isEmpty() || length >= 0 && bytes.size.toLong() != length) throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
                    return bytes
                }
            }
        } catch (_: java.io.IOException) { current(); throw ApiFailure(ApiFailure.Kind.CONNECTION) }
        finally { synchronized(lock) { if (active === call) active = null } }
    }
}

/** Explicit foreground operation. Commit each item; account/profile erasure cannot be undone by a late response. */
class StoryFetch(private val vault: SessionVault, private val store: StoryArchiveStore, private val gate: RequestGate,
    private val source: StoryFeedSource, private val media: StoryMediaSource, private val runtime: ReadRuntime = SystemReadRuntime) {
    fun read(session: Session, target: String, cancelled: () -> Boolean, progress: (Int,Int) -> Unit): StorySummary {
        fun verify(saved: SavedAccount?) {
            if (cancelled() || Thread.currentThread().isInterrupted || saved?.session != session ||
                saved.profile.userId != target && saved.people.none { it.latest.userId == target }) throw ApiFailure(ApiFailure.Kind.CANCELLED)
        }
        fun current() = vault.inspect(::verify)
        fun slot() {
            while (true) {
                current()
                if (gate.automationHeld()) throw ApiFailure(ApiFailure.Kind.CANCELLED)
                if (gate.requiresConnection()) throw ApiFailure(ApiFailure.Kind.SESSION_EXPIRED)
                if (gate.blockedUntil() > runtime.now()) throw ApiFailure(ApiFailure.Kind.LIMITED)
                if (gate.budgetNextAt(runtime.now(),session.userId) > runtime.now()) throw ApiFailure(ApiFailure.Kind.BUDGET)
                val wait = gate.nextAllowedAt(session.userId) - runtime.now()
                if (wait <= 0) return
                if (!runtime.pause(minOf(wait,250L),cancelled)) throw ApiFailure(ApiFailure.Kind.CANCELLED)
            }
        }
        fun mediaSlot() {
            while (true) {
                current()
                if (gate.automationHeld()) throw ApiFailure(ApiFailure.Kind.CANCELLED)
                if (gate.requiresConnection()) throw ApiFailure(ApiFailure.Kind.SESSION_EXPIRED)
                if (gate.blockedUntil() > runtime.now()) throw ApiFailure(ApiFailure.Kind.LIMITED)
                if (gate.mediaBudgetNextAt(runtime.now()) > runtime.now()) throw ApiFailure(ApiFailure.Kind.BUDGET)
                val wait = gate.mediaNextAllowedAt(runtime.now()) - runtime.now()
                if (wait <= 0) return
                if (!runtime.pause(minOf(wait,250L),cancelled)) throw ApiFailure(ApiFailure.Kind.CANCELLED)
            }
        }
        slot()
        val candidates = source.read(session,target)
        current()
        var saved = 0; var existing = 0; var metadata = 0
        candidates.forEachIndexed { index,candidate ->
            current()
            if (candidate.facts.target != target) throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
            if (store.contains(target,candidate.facts.id)) existing++ else {
                vault.inspect { verify(it); store.metadata(candidate.facts,runtime.now()) }
                if (candidate.mediaUrl != null && candidate.facts.kind != StoryKind.UNSUPPORTED) {
                    mediaSlot()
                    val bytes = media.read(session,candidate.mediaUrl)
                    vault.inspect { verify(it); store.saveMedia(candidate.facts,bytes) }
                    saved++
                } else metadata++
            }
            progress(index+1,candidates.size)
        }
        val result = StorySummary(candidates.size,saved,existing,metadata)
        vault.inspect { verify(it); store.checked(target,runtime.now(),result) }
        return result
    }
}
