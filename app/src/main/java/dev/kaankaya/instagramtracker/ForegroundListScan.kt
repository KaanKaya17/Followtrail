package dev.kaankaya.instagramtracker

/** One user-requested scan. Pages commit immediately; only that unchanged run may continue. */
class ForegroundListScan(private val vault: SessionVault, private val lists: FollowListVault,
    private val gate: RequestGate, private val source: FollowListSource, private val runtime: ReadRuntime = SystemReadRuntime) {
    fun read(session: Session, target: String, direction: FollowDirection, firstCursor: String?, pages: Int = 6,
        cancelled: () -> Boolean, progress: (FollowSnapshot) -> Unit,
        notify: (SavedAccount, ListDifference) -> Unit = { _, _ -> }): FollowSnapshot {
        require(pages in 1..6)
        val started = runtime.elapsed()
        var cursor = firstCursor
        var expected = lists.read(session, target)?.run(direction)
        var result = lists.read(session, target)
        fun current(saved: SavedAccount?) = !cancelled() && saved?.session == session &&
            (saved.profile.userId == target || saved.people.any { it.latest.userId == target })
        repeat(pages) { index ->
            while (true) {
                if (cancelled() || Thread.currentThread().isInterrupted) throw ApiFailure(ApiFailure.Kind.CANCELLED)
                if (gate.automationHeld() || gate.requiresConnection()) throw ApiFailure(ApiFailure.Kind.CANCELLED)
                if (gate.blockedUntil() > runtime.now()) throw ApiFailure(ApiFailure.Kind.LIMITED)
                if (index > 0 && runtime.elapsed() - started >= 90_000) return result!!
                val wait = gate.nextAllowedAt() - runtime.now()
                if (wait <= 0) break
                if (index == 0) throw ApiFailure(ApiFailure.Kind.TOO_SOON)
                if (!runtime.pause(minOf(wait, 250L), cancelled)) throw ApiFailure(ApiFailure.Kind.CANCELLED)
            }
            vault.inspect { if (!current(it)) throw ApiFailure(ApiFailure.Kind.CANCELLED) }
            if (lists.read(session, target)?.run(direction) != expected) throw ApiFailure(ApiFailure.Kind.CANCELLED)
            val startedAt = runtime.now()
            val page = source.read(session, target, direction, cursor)
            if (page.target != target || page.direction != direction || page.inputCursor != cursor) throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
            vault.inspect { saved ->
                if (!current(saved)) throw ApiFailure(ApiFailure.Kind.CANCELLED)
                val profile = if (target == saved!!.profile.userId) saved.profile else saved.people.first { it.latest.userId == target }.latest
                result = lists.commitCurrent(session, page, expected, ListBasis.of(profile, direction))
                val entry = lists.target(session, target)!!
                val difference = ListAutomationPolicy.difference(entry, direction)
                val run = result!!.run(direction)!!
                vault.record(session, CheckEvent(target, profile.username, CheckKind.LIST, startedAt, page.observedAt, runtime.now(),
                    entry.previous?.run(direction)?.latest?.observedAt, direction = direction, coverage = run.coverage(),
                    pages = run.pages.size, accounts = run.users.size, added = difference?.added?.size, missing = difference?.missing?.size,
                    newlySeen = CheckEvent.sample(difference?.added.orEmpty()), notSeen = CheckEvent.sample(difference?.missing.orEmpty())))
                if (saved.automation.enabled && target in saved.automation.targets) {
                    difference?.takeIf { it.added.isNotEmpty() || it.missing.isNotEmpty() }?.let { notify(saved, it) }
                }
            }
            progress(result!!)
            expected = result!!.run(direction)
            if (expected!!.coverage() != FollowCoverage.MORE || gate.manualOnly() || gate.restrictionPending()) return result!!
            cursor = expected!!.cursor()
        }
        return result!!
    }
}
