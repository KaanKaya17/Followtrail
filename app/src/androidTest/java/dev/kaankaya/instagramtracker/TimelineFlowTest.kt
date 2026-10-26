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

class TimelineFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val vault = SessionVault(context, "previewaccount")
    private var scenario: ActivityScenario<TimelineActivity>? = null
    private var originalLanguage = "tr"
    @Before fun setup() {
        check(context.packageName.endsWith(".debug")); originalLanguage = AppLanguage.selected(context)
        instrumentation.runOnMainSync { AppLanguage.set(context, "tr") }; instrumentation.waitForIdleSync()
        vault.clear(); vault.write(PreviewMode.seed(true)!!)
        context.getSharedPreferences("preview_gate", 0).edit().clear().commit()
    }
    @After fun cleanup() {
        scenario?.close(); vault.clear(); PreviewMode.cancel(context)
        context.getSharedPreferences("preview_gate", 0).edit().clear().commit()
        instrumentation.runOnMainSync { AppLanguage.set(context, originalLanguage) }; instrumentation.waitForIdleSync()
    }
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun texts(): List<String> {
        var values = emptyList<String>(); scenario!!.onActivity { values = views(it.window.decorView).filterIsInstance<TextView>().map { view -> view.text.toString() } }; return values
    }
    private fun launch(owner: String = vault.read()!!.session.userId, target: String? = "201") {
        scenario = ActivityScenario.launch(Intent(context, TimelineActivity::class.java).putExtra("viewer_id", owner)
            .putExtra("target_id", target).putExtra("offline_preview", true))
    }
    @Test fun timelineShowsLocalObservationAndComparisonTimesAndPreservesRestrictionsWithoutRequests() {
        val saved = vault.read()!!; val person = saved.people.single { it.latest.userId == "201" }.latest
        vault.record(saved.session, CheckEvent("201", person.username, CheckKind.PROFILE, 1000, 2000, 3000, 500, 1, 0, setOf(ProfileChangeField.BIOGRAPHY)))
        vault.record(saved.session, CheckEvent("200", "deniz.demo", CheckKind.PROFILE, null, 4000, 5000))
        val gate = RequestGate(context, "preview_gate"); gate.limited(null); val pause = gate.blockedUntil()
        launch()
        val labels = texts()
        assertTrue(labels.contains("Zaman çizelgesi")); assertTrue(labels.contains("Profil değişikliği görüldü"))
        assertTrue(labels.any { it.contains("Biyografi değişti") }); assertFalse(labels.any { it == "@deniz.demo" })
        assertTrue(labels.any { it.startsWith("Görüldü ·") }); assertTrue(labels.any { it.startsWith("İşlendi ·") })
        assertTrue(labels.any { it.contains("kesin olay saati değildir") })
        scenario!!.recreate(); assertTrue(texts().contains("Profil değişikliği görüldü"))
        assertEquals(0L, gate.attempts()); assertEquals(pause, gate.blockedUntil()); assertEquals(saved, vault.read())
    }
    @Test fun englishTimelineShowsListAnalysisAndMissingTimesWithoutInventingThem() {
        instrumentation.runOnMainSync { AppLanguage.set(context, "en") }; instrumentation.waitForIdleSync()
        val saved = vault.read()!!
        vault.record(saved.session, CheckEvent("201", "ada.demo", CheckKind.LIST, null, 2000, 3000, 1000,
            direction = FollowDirection.FOLLOWERS, coverage = FollowCoverage.END_OBSERVED, pages = 2, accounts = 100, added = 1, missing = 1))
        launch(); val labels = texts()
        assertTrue(labels.contains("Timeline")); assertTrue(labels.contains("Followers · Lists compared"))
        assertTrue(labels.any { it.contains("Newly seen: 1") }); assertEquals(0L, RequestGate(context, "preview_gate").attempts())
    }
    @Test fun aForeignViewerCannotOpenLocalHistory() {
        launch("999"); val end = android.os.SystemClock.elapsedRealtime() + 5000
        while (scenario!!.state != androidx.lifecycle.Lifecycle.State.DESTROYED && android.os.SystemClock.elapsedRealtime() < end) Thread.sleep(20)
        assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED, scenario!!.state)
    }
}
