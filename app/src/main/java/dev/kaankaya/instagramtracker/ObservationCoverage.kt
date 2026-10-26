package dev.kaankaya.instagramtracker

/** The actual retained sample interval, independent of the selected chart filter or retention count. */
data class ObservationCoverage(val count: Int, val firstAt: Long?, val lastAt: Long?) {
    val spanMinutes: Long get() = if (firstAt == null || lastAt == null) 0 else (lastAt - firstAt) / 60_000
    companion object {
        fun of(observations: List<Profile>) = ObservationCoverage(observations.size,
            observations.minOfOrNull { it.observedAt }, observations.maxOfOrNull { it.observedAt })
    }
}
