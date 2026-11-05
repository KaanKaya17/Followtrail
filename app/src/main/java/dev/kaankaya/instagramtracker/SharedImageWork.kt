package dev.kaankaya.instagramtracker

import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** One producer per image key; cancelling a consumer cannot cancel another consumer. */
internal class SharedImageWork<V>(private val executor: ThreadPoolExecutor,
    private val dispatch: (() -> Unit) -> Boolean,
    private val cancelResource: (() -> Unit) -> Unit) {
    private val lock = Any()
    private val flights = mutableMapOf<String, Flight>()
    private val tickets = mutableSetOf<Ticket>()
    private var closed = false
    private val capacity = executor.maximumPoolSize + executor.queue.remainingCapacity()
    internal inner class Flight(val key: String) {
        val subscribers = linkedSetOf<Ticket>()
        var abandoned = false
        var finished = false
        var cancel: (() -> Unit)? = null
        lateinit var future: FutureTask<Unit>
    }
    inner class Context internal constructor(private val flight: Flight) {
        fun active(): Boolean = synchronized(lock) { !closed && !flight.abandoned }
        fun onCancel(action: () -> Unit) {
            val cancelled = synchronized(lock) {
                if (closed || flight.abandoned) true else { flight.cancel = action; false }
            }
            if (cancelled) cancelResource(action)
        }
        fun release() = synchronized(lock) { flight.cancel = null }
    }
    inner class Ticket internal constructor(internal val flight: Flight, val key: String, val deliver: (V) -> Unit) {
        internal var cancelled = false
        internal val completed = CountDownLatch(1)
        fun active(): Boolean = synchronized(lock) { !closed && !cancelled }
        fun cancel() = detach(this)
        /** Consumer completion only; it never exposes or cancels the shared producer. */
        fun await(timeout: Long, unit: TimeUnit) = completed.await(timeout, unit)
    }
    fun subscribe(key: String, load: (Context) -> V?, deliver: (V) -> Unit): Ticket? = synchronized(lock) {
        if (closed || tickets.size >= capacity) return null
        val existing = flights[key]
        val flight = existing ?: Flight(key)
        val ticket = Ticket(flight, key, deliver)
        flight.subscribers += ticket; tickets += ticket
        if (existing != null) return ticket
        flights[key] = flight
        flight.future = FutureTask {
            val value = try { load(Context(flight)) } catch (_: Exception) { null }
            val recipients = synchronized(lock) {
                flight.finished = true
                flight.cancel = null
                if (flights[key] === flight) flights.remove(key)
                flight.subscribers.toList()
            }
            val posted = value != null && try { dispatch {
                recipients.forEach { consumer ->
                    try {
                        val valid = synchronized(lock) { !closed && !consumer.cancelled && consumer in tickets }
                        if (valid) consumer.deliver(value)
                    } catch (_: Exception) { /* One view cannot strand another view's subscription. */
                    } finally { finish(consumer) }
                }
            } } catch (_: Exception) { false }
            if (!posted) recipients.forEach { finish(it) }
        }
        try { executor.execute(flight.future) }
        catch (_: RejectedExecutionException) { ticket.cancel(); return null }
        ticket
    }
    private fun finish(ticket: Ticket) = synchronized(lock) {
        tickets.remove(ticket); ticket.flight.subscribers.remove(ticket)
        ticket.completed.countDown()
    }
    private fun detach(ticket: Ticket) {
        val action = synchronized(lock) {
            if (ticket.cancelled) return
            ticket.cancelled = true; tickets.remove(ticket)
            ticket.completed.countDown()
            val flight = ticket.flight
            flight.subscribers.remove(ticket)
            if (flight.subscribers.isNotEmpty()) return
            flight.abandoned = true
            if (flights[flight.key] === flight) flights.remove(flight.key)
            // Completed UI delivery must not interrupt the thread delivering it.
            if (!flight.finished) { flight.future.cancel(true); executor.remove(flight.future) }
            flight.cancel.also { flight.cancel = null }
        }
        if (action != null) cancelResource(action)
    }
    fun cancelWhere(predicate: (String) -> Boolean) {
        val selected = synchronized(lock) { tickets.filter { predicate(it.key) } }
        selected.forEach { it.cancel() }
    }
    fun cancelAll() = cancelWhere { true }
    fun close() {
        synchronized(lock) { closed = true }
        cancelAll()
    }
}
