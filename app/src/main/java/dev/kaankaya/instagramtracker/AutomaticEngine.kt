package dev.kaankaya.instagramtracker

/** Bounded profile/list jobs and visible quick sessions. Every committed page survives cancellation. */
class AutomaticEngine(private val vault: SessionVault, private val lists: FollowListVault, private val gate: RequestGate,
    private val api: InstagramApi, private val source: FollowListSource, private val canNotify: () -> Boolean,
    private val notifyProfile: (ObservedProfileChange) -> Unit,
    private val notifyList: (Session, Profile, ListDifference) -> Unit,
    private val runtime: ReadRuntime = SystemReadRuntime) {
    companion object { const val MAX_PAGES = 6; const val ROUND_MS = 90_000L }
    private fun next(saved: SavedAccount, now: Long, preferProfile: Boolean = false) = AutomaticQueue.next(
        saved, lists.archive(saved.session), now, preferProfile, gate.monitorIntervalMillis(), gate.backgroundOwnEnabled(),
        gate.profileBudgetNextAt(now))
    private fun selected(account: SavedAccount?, session: Session, id: String) = account?.session == session &&
        (account.automation.enabled && id in account.automation.targets || id == account.profile.userId && gate.backgroundOwnEnabled())
    fun tick(now: Long = runtime.now(), cancelled: () -> Boolean = { false }) {
        dispatch(now, cancelled, false, MAX_PAGES, runtime.elapsed())
    }
    /** One account's turn inside the shared, already-reserved multi-account job. */
    internal fun reservedTurn(started: Long, cancelled: () -> Boolean) {
        dispatch(runtime.now(),cancelled,true,1,started,preferProfile=true)
    }
    /** One shared reservation per bounded job, rather than a 15-minute gap between each due person. */
    fun cycle(now: Long = runtime.now(), cancelled: () -> Boolean = { false }) {
        val interrupted = { cancelled() || gate.quickWatchActive() }
        if (!permitted(cancelled) || gate.quickWatchActive() || gate.preparationActive() || gate.automaticNextAllowedAt() > now) return
        val saved = vault.read() ?: return
        if (next(saved, now) == null) return
        if (!gate.reserveAutomatic(now)) return
        val started = runtime.elapsed(); val attempts = gate.attempts()
        repeat(MAX_PAGES) {
            val current = vault.read() ?: return
            if (current.session != saved.session || next(current, runtime.now()) == null) return
            if (!waitForSlot(started, interrupted)) return
            val remaining = MAX_PAGES - (gate.attempts() - attempts).coerceAtMost(MAX_PAGES.toLong()).toInt()
            if (remaining <= 0) return
            val before = gate.attempts()
            val continueRound = dispatch(runtime.now(), interrupted, true, remaining, started, before == attempts)
            if (!continueRound || gate.attempts() == before) return
        }
    }
    private fun permitted(cancelled: () -> Boolean) = !cancelled() && !Thread.currentThread().isInterrupted && canNotify() &&
        !gate.automationHeld() && !gate.manualOnly() && !gate.requiresConnection()
    private fun waitForSlot(started: Long, cancelled: () -> Boolean): Boolean {
        while (permitted(cancelled) && runtime.elapsed() - started < ROUND_MS) {
            if (gate.budgetNextAt() > runtime.now()) return false
            val wait = gate.nextAllowedAt() - runtime.now()
            if (wait <= 0) return true
            if (!runtime.pause(minOf(wait, 250L), cancelled)) return false
        }
        return false
    }
    private fun dispatch(now: Long, cancelled: () -> Boolean, reserved: Boolean, pages: Int, started: Long, preferProfile: Boolean = false): Boolean {
        if (!permitted(cancelled) || (if (reserved) gate.nextAllowedAt() else gate.automaticNextAllowedAt()) > now) return false
        val saved = vault.read() ?: return false
        if (!saved.automation.enabled && !gate.backgroundOwnEnabled()) {
            MonitorEngine(vault, gate, api, canNotify, notifyProfile, runtime::now).tick(now, cancelled, reserved = reserved); return true
        }
        val task = next(saved, now, preferProfile) ?: return false
        try {
            if (task is AutomaticTask.Person) {
                vault.update(saved.session) { current ->
                    if (cancelled() || !current.automation.enabled && !gate.backgroundOwnEnabled()) throw ApiFailure(ApiFailure.Kind.CANCELLED)
                    current.copy(automation = current.automation.copy(lastWorkWasList = false))
                }
                MonitorEngine(vault, gate, api, canNotify, notifyProfile, runtime::now).tick(now, cancelled, task.target, reserved)
                return true
            }
            if (!reserved && !gate.reserveAutomatic(now)) return false
            vault.update(saved.session) { current ->
                if (cancelled() || !selected(current, saved.session, task.target)) throw ApiFailure(ApiFailure.Kind.CANCELLED)
                current.copy(automation = current.automation.copy(lastWorkWasList = task is AutomaticTask.Page,
                    ownNextCheckAt = if (task is AutomaticTask.Own) now + gate.monitorIntervalMillis() else current.automation.ownNextCheckAt))
            }
            if (task is AutomaticTask.Own) {
                if (cancelled()) return false
                val profile = api.read(saved.session)
                val committed = Observations.own(vault, saved.session, profile, now, runtime::now) {
                    cancelled() || !selected(vault.read(), saved.session, task.target)
                }
                committed.change?.let { notice -> vault.inspect { current ->
                    if (!cancelled() && selected(current, saved.session, task.target) && current?.profile == committed.account.profile && canNotify()) notifyProfile(notice)
                } }
                return true
            }
            return readRound(saved.session, task as AutomaticTask.Page, now, cancelled, pages, started)
        } catch (failure: ApiFailure) {
            if (task !is AutomaticTask.Person) recordFailure(saved.session, task.target, now, failure)
            throw failure
        }
    }
    /** Explicit foreground session: only its target, 60s profile freshness, same HTTP and restriction gate. */
    fun quickCycle(session: Session, target: String, cancelled: () -> Boolean = { false }) {
        if (!permitted(cancelled)) throw ApiFailure(
            if (!cancelled() && gate.restrictionPending()) ApiFailure.Kind.LIMITED else ApiFailure.Kind.CANCELLED)
        val initial = vault.read() ?: throw ApiFailure(ApiFailure.Kind.CANCELLED)
        if (!selected(initial, session, target)) throw ApiFailure(ApiFailure.Kind.CANCELLED)
        val before = if (target == session.userId) initial.profile else initial.people.first { it.latest.userId == target }.latest
        val started = runtime.elapsed(); val now = runtime.now(); val attempts = gate.attempts()
        try {
            if (before.observedAt > now || now - before.observedAt >= QuickWatchTiming.INTERVAL_MS) {
                if (!waitForSlot(started, cancelled)) return
                val requestStarted = runtime.now()
                val profile = if (target == session.userId) api.read(session) else api.readId(session, target)
                val committed = if (target == session.userId) Observations.own(vault, session, profile, requestStarted, runtime::now) {
                    cancelled() || !selected(vault.read(), session, target)
                } else Observations.person(vault, session, profile, true, true,
                    { cancelled() || !selected(vault.read(), session, target) }, requestStarted, runtime::now)
                committed.change?.let { notice ->
                    if (target == session.userId) vault.inspect { current ->
                        if (permitted(cancelled) && selected(current, session, target) && current?.profile == committed.account.profile) notifyProfile(notice)
                    } else Observations.deliver(vault, session, notice, canNotify, notifyProfile, cancelled)
                }
            }
            val saved = vault.read() ?: return
            if (!selected(saved, session, target)) return
            val profile = if (target == session.userId) saved.profile else saved.people.first { it.latest.userId == target }.latest
            val record = lists.target(session, target) ?: ListTarget(target)
            val page = FollowDirection.entries.firstNotNullOfOrNull { direction ->
                AutomaticQueue.page(record, direction, runtime.now(), basis = ListBasis.of(profile, direction))
            } ?: return
            if (!waitForSlot(started, cancelled)) return
            val remaining = MAX_PAGES - (gate.attempts() - attempts).coerceAtMost(MAX_PAGES.toLong()).toInt()
            if (remaining > 0 && !readRound(session, page, runtime.now(), cancelled, remaining, started))
                throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
        } catch (failure: ApiFailure) { recordFailure(session, target, now, failure); throw failure }
    }
    private fun recordFailure(session: Session, target: String, started: Long, failure: ApiFailure) {
        vault.recordFailure(session, target, started, failure, runtime.now())
    }
    private fun readRound(session: Session, first: AutomaticTask.Page, now: Long, cancelled: () -> Boolean,
        pages: Int = MAX_PAGES, started: Long = runtime.elapsed()): Boolean {
        val committed = mutableMapOf<FollowDirection, FollowRun>()
        var task = first
        var attemptAt = now
        repeat(pages) { index ->
            val result = readPage(session, task, attemptAt, committed[task.direction], cancelled)
            val run = result.target.snapshot!!.run(task.direction)!!
            committed[task.direction] = run
            if (run.coverage() !in setOf(FollowCoverage.MORE, FollowCoverage.END_OBSERVED)) return false
            if (index + 1 == pages) return true
            val saved = vault.read() ?: return true
            val profile = if (first.target == saved.profile.userId) saved.profile else saved.people.firstOrNull { it.latest.userId == first.target }?.latest ?: return true
            if (FollowDirection.entries.none { AutomaticQueue.page(result.target, it, runtime.now(), committed[it], ListBasis.of(profile, it)) != null }) return true
            // Poll in short cancellable slices on the job's worker, never on the UI thread.
            while (true) {
                if (cancelled() || !canNotify() || gate.automationHeld() || gate.manualOnly() || gate.requiresConnection() ||
                    runtime.elapsed() - started >= ROUND_MS) return true
                val remaining = gate.nextAllowedAt() - runtime.now()
                if (remaining > 0) {
                    if (!runtime.pause(minOf(250L, remaining), cancelled)) return true
                    continue
                }
                val current = vault.read()
                if (!selected(current, session, first.target)) return true
                val target = lists.target(session, first.target) ?: return true
                if (committed.any { (direction, expected) -> target.snapshot?.run(direction) != expected }) return true
                val opposite = FollowDirection.entries.first { it != task.direction }
                val next = listOf(opposite, task.direction).firstNotNullOfOrNull { direction ->
                    val p = if (first.target == current!!.profile.userId) current.profile else current.people.first { it.latest.userId == first.target }.latest
                    AutomaticQueue.page(target, direction, runtime.now(), committed[direction], ListBasis.of(p, direction))
                } ?: return true
                task = next; attemptAt = runtime.now(); break
            }
        }
        return true
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
                val run = result.target.snapshot!!.run(task.direction)!!
                val comparison = ListAutomationPolicy.difference(result.target, task.direction)
                vault.record(session, CheckEvent(profile.userId, profile.username, CheckKind.LIST, now, page.observedAt,
                    runtime.now(), result.target.previous?.run(task.direction)?.latest?.observedAt, direction = task.direction,
                    coverage = run.coverage(), pages = run.pages.size, accounts = run.users.size,
                    added = comparison?.added?.size, missing = comparison?.missing?.size,
                    newlySeen = CheckEvent.sample(comparison?.added.orEmpty()), notSeen = CheckEvent.sample(comparison?.missing.orEmpty())))
                committed = result
            }
            return committed!!
        } catch (failure: ApiFailure) {
            if (failure.kind !in setOf(ApiFailure.Kind.CANCELLED, ApiFailure.Kind.TOO_SOON, ApiFailure.Kind.BUDGET)) vault.inspect { current ->
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
