package dev.kaankaya.instagramtracker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A single current investigation, scoped to viewer, mobile binding and target. No credentials. */
object FollowListCodec {
    private fun field(body: JSONObject, key: String, field: FollowField<*>) {
        if (field.present) body.put(key, field.value ?: JSONObject.NULL)
    }
    private fun page(page: FollowPage): JSONObject {
        val body = JSONObject().put("status", "ok").put("users", JSONArray().apply {
            page.users.forEach { user -> put(JSONObject().put("pk", user.id).apply {
                user.username?.let { put("username", it) }; user.name?.let { put("full_name", it) }
                user.picture?.let { put("profile_pic_url", it) }; user.privateAccount?.let { put("is_private", it) }
                user.verified?.let { put("is_verified", it) }
            }) }
        })
        field(body, "next_max_id", page.next); field(body, "should_limit_list_of_followers", page.limited)
        field(body, "has_more", page.hasMore); field(body, "big_list", page.bigList)
        return JSONObject().put("body", body).put("input", page.inputCursor ?: JSONObject.NULL).put("time", page.observedAt)
    }
    fun encode(snapshot: FollowSnapshot): String = JSONObject().put("version", 1).put("owner", snapshot.owner)
        .put("binding", snapshot.binding ?: JSONObject.NULL).put("target", snapshot.target).apply {
            FollowDirection.entries.forEach { direction -> snapshot.run(direction)?.let { run ->
                put(direction.path, JSONArray().apply { run.pages.forEach { put(page(it)) } })
            } }
        }.toString()
    fun decode(text: String): FollowSnapshot {
        val body = JSONObject(text); require(body.getInt("version") == 1)
        val owner = InstagramIdentity.id(body.get("owner")); val target = InstagramIdentity.id(body.get("target"))
        var snapshot = FollowSnapshot(owner, FollowPageParser.string(body, "binding"), target)
        FollowDirection.entries.forEach { direction ->
            if (body.has(direction.path)) {
                val pages = body.getJSONArray(direction.path); require(pages.length() > 0)
                for (index in 0 until pages.length()) {
                    val page = pages.getJSONObject(index)
                    val cursor = FollowPageParser.string(page, "input")
                    require((index == 0) == (cursor == null))
                    val time = page.getLong("time"); require(time > 0)
                    snapshot = snapshot.record(FollowPageParser.parse(page.getJSONObject("body"), target, direction, cursor, time))
                }
            }
        }
        require(snapshot.followers != null || snapshot.following != null)
        return snapshot
    }
}

class FollowListVault(context: Context, slot: String = "followlists") {
    companion object { private val lock = Any() }
    private val record = EncryptedRecord(context, slot)
    fun read(session: Session, target: String): FollowSnapshot? = synchronized(lock) {
        record.read()?.let(FollowListCodec::decode)?.takeIf { it.matches(session, target) }
    }
    fun commit(session: Session, page: FollowPage): FollowSnapshot = synchronized(lock) {
        val next = (read(session, page.target) ?: FollowSnapshot(session.userId, session.mobileBinding, page.target)).record(page)
        val text = FollowListCodec.encode(next)
        // Same explicit byte bound as the owning encrypted record; never silently drop page members.
        if (text.toByteArray(Charsets.UTF_8).size > 512 * 1024) throw ApiFailure(ApiFailure.Kind.CAPACITY)
        record.write(text)
        next
    }
    fun clearTarget(session: Session, target: String) = synchronized(lock) {
        if (read(session, target) != null) record.clear()
    }
    fun clear() = synchronized(lock) { record.clear() }
}
