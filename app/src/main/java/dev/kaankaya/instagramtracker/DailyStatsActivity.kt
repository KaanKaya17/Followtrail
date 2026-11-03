package dev.kaankaya.instagramtracker

import android.app.AlertDialog
import android.os.Bundle
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone
import java.util.concurrent.Executors

/** Local-only archive view. It never constructs a network reader. */
class DailyStatsActivity : LocalizedActivity() {
    private lateinit var vault: SessionVault
    private var reference: Session?=null
    private var target=""
    private var username=""
    private var span=30
    private var metric="followers"
    private var epoch=0
    private var loading=true
    private var failed=false
    private var series=DailySeries(emptyList(),1,0,0)
    private val executor=Executors.newSingleThreadExecutor()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);AppUi.systemBars(this)
        if(!BuildConfig.DEBUG)window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val preview=PreviewMode.enabled(this)
        val owner=intent.getStringExtra("viewer_id").orEmpty()
        target=intent.getStringExtra("target_id").orEmpty()
        vault=if(preview)SessionVault(this,"previewaccount") else SessionVault(this,owner=owner)
        val saved=try {vault.read()} catch (_: Exception) {null}
        val profile=saved?.let { if(it.profile.userId==target)it.profile else it.people.firstOrNull { p->p.latest.userId==target }?.latest }
        if(saved==null || saved.session.userId!=owner || profile==null){finish();return}
        reference=saved.session;username=profile.username
        span=savedInstanceState?.getInt("daily_range",30)?.takeIf { it in listOf(0,7,30,365) } ?: 30
        metric=savedInstanceState?.getString("daily_metric")?.takeIf { it in listOf("followers","following") } ?: "followers"
        load()
    }
    override fun onSaveInstanceState(outState: Bundle) {super.onSaveInstanceState(outState);outState.putInt("daily_range",span);outState.putString("daily_metric",metric)}
    override fun onDestroy() {epoch++;executor.shutdownNow();super.onDestroy()}
    private fun dp(value: Int)=(value*resources.displayMetrics.density).toInt()
    private fun number(value: Long)=NumberFormat.getIntegerInstance(AppLanguage.locale(this)).format(value)
    private fun date(at: Long)=SimpleDateFormat("d MMM yyyy",AppLanguage.locale(this)).apply {timeZone=TimeZone.getTimeZone("UTC")}.format(Date(at))
    private fun load(clear: Boolean=false) {
        val ticket=++epoch;val chosenMetric=metric;val chosenSpan=span
        loading=true;failed=false;render()
        executor.execute {
            try {
                val saved=vault.read()?.takeIf {it.session==reference} ?: error("Connection changed")
                if(clear)vault.clearDaily(saved.session,target,System.currentTimeMillis())
                val result=vault.daily.query(saved,target,chosenMetric,chosenSpan,System.currentTimeMillis())
                runOnUiThread {if(ticket==epoch && !isFinishing && !isDestroyed){series=result;loading=false;render()}}
            } catch (_: Exception) {
                runOnUiThread {if(ticket==epoch && !isFinishing && !isDestroyed){failed=true;loading=false;render()}}
            }
        }
    }
    private fun render() {
        val page=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(18),dp(20),dp(24));setBackgroundColor(AppUi.background)}
        page.addView(AppUi.button(this,getString(R.string.ui_close),false){finish()})
        page.addView(AppUi.heading(this,getString(R.string.daily_title),26f))
        page.addView(AppUi.text(this,"@$username",15f,AppUi.muted))
        val metrics=LinearLayout(this)
        for((value,label) in listOf("followers" to R.string.ui_followers,"following" to R.string.ui_following))
            metrics.addView(AppUi.button(this,getString(label),metric==value){metric=value;load()},LinearLayout.LayoutParams(0,-2,1f))
        page.addView(metrics)
        val ranges=LinearLayout(this)
        for((value,label) in listOf(7 to R.string.daily_week,30 to R.string.daily_month,365 to R.string.daily_year,0 to R.string.daily_all))
            ranges.addView(AppUi.button(this,getString(label),span==value){span=value;load()},LinearLayout.LayoutParams(0,-2,1f))
        page.addView(ranges)
        when {
            loading->page.addView(AppUi.text(this,getString(R.string.daily_loading),14f,AppUi.muted))
            failed->{page.addView(AppUi.text(this,getString(R.string.daily_error),14f,AppUi.muted));page.addView(AppUi.button(this,getString(R.string.daily_retry),false){load()})}
            series.points.isEmpty()->page.addView(AppUi.text(this,getString(R.string.daily_empty),14f,AppUi.muted))
            else->{
                page.addView(AppUi.numeric(AppUi.heading(this,number(series.points.last().value),30f)))
                page.addView(AppUi.text(this,getString(R.string.daily_coverage,number(series.observedDays.toLong()),number(series.samples)),13f,AppUi.muted))
                page.addView(DailyChartView(this,series),LinearLayout.LayoutParams(-1,dp(250)))
                if(series.bucketDays>1)page.addView(AppUi.text(this,getString(R.string.daily_grouped,number(series.bucketDays)),12f,AppUi.muted))
                for(point in series.points.takeLast(10).asReversed())page.addView(AppUi.text(this,
                    "${date(point.day)} · ${number(point.value)}\n"+getString(R.string.daily_extrema,number(point.minimum),number(point.maximum)),13f,AppUi.ink))
            }
        }
        page.addView(AppUi.text(this,getString(R.string.daily_scope),12f,AppUi.muted))
        page.addView(AppUi.button(this,getString(R.string.daily_clear),false,!loading){
            AlertDialog.Builder(this).setTitle(R.string.daily_clear_question).setMessage(R.string.daily_clear_scope)
                .setNegativeButton(R.string.list_cancel,null).setPositiveButton(R.string.daily_clear){_,_->load(true)}.show()
        })
        setContentView(ScrollView(this).apply {addView(page)})
    }
}
