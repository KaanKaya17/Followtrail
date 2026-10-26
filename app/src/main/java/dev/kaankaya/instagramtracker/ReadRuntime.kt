package dev.kaankaya.instagramtracker

interface ReadRuntime {
    fun now(): Long
    fun elapsed(): Long
    fun pause(milliseconds: Long, cancelled: () -> Boolean): Boolean
}

internal object SystemReadRuntime : ReadRuntime {
    override fun now() = System.currentTimeMillis()
    override fun elapsed() = System.nanoTime() / 1_000_000
    override fun pause(milliseconds: Long, cancelled: () -> Boolean): Boolean {
        if (cancelled() || Thread.currentThread().isInterrupted) return false
        try { Thread.sleep(milliseconds) } catch (_: InterruptedException) { Thread.currentThread().interrupt(); return false }
        return !cancelled()
    }
}

/** Includes device sleep in the lifetime of a foreground session saved across Activity recreation. */
internal object AndroidReadRuntime : ReadRuntime {
    override fun now() = SystemReadRuntime.now()
    override fun elapsed() = android.os.SystemClock.elapsedRealtime()
    override fun pause(milliseconds: Long, cancelled: () -> Boolean) = SystemReadRuntime.pause(milliseconds, cancelled)
}
