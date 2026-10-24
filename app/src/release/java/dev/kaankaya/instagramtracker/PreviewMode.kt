package dev.kaankaya.instagramtracker

import android.app.Activity

/** Production builds cannot enable the debug preview or synthetic transport. */
object PreviewMode {
    fun enabled(@Suppress("UNUSED_PARAMETER") activity: Activity) = false
    fun seed(@Suppress("UNUSED_PARAMETER") enabled: Boolean): SavedAccount? = null
    fun api(gate: RequestGate, @Suppress("UNUSED_PARAMETER") enabled: Boolean) = InstagramApi(gate)
}
