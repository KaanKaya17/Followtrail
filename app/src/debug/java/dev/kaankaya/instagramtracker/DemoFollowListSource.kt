package dev.kaankaya.instagramtracker

import org.json.JSONArray
import org.json.JSONObject

/** Debug-only fixture. No sockets, account credentials or real Instagram requests. */
class DemoFollowListSource(private val gate: RequestGate) : FollowListSource {
    override fun cancel() = Unit
    override fun read(session: Session, target: String, direction: FollowDirection, cursor: String?): FollowPage {
        gate.claim().use {
            gate.begin()
            val ids = if (direction == FollowDirection.FOLLOWING) listOf("502", "504")
                else if (cursor == null) listOf("501", "502") else listOf("502", "503")
            val body = JSONObject().put("status", "ok").put("users", JSONArray().apply {
                ids.forEach { id -> put(JSONObject().put("pk", id).put("username", "kisi$id.demo").put("full_name", "Örnek kişi $id")
                    .put("profile_pic_url", "https://cdninstagram.com/offline-demo/$id.png")) }
            }).put("next_max_id", if (direction == FollowDirection.FOLLOWERS && cursor == null) "demo-page-2" else JSONObject.NULL)
                .put("has_more", direction == FollowDirection.FOLLOWERS && cursor == null).put("should_limit_list_of_followers", false)
            gate.observedStatus(200)
            return FollowPageParser.parse(body, target, direction, cursor, System.currentTimeMillis())
        }
    }
}
