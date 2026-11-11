package dev.kaankaya.instagramtracker

import org.json.JSONObject

enum class FollowDirection(val path: String) { FOLLOWERS("followers"), FOLLOWING("following") }
data class FollowField<T>(val present: Boolean, val value: T?)
data class FollowUser(val id: String, val username: String?, val name: String?, val picture: String?,
    val privateAccount: Boolean?, val verified: Boolean?)
enum class FollowCoverage { MORE, END_OBSERVED, LIMITED, UNKNOWN, STALLED }
data class FollowPage(val target: String, val direction: FollowDirection, val inputCursor: String?,
    val users: List<FollowUser>, val next: FollowField<String>, val limited: FollowField<Boolean>,
    val hasMore: FollowField<Boolean>, val bigList: FollowField<Boolean>, val observedAt: Long) {
    fun coverage(): FollowCoverage = when {
        limited.value == true -> FollowCoverage.LIMITED
        !next.value.isNullOrEmpty() && hasMore.value == false -> FollowCoverage.UNKNOWN
        !next.value.isNullOrEmpty() -> FollowCoverage.MORE
        hasMore.value == true -> FollowCoverage.UNKNOWN
        next.present || hasMore.value == false -> FollowCoverage.END_OBSERVED
        else -> FollowCoverage.UNKNOWN
    }
}
data class FollowRun(val pages: List<FollowPage>, val expectedCount: Long? = null) {
    init { require(pages.isNotEmpty() && (expectedCount == null || expectedCount >= 0)) }
    fun comparable() = coverage() == FollowCoverage.END_OBSERVED && (expectedCount == null || users.size.toLong() == expectedCount)
    val users: List<FollowUser> get() = pages.flatMap { it.users }.distinctBy { it.id }
    val latest: FollowPage get() = pages.last()
    fun coverage(): FollowCoverage {
        val state = latest.coverage()
        return if (state == FollowCoverage.MORE && (latest.users.isEmpty() || pages.any { it.inputCursor == latest.next.value }))
            FollowCoverage.STALLED else state
    }
    fun cursor(): String? = latest.next.value?.takeIf { coverage() == FollowCoverage.MORE }
    fun append(page: FollowPage): FollowRun {
        require(coverage() == FollowCoverage.MORE && page.inputCursor == cursor())
        require(page.target == latest.target && page.direction == latest.direction)
        return copy(pages = pages + page)
    }
}
data class FollowSnapshot(val owner: String, val binding: String?, val target: String,
    val followers: FollowRun? = null, val following: FollowRun? = null) {
    fun run(direction: FollowDirection) = if (direction == FollowDirection.FOLLOWERS) followers else following
    fun matches(session: Session, targetId: String) = owner == session.userId && binding == session.connectionBinding && target == targetId
    fun record(page: FollowPage, expectedCount: Long? = null): FollowSnapshot {
        require(page.target == target)
        val run = if (page.inputCursor == null) FollowRun(listOf(page), expectedCount) else
            (run(page.direction) ?: error("Missing first page")).append(page)
        return if (page.direction == FollowDirection.FOLLOWERS) copy(followers = run) else copy(following = run)
    }
}

/** One owner for wire fields. Missing/null/false are not interchangeable. */
object FollowPageParser {
    private fun invalid(): Nothing = throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
    fun string(value: JSONObject, key: String): String? = when (val field = value.opt(key)) {
        null, JSONObject.NULL -> null
        is String -> field
        else -> invalid()
    }
    fun bool(value: JSONObject, key: String): Boolean? = when (val field = value.opt(key)) {
        null, JSONObject.NULL -> null
        is Boolean -> field
        else -> invalid()
    }
    fun parse(body: JSONObject, target: String, direction: FollowDirection, cursor: String?, time: Long): FollowPage {
        if (body.optString("status") != "ok") invalid()
        val array = body.optJSONArray("users") ?: invalid()
        val users = (0 until array.length()).map { index ->
            val user = array.optJSONObject(index) ?: invalid()
            val username = string(user, "username")
            if (username != null && !ProfileInput.validUsername(username)) invalid()
            FollowUser(InstagramIdentity.id(user.opt("pk")), username, string(user, "full_name"), string(user, "profile_pic_url"),
                bool(user, "is_private"), bool(user, "is_verified"))
        }
        return FollowPage(InstagramIdentity.id(target), direction, cursor, users,
            FollowField(body.has("next_max_id"), string(body, "next_max_id")),
            FollowField(body.has("should_limit_list_of_followers"), bool(body, "should_limit_list_of_followers")),
            FollowField(body.has("has_more"), bool(body, "has_more")), FollowField(body.has("big_list"), bool(body, "big_list")), time)
    }
}

object FollowAnalysis {
    fun common(first: FollowRun?, second: FollowRun?): List<FollowUser>? {
        if (first == null || second == null) return null
        val secondIds = second.users.mapTo(HashSet()) { it.id }
        return first.users.filter { it.id in secondIds }
    }

    fun privateListNotice(viewer: Profile, target: Profile, following: FollowRun?): Boolean =
        viewer.userId != target.userId && target.details.privateAccount == true &&
            following?.comparable() == true && following.users.size.toLong() == viewer.following &&
            following.users.none { it.id == target.userId }

    data class Comparison(val mutual: List<FollowUser>, val followersOnly: List<FollowUser>, val followingOnly: List<FollowUser>)
    fun compare(snapshot: FollowSnapshot): Comparison? {
        val followers = snapshot.followers?.users ?: return null
        val following = snapshot.following?.users ?: return null
        val followerIds = followers.map { it.id }.toSet()
        val followingIds = following.map { it.id }.toSet()
        return Comparison(followers.filter { it.id in followingIds }, followers.filter { it.id !in followingIds },
            following.filter { it.id !in followerIds })
    }
}
