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
    val newlySeen: List<CheckMember> = emptyList(), val notSeen: List<CheckMember> = emptyList(),
    val beforeFollowers: Long? = null, val afterFollowers: Long? = null,
    val beforeFollowing: Long? = null, val afterFollowing: Long? = null,
    val beforePosts: Long? = null, val afterPosts: Long? = null, val edits: List<ProfileEdit> = emptyList()) {
    val key get() = "$target:${kind.name}:${direction?.name}:$observedAt:$analyzedAt"
    companion object {
        const val MEMBER_SAMPLE = 10
        fun sample(users: List<FollowUser>) = users.take(MEMBER_SAMPLE).map { CheckMember(it.id, it.username) }
    }
}

/** Bounded, encrypted presentation history. Account deletion also deletes its journal. */
class CheckJournal(context: Context, slot: String) {
    companion object {
        const val MAX_EVENTS = 2000
        private const val MAX_BYTES = 2 * 1024 * 1024
        private val lock = Any()
        private val failures = mutableSetOf<String>()
        private val listeners = mutableMapOf<String, MutableSet<() -> Unit>>()
        private val cache = object : LinkedHashMap<String, Snapshot>(4, .75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Snapshot>?) = size > 2
        }
    }
    private data class Snapshot(val owner: String, val events: List<CheckEvent>, val read: Set<String>,
        val rules: Map<String, NoticeRule>, val stamp: String)
    private val record = EncryptedRecord(context, slot, MAX_BYTES)
    private val identity = "${context.packageName}:$slot"
    val writeFailed get() = synchronized(lock) { identity in failures }
    /** Callbacks only schedule presentation work; they must not perform I/O under this lock. */
    fun observe(callback: () -> Unit): AutoCloseable {
        synchronized(lock) { listeners.getOrPut(identity) { mutableSetOf() }.add(callback) }
        return AutoCloseable { synchronized(lock) { listeners[identity]?.let { it.remove(callback); if(it.isEmpty())listeners.remove(identity) }; Unit } }
    }
    private fun load(session: Session): Snapshot {
        val stamp = record.stamp()
        cache[identity]?.takeIf { it.stamp == stamp }?.let { return it }
        val body = record.read()?.let(::JSONObject) ?: return Snapshot(session.userId, emptyList(), emptySet(), emptyMap(), stamp)
        require(body.getInt("version") == 1)
        val array = body.getJSONArray("events"); require(array.length() <= MAX_EVENTS)
        val read = body.optJSONArray("read") ?: JSONArray(); require(read.length() <= 500)
        val rules = body.optJSONObject("rules") ?: JSONObject(); require(rules.length() <= PeoplePolicy.MAX_PEOPLE + 1)
        return Snapshot(InstagramIdentity.id(body.get("owner")), (0 until array.length()).map { decode(array.getJSONObject(it)) },
            (0 until read.length()).map { read.getString(it).also {key -> require(key.length<=256)} }.toSet(), rules.keys().asSequence().associateWith { id ->
                InstagramIdentity.id(id)
                val rule = rules.getJSONObject(id); val fields = rule.getJSONArray("fields"); require(fields.length() <= NoticeField.entries.size)
                NoticeRule(rule.getBoolean("enabled"), (0 until fields.length()).map { NoticeField.valueOf(fields.getString(it)) }.toSet())
            }, record.stamp()).also { cache[identity] = it }
    }
    fun read(session: Session): List<CheckEvent> = synchronized(lock) { load(session).takeIf { it.owner == session.userId }?.events.orEmpty() }
    fun readKeys(session: Session): Set<String> = synchronized(lock) { load(session).takeIf { it.owner == session.userId }?.read.orEmpty() }
    fun notifications(session: Session): List<CheckEvent> = HistoryPresentation.notifications(read(session))
    fun unreadCount(session: Session): Int = synchronized(lock) { val value = load(session); if (value.owner != session.userId) 0 else HistoryPresentation.notifications(value.events).count { it.key !in value.read } }
    fun rule(session: Session, target: String): NoticeRule = synchronized(lock) {
        val value=load(session);require(value.owner==session.userId);value.rules[target] ?: NoticeRule()
    }
    fun setRule(session: Session, target: String, rule: NoticeRule) = synchronized(lock) {
        InstagramIdentity.id(target); val value = load(session); require(value.owner == session.userId)
        write(value.copy(rules = value.rules + (target to rule)))
    }
    fun markRead(session: Session, key: String) = synchronized(lock) {
        val value = load(session); require(value.owner == session.userId)
        if (value.events.any { it.key == key } && key !in value.read) write(value.copy(read = (value.read + key).toList().takeLast(500).toSet()))
    }
    fun append(session: Session, event: CheckEvent): Boolean = synchronized(lock) {
        try {
            val value = load(session); require(value.owner == session.userId)
            val events = (value.events.filterNot { it.key == event.key } + event).takeLast(MAX_EVENTS)
            decode(encode(event))
            write(value.copy(events = events)); failures.remove(identity); true
        } catch (_: Exception) { failures.add(identity); false }
    }
    private fun write(value: Snapshot) {
        var events = value.events
        var encoded = events.map(::encode)
        var bytes = encoded.sumOf { it.toString().toByteArray(Charsets.UTF_8).size + 1 }
        while (bytes > MAX_BYTES - 128 * 1024 && encoded.size > 1) {
            bytes -= encoded.first().toString().toByteArray(Charsets.UTF_8).size + 1
            encoded = encoded.drop(1); events = events.drop(1)
        }
        val kept = events.map { it.key }.toSet()
        val reads = value.read.filter { it in kept }.takeLast(500).toSet()
        val body = JSONObject().put("version", 1).put("owner", value.owner).put("events", JSONArray(encoded))
            .put("read", JSONArray(reads.toList())).put("rules", JSONObject().apply {
                value.rules.forEach { (id, rule) -> put(id, JSONObject().put("enabled", rule.enabled).put("fields", JSONArray(rule.fields.map { it.name }))) }
            })
        record.write(body.toString())
        cache[identity] = value.copy(events = events, read = reads, stamp = record.stamp())
        listeners[identity]?.toList()?.forEach { it() }
    }
    fun clearTarget(session: Session, target: String) = synchronized(lock) {
        val value = load(session); require(value.owner == session.userId)
        write(value.copy(events = value.events.filterNot { it.target == target }, rules = value.rules - target))
    }
    fun clear() = synchronized(lock) { record.clear(); cache.remove(identity); failures.remove(identity); Unit }

    private fun encode(value: CheckEvent) = JSONObject().put("target", value.target).put("username", value.username)
        .put("kind", value.kind.name).put("analyzed", value.analyzedAt).apply {
            value.startedAt?.let { put("started", it) }; value.observedAt?.let { put("observed", it) }
            value.previousAt?.let { put("previous", it) }; value.followersDelta?.let { put("followers", it) }
            value.followingDelta?.let { put("following", it) }
            listOf("beforeFollowers" to value.beforeFollowers, "afterFollowers" to value.afterFollowers,
                "beforeFollowing" to value.beforeFollowing, "afterFollowing" to value.afterFollowing,
                "beforePosts" to value.beforePosts, "afterPosts" to value.afterPosts).forEach { (key, number) -> number?.let { require(it >= 0); put(key, it) } }
            put("edits", JSONArray().apply { value.edits.forEach { edit ->
                require(edit.profileId == value.target && edit.before.length <= 32768 && edit.after.length <= 32768)
                put(JSONObject().put("field", edit.field.name).put("before", edit.before).put("after", edit.after).put("previousAt", edit.previousAt))
            } })
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
        fun fact(key: String) = if (body.has(key)) body.getLong(key).also { require(it >= 0) } else null
        val edits = body.optJSONArray("edits") ?: JSONArray(); require(edits.length() <= ProfileChangeField.entries.size)
        val target = InstagramIdentity.id(body.get("target"))
        val changes = (0 until edits.length()).map { index ->
            val edit = edits.getJSONObject(index)
            val before = edit.getString("before"); val after = edit.getString("after")
            require(before.length <= 32768 && after.length <= 32768)
            ProfileEdit(target, username, ProfileChangeField.valueOf(edit.getString("field")), before, after,
                edit.getLong("previousAt").also { require(it > 0) }, time("observed") ?: error("Missing observation time"))
        }
        return CheckEvent(InstagramIdentity.id(body.get("target")), username, CheckKind.valueOf(body.getString("kind")),
            time("started"), time("observed"), time("analyzed") ?: error("Missing analysis time"), time("previous"),
            if (body.has("followers")) body.getLong("followers") else null, if (body.has("following")) body.getLong("following") else null,
            (0 until fields.length()).map { ProfileChangeField.valueOf(fields.getString(it)) }.toSet(),
            if (body.has("direction")) FollowDirection.valueOf(body.getString("direction")) else null,
            if (body.has("coverage")) FollowCoverage.valueOf(body.getString("coverage")) else null,
            count("pages"), count("accounts"), count("added"), count("missing"),
            if (body.has("failure")) ApiFailure.Kind.valueOf(body.getString("failure")) else null, members("newlySeen"), members("notSeen"),
            fact("beforeFollowers"), fact("afterFollowers"), fact("beforeFollowing"), fact("afterFollowing"),
            fact("beforePosts"), fact("afterPosts"), changes)
    }
}
