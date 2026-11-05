package dev.kaankaya.instagramtracker

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class ForegroundListScanTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val vault = SessionVault(context, "foreaccount")
    private val lists = FollowListVault(context, "forelists")
    private val preferences = context.getSharedPreferences("foregate", 0)
    private val session = Session("100", "", "synthetic", "a".repeat(32))
    private fun gate() = RequestGate(context, "foregate", System::currentTimeMillis, { RequestDefaults.MAX_SPACING_MS })
    private var now = System.currentTimeMillis()
    private var count = 0
    private var pause: () -> Unit = {}
    private var cancelled = false
    private val runtime = object : ReadRuntime {
        override fun now() = now
        override fun elapsed() = now
        override fun pause(milliseconds: Long, cancelled: () -> Boolean): Boolean { now += milliseconds; pause(); return !cancelled() }
    }
    @Before fun setup() {
        check(context.packageName.endsWith(".debug")); lists.clear(); preferences.edit().clear().commit()
        now = System.currentTimeMillis(); count = 0; pause = {}; cancelled = false
        vault.write(SavedAccount(session, Profile("100", "own", 1, 1, 1000), listOf(TrackedPerson(listOf(Profile("200", "friend", 100, 10, 1000))))))
    }
    @After fun cleanup() { vault.clear(); lists.clear(); preferences.edit().clear().commit() }
    private fun page(cursor: String?, ids: List<String>, next: String? = null) = FollowPage("200", FollowDirection.FOLLOWERS, cursor,
        ids.map { FollowUser(it, "member$it", null, null, null, null) }, FollowField(true, next), FollowField(true, false),
        FollowField(true, next != null), FollowField(false, null), now)
    private fun scan(produce: (String?) -> FollowPage) = ForegroundListScan(vault, lists, gate(), object : FollowListSource {
        override fun cancel() = Unit
        override fun read(session: Session, target: String, direction: FollowDirection, cursor: String?): FollowPage = gate().claim().use {
            gate().begin(now); count++; produce(cursor)
        }
    }, runtime)
    @Test fun aHundredFollowersStreamAndCommitInOneSpacedActionWithoutChangingPeople() {
        val before = vault.read(); val starts = mutableListOf<Long>(); val progress = mutableListOf<Int>()
        val result = scan { cursor -> starts += now; page(cursor, (if (cursor == null) 1..50 else 51..100).map { it.toString() }, if (cursor == null) "second" else null) }
            .read(session, "200", FollowDirection.FOLLOWERS, null, cancelled = { cancelled }, progress = { progress += it.followers!!.users.size })
        assertEquals(listOf(50, 100), progress); assertEquals(RequestDefaults.MAX_SPACING_MS, starts[1] - starts[0]); assertEquals(2, count)
        assertEquals(FollowCoverage.END_OBSERVED, result.followers!!.coverage()); assertEquals(before, vault.read())
        assertEquals(result, FollowListVault(context, "forelists").read(session, "200"))
    }
    @Test fun sixPageAndNinetySecondBudgetsLeaveTheSavedContinuationIntact() {
        val scanner = scan { cursor -> page(cursor, listOf(count.toString()), "next$count") }
        val result = scanner.read(session, "200", FollowDirection.FOLLOWERS, null, cancelled = { cancelled }, progress = {})
        assertEquals(6, count); assertEquals("next6", result.followers!!.cursor())
        setup()
        val slow = scan { cursor -> now += 35000; page(cursor, listOf(count.toString()), "next$count") }
        val partial = slow.read(session, "200", FollowDirection.FOLLOWERS, null, cancelled = { cancelled }, progress = {})
        assertEquals(3, count); assertEquals("next3", partial.followers!!.cursor())
    }
    @Test fun a429OnTheSecondPageStopsAndKeepsTheCommittedFirstPage() {
        val scanner = scan { cursor ->
            if (count == 2) { gate().limited(null, now); throw ApiFailure(ApiFailure.Kind.LIMITED) }
            page(cursor, listOf("1"), "next")
        }
        assertEquals(ApiFailure.Kind.LIMITED, assertThrows(ApiFailure::class.java) {
            scanner.read(session, "200", FollowDirection.FOLLOWERS, null, cancelled = { cancelled }, progress = {})
        }.kind)
        assertEquals(2, count); assertEquals(listOf("1"), lists.read(session, "200")!!.followers!!.users.map { it.id }); assertTrue(gate().manualOnly())
    }
    @Test fun cancellationClearAndSessionReplacementDuringTheGapNeverStartAnotherPage() {
        for (action in listOf("cancel", "clear", "session")) {
            setup(); pause = { pause = {}; when (action) {
                "cancel" -> cancelled = true
                "clear" -> lists.clear()
                else -> vault.write(vault.read()!!.copy(session = session.copy(userAgent = "new")))
            } }
            val scanner = scan { cursor -> page(cursor, listOf("1"), "next") }
            assertEquals(ApiFailure.Kind.CANCELLED, assertThrows(ApiFailure::class.java) {
                scanner.read(session, "200", FollowDirection.FOLLOWERS, null, cancelled = { cancelled }, progress = {})
            }.kind)
            assertEquals(action, 1, count)
        }
    }
    @Test fun newerManualSnapshotIsNotOverwrittenByALatePage() {
        val scanner = scan { cursor ->
            lists.commit(session, page(null, listOf("99")).copy(observedAt = now + 1))
            page(cursor, listOf("1"), "next").copy(observedAt = now + 2)
        }
        assertEquals(ApiFailure.Kind.CANCELLED, assertThrows(ApiFailure::class.java) {
            scanner.read(session, "200", FollowDirection.FOLLOWERS, null, cancelled = { cancelled }, progress = {})
        }.kind)
        assertEquals(listOf("99"), lists.read(session, "200")!!.followers!!.users.map { it.id }); assertEquals(1, count)
    }
    @Test fun aLimitedOrStalledSuccessfulResponseStopsWithoutAnExtraPage() {
        for (kind in listOf("limited", "stalled")) {
            setup()
            val scanner = scan { cursor -> if (kind == "limited") page(cursor, listOf("1"), "next").copy(limited = FollowField(true, true)) else page(cursor, emptyList(), "next") }
            val result = scanner.read(session, "200", FollowDirection.FOLLOWERS, null, cancelled = { cancelled }, progress = {})
            assertEquals(1, count); assertNotEquals(FollowCoverage.MORE, result.followers!!.coverage())
        }
    }
    @Test fun anExistingManualPauseAllowsOneExplicitPageButNeverContinuesAutomatically() {
        for (restriction in listOf(false, true)) {
            setup()
            if (restriction) gate().limited(null, now - 2 * 60 * 60 * 1000) else gate().pauseAutomatic()
            val scanner = scan { cursor -> page(cursor, listOf("1"), "next") }
            val result = scanner.read(session, "200", FollowDirection.FOLLOWERS, null, cancelled = { cancelled }, progress = {})
            assertEquals(1, count); assertEquals("next", result.followers!!.cursor()); assertTrue(gate().manualOnly())
            assertEquals(restriction, gate().restrictionPending())
        }
    }
}
