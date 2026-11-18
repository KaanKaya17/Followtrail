package dev.kaankaya.instagramtracker

import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.Call
import okhttp3.FormBody
import okhttp3.Request
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class AccountSafetyBoundaryTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private var now=2_000_000_000_000L
    private val prefs get()=context.getSharedPreferences("safety_budget",0)
    private val sessions=WebSessions(context,"safety")
    @Before fun setup(){check(context.packageName.endsWith(".debug"));cleanup();now=2_000_000_000_000L}
    @After fun cleanup(){sessions.clear();prefs.edit().clear().commit()}
    @Test fun rejectedMutationCannotReachCallFactoryOrIncrementGate(){
        val gate=RequestGate(context,"safety_budget"){now}
        val state=WebSessionCodec.fromHeader("sessionid=synthetic; ds_user_id=123","Synthetic UA")
        val reference=sessions.stage(state)
        var calls=0
        val factory=Call.Factory{calls++;error("Forbidden transport reached")}
        val reader=PrivateReadTransport(gate,sessions,factory,setOf("www.instagram.com"),{s:WebSession,_,_->s})
        for(path in listOf("/api/v1/web/friendships/123/follow/","/api/v1/media/123/like/","/api/graphql")){
            val failure=assertThrows(ApiFailure::class.java){reader.read(reference,request={_,_->Request.Builder()
                .url("https://www.instagram.com$path").post(FormBody.Builder().build()).build()},parse={_,_->Unit})}
            assertEquals(ApiFailure.Kind.POLICY,failure.kind)
        }
        assertEquals(0,calls);assertEquals(0L,gate.attempts());assertEquals(state,sessions.read(reference))
    }
    @Test fun budgetSurvivesNewGateAndSuccessfulReadDoesNotClearIt(){
        val gate=RequestGate(context,"safety_budget"){now}
        // This gate has no account owner, so the device-wide hourly budget is the limit that applies.
        repeat(RequestDefaults.DEVICE_HOURLY){gate.begin();now+=RequestDefaults.MAX_SPACING_MS+1}
        val end=gate.budgetNextAt();val attempts=gate.attempts()
        assertTrue(end>now)
        val failure=assertThrows(ApiFailure::class.java){RequestGate(context,"safety_budget"){now}.begin()}
        assertEquals(ApiFailure.Kind.BUDGET,failure.kind)
        gate.succeeded()
        assertEquals(end,gate.budgetNextAt());assertEquals(attempts,gate.attempts())
        assertEquals(0L,gate.blockedUntil());assertFalse(gate.manualOnly())
    }
}
