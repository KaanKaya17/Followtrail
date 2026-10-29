package dev.kaankaya.instagramtracker

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/** Actual encrypted account and Activity, offline preview only. */
class ClarityFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val vault = SessionVault(context, "previewaccount")
    private var scenario: ActivityScenario<MainActivity>? = null
    private var oldLanguage = "tr"
    private fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    @Before fun before() {
        check(context.packageName.endsWith(".debug")); oldLanguage = AppLanguage.selected(context)
        instrumentation.runOnMainSync { AppLanguage.set(context, "tr") }
        clean()
    }
    private fun clean() {
        vault.clear(); SessionVault(context, "clarity").clear(); FollowListVault(context, "previewlists").clear()
        context.getSharedPreferences("preview_gate", 0).edit().clear().commit()
    }
    @After fun after() {
        scenario?.close(); PreviewMode.cancel(context); clean()
        instrumentation.runOnMainSync { AppLanguage.set(context, oldLanguage) }
    }
    private fun seed(people: List<TrackedPerson>) {
        vault.write(PreviewMode.seed(true)!!.copy(people = people, automation = ListAutomation()))
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java).putExtra("offline_preview", true))
        await { all -> all.filterIsInstance<TextView>().any { it.text == "Kaydedilen Profiller" } }
        click("Kaydedilen Profiller")
    }
    private fun await(predicate: (List<View>) -> Boolean) {
        val end = android.os.SystemClock.elapsedRealtime() + 7000
        while (android.os.SystemClock.elapsedRealtime() < end) {
            var ready = false
            scenario!!.onActivity { ready = predicate(views(it.window.decorView)) }
            if (ready) return
            Thread.sleep(25)
        }
        fail("Offline clarity screen did not reach expected state")
    }
    private fun click(label: String) {
        await { all -> all.filterIsInstance<TextView>().any { it.text == label } }
        scenario!!.onActivity { activity -> views(activity.window.decorView).filterIsInstance<TextView>().single { it.text == label }.performClick() }
    }
    private fun capture(name: String) {
        instrumentation.waitForIdleSync()
        // Layout/scroll completion can precede the compositor's next frame.
        Thread.sleep(300)
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            java.io.File(context.cacheDir, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    @Test fun aHundredFullHistoriesUseTheExpandedEncryptedAccountWithoutLosingThePreviousFileOnCapacityFailure() {
        val own = PreviewMode.seed(true)!!
        val people = (1..100).map { id -> TrackedPerson((1..90).map { n ->
            Profile((1000 + id).toString(), "person$id", 3, 4, 2_000_000_000_000L + n * 60_000)
        }) }
        val saved = own.copy(people = people, automation = ListAutomation())
        val bytes = AccountCodec.encode(saved).toByteArray().size
        assertTrue(bytes > 512 * 1024); assertTrue(bytes < SessionVault.ACCOUNT_BYTES)
        val account = SessionVault(context, "clarity"); account.write(saved)
        assertEquals(saved, SessionVault(context, "clarity").read())
        assertEquals(ApiFailure.Kind.CAPACITY, assertThrows(ApiFailure::class.java) {
            EncryptedRecord(context, "clarity", SessionVault.ACCOUNT_BYTES).write("x".repeat(SessionVault.ACCOUNT_BYTES + 1))
        }.kind)
        assertEquals(saved, account.read())
    }
    @Test fun ninetyMinuteSamplesShowTheirActualIntervalAndTheDayFilterDoesNotInventCoverage() {
        val now = System.currentTimeMillis()
        val person = TrackedPerson((0 until 90).map { n -> Profile("200", "deniz.demo", 3, 4, now - (89 - n) * 60_000) })
        seed(listOf(person)); click("Analizi aç · @deniz.demo")
        await { all -> all.filterIsInstance<TextView>().any { it.text.toString().contains("90 kayıt · Kayıtlı aralık: 0 gün 1 saat 29 dakika") } }
        click("7 gün")
        await { all -> all.filterIsInstance<TextView>().any { it.height > 0 && it.text.toString().contains("90 kayıt · Kayıtlı aralık: 0 gün 1 saat 29 dakika") } }
        instrumentation.waitForIdleSync()
        scenario!!.onActivity { activity ->
            val all = views(activity.window.decorView)
            val label = all.filterIsInstance<TextView>().single { it.text.toString().contains("90 kayıt · Kayıtlı aralık:") }
            val scroll = all.filterIsInstance<android.widget.ScrollView>().first()
            val rect = android.graphics.Rect(); label.getDrawingRect(rect); scroll.offsetDescendantRectToMyCoords(label, rect)
            scroll.scrollTo(0, (rect.top - AppUi.dp(activity, 80)).coerceAtLeast(0))
        }
        await { all -> all.filterIsInstance<TextView>().any { it.text.toString().contains("90 kayıt · Kayıtlı aralık:") && it.getGlobalVisibleRect(android.graphics.Rect()) } }
        capture("clarity-history.png")
        assertEquals(0L, RequestGate(context, "preview_gate").attempts())
    }
    @Test fun aHundredPeopleOpenInBatchesWithFavoritesFirstAndNoNewReads() {
        val people = (1..100).map { id -> TrackedPerson(listOf(Profile((1000 + id).toString(), "person$id", 3, 4, System.currentTimeMillis())), favorite = id == 100) }
        seed(people)
        click("Özet")
        await { all -> all.filterIsInstance<TextView>().any { it.text.toString().contains("Şu anki durum") } }
        capture("clarity-overview.png"); click("Kaydedilen Profiller")
        await { all -> all.count { it.tag?.toString()?.startsWith("profile-status:") == true } == 20 }
        scenario!!.onActivity { activity ->
            val tags = views(activity.window.decorView).mapNotNull { it.tag as? String }.filter { it.startsWith("profile-status:") }
            assertEquals("profile-status:1100", tags.first())
        }
        click("Daha fazla kişi göster")
        await { all -> all.count { it.tag?.toString()?.startsWith("profile-status:") == true } == 40 }
        scenario!!.recreate()
        await { all -> all.count { it.tag?.toString()?.startsWith("profile-status:") == true } == 40 }
        assertEquals(100, vault.read()!!.people.size); assertEquals(0L, RequestGate(context, "preview_gate").attempts())
    }
}
