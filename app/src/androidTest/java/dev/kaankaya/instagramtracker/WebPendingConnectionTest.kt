package dev.kaankaya.instagramtracker

import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/** Production encrypted pending store and screens, with no eligible network operation. */
@SdkSuppress(minSdkVersion = 29)
class WebPendingConnectionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<WebPendingConnectionActivity>? = null
    private fun all(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
    @Before fun setup() { check(context.packageName.endsWith(".debug")); cleanup() }
    @After fun cleanup() {
        scenario?.close(); scenario = null; MonitorScheduler.cancel(context)
        AccountCatalog(context).entries().forEach { SessionVault(context, owner = it.id).clear() }
        WebSessions(context).clear(); MobileSessions(context).clear()
        context.getSharedPreferences("request_gate", 0).edit().clear().commit()
    }
    private fun pending() = WebSessions(context).stage(WebSessionCodec.fromHeader(
        "sessionid=synthetic-pending; ds_user_id=123; csrftoken=synthetic", "Synthetic WebView"))
    @Test fun cooldownDisablesContinuationAndRecreationNeverSendsOrDeletesTheSession() {
        val reference = pending(); val gate = RequestGate(context); gate.limited(null)
        val pause = gate.blockedUntil()
        scenario = ActivityScenario.launch(WebPendingConnectionActivity::class.java)
        repeat(2) {
            scenario!!.onActivity { a ->
                val button = all(a.window.decorView).filterIsInstance<Button>()
                    .single { it.text == a.getString(R.string.ui_complete_connection) }
                assertFalse(button.isEnabled)
            }
            scenario!!.recreate()
        }
        assertEquals(reference, WebSessions(context).pending()); assertNull(SessionVault(context).read())
        assertEquals(0L, gate.attempts()); assertEquals(pause, gate.blockedUntil())
    }
    @Test fun explicitForgetRemovesOnlyPendingSessionAndKeepsSavedAccountAndPause() {
        val existing = SavedAccount(Session("456", "ds_user_id=456; sessionid=synthetic", "Synthetic UA"),
            Profile("456", "saved", 1, 2, 1000))
        SessionVault(context).write(existing)
        pending(); val gate = RequestGate(context); gate.limited(null); val pause = gate.blockedUntil()
        scenario = ActivityScenario.launch(WebPendingConnectionActivity::class.java)
        scenario!!.onActivity { a ->
            all(a.window.decorView).filterIsInstance<Button>()
                .single { it.text == a.getString(R.string.web_pending_forget) }.performClick()
        }
        scenario!!.onActivity { a ->
            WindowInspector.getGlobalWindowViews().flatMap(::all).filterIsInstance<Button>()
                .single { it.id == android.R.id.button1 && it.text == a.getString(R.string.web_pending_forget) }.performClick()
        }
        assertNull(WebSessions(context).pending()); assertEquals(existing, SessionVault(context).read())
        assertEquals(0L, gate.attempts()); assertEquals(pause, gate.blockedUntil())
    }
}
