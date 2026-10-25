package dev.kaankaya.instagramtracker

/** One page OR one profile per Android job. Cursors and scan reservations survive process death. */
class AutomaticEngine(private val vault: SessionVault, private val lists: FollowListVault, private val gate: RequestGate,
    private val api: InstagramApi, private val source: FollowListSource, private val canNotify: () -> Boolean,
    private val notifyProfile: (ObservedProfileChange) -> Unit,
    private val notifyList: (Session, Profile, ListDifference) -> Unit) {
    private fun selected(account: SavedAccount?, session: Session, id: String) = account?.session == session &&
        account.automation.enabled && id in account.automation.targets
    fun tick(now: Long = System.currentTimeMillis(), cancelled: () -> Boolean = { false }) {
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
        task as AutomaticTask.Page
        try {
            vault.inspect { current ->
                if (cancelled() || !selected(current, saved.session, task.target)) throw ApiFailure(ApiFailure.Kind.CANCELLED)
                lists.reserve(saved.session, task, now)
            }
        } catch (failure: ApiFailure) {
            if (failure.kind == ApiFailure.Kind.CAPACITY) gate.pauseForListCapacity()
            throw failure
        }
        try {
            if (cancelled()) throw ApiFailure(ApiFailure.Kind.CANCELLED)
            val page = source.read(saved.session, task.target, task.direction, task.cursor)
            vault.inspect { current ->
                if (cancelled() || !selected(current, saved.session, task.target)) throw ApiFailure(ApiFailure.Kind.CANCELLED)
                val difference = lists.commitAutomatic(saved.session, task, now, page)
                val profile = (listOf(current!!.profile) + current.people.map { it.latest }).first { it.userId == task.target }
                if (difference != null && canNotify() && !cancelled()) notifyList(saved.session, profile, difference)
            }
        } catch (failure: ApiFailure) {
            if (failure.kind != ApiFailure.Kind.CANCELLED) vault.inspect { current ->
                if (!cancelled() && selected(current, saved.session, task.target)) {
                    try { lists.failed(saved.session, task, now, failure.kind) }
                    catch (error: ApiFailure) { if (error.kind == ApiFailure.Kind.CAPACITY) gate.pauseForListCapacity() else gate.pauseAutomatic() }
                    catch (_: Exception) { gate.pauseAutomatic() }
                }
            }
            throw failure
        }
    }
}
