package dev.kaankaya.instagramtracker

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WatchScheduleTest {
    private val profile = Profile("456", "synthetic", 12, 7, 1000)
    private val person = TrackedPerson(listOf(profile), "note", "group", true, 15, 2000, true)
    private fun saved(p: TrackedPerson = person) = SavedAccount(
        Session("123", "ds_user_id=123; sessionid=synthetic", "test-UA"), profile.copy(userId = "123"), listOf(p))
    private fun status(c: WatchConditions = WatchConditions(), now: Long = 3000) =
        WatchStatusPolicy.status(person, listOf(person), c, now)

    @Test fun allIntervalsRoundtripAndSuccessfulReadsUseMinutesWithoutLosingMetadata() {
        for (minutes in WatchIntervals.minutes) {
            val p = person.copy(intervalMinutes = minutes)
            assertEquals(saved(p), AccountCodec.decode(AccountCodec.encode(saved(p))))
            val recorded = PeoplePolicy.record(listOf(p), profile.copy(observedAt = 5000)).single()
            assertEquals(5000 + minutes * 60000L, recorded.nextCheckAt)
            assertEquals(p.note, recorded.note); assertTrue(recorded.favorite); assertTrue(recorded.watching)
        }
    }
    @Test fun oldHourRecordsMigrateExactlyAndDowngradeCompatibilityNeverSpeedsUpChecks() {
        for (version in listOf(3, 4)) for (hours in listOf(1, 6, 24)) {
            val body = JSONObject(AccountCodec.encode(saved(person.copy(intervalMinutes = hours * 60)))).put("version", version)
            body.getJSONArray("people").getJSONObject(0).remove("intervalMinutes")
            val restored = AccountCodec.decode(body.toString())
            assertEquals(saved(person.copy(intervalMinutes = hours * 60)), restored)
        }
        for ((minutes, hours) in listOf(15 to 1, 30 to 1, 60 to 1, 360 to 6, 720 to 24, 1440 to 24)) {
            val body = JSONObject(AccountCodec.encode(saved(person.copy(intervalMinutes = minutes))))
            val p = body.getJSONArray("people").getJSONObject(0)
            assertEquals(hours, p.getInt("intervalHours"))
            p.remove("intervalMinutes")
            assertEquals(hours * 60, AccountCodec.decode(body.toString()).people.single().intervalMinutes)
        }
    }
    @Test fun malformedOrConflictingIntervalsFailAtTheStorageBoundary() {
        for (value in listOf<Any>("15", 15.5, true, JSONObject.NULL, 0, -15, 45, 1441)) {
            val body = JSONObject(AccountCodec.encode(saved()))
            body.getJSONArray("people").getJSONObject(0).put("intervalMinutes", value)
            assertThrows(Exception::class.java) { AccountCodec.decode(body.toString()) }
        }
        val conflict = JSONObject(AccountCodec.encode(saved()))
        conflict.getJSONArray("people").getJSONObject(0).put("intervalHours", 6)
        assertThrows(Exception::class.java) { AccountCodec.decode(conflict.toString()) }
    }
    @Test fun pausesHaveNoPromisedNextTimeAndExpiredRestrictionRemainsManual() {
        val cases = listOf(
            WatchConditions(notificationsAllowed = false) to WatchReason.NOTIFICATIONS_OFF,
            WatchConditions(blockedUntil = 4000, manualOnly = true) to WatchReason.LIMITED,
            WatchConditions(connectionRequired = true) to WatchReason.CONNECTION_REQUIRED,
            WatchConditions(connectionInProgress = true) to WatchReason.CONNECTION_IN_PROGRESS,
            WatchConditions(manualOnly = true) to WatchReason.MANUAL_ONLY,
            WatchConditions(offline = true) to WatchReason.OFFLINE)
        for ((conditions, reason) in cases) {
            assertEquals(reason, status(conditions).reason); assertNull(status(conditions).eligibleAt)
        }
        assertEquals(WatchReason.MANUAL_ONLY, status(WatchConditions(blockedUntil = 2000, manualOnly = true)).reason)
        assertEquals(WatchReason.OFF, WatchStatusPolicy.status(person.copy(watching = false), listOf(person),
            WatchConditions(notificationsAllowed = false, blockedUntil = 9000), 3000).reason)
    }
    @Test fun eligibilityUsesBothIntervalsAndTheSharedGateWithoutClaimingAnExecutionTime() {
        assertEquals(WatchStatus(WatchReason.INTERVAL, 2000), status(now = 1500))
        assertEquals(WatchStatus(WatchReason.SHARED_BUDGET, 5000), status(WatchConditions(automaticAt = 5000)))
        assertEquals(WatchStatus(WatchReason.INTERVAL, 2000), status(WatchConditions(automaticAt = 1800), 1500))
        assertEquals(WatchReason.READY, status().reason)
    }
    @Test fun displayedQueueMatchesExecutionOrderAndFavoritesCannotJumpTheQueue() {
        val earlier = person.copy(history = listOf(profile.copy(userId = "789")), nextCheckAt = 1500, favorite = false)
        val disabled = earlier.copy(history = listOf(profile.copy(userId = "800")), watching = false)
        val future = earlier.copy(history = listOf(profile.copy(userId = "900")), nextCheckAt = 4000)
        val people = listOf(person, earlier, disabled, future)
        assertEquals(earlier, MonitorPolicy.due(people, 3000))
        assertEquals(WatchStatus(WatchReason.QUEUED, 2000, 1), WatchStatusPolicy.status(person, people, WatchConditions(), 3000))
        assertEquals(WatchStatus(WatchReason.SHARED_BUDGET, 5000, 1),
            WatchStatusPolicy.status(person, people, WatchConditions(automaticAt = 5000), 3000))
        val recorded = PeoplePolicy.record(people, earlier.latest.copy(observedAt = 3000))
        assertEquals(person.latest.userId, MonitorPolicy.due(recorded, 3000)!!.latest.userId)
        assertEquals(WatchReason.READY, WatchStatusPolicy.status(person, recorded, WatchConditions(), 3000).reason)
        val tie = earlier.copy(nextCheckAt = person.nextCheckAt)
        assertEquals(person, MonitorPolicy.due(listOf(person, tie), 3000))
    }
}
