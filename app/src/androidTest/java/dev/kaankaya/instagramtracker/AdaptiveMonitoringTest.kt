package dev.kaankaya.instagramtracker

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.ByteArrayInputStream
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection

/** Completed production workflows with a deterministic clock and offline HTTP/list transports. */
class AdaptiveMonitoringTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val vault = SessionVault(context, "adaptivetest")
    private val lists = FollowListVault(context, "adaptivelists")
    private val session = Session("100", "ds_user_id=100; sessionid=synthetic", "test-UA")
    private var now = 2_000_000_000_000L
    private val runtime = object : ReadRuntime {
        override fun now() = now
        override fun elapsed() = now
        override fun pause(milliseconds: Long, cancelled: () -> Boolean): Boolean { now += milliseconds; return !cancelled() }
    }
    private val gate = RequestGate(context, "adaptive_gate", { now }, { RequestDefaults.MAX_SPACING_MS })
    private var followers = 2L
    private var following = 1L
    private var http = 200
    private var profileCalls = 0
    private val listCalls = mutableListOf<Pair<FollowDirection, Long>>()
    private val changes = mutableListOf<ObservedProfileChange>()
    private var missingId = "1"
    private var coverage = FollowCoverage.END_OBSERVED
    private fun profile() = Profile("200", "friend", 2, 1, now - 120_000)
    private fun saved() = SavedAccount(session, Profile("100", "own", 1, 1, now - 120_000),
        listOf(TrackedPerson(listOf(profile()), "private note", "group", true, 15, 0, true)), ListAutomation(true, setOf("200")))
    @Before fun setup() {
        check(context.packageName.endsWith(".debug")); now = 2_000_000_000_000L
        context.getSharedPreferences("adaptive_gate", 0).edit().clear().commit()
        vault.clear(); lists.clear(); profileCalls = 0; listCalls.clear(); changes.clear(); followers = 2; following = 1; http = 200
        coverage = FollowCoverage.END_OBSERVED; missingId = "1"; vault.write(saved())
    }
    @After fun cleanup() { vault.clear(); lists.clear(); context.getSharedPreferences("adaptive_gate", 0).edit().clear().commit() }
    private inner class Connection(url: URL) : HttpsURLConnection(url) {
        override fun getResponseCode(): Int { profileCalls++; return http }
        override fun getInputStream() = ByteArrayInputStream("""{"status":"ok","user":{"pk":"200","username":"friend","follower_count":$followers,"following_count":$following,"biography":"bio","profile_pic_id":"photo"}}""".toByteArray())
        override fun getErrorStream() = ByteArrayInputStream("{}".toByteArray())
        override fun disconnect() = Unit
        override fun connect() = Unit
        override fun usingProxy() = false
        override fun getCipherSuite() = "synthetic"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate>? = null
    }
    private fun page(direction: FollowDirection, at: Long, ids: List<String>) = FollowPage("200", direction, null,
        ids.map { FollowUser(it, "person$it", null, null, null, null) }, FollowField(true, null), FollowField(true, false),
        FollowField(true, false), FollowField(false, null), at)
    private fun baseline() {
        val p = profile()
        for (direction in FollowDirection.entries) lists.commitCurrent(session,
            page(direction, now - 1000, if (direction == FollowDirection.FOLLOWERS) listOf("1", "2") else listOf("3")), null, ListBasis.of(p, direction))
    }
    private fun engine() = AutomaticEngine(vault, lists, gate, InstagramApi(gate) { Connection(it) }, object : FollowListSource {
        override fun cancel() = Unit
        override fun read(session: Session, target: String, direction: FollowDirection, cursor: String?): FollowPage {
            gate.claim().use { gate.begin(); gate.observedStatus(200); gate.succeeded() }
            assertNull(cursor); listCalls += direction to now
            val page = page(direction, now, if (direction == FollowDirection.FOLLOWERS) listOf(missingId, "2", "4").take(followers.toInt()) else listOf("3"))
            return when (coverage) {
                FollowCoverage.UNKNOWN -> page.copy(next = FollowField(false, null), hasMore = FollowField(false, null))
                FollowCoverage.LIMITED -> page.copy(limited = FollowField(true, true))
                else -> page
            }
        }
    }, { true }, { changes += it }, { _, _, _ -> }, runtime)

    @Test fun unchangedCountsSkipListsButStillRetainCheckAndComparisonTimes() {
        baseline(); val before = lists.archive(session)
        engine().quickCycle(session, "200")
        assertEquals(1, profileCalls); assertTrue(listCalls.isEmpty()); assertEquals(before, lists.archive(session))
        val check = vault.journal.read(session).single()
        assertEquals(CheckKind.PROFILE, check.kind); assertEquals(0L, check.followersDelta); assertEquals(0L, check.followingDelta)
        assertEquals(now, check.startedAt); assertEquals(now, check.observedAt); assertEquals(now, check.analyzedAt)
        assertEquals(profile().observedAt, check.previousAt)
        assertEquals("private note", vault.read()!!.people.single().note); assertTrue(vault.read()!!.people.single().favorite)
        now += 59_000; engine().quickCycle(session, "200"); assertEquals(1, profileCalls)
        now += 1000; engine().quickCycle(session, "200"); assertEquals(2, profileCalls); assertTrue(listCalls.isEmpty())
    }
    @Test fun followerChangeReadsOnlyFollowersAndKeepsTheOtherListAndBaseline() {
        baseline(); val before = lists.read(session, "200")!!.following
        followers = 3
        val start = now; engine().quickCycle(session, "200")
        assertEquals(1, profileCalls); assertEquals(listOf(FollowDirection.FOLLOWERS), listCalls.map { it.first })
        assertEquals(start + RequestDefaults.MAX_SPACING_MS, listCalls.single().second); assertEquals(before, lists.read(session, "200")!!.following)
        assertEquals(3L, lists.target(session, "200")!!.followers.basisCount)
        val difference = ListAutomationPolicy.difference(lists.target(session, "200")!!, FollowDirection.FOLLOWERS)!!
        assertEquals(listOf("4"), difference.added.map { it.id }); assertTrue(difference.missing.isEmpty())
        val journal = vault.journal.read(session); assertEquals(listOf(CheckKind.PROFILE, CheckKind.LIST), journal.map { it.kind })
        assertEquals(1, journal.last().added); assertEquals(0, journal.last().missing); assertEquals(start + RequestDefaults.MAX_SPACING_MS, journal.last().analyzedAt)
        assertEquals(listOf("4"), journal.last().newlySeen.map { it.id })
    }
    @Test fun equalCountMembershipChangesAreFoundByDailyScan() {
        baseline(); missingId = "5"; now += ListAutomationPolicy.RESCAN_MS
        engine().quickCycle(session, "200")
        assertEquals(setOf(FollowDirection.FOLLOWERS, FollowDirection.FOLLOWING), listCalls.map { it.first }.toSet())
        val difference = ListAutomationPolicy.difference(lists.target(session, "200")!!, FollowDirection.FOLLOWERS)!!
        assertEquals(listOf("5"), difference.added.map { it.id }); assertEquals(listOf("1"), difference.missing.map { it.id })
    }
    @Test fun oneBackgroundReservationChecksMultipleDuePeopleWithoutFifteenMinutesBetweenThem() {
        val first = saved().people.single()
        vault.write(saved().copy(automation = ListAutomation(), people = listOf(first, first.copy(history = listOf(profile().copy(userId = "201"))))))
        // A target-aware synthetic response is needed for the second person.
        val starts = mutableListOf<Long>()
        val api = InstagramApi(gate) { url -> object : HttpsURLConnection(url) {
            override fun getResponseCode(): Int { starts += now; return 200 }
            override fun getInputStream() = ByteArrayInputStream("""{"status":"ok","user":{"pk":"${url.path.split('/').filter { it.isNotEmpty() }[3]}","username":"friend","follower_count":2,"following_count":1}}""".toByteArray())
            override fun disconnect() = Unit; override fun connect() = Unit; override fun usingProxy() = false
            override fun getCipherSuite() = "synthetic"; override fun getLocalCertificates(): Array<Certificate>? = null; override fun getServerCertificates(): Array<Certificate>? = null
        } }
        val noLists = object : FollowListSource { override fun cancel() = Unit; override fun read(session: Session, target: String, direction: FollowDirection, cursor: String?): FollowPage = error("No list expected") }
        val start = now
        AutomaticEngine(vault, lists, gate, api, noLists, { true }, {}, { _, _, _ -> }, runtime).cycle()
        assertEquals(listOf(start, start + RequestDefaults.MAX_SPACING_MS), starts); assertEquals(2L, gate.attempts())
        // The next automatic slot follows the selected profile check interval, not the 15-minute job period.
        assertEquals(start + gate.monitorIntervalMillis(), gate.automaticNextAllowedAt())
        assertEquals(2, vault.journal.read(session).size)
    }
    @Test fun restrictionAndCancellationDoNotReadListsOrClearExistingData() {
        baseline(); val before = lists.archive(session); http = 429
        try { engine().quickCycle(session, "200"); fail("Expected 429") } catch (error: ApiFailure) { assertEquals(ApiFailure.Kind.LIMITED, error.kind) }
        val pause = gate.blockedUntil(); assertTrue(gate.manualOnly()); assertTrue(listCalls.isEmpty()); assertEquals(before, lists.archive(session))
        assertEquals(CheckKind.FAILURE, vault.journal.read(session).single().kind)
        try { engine().quickCycle(session, "200") } catch (_: ApiFailure) { }
        assertEquals(1, profileCalls); assertEquals(pause, gate.blockedUntil()); assertEquals(1L, gate.attempts())
    }
    @Test fun profileFirstThenRelevantListCompletesInOneBackgroundRound() {
        baseline(); followers = 3; val start = now
        engine().cycle()
        assertEquals(1, profileCalls); assertEquals(listOf(FollowDirection.FOLLOWERS), listCalls.map { it.first })
        assertEquals(start + RequestDefaults.MAX_SPACING_MS, listCalls.single().second)
        assertEquals(2L, gate.attempts()); assertEquals(1L, changes.single().followersDelta)
    }
    @Test fun journalIsBoundedOwnerScopedAndDeletedWithAccount() {
        // Seed the encrypted production boundary once instead of doing 2,000 Keystore rewrites.
        val events=org.json.JSONArray().apply {for(i in 1..CheckJournal.MAX_EVENTS)put(org.json.JSONObject()
            .put("target","200").put("username","friend").put("kind","PROFILE").put("observed",i).put("analyzed",i).put("fields",org.json.JSONArray()))}
        EncryptedRecord(context,"adaptivetestchecks",2*1024*1024).write(org.json.JSONObject().put("version",1).put("owner",session.userId).put("events",events).toString())
        assertEquals(CheckJournal.MAX_EVENTS,vault.journal.read(session).size)
        val next=CheckJournal.MAX_EVENTS+1L
        assertTrue(vault.journal.append(session,CheckEvent("200","friend",CheckKind.PROFILE,null,next,next)))
        assertEquals(CheckJournal.MAX_EVENTS, vault.journal.read(session).size); assertEquals(2L, vault.journal.read(session).first().observedAt)
        assertTrue(vault.journal.read(session.copy(userId = "999")).isEmpty())
        vault.journal.clearTarget(session, "200"); assertTrue(vault.journal.read(session).isEmpty())
        assertTrue(vault.journal.append(session, CheckEvent("200", "friend", CheckKind.PROFILE, null, now, now)))
        vault.clear(); assertTrue(vault.journal.read(session).isEmpty())
    }
    @Test fun uncertainListStopsTheWholeBackgroundRoundAndQuickWatchReportsItWithoutRetrying() {
        baseline(); followers = 3; coverage = FollowCoverage.UNKNOWN
        engine().cycle()
        assertEquals(1, profileCalls); assertEquals(1, listCalls.size)
        assertEquals(FollowCoverage.UNKNOWN, lists.read(session, "200")!!.followers!!.coverage())
        // A fresh explicit session must stop on a limited result too, preserving the page it received.
        lists.clear(); listCalls.clear(); coverage = FollowCoverage.LIMITED; now += MonitorPolicy.POLL_MS
        try { engine().quickCycle(session, "200"); fail("Expected limited/uncertain result") }
        catch (error: ApiFailure) { assertEquals(ApiFailure.Kind.INVALID_RESPONSE, error.kind) }
        assertEquals(1, listCalls.size); assertEquals(FollowCoverage.LIMITED, lists.read(session, "200")!!.followers!!.coverage())
    }
    @Test fun malformedJournalAppendCannotDestroyPreviousHistoryOrProfileState() {
        val before = vault.read(); val event = CheckEvent("200", "friend", CheckKind.PROFILE, null, now, now)
        assertTrue(vault.journal.append(session, event))
        assertFalse(vault.journal.append(session, event.copy(analyzedAt = -1)))
        assertEquals(listOf(event), vault.journal.read(session)); assertEquals(before, vault.read())
        assertTrue(CheckJournal(context, "adaptivetestchecks").writeFailed)
    }
    @Test fun metadataGapRetainsTheEarlierComparableObservationInHistoryAndNotification() {
        val old = profile().copy(observedAt = now - 300_000,
            details = ProfileDetails(biography = ProfileText.known("before")))
        val unknown = profile().copy(observedAt = now - 120_000)
        val current = saved(); vault.write(current.copy(people = listOf(current.people.single().copy(history = listOf(old, unknown)))))
        engine().quickCycle(session, "200")
        val change = changes.single()
        assertEquals(setOf(ProfileChangeField.BIOGRAPHY), change.edits.map { it.field }.toSet())
        assertEquals(old.observedAt, change.previousAt)
        assertEquals(old.observedAt, vault.journal.read(session).first().previousAt)
    }
    @Test fun profileOnlyBackgroundFailureIsRecordedWithoutEnablingListAutomation() {
        vault.write(saved().copy(automation = ListAutomation())); http = 429
        try { engine().cycle(); fail("Expected 429") } catch (failure: ApiFailure) { assertEquals(ApiFailure.Kind.LIMITED, failure.kind) }
        assertEquals(1, profileCalls); assertTrue(listCalls.isEmpty())
        val failure = vault.journal.read(session).single()
        assertEquals(CheckKind.FAILURE, failure.kind); assertNull(failure.observedAt)
        assertEquals(ApiFailure.Kind.LIMITED, failure.failure)
        assertFalse(vault.read()!!.automation.enabled)
    }

}
