package dev.kaankaya.instagramtracker

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class RequestControlTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences = context.getSharedPreferences("request_control_test", 0)
    private fun gate() = RequestGate(context, "request_control_test")
    @Before fun before() { check(context.packageName.endsWith(".debug")); preferences.edit().clear().commit() }
    @After fun after() { preferences.edit().clear().commit() }
    @Test fun automaticOwnRefreshIsOptInAndDoesNotOverrideAnExpiredRestriction() {
        val gate = gate(); val now = System.currentTimeMillis()
        val stale = Profile("123", "synthetic", 1, 2, now - 3600000)
        assertFalse(gate.autoAllowed(stale, now))
        gate.setOwnAutoRefresh(true); assertTrue(gate().autoAllowed(stale, now))
        gate.limited(null, now)
        assertFalse(gate().autoAllowed(stale, gate.blockedUntil() + 1))
        assertTrue(gate().restrictionPending())
        gate.succeeded(); assertTrue(gate().ownAutoRefresh()); assertFalse(gate().restrictionPending())
    }
    @Test fun manualModeAloneIsNotReportedAsAnInstagramRestriction() {
        val gate = gate(); gate.pauseAutomatic()
        assertTrue(gate.manualOnly()); assertFalse(gate.restrictionPending()); assertNull(gate.serverRetryAt())
    }
    @Test fun serverDeadlineAndLocalPauseRemainDistinctAndLongerServerDeadlineWins() {
        val now = System.currentTimeMillis(); val gate = gate()
        gate.limited(null, now)
        assertNull(gate.serverRetryAt()); assertEquals(now + 3600000, gate.blockedUntil())
        preferences.edit().clear().commit()
        gate.limited("7200", now)
        assertEquals(now + 7200000, gate.serverRetryAt()); assertEquals(now + 7200000, gate.blockedUntil())
        gate.succeeded(); assertNull(gate.serverRetryAt())
    }
    @Test fun attemptCounterSurvivesRecreationAndBlockedTapDoesNotCountAsNewAttempt() {
        val gate = gate(); val now = System.currentTimeMillis()
        gate.begin(now); gate.observedStatus(429)
        assertEquals(1L, gate().attempts()); assertEquals(429, gate().lastHttpStatus())
        assertThrows(ApiFailure::class.java) { gate().begin(now + 1) }
        assertEquals(1L, gate().attempts())
        gate().begin(now + 16000)
        assertEquals(2L, gate().attempts()); assertNull(gate().lastHttpStatus())
        assertEquals(setOf("next_read_at", "attempt_count", "last_attempt_at"), preferences.all.keys)
    }
}
