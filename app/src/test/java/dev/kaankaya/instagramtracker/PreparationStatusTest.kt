package dev.kaankaya.instagramtracker

import org.junit.Assert.*
import org.junit.Test

class PreparationStatusTest {
    @Test fun listReadsAreIndependentFromNotificationsButRestrictionsStillWin() {
        val person=TrackedPerson(listOf(Profile("100","synthetic",1,1,1000)),watching=true,nextCheckAt=1000)
        val normal=WatchConditions(notificationsAllowed=false)
        assertEquals(WatchReason.NOTIFICATIONS_OFF,WatchStatusPolicy.status(person,listOf(person),normal,2000).reason)
        val preparation=normal.copy(notificationsRequired=false)
        assertEquals(WatchReason.READY,WatchStatusPolicy.status(person,listOf(person),preparation,2000).reason)
        assertEquals(WatchReason.LIMITED,WatchStatusPolicy.status(person,listOf(person),preparation.copy(blockedUntil=3000),2000).reason)
        assertFalse(preparation.notificationsAllowed)
    }
}
