package dev.kaankaya.instagramtracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView

/** Static product help only. Opening this screen does not read accounts or schedule checks. */
class UsageNotesActivity : LocalizedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(AppUi.dp(context,22),AppUi.dp(context,16),AppUi.dp(context,22),AppUi.dp(context,24))
            setBackgroundColor(AppUi.background)
        }
        val header=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL}
        header.addView(AppUi.iconButton(this,"back",getString(R.string.list_back)){finish()})
        header.addView(AppUi.heading(this,getString(R.string.usage_notes_title),24f))
        page.addView(header)
        val notes = listOf(
            R.string.activity_scope to R.string.usage_notes_observations,
            R.string.ui_lists to R.string.usage_notes_lists,
            R.string.ui_automatic_monitoring to R.string.usage_notes_checks,
            R.string.timeline_title to R.string.timeline_scope,
            R.string.quick_start to R.string.quick_hint
        ).map { (title,body) -> getString(title) to getString(body) } + listOf(
            getString(R.string.ui_people) to listOf(
                getString(R.string.people_capacity_scope,PeoplePolicy.MAX_PEOPLE),
                getString(R.string.profile_watch_scope),getString(R.string.watch_timing_scope)
            ).joinToString("\n\n"),
            getString(R.string.ui_observation_history) to getString(R.string.history_retention_scope,PeoplePolicy.MAX_HISTORY)
        )
        for((title,body) in notes) {
            val card=AppUi.card(this)
            card.addView(AppUi.heading(this,title,18f))
            card.addView(AppUi.text(this,body,14f,AppUi.muted).apply {setPadding(0,AppUi.dp(context,10),0,0)})
            page.addView(card,LinearLayout.LayoutParams(-1,-2).apply {topMargin=AppUi.dp(this@UsageNotesActivity,16)})
        }
        val scroll=ScrollView(this).apply {
            isFillViewport=true;addView(page);AppUi.scrollStyle(this)
            setOnApplyWindowInsetsListener {view,insets->
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom)
                insets
            }
        }
        setContentView(scroll);scroll.requestApplyInsets()
    }
}

object UsageNotes {
    fun open(context: Context) {context.startActivity(Intent(context,UsageNotesActivity::class.java))}
    fun link(context: Context)=AppUi.button(context,context.getString(R.string.usage_notes_title),false) {open(context)}.apply {
        textSize=12f
        setTextColor(AppUi.muted)
        background=AppUi.touch(context,AppUi.shape(context,android.graphics.Color.TRANSPARENT,12))
        minimumHeight=AppUi.dp(context,48); minHeight=AppUi.dp(context,48)
    }
}
