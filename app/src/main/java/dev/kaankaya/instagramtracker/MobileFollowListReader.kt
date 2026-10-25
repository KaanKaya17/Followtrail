package dev.kaankaya.instagramtracker

import okhttp3.Call
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl

interface FollowListSource {
    fun read(session: Session, target: String, direction: FollowDirection, cursor: String?): FollowPage
    fun cancel()
}

/** Exactly one explicit page; no pagination loop, alternate route or automatic login. */
class MobileFollowListReader(gate: RequestGate, sessions: MobileSessions,
    client: Call.Factory = MobileReadTransport.client()) : FollowListSource {
    private val transport = MobileReadTransport(gate, sessions, client)
    companion object {
        // Research request size, not a server maximum or safe account quota.
        const val PAGE_SIZE = 50
        fun request(state: MobileSession, target: String, direction: FollowDirection, cursor: String?, now: Long): Request {
            InstagramIdentity.id(target)
            require(cursor == null || cursor.isNotEmpty())
            val address = "https://i.instagram.com/api/v1/friendships/$target/${direction.path}/".toHttpUrl().newBuilder()
                .addQueryParameter("count", PAGE_SIZE.toString()).addQueryParameter("rank_token", "${state.owner}_${state.headers.getValue("X-IG-Device-ID")}")
                .addQueryParameter("search_surface", "follow_list_page").addQueryParameter("query", "")
                .addQueryParameter("enable_groups", "true")
            if (cursor != null) address.addQueryParameter("max_id", cursor)
            val request = Request.Builder().url(address.build()).get().header("Accept", "application/json")
            state.requestHeaders(now).forEach { (name, value) -> request.header(name, value) }
            return request.build()
        }
    }
    override fun cancel() = transport.cancel()
    override fun read(session: Session, target: String, direction: FollowDirection, cursor: String?): FollowPage {
        if (session.mobileBinding == null) throw ApiFailure(ApiFailure.Kind.MOBILE_REQUIRED)
        return transport.read(session, resumeAutomation = false,
            request = { state, now -> request(state, target, direction, cursor, now) }) { body, time ->
            FollowPageParser.parse(body, target, direction, cursor, time)
        }
    }
}
