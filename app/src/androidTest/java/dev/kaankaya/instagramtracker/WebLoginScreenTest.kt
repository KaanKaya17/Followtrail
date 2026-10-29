package dev.kaankaya.instagramtracker

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real MainActivity + WebView cookie capture; all page/API responses are debug-only fixtures. */
@SdkSuppress(minSdkVersion = 29)
class WebLoginScreenTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    private fun all(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
    @Before fun setup() { check(context.packageName.endsWith(".debug")); cleanup() }
    @After fun cleanup() {
        scenario?.close(); scenario = null
        SessionVault(context).clear(); WebSessions(context).clear(); MobileSessions(context).clear()
        context.getSharedPreferences("request_gate", 0).edit().clear().commit()
        val cleared = CountDownLatch(1)
        instrumentation.runOnMainSync { CookieManager.getInstance().removeAllCookies { cleared.countDown() } }
        assertTrue(cleared.await(5, TimeUnit.SECONDS))
    }
    private fun until(timeoutMs: Long = 8000, predicate: (MainActivity) -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMs
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            var done = false; scenario!!.onActivity { done = predicate(it) }
            if (done) return
            Thread.sleep(20)
        }
        var visible = ""
        scenario!!.onActivity { visible = WindowInspector.getGlobalWindowViews().flatMap(::all)
            .filterIsInstance<TextView>().joinToString(" | ") { it.text.toString() } }
        fail("Offline UI condition not reached: $visible")
    }
    private fun open(status: Int = 200) {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)
            .putExtra("offline_web_login", true).putExtra("web_login_status", status))
        until { a -> all(a.window.decorView).filterIsInstance<Button>().any { it.text == a.getString(R.string.ui_connect_account) } }
        scenario!!.onActivity { a -> all(a.window.decorView).filterIsInstance<Button>().single { it.text == a.getString(R.string.ui_connect_account) }.performClick() }
        instrumentation.waitForIdleSync()
        scenario!!.onActivity { a ->
            val list = WindowInspector.getGlobalWindowViews().flatMap(::all).filterIsInstance<ListView>()
                .first { v -> (0 until v.adapter.count).any { v.adapter.getItem(it).toString() == a.getString(R.string.web_connection_menu) } }
            val index = (0 until list.adapter.count).first { list.adapter.getItem(it).toString() == a.getString(R.string.web_connection_menu) }
            list.performItemClick(list.getChildAt(index), index, list.adapter.getItemId(index))
        }
        // A cold WebView start after APK replacement can exceed the normal native-view wait.
        until(20000) { a -> all(a.window.decorView).filterIsInstance<Button>().any { it.text == a.getString(R.string.web_connection_verify) && it.isEnabled } }
    }
    private fun cookies() {
        val ready = CountDownLatch(3)
        scenario!!.onActivity {
            listOf("sessionid=synthetic-session; Path=/; Secure; HttpOnly", "ds_user_id=123; Path=/; Secure", "csrftoken=csrf; Path=/; Secure").forEach {
                CookieManager.getInstance().setCookie("https://www.instagram.com/", it) { ready.countDown() }
            }
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS))
    }
    private fun verify() = scenario!!.onActivity { a ->
        all(a.window.decorView).filterIsInstance<Button>().single { it.text == a.getString(R.string.web_connection_verify) }.performClick()
    }
    @Test fun realCookieHandoffLinksEncryptedSessionAndReopensWithoutAnotherRequest() {
        open(); cookies(); verify()
        until { a -> all(a.window.decorView).filterIsInstance<TextView>().any { it.text == "@web.demo" } }
        val saved = SessionVault(context).read()!!
        assertNotNull(saved.session.webBinding); assertNull(saved.session.mobileBinding); assertEquals("", saved.session.cookie)
        assertEquals("synthetic-session", WebSessions(context).read(saved.session).cookies["sessionid"])
        assertEquals(1L, RequestGate(context).attempts())
        scenario!!.recreate()
        until { a -> all(a.window.decorView).filterIsInstance<TextView>().any { it.text == "@web.demo" } }
        assertEquals(saved, SessionVault(context).read()); assertEquals(1L, RequestGate(context).attempts())
        until { CookieManager.getInstance().getCookie("https://www.instagram.com/").isNullOrBlank() }
    }
    @Test fun cancellationCleansTheBrowserWithoutSavingOrReadingAnAccount() {
        open(); cookies()
        scenario!!.onActivity { a -> all(a.window.decorView).filterIsInstance<Button>().single { it.text == a.getString(R.string.ui_cancel) }.performClick() }
        until { a -> all(a.window.decorView).none { it is WebView } }
        assertNull(SessionVault(context).read()); assertFalse(WebSessions(context).exists()); assertEquals(0L, RequestGate(context).attempts())
        until { CookieManager.getInstance().getCookie("https://www.instagram.com/").isNullOrBlank() }
    }
    private fun failureDialog(expected: (MainActivity) -> String) {
        until { a -> all(a.window.decorView).none { it is WebView } &&
            WindowInspector.getGlobalWindowViews().flatMap(::all).filterIsInstance<TextView>().any {
                it.text.toString().contains(expected(a)) && it.text.toString().contains(a.getString(R.string.web_connection_failed_scope)) } }
    }
    @Test fun api429ClosesBrowserShowsFailureAndKeepsCooldownAfterRecreation() {
        open(429); cookies(); verify()
        failureDialog { it.getString(R.string.ui_last_http_response, "429") }
        val blocked = RequestGate(context).blockedUntil()
        assertTrue(blocked > System.currentTimeMillis())
        assertNull(SessionVault(context).read()); assertNull(WebSessions(context).pending())
        assertEquals(1L, RequestGate(context).attempts())
        until { CookieManager.getInstance().getCookie("https://www.instagram.com/").isNullOrBlank() }
        scenario!!.onActivity { a ->
            val info = WindowInspector.getGlobalWindowViews().flatMap(::all).filterIsInstance<Button>()
                .single { it.text.toString().equals(a.getString(R.string.ui_request_information), true) }
            info.performClick()
        }
        until { a -> WindowInspector.getGlobalWindowViews().flatMap(::all).filterIsInstance<TextView>().any {
            it.text.toString().contains(a.getString(R.string.web_compare_last_counted_http, "429")) } }
        assertEquals(1L, RequestGate(context).attempts())
        scenario!!.recreate()
        until { a -> all(a.window.decorView).none { it is WebView } }
        assertEquals(blocked, RequestGate(context).blockedUntil()); assertEquals(1L, RequestGate(context).attempts())
    }
    @Test fun expiredOlderCooldownDoesNotHideCurrentVerificationError() {
        open(403); cookies()
        RequestGate(context).limited(null, System.currentTimeMillis() - 8 * 60 * 60 * 1000L)
        verify()
        failureDialog { ApiFailure(ApiFailure.Kind.VERIFICATION).userMessage(it) }
        assertTrue(RequestGate(context).requiresConnection())
        assertNull(SessionVault(context).read()); assertNull(WebSessions(context).pending())
        assertEquals(1L, RequestGate(context).attempts())
        until { CookieManager.getInstance().getCookie("https://www.instagram.com/").isNullOrBlank() }
    }
}
