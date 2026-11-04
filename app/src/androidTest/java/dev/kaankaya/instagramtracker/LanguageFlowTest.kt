package dev.kaankaya.instagramtracker

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Real picker, lifecycle, encrypted records and notification manager; offline preview transport only. */
class LanguageFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    private var original = "tr"
    private val vault = SessionVault(context, "previewaccount")
    private val gate = RequestGate(context, "preview_gate")

    @Before fun setup() {
        check(context.packageName.endsWith(".debug"))
        original = AppLanguage.selected(context)
        instrumentation.runOnMainSync { AppLanguage.set(context, "tr") }
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.serviceInfo = instrumentation.uiAutomation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        vault.clear(); FollowListVault(context, "previewlists").clear()
        ChangeNotifications(context).clear()
        context.getSharedPreferences("preview_gate", 0).edit().clear().commit()
        vault.write(PreviewMode.seed(true)!!)
    }

    @After fun cleanup() {
        scenario?.close(); scenario = null
        PreviewMode.cancel(context); ChangeNotifications(context).clear()
        vault.clear(); FollowListVault(context, "previewlists").clear()
        context.getSharedPreferences("preview_gate", 0).edit().clear().commit()
        instrumentation.runOnMainSync { AppLanguage.set(context, original) }
        instrumentation.waitForIdleSync()
    }

    private fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()

    private fun launch(expected: String) {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java).putExtra("offline_preview", true))
        awaitLabel(expected)
    }

    private fun awaitLabel(label: String) {
        val deadline = SystemClock.elapsedRealtime() + 7000
        while (SystemClock.elapsedRealtime() < deadline) {
            var found = false
            scenario!!.onActivity { activity -> found = all(activity.window.decorView).any { it is TextView && it.text.toString() == label } }
            if (found) return
            Thread.sleep(30)
        }
        fail("Missing label: $label")
    }

    private fun click(label: String) = scenario!!.onActivity { activity ->
        val view = all(activity.window.decorView).single {
            it.isClickable && ((it is TextView && it.text.toString() == label) || it.contentDescription?.toString() == label)
        }
        assertTrue(view.isEnabled); view.performClick()
    }

    private fun dialogClick(label: String) {
        val deadline = SystemClock.elapsedRealtime() + 5000
        while (SystemClock.elapsedRealtime() < deadline) {
            fun nodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = listOf(root) +
                (0 until root.childCount).flatMap { root.getChild(it)?.let(::nodes).orEmpty() }
            val node = instrumentation.uiAutomation.windows.mapNotNull { it.root }.flatMap(::nodes)
                .firstOrNull { it.text?.toString()?.equals(label, ignoreCase = true) == true }
            if (node != null) {
                assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                instrumentation.waitForIdleSync(); return
            }
            Thread.sleep(30)
        }
        fail("Missing dialog choice: $label")
    }

    private fun english() { click("Seçenekler"); dialogClick("Dil / Language"); dialogClick("English"); awaitLabel("At a glance") }

    @Test fun pickerPersistsAcrossRelaunchPreservesRecordsAndDoesNotRefresh() {
        launch("Bir bakışta")
        gate.setOwnAutoRefresh(true)
        val before = vault.update(vault.read()!!.session) { it.copy(profile = it.profile.copy(observedAt = 1000)) }
        english()
        assertEquals("en", AppLanguage.selected(context)); assertEquals(before, vault.read()); assertEquals(0L, gate.attempts())
        // Disable the explicit launch-refresh preference before a genuine new launch.
        gate.setOwnAutoRefresh(false)
        scenario!!.recreate(); awaitLabel("At a glance")
        scenario!!.close(); scenario = null; launch("At a glance")
        click("Saved Profiles"); awaitLabel("Saved Profiles")
        click("Open analysis · @ada.demo"); awaitLabel("2,043")
        assertEquals(before, vault.read()); assertEquals(0L, gate.attempts())
        click("Options"); dialogClick("Language / Dil"); dialogClick("Türkçe")
        awaitLabel("Tüm kayıtlı profiller"); awaitLabel("2.043")
        assertEquals(before, vault.read()); assertEquals(0L, gate.attempts())
    }

    @Test fun languageChangePreserves429AndLocalizedListNavigation() {
        launch("Bir bakışta"); gate.limited(null)
        val pause = gate.blockedUntil(); val before = vault.read()
        english(); assertEquals(pause, gate.blockedUntil()); assertTrue(gate.manualOnly()); assertEquals(before, vault.read())
        click("Options"); dialogClick("My lists")
        val deadline = SystemClock.elapsedRealtime() + 5000
        var found = false
        while (!found && SystemClock.elapsedRealtime() < deadline) {
            fun nodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = listOf(root) +
                (0 until root.childCount).flatMap { root.getChild(it)?.let(::nodes).orEmpty() }
            found = instrumentation.uiAutomation.windows.mapNotNull { it.root }.flatMap(::nodes).any { it.text?.toString() == "Follow Lists" }
            if (!found) Thread.sleep(30)
        }
        assertTrue("English list screen", found); assertEquals(pause, gate.blockedUntil()); assertEquals(0L, gate.attempts())
        instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
    }

    @Test fun backgroundNotificationUsesSelectedLanguageAndKeepsPrivatePublicVersion() {
        if (Build.VERSION.SDK_INT >= 33) android.os.ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")).use { it.readBytes() }
        val notifications = ChangeNotifications(context, true)
        instrumentation.runOnMainSync { AppLanguage.set(context, "en") }
        instrumentation.waitForIdleSync()
        val profile = vault.read()!!.people.first().latest
        val owner=vault.read()!!.session.userId
        val change = ObservedProfileChange(owner, profile, 2, 0, listOf(ProfileEdit(profile.userId, profile.username,
            ProfileChangeField.BIOGRAPHY, "özel", "private", 1000, 2000)))
        notifications.post(change)
        val manager = context.getSystemService(NotificationManager::class.java)
        val deadline = SystemClock.elapsedRealtime() + 5000
        while (manager.activeNotifications.none { it.tag == "profile:$owner:${profile.userId}" } && SystemClock.elapsedRealtime() < deadline) Thread.sleep(30)
        val notification = manager.activeNotifications.single { it.tag == "profile:$owner:${profile.userId}" }.notification
        assertTrue(notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString().endsWith("profile change"))
        assertTrue(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("Biography changed"))
        assertEquals("Demo: a sample profile changed.", notification.publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertFalse(notification.publicVersion.extras.toString().contains(profile.username))
        if (Build.VERSION.SDK_INT >= 26) assertEquals("Profile changes", context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(ChangeNotifications.CHANNEL).name.toString())
        assertTrue(ApiFailure(ApiFailure.Kind.LIMITED).userMessage(AppLanguage.wrap(context)).startsWith("Instagram limited requests"))
        val resources = AppLanguage.wrap(context).resources
        assertEquals("Starting in 1 second", resources.getQuantityString(R.plurals.ui_starting_in_seconds, 1, 1))
        assertEquals("Starting in 2 seconds", resources.getQuantityString(R.plurals.ui_starting_in_seconds, 2, 2))
        assertEquals("1 saved profile", resources.getQuantityString(R.plurals.ui_saved_profiles, 1, 1))
    }

    @Test fun fullLanguagesKeepTheirRegionalTagAndTranslateTheHomeLabel() {
        instrumentation.runOnMainSync { AppLanguage.set(context, "es") }
        instrumentation.waitForIdleSync()
        assertEquals("es", AppLanguage.selected(context))
        assertEquals("Idioma de la aplicación", AppLanguage.wrap(context).getString(R.string.language_title))
        assertEquals("De un vistazo", AppLanguage.wrap(context).getString(R.string.ui_at_a_glance))

        instrumentation.runOnMainSync { AppLanguage.set(context, "pt-BR") }
        instrumentation.waitForIdleSync()
        assertEquals("pt-BR", AppLanguage.selected(context))
        assertEquals("Idioma do aplicativo", AppLanguage.wrap(context).getString(R.string.language_title))
        assertEquals("Em resumo", AppLanguage.wrap(context).getString(R.string.ui_at_a_glance))
        assertEquals(0L, gate.attempts())
    }

    @Test fun brazilianPortugueseKeepsTheSameActivityAcrossResumeAndPreservesRecords() {
        instrumentation.runOnMainSync { AppLanguage.set(context, "pt-BR") }
        instrumentation.waitForIdleSync()
        gate.limited(null)
        val before = vault.read()
        val pause = gate.blockedUntil()
        launch("Em resumo")
        var first: MainActivity? = null
        scenario!!.onActivity { first = it }
        repeat(3) {
            scenario!!.moveToState(Lifecycle.State.CREATED)
            scenario!!.moveToState(Lifecycle.State.RESUMED)
            instrumentation.waitForIdleSync()
            scenario!!.onActivity { activity ->
                assertSame("Resuming pt-BR must not recreate the activity", first, activity)
                assertEquals("pt-BR", AppLanguage.selected(activity))
                assertEquals("pt-BR", AppLanguage.locale(activity).toLanguageTag())
                assertEquals("Em resumo", activity.getString(R.string.ui_at_a_glance))
            }
        }
        assertEquals(before, vault.read())
        assertEquals(pause, gate.blockedUntil())
        assertEquals(0L, gate.attempts())
    }
}
