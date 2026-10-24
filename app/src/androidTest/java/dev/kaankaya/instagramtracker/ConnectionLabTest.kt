package dev.kaankaya.instagramtracker

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real Android WebView + shipped JS + synthetic HTTPS origin; never logs into Instagram. */
class ConnectionLabTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private var scenario: ActivityScenario<ConnectionLabActivity>? = null
    private fun gate() = RequestGate(context, "probe_preview_gate")
    private fun diagnostics() = ProbeDiagnostics(context, "probe_preview_diagnostics")
    @Before fun prepare() {
        check(context.packageName.endsWith(".debug"))
        listOf("probe_preview_gate", "probe_preview_diagnostics", "request_gate").forEach { context.getSharedPreferences(it, 0).edit().clear().commit() }
    }
    @After fun cleanup() {
        ConnectionLabMode.releaseDelayedResponse?.countDown()
        scenario?.close(); ConnectionLabMode.delayedResponseReady = null
        ConnectionLabMode.delayedRequestEntered = null; ConnectionLabMode.releaseDelayedResponse = null
        listOf("probe_preview_gate", "probe_preview_diagnostics", "request_gate").forEach { context.getSharedPreferences(it, 0).edit().clear().commit() }
        instrumentation.runOnMainSync { CookieManager.getInstance().removeAllCookies(null) }
    }
    private fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
    private fun await(message: String, condition: () -> Boolean) {
        val until = android.os.SystemClock.elapsedRealtime() + 12000
        while (android.os.SystemClock.elapsedRealtime() < until) { if (condition()) return; Thread.sleep(30) }
        fail(message)
    }
    private fun click(label: String) = scenario!!.onActivity {
        val button = all(it.window.decorView).filterIsInstance<Button>().single { view -> view.text.toString() == label }
        assertTrue("Disabled: $label", button.isEnabled); button.performClick()
    }
    private fun open(status: Int = 200, delayed: Boolean = false): Boolean {
        scenario = ActivityScenario.launch(Intent(context, ConnectionLabActivity::class.java).putExtra("offline_lab", true)
            .putExtra("fixture_status", status).putExtra("fixture_delay", delayed))
        click("Örnek oturumu aç")
        var supported = false; var unsupported = false
        await("Fixture page neither ready nor explicitly unsupported") {
            scenario!!.onActivity {
                val views = all(it.window.decorView)
                supported = views.filterIsInstance<Button>().single { view -> view.text.toString() == "Tek okumayı başlat" }.isEnabled
                unsupported = views.filterIsInstance<TextView>().any { view -> view.text.toString() == context.getString(R.string.lab_update_webview) }
            }
            supported || unsupported
        }
        if (unsupported) { assertEquals(0L, diagnostics().count(ProbeDiagnostics.Event.STARTED)); assertEquals(0L, diagnostics().count(ProbeDiagnostics.Event.PROFILE_RESOURCE)) }
        return supported
    }
    @Test fun realWebViewFetchReturnsCountsOnceAndRecreationCannotRepeat() {
        org.junit.Assume.assumeTrue("System WebView lacks required browser APIs; no profile request was sent", open())
        click("Tek okumayı başlat")
        await("No successful browser result") { diagnostics().count(ProbeDiagnostics.Event.SUCCEEDED) == 1L }
        scenario!!.onActivity { activity ->
            assertTrue(all(activity.window.decorView).filterIsInstance<TextView>().any { it.text.toString().contains("875 takipçi · 412 takip edilen") })
            val button = all(activity.window.decorView).filterIsInstance<Button>().single { it.text.toString() == "Tek okumayı başlat" }
            assertFalse(button.isEnabled); button.performClick()
        }
        assertEquals(1L, diagnostics().count(ProbeDiagnostics.Event.STARTED))
        assertEquals(1L, diagnostics().count(ProbeDiagnostics.Event.PROFILE_RESOURCE))
        scenario!!.recreate()
        assertTrue(gate().nextPrototypeAt() > System.currentTimeMillis())
        assertEquals(1L, diagnostics().count(ProbeDiagnostics.Event.STARTED))
    }
    @Test fun browser429PersistsServerWaitAndNeverRetries() {
        org.junit.Assume.assumeTrue("System WebView lacks required browser APIs; no profile request was sent", open(429))
        val before = System.currentTimeMillis(); click("Tek okumayı başlat")
        await("429 was not recorded") { diagnostics().count(ProbeDiagnostics.Event.LIMITED) == 1L }
        assertTrue(RequestGate(context, "probe_preview_gate").blockedUntil() >= before + 7200000)
        assertTrue(gate().manualOnly())
        assertEquals(1L, diagnostics().count(ProbeDiagnostics.Event.STARTED))
        assertEquals(1L, diagnostics().count(ProbeDiagnostics.Event.PROFILE_RESOURCE))
        assertEquals(0L, diagnostics().count(ProbeDiagnostics.Event.SUCCEEDED))
        scenario!!.recreate()
        scenario!!.onActivity { activity ->
            assertFalse(all(activity.window.decorView).filterIsInstance<Button>().single { it.text.toString() == "Örnek oturumu aç" }.isEnabled)
        }
    }
    @Test fun realModeHonorsExistingRestrictionBeforeAnyPageIsLoaded() {
        val realGate = RequestGate(context); realGate.limited("7200")
        val until = realGate.blockedUntil()
        scenario = ActivityScenario.launch(ConnectionLabActivity::class.java)
        scenario!!.onActivity { activity ->
            assertFalse(all(activity.window.decorView).filterIsInstance<Button>().single { it.text.toString() == "Instagram oturumunu aç" }.isEnabled)
            assertTrue(realGate.prototypeActive())
        }
        assertEquals(until, RequestGate(context).blockedUntil())
        click("Denemeyi bitir")
        await("Prototype hold not released") { !realGate.prototypeActive() }
        assertEquals(until, RequestGate(context).blockedUntil())
    }
    @Test fun closingClearsBrowserCookiesAndDiagnosticsContainNoCookieOrUsername() {
        val supported = open()
        val cookieSet = CountDownLatch(1)
        instrumentation.runOnMainSync {
            CookieManager.getInstance().setCookie(ConnectionLabMode.FIXTURE_ORIGIN, "synthetic_secret=private-cookie; Secure; HttpOnly; Path=/") { cookieSet.countDown() }
        }
        assertTrue(cookieSet.await(5, TimeUnit.SECONDS))
        if (supported) {
            click("Tek okumayı başlat")
            await("No result") { diagnostics().count(ProbeDiagnostics.Event.SUCCEEDED) == 1L }
        }
        click("Denemeyi bitir")
        await("Browser cleanup not completed") { !gate().prototypeActive() }
        assertNull(CookieManager.getInstance().getCookie(ConnectionLabMode.FIXTURE_ORIGIN))
        val stored = context.getSharedPreferences("probe_preview_diagnostics", 0).all.toString()
        assertFalse(stored.contains("private-cookie")); assertFalse(stored.contains("synthetic_secret")); assertFalse(stored.contains("lab.demo"))
    }
    @Test fun closingDuringResponseReleasesLeaseAndRejectsLateResult() {
        val ready = CountDownLatch(1); val entered = CountDownLatch(1); val release = CountDownLatch(1)
        ConnectionLabMode.delayedResponseReady = ready; ConnectionLabMode.delayedRequestEntered = entered; ConnectionLabMode.releaseDelayedResponse = release
        org.junit.Assume.assumeTrue("System WebView lacks required browser APIs; no profile request was sent", open(delayed = true))
        click("Tek okumayı başlat")
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        click("Denemeyi bitir"); release.countDown()
        assertTrue(ready.await(5, TimeUnit.SECONDS))
        await("Hold not released") { !gate().prototypeActive() }
        instrumentation.waitForIdleSync()
        gate().claim().close()
        assertEquals(0L, diagnostics().count(ProbeDiagnostics.Event.SUCCEEDED))
        assertEquals(1L, diagnostics().count(ProbeDiagnostics.Event.CANCELLED))
    }
    @Test fun prototypeHoldAndIntervalPersistAcrossOwnersWithoutResetting429() {
        val g = gate(); g.setPrototypeActive(true)
        assertFalse(RequestGate(context, "probe_preview_gate").reserveAutomatic())
        val now = System.currentTimeMillis(); g.beginPrototype(now)
        try { RequestGate(context, "probe_preview_gate").beginPrototype(now + 16000); fail("Prototype repeat must be blocked") }
        catch (error: ApiFailure) { assertEquals(ApiFailure.Kind.TOO_SOON, error.kind) }
        g.limited("7200", now); val until = g.blockedUntil()
        g.setPrototypeActive(false)
        assertEquals(until, g.blockedUntil()); assertTrue(g.manualOnly()); assertFalse(g.reserveAutomatic(until + 1))
    }
}
