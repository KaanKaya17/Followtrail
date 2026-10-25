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
        FollowListVault(context, "previewlists").clear()
        SessionVault(context, "previewaccount").clear()
        context.getSharedPreferences("preview_gate", 0).edit().clear().commit()
    }
    @After fun cleanup() {
        scenario?.close()
        PreviewMode.cancel(context)
        ChangeNotifications(context).clear()
        FollowListVault(context, "previewlists").clear()
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
        val button = all(activity.window.decorView).filterIsInstance<TextView>().single { it.text == label && it.isClickable }
        assertTrue("Disabled: $label", button.isEnabled); button.performClick()
    }
    private fun launch() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java).putExtra("offline_preview", true))
        awaitLabel("@benim.demo")
    }
    private fun clickDescription(description: String) = scenario!!.onActivity { activity ->
        val view = all(activity.window.decorView).single { it.contentDescription?.toString() == description && it.isClickable }
        assertTrue(view.isEnabled); view.performClick()
    }
    @Test fun favoritesStayFirstAcrossRecreationAndRefreshWithoutExtraReads() {
        launch(); click("Kişiler"); clickDescription("Favoriye ekle · @ada.demo")
        val vault = SessionVault(context, "previewaccount")
        fun orderedNames(): List<String> {
            var result = emptyList<String>()
            scenario!!.onActivity { activity -> result = all(activity.window.decorView).filterIsInstance<android.widget.Button>()
                .map { it.text.toString() }.filter { it.startsWith("Analizi aç ·") } }
            return result
        }
        assertEquals(listOf("Analizi aç · @ada.demo", "Analizi aç · @deniz.demo"), orderedNames())
        assertTrue(vault.read()!!.people.single { it.latest.userId == "201" }.favorite)
        assertEquals(0L, RequestGate(context, "preview_gate").attempts())
        scenario!!.recreate(); awaitLabel("Seçtiğin kişiler")
        assertEquals("Analizi aç · @ada.demo", orderedNames().first())
        click("Analizi aç · @ada.demo"); click("Kişiyi yenile"); awaitLabel("Yeni gözlem bu cihazda kaydedildi.")
        val refreshed = vault.read()!!.people.single { it.latest.userId == "201" }
        assertTrue(refreshed.favorite); assertEquals(13, refreshed.history.size); assertEquals("İçerik üreticileri", refreshed.group)
        assertEquals(1L, RequestGate(context, "preview_gate").attempts())
        clickDescription("Favorilerden çıkar · @ada.demo"); click("Tüm kişiler")
        assertEquals("Analizi aç · @deniz.demo", orderedNames().first())
        assertFalse(vault.read()!!.people.single { it.latest.userId == "201" }.favorite)
        assertEquals(1L, RequestGate(context, "preview_gate").attempts())
    }
    @Test fun homeFeedOpensAnalysisAndShowsSavedPagesWithoutRequestingAnything() {
        launch(); awaitLabel("Son değişiklikler")
        scenario!!.onActivity { activity -> all(activity.window.decorView).first {
            it.tag?.toString()?.startsWith("activity:counts:200:") == true }.performClick() }
        awaitLabel("@deniz.demo"); assertEquals(0L, RequestGate(context, "preview_gate").attempts())
        click("Özet")
        val vault = SessionVault(context, "previewaccount"); val saved = vault.read()!!
        val page = FollowPageParser.parse(org.json.JSONObject("""{"status":"ok","users":[],"next_max_id":null}"""),
            "200", FollowDirection.FOLLOWERS, null, System.currentTimeMillis())
        val lists = FollowListVault(context, "previewlists"); lists.commit(saved.session, page)
        scenario!!.recreate(); awaitLabel("Takipçiler · Kaydedilen sayfa: 1")
        assertEquals(saved, vault.read()); assertEquals(0L, RequestGate(context, "preview_gate").attempts())
        assertNotNull(lists.current(saved.session)); assertNull(lists.current(saved.session.copy(userId = "999")))
    }
    @Test fun navigatingPeopleAndHistoryDoesNotReserveNetworkAndSurvivesRecreation() {
        launch(); click("Kişiler"); click("Analizi aç · @deniz.demo")
        awaitLabel("@deniz.demo"); click("7 gün"); click("Takip edilen")
        scenario!!.onActivity { activity ->
            val labels = all(activity.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }
            assertTrue(labels.contains("DEMO · ÖRNEK VERİ"))
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
    @Test fun staleOwnPreviewWaitsForTapAndRepeatedTapUsesFreshCache() {
        val seed = PreviewMode.seed(true)!!
        SessionVault(context, "previewaccount").write(seed.copy(profile = seed.profile.copy(observedAt = System.currentTimeMillis() - 3600000)))
        launch()
        val gate = RequestGate(context, "preview_gate")
        assertFalse(gate.ownAutoRefresh()); assertEquals(0L, gate.attempts())
        scenario!!.recreate(); awaitLabel("@benim.demo"); assertEquals(0L, gate.attempts())
        click("Yenile"); awaitLabel("988")
        assertEquals(1L, gate.attempts())
        scenario!!.onActivity { activity ->
            val refresh = all(activity.window.decorView).filterIsInstance<Button>().single { it.text == "Yenile" }
            assertFalse(refresh.isEnabled); refresh.performClick()
        }
        assertEquals(1L, gate.attempts())
    }
    @Test fun newerVaultObservationWinsOverStaleScreenWithoutAnotherRequest() {
        val seed = PreviewMode.seed(true)!!
        val vault = SessionVault(context, "previewaccount")
        vault.write(seed.copy(profile = seed.profile.copy(observedAt = System.currentTimeMillis() - 3600000)))
        launch()
        vault.update(seed.session) { it.copy(profile = it.profile.copy(followers = 876, observedAt = System.currentTimeMillis())) }
        click("Yenile"); awaitLabel("876")
        assertEquals(0L, RequestGate(context, "preview_gate").attempts())
        click("Kişiler"); click("Analizi aç · @deniz.demo")
        vault.update(seed.session) { current ->
            val previous = current.people.first().latest
            current.copy(people = PeoplePolicy.record(current.people, previous.copy(followers = 1400, observedAt = System.currentTimeMillis())))
        }
        click("Kişiyi yenile"); awaitLabel("1.400")
        assertEquals(0L, RequestGate(context, "preview_gate").attempts())
        assertEquals(13, vault.read()!!.people.first().history.size)
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
        click("Değişiklik bildirimi"); dialogClick("En az 6 saat arayla"); dialogClick("İzlemeyi aç")
        awaitLabel("6 saatte bir")
        val person = SessionVault(context, "previewaccount").read()!!.people.first()
        assertTrue(person.watching); assertEquals(360, person.intervalMinutes)
        scenario!!.recreate(); awaitLabel("6 saatte bir")
        click("Değişiklik bildirimi")
        scenario!!.onActivity { activity -> assertFalse(all(activity.window.decorView).filterIsInstance<android.widget.Switch>().single().isChecked) }
        assertFalse(SessionVault(context, "previewaccount").read()!!.people.first().watching)
        assertNull(context.getSystemService(android.app.job.JobScheduler::class.java).getPendingJob(PreviewMode.JOB_ID))
        assertEquals(0L, RequestGate(context, "preview_gate").nextAllowedAt())
    }

    @Test fun minuteAndTwelveHourChoicesPersistAndLocalStatusRefreshHonorsRestrictionsWithoutRequests() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
                "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")).use { it.readBytes() }
        }
        // Keep all target observations fresh: enabling a due watch may legitimately start the real Android job.
        // This test isolates local settings/status; MonitoringTest verifies the due-job transport separately.
        val seed = PreviewMode.seed(true)!!
        val now = System.currentTimeMillis()
        SessionVault(context, "previewaccount").write(seed.copy(people = seed.people.map {
            it.copy(history = it.history.dropLast(1) + it.latest.copy(observedAt = now))
        }))
        launch(); click("Kişiler"); click("Analizi aç · @deniz.demo")
        click("Değişiklik bildirimi"); dialogClick("En az 15 dakika arayla"); dialogClick("İzlemeyi aç")
        awaitLabel("15 dakikada bir")
        val vault = SessionVault(context, "previewaccount"); val gate = RequestGate(context, "preview_gate")
        assertEquals(15, vault.read()!!.people.first().intervalMinutes)
        scenario!!.recreate(); awaitLabel("15 dakikada bir")
        for ((choice, label, minutes) in listOf(Triple("En az 30 dakika arayla", "30 dakikada bir", 30),
                Triple("En az 12 saat arayla", "12 saatte bir", 720))) {
            click("Kontrol aralığını değiştir"); dialogClick(choice); dialogClick("İzlemeyi aç")
            awaitLabel(label); assertEquals(minutes, vault.read()!!.people.first().intervalMinutes)
        }
        val before = vault.read()!!
        gate.limited(null)
        click("Durumu güncelle"); awaitLabel("Sıradaki kontrol: beklemede.")
        scenario!!.onActivity { activity ->
            assertTrue(all(activity.window.decorView).filterIsInstance<TextView>().any { it.text.toString().startsWith("Instagram molası var:") })
        }
        assertEquals(before, vault.read()); assertTrue(gate.manualOnly()); assertEquals(0L, gate.attempts())
        assertNull(context.getSystemService(android.app.job.JobScheduler::class.java).getPendingJob(PreviewMode.JOB_ID))
        scenario!!.recreate(); awaitLabel("12 saatte bir"); awaitLabel("Sıradaki kontrol: beklemede.")
        assertEquals(0L, gate.attempts())
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
