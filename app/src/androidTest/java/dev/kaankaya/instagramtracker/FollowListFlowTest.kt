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

/** Real Activity, Keystore and parser; debug-only synthetic transport. */
class FollowListFlowTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences = context.getSharedPreferences("preview_gate", 0)
    private val vault = SessionVault(context, "previewaccount")
    private val lists = FollowListVault(context, "previewlists")
    private var scenario: ActivityScenario<FollowListActivity>? = null
    @Before fun setup() {
        check(context.packageName.endsWith(".debug")); lists.clear(); preferences.edit().clear().commit()
        vault.write(PreviewMode.seed(true)!!)
    }
    @After fun cleanup() { scenario?.close(); lists.clear(); vault.clear(); preferences.edit().clear().commit() }
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun launch() {
        scenario = ActivityScenario.launch(Intent(context, FollowListActivity::class.java).putExtra("target_id", "200").putExtra("offline_preview", true))
        awaitText("Henüz liste okunmadı.")
    }
    private fun click(text: String) = scenario!!.onActivity { activity ->
        val button = views(activity.window.decorView).filterIsInstance<Button>().single { it.text.toString() == text }
        assertTrue("Button disabled: $text", button.isEnabled); button.performClick()
    }
    private fun awaitText(text: String) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 5000
        var found = false
        while (!found && android.os.SystemClock.elapsedRealtime() < deadline) {
            scenario!!.onActivity { activity -> found = views(activity.window.decorView).filterIsInstance<TextView>().any { it.text.toString() == text } }
            if (!found) Thread.sleep(20)
        }
        assertTrue("Missing label: $text", found)
    }
    @Suppress("DEPRECATION")
    @Test fun manualPagesPersistWithoutAutomaticRequestsAcrossTabsAndRecreation() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        launch(); val gate = RequestGate(context, "preview_gate")
        assertEquals(0L, gate.attempts()); scenario!!.recreate(); assertEquals(0L, gate.attempts())
        click("İlk sayfayı oku"); awaitText("Alınan hesap: 2 · Sayfa: 1")
        assertEquals(1L, gate.attempts()); assertEquals("demo-page-2", lists.read(vault.read()!!.session, "200")!!.followers!!.cursor())
        preferences.edit().putLong("next_read_at", 0).commit(); scenario!!.recreate()
        awaitText("Alınan hesap: 2 · Sayfa: 1"); assertEquals(1L, gate.attempts())
        click("Sonraki sayfayı oku"); awaitText("Alınan hesap: 3 · Sayfa: 2")
        assertEquals(2L, gate.attempts())
        click("Takip edilenler"); awaitText("Henüz liste okunmadı."); assertEquals(2L, gate.attempts())
        preferences.edit().putLong("next_read_at", 0).commit(); scenario!!.recreate()
        click("İlk sayfayı oku"); awaitText("Alınan hesap: 2 · Sayfa: 1")
        assertEquals(3L, gate.attempts()); assertEquals(1, FollowAnalysis.compare(lists.read(vault.read()!!.session, "200")!!)!!.mutual.size)
        scenario!!.close(); scenario = ActivityScenario.launch(Intent(context, FollowListActivity::class.java)
            .putExtra("target_id", "200").putExtra("offline_preview", true))
        awaitText("Alınan hesap: 3 · Sayfa: 2"); assertEquals(3L, gate.attempts())
        val bytes = context.noBackupFilesDir.resolve("previewlists.enc").readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(bytes.contains("kisi501.demo"))
    }
    @Test fun tappingAFilteredMemberOpensItsObservedInstagramProfileWithoutAnotherRead() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        launch(); click("İlk sayfayı oku"); awaitText("Alınan hesap: 2 · Sayfa: 1")
        val filter = android.content.IntentFilter(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_BROWSABLE); addDataScheme("https")
            addDataAuthority("www.instagram.com", null); addDataPath("/kisi502.demo/", android.os.PatternMatcher.PATTERN_LITERAL)
        }
        // A second monitor blocks any unexpected external navigation too, so failure cannot leave the test app.
        val expected = instrumentation.addMonitor(filter, android.app.Instrumentation.ActivityResult(0, null), true)
        val fallback = instrumentation.addMonitor(android.content.IntentFilter(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_BROWSABLE); addDataScheme("https")
        }, android.app.Instrumentation.ActivityResult(0, null), true)
        try {
            scenario!!.onActivity { activity ->
                views(activity.window.decorView).filterIsInstance<android.widget.EditText>().single().setText("502")
                val list = views(activity.window.decorView).filterIsInstance<android.widget.ListView>().single()
                list.performItemClick(null, list.headerViewsCount, 0)
            }
            assertEquals(1, expected.hits); assertEquals(0, fallback.hits)
            assertEquals(1L, RequestGate(context, "preview_gate").attempts())
            assertEquals(2, vault.read()!!.people.size)
        } finally { instrumentation.removeMonitor(expected); instrumentation.removeMonitor(fallback) }
    }
    @Test fun localSearchFiltersLoadedUsersWithoutRequestsAndRestoresQuery() {
        launch(); click("İlk sayfayı oku"); awaitText("Alınan hesap: 2 · Sayfa: 1")
        scenario!!.onActivity { activity ->
            views(activity.window.decorView).filterIsInstance<android.widget.EditText>().single().setText("501")
            val adapter = views(activity.window.decorView).filterIsInstance<android.widget.ListView>().single().adapter
            assertEquals(listOf("501"), (0 until adapter.count).mapNotNull { (adapter.getItem(it) as? FollowUser)?.id })
        }
        scenario!!.recreate(); awaitText("Alınan hesap: 2 · Sayfa: 1")
        scenario!!.onActivity { activity ->
            val search = views(activity.window.decorView).filterIsInstance<android.widget.EditText>().single()
            assertEquals("501", search.text.toString()); search.setText("missing")
            val adapter = views(activity.window.decorView).filterIsInstance<android.widget.ListView>().single().adapter
            assertTrue((0 until adapter.count).none { adapter.getItem(it) is FollowUser })
            val labels = views(activity.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }
            assertTrue(labels.contains("Aramana uygun hesap yok")); assertFalse(labels.contains("Bu kayıtta hesap yok"))
            search.setText("")
            assertEquals(2, (0 until adapter.count).count { adapter.getItem(it) is FollowUser })
        }
        assertEquals(1L, RequestGate(context, "preview_gate").attempts())
    }
    @Test fun cachedLimitedAndEmptyPagesKeepTheirMeaningWithoutRequests() {
        val account = vault.read()!!
        val partial = FollowPageParser.parse(org.json.JSONObject("""{"status":"ok","users":[{"pk":"501","username":"kisi501.demo"}],"should_limit_list_of_followers":true}"""),
            "200", FollowDirection.FOLLOWERS, null, 1000)
        lists.commit(account.session, partial)
        val empty = FollowPageParser.parse(org.json.JSONObject("""{"status":"ok","users":[],"next_max_id":null}"""),
            "200", FollowDirection.FOLLOWING, null, 2000)
        val saved = lists.commit(account.session, empty)
        scenario = ActivityScenario.launch(Intent(context, FollowListActivity::class.java).putExtra("target_id", "200").putExtra("offline_preview", true))
        awaitText("Alınan hesap: 1 · Sayfa: 1"); awaitText(context.getString(R.string.list_limited))
        click("Takip edilenler"); awaitText("Alınan hesap: 0 · Sayfa: 1"); awaitText("Bu kayıtta hesap yok")
        scenario!!.recreate(); awaitText("Alınan hesap: 0 · Sayfa: 1")
        assertEquals(saved, lists.read(account.session, "200")); assertEquals(0L, RequestGate(context, "preview_gate").attempts())
    }
    @Test fun existingRestrictionBlocksPageAndPreservesLastSnapshot() {
        launch(); click("İlk sayfayı oku"); awaitText("Alınan hesap: 2 · Sayfa: 1")
        val saved = lists.read(vault.read()!!.session, "200")
        val gate = RequestGate(context, "preview_gate"); gate.limited("7200"); scenario!!.recreate()
        scenario!!.onActivity { activity ->
            assertFalse(views(activity.window.decorView).filterIsInstance<Button>().single { it.text == "Sonraki sayfayı oku" }.isEnabled)
        }
        assertEquals(1L, gate.attempts()); assertEquals(saved, lists.read(vault.read()!!.session, "200"))
    }
    @Test fun changedAccountPreventsAnOldScreenFromSendingItsRequest() {
        launch()
        val account = vault.read()!!
        vault.write(account.copy(session = account.session.copy(cookie = "ds_user_id=100; sessionid=changed")))
        click("İlk sayfayı oku")
        awaitText(ApiFailure(ApiFailure.Kind.CANCELLED).userMessage())
        assertEquals(0L, RequestGate(context, "preview_gate").attempts())
        assertNull(lists.read(account.session, "200"))
    }
    @Test fun storageCapacityFailurePreservesPreviousPageAndAccountScope() {
        val session = vault.read()!!.session
        val page = FollowPageParser.parse(org.json.JSONObject("""{"status":"ok","users":[{"pk":"1"}],"next_max_id":"next"}"""),
            "200", FollowDirection.FOLLOWERS, null, 1000)
        val before = lists.commit(session, page)
        val huge = page.copy(inputCursor = "next", users = listOf(page.users.single().copy(name = "x".repeat(600 * 1024))))
        assertEquals(ApiFailure.Kind.CAPACITY, assertThrows(ApiFailure::class.java) { lists.commit(session, huge) }.kind)
        assertEquals(before, lists.read(session, "200")); assertNull(lists.read(session.copy(userId = "999"), "200"))
        lists.clearTarget(session, "200"); assertNull(lists.read(session, "200"))
    }
}
