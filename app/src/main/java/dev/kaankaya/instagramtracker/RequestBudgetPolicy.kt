package dev.kaankaya.instagramtracker

/** Rolling reservations. Persisted keys are local owner digests, never credentials. */
object RequestBudgetPolicy {
    // Accept old Windows-sized histories during a policy transition without deleting evidence.
    val MAX_STORED = maxOf(4800, RequestDefaults.DEVICE_DAILY)
    const val HOUR = 60 * 60 * 1000L
    const val DAY = 24 * HOUR
    fun availableAt(times: List<Long>, now: Long, hourly: Int, daily: Int): Long {
        require(hourly > 0 && daily >= hourly && times.size <= MAX_STORED && times.all { it >= 0 } && times == times.sorted())
        val day = times.filter { it > now - DAY }
        val hour = day.filter { it > now - HOUR }
        return maxOf(if (hour.size >= hourly) hour[hour.size - hourly] + HOUR else 0,
            if (day.size >= daily) day[day.size - daily] + DAY else 0)
    }
    fun nextAt(times: List<Long>, now: Long, account: List<Long> = emptyList()): Long = maxOf(
        availableAt(times, now, RequestDefaults.DEVICE_HOURLY, RequestDefaults.DEVICE_DAILY),
        availableAt(account, now, RequestDefaults.ACCOUNT_HOURLY, RequestDefaults.ACCOUNT_DAILY))
    fun profileNextAt(times: List<Long>, now: Long, account: List<Long> = emptyList()): Long = maxOf(
        availableAt(times, now, RequestDefaults.DEVICE_HOURLY * RequestDefaults.AUTO_PROFILE_PERCENT / 100,
            RequestDefaults.DEVICE_DAILY * RequestDefaults.AUTO_PROFILE_PERCENT / 100),
        availableAt(account, now, RequestDefaults.ACCOUNT_HOURLY * RequestDefaults.AUTO_PROFILE_PERCENT / 100,
            RequestDefaults.ACCOUNT_DAILY * RequestDefaults.AUTO_PROFILE_PERCENT / 100))
    fun ownerKey(owner: String): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(InstagramIdentity.id(owner).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    fun reserve(times: List<Long>, now: Long) = (times.filter { it > now - DAY } + now).sorted()
}
