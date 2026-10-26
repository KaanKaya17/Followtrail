package dev.kaankaya.instagramtracker

object WatchPreference {
    fun set(person: TrackedPerson, minutes: Int, enabled: Boolean, now: Long = System.currentTimeMillis()): TrackedPerson = person.copy(
        watching = enabled, intervalMinutes = minutes,
        nextCheckAt = if (enabled) maxOf(now, person.latest.observedAt + WatchIntervals.durationMs(minutes)) else 0)
}
