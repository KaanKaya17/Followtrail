package dev.kaankaya.instagramtracker

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class ArchiveRetentionTest {
    private val now=1800000000000L
    private val session=Session("100","synthetic","test")
    private fun profile(at: Long)=Profile("200","qa.person",2,3,at)
    @Test fun defaultsPreserveHistoryAndDoNotScheduleCleanup() {
        val account=SavedAccount(session,profile(now).copy(userId="100"),listOf(TrackedPerson(listOf(profile(1),profile(now)))))
        assertSame(account,ArchiveRetention().retain(account,now))
        assertFalse(ArchiveRetention().due(now))
    }
    @Test fun exactBoundaryAndLatestBaselineSurviveWithoutChangingPreferencesOrCounters() {
        val policy=ArchiveRetention(30,90);val cutoff=now-30*ArchiveRetention.DAY
        val person=TrackedPerson(listOf(profile(cutoff-1),profile(cutoff),profile(now)),note="keep",watching=true)
        val account=SavedAccount(session,profile(now).copy(userId="100"),listOf(person,person.copy(history=listOf(profile(1).copy(userId="300")))))
        val result=policy.retain(account,now)
        assertEquals(listOf(cutoff,now),result.people[0].history.map {it.observedAt})
        assertEquals("keep",result.people[0].note);assertTrue(result.people[0].watching)
        assertEquals(1,result.people[1].history.size);assertEquals(account.session,result.session)
        assertEquals((now/ArchiveRetention.DAY-89)*ArchiveRetention.DAY,policy.dailyBefore(now))
    }
    @Test fun cleanupIsDueOnlyOncePerDayAndUnknownDurationsAreRejected() {
        assertTrue(ArchiveRetention(30,0).due(now))
        assertFalse(ArchiveRetention(30,0,now).due(now+ArchiveRetention.DAY-1))
        assertTrue(ArchiveRetention(30,0,now).due(now+ArchiveRetention.DAY))
        assertThrows(IllegalArgumentException::class.java){ArchiveRetention(1,0)}
        assertThrows(IllegalArgumentException::class.java){ArchiveRetention(0,31)}
    }
    @Test fun visualPruningCollectsUnusedBlobsButKeepsEnabledPreferenceAndOtherProfiles() {
        val data=VisualHistory.empty("100")
        VisualHistory.target(data,"200").put("enabled",true)
        VisualHistory.observe(data,profile(1))
        VisualHistory.target(data,"300").put("enabled",true)
        VisualHistory.observe(data,profile(now).copy(userId="300"))
        VisualHistory.prune(data,now-30*ArchiveRetention.DAY)
        assertEquals(0,data.getJSONObject("profiles").getJSONObject("200").getJSONArray("entries").length())
        assertTrue(data.getJSONObject("profiles").getJSONObject("200").getBoolean("enabled"))
        assertEquals(1,data.getJSONObject("profiles").getJSONObject("300").getJSONArray("entries").length())
        VisualHistory.validate(data)
    }
}
