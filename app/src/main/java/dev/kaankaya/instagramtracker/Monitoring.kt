package dev.kaankaya.instagramtracker

data class ObservedProfileChange(val ownerId: String, val profile: Profile, val followersDelta: Long, val followingDelta: Long, val edits: List<ProfileEdit> = emptyList())

object MonitorPolicy {
    const val POLL_MS = 15 * 60 * 1000L
    fun orderedWatches(people: List<TrackedPerson>): List<TrackedPerson> = people.filter { it.watching }.sortedBy { it.nextCheckAt }
    fun due(people: List<TrackedPerson>, now: Long): TrackedPerson? = orderedWatches(people).firstOrNull { it.nextCheckAt <= now }
    fun change(owner: String, person: TrackedPerson?, profile: Profile): ObservedProfileChange? {
        if (person == null || !person.watching) return null
        val followers = profile.followers - person.latest.followers
        val following = profile.following - person.latest.following
        val edits = if (profile.observedAt > person.latest.observedAt) ProfileChanges.latest(person.history, profile) else emptyList()
        return if (followers == 0L && following == 0L && edits.isEmpty()) null else ObservedProfileChange(owner, profile, followers, following, edits)
    }
}

data class ObservationCommit(val account: SavedAccount, val change: ObservedProfileChange?)

object Observations {
    fun deliver(vault: SessionVault, session: Session, change: ObservedProfileChange, allowed: () -> Boolean,
        notify: (ObservedProfileChange) -> Unit, cancelled: () -> Boolean = { false }) {
        vault.inspect { current ->
            if (!cancelled() && current?.session == session && current.people.any {
                    it.watching && it.latest.userId == change.profile.userId && it.latest.observedAt == change.profile.observedAt
                } && allowed()) notify(change)
        }
    }
    fun person(vault: SessionVault, session: Session, profile: Profile, existingRequired: Boolean,
        watchRequired: Boolean = false, cancelled: () -> Boolean = { false }): ObservationCommit {
        var change: ObservedProfileChange? = null
        val updated = vault.update(session) { current ->
            if (cancelled()) throw ApiFailure(ApiFailure.Kind.CANCELLED)
            val person = current.people.firstOrNull { it.latest.userId == profile.userId }
            if ((existingRequired && person == null) || (watchRequired && person?.watching != true)) throw ApiFailure(ApiFailure.Kind.CANCELLED)
            change = MonitorPolicy.change(session.userId, person, profile)
            current.copy(people = PeoplePolicy.record(current.people, profile))
        }
        return ObservationCommit(updated, change)
    }
}

/** One due profile per invocation, no retries, and the same gate used by foreground reads. */
class MonitorEngine(private val vault: SessionVault, private val gate: RequestGate, private val api: InstagramApi,
    private val canNotify: () -> Boolean, private val notify: (ObservedProfileChange) -> Unit) {
    fun tick(now: Long = System.currentTimeMillis(), cancelled: () -> Boolean = { false }) {
        if (cancelled() || !canNotify() || gate.automationHeld() || gate.manualOnly() || gate.requiresConnection() || gate.nextAllowedAt() > now) return
        val saved = vault.read() ?: return
        val selected = MonitorPolicy.due(saved.people, now) ?: return
        if (!gate.reserveAutomatic(now)) return
        // Reserve this person's interval before transport, including transient failures.
        vault.update(saved.session) { current ->
            if (cancelled()) throw ApiFailure(ApiFailure.Kind.CANCELLED)
            val person = current.people.firstOrNull { it.latest.userId == selected.latest.userId && it.watching && it.nextCheckAt <= now }
                ?: throw ApiFailure(ApiFailure.Kind.CANCELLED)
            current.copy(people = current.people.map { if (it.latest.userId == person.latest.userId)
                it.copy(nextCheckAt = now + WatchIntervals.durationMs(it.intervalMinutes)) else it })
        }
        if (cancelled()) return
        val profile = api.readId(saved.session, selected.latest.userId)
        val committed = Observations.person(vault, saved.session, profile, existingRequired = true, watchRequired = true, cancelled = cancelled)
        committed.change?.let { change ->
            Observations.deliver(vault, saved.session, change, canNotify, notify, cancelled)
        }
    }
}
