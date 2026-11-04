package dev.kaankaya.instagramtracker

/** Derived from retained successful observations; never an Instagram event timestamp. */
sealed interface ActivityItem {
    val profileId: String
    val username: String
    val observedAt: Long
    val key: String

    data class Counts(override val profileId: String, override val username: String,
        val previousAt: Long, override val observedAt: Long, val followersDelta: Long, val followingDelta: Long, val beforeFollowers: Long? = null, val beforeFollowing: Long? = null) : ActivityItem {
        override val key get() = "counts:$profileId:$previousAt:$observedAt"
    }
    data class ProfileChanged(val edit: ProfileEdit) : ActivityItem {
        override val profileId get() = edit.profileId
        override val username get() = edit.username
        override val observedAt get() = edit.observedAt
        override val key get() = edit.key
    }
    data class ListRead(override val profileId: String, override val username: String, override val observedAt: Long,
        val direction: FollowDirection, val pageNumber: Int) : ActivityItem {
        override val key get() = "list:$profileId:${direction.path}:$pageNumber:$observedAt"
    }
}

object RecentActivity {
    const val MAX_ITEMS = 20
    fun items(account: SavedAccount, lists: FollowSnapshot? = null): List<ActivityItem> = all(account, listOfNotNull(lists))
    fun all(account: SavedAccount, snapshots: List<FollowSnapshot>): List<ActivityItem> {
        val entries = mutableListOf<ActivityItem>()
        for (person in account.people) person.history.zipWithNext().forEach { (before, after) ->
            val followers = after.followers - before.followers
            val following = after.following - before.following
            if (followers != 0L || following != 0L) entries += ActivityItem.Counts(after.userId, after.username,
                before.observedAt, after.observedAt, followers, following, before.followers, before.following)
        }
        account.people.forEach { person -> entries += ProfileChanges.history(person.history).map { ActivityItem.ProfileChanged(it) } }
        for (lists in snapshots) if (lists.matches(account.session, lists.target)) {
            val target = (listOf(account.profile) + account.people.map { it.latest }).firstOrNull { it.userId == lists.target }
            if (target != null) FollowDirection.entries.forEach { direction ->
                lists.run(direction)?.pages?.forEachIndexed { index, page ->
                    entries += ActivityItem.ListRead(target.userId, target.username, page.observedAt, direction, index + 1)
                }
            }
        }
        return entries.sortedWith(compareByDescending<ActivityItem> { it.observedAt }.thenBy { it.key }).take(MAX_ITEMS)
    }
}
