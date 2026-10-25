package dev.kaankaya.instagramtracker

import android.app.Activity
import android.content.Context

/** Production builds cannot enable the debug preview or synthetic transport. */
object PreviewMode {
    fun avatarFetcher(@Suppress("UNUSED_PARAMETER") enabled: Boolean): AvatarFetcher = CdnAvatarFetcher()
    fun sync(@Suppress("UNUSED_PARAMETER") context: Context, @Suppress("UNUSED_PARAMETER") account: SavedAccount?,
        @Suppress("UNUSED_PARAMETER") gate: RequestGate) = false
    fun cancel(@Suppress("UNUSED_PARAMETER") context: Context) = Unit
    fun enabled(@Suppress("UNUSED_PARAMETER") activity: Activity) = false
    fun seed(@Suppress("UNUSED_PARAMETER") enabled: Boolean): SavedAccount? = null
    fun api(gate: RequestGate, @Suppress("UNUSED_PARAMETER") enabled: Boolean) = InstagramApi(gate)
    fun followLists(gate: RequestGate, @Suppress("UNUSED_PARAMETER") enabled: Boolean): FollowListSource =
        MobileFollowListReader(gate, MobileSessions(gate.context))
}
