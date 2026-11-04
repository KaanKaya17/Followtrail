package dev.kaankaya.instagramtracker

import android.app.AlertDialog
import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import java.text.NumberFormat

/** Shared mobile summaries/details; recorded identities and missing facts retain their meaning. */
object HistoryViews {
    fun number(context: Context, value: Long) = NumberFormat.getIntegerInstance(AppLanguage.locale(context)).format(value)
    fun delta(context: Context, value: Long) = (if (value > 0) "+" else "") + number(context, value)
    fun field(context: Context, field: NoticeField) = context.getString(when (field) {
        NoticeField.FOLLOWERS -> R.string.ui_followers; NoticeField.FOLLOWING -> R.string.ui_following
        NoticeField.POSTS -> R.string.parity_posts; NoticeField.USERNAME -> R.string.profile_username_changed
        NoticeField.BIOGRAPHY -> R.string.profile_bio_changed; NoticeField.PICTURE -> R.string.profile_picture_changed
        NoticeField.LISTS -> R.string.parity_lists
    })
    fun summary(context: Context, event: CheckEvent, preview: Boolean = true): List<String> {
        val result = mutableListOf<String>()
        fun count(field: NoticeField, before: Long?, after: Long?, difference: Long?) {
            if (before != null && after != null && before != after)
                result += "${field(context, field)}: ${number(context,before)} → ${number(context,after)} · ${delta(context,after-before)}"
            else if (difference != null && difference != 0L) result += "${field(context,field)}: ${delta(context,difference)}"
        }
        when (event.kind) {
            CheckKind.FAILURE -> result += context.getString(R.string.timeline_failure)
            CheckKind.PROFILE -> {
                count(NoticeField.FOLLOWERS,event.beforeFollowers,event.afterFollowers,event.followersDelta)
                count(NoticeField.FOLLOWING,event.beforeFollowing,event.afterFollowing,event.followingDelta)
                count(NoticeField.POSTS,event.beforePosts,event.afterPosts,null)
                event.fields.forEach { field ->
                    val edit = event.edits.firstOrNull { it.field == field }
                    val label = field(context,NoticeField.valueOf(field.name))
                    result += if (preview && edit != null && field != ProfileChangeField.PICTURE) "$label: ${edit.before.take(80)} → ${edit.after.take(80)}" else label
                }
                if (result.isEmpty()) result += context.getString(R.string.parity_checked)
            }
            CheckKind.LIST -> {
                result += context.getString(if (event.direction == FollowDirection.FOLLOWING) R.string.list_following else R.string.list_followers)
                if (event.added == null) result += context.getString(R.string.parity_baseline)
                else {
                    fun names(users: List<CheckMember>, total: Int): String {
                        val shown = users.take(2).map { it.username?.let { name -> "@$name" } ?: "#${it.id}" }.joinToString(", ")
                        return shown + if (total > 2) " · " + context.resources.getQuantityString(R.plurals.parity_more_people,total-2,total-2) else ""
                    }
                    result += context.getString(R.string.parity_arrivals,event.added) + names(event.newlySeen,event.added).let { if (it.isNotEmpty()) " · $it" else "" }
                    result += context.getString(R.string.parity_missing,event.missing ?: 0) + names(event.notSeen,event.missing ?: 0).let { if (it.isNotEmpty()) " · $it" else "" }
                }
            }
        }
        return result
    }
    fun detail(context: Context, event: CheckEvent, images: AvatarImages, owner: String, profile: Profile?) {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(AppUi.dp(context,24),AppUi.dp(context,16),AppUi.dp(context,24),AppUi.dp(context,12))
        }
        val identity = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        identity.addView(ProfileAvatarView(context,48).apply { bind(images,owner,event.target,event.username,
            profile?.details?.pictureUrl?.value,profile?.details?.pictureId?.value) })
        identity.addView(AppUi.text(context,"@${event.username}",17f,AppUi.ink,true).apply {setPadding(AppUi.dp(context,12),0,0,0)})
        box.addView(identity)
        box.addView(AppUi.text(context,ObservationTimes.format(context,event.observedAt ?: event.analyzedAt),12f,AppUi.muted).apply {setPadding(0,AppUi.dp(context,12),0,AppUi.dp(context,12))})
        summary(context,event,false).forEach { box.addView(AppUi.text(context,it,14f,if(HistoryPresentation.routine(event))AppUi.teal else AppUi.ink).apply {setPadding(0,0,0,AppUi.dp(context,10))}) }
        if (HistoryPresentation.changed(event)) {
            event.edits.filter { it.field != ProfileChangeField.PICTURE }.forEach { edit ->
                box.addView(AppUi.text(context,edit.before,13f,AppUi.coral));box.addView(AppUi.text(context,edit.after,13f,AppUi.teal))
            }
            if (event.kind == CheckKind.LIST) {
                fun members(label: Int, users: List<CheckMember>) {
                    if (users.isNotEmpty())box.addView(AppUi.text(context,context.getString(label)+"\n"+users.joinToString("\n") { it.username?.let { name -> "@$name" } ?: "#${it.id}" },13f))
                }
                members(R.string.ui_newly_seen_n,event.newlySeen);members(R.string.ui_n_nmissing_from_this_read_n,event.notSeen)
                if ((event.added ?: 0)>event.newlySeen.size || (event.missing ?: 0)>event.notSeen.size)
                    box.addView(AppUi.text(context,context.resources.getQuantityString(R.plurals.timeline_member_sample,CheckEvent.MEMBER_SAMPLE,CheckEvent.MEMBER_SAMPLE),12f,AppUi.muted))
            }
        }
        if(event.kind==CheckKind.LIST && event.pages!=null && event.accounts!=null)
            box.addView(AppUi.text(context,context.getString(R.string.timeline_list_counts,event.pages,event.accounts),12f,AppUi.muted))
        event.previousAt?.let {box.addView(AppUi.text(context,context.getString(R.string.timeline_previous,ObservationTimes.format(context,it)),12f,AppUi.muted))}
        event.startedAt?.let {box.addView(AppUi.text(context,context.getString(R.string.timeline_started,ObservationTimes.format(context,it)),12f,AppUi.muted))}
        box.addView(AppUi.text(context,context.getString(R.string.timeline_analyzed,ObservationTimes.format(context,event.analyzedAt)),12f,AppUi.muted))
        if (event.kind == CheckKind.FAILURE) box.addView(AppUi.button(context,context.getString(R.string.parity_technical),false) {
            AlertDialog.Builder(context).setTitle(R.string.parity_technical).setMessage(event.failure?.let { ApiFailure(it).userMessage(context) }.orEmpty()).setPositiveButton(R.string.ui_close,null).show()
        })
        AlertDialog.Builder(context).setTitle(context.getString(if(HistoryPresentation.routine(event)) R.string.parity_profile_check else R.string.parity_details))
            .setView(android.widget.ScrollView(context).apply { addView(box) }).setPositiveButton(R.string.ui_close,null).show()
    }
    fun preferences(context: Context, journal: CheckJournal, session: Session, profile: Profile) {
        val saved = journal.rule(session,profile.userId)
        val choices = listOf<NoticeField?>(null) + NoticeField.entries
        val checked = choices.map { if(it == null)saved.enabled else it in saved.fields }.toBooleanArray()
        AlertDialog.Builder(context).setTitle(context.getString(R.string.parity_preferences,"@${profile.username}"))
            .setMultiChoiceItems(choices.map { if(it == null)context.getString(R.string.parity_notices_enabled) else field(context,it) }.toTypedArray(),checked) { _,which,value -> checked[which] = value }
            .setNegativeButton(R.string.ui_cancel,null).setPositiveButton(R.string.ui_save) { _,_ ->
                try { journal.setRule(session,profile.userId,NoticeRule(checked[0],NoticeField.entries.filterIndexed { index,_ -> checked[index+1] }.toSet())) }
                catch (_: Exception) { AlertDialog.Builder(context).setMessage(R.string.timeline_write_failed).setPositiveButton(R.string.ui_close,null).show() }
            }.show()
    }
}
