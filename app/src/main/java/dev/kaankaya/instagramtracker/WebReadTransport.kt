package dev.kaankaya.instagramtracker

import okhttp3.Call
import okhttp3.Request
import org.json.JSONObject

/** Metadata for this app's fixed-origin web protocol; never applied to mobile bindings or other hosts. */
internal object WebRequestContext {
    fun sameOrigin(request: Request): Request {
        require(request.url.isHttps && request.url.host == "www.instagram.com" && request.url.port == 443 &&
            request.url.username.isEmpty() && request.url.password.isEmpty())
        return request.newBuilder().header("Sec-Fetch-Site", "same-origin")
            .header("Sec-Fetch-Mode", "cors").header("Sec-Fetch-Dest", "empty").build()
    }
}

/** First-party web protocol. Credentials can only be sent to the fixed www.instagram.com origin. */
class WebReadTransport(gate: RequestGate, sessions: WebSessions,
    client: Call.Factory = WebRequestClients.create(gate.context)) {
    private val transport = PrivateReadTransport(gate, sessions, client, setOf("www.instagram.com"),
        { state: WebSession, headers, now -> state.updated(headers, now) })
    fun cancel() = transport.cancel()
    fun <T> read(reference: Session, connectionTest: Boolean = false, resumeAutomation: Boolean = true,
        request: (WebSession, Long) -> Request, parse: (JSONObject, Long) -> T): T {
        if (reference.webBinding == null) throw ApiFailure(ApiFailure.Kind.SESSION_EXPIRED)
        return transport.read(reference, connectionTest, resumeAutomation, request, parse)
    }
}

object WebRequests {
    // Public Instagram Web app configuration observed without a user session on 2026-10-27.
    const val APP_ID = "1217981644879628"
    private fun request(state: WebSession, path: String, query: Map<String, String> = emptyMap()): Request {
        WebSessionCodec.validate(state)
        require(path.startsWith("/api/v1/") && !path.contains("..") && !path.contains('?') && !path.contains('#'))
        val url = WebSessionCodec.origin.newBuilder().encodedPath(path)
        query.forEach { (name, value) -> url.addQueryParameter(name, value) }
        val builder = Request.Builder().url(url.build()).get().header("Accept", "application/json")
            .header("User-Agent", state.userAgent).header("Cookie", state.cookieHeader())
            .header("X-IG-App-ID", APP_ID).header("X-Requested-With", "XMLHttpRequest")
            .header("X-IG-WWW-Claim", state.claim).header("Referer", WebSessionCodec.origin.toString())
        state.cookies["csrftoken"]?.takeIf { it.isNotEmpty() }?.let { builder.header("X-CSRFToken", it) }
        return WebRequestContext.sameOrigin(builder.build())
    }
    fun profile(state: WebSession, id: String): Request {
        InstagramIdentity.id(id)
        return request(state, "/api/v1/users/$id/info/")
    }
    fun search(state: WebSession, query: String): Request = request(state,"/api/v1/users/search/",
        mapOf("query" to ProfileSearch.query(query),"count" to "20"))
    fun username(state: WebSession, username: String): Request {
        require(ProfileInput.validUsername(username))
        return request(state, "/api/v1/users/web_profile_info/", mapOf("username" to username))
    }
    fun list(state: WebSession, target: String, direction: FollowDirection, cursor: String?): Request {
        InstagramIdentity.id(target)
        require(cursor == null || cursor.isNotEmpty() && cursor.length <= 4096)
        val query = linkedMapOf("count" to "50", "search_surface" to "follow_list_page")
        if (cursor != null) query["max_id"] = cursor
        return request(state, "/api/v1/friendships/$target/${direction.path}/", query)
    }
}

class WebFollowListReader(gate: RequestGate, sessions: WebSessions,
    client: Call.Factory = WebRequestClients.create(gate.context)) : FollowListSource {
    private val transport = WebReadTransport(gate, sessions, client)
    override fun cancel() = transport.cancel()
    override fun read(session: Session, target: String, direction: FollowDirection, cursor: String?): FollowPage =
        transport.read(session, resumeAutomation = false,
            request = { state, _ -> WebRequests.list(state, target, direction, cursor) }) { body, time ->
            FollowPageParser.parse(body, target, direction, cursor, time)
        }
}

/** Session kind selects one reader before any request. A failed request never changes endpoints or protocols. */
class AccountFollowListReader(gate: RequestGate,
    private val mobile: FollowListSource = MobileFollowListReader(gate, MobileSessions(gate.context)),
    private val web: FollowListSource = WebFollowListReader(gate, WebSessions(gate.context))) : FollowListSource {
    override fun cancel() { mobile.cancel(); web.cancel() }
    override fun read(session: Session, target: String, direction: FollowDirection, cursor: String?): FollowPage = when {
        session.mobileBinding != null -> mobile.read(session, target, direction, cursor)
        session.webBinding != null -> web.read(session, target, direction, cursor)
        else -> throw ApiFailure(ApiFailure.Kind.MOBILE_REQUIRED)
    }
}
