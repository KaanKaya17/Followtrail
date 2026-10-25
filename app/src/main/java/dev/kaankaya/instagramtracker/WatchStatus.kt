package dev.kaankaya.instagramtracker

object WatchIntervals {
    val minutes = listOf(15, 30, 60, 360, 720, 1440)
    fun durationMs(minutes: Int): Long {
        require(minutes in this.minutes)
        return minutes * 60000L
    }
}

data class WatchConditions(val notificationsAllowed: Boolean = true, val offline: Boolean = false,
    val connectionRequired: Boolean = false, val connectionInProgress: Boolean = false,
    val blockedUntil: Long = 0, val manualOnly: Boolean = false, val automaticAt: Long = 0)

enum class WatchReason { OFF, NOTIFICATIONS_OFF, LIMITED, CONNECTION_REQUIRED, CONNECTION_IN_PROGRESS,
    MANUAL_ONLY, OFFLINE, INTERVAL, SHARED_BUDGET, QUEUED, READY }

/** Local eligibility, never a promise that Android or Instagram will complete a read at this time. */
data class WatchStatus(val reason: WatchReason, val eligibleAt: Long? = null, val ahead: Int = 0)

object WatchStatusPolicy {
    fun status(person: TrackedPerson, people: List<TrackedPerson>, conditions: WatchConditions, now: Long): WatchStatus {
        val paused = when {
            !person.watching -> WatchReason.OFF
            !conditions.notificationsAllowed -> WatchReason.NOTIFICATIONS_OFF
            conditions.blockedUntil > now -> WatchReason.LIMITED
            conditions.connectionRequired -> WatchReason.CONNECTION_REQUIRED
            conditions.connectionInProgress -> WatchReason.CONNECTION_IN_PROGRESS
            conditions.manualOnly -> WatchReason.MANUAL_ONLY
            conditions.offline -> WatchReason.OFFLINE
            else -> null
        }
        if (paused != null) return WatchStatus(paused)
        val eligible = maxOf(person.nextCheckAt, conditions.automaticAt)
        val due = MonitorPolicy.orderedWatches(people).filter { it.nextCheckAt <= now }
        val ahead = due.indexOfFirst { it.latest.userId == person.latest.userId }.coerceAtLeast(0)
        if (eligible > now) return WatchStatus(
            if (conditions.automaticAt >= person.nextCheckAt) WatchReason.SHARED_BUDGET else WatchReason.INTERVAL, eligible, ahead)
        return WatchStatus(if (ahead > 0) WatchReason.QUEUED else WatchReason.READY, eligible, ahead)
    }
}
