package dev.kaankaya.instagramtracker

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MonitorPolicyTest {
    private val profile = Profile("456", "synthetic", 12, 7, 1000, 4)
    private val person = TrackedPerson(listOf(profile), "note", "group", true, 360, 2000)
    @Test fun earliestDueWatchWinsAndDisabledOrFutureWatchesDoNotRun() {
        val earlier = person.copy(history = listOf(profile.copy(userId = "789")), nextCheckAt = 1500)
        assertEquals(earlier, MonitorPolicy.due(listOf(person, earlier), 2000))
        assertNull(MonitorPolicy.due(listOf(person.copy(watching = false), earlier), 1499))
    }
    @Test fun onlyObservedFollowerOrFollowingChangesProduceNetDeltas() {
        assertNull(MonitorPolicy.change("123", person, profile.copy(posts = 5)))
        assertNull(MonitorPolicy.change("123", person.copy(watching = false), profile.copy(followers = 14)))
        assertNull(MonitorPolicy.change("123", null, profile))
        val change = MonitorPolicy.change("123", person, profile.copy(followers = 10, following = 8))!!
        assertEquals(-2L, change.followersDelta); assertEquals(1L, change.followingDelta)
        val recorded = PeoplePolicy.record(listOf(person), profile.copy(observedAt = 3000)).single()
        assertEquals(3000 + 6 * 3600000L, recorded.nextCheckAt)
        assertEquals("note", recorded.note); assertTrue(recorded.watching)
    }
    @Test fun watchSettingsRoundtripAndVersionTwoMigratesWithMonitoringOff() {
        val account = SavedAccount(Session("123", "ds_user_id=123; sessionid=synthetic", "test-UA"),
            profile.copy(userId = "123"), listOf(person))
        val encoded = AccountCodec.encode(account)
        assertEquals(account, AccountCodec.decode(encoded))
        val old = JSONObject(encoded).put("version", 2)
        val oldPerson = old.getJSONArray("people").getJSONObject(0)
        listOf("watching", "intervalMinutes", "intervalHours", "nextCheckAt").forEach { oldPerson.remove(it) }
        val migrated = AccountCodec.decode(old.toString()).people.single()
        assertFalse(migrated.watching); assertEquals(60, migrated.intervalMinutes); assertEquals(0L, migrated.nextCheckAt)
        assertThrows(Exception::class.java) { AccountCodec.encode(account.copy(people = listOf(person.copy(intervalMinutes = 2)))) }
    }
}
