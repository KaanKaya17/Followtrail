package dev.kaankaya.instagramtracker

import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ForegroundReadQueueTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences = context.getSharedPreferences("queue_test", 0)
    private val gate = RequestGate(context, "queue_test")
    private var queue: ForegroundReadQueue? = null
    @Before fun setup() { check(context.packageName.endsWith(".debug")); preferences.edit().clear().commit() }
    @After fun cleanup() { instrumentation.runOnMainSync { queue?.cancel(false) }; preferences.edit().clear().commit() }
    @Test fun aShortWaitRunsOneExplicitActionOnTheUiThread() {
        val done = CountDownLatch(1); val count = AtomicInteger()
        preferences.edit().putLong("next_read_at", System.currentTimeMillis() + 500).commit()
        instrumentation.runOnMainSync {
            queue = ForegroundReadQueue(gate) { fail("Queue unexpectedly stopped") }
            assertTrue(queue!!.enqueue { assertEquals(Looper.getMainLooper(), Looper.myLooper()); count.incrementAndGet(); done.countDown() })
            assertTrue(queue!!.enqueue { fail("A second action replaced the first") })
        }
        assertTrue(done.await(5, TimeUnit.SECONDS)); assertEquals(1, count.get()); assertEquals(0L, gate.attempts())
    }
    @Test fun cancellationDoesNotRunTheActionAfterTheDeadline() {
        val done = CountDownLatch(1)
        preferences.edit().putLong("next_read_at", System.currentTimeMillis() + 150).commit()
        instrumentation.runOnMainSync { queue = ForegroundReadQueue(gate) {}; assertTrue(queue!!.enqueue { done.countDown() }); queue!!.cancel(false) }
        assertFalse(done.await(350, TimeUnit.MILLISECONDS)); assertEquals(0L, gate.attempts())
    }
    @Test fun aNew429StopsAPendingActionAndAnExistingRestrictionCannotBeQueued() {
        val stopped = CountDownLatch(1); val count = AtomicInteger()
        preferences.edit().putLong("next_read_at", System.currentTimeMillis() + 300).commit()
        instrumentation.runOnMainSync { queue = ForegroundReadQueue(gate) { stopped.countDown() }; assertTrue(queue!!.enqueue { count.incrementAndGet() }) }
        gate.limited(null); val pause = gate.blockedUntil()
        assertTrue(stopped.await(5, TimeUnit.SECONDS)); assertEquals(0, count.get())
        instrumentation.runOnMainSync { assertFalse(queue!!.enqueue { fail("Restricted action") }) }
        assertEquals(pause, gate.blockedUntil()); assertTrue(gate.manualOnly()); assertEquals(0L, gate.attempts())
    }
}
