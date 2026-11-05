// Generated from design/request-policy.json; run node interface/scripts/generate-request-policy.mjs.
package dev.kaankaya.instagramtracker

object RequestDefaults {
    const val MIN_SPACING_MS = 6000L
    const val MAX_SPACING_MS = 10000L
    const val ACCOUNT_HOURLY = 120
    const val ACCOUNT_DAILY = 3000
    const val DEVICE_HOURLY = 240
    const val DEVICE_DAILY = 6000
    const val AUTO_PROFILE_PERCENT = 80
    const val PROFILE_BASIS_MS = 60000L
    const val DEFAULT_INTERVAL_MINUTES = 5
    val INTERVAL_MINUTES = listOf(1, 5, 15, 30, 60, 180, 360, 720, 1440)
}
