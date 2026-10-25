package dev.kaankaya.instagramtracker

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class MemberProfileFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val vault = SessionVault(context, "previewaccount")
    private val lists = FollowListVault(context, "previewlists")
    private val preferences = context.getSharedPreferences("preview_gate", 0)
    private var scenario: ActivityScenario<MemberProfileActivity>? = null
    @Before fun setup() {
        check(context.packageName.endsWith(".debug")); PreviewMode.cancel(context); preferences.edit().clear().commit(); lists.clear()
        val seed = PreviewMode.seed(true)!!; vault.write(seed)
        lists.commit(seed.session, FollowPageParser.parse(org.json.JSONObject("""{"status":"ok","users":[
            {"pk":"501","username":"kisi501.demo","full_name":"Örnek kişi"},{"pk":"201","username":"ada.demo"}],"next_max_id":null}"""),
            "100", FollowDirection.FOLLOWERS, null, System.currentTimeMillis()))
    }
    @After fun cleanup() {
        scenario?.close(); PreviewMode.cancel(context); vault.clear(); lists.clear(); preferences.edit().clear().commit()
        ChangeNotifications(context).clear(); AvatarCache(context, "previewavatars").clear()
    }
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun launch(id: String = "501", viewer: String = "100") {
        scenario = ActivityScenario.launch(Intent(context, MemberProfileActivity::class.java).putExtra("viewer_id", viewer)
            .putExtra("list_target_id", "100").putExtra("list_direction", "FOLLOWERS").putExtra("member_id", id).putExtra("offline_preview", true))
    }
    private fun await(text: String) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 5000
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            var found = false; scenario!!.onActivity { found = views(it.window.decorView).filterIsInstance<TextView>().any { v -> v.text == text } }
            if (found) return; Thread.sleep(20)
        }
        fail("Missing profile text: $text")
    }
    private fun click(text: String) = scenario!!.onActivity { activity ->
        val button = views(activity.window.decorView).filterIsInstance<Button>().single { it.text == text }
        assertTrue(button.isEnabled); button.performClick()
    }
    @Test fun unregisteredProfileReadsOnceSurvivesRotationAndOnlyAddsOnExplicitAction() {
        val before = vault.read()!!; launch(); await("Takipçi · 988")
        assertEquals(1L, RequestGate(context, "preview_gate").attempts()); assertEquals(before, vault.read())
        scenario!!.recreate(); await("Takipçi · 988"); assertEquals(1L, RequestGate(context, "preview_gate").attempts())
        scenario!!.moveToState(androidx.lifecycle.Lifecycle.State.CREATED); scenario!!.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        await("@kisi501.demo"); assertEquals(1L, RequestGate(context, "preview_gate").attempts())
        click("Kişilere ekle"); await("Listelerini incele")
        val saved = vault.read()!!; assertEquals(before.people, saved.people.take(2))
        val added = saved.people.single { it.latest.userId == "501" }; assertFalse(added.watching); assertFalse(added.favorite)
        assertEquals(before.automation, saved.automation); assertEquals(1L, RequestGate(context, "preview_gate").attempts())
    }
    @Test fun freshRegisteredProfileUsesTheExistingObservationWithoutAnotherRequest() {
        val original = vault.read()!!; val fresh = original.people.single { it.latest.userId == "201" }.latest.copy(observedAt = System.currentTimeMillis())
        vault.write(original.copy(people = original.people.map { if (it.latest.userId == "201") it.copy(history = listOf(fresh)) else it }))
        val before = vault.read(); launch("201"); await("Takipçi · ${fresh.followers}")
        assertEquals(0L, RequestGate(context, "preview_gate").attempts()); assertEquals(before, vault.read())
        scenario!!.recreate(); await("Takipçi · ${fresh.followers}"); assertEquals(0L, RequestGate(context, "preview_gate").attempts())
    }
    @Test fun aRestrictionShowsOnlyTheKnownListRowAndBackDoesNotSendARequest() {
        val gate = RequestGate(context, "preview_gate"); gate.limited(null); val before = vault.read()
        launch(); await("@kisi501.demo")
        scenario!!.onActivity { activity ->
            val labels = views(activity.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }
            assertFalse(labels.any { it.startsWith("Takipçi ·") })
            views(activity.window.decorView).single { it.contentDescription == "Listeye dön" }.performClick()
        }
        val deadline = android.os.SystemClock.elapsedRealtime() + 5000
        while (scenario!!.state != androidx.lifecycle.Lifecycle.State.DESTROYED && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(20)
        assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED, scenario!!.state)
        assertEquals(0L, gate.attempts()); assertTrue(gate.manualOnly()); assertEquals(before, vault.read())
    }
    @Test fun foreignViewersAndIdsNotInTheSourceListNeverReadAProfile() {
        for ((id, viewer) in listOf("501" to "999", "777" to "100")) {
            launch(id, viewer); await("Profil açılamadı"); assertEquals(0L, RequestGate(context, "preview_gate").attempts())
            scenario!!.close(); scenario = null
        }
    }
}
