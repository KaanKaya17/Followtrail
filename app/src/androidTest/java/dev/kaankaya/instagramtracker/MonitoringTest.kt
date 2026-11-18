package dev.kaankaya.instagramtracker

import android.app.Notification
import android.app.NotificationManager
import android.app.job.JobScheduler
import android.os.Build
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection

/** Production storage, gate and monitor; synthetic HTTPS transport only. */
class MonitoringTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val vault = SessionVault(context, "monitortest")
    private val preferences = context.getSharedPreferences("monitor_test_gate", 0)
    private val session = Session("123", "ds_user_id=123; sessionid=synthetic", "test-UA")
    private val profile = Profile("456", "synthetic", 10, 7, 1000, 4)
    private val person = TrackedPerson(listOf(profile), "note", "group", true, 60, 0)
    private fun gate() = RequestGate(context, "monitor_test_gate")
    @Before fun prepare() {
        check(context.packageName.endsWith(".debug"))
        preferences.edit().clear().commit()
        vault.write(SavedAccount(session, profile.copy(userId = "123"), listOf(person, person.copy(history = listOf(profile.copy(userId = "789"))))))
    }
    @After fun cleanup() {
        vault.clear(); preferences.edit().clear().commit()
        PreviewMode.cancel(context); SessionVault(context, "previewaccount").clear()
        context.getSharedPreferences("preview_gate", 0).edit().clear().commit()
        ChangeNotifications(context).clear()
    }
    private class Connection(private val status: Int = 200, private val beforeResponse: () -> Unit = {}) : HttpsURLConnection(URL("https://www.instagram.com/")) {
        var disconnected = false
        override fun getResponseCode(): Int { beforeResponse(); return status }
        override fun getInputStream() = ByteArrayInputStream("""{"status":"ok","user":{"pk":"456","username":"renamed","follower_count":12,"following_count":6,"media_count":4}}""".toByteArray())
        override fun getErrorStream() = inputStream
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun connect() = Unit
        override fun getCipherSuite() = "synthetic"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate>? = null
    }
    private fun expect(kind: ApiFailure.Kind, action: () -> Unit) {
        try { action(); fail("Expected $kind") } catch (error: ApiFailure) { assertEquals(kind, error.kind) }
    }
    @Test fun oneDuePersonIsCommittedWithDeltaAndForegroundMetadataSurvives() {
        var requests = 0
        val notices = mutableListOf<ObservedProfileChange>()
        val api = InstagramApi(gate()) { url ->
            requests++; assertEquals("/api/v1/users/456/info/", url.path)
            Connection { vault.update(session) { account -> account.copy(people = account.people.map { it.copy(note = "edited during request") }) } }
        }
        val now = System.currentTimeMillis()
        val engine = MonitorEngine(vault, gate(), api, { true }, notices::add)
        engine.tick(now); engine.tick(now + 16000)
        assertEquals(1, requests); assertEquals(1, notices.size)
        assertEquals(2L, notices.single().followersDelta); assertEquals(-1L, notices.single().followingDelta)
        val updated = vault.read()!!.people.first()
        assertEquals("edited during request", updated.note); assertEquals(2, updated.history.size)
        assertTrue(updated.nextCheckAt >= now + 3600000)
        assertEquals(1, vault.read()!!.people.last().history.size)
    }
    @Test fun disabledNotificationsOrPersistentPausesNeverOpenTransport() {
        val api = InstagramApi(gate()) { throw AssertionError("Transport must not open") }
        MonitorEngine(vault, gate(), api, { false }, {}).tick()
        assertEquals(0L, gate().nextAllowedAt())
        gate().limited(null, 1)
        MonitorEngine(vault, gate(), api, { true }, {}).tick()
        preferences.edit().clear().commit(); gate().pauseForConnection()
        MonitorEngine(vault, gate(), api, { true }, {}).tick()
        assertEquals(1, vault.read()!!.people.first().history.size)
    }
    @Test fun fifteenMinutePersonStillSharesOneAutomaticSlotAndTheOtherPersonKeepsItsTurn() {
        val saved = vault.read()!!
        vault.write(saved.copy(people = saved.people.map { it.copy(intervalMinutes = 15) }))
        var requests = 0
        val gate = gate(); val now = System.currentTimeMillis()
        val engine = MonitorEngine(vault, gate, InstagramApi(gate) { requests++; Connection() }, { true }, {})
        engine.tick(now); engine.tick(now + 16000)
        val people = vault.read()!!.people
        assertEquals(1, requests); assertEquals(1L, gate.attempts())
        assertTrue(people.first().nextCheckAt >= now + 15 * 60000L)
        // The shared automatic slot follows the selected check interval (5 minutes by default).
        assertTrue(gate.automaticNextAllowedAt() >= now + gate.monitorIntervalMillis())
        assertEquals("789", MonitorPolicy.due(people, now + 16000)!!.latest.userId)
        val state = WatchStatusPolicy.status(people.last(), people,
            WatchConditions(automaticAt = gate.automaticNextAllowedAt()), now + 16000)
        assertEquals(WatchReason.SHARED_BUDGET, state.reason)
    }
    @Test fun serverLimitAndAuthenticationStopAutomaticWorkAcrossInstances() {
        for ((status, kind) in listOf(429 to ApiFailure.Kind.LIMITED, 401 to ApiFailure.Kind.SESSION_EXPIRED)) {
            prepare()
            val now = System.currentTimeMillis()
            val connection = Connection(status)
            expect(kind) { MonitorEngine(vault, gate(), InstagramApi(gate()) { connection }, { true }, {}).tick(now) }
            assertTrue(connection.disconnected)
            assertTrue(vault.read()!!.people.first().nextCheckAt >= now + 3600000)
            assertTrue(gate().manualOnly() || gate().requiresConnection())
            MonitorEngine(vault, gate(), InstagramApi(gate()) { throw AssertionError("No retry") }, { true }, {}).tick(now + 86400000)
        }
    }
    @Test fun deletionDisableSessionChangeAndCancellationDiscardLateResponse() {
        for (action in listOf("delete", "disable", "session", "cancel")) {
            prepare(); var cancelled = false; var notifications = 0
            val connection = Connection {
                when (action) {
                    "delete" -> vault.update(session) { it.copy(people = emptyList()) }
                    "disable" -> vault.update(session) { it.copy(people = it.people.map { p -> p.copy(watching = false) }) }
                    "session" -> vault.write(vault.read()!!.copy(session = session.copy(cookie = "ds_user_id=123; sessionid=replaced")))
                    else -> cancelled = true
                }
            }
            expect(ApiFailure.Kind.CANCELLED) {
                MonitorEngine(vault, gate(), InstagramApi(gate()) { connection }, { true }, { notifications++ }).tick(cancelled = { cancelled })
            }
            assertEquals(0, notifications); assertTrue(connection.disconnected)
            assertTrue(vault.read()!!.people.all { it.history.size == 1 })
        }
    }
    @Test fun inFlightLeaseBlocksAnotherReaderAndIsReleasedAfterFailure() {
        gate().claim().use {
            expect(ApiFailure.Kind.TOO_SOON) { InstagramApi(gate()) { throw AssertionError("No second connection") }.read(session) }
        }
        expect(ApiFailure.Kind.CONNECTION) { InstagramApi(gate()) { throw java.io.IOException("synthetic") }.read(session) }
        gate().claim().close()
    }
    private fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).use {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()
    }
    private fun await(message: String = "Background job did not complete", check: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10000
        while (SystemClock.elapsedRealtime() < deadline) { if (check()) return; Thread.sleep(50) }
        fail(message)
    }
    @Test fun realAndroidJobPostsPrivateDemoNotificationWithNoActivityOpen() {
        if (Build.VERSION.SDK_INT >= 33) shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        val notices = ChangeNotifications(context, true); notices.ensureChannel(); assertTrue(notices.allowed())
        val previewVault = SessionVault(context, "previewaccount")
        val seeded = PreviewMode.seed(true)!!
        previewVault.write(seeded.copy(people = seeded.people.mapIndexed { index, p -> p.copy(watching = index == 0, nextCheckAt = 0) }))
        val previewGate = RequestGate(context, "preview_gate")
        context.getSharedPreferences("preview_gate", 0).edit().clear().commit()
        shell("input keyevent KEYCODE_HOME")
        assertTrue(PreviewMode.sync(context, previewVault.read(), previewGate))
        val job = context.getSystemService(JobScheduler::class.java).getPendingJob(PreviewMode.JOB_ID)!!
        assertTrue(job.isPersisted); assertEquals(MonitorPolicy.POLL_MS, job.intervalMillis)
        assertTrue(shell("cmd jobscheduler run -f ${context.packageName} ${PreviewMode.JOB_ID}").contains("Running job"))
        val manager = context.getSystemService(NotificationManager::class.java)
        await { manager.activeNotifications.any { it.tag == "profile:100:200" } }
        val notification = manager.activeNotifications.single { it.tag == "profile:100:200" }.notification
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        assertTrue(notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString().startsWith("DEMO"))
        val detail = notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertTrue(detail.contains("Biyografi değişti")); assertTrue(detail.contains("Profil fotoğrafı bilgisi değişti"))
        assertFalse(notification.publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("deniz"))
        assertEquals(1356L, previewVault.read()!!.people.first().latest.followers)
        previewVault.update(seeded.session) { it.copy(people = it.people.map { p -> p.copy(watching = false) }) }
        assertFalse(PreviewMode.sync(context, previewVault.read(), previewGate))
        assertNull(context.getSystemService(JobScheduler::class.java).getPendingJob(PreviewMode.JOB_ID))
        notices.cancel("100", "200")
        // NotificationManager queues cancellation in the system service; verify its observable completion.
        await("System did not remove the cancelled demo notification") {
            manager.activeNotifications.none { it.tag == "profile:100:200" }
        }
    }
}
