package dev.kaankaya.instagramtracker

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ListAutomationTest {
    @Test fun ownProfileIntervalRequiresExplicitBackgroundPermissionAndDoesNotEnableLists() {
        val account = saved().copy(people=emptyList(),automation=ListAutomation())
        val archive = ListArchive(account.session.userId,account.session.connectionBinding)
        val due = account.profile.observedAt + 60_000
        assertNull(AutomaticQueue.next(account,archive,due))
        assertNull(AutomaticQueue.next(account,archive,due-1,ownIntervalMillis=60_000,ownEnabled=true))
        assertTrue(AutomaticQueue.next(account,archive,due,ownIntervalMillis=60_000,ownEnabled=true) is AutomaticTask.Own)
        assertNull(AutomaticQueue.next(account,archive,due,ownIntervalMillis=300_000,ownEnabled=true))
    }
    @Test fun savedOwnReservationRemainsLaterThanANewShorterInterval() {
        val account = saved().copy(people=emptyList(),automation=ListAutomation(ownNextCheckAt=900_000))
        val archive = ListArchive(account.session.userId,account.session.connectionBinding)
        assertNull(AutomaticQueue.next(account,archive,899_999,ownIntervalMillis=60_000,ownEnabled=true))
        assertEquals(900_000L,(AutomaticQueue.next(account,archive,900_000,ownIntervalMillis=60_000,ownEnabled=true) as AutomaticTask.Own).dueAt)
    }
    private val session = Session("100", "ds_user_id=100; sessionid=synthetic", "test-UA", "a".repeat(32))
    private val profile = Profile("200", "synthetic", 2, 1, 1000)
    private fun saved() = SavedAccount(session.copy(cookie = ""), profile.copy(userId = "100"),
        listOf(TrackedPerson(listOf(profile))), ListAutomation(true, setOf("100", "200")))
    private fun page(time: Long, ids: List<String>, cursor: String? = null, next: String? = null,
        limited: Boolean = false, direction: FollowDirection = FollowDirection.FOLLOWERS, target: String = "200") =
        FollowPage(target, direction, cursor, ids.map { FollowUser(it, "person$it", null, null, null, null) },
            FollowField(true, next), FollowField(true, limited), FollowField(true, next != null), FollowField(false, null), time)
    @Test fun automationRoundtripsAndOldAccountsDefaultToNoListRequests() {
        val account = saved(); assertEquals(account, AccountCodec.decode(AccountCodec.encode(account)))
        val old = JSONObject(AccountCodec.encode(account)); old.remove("listAutomation")
        val restored = AccountCodec.decode(old.toString())
        assertEquals(ListAutomation(), restored.automation); assertEquals(account.people, restored.people)
        val foreign = JSONObject(AccountCodec.encode(account)); foreign.getJSONObject("listAutomation").getJSONArray("targets").put("999")
        assertThrows(Exception::class.java) { AccountCodec.decode(foreign.toString()) }
    }
    @Test fun serverLimitedCoverageIsNotRetriedAutomaticallyOnCountChangeOrDailyDeadline() {
        val initial=ListTarget("200",followers=ListScan(basisCount=2,basisAt=1000))
        val target=ListAutomationPolicy.record(initial,session,page(2000,listOf("1"),limited=true))
        val restored=FollowArchiveCodec.decode(FollowArchiveCodec.encode(ListArchive(session.userId,session.connectionBinding,listOf(target)))).targets.single()
        assertEquals(target,restored)
        assertTrue(ListAutomationPolicy.coverageHeld(restored,FollowDirection.FOLLOWERS))
        assertFalse(ListAutomationPolicy.coverageHeld(restored,FollowDirection.FOLLOWING))
        assertNull(AutomaticQueue.page(target,FollowDirection.FOLLOWERS,3000,basis=ListBasis(3,3000)))
        assertNull(AutomaticQueue.page(target,FollowDirection.FOLLOWERS,2001+ListAutomationPolicy.RESCAN_MS,basis=ListBasis(3,3000)))
    }
    @Test fun reconnectResetAndNewManualPagesCanResumeWithoutDiscardingOldCoverage() {
        val limited=ListAutomationPolicy.record(ListTarget("200"),session,page(2000,listOf("1"),limited=true))
        val reconnected=limited.copy(followers=ListScan())
        assertNotNull(AutomaticQueue.page(reconnected,FollowDirection.FOLLOWERS,3000,basis=ListBasis(3,3000)))
        val manual=ListAutomationPolicy.record(limited,session,page(4000,listOf("1"),next="next"))
        assertNotNull(AutomaticQueue.page(manual,FollowDirection.FOLLOWERS,4001,manual.snapshot!!.followers,ListBasis(3,4000)))
    }
    @Test fun stalledAndUnknownCoverageAlsoRequireAnExplicitRetry() {
        val first=ListAutomationPolicy.record(ListTarget("200"),session,page(1000,listOf("1"),next="c"))
        val stalled=ListAutomationPolicy.record(first,session,page(2000,listOf("2"),cursor="c",next="c"))
        assertNull(AutomaticQueue.page(stalled,FollowDirection.FOLLOWERS,2001+ListAutomationPolicy.RESCAN_MS,basis=ListBasis(3,3000)))
        val unknown=ListAutomationPolicy.record(ListTarget("200"),session,page(2000,listOf("1")).copy(hasMore=FollowField(true,true)))
        assertNull(AutomaticQueue.page(unknown,FollowDirection.FOLLOWERS,2001+ListAutomationPolicy.RESCAN_MS,basis=ListBasis(3,3000)))
    }
    @Test fun legacySingleTargetMigratesWithoutDiscardingCoverageAndCursor() {
        val snapshot = FollowSnapshot("100", session.mobileBinding, "200").record(page(1000, listOf("1", "2"), next = "cursor"))
        val migrated = FollowArchiveCodec.decode(FollowListCodec.encode(snapshot))
        assertEquals(snapshot, migrated.targets.single().snapshot)
        assertTrue(migrated.targets.single().followers.continuing)
        assertEquals("cursor", migrated.targets.single().snapshot!!.followers!!.cursor())
        assertEquals(migrated, FollowArchiveCodec.decode(FollowArchiveCodec.encode(migrated)))
        assertFalse(migrated.matches(session.copy(mobileBinding = "b".repeat(32))))
    }
    @Test fun multipleTargetsAndTheirPreviousBaselinesHaveOneStrictScope() {
        var first = ListAutomationPolicy.record(ListTarget("200"), session, page(1000, listOf("1")))
        first = ListAutomationPolicy.record(first, session, page(2000, listOf("2")))
        val second = ListAutomationPolicy.record(ListTarget("300"), session, page(3000, listOf("3"), target = "300"))
        val archive = ListArchive("100", session.mobileBinding, listOf(first, second))
        assertEquals(archive, FollowArchiveCodec.decode(FollowArchiveCodec.encode(archive)))
        val invalid = JSONObject(FollowArchiveCodec.encode(archive))
        invalid.getJSONArray("targets").getJSONObject(0).getJSONObject("previous").put("owner", "999")
        assertThrows(Exception::class.java) { FollowArchiveCodec.decode(invalid.toString()) }
    }
    @Test fun onlyTwoSeparateObservedEndsProduceIdentityBasedDifferences() {
        var target = ListAutomationPolicy.record(ListTarget("200"), session, page(1000, listOf("1", "2")))
        assertNull(ListAutomationPolicy.difference(target, FollowDirection.FOLLOWERS))
        target = ListAutomationPolicy.record(target, session, page(2000, listOf("2", "3")))
        val diff = ListAutomationPolicy.difference(target, FollowDirection.FOLLOWERS)!!
        assertEquals(listOf("3"), diff.added.map { it.id }); assertEquals(listOf("1"), diff.missing.map { it.id })
        val same = ListAutomationPolicy.record(target, session, page(3000, listOf("3", "2")))
        val unchanged = ListAutomationPolicy.difference(same, FollowDirection.FOLLOWERS)!!
        assertTrue(unchanged.added.isEmpty()); assertTrue(unchanged.missing.isEmpty())
    }
    @Test fun incompleteLimitedAndOverlappingReadsNeverProduceRemovalClaims() {
        val baseline = ListAutomationPolicy.record(ListTarget("200"), session, page(1000, listOf("1", "2")))
        val partial = ListAutomationPolicy.record(baseline, session, page(2000, listOf("3"), next = "cursor"))
        assertNull(ListAutomationPolicy.difference(partial, FollowDirection.FOLLOWERS))
        val limited = ListAutomationPolicy.record(partial, session, page(3000, listOf("3"), limited = true))
        assertNull(ListAutomationPolicy.difference(limited, FollowDirection.FOLLOWERS))
        assertEquals(baseline.snapshot!!.followers, limited.previous!!.followers)
        val completed = ListAutomationPolicy.record(limited, session, page(4000, listOf("3")))
        assertEquals(listOf("1", "2"), ListAutomationPolicy.difference(completed, FollowDirection.FOLLOWERS)!!.missing.map { it.id })
        val overlapping = ListAutomationPolicy.record(baseline, session, page(900, listOf("3")))
        assertNull(ListAutomationPolicy.difference(overlapping, FollowDirection.FOLLOWERS))
    }
    @Test fun partialFirstScanIsNotAnEmptyBaselineAndStalledCursorStopsContinuation() {
        val first = ListAutomationPolicy.record(ListTarget("200"), session, page(1000, listOf("1"), next = "c"))
        val end = ListAutomationPolicy.record(first, session, page(2000, listOf("2"), cursor = "c"))
        assertNull(ListAutomationPolicy.difference(end, FollowDirection.FOLLOWERS))
        val stalled = ListAutomationPolicy.record(first, session, page(2000, listOf("2"), cursor = "c", next = "c"))
        assertFalse(stalled.followers.continuing)
        assertEquals(FollowCoverage.STALLED, stalled.snapshot!!.followers!!.coverage())
        assertEquals(2000 + ListAutomationPolicy.RESCAN_MS, stalled.followers.nextAt)
    }
    @Test fun profileAndPageQueuesAlternateWithoutStarvationUnderTheSharedBudget() {
        val account = saved().copy(people = listOf(saved().people.single().copy(watching = true, nextCheckAt = 0)))
        val archive = ListArchive("100", session.mobileBinding)
        assertTrue(AutomaticQueue.next(account, archive, 4000000) is AutomaticTask.Page)
        assertTrue(AutomaticQueue.next(account.copy(automation = account.automation.copy(lastWorkWasList = true)), archive, 4000000) is AutomaticTask.Person)
        val noPeople = account.copy(people = emptyList(), automation = ListAutomation(true, setOf("100"), lastWorkWasList = true))
        assertTrue(AutomaticQueue.next(noPeople, archive, 4000000) is AutomaticTask.Own)
        assertThrows(Exception::class.java) { AutomaticQueue.next(account, archive.copy(owner = "999"), 4000000) }
    }
    @Test fun onlyTheExactContinuingRunCanBypassTheNextRoundDeadline() {
        val target = ListAutomationPolicy.record(ListTarget("200"), session, page(1000, listOf("1"), next = "next"))
        val direction = FollowDirection.FOLLOWERS
        val run = target.snapshot!!.followers!!
        assertNull(AutomaticQueue.page(target, direction, 2000))
        assertEquals("next", AutomaticQueue.page(target, direction, 2000, run)!!.cursor)
        val restarted = ListAutomationPolicy.record(target, session, page(3000, listOf("2"), next = "new"))
        assertNull(AutomaticQueue.page(restarted, direction, 4000, run))
        val ended = ListAutomationPolicy.record(target, session, page(3000, listOf("2"), cursor = "next"))
        assertNull(AutomaticQueue.page(ended, direction, 4000, ended.snapshot!!.followers))
        val capacity = target.withScan(direction, target.followers.copy(failure = ApiFailure.Kind.CAPACITY))
        assertNull(AutomaticQueue.page(capacity, direction, 2000, run))
    }
    @Test fun capacityPausedDirectionIsSkippedWhileOtherWorkCanContinue() {
        val account = saved().copy(automation = ListAutomation(true, setOf("200")))
        val archive = ListArchive("100", session.mobileBinding, listOf(ListTarget("200", followers = ListScan(failure = ApiFailure.Kind.CAPACITY))))
        val next = AutomaticQueue.next(account, archive, 10000) as AutomaticTask.Page
        assertEquals(FollowDirection.FOLLOWING, next.direction)
    }
}
