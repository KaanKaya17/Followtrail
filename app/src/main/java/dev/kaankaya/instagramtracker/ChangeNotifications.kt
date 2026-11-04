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

class ChangeNotifications(context: Context, private val preview: Boolean = false,
    private val vaultForOwner: (String) -> SessionVault = { owner ->
        SessionVault(context.applicationContext,if(preview)"previewaccount" else "account",if(preview)null else owner)
    }) {
    private val sourceContext = context.applicationContext
    companion object { const val CHANNEL = "profile_changes_v1" }
    private val manager = sourceContext.getSystemService(NotificationManager::class.java)
    fun allowed(): Boolean = (Build.VERSION.SDK_INT < 33 || sourceContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        manager.areNotificationsEnabled() && (Build.VERSION.SDK_INT < 26 || manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE)
    fun ensureChannel() {
        val context = AppLanguage.wrap(sourceContext)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL,
            context.getString(R.string.ui_profile_changes), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = context.getString(R.string.ui_profile_changes_and_observed_differences_between_previous)
        })
    }
    @Suppress("DEPRECATION")
    private fun builder(context: Context): Notification.Builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL) else Notification.Builder(context)
    private fun accountLabel(owner: String): String? = if(preview)"DEMO" else try {
        AccountCatalog(sourceContext).entries().firstOrNull {it.id==owner}?.let {"@${it.username}"}
    }catch (_: Exception) {null}
    private fun rule(owner: String,target: String,expected: Session?=null): NoticeRule? = try {
        val vault=vaultForOwner(owner)
        var result: NoticeRule?=null
        vault.inspect {saved -> if(saved?.session?.userId==owner && (expected==null || saved.session==expected))result=vault.journal.rule(saved.session,target)}
        result
    } catch (_: Exception) {null}
    fun post(observation: ObservedProfileChange) {
        val context = AppLanguage.wrap(sourceContext)
        ensureChannel()
        if (!allowed()) return
        val change = rule(observation.ownerId,observation.profile.userId)?.select(observation) ?: return
        val intent = Intent(context, MainActivity::class.java).setData(Uri.parse("instagramtracker://observation/${change.ownerId}/${change.profile.userId}"))
            .putExtra("notification_owner", change.ownerId).putExtra("notification_person", change.profile.userId)
            .putExtra("offline_preview", preview)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val format = NumberFormat.getIntegerInstance(AppLanguage.locale(context))
        fun delta(value: Long) = (if (value > 0) "+" else "") + format.format(value)
        val details = mutableListOf<String>()
        if (change.followersDelta != 0L || change.followingDelta != 0L)
            details += context.getString(R.string.ui_notification_count_changes, delta(change.followersDelta), delta(change.followingDelta))
        change.edits.forEach { edit -> details += context.getString(when (edit.field) {
            ProfileChangeField.USERNAME -> R.string.profile_username_changed
            ProfileChangeField.BIOGRAPHY -> R.string.profile_bio_changed
            ProfileChangeField.PICTURE -> R.string.profile_picture_changed
        }) }
        change.postsDelta?.let {details += context.getString(R.string.parity_post_delta,delta(it))}
        details += context.getString(R.string.timeline_observed, ObservationTimes.format(context, change.profile.observedAt))
        change.previousAt?.let { details += context.getString(R.string.timeline_previous, ObservationTimes.format(context, it)) }
        val public = builder(context).setSmallIcon(R.drawable.ic_notification).setContentTitle("InstagramTracker")
            .setContentText(if (preview) context.getString(R.string.ui_demo_a_sample_profile_changed) else context.getString(R.string.ui_a_change_was_observed_in_a_profile)).build()
        val notification = builder(context).setSmallIcon(R.drawable.ic_notification)
            .setWhen(change.profile.observedAt).setShowWhen(true)
            .setContentTitle(context.getString(R.string.ui_profile_change, if (preview) "DEMO · " else "", change.profile.username))
            .setSubText(accountLabel(change.ownerId))
            .setContentText(details.joinToString(" · "))
            .setStyle(Notification.BigTextStyle().bigText(details.joinToString("\n")))
            .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(public).setContentIntent(pending)
            .setAutoCancel(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_STATUS).build()
        try { manager.notify(tag(change.ownerId, change.profile.userId), 0, notification) }
        catch (_: SecurityException) { /* Permission can be revoked after the check; observations stay saved. */ }
    }
    fun postList(session: Session, profile: Profile, difference: ListDifference) {
        val context = AppLanguage.wrap(sourceContext)
        ensureChannel(); if (!allowed()) return
        val rule=rule(session.userId,profile.userId,session) ?: return
        if(!rule.enabled || NoticeField.LISTS !in rule.fields)return
        val intent = Intent(context, FollowListActivity::class.java)
            .setData(Uri.parse("instagramtracker://list/${session.userId}/${profile.userId}/${difference.direction.path}"))
            .putExtra("target_id", profile.userId).putExtra("notification_owner", session.userId)
            .putExtra("initial_direction", difference.direction.name).putExtra("offline_preview", preview)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val direction = context.getString(if (difference.direction == FollowDirection.FOLLOWERS) R.string.list_followers else R.string.list_following)
        val details = context.getString(R.string.auto_difference_counts, difference.added.size.toString(), difference.missing.size.toString())
        val public = builder(context).setSmallIcon(R.drawable.ic_notification).setContentTitle("InstagramTracker")
            .setContentText(context.getString(R.string.auto_notification_public)).build()
        val notification = builder(context).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${if (preview) "DEMO · " else ""}@${profile.username} · $direction")
            .setSubText(accountLabel(session.userId))
            .setWhen(difference.observedAt).setShowWhen(true)
            .setContentText(details).setStyle(Notification.BigTextStyle().bigText(details + "\n\n" +
                context.getString(R.string.timeline_observed, ObservationTimes.format(context, difference.observedAt)) + "\n" +
                context.getString(R.string.timeline_previous, ObservationTimes.format(context, difference.previousAt)) + "\n\n" + context.getString(R.string.auto_difference_scope)))
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
    fun clearOwner(owner: String) {
        manager.activeNotifications.filter {it.tag?.startsWith("profile:$owner:")==true || it.tag?.startsWith("list:$owner:")==true}
            .forEach {manager.cancel(it.tag,it.id)}
    }
    private fun tag(owner: String, person: String) = "profile:$owner:$person"
}
