package dev.kaankaya.instagramtracker

data class ListAutomation(val enabled: Boolean = false, val targets: Set<String> = emptySet(), val ownNextCheckAt: Long = 0,
    val lastWorkWasList: Boolean = false)
data class ListScan(val nextAt: Long = 0, val attemptAt: Long = 0, val continuing: Boolean = false,
    val failure: ApiFailure.Kind? = null, val basisCount: Long? = null, val basisAt: Long = 0)

/** Profile observation used when a scan starts; never treated as proof that the list is complete. */
data class ListBasis(val count: Long, val observedAt: Long) {
    companion object {
        fun of(profile: Profile, direction: FollowDirection) = ListBasis(
            if (direction == FollowDirection.FOLLOWERS) profile.followers else profile.following, profile.observedAt)
    }
}
data class ListTarget(val target: String, val snapshot: FollowSnapshot? = null, val previous: FollowSnapshot? = null,
    val followers: ListScan = ListScan(), val following: ListScan = ListScan()) {
    fun scan(direction: FollowDirection) = if (direction == FollowDirection.FOLLOWERS) followers else following
    fun withScan(direction: FollowDirection, scan: ListScan) =
        if (direction == FollowDirection.FOLLOWERS) copy(followers = scan) else copy(following = scan)
}
data class ListArchive(val owner: String, val binding: String?, val targets: List<ListTarget> = emptyList()) {
    fun matches(session: Session) = owner == session.userId && binding == session.connectionBinding
    fun replace(target: ListTarget) = copy(targets = targets.filterNot { it.target == target.target } + target)
}
data class ListDifference(val target: String, val direction: FollowDirection, val previousAt: Long, val observedAt: Long,
    val added: List<FollowUser>, val missing: List<FollowUser>)
data class ListPageCommit(val target: ListTarget, val difference: ListDifference?)

object ListAutomationPolicy {
    const val RESCAN_MS = 24 * 60 * 60 * 1000L
    const val OWN_PROFILE_MS = RequestDefaults.DEFAULT_INTERVAL_MINUTES * 60_000L
    /** A restricted committed run needs an explicit retry. Reconnecting resets its scan state. */
    fun coverageHeld(target: ListTarget, direction: FollowDirection): Boolean {
        val state = target.scan(direction)
        val run = target.snapshot?.run(direction) ?: return false
        return state.failure == null && state.attemptAt == run.latest.observedAt && when (run.coverage()) {
            FollowCoverage.LIMITED, FollowCoverage.UNKNOWN, FollowCoverage.STALLED -> true
            else -> false
        }
    }
    fun difference(target: ListTarget, direction: FollowDirection): ListDifference? {
        val before = target.previous?.run(direction) ?: return null
        val after = target.snapshot?.run(direction) ?: return null
        // Reaching the observed end is necessary, not a guarantee of an atomic/complete Instagram snapshot.
        if (!before.comparable() || !after.comparable() ||
            before.latest.observedAt >= after.pages.first().observedAt) return null
        val old = before.users.map { it.id }.toSet(); val current = after.users.map { it.id }.toSet()
        return ListDifference(target.target, direction, before.latest.observedAt, after.latest.observedAt,
            after.users.filter { it.id !in old }, before.users.filter { it.id !in current })
    }
    fun record(target: ListTarget, session: Session, page: FollowPage): ListTarget {
        var previous = target.previous
        val old = target.snapshot?.run(page.direction)
        if (page.inputCursor == null && old?.comparable() == true) {
            val base = previous ?: FollowSnapshot(session.userId, session.connectionBinding, target.target)
            previous = if (page.direction == FollowDirection.FOLLOWERS) base.copy(followers = old) else base.copy(following = old)
        }
        val snapshot = (target.snapshot ?: FollowSnapshot(session.userId, session.connectionBinding, target.target)).record(page, target.scan(page.direction).basisCount)
        val more = snapshot.run(page.direction)!!.coverage() == FollowCoverage.MORE
        return target.copy(snapshot = snapshot, previous = previous).withScan(page.direction,
            target.scan(page.direction).copy(nextAt = page.observedAt + if (more) MonitorPolicy.POLL_MS else RESCAN_MS,
                attemptAt = page.observedAt, continuing = more, failure = null))
    }
}

sealed interface AutomaticTask {
    val target: String
    val dueAt: Long
    data class Person(override val target: String, override val dueAt: Long) : AutomaticTask
    data class Own(override val target: String, override val dueAt: Long) : AutomaticTask
    data class Page(override val target: String, override val dueAt: Long, val direction: FollowDirection,
        val cursor: String?, val previousObservation: Long?, val basis: ListBasis? = null) : AutomaticTask
}

object AutomaticQueue {
    /** Only the unchanged MORE run committed by this round can bypass its next-job deadline. */
    fun page(target: ListTarget, direction: FollowDirection, now: Long, continuationOf: FollowRun? = null,
        basis: ListBasis? = null): AutomaticTask.Page? {
        val state = target.scan(direction)
        if (state.failure == ApiFailure.Kind.CAPACITY || ListAutomationPolicy.coverageHeld(target, direction)) return null
        val run = target.snapshot?.run(direction)
        val continuingRound = continuationOf != null && state.continuing && run == continuationOf && run.coverage() == FollowCoverage.MORE
        val changed = !state.continuing && state.failure == null && basis != null &&
            basis.observedAt > (if (state.basisCount == null) run?.latest?.observedAt ?: 0 else state.basisAt) &&
            (state.basisCount == null || basis.count != state.basisCount)
        val dueAt = if (changed) minOf(state.nextAt, basis!!.observedAt) else state.nextAt
        if (dueAt > now && !continuingRound) return null
        return AutomaticTask.Page(target.target, dueAt, direction, if (state.continuing && run != null && now - run.latest.observedAt < ListAutomationPolicy.RESCAN_MS) run.cursor() else null,
            run?.latest?.observedAt, basis)
    }
    fun next(account: SavedAccount, archive: ListArchive, now: Long, preferProfile: Boolean = false,
        ownIntervalMillis: Long = ListAutomationPolicy.OWN_PROFILE_MS, ownEnabled: Boolean = false): AutomaticTask? {
        require(archive.matches(account.session))
        require(ownIntervalMillis > 0)
        val tasks = account.people.filter { it.watching }.map { AutomaticTask.Person(it.latest.userId, it.nextCheckAt) }.toMutableList<AutomaticTask>()
        if (ownEnabled || account.automation.enabled && account.session.userId in account.automation.targets)
            tasks += AutomaticTask.Own(account.session.userId,
                maxOf(account.automation.ownNextCheckAt, account.profile.observedAt + ownIntervalMillis))
        if (account.automation.enabled) {
            for (id in account.automation.targets) for (direction in FollowDirection.entries) {
                val target = archive.targets.firstOrNull { it.target == id } ?: ListTarget(id)
                val profile = if (id == account.profile.userId) account.profile else account.people.first { it.latest.userId == id }.latest
                page(target, direction, now, basis = ListBasis.of(profile, direction))?.let { tasks += it }
            }
        }
        val ready = tasks.filter { it.dueAt <= now }
        val page = ready.filterIsInstance<AutomaticTask.Page>().minByOrNull { it.dueAt }
        val profile = ready.filterNot { it is AutomaticTask.Page }.minByOrNull { it.dueAt }
        if (preferProfile && profile != null) return profile
        // A large first scan must not starve profile watches (or vice versa) under the shared budget.
        return if (page != null && profile != null) { if (account.automation.lastWorkWasList) profile else page }
            else page ?: profile
    }
}
