package dev.kaankaya.instagramtracker

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import okio.ByteString.Companion.encodeUtf8
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/** The setup UI does not open Instagram or read profiles. */
class DesktopLinkScreenTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<DesktopLinkActivity>? = null
    private val sessions = MobileSessions(context)
    private val link = DesktopLink(context)
    @Before fun before() { check(context.packageName.endsWith(".debug")); clean() }
    private fun clean() {
        sessions.clear(); link.clear(); SessionVault(context).clear()
        context.getSharedPreferences("request_gate", 0).edit().clear().commit()
    }
    @After fun after() { scenario?.close(); clean() }
    private fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
    private fun await(label: String) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 10000
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            var ready = false
            scenario!!.onActivity { ready = all(it.window.decorView).filterIsInstance<TextView>().any { view -> view.text == label } }
            if (ready) return
            Thread.sleep(30)
        }
        fail("Setup state not visible: $label")
    }
    private fun buttons(): List<String> {
        var result = emptyList<String>()
        scenario!!.onActivity { result = all(it.window.decorView).filterIsInstance<Button>().map { button -> button.text.toString() } }
        return result
    }
    @Test fun setupShowsOneStepAtATimeAndPreparedStateSurvivesRecreation() {
        scenario = ActivityScenario.launch(DesktopLinkActivity::class.java)
        assertTrue(buttons().contains("Bağlantıyı hazırla")); assertFalse(buttons().contains("Aktarımı al"))
        scenario!!.onActivity { activity -> all(activity.window.decorView).filterIsInstance<Button>().single { it.text == "Bağlantıyı hazırla" }.performClick() }
        await("Sıra bilgisayarında.")
        assertTrue(link.isPrepared()); assertTrue(buttons().contains("Aktarımı al"))
        assertFalse(buttons().contains("Bağlantıyı hazırla")); assertFalse(buttons().contains("Bağlan"))
        scenario!!.recreate(); await("Sıra bilgisayarında.")
        assertEquals(0L, RequestGate(context).attempts())
    }
    @Test fun pendingTransferRestoresWithoutReadingOrReplacingAnOldPause() {
        val auth = "Bearer IGT:2:" + """{"ds_user_id":"123","sessionid":"synthetic"}""".encodeUtf8().base64()
        sessions.stage(MobileSession("a".repeat(32), "123", mapOf("Authorization" to auth, "User-Agent" to "Instagram synthetic",
            "X-IG-Device-ID" to "device", "X-IG-Family-Device-ID" to "family", "X-IG-Android-ID" to "android-test",
            "X-IG-App-ID" to "567067343352427", "X-Bloks-Version-Id" to "hash", "IG-INTENDED-USER-ID" to "123",
            "IG-U-DS-USER-ID" to "123"), mapOf("ds_user_id" to "123", "sessionid" to "synthetic")))
        val gate = RequestGate(context); gate.limited(null); val until = gate.blockedUntil()
        scenario = ActivityScenario.launch(DesktopLinkActivity::class.java)
        await("Aktarım hazır.")
        assertFalse(buttons().contains("Bağlantıyı hazırla")); assertFalse(buttons().contains("Aktarımı al"))
        scenario!!.onActivity { activity -> assertFalse(all(activity.window.decorView).filterIsInstance<Button>().single { it.text == "Bağlan" }.isEnabled) }
        scenario!!.recreate(); await("Aktarım hazır.")
        assertEquals(until, gate.blockedUntil()); assertEquals(0L, gate.attempts())
        assertNull(SessionVault(context).read())
    }
}
