package dev.kaankaya.instagramtracker

import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.atomic.AtomicLong

class WebComparisonBoundaryTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val clock=AtomicLong(1000000)
    private val gate=RequestGate(context,"compare_boundary",clock::get)
    private val sessions=WebSessions(context,"comparetest")
    private val vault=SessionVault(context,"comparetestaccount")
    private val journal=WebConnectionJournal(context)
    private fun state()=WebSessionCodec.fromHeader("ds_user_id=123; sessionid=SYNTHETIC-SECRET; csrftoken=csrf","Synthetic WebView")
    @Before fun setup() {check(context.packageName.endsWith(".debug"));cleanup();clock.set(1000000)}
    @After fun cleanup() {sessions.clear();vault.clear();journal.clear();context.getSharedPreferences("compare_boundary",0).edit().clear().commit()}
    private fun proof(): BrowserConnectionProof {
        gate.pauseAutomatic();gate.beginPrototype()
        return BrowserConnectionProof(gate,"123","Synthetic WebView",gate.nextPrototypeAt())
    }
    private class Transport: Call.Factory {
        var calls=0
        override fun newCall(request: okhttp3.Request): Call {
            calls++
            return object: Call by PrivateReadTransport.client().newCall(request) {
                override fun execute()=Response.Builder().request(request).protocol(Protocol.HTTP_2).code(200).message("fixture")
                    .body("""{"status":"ok","user":{"pk":"123","username":"synthetic","follower_count":0,"following_count":7}}""".toResponseBody()).build()
                override fun enqueue(callback: Callback)=error("Synchronous test only")
                override fun timeout()=Timeout()
            }
        }
    }
    @Test fun continuationHonorsSpacingAndPreservesTheOriginalRoundDeadline() {
        val p=proof();val deadline=gate.nextPrototypeAt();val http=Transport()
        // A proof is not permission to bypass the existing request interval.
        assertEquals(ApiFailure.Kind.TOO_SOON,assertThrows(ApiFailure::class.java) {
            WebConnectionAttempt(context,gate,sessions,vault,http).connect(state(),null,p)
        }.kind)
        assertEquals(0,http.calls);assertEquals(1L,gate.attempts());assertNull(vault.read())
        // The rejected continuation consumed its one-use proof; there is no replay.
        clock.addAndGet(15001)
        assertEquals(ApiFailure.Kind.TOO_SOON,assertThrows(ApiFailure::class.java) {
            WebConnectionAttempt(context,gate,sessions,vault,http).connect(state(),null,p)
        }.kind)
        assertEquals(deadline,gate.nextPrototypeAt());assertEquals(0,http.calls)
    }
    @Test fun successfulNativeContinuationUsesOneCallAndCannotBeReplayed() {
        val p=proof();val deadline=gate.nextPrototypeAt();clock.addAndGet(15001);val http=Transport()
        val saved=WebConnectionAttempt(context,gate,sessions,vault,http).connect(state(),null,p)
        assertEquals("123",saved.profile.userId);assertEquals(1,http.calls);assertEquals(2L,gate.attempts())
        assertEquals(deadline,gate.nextPrototypeAt())
        assertEquals(ApiFailure.Kind.TOO_SOON,assertThrows(ApiFailure::class.java) {
            WebConnectionAttempt(context,gate,sessions,vault,http).connect(state(),saved.session,p)
        }.kind)
        assertEquals(saved,vault.read());assertEquals(1,http.calls)
    }
    @Test fun changedIdentityExpiredProofAndNewCooldownCannotSendARequest() {
        val p=proof();clock.addAndGet(15001);val http=Transport()
        val changed=state().copy(owner="456",cookies=state().cookies+mapOf("ds_user_id" to "456"))
        assertEquals(ApiFailure.Kind.WRONG_ACCOUNT,assertThrows(ApiFailure::class.java) {
            WebConnectionAttempt(context,gate,sessions,vault,http).connect(changed,null,p)
        }.kind)
        gate.limited(null)
        assertEquals(ApiFailure.Kind.LIMITED,assertThrows(ApiFailure::class.java) {
            WebConnectionAttempt(context,gate,sessions,vault,http).connect(state(),null,p)
        }.kind)
        assertEquals(0,http.calls);assertNull(vault.read())
        val expired=BrowserConnectionProof(gate,"123","Synthetic WebView",clock.get())
        assertEquals(ApiFailure.Kind.TOO_SOON,assertThrows(ApiFailure::class.java) {
            WebConnectionAttempt(context,gate,sessions,vault,http).connect(state(),null,expired)
        }.kind)
        assertEquals(0,http.calls)
    }
    @Test fun journalPersistsOnlyFixedMetadataAndDoesNotOverwriteACompletedStep() {
        journal.begin(WebConnectionJournal.Step.BROWSER)
        journal.end(WebConnectionJournal.Step.BROWSER,WebConnectionJournal.Outcome.SUCCESS,200)
        journal.end(WebConnectionJournal.Step.BROWSER,WebConnectionJournal.Outcome.CANCELLED,null)
        journal.begin(WebConnectionJournal.Step.NATIVE)
        journal.end(WebConnectionJournal.Step.NATIVE,WebConnectionJournal.Outcome.LIMITED,429)
        val restored=WebConnectionJournal(context).read()
        assertEquals(listOf(200,429),restored.map {it.http})
        assertEquals(listOf(WebConnectionJournal.Outcome.SUCCESS,WebConnectionJournal.Outcome.LIMITED),restored.map {it.outcome})
        val prefs=context.getSharedPreferences("web_connection_diagnostic",0).all
        assertEquals(setOf("format","BROWSER","BROWSERat","BROWSERhttp","NATIVE","NATIVEat","NATIVEhttp"),prefs.keys)
        assertTrue(prefs.values.all {it is Number || it in WebConnectionJournal.Outcome.entries.map {entry->entry.name}})
        journal.begin(WebConnectionJournal.Step.BROWSER)
        assertEquals(1,journal.read().size);assertNull(journal.read().single().http)
    }
    @Test fun older429AttributionRemainsExplicitlyUnknownUntilANewRound() {
        journal.begin(WebConnectionJournal.Step.BROWSER)
        journal.end(WebConnectionJournal.Step.BROWSER,WebConnectionJournal.Outcome.LIMITED,429)
        val prefs=context.getSharedPreferences("web_connection_diagnostic",0)
        assertTrue(prefs.edit().remove("format").commit())
        val original=prefs.all
        assertTrue(WebConnectionJournal(context).legacyAttribution())
        assertEquals(429,WebConnectionJournal(context).read().single().http)
        assertEquals(original,prefs.all)
        journal.reset();assertFalse(journal.legacyAttribution());assertTrue(journal.read().isEmpty())
    }
}
