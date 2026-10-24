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
            "Profil sayı değişiklikleri", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Seçtiğin kişilerde gözlemlenen takipçi ve takip edilen sayı farkları."
        })
    }
    @Suppress("DEPRECATION")
    private fun builder(): Notification.Builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL) else Notification.Builder(context)
    fun post(change: CountChange) {
        ensureChannel()
        if (!allowed()) return
        val intent = Intent(context, MainActivity::class.java).setData(Uri.parse("instagramtracker://observation/${change.ownerId}/${change.profile.userId}"))
            .putExtra("notification_owner", change.ownerId).putExtra("notification_person", change.profile.userId)
            .putExtra("offline_preview", preview)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val format = NumberFormat.getIntegerInstance(Locale.forLanguageTag("tr-TR"))
        fun delta(value: Long) = (if (value > 0) "+" else "") + format.format(value)
        val public = builder().setSmallIcon(R.drawable.ic_notification).setContentTitle("InstagramTracker")
            .setContentText(if (preview) "Demo: örnek profilde sayı değişikliği." else "İzlediğin bir profilde sayı değişikliği gözlendi.").build()
        val notification = builder().setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${if (preview) "DEMO · " else ""}@${change.profile.username} · sayı değişikliği")
            .setContentText("Takipçi ${delta(change.followersDelta)} · Takip edilen ${delta(change.followingDelta)}")
            .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(public).setContentIntent(pending)
            .setAutoCancel(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_STATUS).build()
        try { manager.notify(tag(change.ownerId, change.profile.userId), 0, notification) }
        catch (_: SecurityException) { /* Permission can be revoked after the check; observations stay saved. */ }
    }
    fun cancel(owner: String, person: String) { manager.cancel(tag(owner, person), 0) }
    fun clear() { manager.cancelAll() }
    private fun tag(owner: String, person: String) = "profile:$owner:$person"
}
