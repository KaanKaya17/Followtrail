package dev.kaankaya.instagramtracker

import org.json.JSONArray
import org.json.JSONObject

/** Archive ownership is viewer + mobile binding; the legacy single-target record migrates on write. */
object FollowArchiveCodec {
    private fun scan(value: ListScan) = JSONObject().put("nextAt", value.nextAt).put("attemptAt", value.attemptAt)
        .put("continuing", value.continuing).put("basisAt", value.basisAt).apply {
            if (value.firstCountRetryUsed) put("firstCountRetryUsed", true)
            value.basisCount?.let { put("basisCount", it) }; value.failure?.let { put("failure", it.name) } }
    private fun scan(value: JSONObject): ListScan {
        val next = value.getLong("nextAt"); val attempt = value.getLong("attemptAt")
        require(next >= 0 && attempt >= 0)
        val continuing = value.get("continuing") as? Boolean ?: error("Invalid scan state")
        val count = if (value.has("basisCount")) value.getLong("basisCount").also { require(it >= 0) } else null
        val basisAt = if (value.has("basisAt")) value.getLong("basisAt").also { require(it >= 0) } else 0
        require(count == null || basisAt > 0)
        val retryUsed = if (value.has("firstCountRetryUsed")) value.get("firstCountRetryUsed") as Boolean else false
        return ListScan(next, attempt, continuing, if (value.has("failure")) ApiFailure.Kind.valueOf(value.getString("failure")) else null, count, basisAt, retryUsed)
    }
    fun encode(archive: ListArchive): String = JSONObject().put("version", 2).put("owner", archive.owner)
        .put("binding", archive.binding ?: JSONObject.NULL).put("targets", JSONArray().apply {
            archive.targets.forEach { target -> put(JSONObject().put("target", target.target)
                .put("followersState", scan(target.followers)).put("followingState", scan(target.following)).apply {
                    target.snapshot?.let { put("snapshot", JSONObject(FollowListCodec.encode(it))) }
                    target.previous?.let { put("previous", JSONObject(FollowListCodec.encode(it))) }
                }) }
        }).toString().also { decode(it) }
    fun decode(text: String): ListArchive {
        val body = JSONObject(text)
        if (body.getInt("version") == 1) {
            val snapshot = FollowListCodec.decode(text)
            fun state(direction: FollowDirection): ListScan {
                val run = snapshot.run(direction) ?: return ListScan()
                val more = run.coverage() == FollowCoverage.MORE
                return ListScan(run.latest.observedAt + if (more) MonitorPolicy.POLL_MS else ListAutomationPolicy.RESCAN_MS,
                    run.latest.observedAt, more)
            }
            return ListArchive(snapshot.owner, snapshot.binding, listOf(ListTarget(snapshot.target, snapshot,
                followers = state(FollowDirection.FOLLOWERS), following = state(FollowDirection.FOLLOWING))))
        }
        require(body.getInt("version") == 2)
        val owner = InstagramIdentity.id(body.get("owner")); val binding = FollowPageParser.string(body, "binding")
        val array = body.getJSONArray("targets"); require(array.length() <= PeoplePolicy.MAX_PEOPLE + 1)
        val targets = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index); val id = InstagramIdentity.id(item.get("target"))
            fun snapshot(key: String): FollowSnapshot? = if (!item.has(key)) null else FollowListCodec.decode(item.getJSONObject(key).toString()).also {
                require(it.owner == owner && it.binding == binding && it.target == id)
            }
            ListTarget(id, snapshot("snapshot"), snapshot("previous"), scan(item.getJSONObject("followersState")), scan(item.getJSONObject("followingState"))).also { target ->
                FollowDirection.entries.forEach { direction ->
                    if (target.scan(direction).continuing) require(target.snapshot?.run(direction)?.coverage() == FollowCoverage.MORE)
                    target.previous?.run(direction)?.let { require(it.coverage() == FollowCoverage.END_OBSERVED) }
                }
            }
        }
        require(targets.map { it.target }.distinct().size == targets.size)
        return ListArchive(owner, binding, targets)
    }
}
