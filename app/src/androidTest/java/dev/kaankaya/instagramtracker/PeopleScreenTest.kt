package dev.kaankaya.instagramtracker

import android.content.Intent
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

/** The preview uses the real Activity/parser/gate/vault with an entirely offline transport. */
class PeopleScreenTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    @Before fun prepare() {
        check(context.packageName.endsWith(".debug"))
        instrumentation.uiAutomation.serviceInfo = instrumentation.uiAutomation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        SessionVault(context, "previewaccount").clear()
        context.getSharedPreferences("preview_gate", 0).edit().clear().commit()
    }
    @After fun cleanup() {
        scenario?.close()
        PreviewMode.cancel(context)
        ChangeNotifications(context).clear()
        SessionVault(context, "previewaccount").clear()
        context.getSharedPreferences("preview_gate", 0).edit().clear().commit()
    }
    private fun all(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
    private fun awaitLabel(label: String) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 5000
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            var found = false
            scenario!!.onActivity { activity -> found = all(activity.window.decorView).filterIsInstance<TextView>().any { it.text == label } }
            if (found) return
            Thread.sleep(20)
        }
        fail("Label not found: $label")
    }
    private fun click(label: String) = scenario!!.onActivity { activity ->
        val button = all(activity.window.decorView).filterIsInstance<Button>().single { it.text == label }
        assertTrue("Disabled: $label", button.isEnabled); button.performClick()
    }
    private fun launch() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java).putExtra("offline_preview", true))
        awaitLabel("@benim.demo")
    }
    @Test fun navigatingPeopleAndHistoryDoesNotReserveNetworkAndSurvivesRecreation() {
        launch(); click("Kişiler"); click("Analizi aç · @deniz.demo")
        awaitLabel("@deniz.demo"); click("7 gün"); click("Takip edilen grafiği")
        scenario!!.onActivity { activity ->
            val labels = all(activity.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }
            assertTrue(labels.contains("DEMO · ÖRNEK VERİ · AĞ İSTEĞİ YOK"))
            assertTrue(labels.contains("Özel notun"))
            assertTrue(labels.any { it.startsWith("Dönem net farkı:") })
            assertTrue(all(activity.window.decorView).any { it is HistoryChartView })
        }
        assertEquals(0L, RequestGate(context, "preview_gate").nextAllowedAt())
        scenario!!.recreate(); awaitLabel("@deniz.demo")
        assertEquals(0L, RequestGate(context, "preview_gate").nextAllowedAt())
    }
    @Test fun explicitRefreshAddsExactlyOneObservationAndPreservesNote() {
        launch(); click("Kişiler"); click("Analizi aç · @deniz.demo"); click("Kişiyi yenile")
        awaitLabel("Yeni gözlem bu cihazda kaydedildi.")
        val person = SessionVault(context, "previewaccount").read()!!.people.single { it.latest.userId == "200" }
        assertEquals(13, person.history.size); assertEquals(1356L, person.latest.followers)
        assertEquals("Bu bir örnek not. Gerçek hesap verisi değildir.", person.note)
        scenario!!.onActivity { activity ->
            assertFalse(all(activity.window.decorView).filterIsInstance<Button>().single { it.text == "Kişiyi yenile" }.isEnabled)
        }
    }
    @Test fun freshOwnProfileDoesNotReadAgainAcrossRecreation() {
        launch(); scenario!!.recreate(); awaitLabel("@benim.demo")
        assertEquals(0L, RequestGate(context, "preview_gate").nextAllowedAt())
        assertEquals(875L, SessionVault(context, "previewaccount").read()!!.profile.followers)
    }
    private fun dialogNode(text: String): android.view.accessibility.AccessibilityNodeInfo {
        val deadline = android.os.SystemClock.elapsedRealtime() + 5000
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            fun nodes(root: android.view.accessibility.AccessibilityNodeInfo): List<android.view.accessibility.AccessibilityNodeInfo> =
                listOf(root) + (0 until root.childCount).flatMap { root.getChild(it)?.let { child -> nodes(child) }.orEmpty() }
            val roots = instrumentation.uiAutomation.windows.mapNotNull { it.root }
            val node = roots.flatMap { nodes(it) }.firstOrNull {
                it.text?.toString()?.equals(text, ignoreCase = true) == true ||
                    (android.os.Build.VERSION.SDK_INT >= 26 && it.hintText?.toString() == text)
            }
            if (node != null) return node
            Thread.sleep(30)
        }
        error("Dialog control not found: $text")
    }
    private fun setText(label: String, value: String) {
        assertTrue(dialogNode(label).performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,
            android.os.Bundle().apply { putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) }))
    }
    private fun dialogClick(label: String) {
        assertTrue(dialogNode(label).performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }
    @Test fun watchSettingsPersistAcrossRecreationAndDisableCancelsJob() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
                "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")).use { it.readBytes() }
            instrumentation.waitForIdleSync()
        }
        launch(); click("Kişiler"); click("Analizi aç · @deniz.demo")
        click("Değişiklik bildirimini aç"); dialogClick("En az 6 saat arayla"); dialogClick("İzlemeyi aç")
        awaitLabel("Açık · En az 6 saat arayla kontrol edilir.")
        val person = SessionVault(context, "previewaccount").read()!!.people.first()
        assertTrue(person.watching); assertEquals(6, person.intervalHours)
        scenario!!.recreate(); awaitLabel("Açık · En az 6 saat arayla kontrol edilir.")
        click("Bu kişide izlemeyi kapat"); awaitLabel("Kapalı · Bu kişi arka planda sorgulanmaz.")
        assertFalse(SessionVault(context, "previewaccount").read()!!.people.first().watching)
        assertNull(context.getSystemService(android.app.job.JobScheduler::class.java).getPendingJob(PreviewMode.JOB_ID))
        assertEquals(0L, RequestGate(context, "preview_gate").nextAllowedAt())
    }
    @Test fun addEditAndRemoveUseTheRealDialogsAndEncryptedStorage() {
        launch(); click("Kişiler"); click("Kişi ekle")
        setText("@kullanıcıadı veya profil bağlantısı", "@efe.demo")
        dialogClick("Profili getir"); awaitLabel("@efe.demo")
        assertEquals(3, SessionVault(context, "previewaccount").read()!!.people.size)
        click("Not ve grup düzenle")
        setText("Grup (isteğe bağlı)", "Test grubu")
        setText("Yalnız bu cihazda saklanan notun", "Test notu")
        dialogClick("Kaydet"); awaitLabel("Test notu")
        scenario!!.recreate(); awaitLabel("Test notu")
        assertEquals("Test grubu", SessionVault(context, "previewaccount").read()!!.people.last().group)
        click("Kişiyi kaldır"); dialogClick("Kaldır"); awaitLabel("Seçtiğin kişiler")
        assertEquals(2, SessionVault(context, "previewaccount").read()!!.people.size)
    }
}
