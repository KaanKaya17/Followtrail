package dev.kaankaya.instagramtracker

enum class NoticeField { FOLLOWERS, FOLLOWING, POSTS, USERNAME, BIOGRAPHY, PICTURE, LISTS }
data class NoticeRule(val enabled: Boolean = true, val fields: Set<NoticeField> = NoticeField.entries.toSet()) {
    fun select(change: ObservedProfileChange): ObservedProfileChange? {
        if (!enabled) return null
        return change.copy(followersDelta=if(NoticeField.FOLLOWERS in fields)change.followersDelta else 0,
            followingDelta=if(NoticeField.FOLLOWING in fields)change.followingDelta else 0,
            postsDelta=change.postsDelta.takeIf {NoticeField.POSTS in fields},
            edits=change.edits.filter {NoticeField.valueOf(it.field.name) in fields})
            .takeIf {it.followersDelta!=0L || it.followingDelta!=0L || it.postsDelta!=null || it.edits.isNotEmpty()}
    }
}

/** Interpretation of recorded facts only; old events never acquire invented before/after values. */
object HistoryPresentation {
    fun changed(event: CheckEvent): Boolean = when (event.kind) {
        CheckKind.FAILURE -> false
        CheckKind.LIST -> (event.added ?: 0) > 0 || (event.missing ?: 0) > 0
        CheckKind.PROFILE -> event.previousAt != null && noticeFields(event).isNotEmpty()
    }
    fun routine(event: CheckEvent) = event.kind == CheckKind.PROFILE && !changed(event)
    fun noticeFields(event: CheckEvent): Set<NoticeField> = buildSet {
        if ((event.followersDelta ?: 0) != 0L) add(NoticeField.FOLLOWERS)
        if ((event.followingDelta ?: 0) != 0L) add(NoticeField.FOLLOWING)
        if (event.beforePosts != null && event.afterPosts != null && event.beforePosts != event.afterPosts) add(NoticeField.POSTS)
        event.fields.forEach { add(NoticeField.valueOf(it.name)) }
        if (event.kind == CheckKind.LIST) add(NoticeField.LISTS)
    }
    fun notifications(events: List<CheckEvent>) = events.filter(::changed).sortedByDescending { it.analyzedAt }.take(500)
}
