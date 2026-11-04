package dev.kaankaya.instagramtracker

import org.junit.Assert.*
import org.junit.Test

class HistoryPresentationTest {
    private val event=CheckEvent("200","recorded_name",CheckKind.PROFILE,1000,2000,3000,1000,0,0)
    @Test fun routineAndInitialChecksDoNotBecomeChangeNotifications() {
        assertTrue(HistoryPresentation.routine(event))
        assertTrue(HistoryPresentation.routine(event.copy(previousAt=null)))
        assertFalse(HistoryPresentation.changed(event.copy(kind=CheckKind.FAILURE,failure=ApiFailure.Kind.LIMITED)))
        assertFalse(HistoryPresentation.changed(event.copy(kind=CheckKind.LIST,added=null,missing=null)))
    }
    @Test fun legacyDeltasRemainUsefulWithoutInventingOldValues() {
        val legacy=event.copy(followersDelta=-2)
        assertTrue(HistoryPresentation.changed(legacy))
        assertNull(legacy.beforeFollowers);assertNull(legacy.afterFollowers)
        assertEquals(setOf(NoticeField.FOLLOWERS),HistoryPresentation.noticeFields(legacy))
        val posts=event.copy(beforePosts=3,afterPosts=4)
        assertTrue(HistoryPresentation.changed(posts));assertEquals(setOf(NoticeField.POSTS),HistoryPresentation.noticeFields(posts))
        assertFalse(HistoryPresentation.changed(event.copy(afterPosts=4)))
    }
    @Test fun oneCheckGroupsItsFieldsAndNotificationsKeepOnlyNewestChanges() {
        val grouped=event.copy(followersDelta=2,followingDelta=-1,fields=setOf(ProfileChangeField.BIOGRAPHY))
        val history=(1..600).map {grouped.copy(observedAt=it.toLong()+2000,analyzedAt=it.toLong()+3000)}+event
        val notifications=HistoryPresentation.notifications(history)
        assertEquals(500,notifications.size)
        assertEquals(3600L,notifications.first().analyzedAt)
        assertEquals(setOf(NoticeField.FOLLOWERS,NoticeField.FOLLOWING,NoticeField.BIOGRAPHY),HistoryPresentation.noticeFields(notifications.first()))
    }
    @Test fun preferencesSuppressOnlySelectedDeliveryFieldsAndLeaveRecordedFactsUntouched() {
        val edit=ProfileEdit("200","recorded_name",ProfileChangeField.USERNAME,"old","new",1000,2000)
        val change=ObservedProfileChange("123",Profile("200","new",10,20,2000,4),2,3,listOf(edit),1000,1)
        val usernameOnly=NoticeRule(fields=setOf(NoticeField.USERNAME)).select(change)!!
        assertEquals(listOf(edit),usernameOnly.edits);assertEquals(0L,usernameOnly.followersDelta);assertNull(usernameOnly.postsDelta)
        assertNull(NoticeRule(enabled=false).select(change))
        assertNull(NoticeRule(fields=emptySet()).select(change))
        assertEquals(change,NoticeRule().select(change))
        assertEquals(2L,change.followersDelta)
    }
}
