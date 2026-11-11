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
                run.expectedCount?.let { put(direction.path + "Expected", it) }
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
                    val expected = if (body.has(direction.path + "Expected")) body.getLong(direction.path + "Expected").also { require(it >= 0) } else null
                    snapshot = snapshot.record(FollowPageParser.parse(page.getJSONObject("body"), target, direction, cursor, time), expected)
                }
            }
        }
        require(snapshot.followers != null || snapshot.following != null)
        return snapshot
    }
}

class FollowListVault(private val context: Context, private val slot: String = "followlists", private val owner: String? = null) {
    companion object { private val lock = AccountCatalog.lock }
    private fun record(id: String?)=EncryptedRecord(context,if(slot=="followlists") {
        val catalog=AccountCatalog(context)
        if(id==null || catalog.storage(id)=="account")slot else catalog.storage(id)+"lists"
    } else slot)
    fun archive(session: Session): ListArchive = synchronized(lock) {
        val saved=record(session.userId).read()?.let(FollowArchiveCodec::decode)
        if(saved?.matches(session)==true)return@synchronized saved
        // Only the currently committed session of this numeric owner proves a reconnect. Preserve
        // dated history even if a previous write stopped between the account and list records.
        if(slot=="followlists" && saved?.owner==session.userId && SessionVault(context,owner=session.userId).read()?.session==session)
            return@synchronized reconnected(saved,session)
        ListArchive(session.userId, session.connectionBinding)
    }
    private fun reconnected(old: ListArchive,next: Session): ListArchive {
        fun rebind(snapshot: FollowSnapshot?)=snapshot?.copy(binding=next.connectionBinding)
        return old.copy(binding=next.connectionBinding,targets=old.targets.map {target->target.copy(
            snapshot=rebind(target.snapshot),previous=rebind(target.previous),followers=ListScan(),following=ListScan())})
    }
    private fun write(archive: ListArchive) = record(archive.owner).write(FollowArchiveCodec.encode(archive))
    fun current(session: Session): FollowSnapshot? = synchronized(lock) {
        archive(session).targets.mapNotNull { it.snapshot }.maxByOrNull { snapshot ->
            FollowDirection.entries.mapNotNull { snapshot.run(it)?.latest?.observedAt }.maxOrNull() ?: 0
        }
    }
    fun read(session: Session, target: String): FollowSnapshot? = synchronized(lock) {
        archive(session).targets.firstOrNull { it.target == target }?.snapshot
    }
    fun target(session: Session, target: String): ListTarget? = synchronized(lock) { archive(session).targets.firstOrNull { it.target == target } }
    fun commit(session: Session, page: FollowPage): FollowSnapshot = synchronized(lock) {
        val archive = archive(session)
        val previous = archive.targets.firstOrNull { it.target == page.target } ?: ListTarget(page.target)
        val next = ListAutomationPolicy.record(previous, session, page)
        write(archive.replace(next))
        next.snapshot!!
    }
    fun commitCurrent(session: Session, page: FollowPage, expected: FollowRun?, basis: ListBasis? = null): FollowSnapshot = synchronized(lock) {
        if (read(session, page.target)?.run(page.direction) != expected) throw ApiFailure(ApiFailure.Kind.CANCELLED)
        val archive = archive(session)
        val target = archive.targets.firstOrNull { it.target == page.target } ?: ListTarget(page.target)
        val state = target.scan(page.direction)
        val prepared = if (page.inputCursor == null) target.withScan(page.direction, state.copy(basisCount = basis?.count, basisAt = basis?.observedAt ?: 0)) else target
        val next = ListAutomationPolicy.record(prepared, session, page)
        write(archive.replace(next)); next.snapshot!!
    }
    private fun matches(target: ListTarget, task: AutomaticTask.Page, now: Long? = null) =
        target.snapshot?.run(task.direction)?.latest?.observedAt == task.previousObservation &&
            ((if (target.scan(task.direction).continuing) target.snapshot?.run(task.direction)?.cursor() else null) == task.cursor ||
                task.cursor == null && now != null && target.snapshot?.run(task.direction)?.let { now - it.latest.observedAt >= ListAutomationPolicy.RESCAN_MS } == true)
    fun reserve(session: Session, task: AutomaticTask.Page, now: Long, continuationOf: FollowRun? = null) = synchronized(lock) {
        val archive = archive(session)
        val target = archive.targets.firstOrNull { it.target == task.target } ?: ListTarget(task.target)
        if (!matches(target, task, now) || AutomaticQueue.page(target, task.direction, now, continuationOf, task.basis) != task) throw ApiFailure(ApiFailure.Kind.CANCELLED)
        val state = target.scan(task.direction)
        val capacityProbe = archive.replace(target.withScan(task.direction, state.copy(attemptAt = now, nextAt = now + MonitorPolicy.POLL_MS,
            continuing = state.continuing && task.cursor != null,
            basisCount = if (task.cursor == null) task.basis?.count else state.basisCount,
            basisAt = if (task.cursor == null) task.basis?.observedAt ?: 0 else state.basisAt)))
        write(capacityProbe)
    }
    fun commitAutomatic(session: Session, task: AutomaticTask.Page, attemptAt: Long, page: FollowPage): ListPageCommit = synchronized(lock) {
        val archive = archive(session)
        val target = archive.targets.firstOrNull { it.target == task.target } ?: throw ApiFailure(ApiFailure.Kind.CANCELLED)
        if (!matches(target, task) || target.scan(task.direction).attemptAt != attemptAt) throw ApiFailure(ApiFailure.Kind.CANCELLED)
        require(page.target == task.target && page.direction == task.direction && page.inputCursor == task.cursor)
        val next = ListAutomationPolicy.record(target, session, page)
        write(archive.replace(next))
        ListPageCommit(next, ListAutomationPolicy.difference(next, task.direction)?.takeIf { it.added.isNotEmpty() || it.missing.isNotEmpty() })
    }
    fun failed(session: Session, task: AutomaticTask.Page, attemptAt: Long, failure: ApiFailure.Kind, http: Int? = null) = synchronized(lock) {
        val archive = archive(session)
        val target = archive.targets.firstOrNull { it.target == task.target } ?: return@synchronized
        if (!matches(target, task) || target.scan(task.direction).attemptAt != attemptAt) return@synchronized
        write(archive.replace(target.withScan(task.direction, target.scan(task.direction).copy(
            nextAt = attemptAt + if (http in setOf(400, 404) && task.cursor != null) 30 * 60_000L else ListAutomationPolicy.RESCAN_MS,
            continuing = task.cursor != null && http !in setOf(400, 404) &&
                failure in setOf(ApiFailure.Kind.CONNECTION, ApiFailure.Kind.LIMITED) && target.snapshot?.run(task.direction)?.coverage() == FollowCoverage.MORE, failure = failure))))
    }
    fun deferCurrent(session: Session, targetId: String, direction: FollowDirection, expected: FollowRun?, failure: ApiFailure.Kind, now: Long, http: Int? = null) = synchronized(lock) {
        val archive = archive(session)
        val target = archive.targets.firstOrNull { it.target == targetId } ?: ListTarget(targetId)
        if (target.snapshot?.run(direction) != expected) return@synchronized
        write(archive.replace(target.withScan(direction, target.scan(direction).copy(nextAt = now + 30 * 60 * 1000L,
            failure = failure, continuing = http !in setOf(400, 404) &&
                failure in setOf(ApiFailure.Kind.CONNECTION, ApiFailure.Kind.LIMITED) && expected?.coverage() == FollowCoverage.MORE))))
    }
    fun clearTarget(session: Session, target: String) = synchronized(lock) {
        val archive = archive(session)
        if (archive.targets.any { it.target == target }) write(archive.copy(targets = archive.targets.filterNot { it.target == target }))
    }
    fun prunePast(session: Session, before: Long) = synchronized(lock) {
        if (before == 0L) return@synchronized
        val saved = record(session.userId).read()?.let(FollowArchiveCodec::decode) ?: return@synchronized
        require(saved.owner==session.userId)
        fun recent(snapshot: FollowSnapshot?): FollowSnapshot? = snapshot?.let {
            val followers=it.followers?.takeIf {run -> run.latest.observedAt>=before}
            val following=it.following?.takeIf {run -> run.latest.observedAt>=before}
            if(followers==null && following==null)null else it.copy(followers=followers,following=following)
        }
        write(saved.copy(targets=saved.targets.map { it.copy(previous=recent(it.previous)) }))
    }
    fun clear() = synchronized(lock) { record(owner ?: if(slot=="followlists")AccountCatalog(context).active() else null).clear() }
}
