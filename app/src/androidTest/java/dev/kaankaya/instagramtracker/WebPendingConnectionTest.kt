package dev.kaankaya.instagramtracker

import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.Button
import android.widget.TextView
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
    private fun owner(view: View): android.app.Activity? {
        var context: android.content.Context? = view.context
        while (context is android.content.ContextWrapper) { if (context is android.app.Activity) return context; context = context.baseContext }
        return null
    }
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
                    .single { it.text == a.getString(R.string.web_pending_verify) }
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
            // Use only this activity's dialog; a window left by an earlier test could carry the same button.
            WindowInspector.getGlobalWindowViews().filter { it.isShown && owner(it) === a }.flatMap(::all).filterIsInstance<Button>()
                .single { it.id == android.R.id.button1 && it.text == a.getString(R.string.web_pending_forget) }.performClick()
        }
        assertNull(WebSessions(context).pending()); assertEquals(existing, SessionVault(context).read())
        assertEquals(0L, gate.attempts()); assertEquals(pause, gate.blockedUntil())
    }
    @Test fun requestDetailsShowTheReaderContractWithoutSendingOrDiscardingThePendingSession() {
        val reference = pending(); val gate = RequestGate(context); gate.limited(null)
        val pause = gate.blockedUntil()
        scenario = ActivityScenario.launch(WebPendingConnectionActivity::class.java)
        scenario!!.onActivity { a ->
            all(a.window.decorView).filterIsInstance<Button>()
                .single { it.text == a.getString(R.string.ui_request_information) }.performClick()
            val text = WindowInspector.getGlobalWindowViews().flatMap(::all).filterIsInstance<TextView>().map { it.text.toString() }
            assertTrue(text.any { it.contains(a.getString(R.string.web_transport_app_id, WebRequests.APP_ID)) })
            assertTrue(text.any { it.contains(a.getString(R.string.web_compare_last_counted_http, a.getString(R.string.ui_no_response_yet))) })
        }
        assertEquals(reference, WebSessions(context).pending()); assertNull(SessionVault(context).read())
        assertEquals(0L, gate.attempts()); assertEquals(pause, gate.blockedUntil())
    }
}
