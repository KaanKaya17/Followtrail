package dev.kaankaya.instagramtracker

import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/** Form, lifecycle and shared restrictions, without submitting credentials to Instagram. */
class PhoneLoginScreenTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val gate = RequestGate(context)
    private var scenario: ActivityScenario<PhoneLoginActivity>? = null
    private fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
    @Before fun before() {
        check(context.packageName.endsWith(".debug"))
        context.getSharedPreferences("request_gate", 0).edit().clear().commit()
    }
    @After fun after() {
        scenario?.close()
        context.getSharedPreferences("request_gate", 0).edit().clear().commit()
    }
    @Test fun openingAndRecreatingDoesNotSendRequestsOrRestorePassword() {
        scenario = ActivityScenario.launch(PhoneLoginActivity::class.java)
        assertTrue(gate.phoneLoginActive()); assertTrue(gate.automationHeld())
        assertFalse(gate.reserveAutomatic())
        scenario!!.onActivity { activity ->
            assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            val inputs = all(activity.window.decorView).filterIsInstance<EditText>()
            assertEquals(2, inputs.size); assertTrue(inputs.none { it.isSaveEnabled })
            inputs.last().setText("synthetic-password")
        }
        scenario!!.recreate()
        scenario!!.onActivity { activity ->
            assertTrue(all(activity.window.decorView).filterIsInstance<EditText>().all { it.text.isEmpty() })
        }
        assertEquals(0L, gate.attempts()); assertTrue(gate.phoneLoginActive())
        scenario!!.close(); scenario = null
        assertFalse(gate.phoneLoginActive()); assertFalse(gate.automationHeld())
    }
    @Test fun blankSubmissionIsLocalAndReturnsAUsableForm() {
        scenario = ActivityScenario.launch(PhoneLoginActivity::class.java)
        scenario!!.onActivity { activity ->
            all(activity.window.decorView).filterIsInstance<Button>().single { it.text == activity.getString(R.string.phone_start) }.performClick()
            assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
        }
        val deadline = android.os.SystemClock.elapsedRealtime() + 5000
        var found = false
        while (!found && android.os.SystemClock.elapsedRealtime() < deadline) {
            scenario!!.onActivity { activity ->
                found = all(activity.window.decorView).filterIsInstance<TextView>().any { it.text == activity.getString(R.string.phone_input_error) }
            }
            if (!found) Thread.sleep(25)
        }
        assertTrue(found); assertEquals(0L, gate.attempts())
        scenario!!.onActivity { activity -> assertEquals(0, activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    @Test fun anExistingRestrictionSurvivesOpeningRecreationAndClosing() {
        gate.limited("7200"); val blocked = gate.blockedUntil()
        scenario = ActivityScenario.launch(PhoneLoginActivity::class.java)
        scenario!!.onActivity { activity ->
            assertFalse(all(activity.window.decorView).filterIsInstance<Button>().single { it.text == activity.getString(R.string.phone_start) }.isEnabled)
        }
        scenario!!.recreate(); scenario!!.close(); scenario = null
        assertEquals(blocked, gate.blockedUntil()); assertTrue(gate.manualOnly())
        assertEquals(0L, gate.attempts()); assertFalse(gate.phoneLoginActive())
    }
}
