package dev.kaankaya.instagramtracker

data class ListAutomation(val enabled: Boolean = false, val targets: Set<String> = emptySet(), val ownNextCheckAt: Long = 0,
    val lastWorkWasList: Boolean = false)
data class ListScan(val nextAt: Long = 0, val attemptAt: Long = 0, val continuing: Boolean = false,
    val failure: ApiFailure.Kind? = null)
data class ListTarget(val target: String, val snapshot: FollowSnapshot? = null, val previous: FollowSnapshot? = null,
    val followers: ListScan = ListScan(), val following: ListScan = ListScan()) {
    fun scan(direction: FollowDirection) = if (direction == FollowDirection.FOLLOWERS) followers else following
    fun withScan(direction: FollowDirection, scan: ListScan) =
        if (direction == FollowDirection.FOLLOWERS) copy(followers = scan) else copy(following = scan)
}
data class ListArchive(val owner: String, val binding: String?, val targets: List<ListTarget> = emptyList()) {
    fun matches(session: Session) = owner == session.userId && binding == session.mobileBinding
    fun replace(target: ListTarget) = copy(targets = targets.filterNot { it.target == target.target } + target)
}
data class ListDifference(val target: String, val direction: FollowDirection, val previousAt: Long, val observedAt: Long,
    val added: List<FollowUser>, val missing: List<FollowUser>)

object ListAutomationPolicy {
    const val RESCAN_MS = 24 * 60 * 60 * 1000L
    const val OWN_PROFILE_MS = 60 * 60 * 1000L
    fun difference(target: ListTarget, direction: FollowDirection): ListDifference? {
        val before = target.previous?.run(direction) ?: return null
        val after = target.snapshot?.run(direction) ?: return null
        // Reaching the observed end is necessary, not a guarantee of an atomic/complete Instagram snapshot.
        if (before.coverage() != FollowCoverage.END_OBSERVED || after.coverage() != FollowCoverage.END_OBSERVED ||
            before.latest.observedAt >= after.pages.first().observedAt) return null
        val old = before.users.map { it.id }.toSet(); val current = after.users.map { it.id }.toSet()
        return ListDifference(target.target, direction, before.latest.observedAt, after.latest.observedAt,
            after.users.filter { it.id !in old }, before.users.filter { it.id !in current })
    }
    fun record(target: ListTarget, session: Session, page: FollowPage): ListTarget {
        var previous = target.previous
        val old = target.snapshot?.run(page.direction)
        if (page.inputCursor == null && old?.coverage() == FollowCoverage.END_OBSERVED) {
            val base = previous ?: FollowSnapshot(session.userId, session.mobileBinding, target.target)
            previous = if (page.direction == FollowDirection.FOLLOWERS) base.copy(followers = old) else base.copy(following = old)
        }
        val snapshot = (target.snapshot ?: FollowSnapshot(session.userId, session.mobileBinding, target.target)).record(page)
        val more = snapshot.run(page.direction)!!.coverage() == FollowCoverage.MORE
        return target.copy(snapshot = snapshot, previous = previous).withScan(page.direction,
            ListScan(page.observedAt + if (more) MonitorPolicy.POLL_MS else RESCAN_MS, page.observedAt, more))
    }
}

sealed interface AutomaticTask {
    val target: String
    val dueAt: Long
    data class Person(override val target: String, override val dueAt: Long) : AutomaticTask
    data class Own(override val target: String, override val dueAt: Long) : AutomaticTask
    data class Page(override val target: String, override val dueAt: Long, val direction: FollowDirection,
        val cursor: String?, val previousObservation: Long?) : AutomaticTask
}

object AutomaticQueue {
    fun next(account: SavedAccount, archive: ListArchive, now: Long): AutomaticTask? {
        require(archive.matches(account.session))
        val tasks = account.people.filter { it.watching }.map { AutomaticTask.Person(it.latest.userId, it.nextCheckAt) }.toMutableList<AutomaticTask>()
        if (account.automation.enabled) {
            if (account.session.userId in account.automation.targets) tasks += AutomaticTask.Own(account.session.userId,
                maxOf(account.automation.ownNextCheckAt, account.profile.observedAt + ListAutomationPolicy.OWN_PROFILE_MS))
            for (id in account.automation.targets) for (direction in FollowDirection.entries) {
                val target = archive.targets.firstOrNull { it.target == id }
                val state = target?.scan(direction) ?: ListScan()
                // Capacity needs user action, never repeatedly fetch an unrecordable page.
                if (state.failure == ApiFailure.Kind.CAPACITY) continue
                val run = target?.snapshot?.run(direction)
                tasks += AutomaticTask.Page(id, state.nextAt, direction,
                    if (state.continuing) run?.cursor() else null, run?.latest?.observedAt)
            }
        }
        val ready = tasks.filter { it.dueAt <= now }
        val page = ready.filterIsInstance<AutomaticTask.Page>().minByOrNull { it.dueAt }
        val profile = ready.filterNot { it is AutomaticTask.Page }.minByOrNull { it.dueAt }
        // A large first scan must not starve profile watches (or vice versa) under the shared budget.
        return if (page != null && profile != null) { if (account.automation.lastWorkWasList) profile else page }
            else page ?: profile
    }
}
