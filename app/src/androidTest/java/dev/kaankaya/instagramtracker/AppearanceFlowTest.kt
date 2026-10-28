package dev.kaankaya.instagramtracker

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Real native theme picker and recreation; isolated offline preview account only. */
class AppearanceFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    private var original = "system"
    private var language = "tr"
    private val vault = SessionVault(context, "previewaccount")
    private val gate = RequestGate(context, "preview_gate")
    @Before fun setup() {
        check(context.packageName.endsWith(".debug"))
        original=AppAppearance.selected(context);language=AppLanguage.selected(context)
        instrumentation.runOnMainSync { AppAppearance.set(context,"light");AppLanguage.set(context,"tr") }
        instrumentation.uiAutomation.serviceInfo=instrumentation.uiAutomation.serviceInfo.apply {
            flags=flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        vault.clear();context.getSharedPreferences("preview_gate",0).edit().clear().commit()
        vault.write(PreviewMode.seed(true)!!)
    }
    @After fun cleanup() {
        scenario?.close();PreviewMode.cancel(context);vault.clear()
        context.getSharedPreferences("preview_gate",0).edit().clear().commit()
        instrumentation.runOnMainSync { AppAppearance.set(context,original);AppLanguage.set(context,language) }
    }
    private fun all(view: View): List<View> = listOf(view)+if(view is ViewGroup) (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
    private fun await(mode: String) {
        val deadline=SystemClock.elapsedRealtime()+8000
        while(SystemClock.elapsedRealtime()<deadline) {
            var matched=false
            scenario!!.onActivity { activity ->
                matched=activity.resources.getColor(when(mode) {
                    "dark"->R.color.theme_dark_background;"oled"->R.color.theme_oled_background;else->R.color.theme_light_background
                },activity.theme)==AppUi.background && all(activity.window.decorView).any { it is TextView && it.text.toString()=="Bir bakışta" }
            }
            if(matched) return
            Thread.sleep(40)
        }
        fail("Theme did not render: $mode")
    }
    private fun click(label: String) = scenario!!.onActivity { activity ->
        all(activity.window.decorView).single { it.isClickable && ((it is TextView && it.text.toString()==label)||it.contentDescription?.toString()==label) }.performClick()
    }
    private fun dialogClick(label: String) {
        val deadline=SystemClock.elapsedRealtime()+5000
        while(SystemClock.elapsedRealtime()<deadline) {
            fun nodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = listOf(root)+(0 until root.childCount).flatMap { root.getChild(it)?.let(::nodes).orEmpty() }
            val node=instrumentation.uiAutomation.windows.mapNotNull { it.root }.flatMap(::nodes).firstOrNull { it.text?.toString()==label }
            if(node!=null) {assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK));instrumentation.waitForIdleSync();return}
            Thread.sleep(40)
        }
        fail("Missing theme choice: $label")
    }
    private fun screenshot(mode: String) {
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            context.openFileOutput("appearance-$mode.png",0).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
    }
    @Test fun realPickerPersistsAndRecreatesWithoutRefreshingOrChangingAccount() {
        scenario=ActivityScenario.launch(Intent(context,MainActivity::class.java).putExtra("offline_preview",true))
        await("light");gate.setOwnAutoRefresh(true)
        val before=vault.read();val attempts=gate.attempts()
        for((mode,label) in listOf("dark" to "Koyu","oled" to "OLED siyahı","light" to "Açık")) {
            click("Seçenekler");dialogClick("Tema");dialogClick(label);await(mode)
            screenshot(mode)
            assertEquals(mode,AppAppearance.selected(context));assertEquals(before,vault.read());assertEquals(attempts,gate.attempts())
            scenario!!.onActivity { activity ->
                val value=android.util.TypedValue();activity.theme.resolveAttribute(android.R.attr.windowLightStatusBar,value,true)
                assertEquals(mode=="light",value.data!=0)
            }
        }
        gate.setOwnAutoRefresh(false)
        instrumentation.runOnMainSync { AppAppearance.set(context,"oled") }
        scenario!!.recreate();await("oled");scenario!!.close()
        scenario=ActivityScenario.launch(Intent(context,MainActivity::class.java).putExtra("offline_preview",true));await("oled")
        assertEquals(before,vault.read());assertEquals(attempts,gate.attempts())
    }
    @Test fun systemSelectionResolvesCurrentConfigurationAndCooldownIsRetained() {
        instrumentation.runOnMainSync { AppAppearance.set(context,"system") }
        gate.limited(null);val until=gate.blockedUntil();val before=vault.read()
        scenario=ActivityScenario.launch(Intent(context,MainActivity::class.java).putExtra("offline_preview",true))
        val expected=AppAppearance.resolved(context);await(expected)
        assertEquals(until,gate.blockedUntil());assertTrue(gate.manualOnly());assertEquals(before,vault.read())
        scenario!!.onActivity { activity ->assertEquals(AppAppearance.resolved(activity),expected) }
    }
    @Test fun systemChangesRecreateTheScreenWithoutReadingTheAccount() {
        val manager=context.getSystemService(android.app.UiModeManager::class.java)
        val originalMode=manager.nightMode
        fun system(mode: String) { instrumentation.uiAutomation.executeShellCommand("cmd uimode night $mode").close();instrumentation.waitForIdleSync() }
        try {
            system("no");instrumentation.runOnMainSync { AppAppearance.set(context,"system") }
            scenario=ActivityScenario.launch(Intent(context,MainActivity::class.java).putExtra("offline_preview",true));await("light")
            gate.setOwnAutoRefresh(true);val before=vault.read();val attempts=gate.attempts()
            system("yes");await("dark");assertEquals(before,vault.read());assertEquals(attempts,gate.attempts())
            system("no");await("light");assertEquals(before,vault.read());assertEquals(attempts,gate.attempts())
        } finally {
            gate.setOwnAutoRefresh(false)
            system(when(originalMode) {android.app.UiModeManager.MODE_NIGHT_YES->"yes";android.app.UiModeManager.MODE_NIGHT_NO->"no";else->"auto"})
        }
    }
}
