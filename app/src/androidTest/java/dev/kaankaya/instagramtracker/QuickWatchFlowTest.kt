package dev.kaankaya.instagramtracker

import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.atomic.AtomicInteger

class QuickWatchFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val vault = SessionVault(context, "quicktest")
    private val lists = FollowListVault(context, "quicklists")
    private val calls = AtomicInteger()
    private var elapsed = 100_000L
    private var now = System.currentTimeMillis()
    private val runtime = object : ReadRuntime {
        override fun now() = now
        override fun elapsed() = elapsed
        override fun pause(milliseconds: Long, cancelled: () -> Boolean) = !cancelled()
    }
    private val gate = RequestGate(context, "quick_test_gate") { now }
    private val controllers = mutableListOf<QuickWatch>()
    @Before fun setup() {
        check(context.packageName.endsWith(".debug")); calls.set(0); vault.clear(); lists.clear()
        context.getSharedPreferences("quick_test_gate", 0).edit().clear().commit()
        if (Build.VERSION.SDK_INT >= 33) android.os.ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")).use { it.readBytes() }
        ChangeNotifications(context).ensureChannel()
        val seed = PreviewMode.seed(true)!!
        vault.write(seed.copy(automation = ListAutomation(true, setOf("201")), people = seed.people.map { if (it.latest.userId == "201") it.copy(watching = true) else it }))
        val p = vault.read()!!.people.single { it.latest.userId == "201" }.latest
        now = p.observedAt + 1000
        // Both complete baselines are current, so the first tick should use the fresh profile without transport.
        for (direction in FollowDirection.entries) lists.commitCurrent(seed.session, FollowPage("201", direction, null,
            emptyList(), FollowField(true, null), FollowField(true, false), FollowField(true, false), FollowField(false, null), now), null, ListBasis.of(p, direction))
    }
    @After fun cleanup() {
        instrumentation.runOnMainSync { controllers.forEach { it.close() } }
        vault.clear(); lists.clear(); context.getSharedPreferences("quick_test_gate", 0).edit().clear().commit()
        ChangeNotifications(context).clear()
    }
    private fun controller(onChange: (ApiFailure?) -> Unit = {}) = QuickWatch(vault, lists, gate,
        { calls.incrementAndGet(); PreviewMode.api(gate, true) }, { PreviewMode.followLists(gate, true) },
        ChangeNotifications(context), onChange, runtime).also { controllers += it }
    private fun await(predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 5000
        while (!predicate() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(25)
        assertTrue(predicate())
    }
    @Test fun pauseExpiryAndRecreationNeverRestartTheFifteenMinuteLifetime() {
        val done = AtomicInteger(); val quick = controller { done.incrementAndGet() }
        instrumentation.runOnMainSync { quick.start(vault.read()!!.session, "201") }
        await { done.get() > 0 }; assertEquals(1, calls.get()); assertEquals(0L, gate.attempts())
        val state = Bundle()
        instrumentation.runOnMainSync { quick.save(state); quick.pause() }
        assertFalse(gate.quickWatchActive())
        elapsed += 120_000; now += 120_000
        Thread.sleep(1100); assertEquals(1, calls.get())
        val restored = controller()
        instrumentation.runOnMainSync { restored.restore(state, vault.read()!!) }
        assertEquals(780L, restored.secondsLeft)
        elapsed += 780_001; now += 780_001
        instrumentation.runOnMainSync { restored.resume() }
        assertFalse(restored.active); assertNull(restored.target); assertFalse(gate.quickWatchActive()); assertEquals(1, calls.get())
    }
    @Test fun restrictionStopsVisibleSessionWithoutAnAutomaticRetry() {
        val failure = java.util.concurrent.atomic.AtomicReference<ApiFailure?>()
        val quick = controller { failure.set(it) }
        instrumentation.runOnMainSync { quick.start(vault.read()!!.session, "201") }
        await { calls.get() == 1 }
        gate.limited(null); val pause = gate.blockedUntil()
        await { failure.get() != null }; assertFalse(quick.active); assertEquals(ApiFailure.Kind.LIMITED, failure.get()?.kind)
        assertEquals(0L, gate.attempts()); assertEquals(pause, gate.blockedUntil()); assertTrue(gate.manualOnly())
    }
    @Test fun activeSessionHoldsBackgroundReservationAndReleasesItOnPause() {
        val quick = controller()
        instrumentation.runOnMainSync { quick.start(vault.read()!!.session, "201") }
        assertTrue(gate.quickWatchActive()); assertFalse(gate.reserveAutomatic())
        instrumentation.runOnMainSync { quick.pause() }
        assertFalse(gate.quickWatchActive()); assertTrue(gate.reserveAutomatic())
    }
    @Test fun aDueCheckReportsTheSharedSlotDelayInsteadOfPromisingAnImmediateResult() {
        val quick = controller()
        instrumentation.runOnMainSync { quick.start(vault.read()!!.session, "201") }
        await { calls.get() == 1 && !quick.busy }
        elapsed += 60_000; now += 60_000
        gate.begin(now)
        assertEquals(0L, quick.secondsToCheck); assertTrue(quick.waitingForSlot)
        Thread.sleep(1100); assertEquals(1, calls.get())
        elapsed += 15_000; now += 15_000
        await { calls.get() == 2 }; assertFalse(quick.waitingForSlot)
    }
}
