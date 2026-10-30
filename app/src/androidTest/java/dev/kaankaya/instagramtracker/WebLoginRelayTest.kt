package dev.kaankaya.instagramtracker

import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.Call
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Actual origin-bound WebView messages, fetch/XHR and cookies; HTTP is entirely synthetic. */
class WebLoginRelayTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val worker = Executors.newSingleThreadExecutor()
    private var scenario: ActivityScenario<WebRelayFixtureActivity>? = null
    private lateinit var browser: WebView
    private lateinit var relay: WebLoginRelay
    private lateinit var gate: RequestGate
    private var now = 2_000_000_000_000L
    private val calls = AtomicInteger()
    private val errors = java.util.concurrent.CopyOnWriteArrayList<ApiFailure.Kind>()
    private var response: (Request) -> Response = { request -> Response.Builder().request(request).protocol(Protocol.HTTP_2)
        .code(200).message("fixture").addHeader("Set-Cookie", "sessionid=synthetic-login; Path=/; Secure; HttpOnly")
        .addHeader("Set-Cookie", "foreign=never; Domain=evil.invalid; Path=/; Secure")
        .body("{\"data\":{\"caa_login_web\":{\"ig_authenticated\":true}}}".toResponseBody()).build() }
    private val factory = Call.Factory { request ->
        calls.incrementAndGet()
        check(request.url.toString() == WebLoginContract.URL && request.method == "POST")
        check(request.header("Origin") == "https://www.instagram.com" && request.header("Sec-Fetch-Site") == "same-origin")
        check(request.header("Sec-Fetch-Mode") == "cors" && request.header("Sec-Fetch-Dest") == "empty")
        object : Call by PrivateReadTransport.client().newCall(request) {
            override fun execute() = response(request)
        }
    }
    @Before fun setup() {
        check(context.packageName.endsWith(".debug"))
        context.getSharedPreferences("relay_test_gate", 0).edit().clear().commit()
        gate = RequestGate(context, "relay_test_gate") { now }
        clearCookies()
    }
    @After fun cleanup() {
        scenario?.onActivity { if (::relay.isInitialized) relay.close(); if (::browser.isInitialized) browser.destroy() }
        scenario?.close(); worker.shutdownNow(); clearCookies()
        context.getSharedPreferences("relay_test_gate", 0).edit().clear().commit()
    }
    private fun clearCookies() {
        val done=CountDownLatch(1)
        instrumentation.runOnMainSync { CookieManager.getInstance().removeAllCookies { done.countDown() } }
        assertTrue(done.await(5,TimeUnit.SECONDS))
    }
    private fun validBody(doc: String = WebLoginContract.DOCUMENT) = SyntheticWebLogin.body(doc)
    private fun open(frame: Boolean = false, security: Boolean = false) {
        scenario=ActivityScenario.launch(WebRelayFixtureActivity::class.java)
        scenario!!.onActivity { activity ->
            browser=WebView(activity); browser.settings.javaScriptEnabled=true; browser.settings.domStorageEnabled=true
            CookieManager.getInstance().setAcceptCookie(true)
            relay=WebLoginRelay(activity,browser,gate,worker,LoginPageTrace(context),{ error, _ -> errors.add(error.kind) },factory,
                securityScope={url->security && url=="https://www.instagram.com/auth_platform/synthetic/"})
            assertTrue(relay.install())
            browser.webViewClient=object:WebViewClient(){
                override fun shouldInterceptRequest(view:WebView,request:WebResourceRequest):WebResourceResponse {
                    val html=if (request.url.path=="/challenge/frame/") """<script>trackerLoginRelay.postMessage(JSON.stringify({id:'1',body:${JSONObject.quote(validBody())},headers:{}}));</script>"""
                        else if(request.isForMainFrame) "<!doctype html><body>"+(if(frame)"<iframe src='/challenge/frame/'></iframe>" else "")+"</body>"
                        else return LoginBrowserResources.blocked()
                    return WebResourceResponse("text/html","UTF-8",ByteArrayInputStream(html.toByteArray()))
                }
            }
            activity.setContentView(browser);browser.loadUrl(if(security)"https://www.instagram.com/auth_platform/synthetic/" else "https://www.instagram.com/accounts/login/")
        }
        until("document.readyState==='complete' && typeof trackerLoginRelay==='object'", "true")
    }
    @Test fun securityOperationIdentityIsObservedWithoutSendingItsPrivateBodyOrApplyingCookies() {
        open(security=true)
        val body=JSONObject.quote("doc_id=123456789&fb_api_req_friendly_name=PolarisSecurityScreenQuery&variables=private-token&code=private-code")
        js("fetch('/graphql/query/',{method:'POST',body:$body}).catch(()=>window.__securityBlocked=true)")
        until("window.__securityBlocked===true","true")
        val saved=LoginPageTrace.read(context)!!
        val docs=JSONObject(saved).getJSONArray("blockedDocuments")
        assertEquals("123456789",docs.getJSONObject(0).getString("document"))
        assertEquals("PolarisSecurityScreenQuery",docs.getJSONObject(0).getString("operation"))
        assertFalse(saved.contains("private-"));assertEquals(0,calls.get());assertEquals(0L,gate.attempts())
        assertTrue(errors.isEmpty());assertTrue(CookieManager.getInstance().getCookie("https://www.instagram.com/").isNullOrBlank())
    }
    @Test fun alternateGraphqlPathCannotTurnTheInitialLoginDocumentIntoAnotherEndpoint() {
        open()
        js("fetch('/api/graphql/',{method:'POST',body:${JSONObject.quote(validBody())}}).catch(()=>window.__alternateBlocked=true)")
        until("window.__alternateBlocked===true","true")
        assertEquals(0,calls.get());assertEquals(0L,gate.attempts());assertEquals(listOf(ApiFailure.Kind.POLICY),errors.toList())
    }
    private fun js(script: String) = scenario!!.onActivity { browser.evaluateJavascript(script,null) }
    private fun until(script: String, expected: String, timeout: Long = 10000) {
        val value=AtomicReference<String>();val end=android.os.SystemClock.elapsedRealtime()+timeout
        while(android.os.SystemClock.elapsedRealtime()<end){
            scenario!!.onActivity{browser.evaluateJavascript(script){value.set(it)}}
            if(value.get()==expected)return
            Thread.sleep(20)
        }
        fail("Relay condition not reached; expected $expected")
    }
    @Test fun fetchUsesOneFixedLoginPostAndAppliesFirstPartyCookiesBeforeReply() {
        open()
        js("fetch('/api/graphql',{method:'POST',body:${JSONObject.quote(validBody())}}).then(r=>r.json()).then(j=>window.result=j.data.caa_login_web.ig_authenticated).catch(()=>window.result=false)")
        until("window.result","true")
        assertEquals(1,calls.get());assertEquals(1L,gate.attempts())
        assertTrue(CookieManager.getInstance().getCookie("https://www.instagram.com/").contains("sessionid=synthetic-login"))
        assertTrue(CookieManager.getInstance().getCookie("https://evil.invalid/").isNullOrBlank())
        val report=LoginPageTrace.read(context)!!
        assertTrue(report.contains("ALLOW:POST:AUTH"));assertFalse(report.contains("sensitive_string_value"));assertFalse(report.contains("synthetic.account"))
        val facts=JSONObject(report).getJSONObject("authentication")
        assertEquals(200,facts.getInt("status"));assertTrue(facts.getBoolean("sessionCookieReceived"));assertTrue(facts.getBoolean("cookiesApplied"))
        now+=15001
        assertTrue(gate.nextPrototypeAt()<=now);assertTrue(gate.nextWebAuthenticationAt()>now)
        assertEquals(ApiFailure.Kind.TOO_SOON,assertThrows(ApiFailure::class.java){gate.beginWebAuthentication()}.kind)
    }
    @Test fun xhrReceivesStatusAndLoadEventsAndRejectsAnAutomaticSecondPost() {
        open()
        js("var x=new XMLHttpRequest();x.open('POST','/api/graphql');x.onload=()=>window.result=x.status;x.send(${JSONObject.quote(validBody())});")
        until("window.result","200")
        js("fetch('/api/graphql',{method:'POST',body:${JSONObject.quote(validBody())}}).then(()=>window.second='unexpected').catch(()=>window.second='blocked')")
        until("window.second","\"blocked\"");assertEquals(1,calls.get());assertEquals(1L,gate.attempts())
    }
    @Test fun socialDocumentCannotReachTheTransportOrConsumeBudget() {
        open()
        js("fetch('/api/graphql',{method:'POST',body:${JSONObject.quote(validBody("123"))}}).then(()=>window.result='unexpected').catch(()=>window.result='blocked')")
        until("window.result","\"blocked\"");assertEquals(0,calls.get());assertEquals(0L,gate.attempts())
        assertTrue(errors.contains(ApiFailure.Kind.POLICY))
    }
    @Test fun rootRequestErrorIsNotForwardedAsALoginVerdictAndCannotApplyCookies() {
        response={r->Response.Builder().request(r).protocol(Protocol.HTTP_2).code(200).message("fixture")
            .addHeader("Set-Cookie","sessionid=never; Path=/; Secure")
            .body("{\"error\":1357054}".toResponseBody()).build()}
        open();js("fetch('/api/graphql',{method:'POST',body:${JSONObject.quote(validBody())}}).then(()=>window.result='unexpected').catch(()=>window.result='rejected')")
        until("window.result","\"rejected\"")
        assertTrue(errors.contains(ApiFailure.Kind.INVALID_RESPONSE));assertEquals(1,calls.get())
        assertTrue(CookieManager.getInstance().getCookie("https://www.instagram.com/").isNullOrBlank())
        val facts=JSONObject(LoginPageTrace.read(context)!!).getJSONObject("authentication")
        assertEquals(1357054,facts.getJSONArray("errorCodes").getInt(0));assertFalse(facts.getBoolean("cookiesApplied"))
    }
    @Test fun sameOriginSubframeCannotUseTheLoginListener() {
        open(frame=true)
        val end=android.os.SystemClock.elapsedRealtime()+5000
        while(errors.isEmpty()&&android.os.SystemClock.elapsedRealtime()<end)Thread.sleep(20)
        assertTrue(errors.contains(ApiFailure.Kind.POLICY));assertEquals(0,calls.get());assertEquals(0L,gate.attempts())
    }
    @Test fun server429KeepsPersistentPauseAndDoesNotApplyCookies() {
        response={r->Response.Builder().request(r).protocol(Protocol.HTTP_2).code(429).message("fixture")
            .addHeader("Retry-After","900").addHeader("Set-Cookie","sessionid=never; Path=/; Secure")
            .body("{}".toResponseBody()).build()}
        open();js("fetch('/api/graphql',{method:'POST',body:${JSONObject.quote(validBody())}}).catch(()=>window.result='blocked')")
        until("window.result","\"blocked\"")
        assertEquals(1,calls.get());assertTrue(gate.blockedUntil()>now)
        assertEquals(gate.blockedUntil(),RequestGate(context,"relay_test_gate"){now}.blockedUntil())
        assertTrue(CookieManager.getInstance().getCookie("https://www.instagram.com/").isNullOrBlank())
    }
    @Test fun cancellingActiveRequestReleasesTheLeaseAndCannotCommitLateCookies() {
        val entered=CountDownLatch(1);val finish=CountDownLatch(1)
        response={r->entered.countDown();finish.await(10,TimeUnit.SECONDS)
            Response.Builder().request(r).protocol(Protocol.HTTP_2).code(200).message("late")
                .addHeader("Set-Cookie","sessionid=never; Path=/; Secure").body("{}".toResponseBody()).build()}
        open();js("fetch('/api/graphql',{method:'POST',body:${JSONObject.quote(validBody())}}).catch(()=>window.result='cancelled')")
        assertTrue(entered.await(5,TimeUnit.SECONDS))
        scenario!!.onActivity{relay.cancel()};finish.countDown()
        until("window.result","\"cancelled\"")
        gate.claim().close()
        assertTrue(CookieManager.getInstance().getCookie("https://www.instagram.com/").isNullOrBlank())
        assertEquals(1,calls.get())
    }
}
