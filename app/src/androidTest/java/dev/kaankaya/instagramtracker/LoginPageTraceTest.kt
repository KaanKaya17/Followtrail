package dev.kaankaya.instagramtracker

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class LoginPageTraceTest {
    @Test fun exchangeFailuresRetainOnlyFiniteFactsAndNeverExceptionMessagesOrCauses() {
        val trace = LoginPageTrace(context) { 1234 }
        trace.exchangeFailure("AUTH", ApiFailure.Kind.CONNECTION, java.io.IOException("https://private.invalid/?password=secret"))
        trace.exchangeFailure("CODE_SUBMISSION", ApiFailure.Kind.CONNECTION, WebTransportFailure(WebTransportFailure.Reason.NETWORK, 6))
        val raw = LoginPageTrace.read(context)!!
        assertFalse(raw.contains("private.invalid")); assertFalse(raw.contains("password")); assertFalse(raw.contains("secret"))
        val facts = report().getJSONObject("exchangeFailures")
        assertEquals("CONNECTION", facts.getJSONObject("AUTH").getString("kind"))
        assertTrue(facts.getJSONObject("AUTH").isNull("transport"))
        assertEquals(6, facts.getJSONObject("CODE_SUBMISSION").getInt("networkCode"))
        trace.close()
    }
    @org.junit.Test fun navigationFactsContainOnlyFiniteRouteAndAuthorityLabels() {
        val trace=LoginPageTrace(context) {1234}
        trace.started(WebLoginChallenge.Companion.Route.BLANK)
        trace.finished(WebLoginChallenge.Companion.Route.AUTH_PLATFORM)
        trace.navigated(true,true,WebLoginChallenge.Companion.Route.AUTH_PLATFORM)
        trace.navigated(false,false,WebLoginChallenge.Companion.Route.OTHER)
        trace.flush()
        val nav=report().getJSONObject("navigation")
        assertEquals(1,nav.getInt("STARTED:BLANK"));assertEquals(1,nav.getInt("FINISHED:AUTH_PLATFORM"))
        assertEquals(1,nav.getInt("ALLOW:HTTP_REDIRECT:AUTH_PLATFORM"));assertEquals(1,nav.getInt("BLOCK:PAGE:OTHER"))
        trace.close()
    }
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun setup() { check(context.packageName.endsWith(".debug")); clear() }
    @After fun clear() { context.getSharedPreferences("login_page_trace", 0).edit().clear().commit() }
    private fun report() = JSONObject(LoginPageTrace.read(context)!!)
    @Test fun metadataHasNoAddressIdentityOrSecretAndDistinguishesLocalBlocks() {
        val trace = LoginPageTrace(context) { 1234 }
        trace.started()
        trace.resource("GET", LoginBrowserPolicy.resourceDecision("GET", "https://www.instagram.com/api/v1/users/987654321/info/?sessionid=secret-query"))
        trace.resource("POST", LoginBrowserPolicy.resourceDecision("POST", "https://www.instagram.com/api/graphql?secret=private"))
        trace.http(403, false, false); trace.finished(); trace.form(false)
        val saved = LoginPageTrace.read(context)!!
        for (privateValue in listOf("instagram.com", "987654321", "secret", "private", "sessionid", "https")) assertFalse(saved, saved.contains(privateValue))
        assertEquals(1, report().getJSONObject("resources").getInt("BLOCK:POST:GRAPHQL"))
        assertEquals(1, report().getJSONObject("http").getInt("LOCAL_BLOCK:PART:403"))
        assertEquals(1, report().getInt("documentsFinished")); assertFalse(report().getBoolean("formVisible"))
        trace.close()
    }
    @Test fun closingAnOlderPagePreventsLateEventsFromOverwritingANewerReport() {
        val first = LoginPageTrace(context) { 1000 }; first.started(); first.close()
        val second = LoginPageTrace(context) { 2000 }; second.started(); second.form(true)
        val expected = LoginPageTrace.read(context)
        first.resource("GET", LoginBrowserPolicy.resourceDecision("GET", "https://www.instagram.com/accounts/login/"))
        first.error(-2, true); first.form(false); first.finished(); first.flush(); first.close()
        assertEquals(expected, LoginPageTrace.read(context)); assertEquals(2000L, report().getLong("startedAt"))
        second.close()
    }
    @Test fun countersAreBoundedAndLoadCompletionDoesNotInventAnHttpResult() {
        val trace = LoginPageTrace(context) { 1234 }
        val decision = LoginBrowserPolicy.resourceDecision("GET", "https://static.cdninstagram.com/static/login.js")
        repeat(1500) { trace.resource("GET", decision) }
        trace.finished()
        assertEquals(999, report().getJSONObject("resources").getInt("ALLOW:GET:CDN"))
        assertEquals(0, report().getJSONObject("http").length()); assertTrue(report().isNull("formVisible"))
        for (status in 400..499) trace.http(status, false, true)
        trace.error(-2, true); trace.error(-7, false); trace.form(null)
        assertEquals(32, report().getJSONObject("http").length())
        assertEquals(1, report().getJSONObject("errors").getInt("PAGE:-2"))
        assertEquals(1, report().getJSONObject("errors").getInt("PART:-7"))
        assertTrue(LoginPageTrace.read(context)!!.length <= 8192)
        trace.close()
    }
}
