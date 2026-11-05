package dev.kaankaya.instagramtracker

import android.app.Notification
import android.app.NotificationManager
import android.app.job.JobScheduler
import android.os.Build
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.ByteArrayInputStream
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection

/** Production queue, Keystore, reservations and notifications; every transport is synthetic. */
class AutomaticFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val vault = SessionVault(context, "autotest")
    private val lists = FollowListVault(context, "autolists")
    private val preferences = context.getSharedPreferences("auto_test_gate", 0)
    private fun gate() = RequestGate(context, "auto_test_gate", System::currentTimeMillis, { RequestDefaults.MAX_SPACING_MS })
    private val session = Session("100", "", "test-UA", "a".repeat(32))
    private var now = System.currentTimeMillis()
    private var phase = 0
    private var requests = 0
    private var onPause: () -> Unit = {}
    private val runtime = object : ReadRuntime {
        override fun now() = now
        override fun elapsed() = now
        override fun pause(milliseconds: Long, cancelled: () -> Boolean): Boolean {
            now += milliseconds; onPause(); return !cancelled()
        }
    }
    private fun saved() = SavedAccount(session, Profile("100", "own", 3, 1, 1000),
        listOf(TrackedPerson(listOf(Profile("200", "friend", 3, 1, 1000)), "note", "group", favorite = true)),
        ListAutomation(true, setOf("200")))
    @Before fun prepare() {
        check(context.packageName.endsWith(".debug")); preferences.edit().clear().commit(); lists.clear()
        now = System.currentTimeMillis(); phase = 0; requests = 0; onPause = {}; vault.write(saved())
    }
    @After fun cleanup() {
        vault.clear(); lists.clear(); preferences.edit().clear().commit(); ChangeNotifications(context,vaultForOwner={vault}).clear()
        PreviewMode.cancel(context); SessionVault(context, "previewaccount").clear(); FollowListVault(context, "previewlists").clear()
        context.getSharedPreferences("preview_gate", 0).edit().clear().commit()
    }
    private fun page(target: String, direction: FollowDirection, cursor: String?, ids: List<String>, next: String? = null, time: Long = now) =
        FollowPage(target, direction, cursor, ids.map { FollowUser(it, "synthetic$it", null, null, null, null) },
            FollowField(true, next), FollowField(true, false), FollowField(true, next != null), FollowField(false, null), time)
    private fun source(produce: (String, FollowDirection, String?) -> FollowPage) = object : FollowListSource {
        override fun cancel() = Unit
        override fun read(session: Session, target: String, direction: FollowDirection, cursor: String?): FollowPage = gate().claim().use {
            gate().begin(now); requests++; gate().observedStatus(200); produce(target, direction, cursor)
        }
    }
    private fun api() = InstagramApi(gate()) { throw AssertionError("This test must not send a profile request") }
    private fun engine(source: FollowListSource, notify: (Session, Profile, ListDifference) -> Unit = { _, _, _ -> }) =
        AutomaticEngine(vault, lists, gate(), api(), source, { true }, { throw AssertionError("No profile notification expected") }, notify, runtime)
    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()
    }
    private fun await(message: String, timeout: Long = 5000, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < deadline) { if (predicate()) return; Thread.sleep(30) }
        fail(message)
    }
    @Test fun pagesResumeAcrossEngineInstancesAndTwoCompletedCyclesPostPrivateListDifferences() {
        if (Build.VERSION.SDK_INT >= 33) shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        val notices = ChangeNotifications(context,vaultForOwner={vault}); notices.ensureChannel()
        val transport = source { target, direction, cursor ->
            if (direction == FollowDirection.FOLLOWING) page(target, direction, cursor, if (phase == 0) listOf("2") else listOf("4"))
            else if (cursor == null) page(target, direction, cursor, if (phase == 0) listOf("1", "2") else listOf("2", "4"), "second")
            else { assertEquals("second", cursor); page(target, direction, cursor, if (phase == 0) listOf("2", "3") else listOf("4", "5")) }
        }
        fun step() {
            val engine = engine(transport, notices::postList); engine.tick(now)
            val count = requests; engine.tick(now); assertEquals(count, requests)
            now += MonitorPolicy.POLL_MS
        }
        step()
        assertEquals(3, requests)
        assertEquals(listOf("1", "2", "3"), FollowListVault(context, "autolists").read(session, "200")!!.followers!!.users.map { it.id })
        assertTrue(context.getSystemService(NotificationManager::class.java).activeNotifications.none { it.tag.startsWith("list:") })
        phase = 1; now += ListAutomationPolicy.RESCAN_MS
        step()
        assertEquals(6, requests); assertEquals(6L, gate().attempts())
        val target = FollowListVault(context, "autolists").target(session, "200")!!
        val difference = ListAutomationPolicy.difference(target, FollowDirection.FOLLOWERS)!!
        assertEquals(listOf("4", "5"), difference.added.map { it.id }); assertEquals(listOf("1", "3"), difference.missing.map { it.id })
        val manager = context.getSystemService(NotificationManager::class.java)
        await("List difference notifications were not delivered") { manager.activeNotifications.count { it.tag.startsWith("list:100:200:") } == 2 }
        val notification = manager.activeNotifications.single { it.tag == "list:100:200:followers" }.notification
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        assertTrue(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("Yeni görülen hesaplar: 2"))
        assertFalse(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("synthetic"))
        assertFalse(notification.publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("friend"))
        assertTrue(vault.read()!!.people.single().favorite); assertEquals("note", vault.read()!!.people.single().note)
    }
    @Test fun targetCachesCoexistAndLegacyEncryptedDataMigratesOnTheNextWrite() {
        val first = FollowSnapshot("100", session.mobileBinding, "200").record(page("200", FollowDirection.FOLLOWERS, null, listOf("1")))
        EncryptedRecord(context, "autolists").write(FollowListCodec.encode(first))
        assertEquals(first, lists.read(session, "200"))
        lists.commit(session, page("100", FollowDirection.FOLLOWING, null, listOf("2"), time = now + 1))
        assertEquals(first, FollowListVault(context, "autolists").read(session, "200"))
        assertNotNull(lists.read(session, "100")); assertEquals(2, lists.archive(session).targets.size)
        assertNull(lists.read(session.copy(userId = "999"), "200"))
        assertNull(lists.read(session.copy(mobileBinding = "b".repeat(32)), "200"))
        lists.clearTarget(session, "100"); assertEquals(first, lists.read(session, "200"))
    }
    @Test fun aHundredFollowersAndFollowingFinishInOneSpacedRound() {
        val order = mutableListOf<Pair<FollowDirection, Long>>()
        val start = now
        val transport = source { id, direction, cursor ->
            order += direction to now
            if (direction == FollowDirection.FOLLOWING) page(id, direction, cursor, listOf("999"))
            else page(id, direction, cursor, (if (cursor == null) 1..50 else 51..100).map { it.toString() },
                if (cursor == null) "page2" else null)
        }
        engine(transport).tick(now)
        assertEquals(listOf(FollowDirection.FOLLOWERS, FollowDirection.FOLLOWING, FollowDirection.FOLLOWERS), order.map { it.first })
        assertEquals(listOf(start, start + 10000, start + 20000), order.map { it.second })
        val snapshot = lists.read(session, "200")!!
        assertEquals(100, snapshot.followers!!.users.size); assertEquals(FollowCoverage.END_OBSERVED, snapshot.followers!!.coverage())
        assertEquals(FollowCoverage.END_OBSERVED, snapshot.following!!.coverage())
        engine(transport).tick(now); assertEquals(3, requests)
    }
    @Test fun sixPageCapResumesSavedCursorsAfterRestartWithoutSwitchingTargets() {
        vault.write(saved().copy(people = saved().people + TrackedPerson(listOf(Profile("300", "other", 1, 1, 1000))),
            automation = ListAutomation(true, linkedSetOf("200", "300"))))
        val transport = source { id, direction, cursor ->
            assertEquals("200", id)
            page(id, direction, cursor, listOf(requests.toString()), "cursor$requests")
        }
        engine(transport).tick(now); assertEquals(6, requests); assertNull(lists.read(session, "300"))
        val snapshot = lists.read(session, "200")!!
        assertEquals(3, snapshot.followers!!.pages.size); assertEquals(3, snapshot.following!!.pages.size)
        // Keep only this target selected so the next round can prove persisted continuation.
        vault.update(session) { it.copy(automation = it.automation.copy(targets = setOf("200"))) }
        now += MonitorPolicy.POLL_MS
        var resumed: String? = null
        val second = source { id, direction, cursor ->
            if (resumed == null) resumed = cursor
            page(id, direction, cursor, listOf("99"))
        }
        engine(second).tick(now)
        assertEquals("cursor5", resumed); assertEquals(8, requests)
        assertEquals(4, FollowListVault(context, "autolists").read(session, "200")!!.followers!!.pages.size)
    }
    @Test fun uncertainLimitedAndStalledPagesStopTheWholeRound() {
        for (coverage in listOf(FollowCoverage.UNKNOWN, FollowCoverage.LIMITED, FollowCoverage.STALLED)) {
            prepare()
            val transport = source { id, direction, cursor ->
                when (coverage) {
                    FollowCoverage.UNKNOWN -> page(id, direction, cursor, listOf("1")).copy(next = FollowField(false, null), hasMore = FollowField(false, null))
                    FollowCoverage.LIMITED -> page(id, direction, cursor, listOf("1")).copy(limited = FollowField(true, true))
                    else -> page(id, direction, cursor, emptyList(), "empty")
                }
            }
            engine(transport).tick(now); assertEquals(1, requests)
            assertEquals(coverage, lists.read(session, "200")!!.followers!!.coverage()); assertNull(lists.read(session, "200")!!.following)
        }
    }
    @Test fun secondRequest429PreservesTheFirstPageAndStopsContinuation() {
        val transport = source { id, direction, cursor ->
            if (requests == 2) { gate().limited(null, now); throw ApiFailure(ApiFailure.Kind.LIMITED) }
            page(id, direction, cursor, listOf("1"), "next")
        }
        assertEquals(ApiFailure.Kind.LIMITED, assertThrows(ApiFailure::class.java) { engine(transport).tick(now) }.kind)
        assertEquals(2, requests); assertEquals(1, lists.read(session, "200")!!.followers!!.pages.size)
        assertNull(lists.read(session, "200")!!.following); assertTrue(gate().manualOnly())
        now += ListAutomationPolicy.RESCAN_MS; engine(transport).tick(now); assertEquals(2, requests)
    }
    @Test fun changesDuringTheGapStopBeforeTheNextRequestAndKeepCommittedPages() {
        for (action in listOf("cancel", "disable", "session", "clear", "manual", "hold")) {
            prepare(); var cancelled = false
            onPause = {
                onPause = {}
                when (action) {
                    "cancel" -> cancelled = true
                    "disable" -> vault.update(session) { it.copy(automation = ListAutomation()) }
                    "session" -> vault.write(saved().copy(session = session.copy(userAgent = "new-UA")))
                    "clear" -> lists.clear()
                    "manual" -> lists.commit(session, page("200", FollowDirection.FOLLOWERS, null, listOf("99"), time = now + 1))
                    "hold" -> gate().setMobileLinkActive(true)
                }
            }
            engine(source { id, direction, cursor -> page(id, direction, cursor, listOf("1"), "next") }).tick(now, { cancelled })
            assertEquals(action, 1, requests)
            if (action != "clear") assertEquals(if (action == "manual") listOf("99") else listOf("1"),
                lists.read(session, "200")!!.followers!!.users.map { it.id })
        }
    }
    @Test fun elapsedRoundBudgetStopsNewRequestsEvenBeforeThePageCap() {
        val transport = source { id, direction, cursor -> now += 35000; page(id, direction, cursor, listOf(requests.toString()), "next$requests") }
        engine(transport).tick(now)
        assertEquals(3, requests); assertEquals(2, lists.read(session, "200")!!.followers!!.pages.size)
    }
    @Test fun aBusyManualRequestEndsTheRoundWithoutDiscardingTheSavedCursor() {
        var lease: AutoCloseable? = null
        onPause = { onPause = {}; lease = gate().claim() }
        try {
            val transport = source { id, direction, cursor -> page(id, direction, cursor, listOf("1"), "next") }
            assertEquals(ApiFailure.Kind.TOO_SOON, assertThrows(ApiFailure::class.java) { engine(transport).tick(now) }.kind)
            assertEquals(1, requests); val target = lists.target(session, "200")!!
            assertTrue(target.followers.continuing); assertNull(target.following.failure)
            assertTrue(target.following.nextAt < now + ListAutomationPolicy.RESCAN_MS)
        } finally { lease?.close() }
    }
    @Test fun serverRestrictionSurvivesRestartAndStopsFurtherAutomaticRequests() {
        val transport = source { _, _, _ -> gate().observedStatus(429); gate().limited("3600", now); throw ApiFailure(ApiFailure.Kind.LIMITED) }
        assertEquals(ApiFailure.Kind.LIMITED, assertThrows(ApiFailure::class.java) { engine(transport).tick(now) }.kind)
        assertTrue(gate().manualOnly()); assertEquals(ApiFailure.Kind.LIMITED, lists.target(session, "200")!!.followers.failure)
        now += 3 * ListAutomationPolicy.RESCAN_MS
        engine(transport).tick(now); assertEquals(1, requests); assertNull(lists.read(session, "200"))
    }
    @Test fun failedContinuationKeepsItsPartialDataButStartsAFreshScanNextCycle() {
        lists.commit(session, page("200", FollowDirection.FOLLOWERS, null, listOf("1"), "stale", 1000))
        lists.commit(session, page("200", FollowDirection.FOLLOWING, null, listOf("2"), time = 2000))
        val before = lists.read(session, "200")
        val transport = source { _, _, cursor -> assertNull(cursor); throw ApiFailure(ApiFailure.Kind.INVALID_RESPONSE) }
        assertEquals(ApiFailure.Kind.INVALID_RESPONSE, assertThrows(ApiFailure::class.java) { engine(transport).tick(now) }.kind)
        assertEquals(before, lists.read(session, "200")); assertFalse(lists.target(session, "200")!!.followers.continuing)
        lists.commit(session, page("200", FollowDirection.FOLLOWING, null, listOf("2"), time = now + ListAutomationPolicy.RESCAN_MS))
        val next = AutomaticQueue.next(vault.read()!!, lists.archive(session), now + ListAutomationPolicy.RESCAN_MS) as AutomaticTask.Page
        assertEquals(FollowDirection.FOLLOWERS, next.direction); assertNull(next.cursor); assertEquals(1, requests)
    }
    @Test fun disablingDeletingChangingSessionAndCancellationDiscardLatePages() {
        for (action in listOf("disable", "delete", "session", "cancel")) {
            prepare(); var cancelled = false
            val transport = source { id, direction, cursor ->
                when (action) {
                    "disable" -> vault.update(session) { it.copy(automation = it.automation.copy(enabled = false)) }
                    "delete" -> { vault.update(session) { it.copy(people = emptyList(), automation = ListAutomation()) }; lists.clearTarget(session, id) }
                    "session" -> vault.write(saved().copy(session = session.copy(userAgent = "replaced-UA")))
                    else -> cancelled = true
                }
                page(id, direction, cursor, listOf("1"))
            }
            assertEquals(ApiFailure.Kind.CANCELLED, assertThrows(ApiFailure::class.java) { engine(transport).tick(now, { cancelled }) }.kind)
            assertNull(lists.read(session, "200")); assertEquals(1, requests)
        }
    }
    @Test fun aManualRestartDuringAutomaticReadKeepsTheNewerManualSnapshot() {
        val manual = page("200", FollowDirection.FOLLOWERS, null, listOf("99"), time = now + 1)
        val transport = source { id, direction, cursor -> lists.commit(session, manual); page(id, direction, cursor, listOf("1"), time = now + 2) }
        assertEquals(ApiFailure.Kind.CANCELLED, assertThrows(ApiFailure::class.java) { engine(transport).tick(now) }.kind)
        assertEquals(listOf("99"), lists.read(session, "200")!!.followers!!.users.map { it.id })
    }
    @Test fun capacityKeepsThePreviousSnapshotAndPausesOnlyTheUnrecordableDirection() {
        lists.commit(session, page("200", FollowDirection.FOLLOWERS, null, listOf("1"), time = 1000))
        lists.commit(session, page("200", FollowDirection.FOLLOWING, null, listOf("2"), time = 2000))
        val before = lists.read(session, "200")!!
        val transport = source { id, direction, cursor -> page(id, direction, cursor, listOf("3")).let {
            it.copy(users = listOf(it.users.single().copy(name = "x".repeat(600 * 1024))))
        } }
        assertEquals(ApiFailure.Kind.CAPACITY, assertThrows(ApiFailure::class.java) { engine(transport).tick(now) }.kind)
        assertEquals(before, lists.read(session, "200"))
        assertEquals(ApiFailure.Kind.CAPACITY, lists.target(session, "200")!!.followers.failure)
        val next = AutomaticQueue.next(vault.read()!!, lists.archive(session), now + MonitorPolicy.POLL_MS) as AutomaticTask.Page
        assertEquals(FollowDirection.FOLLOWING, next.direction)
    }
    @Test fun linkPreservesAutomationForTheSameOwnerAndResetsItForAnotherAccount() {
        val linked = vault.link(session.copy(userAgent = "new-UA"), saved().profile.copy(observedAt = now))
        assertEquals(saved().automation, linked.automation); assertTrue(linked.people.single().favorite)
        val other = vault.link(Session("300", "ds_user_id=300; sessionid=synthetic", "test-UA"), saved().profile.copy(userId = "300"))
        assertEquals(ListAutomation(enabled = true, targets = setOf(other.session.userId)), other.automation); assertTrue(other.people.isEmpty())
    }
    @Test fun clearingOnlyTheCapacityPauseCannotClearAnInstagramRestriction() {
        val gate = gate(); gate.pauseForListCapacity(); assertTrue(gate.manualOnly())
        gate.succeeded(); assertTrue(gate.listCapacityPaused()); assertTrue(gate.manualOnly())
        gate.clearListCapacityPause(); assertFalse(gate.manualOnly())
        gate.limited(null, now); gate.pauseForListCapacity(); gate.clearListCapacityPause()
        assertFalse(gate.listCapacityPaused()); assertTrue(gate.manualOnly()); assertTrue(gate.blockedUntil() > now)
    }
    @Test fun aFullLegacyCacheStopsBeforeTransportWhenTheArchiveReservationCannotFit() {
        val initial = page("200", FollowDirection.FOLLOWERS, null, listOf("1"), time = 1000)
        val emptyName = initial.copy(users = listOf(initial.users.single().copy(name = "")))
        val base = FollowSnapshot("100", session.mobileBinding, "200").record(emptyName)
        val padding = 512 * 1024 - 1 - FollowListCodec.encode(base).toByteArray().size
        val full = base.copy(followers = FollowRun(listOf(emptyName.copy(users = listOf(emptyName.users.single().copy(name = "x".repeat(padding)))))))
        val original = FollowListCodec.encode(full); assertEquals(512 * 1024 - 1, original.toByteArray().size)
        EncryptedRecord(context, "autolists").write(original)
        val transport = source { _, _, _ -> throw AssertionError("No room to reserve; do not fetch") }
        assertEquals(ApiFailure.Kind.CAPACITY, assertThrows(ApiFailure::class.java) { engine(transport).tick(now) }.kind)
        assertEquals(0, requests); assertTrue(gate().listCapacityPaused()); assertTrue(gate().manualOnly())
        assertEquals(original, EncryptedRecord(context, "autolists").read())
        engine(transport).tick(now + ListAutomationPolicy.RESCAN_MS); assertEquals(0, requests)
        lists.clear(); gate().clearListCapacityPause(); assertFalse(gate().manualOnly())
    }
    private class OwnConnection : HttpsURLConnection(URL("https://www.instagram.com/")) {
        override fun getResponseCode() = 200
        override fun getInputStream() = ByteArrayInputStream("""{"status":"ok","user":{"pk":"100","username":"own","follower_count":4,"following_count":1}}""".toByteArray())
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun connect() = Unit
        override fun getCipherSuite() = "synthetic"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate>? = null
    }
    @Test fun ownProfileAlsoRunsInTheBackgroundWithoutReplacingPeopleOrListProgress() {
        // This fixture injects HTTPS, so use the web session route; mobile routing is tested separately.
        val web = session.copy(cookie = "ds_user_id=100; sessionid=synthetic", mobileBinding = null)
        vault.write(saved().copy(session = web, automation = ListAutomation(true, setOf("100"), lastWorkWasList = true)))
        var profileReads = 0; var notices = 0
        val engine = AutomaticEngine(vault, lists, gate(), InstagramApi(gate()) { profileReads++; OwnConnection() },
            source { _, _, _ -> throw AssertionError("The due profile has the next turn") }, { true }, {
                notices++; assertEquals(1L, it.followersDelta); assertEquals("100", it.profile.userId)
            }, { _, _, _ -> throw AssertionError("No list read") })
        engine.tick(now)
        assertEquals(1, profileReads); assertEquals(1, notices); assertEquals(4L, vault.read()!!.profile.followers)
        assertEquals(saved().people, vault.read()!!.people)
        assertTrue(vault.read()!!.automation.ownNextCheckAt >= now + ListAutomationPolicy.OWN_PROFILE_MS)
    }
    @Test fun realAndroidJobCompletesBothDirectionsAndContinuationInOneActivationWithNoActivityOpen() {
        if (Build.VERSION.SDK_INT >= 33) shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        val seed = PreviewMode.seed(true)!!
        val previewVault = SessionVault(context, "previewaccount"); val previewLists = FollowListVault(context, "previewlists")
        previewLists.clear(); context.getSharedPreferences("preview_gate", 0).edit().clear().commit()
        previewVault.write(seed.copy(automation = ListAutomation(true, setOf("100"))))
        val previewGate = RequestGate(context, "preview_gate")
        ChangeNotifications(context,vaultForOwner={vault}).ensureChannel(); shell("input keyevent KEYCODE_HOME")
        assertTrue(PreviewMode.sync(context, previewVault.read(), previewGate))
        shell("cmd jobscheduler run -f ${context.packageName} ${PreviewMode.JOB_ID}")
        await("The Android job did not save its first list page") { previewLists.read(seed.session, "100")?.followers != null }
        assertEquals(1L, previewGate.attempts()); assertEquals(1, previewLists.read(seed.session, "100")!!.followers!!.pages.size)
        val firstAt = previewGate.lastAttemptAt()
        await("The same Android job did not finish both lists", 45000) {
            val snapshot = previewLists.read(seed.session, "100")
            snapshot?.followers?.coverage() == FollowCoverage.END_OBSERVED && snapshot.following?.coverage() == FollowCoverage.END_OBSERVED
        }
        assertEquals(3L, previewGate.attempts()); assertTrue(previewGate.lastAttemptAt() - firstAt >= 30000)
        assertEquals(3, previewLists.read(seed.session, "100")!!.followers!!.users.size)
        shell("cmd jobscheduler run -f ${context.packageName} ${PreviewMode.JOB_ID}"); assertEquals(3L, previewGate.attempts())
        previewVault.update(seed.session) { it.copy(automation = ListAutomation()) }
        assertFalse(PreviewMode.sync(context, previewVault.read(), previewGate))
        assertNull(context.getSystemService(JobScheduler::class.java).getPendingJob(PreviewMode.JOB_ID))
    }
}
