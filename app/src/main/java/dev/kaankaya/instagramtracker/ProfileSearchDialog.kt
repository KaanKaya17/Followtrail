package dev.kaankaya.instagramtracker

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import java.util.concurrent.Executors

/** Only the latest input survives debounce and pacing; thumbnails never use the session. */
class ProfileSearchDialog(private val context: Context, private val known: List<ProfileCandidate>,
    private val search: (String)->List<ProfileCandidate>, private val cancel: ()->Unit,
    private val select: (ProfileCandidate)->Unit, private val direct: (String)->Unit,
    private val gate: RequestGate? = null, private val owner: String = "search",
    private val dismissed: ()->Unit = {}) : AutoCloseable {
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private val images=AvatarImages(context,transient=true)
    private var busy=false
    private var held=false
    private var heldError: ApiFailure?=null
    private var closed=false
    private var inFlight: String?=null
    private var attempted: String?=null
    private val cache=linkedMapOf<String,Pair<Long,List<ProfileCandidate>>>()
    private val input=EditText(context).apply {
        hint=context.getString(R.string.search_people_hint);setSingleLine(true)
        filters=arrayOf(android.text.InputFilter.LengthFilter(256))
        inputType=android.text.InputType.TYPE_CLASS_TEXT
        setTextColor(AppUi.ink);setHintTextColor(AppUi.muted)
        if(android.os.Build.VERSION.SDK_INT>=26)importantForAutofill=android.view.View.IMPORTANT_FOR_AUTOFILL_NO
    }
    private val status=AppUi.text(context,"",12f,AppUi.muted).apply {accessibilityLiveRegion=android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE}
    private val rows=LinearLayout(context).apply {orientation=LinearLayout.VERTICAL}
    private val box=LinearLayout(context).apply {
        orientation=LinearLayout.VERTICAL;setPadding(AppUi.dp(context,20),AppUi.dp(context,12),AppUi.dp(context,20),0)
        addView(input);addView(status)
        addView(ScrollView(context).apply {addView(rows)},LinearLayout.LayoutParams(-1,AppUi.dp(context,260)))
    }
    private val dialog=AlertDialog.Builder(context).setTitle(R.string.ui_add_person).setView(box)
        .setPositiveButton(R.string.search_instagram,null).setNeutralButton(R.string.search_direct,null)
        .setNegativeButton(R.string.ui_cancel,null).create()
    private val pending=Runnable { request() }
    private fun schedule(delay: Long) {main.removeCallbacks(pending);if(!closed)main.postDelayed(pending,delay)}
    private fun display(users: List<ProfileCandidate>, label: Int) {
        images.cancelAll();rows.removeAllViews()
        status.text=context.getString(if(users.isEmpty())R.string.search_none else label)
        for(person in users) {
            val row=LinearLayout(context).apply {
                gravity=android.view.Gravity.CENTER_VERTICAL
                val pad=AppUi.dp(context,8);setPadding(pad,pad,pad,pad)
                background=AppUi.touch(context,AppUi.shape(context,AppUi.surface,12))
                isClickable=true;isFocusable=true;contentDescription="@${person.username} · ${person.name}"
                setOnClickListener {dialog.dismiss();select(person)}
            }
            row.addView(ProfileAvatarView(context,40).apply {bind(images,owner,person.id,person.username,person.pictureUrl)})
            val names=LinearLayout(context).apply {
                orientation=LinearLayout.VERTICAL;setPadding(AppUi.dp(context,12),0,0,0)
                addView(AppUi.text(context,person.name.ifBlank {person.username},14f,AppUi.ink,true).apply {maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END})
                addView(AppUi.text(context,"@${person.username}",12f,AppUi.muted).apply {maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END})
            }
            row.addView(names,LinearLayout.LayoutParams(0,-2,1f));rows.addView(row)
        }
    }
    private fun query(): String? = try {ProfileSearch.query(input.text.toString())}catch (_:Exception){null}
    private fun changed() {
        attempted=null;main.removeCallbacks(pending)
        display(known.filter {ProfileSearch.matches(it.username+" "+it.name,input.text.toString())}.take(20),R.string.search_local)
        val q=query() ?: return
        val saved=cache[q]
        if(saved!=null && android.os.SystemClock.elapsedRealtime()-saved.first<60000) {
            attempted=q;display(saved.second,R.string.search_results);return
        }
        if(!held)schedule(650) else status.text=heldError?.userMessage(context)
    }
    private fun request() {
        val q=query() ?: return
        if(closed || held || attempted==q)return
        if(busy) {schedule(250);return}
        try {
            val now=gate?.now() ?: System.currentTimeMillis()
            if(gate!=null) {
                if(gate.blockedUntil()>now) {status.text=ApiFailure(ApiFailure.Kind.LIMITED).userMessage(context);return}
                if(gate.requiresConnection()) {status.text=ApiFailure(ApiFailure.Kind.CONNECTION).userMessage(context);return}
                if(gate.budgetNextAt()>now) {status.text=ApiFailure(ApiFailure.Kind.BUDGET).userMessage(context);return}
                val wait=gate.nextAllowedAt()-now
                if(wait>0) {
                    val seconds=((wait+999)/1000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    status.text=context.resources.getQuantityString(R.plurals.search_waiting,seconds,seconds)
                    schedule(minOf(wait,1000));return
                }
            }
        } catch(error:ApiFailure) {status.text=error.userMessage(context);return}
        busy=true;inFlight=q;attempted=q;status.text=context.getString(R.string.search_loading)
        worker.execute {
            val result=try {Result.success(search(q))}catch (error:ApiFailure){Result.failure(error)}catch (_:Exception){Result.failure(ApiFailure(ApiFailure.Kind.INVALID_RESPONSE))}
            main.post {
                busy=false;inFlight=null
                if(closed)return@post
                result.onSuccess {
                    cache.remove(q);cache[q]=android.os.SystemClock.elapsedRealtime() to it
                    if(cache.size>6)cache.remove(cache.keys.first())
                }.onFailure {if((it as ApiFailure).kind in setOf(ApiFailure.Kind.LIMITED,ApiFailure.Kind.BUDGET,ApiFailure.Kind.CONNECTION,ApiFailure.Kind.SESSION_EXPIRED,ApiFailure.Kind.VERIFICATION)) {held=true;heldError=it}}
                if(q==query()) {
                    attempted=q
                    result.onSuccess {display(it,R.string.search_results)}.onFailure {status.text=(it as ApiFailure).userMessage(context)}
                } else if(!held) {
                    schedule(0)
                } else {
                    status.text=heldError?.userMessage(context)
                }
            }
        }
    }
    fun show() {
        dialog.setOnDismissListener {
            closed=true;main.removeCallbacksAndMessages(null);cache.clear();cancel();images.close();worker.shutdownNow();dismissed()
        }
        dialog.show()
        input.addTextChangedListener(object:TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {changed()}
            override fun afterTextChanged(s:Editable?) {}
        })
        changed()
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            val username=ProfileInput.username(input.text.toString())
            if(username==null)input.error=context.getString(R.string.ui_enter_a_valid_username_or_instagram_profile)
            else {dialog.dismiss();direct(username)}
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if(query()==null) {input.error=context.getString(R.string.search_query_invalid);return@setOnClickListener}
            if(inFlight!=query()) {held=false;attempted=null;schedule(0)}
        }
    }
    override fun close() {dialog.dismiss()}
}
