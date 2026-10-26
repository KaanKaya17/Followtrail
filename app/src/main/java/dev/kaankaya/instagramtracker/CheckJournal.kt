package dev.kaankaya.instagramtracker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class CheckKind { PROFILE, LIST, FAILURE }
data class CheckMember(val id: String, val username: String?)

/** Device observation and analysis times, never an Instagram event timestamp. No credentials or response bodies. */
data class CheckEvent(val target: String, val username: String, val kind: CheckKind, val startedAt: Long?,
    val observedAt: Long?, val analyzedAt: Long, val previousAt: Long? = null,
    val followersDelta: Long? = null, val followingDelta: Long? = null,
    val fields: Set<ProfileChangeField> = emptySet(), val direction: FollowDirection? = null,
    val coverage: FollowCoverage? = null, val pages: Int? = null, val accounts: Int? = null,
    val added: Int? = null, val missing: Int? = null, val failure: ApiFailure.Kind? = null,
    val newlySeen: List<CheckMember> = emptyList(), val notSeen: List<CheckMember> = emptyList()) {
    val key get() = "$target:${kind.name}:${direction?.name}:$observedAt:$analyzedAt"
    companion object {
        const val MEMBER_SAMPLE = 10
        fun sample(users: List<FollowUser>) = users.take(MEMBER_SAMPLE).map { CheckMember(it.id, it.username) }
    }
}

/** Bounded, encrypted presentation history. Account deletion also deletes its journal. */
class CheckJournal(context: Context, slot: String) {
    companion object { const val MAX_EVENTS = 120; private val lock = Any(); private val failures = mutableSetOf<String>() }
    private val record = EncryptedRecord(context, slot)
    private val identity = "${context.packageName}:$slot"
    val writeFailed get() = synchronized(lock) { identity in failures }
    fun read(session: Session): List<CheckEvent> = synchronized(lock) {
        val body = record.read()?.let(::JSONObject) ?: return@synchronized emptyList()
        require(body.getInt("version") == 1)
        if (body.getString("owner") != session.userId) return@synchronized emptyList()
        val array = body.getJSONArray("events"); require(array.length() <= MAX_EVENTS)
        (0 until array.length()).map { decode(array.getJSONObject(it)) }
    }
    fun append(session: Session, event: CheckEvent): Boolean = synchronized(lock) {
        try {
            val events = (read(session).filterNot { it.key == event.key } + event).takeLast(MAX_EVENTS)
            decode(encode(event))
            write(session, events); failures.remove(identity); true
        } catch (_: Exception) { failures.add(identity); false }
    }
    private fun write(session: Session, events: List<CheckEvent>) {
        record.write(JSONObject().put("version", 1).put("owner", session.userId)
            .put("events", JSONArray().apply { events.forEach { put(encode(it)) } }).toString())
    }
    fun clearTarget(session: Session, target: String) = synchronized(lock) { write(session, read(session).filterNot { it.target == target }) }
    fun clear() = synchronized(lock) { record.clear(); failures.remove(identity); Unit }

    private fun encode(value: CheckEvent) = JSONObject().put("target", value.target).put("username", value.username)
        .put("kind", value.kind.name).put("analyzed", value.analyzedAt).apply {
            value.startedAt?.let { put("started", it) }; value.observedAt?.let { put("observed", it) }
            value.previousAt?.let { put("previous", it) }; value.followersDelta?.let { put("followers", it) }
            value.followingDelta?.let { put("following", it) }
            put("fields", JSONArray(value.fields.map { it.name }))
            value.direction?.let { put("direction", it.name) }; value.coverage?.let { put("coverage", it.name) }
            value.pages?.let { put("pages", it) }; value.accounts?.let { put("accounts", it) }
            value.added?.let { put("added", it) }; value.missing?.let { put("missing", it) }
            value.failure?.let { put("failure", it.name) }
            fun members(key: String, users: List<CheckMember>) = put(key, JSONArray().apply {
                users.forEach { put(JSONObject().put("id", it.id).apply { it.username?.let { name -> put("username", name) } }) }
            })
            require(value.newlySeen.size <= CheckEvent.MEMBER_SAMPLE && value.notSeen.size <= CheckEvent.MEMBER_SAMPLE)
            members("newlySeen", value.newlySeen); members("notSeen", value.notSeen)
        }
    private fun decode(body: JSONObject): CheckEvent {
        fun time(key: String) = if (body.has(key)) body.getLong(key).also { require(it > 0) } else null
        fun count(key: String) = if (body.has(key)) body.getInt(key).also { require(it >= 0) } else null
        val fields = body.getJSONArray("fields"); require(fields.length() <= ProfileChangeField.entries.size)
        val username = body.getString("username"); require(ProfileInput.validUsername(username))
        fun members(key: String): List<CheckMember> {
            if (!body.has(key)) return emptyList()
            val items = body.getJSONArray(key); require(items.length() <= CheckEvent.MEMBER_SAMPLE)
            return (0 until items.length()).map { index ->
                val item = items.getJSONObject(index)
                val name = if (item.has("username")) item.getString("username").also { require(ProfileInput.validUsername(it)) } else null
                CheckMember(InstagramIdentity.id(item.get("id")), name)
            }
        }
        return CheckEvent(InstagramIdentity.id(body.get("target")), username, CheckKind.valueOf(body.getString("kind")),
            time("started"), time("observed"), time("analyzed") ?: error("Missing analysis time"), time("previous"),
            if (body.has("followers")) body.getLong("followers") else null, if (body.has("following")) body.getLong("following") else null,
            (0 until fields.length()).map { ProfileChangeField.valueOf(fields.getString(it)) }.toSet(),
            if (body.has("direction")) FollowDirection.valueOf(body.getString("direction")) else null,
            if (body.has("coverage")) FollowCoverage.valueOf(body.getString("coverage")) else null,
            count("pages"), count("accounts"), count("added"), count("missing"),
            if (body.has("failure")) ApiFailure.Kind.valueOf(body.getString("failure")) else null, members("newlySeen"), members("notSeen"))
    }
}
