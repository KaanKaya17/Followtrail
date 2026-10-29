package dev.kaankaya.instagramtracker

/** Device-wide timestamp-only budget; no account identifier or credentials. */
object RequestBudgetPolicy {
    const val HOURLY = 120
    const val DAILY = 1200
    const val HOUR = 60 * 60 * 1000L
    const val DAY = 24 * HOUR
    fun nextAt(times: List<Long>, now: Long): Long {
        require(times.size <= DAILY && times.all { it >= 0 } && times == times.sorted())
        val day = times.filter { it > now - DAY }
        val hour = day.filter { it > now - HOUR }
        return maxOf(if (hour.size >= HOURLY) hour[hour.size - HOURLY] + HOUR else 0,
            if (day.size >= DAILY) day[day.size - DAILY] + DAY else 0)
    }
    fun reserve(times: List<Long>, now: Long) = (times.filter { it > now - DAY } + now).sorted()
}
