package dev.kaankaya.instagramtracker

import org.junit.Assert.*
import org.junit.Test

class ObservationCoverageTest {
    private fun p(at: Long) = Profile("123", "owner", 1, 2, at)
    @Test fun sampleCountNeverBecomesADayCountOrSelectedFilterLength() {
        val minutes = (0 until 90).map { p(1_000_000L + it * 60_000L) }
        assertEquals(90, ObservationCoverage.of(minutes).count)
        assertEquals(89L, ObservationCoverage.of(minutes).spanMinutes)
        val sparse = ObservationCoverage.of(listOf(p(1_000_000L + 40 * 86400000L), p(1_000_000L)))
        assertEquals(2, sparse.count); assertEquals(40 * 1440L, sparse.spanMinutes)
        assertEquals(1_000_000L, sparse.firstAt)
        assertEquals(0, ObservationCoverage.of(emptyList()).count)
        assertNull(ObservationCoverage.of(emptyList()).firstAt)
        assertEquals(0L, ObservationCoverage.of(listOf(p(1_000_000L))).spanMinutes)
    }
}
