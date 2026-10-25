package dev.kaankaya.instagramtracker

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import java.text.NumberFormat
import java.util.Locale

class ChangeNotifications(private val context: Context, private val preview: Boolean = false) {
    companion object { const val CHANNEL = "profile_changes_v1" }
    private val manager = context.getSystemService(NotificationManager::class.java)
    fun allowed(): Boolean = (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        manager.areNotificationsEnabled() && (Build.VERSION.SDK_INT < 26 || manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE)
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL,
            "Profil değişiklikleri", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Profil değişiklikleri ve önceki/güncel liste okumaları arasındaki gözlemlenen farklar."
        })
    }
    @Suppress("DEPRECATION")
    private fun builder(): Notification.Builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL) else Notification.Builder(context)
    fun post(change: ObservedProfileChange) {
        ensureChannel()
        if (!allowed()) return
        val intent = Intent(context, MainActivity::class.java).setData(Uri.parse("instagramtracker://observation/${change.ownerId}/${change.profile.userId}"))
            .putExtra("notification_owner", change.ownerId).putExtra("notification_person", change.profile.userId)
            .putExtra("offline_preview", preview)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val format = NumberFormat.getIntegerInstance(Locale.forLanguageTag("tr-TR"))
        fun delta(value: Long) = (if (value > 0) "+" else "") + format.format(value)
        val details = mutableListOf<String>()
        if (change.followersDelta != 0L || change.followingDelta != 0L)
            details += "Takipçi ${delta(change.followersDelta)} · Takip edilen ${delta(change.followingDelta)}"
        change.edits.forEach { edit -> details += context.getString(when (edit.field) {
            ProfileChangeField.USERNAME -> R.string.profile_username_changed
            ProfileChangeField.BIOGRAPHY -> R.string.profile_bio_changed
            ProfileChangeField.PICTURE -> R.string.profile_picture_changed
        }) }
        val public = builder().setSmallIcon(R.drawable.ic_notification).setContentTitle("InstagramTracker")
            .setContentText(if (preview) "Demo: örnek profilde değişiklik." else "İzlediğin bir profilde değişiklik gözlendi.").build()
        val notification = builder().setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${if (preview) "DEMO · " else ""}@${change.profile.username} · profil değişikliği")
            .setContentText(details.joinToString(" · "))
            .setStyle(Notification.BigTextStyle().bigText(details.joinToString("\n")))
            .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(public).setContentIntent(pending)
            .setAutoCancel(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_STATUS).build()
        try { manager.notify(tag(change.ownerId, change.profile.userId), 0, notification) }
        catch (_: SecurityException) { /* Permission can be revoked after the check; observations stay saved. */ }
    }
    fun postList(session: Session, profile: Profile, difference: ListDifference) {
        ensureChannel(); if (!allowed()) return
        val intent = Intent(context, FollowListActivity::class.java)
            .setData(Uri.parse("instagramtracker://list/${session.userId}/${profile.userId}/${difference.direction.path}"))
            .putExtra("target_id", profile.userId).putExtra("notification_owner", session.userId)
            .putExtra("initial_direction", difference.direction.name).putExtra("offline_preview", preview)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val direction = context.getString(if (difference.direction == FollowDirection.FOLLOWERS) R.string.list_followers else R.string.list_following)
        val details = context.getString(R.string.auto_difference_counts, difference.added.size.toString(), difference.missing.size.toString())
        val public = builder().setSmallIcon(R.drawable.ic_notification).setContentTitle("InstagramTracker")
            .setContentText(context.getString(R.string.auto_notification_public)).build()
        val notification = builder().setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${if (preview) "DEMO · " else ""}@${profile.username} · $direction")
            .setContentText(details).setStyle(Notification.BigTextStyle().bigText(details + "\n" + context.getString(R.string.auto_difference_scope)))
            .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(public).setContentIntent(pending)
            .setAutoCancel(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_STATUS).build()
        try { manager.notify("list:${session.userId}:${profile.userId}:${difference.direction.path}", 0, notification) }
        catch (_: SecurityException) { /* The committed comparison remains available in the app. */ }
    }
    fun cancelLists(owner: String, person: String) {
        FollowDirection.entries.forEach { manager.cancel("list:$owner:$person:${it.path}", 0) }
    }
    fun cancel(owner: String, person: String) { manager.cancel(tag(owner, person), 0); cancelLists(owner, person) }
    fun clear() { manager.cancelAll() }
    private fun tag(owner: String, person: String) = "profile:$owner:$person"
}
