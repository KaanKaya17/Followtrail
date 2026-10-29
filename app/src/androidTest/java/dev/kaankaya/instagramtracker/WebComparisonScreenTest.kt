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
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Actual two-click MainActivity, real browser fetch and staging; debug fixtures cannot reach Instagram. */
class WebComparisonScreenTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private var scenario: ActivityScenario<MainActivity>?=null
    private fun all(view: View): List<View> = listOf(view)+if(view is ViewGroup)(0 until view.childCount).flatMap {all(view.getChildAt(it))}else emptyList()
    @Before fun setup() {check(context.packageName.endsWith(".debug"));cleanup()}
    @After fun cleanup() {
        scenario?.close();scenario=null;SessionVault(context).clear();WebSessions(context).clear();MobileSessions(context).clear()
        WebConnectionJournal(context).clear();context.getSharedPreferences("request_gate",0).edit().clear().commit()
        val latch=CountDownLatch(1);instrumentation.runOnMainSync {CookieManager.getInstance().removeAllCookies {latch.countDown()}}
        assertTrue(latch.await(5,TimeUnit.SECONDS))
    }
    private fun until(timeout: Long=10000, test: (MainActivity)->Boolean) {
        val deadline=android.os.SystemClock.elapsedRealtime()+timeout
        while(android.os.SystemClock.elapsedRealtime()<deadline) {
            var done=false;scenario!!.onActivity {done=test(it)};if(done)return;Thread.sleep(40)
        }
        fail("Comparison UI did not reach the expected state")
    }
    private fun click(id: Int) = scenario!!.onActivity {a->
        all(a.window.decorView).single {it.isClickable && ((it is TextView && it.text.toString()==a.getString(id))||it.contentDescription?.toString()==a.getString(id))}.performClick()
    }
    private fun choice(id: Int) = scenario!!.onActivity {a->
        val list=WindowInspector.getGlobalWindowViews().flatMap(::all).filterIsInstance<ListView>().first {v->(0 until v.adapter.count).any {v.adapter.getItem(it).toString()==a.getString(id)}}
        val index=(0 until list.adapter.count).first {list.adapter.getItem(it).toString()==a.getString(id)}
        list.performItemClick(list.getChildAt(index),index,list.adapter.getItemId(index))
    }
    private fun open(browserStatus: Int=200,nativeStatus: Int=200) {
        scenario=ActivityScenario.launch(Intent(context,MainActivity::class.java).putExtra("offline_web_login",true)
            .putExtra("web_browser_status",browserStatus).putExtra("web_login_status",nativeStatus))
        until {a->all(a.window.decorView).any {it.contentDescription==a.getString(R.string.ui_options)}}
        click(R.string.ui_options);choice(R.string.ui_advanced);choice(R.string.web_compare_menu)
        until(25000) {a->all(a.window.decorView).filterIsInstance<Button>().any {it.text==a.getString(R.string.web_compare_browser)&&it.isEnabled}}
        val latch=CountDownLatch(3)
        scenario!!.onActivity {
            listOf("sessionid=synthetic-session; Path=/; Secure; HttpOnly","ds_user_id=123; Path=/; Secure","csrftoken=csrf; Path=/; Secure").forEach {
                CookieManager.getInstance().setCookie("https://www.instagram.com/",it){latch.countDown()}
            }
        };assertTrue(latch.await(5,TimeUnit.SECONDS))
    }
    private fun first() {
        click(R.string.web_compare_browser)
        until {WebConnectionJournal(context).read().firstOrNull()?.outcome?.let {it!=WebConnectionJournal.Outcome.STARTED}==true ||
            WindowInspector.getGlobalWindowViews().flatMap(::all).filterIsInstance<TextView>().any {it.text==context.getString(R.string.web_connection_failed)}}
        assertEquals(WebConnectionJournal(context).read().toString(),WebConnectionJournal.Outcome.SUCCESS,WebConnectionJournal(context).read().firstOrNull()?.outcome)
        assertEquals(1L,RequestGate(context).attempts());assertNull(SessionVault(context).read());assertFalse(WebSessions(context).exists())
        assertTrue(RequestGate(context).manualOnly());assertTrue(RequestGate(context).nextPrototypeAt()>System.currentTimeMillis()+10*60*1000)
    }
    private fun screenshot(name: String) {
        scenario!!.onActivity {it.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)}
        instrumentation.waitForIdleSync()
        Thread.sleep(500) // Let the compositor apply the test-only flag change and finish dialog animation.
        instrumentation.uiAutomation.takeScreenshot()?.let {bitmap->
            context.openFileOutput("web-comparison-$name.png",0).use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }
    }
    @Test fun twoExplicitReadsUseSameProfileAndCommitOnlyAfterNativeSuccess() {
        open()
        val finished=org.json.JSONObject(LoginPageTrace.read(context)!!).getInt("documentsFinished")
        // Wait for the new document completion, not the old enabled button during navigation.
        scenario!!.onActivity {a->all(a.window.decorView).filterIsInstance<WebView>().single().loadUrl("https://www.instagram.com/accounts/onetap/")}
        until(25000) {a->org.json.JSONObject(LoginPageTrace.read(context)!!).getInt("documentsFinished")>finished &&
            all(a.window.decorView).filterIsInstance<WebView>().single().url=="https://www.instagram.com/accounts/onetap/" &&
            all(a.window.decorView).filterIsInstance<Button>().any {it.text==a.getString(R.string.web_compare_browser)&&it.isEnabled}}
        first()
        screenshot("browser")
        Thread.sleep(400);assertEquals(1L,RequestGate(context).attempts())
        until(20000) {a->all(a.window.decorView).filterIsInstance<Button>().any {it.text==a.getString(R.string.web_compare_native)&&it.isEnabled}}
        click(R.string.web_compare_native)
        until {WebConnectionJournal(context).read().lastOrNull()?.let {it.step==WebConnectionJournal.Step.NATIVE && it.outcome==WebConnectionJournal.Outcome.SUCCESS}==true}
        assertEquals(2L,RequestGate(context).attempts());assertEquals("123",SessionVault(context).read()!!.profile.userId)
        assertEquals(listOf(WebConnectionJournal.Outcome.SUCCESS,WebConnectionJournal.Outcome.SUCCESS),WebConnectionJournal(context).read().map {it.outcome})
        assertEquals(listOf(200,200),WebConnectionJournal(context).read().map {it.http})
        assertTrue(RequestGate(context).nextPrototypeAt()>System.currentTimeMillis()+10*60*1000)
        click(R.string.ui_options);choice(R.string.ui_request_information);screenshot("report")
    }
    @Test fun browser429StopsBeforeNativeAndPersistsUnambiguousDiagnostic() {
        open(browserStatus=429);click(R.string.web_compare_browser)
        until {a->all(a.window.decorView).none {it is WebView}}
        assertEquals(1L,RequestGate(context).attempts());assertTrue(RequestGate(context).blockedUntil()>System.currentTimeMillis())
        assertNull(SessionVault(context).read());assertFalse(WebSessions(context).exists())
        val report=WebConnectionJournal(context).read();assertEquals(1,report.size);assertEquals(429,report.single().http)
        assertEquals(WebConnectionJournal.Outcome.LIMITED,report.single().outcome)
        assertEquals(1,context.getSharedPreferences("request_gate",0).getInt("limit_streak",0))
        assertEquals(429,RequestGate(context).lastHttpStatus())
        scenario!!.recreate();assertEquals(report,WebConnectionJournal(context).read())
    }
    @Test fun browserSuccessNative429DoesNotClaimWorkingConnection() {
        open(nativeStatus=429);first()
        until(20000) {a->all(a.window.decorView).filterIsInstance<Button>().any {it.text==a.getString(R.string.web_compare_native)&&it.isEnabled}}
        click(R.string.web_compare_native);until {WebConnectionJournal(context).read().size==2 && WebConnectionJournal(context).read()[1].outcome!=WebConnectionJournal.Outcome.STARTED}
        assertEquals(listOf(WebConnectionJournal.Outcome.SUCCESS,WebConnectionJournal.Outcome.LIMITED),WebConnectionJournal(context).read().map {it.outcome})
        assertEquals(listOf(200,429),WebConnectionJournal(context).read().map {it.http})
        assertNull(SessionVault(context).read());assertNull(WebSessions(context).pending());assertEquals(2L,RequestGate(context).attempts())
    }
    @Test fun cancellingAfterBrowserSuccessCannotCommitOrStartNative() {
        open();first();click(R.string.ui_cancel)
        until {a->all(a.window.decorView).none {it is WebView}}
        assertNull(SessionVault(context).read());assertEquals(1L,RequestGate(context).attempts())
        assertEquals(1,WebConnectionJournal(context).read().size)
    }
    @Test fun backgroundingAfterBrowserSuccessCancelsTheContinuation() {
        open();first();scenario!!.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        assertNull(SessionVault(context).read());assertEquals(1L,RequestGate(context).attempts())
        scenario!!.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        until {a->all(a.window.decorView).none {it is WebView}}
        assertEquals(1L,RequestGate(context).attempts());assertEquals(1,WebConnectionJournal(context).read().size)
    }
    @Test fun unrelatedPage429DuringBrowserReadDoesNotBecomeAProfileResponse() {
        open()
        scenario!!.onActivity {activity->
            all(activity.window.decorView).filterIsInstance<Button>().single {it.text==activity.getString(R.string.web_compare_browser)}.performClick()
            val browser=all(activity.window.decorView).filterIsInstance<WebView>().single()
            val request=object: android.webkit.WebResourceRequest {
                override fun getUrl()=android.net.Uri.parse("https://www.instagram.com/api/v1/other-page-resource/")
                override fun isForMainFrame()=false
                override fun isRedirect()=false
                override fun hasGesture()=false
                override fun getMethod()="GET"
                override fun getRequestHeaders()=emptyMap<String,String>()
            }
            browser.webViewClient.onReceivedHttpError(browser,request,android.webkit.WebResourceResponse("application/json","UTF-8",429,"fixture",emptyMap(),java.io.ByteArrayInputStream("{}".toByteArray())))
        }
        until {a->all(a.window.decorView).none {it is WebView}}
        val report=WebConnectionJournal(context).read()
        assertEquals(WebConnectionJournal.Outcome.LIMITED,report.single {it.step==WebConnectionJournal.Step.PAGE}.outcome)
        assertEquals(429,report.single {it.step==WebConnectionJournal.Step.PAGE}.http)
        assertEquals(WebConnectionJournal.Outcome.CANCELLED,report.single {it.step==WebConnectionJournal.Step.BROWSER}.outcome)
        assertNull(report.single {it.step==WebConnectionJournal.Step.BROWSER}.http)
        assertTrue(report.none {it.step==WebConnectionJournal.Step.NATIVE})
        assertNull(RequestGate(context).lastHttpStatus());assertNull(SessionVault(context).read())
    }
}
