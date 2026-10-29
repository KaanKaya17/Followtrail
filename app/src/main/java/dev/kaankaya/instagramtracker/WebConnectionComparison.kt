package dev.kaankaya.instagramtracker

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.WebView
import org.json.JSONObject
import java.util.UUID

/** Fixed-value local diagnostics. No identity, cookie, URL, body or header is accepted. */
class WebConnectionJournal(context: Context) {
    enum class Step { BROWSER, NATIVE }
    enum class Outcome { STARTED, SUCCESS, LIMITED, AUTH, INVALID, CONNECTION, CANCELLED }
    data class Entry(val step: Step, val at: Long, val outcome: Outcome, val http: Int?)
    private val preferences=context.getSharedPreferences("web_connection_diagnostic",Context.MODE_PRIVATE)
    fun begin(step: Step) {
        if(step==Step.BROWSER) check(preferences.edit().clear().commit())
        write(Entry(step,System.currentTimeMillis(),Outcome.STARTED,null))
    }
    fun end(step: Step, outcome: Outcome, http: Int?) {
        val previous=read().firstOrNull { it.step==step } ?: return
        if(previous.outcome==Outcome.STARTED) write(previous.copy(outcome=outcome,http=http))
    }
    private fun write(entry: Entry) {
        require(entry.at>0 && (entry.http==null || entry.http in 100..599))
        val edit=preferences.edit().putLong(entry.step.name+"at",entry.at).putString(entry.step.name,entry.outcome.name)
        if(entry.http==null)edit.remove(entry.step.name+"http")else edit.putInt(entry.step.name+"http",entry.http)
        if(!edit.commit())throw ApiFailure(ApiFailure.Kind.STORAGE)
    }
    fun read(): List<Entry> = Step.entries.mapNotNull { step ->
        preferences.getString(step.name,null)?.let { name ->
            val at=preferences.getLong(step.name+"at",0)
            val http=if(preferences.contains(step.name+"http"))preferences.getInt(step.name+"http",0)else null
            require(at>0 && (http==null || http in 100..599))
            Entry(step,at,Outcome.valueOf(name),http)
        }
    }
    fun clear() {check(preferences.edit().clear().commit())}
    companion object {
        fun outcome(error: ApiFailure.Kind): Outcome = when(error) {
            ApiFailure.Kind.LIMITED->Outcome.LIMITED
            ApiFailure.Kind.SESSION_EXPIRED,ApiFailure.Kind.VERIFICATION->Outcome.AUTH
            ApiFailure.Kind.CONNECTION->Outcome.CONNECTION
            ApiFailure.Kind.CANCELLED->Outcome.CANCELLED
            else->Outcome.INVALID
        }
    }
}

/** One continuation of the same explicit connection attempt, not another login/retry. */
class BrowserConnectionProof internal constructor(private val gate: RequestGate, private val owner: String,
    private val userAgent: String, private val expires: Long) {
    private var consumed=false
    @Synchronized internal fun consume(candidate: RequestGate, state: WebSession) {
        if(consumed || candidate!==gate || gate.now()>=expires)throw ApiFailure(ApiFailure.Kind.TOO_SOON)
        if(state.owner!=owner || state.userAgent!=userAgent)throw ApiFailure(ApiFailure.Kind.WRONG_ACCOUNT)
        consumed=true
    }
}

/** One same-origin browser GET, using the production native reader's request contract. */
class BrowserProfileProbe(private val context: Context, private val browser: WebView, private val gate: RequestGate,
    private val journal: WebConnectionJournal, private val complete: (Result<Profile>,BrowserConnectionProof?)->Unit) {
    private val handler=Handler(Looper.getMainLooper())
    private var lease: AutoCloseable?=null
    private var slot: String?=null
    private var session: WebSession?=null
    private var started=0L
    val running get()=lease!=null
    fun start(state: WebSession) {
        check(!running)
        if(!ProbePolicy.usablePage(browser.url))throw ApiFailure(ApiFailure.Kind.SESSION_EXPIRED)
        val request=WebRequests.profile(state,state.owner)
        val settings=JSONObject().put("path",request.url.encodedPath).put("owner",state.owner)
            .put("headers",JSONObject().apply {
                // Browser owns cookies, User-Agent, Referer, origin and transport.
                for(name in listOf("Accept","X-IG-App-ID","X-Requested-With","X-IG-WWW-Claim","X-CSRFToken"))
                    request.header(name)?.let { put(name,it) }
            })
        val claimed=gate.claim()
        try {gate.beginPrototype();journal.begin(WebConnectionJournal.Step.BROWSER);lease=claimed}
        catch(error: Exception) {claimed.close();throw error}
        session=state;started=SystemClock.elapsedRealtime()
        val key="__tracker_connection_"+UUID.randomUUID().toString().replace("-","");slot=key
        settings.put("slot",key)
        try {
            val script=context.assets.open("web_connection_probe.js").bufferedReader().use {it.readText()}
            browser.evaluateJavascript("$script($settings)") { value ->
                if(slot!=key || !running)return@evaluateJavascript
                if(value!="true")finish(JSONObject().put("reason","invalid"))else handler.post(poll)
            }
        }catch(_: Exception) {finish(JSONObject().put("reason","invalid"))}
    }
    private val poll=object: Runnable {
        override fun run() {
            val key=slot ?: return
            if(!running)return
            if(!ProbePolicy.usablePage(browser.url)) {finish(JSONObject().put("reason","cancelled"));return}
            if(SystemClock.elapsedRealtime()-started>25000) {finish(JSONObject().put("reason","timeout"));return}
            browser.evaluateJavascript("JSON.stringify(window[${JSONObject.quote(key)}]||{stage:'missing'})") { raw ->
                if(slot!=key || !running)return@evaluateJavascript
                try {
                    val result=ProbePolicy.decode(raw)
                    when(result.optString("stage")) {
                        "waiting"->handler.postDelayed(this,200)
                        "done"->finish(result)
                        else->finish(JSONObject().put("reason","invalid"))
                    }
                }catch(_: Exception) {finish(JSONObject().put("reason","invalid"))}
            }
        }
    }
    private fun finish(result: JSONObject) {
        if(!running)return
        val state=requireNotNull(session)
        val http=result.optInt("status").takeIf {it in 100..599}
        val value=try {
            http?.let(gate::observedStatus)
            val failure=ProbePolicy.failure(result)
            if(failure!=null) {
                if(failure==ApiFailure.Kind.LIMITED)gate.limited(result.optString("retryAfter").takeIf {it.length<=160},
                    reason=if(http==429)InstagramResponsePolicy.Restriction.HTTP_429.label else if(result.optString("reason")=="feedback")InstagramResponsePolicy.Restriction.FEEDBACK.label else InstagramResponsePolicy.Restriction.WAIT.label)
                if(failure in setOf(ApiFailure.Kind.SESSION_EXPIRED,ApiFailure.Kind.VERIFICATION))gate.pauseForConnection()
                throw ApiFailure(failure)
            }
            val profile=ProfileParser.fromBody(result.getJSONObject("payload"),state.owner,gate.now())
            // A browser success never clears the shared pause or commits the account.
            if(gate.blockedUntil()>gate.now())throw ApiFailure(ApiFailure.Kind.LIMITED)
            journal.end(WebConnectionJournal.Step.BROWSER,WebConnectionJournal.Outcome.SUCCESS,http)
            Result.success(profile)
        }catch(error: Exception) {
            val failure=(error as? ApiFailure) ?: ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)
            try {journal.end(WebConnectionJournal.Step.BROWSER,WebConnectionJournal.outcome(failure.kind),http)}catch(_: Exception) { }
            Result.failure(failure)
        }
        val proof=if(value.isSuccess)BrowserConnectionProof(gate,state.owner,state.userAgent,gate.nextPrototypeAt())else null
        stop();complete(value,proof)
    }
    private fun stop() {
        handler.removeCallbacks(poll)
        val key=slot;slot=null;session=null
        try {if(key!=null)browser.evaluateJavascript("(()=>{const s=window[${JSONObject.quote(key)}];if(s&&s.cancel)s.cancel();delete window[${JSONObject.quote(key)}];})()",null)}
        finally {lease?.close();lease=null}
    }
    fun cancel() {
        if(!running)return
        try {journal.end(WebConnectionJournal.Step.BROWSER,WebConnectionJournal.Outcome.CANCELLED,null)}finally {stop()}
    }
}
