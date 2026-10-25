package dev.kaankaya.instagramtracker

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.ByteArrayInputStream
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection

/** Real storage, parser, monitor, Android notification and Activity; synthetic network only. */
class ProfileMetadataFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val vault = SessionVault(context, "metatest")
    private val preferences = context.getSharedPreferences("meta_test_gate", 0)
    private var scenario: ActivityScenario<MainActivity>? = null
    private val session = Session("123", "ds_user_id=123; sessionid=synthetic", "test-UA")
    private fun p(time: Long = 1000) = Profile("456", "original", 20, 8, time, 3,
        ProfileDetails(ProfileText.known("before-private-bio"), ProfileText.known("photo1")))
    private fun saved() = SavedAccount(session, p().copy(userId = "123"),
        listOf(TrackedPerson(listOf(p()), "note", "group", true, 6, 0, true)))
    @Before fun prepare() {
        check(context.packageName.endsWith(".debug")); preferences.edit().clear().commit()
        vault.write(saved()); SessionVault(context, "previewaccount").clear(); FollowListVault(context, "previewlists").clear()
        context.getSharedPreferences("preview_gate", 0).edit().clear().commit()
    }
    @After fun cleanup() {
        scenario?.close(); vault.clear(); preferences.edit().clear().commit()
        SessionVault(context, "previewaccount").clear(); FollowListVault(context, "previewlists").clear()
        context.getSharedPreferences("preview_gate", 0).edit().clear().commit(); PreviewMode.cancel(context)
        ChangeNotifications(context).clear(); EncryptedRecord(context, "metacap").clear()
    }
    private class Connection : HttpsURLConnection(URL("https://www.instagram.com/")) {
        override fun getResponseCode() = 200
        override fun getInputStream() = ByteArrayInputStream("""{"status":"ok","user":{"pk":"456","username":"renamed","follower_count":20,"following_count":8,"media_count":3,"biography":"after-private-bio","profile_pic_id":"photo2"}}""".toByteArray())
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun connect() = Unit
        override fun getCipherSuite() = "synthetic"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate>? = null
    }
    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()
    }
    @Test fun unchangedCountsStillPostOnePrivateProfileNotificationAndPreserveFavorites() {
        if (Build.VERSION.SDK_INT >= 33) shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        val notices = ChangeNotifications(context); notices.ensureChannel(); assertTrue(notices.allowed())
        val gate = RequestGate(context, "meta_test_gate"); var requests = 0
        val api = InstagramApi(gate) { requests++; Connection() }
        val engine = MonitorEngine(vault, gate, api, notices::allowed, notices::post)
        engine.tick(); engine.tick(System.currentTimeMillis() + 16000)
        assertEquals(1, requests); assertEquals(1L, gate.attempts())
        val person = vault.read()!!.people.single(); assertTrue(person.favorite); assertEquals("note", person.note)
        assertEquals(2, person.history.size); assertEquals("after-private-bio", person.latest.details.biography.value)
        val manager = context.getSystemService(NotificationManager::class.java)
        val deadline = android.os.SystemClock.elapsedRealtime() + 5000
        while (manager.activeNotifications.none { it.tag == "profile:123:456" } && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(30)
        val notification = manager.activeNotifications.single { it.tag == "profile:123:456" }.notification
        val body = notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertTrue(body.contains("Kullanıcı adı değişti")); assertTrue(body.contains("Biyografi değişti"))
        assertTrue(body.contains("Profil fotoğrafı bilgisi değişti")); assertFalse(body.contains("private-bio"))
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        assertFalse(notification.publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("renamed"))
        assertEquals(3, RecentActivity.items(vault.read()!!).filterIsInstance<ActivityItem.ProfileChanged>().size)
    }
    private fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
    private fun awaitText(value: String) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 5000
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            var found = false
            scenario!!.onActivity { found = all(it.window.decorView).filterIsInstance<TextView>().any { label -> label.text.toString() == value } }
            if (found) return
            Thread.sleep(20)
        }
        fail("Missing UI state: $value")
    }
    private fun click(value: String) = scenario!!.onActivity { activity ->
        val button = all(activity.window.decorView).filterIsInstance<Button>().single { it.text.toString() == value }
        assertTrue(button.isEnabled); button.performClick()
    }
    @Test fun profilePanelAndFeedRestoreWithoutAnotherRead() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java).putExtra("offline_preview", true))
        awaitText("@benim.demo"); click("Kişiler"); click("Analizi aç · @deniz.demo")
        awaitText("Kahve, kitaplar ve yeni yollar. ☀️"); click("Kişiyi yenile")
        awaitText("Yeni gözlem bu cihazda kaydedildi."); awaitText("Yeni duraklar, yeni hikâyeler. ☀️\nİstanbul")
        val account = SessionVault(context, "previewaccount").read()!!
        scenario!!.recreate(); awaitText("Yeni duraklar, yeni hikâyeler. ☀️\nİstanbul")
        click("Özet"); awaitText("Biyografi değişti"); awaitText("Profil fotoğrafı bilgisi değişti")
        assertEquals(1L, RequestGate(context, "preview_gate").attempts())
        assertEquals(account, SessionVault(context, "previewaccount").read())
    }
    @Test fun oversizedMetadataCannotReplaceThePreviousEncryptedAccount() {
        val record = EncryptedRecord(context, "metacap"); val before = AccountCodec.encode(saved()); record.write(before)
        val people = (1..20).map { id -> TrackedPerson((1..5).map { time -> p(time * 1000L).copy(userId = id.toString(),
            details = ProfileDetails(ProfileText.known("$id/$time" + "x".repeat(7500)))) }) }
        val text = AccountCodec.encode(saved().copy(people = people)); assertTrue(text.toByteArray().size > 512 * 1024)
        assertEquals(ApiFailure.Kind.CAPACITY, assertThrows(ApiFailure::class.java) { record.write(text) }.kind)
        assertEquals(before, record.read()); assertEquals(saved(), AccountCodec.decode(record.read()!!))
    }
}
