package dev.kaankaya.instagramtracker

data class ObservedProfileChange(val ownerId: String, val profile: Profile, val followersDelta: Long, val followingDelta: Long,
    val edits: List<ProfileEdit> = emptyList(), val previousAt: Long? = null, val postsDelta: Long? = null)

object MonitorPolicy {
    const val POLL_MS = 15 * 60 * 1000L
    fun orderedWatches(people: List<TrackedPerson>): List<TrackedPerson> = people.filter { it.watching }.sortedBy { it.nextCheckAt }
    fun due(people: List<TrackedPerson>, now: Long): TrackedPerson? = orderedWatches(people).firstOrNull { it.nextCheckAt <= now }
    fun change(owner: String, person: TrackedPerson?, profile: Profile): ObservedProfileChange? {
        if (person == null || !person.watching) return null
        val followers = profile.followers - person.latest.followers
        val following = profile.following - person.latest.following
        val edits = if (profile.observedAt > person.latest.observedAt) ProfileChanges.latest(person.history, profile) else emptyList()
        val posts = person.latest.posts?.let { before -> profile.posts?.let { it-before } }?.takeIf { it != 0L }
        return if (followers == 0L && following == 0L && edits.isEmpty() && posts == null) null else ObservedProfileChange(owner, profile, followers, following, edits, minOf(person.latest.observedAt, edits.minOfOrNull { it.previousAt } ?: person.latest.observedAt),posts)
    }
}

data class ObservationCommit(val account: SavedAccount, val change: ObservedProfileChange?)

object Observations {
    fun own(vault: SessionVault, session: Session, profile: Profile, startedAt: Long? = null,
        clock: () -> Long = System::currentTimeMillis, cancelled: () -> Boolean = { false }): ObservationCommit {
        var before: Profile? = null
        var change: ObservedProfileChange? = null
        val updated = vault.update(session) { current ->
            if (cancelled()) throw ApiFailure(ApiFailure.Kind.CANCELLED)
            require(profile.userId == session.userId)
            if (profile.observedAt <= current.profile.observedAt) throw ApiFailure(ApiFailure.Kind.CANCELLED)
            before = current.profile
            change = MonitorPolicy.change(session.userId, TrackedPerson(listOf(current.profile), watching = true), profile)
            current.copy(profile = profile)
        }
        vault.record(session, CheckEvent(profile.userId, profile.username, CheckKind.PROFILE, startedAt, profile.observedAt,
            clock(), before?.observedAt, before?.let { profile.followers - it.followers }, before?.let { profile.following - it.following },
            change?.edits?.map { it.field }?.toSet().orEmpty(), beforeFollowers = before?.followers, afterFollowers = profile.followers,
            beforeFollowing = before?.following, afterFollowing = profile.following, beforePosts = before?.posts,
            afterPosts = profile.posts, edits = change?.edits.orEmpty()))
        return ObservationCommit(updated, change)
    }
    fun deliver(vault: SessionVault, session: Session, change: ObservedProfileChange, allowed: () -> Boolean,
        notify: (ObservedProfileChange) -> Unit, cancelled: () -> Boolean = { false }) {
        vault.inspect { current ->
            if (!cancelled() && current?.session == session && current.people.any {
                    it.watching && it.latest.userId == change.profile.userId && it.latest.observedAt == change.profile.observedAt
                } && allowed()) notify(change)
        }
    }
    fun person(vault: SessionVault, session: Session, profile: Profile, existingRequired: Boolean,
        watchRequired: Boolean = false, cancelled: () -> Boolean = { false }, startedAt: Long? = null,
        analyzedAt: () -> Long = System::currentTimeMillis): ObservationCommit {
        var change: ObservedProfileChange? = null
        var before: Profile? = null
        var fields = emptySet<ProfileChangeField>()
        var comparisonAt: Long? = null
        var edits = emptyList<ProfileEdit>()
        val updated = vault.update(session) { current ->
            if (cancelled()) throw ApiFailure(ApiFailure.Kind.CANCELLED)
            val person = current.people.firstOrNull { it.latest.userId == profile.userId }
            before = person?.latest
            if ((existingRequired && person == null) || (watchRequired && person?.watching != true)) throw ApiFailure(ApiFailure.Kind.CANCELLED)
            val observedChange = MonitorPolicy.change(session.userId, person?.copy(watching = true), profile)
            change = observedChange.takeIf { person?.watching == true }
            fields = observedChange?.edits?.map { it.field }?.toSet().orEmpty()
            edits = observedChange?.edits.orEmpty()
            comparisonAt = observedChange?.previousAt ?: before?.observedAt
            current.copy(people = PeoplePolicy.record(current.people, profile))
        }
        vault.record(session, CheckEvent(profile.userId, profile.username, CheckKind.PROFILE, startedAt, profile.observedAt,
            analyzedAt(), comparisonAt, before?.let { profile.followers - it.followers }, before?.let { profile.following - it.following },
            fields, beforeFollowers = before?.followers, afterFollowers = profile.followers, beforeFollowing = before?.following,
            afterFollowing = profile.following, beforePosts = before?.posts, afterPosts = profile.posts,
            edits = edits))
        return ObservationCommit(updated, change)
    }
}

/** One due profile per invocation, no retries, and the same gate used by foreground reads. */
class MonitorEngine(private val vault: SessionVault, private val gate: RequestGate, private val api: InstagramApi,
    private val canNotify: () -> Boolean, private val notify: (ObservedProfileChange) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis) {
    fun tick(now: Long = System.currentTimeMillis(), cancelled: () -> Boolean = { false }, targetId: String? = null,
        reserved: Boolean = false) {
        if (cancelled() || !canNotify() || gate.automationHeld() || gate.manualOnly() || gate.requiresConnection() || gate.nextAllowedAt() > now || gate.profileBudgetNextAt(now) > now) return
        val saved = vault.read() ?: return
        val selected = if (targetId == null) MonitorPolicy.due(saved.people, now) else
            saved.people.firstOrNull { it.latest.userId == targetId && it.watching && it.nextCheckAt <= now }
        if (selected == null) return
        if (!reserved && !gate.reserveAutomatic(now)) return
        // Reserve this person's interval before transport, including transient failures.
        vault.update(saved.session) { current ->
            if (cancelled()) throw ApiFailure(ApiFailure.Kind.CANCELLED)
            val person = current.people.firstOrNull { it.latest.userId == selected.latest.userId && it.watching && it.nextCheckAt <= now }
                ?: throw ApiFailure(ApiFailure.Kind.CANCELLED)
            current.copy(people = current.people.map { if (it.latest.userId == person.latest.userId)
                it.copy(nextCheckAt = now + WatchIntervals.durationMs(it.intervalMinutes)) else it })
        }
        if (cancelled()) return
        try {
            val profile = api.readId(saved.session, selected.latest.userId)
            val committed = Observations.person(vault, saved.session, profile, existingRequired = true, watchRequired = true, cancelled = cancelled,
                startedAt = now, analyzedAt = clock)
            committed.change?.let { change ->
                Observations.deliver(vault, saved.session, change, canNotify, notify, cancelled)
            }
        } catch (failure: ApiFailure) {
            vault.recordFailure(saved.session, selected.latest.userId, now, failure, clock()); throw failure
        }
    }
}
