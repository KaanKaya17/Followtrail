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

/** One explicit search request at a time; closing the dialog cancels its owned reader. */
class ProfileSearchDialog(private val context: Context, private val known: List<ProfileCandidate>,
    private val search: (String)->List<ProfileCandidate>, private val cancel: ()->Unit,
    private val select: (ProfileCandidate)->Unit, private val direct: (String)->Unit,
    private val dismissed: ()->Unit = {}) : AutoCloseable {
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private var generation=0
    private var busy=false
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
    fun show() {
        dialog.setOnDismissListener {generation++;cancel();worker.shutdownNow();dismissed()}
        dialog.show()
        fun display(users: List<ProfileCandidate>, label: Int) {
            rows.removeAllViews();status.text=context.getString(if(users.isEmpty())R.string.search_none else label)
            for(person in users)rows.addView(AppUi.button(context,
                "@${person.username}"+person.name.takeIf {it.isNotBlank()}?.let {" · $it"}.orEmpty(),false) {
                dialog.dismiss();select(person)
            }.apply {maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END})
        }
        fun local() = display(known.filter {ProfileSearch.matches(it.username+" "+it.name,input.text.toString())}.take(20),R.string.search_local)
        input.addTextChangedListener(object:TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {if(!busy)local()}
            override fun afterTextChanged(s:Editable?) {}
        })
        local()
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            val username=ProfileInput.username(input.text.toString())
            if(username==null)input.error=context.getString(R.string.ui_enter_a_valid_username_or_instagram_profile)
            else {dialog.dismiss();direct(username)}
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val query=try {ProfileSearch.query(input.text.toString())}catch (_:Exception){input.error=context.getString(R.string.search_query_invalid);return@setOnClickListener}
            val ticket=++generation;busy=true;input.isEnabled=false;rows.removeAllViews()
            status.text=context.getString(R.string.search_loading)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=false
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled=false
            worker.execute {
                val result=try {Result.success(search(query))}catch (error:ApiFailure){Result.failure(error)}catch (_:Exception){Result.failure(ApiFailure(ApiFailure.Kind.INVALID_RESPONSE))}
                main.post {
                    if(!dialog.isShowing || generation!=ticket)return@post
                    busy=false;input.isEnabled=true
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=true
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled=true
                    result.onSuccess {display(it,R.string.search_results)}.onFailure {status.text=(it as ApiFailure).userMessage(context)}
                }
            }
        }
    }
    override fun close() {dialog.dismiss()}
}
