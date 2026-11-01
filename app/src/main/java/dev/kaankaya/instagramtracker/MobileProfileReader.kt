package dev.kaankaya.instagramtracker

import okhttp3.Call
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl

class MobileProfileReader(gate: RequestGate, sessions: MobileSessions,
    client: Call.Factory = MobileReadTransport.client()) {
    private val transport = MobileReadTransport(gate, sessions, client)
    companion object {
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
    fun cancel() = transport.cancel()
    fun search(reference: Session,query: String): List<ProfileCandidate> = transport.read(reference,request = {state,now->
        val url="https://i.instagram.com/api/v1/users/search/".toHttpUrl().newBuilder()
            .addQueryParameter("query",ProfileSearch.query(query)).addQueryParameter("count","20").build()
        Request.Builder().url(url).get().header("Accept","application/json").apply {
            state.requestHeaders(now).forEach {(name,value)->header(name,value)}
        }.build()
    }) {body,_->ProfileSearch.parse(body)}
    fun read(reference: Session, userId: String?, username: String?, connectionTest: Boolean = false): Profile =
        transport.read(reference, connectionTest, request = { state, now -> request(state, userId, username, now) }) { body, time ->
            if (userId != null) ProfileParser.fromBody(body, userId, time)
            else {
                if (body.optString("status") != "ok") throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
                ProfileParser.user(body.optJSONObject("user") ?: throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE), time).also {
                    if (!it.username.equals(username, true)) throw ApiFailure(ApiFailure.Kind.WRONG_ACCOUNT)
                }
            }
        }
}
