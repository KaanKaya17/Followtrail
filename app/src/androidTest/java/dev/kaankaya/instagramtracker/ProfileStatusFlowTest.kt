package dev.kaankaya.instagramtracker

import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/** Actual Activity, encrypted records and periodic local refresh. No live network. */
class ProfileStatusFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val vault = SessionVault(context, "previewaccount")
    private val lists = FollowListVault(context, "previewlists")
    private val gate = RequestGate(context, "preview_gate")
    private var scenario: ActivityScenario<MainActivity>? = null
    private var originalLanguage = "tr"
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    @Before fun prepare() {
        check(context.packageName.endsWith(".debug"))
        instrumentation.uiAutomation.serviceInfo = instrumentation.uiAutomation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        originalLanguage = AppLanguage.selected(context)
        instrumentation.runOnMainSync { AppLanguage.set(context, "tr") }; instrumentation.waitForIdleSync()
        vault.clear(); lists.clear(); context.getSharedPreferences("preview_gate", 0).edit().clear().commit()
        if (Build.VERSION.SDK_INT >= 33) android.os.ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")).use { it.readBytes() }
        ChangeNotifications(context).ensureChannel()
        // Isolate status rendering from an independent Android job becoming due during the 30s UI check.
        assertTrue(gate.reserveAutomatic())
        val seed = PreviewMode.seed(true)!!; val now = System.currentTimeMillis()
        vault.write(seed.copy(people = seed.people.map { person -> if (person.latest.userId != "200") person else
            person.copy(history = person.history.dropLast(1) + person.latest.copy(observedAt = now - 240_000),
                watching = true, nextCheckAt = now + 900_000) }, automation = ListAutomation(true, setOf("200"))))
        val page = FollowPage("200", FollowDirection.FOLLOWERS, null,
            listOf(FollowUser("501", "sample", null, null, null, null)), FollowField(true, "next"),
            FollowField(true, false), FollowField(true, true), FollowField(false, null), now - 60_000)
        lists.commitCurrent(seed.session, page, null)
    }
    @After fun cleanup() {
        scenario?.close(); PreviewMode.cancel(context); ChangeNotifications(context).clear()
        vault.clear(); lists.clear(); context.getSharedPreferences("preview_gate", 0).edit().clear().commit()
        instrumentation.runOnMainSync { AppLanguage.set(context, originalLanguage) }; instrumentation.waitForIdleSync()
    }
    private fun launch() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java).putExtra("offline_preview", true))
        await("Otomatik kontrol kapalı", "100"); instrumentation.waitForIdleSync(); PreviewMode.cancel(context)
    }
    private fun click(text: String) = scenario!!.onActivity { activity ->
        views(activity.window.decorView).single { it is TextView && it.text.toString() == text && it.isClickable }.performClick()
    }
    private fun status(id: String): TextView? {
        var label: TextView? = null
        scenario!!.onActivity { activity ->
            val card = views(activity.window.decorView).firstOrNull { it.tag == "profile-status:$id" }
            label = card?.let { views(it).filterIsInstance<TextView>().single() }
        }
        return label
    }
    private fun await(text: String, id: String, timeout: Long = 7000): TextView {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < deadline) {
            val label = status(id)
            if (label?.text?.contains(text) == true) return label
            Thread.sleep(30)
        }
        error("Missing status $text: ${status(id)?.text}")
    }
    private fun awaitDialog(text: String): String {
        fun content() = instrumentation.uiAutomation.windows.flatMap { window ->
            fun labels(node: android.view.accessibility.AccessibilityNodeInfo?): List<String> = if (node == null) emptyList() else
                listOfNotNull(node.text?.toString()) + (0 until node.childCount).flatMap { labels(node.getChild(it)) }
            labels(window.root)
        }.joinToString("\n")
        val deadline = SystemClock.elapsedRealtime() + 7000
        var body = content()
        while (!body.contains(text) && SystemClock.elapsedRealtime() < deadline) {
            Thread.sleep(30); body = content()
        }
        assertTrue(body.take(1200), body.contains(text)); return body
    }
    @Test fun ownAndPeopleSummariesSeparateSuccessfulProfileTimeFromPendingListPages() {
        val before = vault.read(); val archive = lists.archive(before!!.session)
        launch(); click("Kaydedilen Profiller")
        val label = await("Profil ve alınan liste sayfaları · Kısmi", "200")
        assertEquals(3, label.text.lines().size); assertTrue(label.text.contains("Veri kapsamı")); assertTrue(label.text.contains("Şu anki durum")); assertTrue(label.text.contains("4 dakika önce")); assertTrue(label.text.contains("Otomatik kontrol planlandı"))
        await("Otomatik kontrol kapalı", "201")
        click("Analizi aç · @deniz.demo"); await("Profil ve alınan liste sayfaları · Kısmi", "200")
        scenario!!.onActivity { activity -> views(activity.window.decorView).single { it.tag == "profile-status:200" }.performClick() }
        instrumentation.waitForIdleSync()
        val body = awaitDialog("Son başarılı profil okuması")
        assertTrue(body.take(1200), body.contains("Takip edilenler · Henüz kayıt yok"))
        assertTrue(body.contains("Liste devamı bekliyor"))
        assertTrue(body.contains("ek Instagram isteği göndermez"))
        assertEquals(0L, gate.attempts()); assertEquals(before, vault.read()); assertEquals(archive, lists.archive(before.session))
    }
    @Test fun unchangedObservationAndRestrictionHaveDistinctAccurateStatesWithoutRequests() {
        val saved = vault.read()!!; val p = saved.people.first().latest
        vault.record(saved.session, CheckEvent(p.userId, p.username, CheckKind.PROFILE, p.observedAt,
            p.observedAt, p.observedAt, p.observedAt - 60_000, 0, 0))
        launch(); click("Kaydedilen Profiller"); await("Son profil karşılaştırmasında fark yok", "200")
        gate.limited(null); val pause = gate.blockedUntil()
        scenario!!.recreate(); await("Instagram molası", "200")
        assertEquals(pause, gate.blockedUntil()); assertEquals(0L, gate.attempts()); assertEquals(saved, vault.read())
    }
    @Test fun visibleLocalRefreshUpdatesTheSameLabelWithoutAnotherReadOrRebuildingTheScreen() {
        launch(); click("Kaydedilen Profiller"); val label = await("4 dakika önce", "200")
        val saved = vault.read()!!
        vault.update(saved.session) { current -> current.copy(people = current.people.map { person ->
            if (person.latest.userId != "200") person else person.copy(history = person.history + person.latest.copy(observedAt = System.currentTimeMillis()))
        }) }
        val refreshed = await("Az önce", "200", 36_000)
        assertSame(label, refreshed); assertEquals(0L, gate.attempts())
    }
    @Test fun englishFailureSummaryDoesNotChangeStoredDataOrRestrictions() {
        val saved = vault.read()!!
        vault.recordFailure(saved.session, "200", System.currentTimeMillis(), ApiFailure(ApiFailure.Kind.CONNECTION))
        instrumentation.runOnMainSync { AppLanguage.set(context, "en") }; instrumentation.waitForIdleSync()
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java).putExtra("offline_preview", true))
        await("Automatic checks off", "100"); PreviewMode.cancel(context); click("Saved Profiles")
        await("Last check did not finish", "200"); await("Profile and received list pages · Partial", "200")
        assertEquals(saved, vault.read()); assertEquals(0L, gate.attempts()); assertFalse(gate.manualOnly())
    }
    @Test fun aNewProfileCountQueuesItsCompletedListWithoutClaimingAReadIsRunning() {
        val saved = vault.read()!!; val p = saved.people.first().latest
        lists.clear()
        for (direction in FollowDirection.entries) lists.commitCurrent(saved.session, FollowPage("200", direction, null,
            emptyList(), FollowField(true, null), FollowField(true, false), FollowField(true, false),
            FollowField(false, null), p.observedAt + 1000), null, ListBasis.of(p, direction))
        launch(); click("Kaydedilen Profiller"); await("İki liste sonu da gözlendi", "200")
        vault.update(saved.session) { current -> current.copy(people = current.people.map { person ->
            if (person.latest.userId != "200") person else person.copy(history = person.history +
                person.latest.copy(observedAt = System.currentTimeMillis(), followers = p.followers + 1))
        }) }
        scenario!!.recreate(); await("İki liste sonu da gözlendi", "200")
        scenario!!.onActivity { activity -> views(activity.window.decorView).single { it.tag == "profile-status:200" }.performClick() }
        awaitDialog("Liste güncellemesi sırada")
        assertEquals(0L, gate.attempts())
    }

    @Test fun unreadableListArchiveIsUnknownRatherThanAnEmptyOrInitialList() {
        val saved = vault.read()!!
        EncryptedRecord(context, "previewlists").write("{")
        launch(); click("Kaydedilen Profiller"); await("Liste durumu okunamadı", "200")
        scenario!!.onActivity { activity -> views(activity.window.decorView).single { it.tag == "profile-status:200" }.performClick() }
        val body = awaitDialog("Liste durumu okunamadı")
        assertFalse(body.contains("Takipçiler · Henüz kayıt yok"))
        assertFalse(body.contains("Takip edilenler · Henüz kayıt yok"))
        assertEquals(saved, vault.read()); assertEquals("{", EncryptedRecord(context, "previewlists").read())
        assertEquals(0L, gate.attempts())
    }

}
