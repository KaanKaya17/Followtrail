package dev.kaankaya.instagramtracker

import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SharedImageWorkTest {
    private val pools = mutableListOf<ThreadPoolExecutor>()
    private val owners = mutableListOf<SharedImageWork<String>>()
    private val holds = mutableListOf<CountDownLatch>()
    private fun hold() = CountDownLatch(1).also { holds += it }
    private fun pool(threads: Int = 2, queued: Int = 20) = ThreadPoolExecutor(threads,threads,0,
        TimeUnit.MILLISECONDS,ArrayBlockingQueue(queued)).also { pools += it }
    private fun owner(executor: ThreadPoolExecutor, dispatch: (() -> Unit) -> Boolean = { it(); true }) =
        SharedImageWork<String>(executor,dispatch,{it()}).also { owners += it }
    private fun await(latch: CountDownLatch) = assertTrue(latch.await(3,TimeUnit.SECONDS))
    @After fun cleanup() {
        holds.forEach { it.countDown() }
        owners.forEach { it.close() }; pools.forEach { it.shutdownNow() }
        pools.forEach { assertTrue(it.awaitTermination(3,TimeUnit.SECONDS)) }
    }
    @Test fun oneProducerServesTwoViewsAndCancellingOneKeepsTheOther() {
        val shared=owner(pool());val entered=CountDownLatch(1);val release=CountDownLatch(1);val done=CountDownLatch(1)
        val reads=AtomicInteger();val cancels=AtomicInteger();val firstDelivery=AtomicInteger()
        val first=shared.subscribe("100:200:url",{work->reads.incrementAndGet();work.onCancel {cancels.incrementAndGet()}
            entered.countDown();release.await(3,TimeUnit.SECONDS);"image"},{firstDelivery.incrementAndGet()})!!
        await(entered)
        val second=shared.subscribe("100:200:url",{fail("Duplicate producer");null},{assertEquals("image",it);done.countDown()})!!
        first.cancel();assertTrue(first.await(1,TimeUnit.SECONDS));assertEquals(0,cancels.get());release.countDown();await(done)
        assertEquals(1,reads.get());assertEquals(0,firstDelivery.get());assertEquals(0,cancels.get())
        assertTrue(second.await(1,TimeUnit.SECONDS));assertTrue(second.active());second.cancel();assertFalse(second.active())
    }
    @Test fun abandonedLateCompletionCannotRemoveANewerFlightForTheSameKey() {
        val executor=pool();val shared=owner(executor)
        val oldEntered=CountDownLatch(1);val oldRelease=hold();val cancelled=CountDownLatch(1)
        val freshEntered=CountDownLatch(1);val freshRelease=hold();val oldDrained=CountDownLatch(1);val done=CountDownLatch(2)
        val old=shared.subscribe("same",{work->work.onCancel {cancelled.countDown()};oldEntered.countDown()
            while(oldRelease.count>0)try {oldRelease.await(3,TimeUnit.SECONDS)}catch (_:InterruptedException){}
            "old"},{fail("Cancelled old image delivered")})!!
        await(oldEntered);old.cancel();await(cancelled)
        shared.subscribe("same",{freshEntered.countDown();freshRelease.await(3,TimeUnit.SECONDS);"fresh"},{assertEquals("fresh",it);done.countDown()})!!
        await(freshEntered);oldRelease.countDown();executor.execute {oldDrained.countDown()};await(oldDrained)
        shared.subscribe("same",{fail("New flight was removed by old completion");null},{assertEquals("fresh",it);done.countDown()})!!
        freshRelease.countDown();await(done)
    }
    @Test fun closingBeforeUiDeliverySuppressesEveryLateCallback() {
        val posted=LinkedBlockingQueue<()->Unit>();val deliveries=AtomicInteger();val shared=owner(pool(),{posted.put(it);true})
        shared.subscribe("key",{"image"},{deliveries.incrementAndGet()})!!
        val callback=posted.poll(3,TimeUnit.SECONDS)!!;shared.close();callback()
        assertEquals(0,deliveries.get());assertNull(shared.subscribe("key",{"late"},{}))
    }
    @Test fun consumerCapacityIsBoundedEvenWhenAllConsumersShareOneProducer() {
        val shared=owner(pool(1,1));val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val first=shared.subscribe("key",{work->work.onCancel {release.countDown()};entered.countDown();release.await(3,TimeUnit.SECONDS);null},{})!!
        await(entered)
        assertNotNull(shared.subscribe("key",{fail("Duplicate");null},{}))
        assertNull(shared.subscribe("key",{fail("Capacity");null},{}))
        first.cancel();shared.close();release.countDown()
    }
    @Test fun differentOwnersDoNotShareAndOneBadViewDoesNotStrandAnother() {
        val shared=owner(pool());val entered=CountDownLatch(2);val release=CountDownLatch(1);val done=CountDownLatch(2);val reads=AtomicInteger()
        fun read(work: SharedImageWork<String>.Context): String {reads.incrementAndGet();entered.countDown();release.await(3,TimeUnit.SECONDS);return "image"}
        shared.subscribe("100:200:url",::read,{throw IllegalStateException("Broken view")})!!
        shared.subscribe("100:200:url",{fail("Duplicate");null},{done.countDown()})!!
        shared.subscribe("300:200:url",::read,{done.countDown()})!!
        await(entered);release.countDown();await(done);assertEquals(2,reads.get())
    }
    @Test fun failedProducerAndRejectedDeliveryLeaveTheKeyReusable() {
        val executor=pool(1,2);val shared=owner(executor,{false});val drained=CountDownLatch(1)
        shared.subscribe("key",{throw IllegalStateException("Synthetic read failure")},{fail("Failure delivery")})!!
        executor.execute {drained.countDown()};await(drained)
        assertNotNull(shared.subscribe("key",{"image"},{fail("Rejected UI delivery")}))
    }
}
