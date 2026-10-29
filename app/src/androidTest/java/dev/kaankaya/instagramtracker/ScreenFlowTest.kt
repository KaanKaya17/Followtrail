package dev.kaankaya.instagramtracker

import android.app.AlertDialog
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Runs only in the isolated .debug app and uses no Instagram account/network. */
class ScreenFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    @Before fun prepare() {
        check(context.packageName.endsWith(".debug"))
        SessionVault(context).clear()
        context.getSharedPreferences("request_gate", 0).edit().clear().commit()
    }
    @After fun cleanup() {
        scenario?.close()
        SessionVault(context).clear()
        context.getSharedPreferences("request_gate", 0).edit().clear().commit()
    }
    private fun all(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
    private fun views(activity: MainActivity) = all(activity.window.decorView)
    private fun launchUntilReady() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        val deadline = android.os.SystemClock.elapsedRealtime() + 5000
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            var ready = false
            scenario!!.onActivity { activity -> ready = views(activity).none { it is android.widget.ProgressBar } }
            if (ready) return
            Thread.sleep(20)
        }
        fail("Screen did not leave loading state")
    }
    @Test fun welcomeShowsConnectionWithoutInventedCounts() {
        launchUntilReady()
        scenario!!.onActivity { activity ->
            val labels = views(activity).filterIsInstance<TextView>().map { it.text.toString() }
            assertTrue(labels.contains("Hesabın,\nbir bakışta."))
            assertTrue(views(activity).filterIsInstance<Button>().single { it.text == "Hesap bağla" }.isEnabled)
            assertFalse(labels.contains("Takipçi"))
            assertFalse(labels.any { it.startsWith("@") })
            assertFalse(labels.any { it.contains("prototip", true) || it.contains("HTTP") })
            assertTrue(labels.joinToString(" ").split(Regex("\\s+")).size < 60)
        }
    }
    @Test fun savedCountsAndPauseSurviveActivityRecreationWithoutRequest() {
        val session = SessionCookies.fromHeader("ds_user_id=123; sessionid=synthetic", "test-UA")!!
        SessionVault(context).write(SavedAccount(session, Profile("123", "synthetic_only", 37, 12, 1000)))
        RequestGate(context).limited("120")
        launchUntilReady()
        fun assertSaved() = scenario!!.onActivity { activity ->
            val labels = views(activity).filterIsInstance<TextView>().map { it.text.toString() }
            assertTrue(labels.contains("@synthetic_only")); assertTrue(labels.contains("37")); assertTrue(labels.contains("12"))
            assertTrue(labels.any { it.startsWith("Kontroller için mola ·") })
            assertFalse(views(activity).filterIsInstance<Button>().single { it.text == "Yenile" }.isEnabled)
            assertTrue(views(activity).filterIsInstance<Button>().single { it.contentDescription == "Seçenekler" }.isEnabled)
            assertFalse(labels.contains("Bağlantıyı kaldır"))
            assertFalse(labels.any { it.contains("prototip", true) || it.contains("HTTP") })
        }
        assertSaved(); scenario!!.recreate()
        val deadline = android.os.SystemClock.elapsedRealtime() + 5000
        var loaded = false
        while (!loaded && android.os.SystemClock.elapsedRealtime() < deadline) {
            scenario!!.onActivity { activity -> loaded = views(activity).filterIsInstance<TextView>().any { it.text == "@synthetic_only" } }
            if (!loaded) Thread.sleep(20)
        }
        assertTrue(loaded); assertSaved()
        assertTrue(RequestGate(context).blockedUntil() > System.currentTimeMillis())
    }
    @Test fun navigationLeavesTheScrollViewportUncoveredAndDoesNotRead() {
        val session = SessionCookies.fromHeader("ds_user_id=123; sessionid=synthetic", "test-UA")!!
        SessionVault(context).write(SavedAccount(session, Profile("123", "synthetic_only", 37, 12, System.currentTimeMillis())))
        launchUntilReady(); instrumentation.waitForIdleSync()
        scenario!!.onActivity { activity ->
            val scroll = views(activity).filterIsInstance<android.widget.ScrollView>().single()
            val navigation = views(activity).single { it.tag == "primary_navigation" }
            val scrollPosition = IntArray(2); val navPosition = IntArray(2)
            scroll.getLocationOnScreen(scrollPosition); navigation.getLocationOnScreen(navPosition)
            assertTrue(scroll.height > 0); assertTrue(scrollPosition[1] + scroll.height <= navPosition[1])
        }
        assertEquals(0L, RequestGate(context).attempts())
    }
    @Test fun malformedVaultShowsRecoveryWithoutFabricatingProfile() {
        java.io.File(context.noBackupFilesDir, "account.enc").writeBytes(byteArrayOf(1, 2, 3))
        launchUntilReady()
        scenario!!.onActivity { activity ->
            val labels = views(activity).filterIsInstance<TextView>().map { it.text.toString() }
            assertTrue(labels.any { it.contains("güvenli kayıt açılamadı") })
            assertTrue(views(activity).filterIsInstance<Button>().any { it.contentDescription == "Seçenekler" && it.isEnabled })
            assertFalse(labels.any { it.startsWith("@") })
        }
    }
}
