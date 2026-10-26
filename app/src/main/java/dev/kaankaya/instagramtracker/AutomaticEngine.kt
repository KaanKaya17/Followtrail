package dev.kaankaya.instagramtracker

/** One profile or a bounded, sequential list round for one target. Every committed page survives cancellation. */
class AutomaticEngine(private val vault: SessionVault, private val lists: FollowListVault, private val gate: RequestGate,
    private val api: InstagramApi, private val source: FollowListSource, private val canNotify: () -> Boolean,
    private val notifyProfile: (ObservedProfileChange) -> Unit,
    private val notifyList: (Session, Profile, ListDifference) -> Unit,
    private val runtime: ReadRuntime = SystemReadRuntime) {
    companion object { const val MAX_PAGES = 6; const val ROUND_MS = 90_000L }
    private fun selected(account: SavedAccount?, session: Session, id: String) = account?.session == session &&
        account.automation.enabled && id in account.automation.targets
    fun tick(now: Long = runtime.now(), cancelled: () -> Boolean = { false }) {
        if (cancelled() || !canNotify() || gate.automationHeld() || gate.manualOnly() || gate.requiresConnection() || gate.automaticNextAllowedAt() > now) return
        val saved = vault.read() ?: return
        if (!saved.automation.enabled) {
            MonitorEngine(vault, gate, api, canNotify, notifyProfile).tick(now, cancelled); return
        }
        val task = AutomaticQueue.next(saved, lists.archive(saved.session), now) ?: return
        if (task is AutomaticTask.Person) {
            vault.update(saved.session) { current ->
                if (cancelled() || !current.automation.enabled) throw ApiFailure(ApiFailure.Kind.CANCELLED)
                current.copy(automation = current.automation.copy(lastWorkWasList = false))
            }
            MonitorEngine(vault, gate, api, canNotify, notifyProfile).tick(now, cancelled, task.target)
            return
        }
        if (!gate.reserveAutomatic(now)) return
        vault.update(saved.session) { current ->
            if (cancelled() || !selected(current, saved.session, task.target)) throw ApiFailure(ApiFailure.Kind.CANCELLED)
            current.copy(automation = current.automation.copy(lastWorkWasList = task is AutomaticTask.Page,
                ownNextCheckAt = if (task is AutomaticTask.Own) now + ListAutomationPolicy.OWN_PROFILE_MS else current.automation.ownNextCheckAt))
        }
        if (task is AutomaticTask.Own) {
            if (cancelled()) return
            val profile = api.read(saved.session)
            var change: ObservedProfileChange? = null
            val committed = vault.update(saved.session) { current ->
                if (cancelled() || !selected(current, saved.session, task.target)) throw ApiFailure(ApiFailure.Kind.CANCELLED)
                if (profile.observedAt <= current.profile.observedAt) throw ApiFailure(ApiFailure.Kind.CANCELLED)
                change = MonitorPolicy.change(saved.session.userId, TrackedPerson(listOf(current.profile), watching = true), profile)
                current.copy(profile = profile)
            }
            change?.let { notice -> vault.inspect { current ->
                if (!cancelled() && selected(current, saved.session, task.target) && current?.profile == committed.profile && canNotify()) notifyProfile(notice)
            } }
            return
        }
        readRound(saved.session, task as AutomaticTask.Page, now, cancelled)
    }
    private fun readRound(session: Session, first: AutomaticTask.Page, now: Long, cancelled: () -> Boolean) {
        val started = runtime.elapsed()
        val committed = mutableMapOf<FollowDirection, FollowRun>()
        var task = first
        var attemptAt = now
        repeat(MAX_PAGES) { index ->
            val result = readPage(session, task, attemptAt, committed[task.direction], cancelled)
            val run = result.target.snapshot!!.run(task.direction)!!
            committed[task.direction] = run
            if (run.coverage() !in setOf(FollowCoverage.MORE, FollowCoverage.END_OBSERVED) || index + 1 == MAX_PAGES) return
            if (FollowDirection.entries.none { AutomaticQueue.page(result.target, it, runtime.now(), committed[it]) != null }) return
            // Poll in short cancellable slices on the job's worker, never on the UI thread.
            while (true) {
                if (cancelled() || !canNotify() || gate.automationHeld() || gate.manualOnly() || gate.requiresConnection() ||
                    runtime.elapsed() - started >= ROUND_MS) return
                val remaining = gate.nextAllowedAt() - runtime.now()
                if (remaining > 0) {
                    if (!runtime.pause(minOf(250L, remaining), cancelled)) return
                    continue
                }
                val current = vault.read()
                if (!selected(current, session, first.target)) return
                val target = lists.target(session, first.target) ?: return
                if (committed.any { (direction, expected) -> target.snapshot?.run(direction) != expected }) return
                val opposite = FollowDirection.entries.first { it != task.direction }
                val next = listOf(opposite, task.direction).firstNotNullOfOrNull { direction ->
                    AutomaticQueue.page(target, direction, runtime.now(), committed[direction])
                } ?: return
                task = next; attemptAt = runtime.now(); break
            }
        }
    }
    private fun readPage(session: Session, task: AutomaticTask.Page, now: Long, continuationOf: FollowRun?,
        cancelled: () -> Boolean): ListPageCommit {
        try {
            vault.inspect { current ->
                if (cancelled() || !canNotify() || !selected(current, session, task.target)) throw ApiFailure(ApiFailure.Kind.CANCELLED)
                lists.reserve(session, task, now, continuationOf)
            }
        } catch (failure: ApiFailure) {
            if (failure.kind == ApiFailure.Kind.CAPACITY) gate.pauseForListCapacity()
            throw failure
        }
        try {
            if (cancelled()) throw ApiFailure(ApiFailure.Kind.CANCELLED)
            val page = source.read(session, task.target, task.direction, task.cursor)
            var committed: ListPageCommit? = null
            vault.inspect { current ->
                if (cancelled() || !selected(current, session, task.target)) throw ApiFailure(ApiFailure.Kind.CANCELLED)
                val result = lists.commitAutomatic(session, task, now, page)
                val profile = (listOf(current!!.profile) + current.people.map { it.latest }).first { it.userId == task.target }
                result.difference?.let { if (canNotify() && !cancelled()) notifyList(session, profile, it) }
                committed = result
            }
            return committed!!
        } catch (failure: ApiFailure) {
            if (failure.kind !in setOf(ApiFailure.Kind.CANCELLED, ApiFailure.Kind.TOO_SOON)) vault.inspect { current ->
                if (!cancelled() && selected(current, session, task.target)) {
                    try { lists.failed(session, task, now, failure.kind) }
                    catch (error: ApiFailure) { if (error.kind == ApiFailure.Kind.CAPACITY) gate.pauseForListCapacity() else gate.pauseAutomatic() }
                    catch (_: Exception) { gate.pauseAutomatic() }
                }
            }
            throw failure
        }
    }
}
