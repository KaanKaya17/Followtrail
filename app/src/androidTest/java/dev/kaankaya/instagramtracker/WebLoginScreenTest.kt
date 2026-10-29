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
    private fun open(status: Int = 200, bootstrap: Boolean = false, offline: Boolean = true, auth: Boolean = false, captcha: Boolean = false, rejected: Boolean = false) {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)
            .putExtra("offline_web_login", offline).putExtra("web_login_status", status).putExtra("web_bootstrap_fixture", bootstrap)
            .putExtra("web_auth_fixture", auth).putExtra("web_captcha_fixture", captcha).putExtra("web_rejected_fixture", rejected))
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
    private fun submitSyntheticLogin() = scenario!!.onActivity { a ->
        val body=org.json.JSONObject.quote(SyntheticWebLogin.body())
        all(a.window.decorView).filterIsInstance<WebView>().single().evaluateJavascript(
            "fetch('/api/graphql',{method:'POST',body:$body}).then(r=>r.json()).then(j=>{if(j.data.caa_login_web.ig_authenticated)location.href='/accounts/onetap/';}).catch(()=>{})",null)
    }
    @Test fun successfulLoginAutomaticallyReadsOnceAndLinksWithoutPressingVerify() {
        open(auth=true);submitSyntheticLogin()
        until(23000) { SessionVault(context).read()!=null }
        assertEquals("123",SessionVault(context).read()!!.profile.userId)
        assertEquals(2L,RequestGate(context).attempts())
        assertNotNull(SessionVault(context).read()!!.session.webBinding)
        Thread.sleep(400);assertEquals(2L,RequestGate(context).attempts())
    }
    @Test fun captchaIsShownAsUserActionAndExternalResourcesStayBlockedUntilConsent() {
        open(auth=true,captcha=true);submitSyntheticLogin()
        until {a->all(a.window.decorView).filterIsInstance<Button>().any {it.text==a.getString(R.string.web_login_show_challenge)&&it.visibility==View.VISIBLE}}
        scenario!!.onActivity {a->assertTrue(all(a.window.decorView).filterIsInstance<TextView>().any {it.text==a.getString(R.string.web_login_captcha)})}
        assertEquals(1L,RequestGate(context).attempts());assertNull(SessionVault(context).read())
        fun resource(method: String,url: String): Int {
            var status=0
            scenario!!.onActivity {a->val browser=all(a.window.decorView).filterIsInstance<WebView>().single()
                val request=object: android.webkit.WebResourceRequest {
                    override fun getUrl()=android.net.Uri.parse(url)
                    override fun isForMainFrame()=false
                    override fun isRedirect()=false
                    override fun hasGesture()=false
                    override fun getMethod()=method
                    override fun getRequestHeaders()=emptyMap<String,String>()
                }
                status=browser.webViewClient.shouldInterceptRequest(browser,request)!!.statusCode
            };return status
        }
        val frame="https://www.fbsbx.com/captcha/recaptcha/iframe/"
        assertEquals(403,resource("GET",frame))
        scenario!!.onActivity {a->all(a.window.decorView).filterIsInstance<Button>().single {it.text==a.getString(R.string.web_login_show_challenge)}.performClick()}
        instrumentation.runOnMainSync { WindowInspector.getGlobalWindowViews().flatMap(::all).filterIsInstance<Button>().single {it.id==android.R.id.button1}.performClick() }
        until {a->all(a.window.decorView).filterIsInstance<WebView>().single().url?.contains("challenge/")==false}
        assertEquals(200,resource("GET",frame))
        assertEquals(403,resource("POST","https://www.instagram.com/api/v1/web/friendships/123/follow/"))
        assertNull(SessionVault(context).read());assertEquals(1L,RequestGate(context).attempts())
    }
    @Test fun automaticConnectionWaitsWhileTheLoginScreenIsInBackground() {
        open(auth=true);submitSyntheticLogin()
        until { org.json.JSONObject(LoginPageTrace.read(context)!!).optJSONObject("authentication")?.optBoolean("cookiesApplied")==true }
        scenario!!.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        Thread.sleep(16000)
        assertEquals(1L,RequestGate(context).attempts());assertNull(SessionVault(context).read())
        scenario!!.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        until {SessionVault(context).read()!=null}
        assertEquals(2L,RequestGate(context).attempts())
    }
    @Test fun rejectedLoginClosesTheFormAndShowsOneVerdictWithoutAllowingMisleadingRetries() {
        open(auth=true,rejected=true);submitSyntheticLogin()
        until { a -> all(a.window.decorView).none {it is WebView} &&
            WindowInspector.getGlobalWindowViews().flatMap(::all).filterIsInstance<TextView>().any {it.text==a.getString(R.string.web_login_not_accepted)} }
        assertEquals(1L,RequestGate(context).attempts());assertNull(SessionVault(context).read())
        val facts=org.json.JSONObject(LoginPageTrace.read(context)!!).getJSONObject("authentication")
        assertFalse(facts.getBoolean("authenticated"));assertFalse(facts.getBoolean("captchaNeeded"))
        assertEquals(1348009,facts.getJSONArray("errorCodes").getInt(0))
        assertTrue(RequestGate(context).nextWebAuthenticationAt()>System.currentTimeMillis())
    }
    @Test fun authenticationReadPauseReenablesVerificationWithoutReadingOrErasingFailure() {
        open()
        val gate=RequestGate(context);gate.beginWebAuthentication()
        scenario!!.onActivity { a -> all(a.window.decorView).filterIsInstance<WebView>().single()
            .loadUrl("https://www.instagram.com/accounts/login/?fixture=gate-wake") }
        until { a -> all(a.window.decorView).filterIsInstance<Button>().any { it.text==a.getString(R.string.web_connection_verify) && !it.isEnabled } }
        scenario!!.onActivity { a -> all(a.window.decorView).filterIsInstance<WebView>().single()
            .evaluateJavascript("fetch('/api/graphql',{method:'POST',body:'unapproved-local'}).catch(()=>{})",null) }
        until { a -> all(a.window.decorView).filterIsInstance<TextView>().any { it.text==a.getString(R.string.web_relay_blocked) } }
        until(22000) { a -> all(a.window.decorView).filterIsInstance<Button>().any { it.text==a.getString(R.string.web_connection_verify) && it.isEnabled } }
        scenario!!.onActivity { a -> assertTrue(all(a.window.decorView).filterIsInstance<TextView>().any { it.text==a.getString(R.string.web_relay_blocked) }) }
        assertEquals(1L,gate.attempts());assertNull(SessionVault(context).read())
        assertTrue(gate.nextWebAuthenticationAt()>System.currentTimeMillis())
    }
    @Test fun desktopLoginLoadsExactBootstrapReadsAndDetectsModernFieldsWithoutVerifyingAnAccount() {
        open(bootstrap = true)
        until(20000) { LoginPageTrace.read(context)?.let { org.json.JSONObject(it).optBoolean("formVisible", false) } == true }
        val saved = LoginPageTrace.read(context)!!
        assertTrue(saved, saved.contains("ALLOW:GET:BOOTSTRAP")); assertTrue(saved, saved.contains("ALLOW:POST:BOOTSTRAP"))
        assertFalse(saved.contains("synthetic-route-fields"))
        scenario!!.onActivity { a ->
            val settings = all(a.window.decorView).filterIsInstance<WebView>().single().settings
            assertFalse(settings.userAgentString.contains("Android")); assertFalse(settings.userAgentString.contains("Mobile"))
            assertTrue(settings.useWideViewPort); assertTrue(settings.loadWithOverviewMode)
            assertTrue(settings.builtInZoomControls); assertFalse(settings.displayZoomControls)
        }
        assertEquals(0L, RequestGate(context).attempts()); assertNull(SessionVault(context).read())
    }
    /** Explicit opt-in, isolated debug package only. Never enters credentials or presses verification. */
    @Test fun publicLoginFormSmokeRequiresExplicitOptIn() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("live_login_page") == "true")
        open(offline = false)
        val state = java.util.concurrent.atomic.AtomicReference<String>()
        // Public entry screen only. Never press the form's submit button or inspect field values.
        val script = """(function(){
            if(Array.from(document.querySelectorAll('input[name=username],input[name=email],input[type=password]')).some(n=>n.getClientRects().length))return 'form';
            if(window.__trackerSmokeEntryClicked)return 'waiting';
            var names=['Log in','Giriş yap'];
            var nodes=Array.from(document.querySelectorAll('a,button,[role=button],span')).filter(n=>n.getClientRects().length&&names.includes(n.textContent.trim())&&!Array.from(n.children).some(c=>c.textContent.trim()===n.textContent.trim()));
            if(nodes.length===1){window.__trackerSmokeEntryClicked=true;nodes[0].click();}
            return 'waiting';
        })()"""
        until(25000) { a ->
            all(a.window.decorView).filterIsInstance<WebView>().single().evaluateJavascript(script) { state.set(it) }
            state.get() == "\"form\""
        }
        assertEquals(0L, RequestGate(context).attempts()); assertNull(SessionVault(context).read())
    }
    @Test fun pageDiagnosticsExplainBlockedResourcesWithoutStoringRequestContentsOrSendingAProfileRead() {
        open()
        scenario!!.onActivity { a ->
            all(a.window.decorView).filterIsInstance<WebView>().single().evaluateJavascript(
                "fetch('/api/graphql',{method:'POST',body:'synthetic-secret-body'}).catch(function(){})", null)
        }
        until(20000) { LoginPageTrace.read(context)?.let { org.json.JSONObject(it).optBoolean("formVisible", false) } == true }
        scenario!!.onActivity { a -> all(a.window.decorView).filterIsInstance<Button>().single { it.text == a.getString(R.string.page_trace_open) }.performClick() }
        val saved = LoginPageTrace.read(context)!!
        assertTrue(saved, saved.contains("BLOCK:POST:GRAPHQL")); assertFalse(saved.contains("synthetic-secret-body"))
        assertEquals(0L, RequestGate(context).attempts()); assertNull(SessionVault(context).read())
        assertTrue(WindowInspector.getGlobalWindowViews().flatMap(::all).filterIsInstance<TextView>().any { it.text.toString().contains("LP01") })
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
    @Test fun loginBrowserBlocksSocialPostsAndNonLoginNavigationWithoutAnAccountRequest() {
        open(); cookies()
        scenario!!.onActivity { a ->
            val browser=all(a.window.decorView).filterIsInstance<WebView>().single()
            browser.evaluateJavascript("fetch('/api/v1/web/friendships/123/follow/',{method:'POST'}).then(r=>window.__policyStatus=r.status)",null)
        }
        val status=java.util.concurrent.atomic.AtomicReference<String>()
        until { a ->
            all(a.window.decorView).filterIsInstance<WebView>().single().evaluateJavascript("window.__policyStatus") { status.set(it) }
            status.get()=="403"
        }
        scenario!!.onActivity { a ->
            all(a.window.decorView).filterIsInstance<WebView>().single().evaluateJavascript("location.href='/direct/inbox/'",null)
        }
        until { a -> all(a.window.decorView).filterIsInstance<TextView>().any { it.text==a.getString(R.string.security_login_scope) } }
        assertEquals(0L,RequestGate(context).attempts());assertNull(SessionVault(context).read())
        verify()
        until { a -> all(a.window.decorView).filterIsInstance<TextView>().any { it.text=="@web.demo" } }
        assertEquals(1L,RequestGate(context).attempts())
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
