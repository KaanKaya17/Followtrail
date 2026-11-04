package dev.kaankaya.instagramtracker

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.concurrent.Executors

/** Shared timeline/notification screen. Reads local encrypted facts only; rows are recycled. */
class TimelineActivity : LocalizedActivity() {
    private lateinit var vault: SessionVault
    private lateinit var images: AvatarImages
    private lateinit var list: ListView
    private lateinit var filters: LinearLayout
    private lateinit var choices: LinearLayout
    private lateinit var ownerButton: android.widget.Button
    private lateinit var status: TextView
    private lateinit var footer: LinearLayout
    private var reference: Session?=null
    private var account: SavedAccount?=null
    private var events=emptyList<CheckEvent>()
    private var readKeys=emptySet<String>()
    private var target: String?=null
    private var type="all"
    private var unread=false
    private var notices=false
    private var preview=false
    private var loading=false
    private var epoch=0
    private var eventLimit=50
    private var checkLimit=50
    private val expanded=mutableSetOf<String>()
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private var listening: AutoCloseable?=null
    private val reload=Runnable { load() }
    private sealed interface Row {
        data class Day(val label: String): Row
        data class Routine(val day: String,val count: Int): Row
        data class Event(val event: CheckEvent): Row
    }
    private var rows=emptyList<Row>()
    private data class Holder(val photo: ProfileAvatarView,val name: TextView,val body: TextView,val time: TextView,val state: TextView)
    private fun dp(n: Int)=AppUi.dp(this,n)
    private fun column()=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);AppUi.systemBars(this)
        if(!BuildConfig.DEBUG)window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        preview=PreviewMode.enabled(this);notices=intent.getBooleanExtra("notifications_only",false)
        vault=SessionVault(this,if(preview)"previewaccount" else "account",if(preview)null else intent.getStringExtra("viewer_id"))
        images=AvatarImages(this,PreviewMode.avatarFetcher(preview),if(preview)"previewavatars" else "avatars")
        target=if(savedInstanceState?.containsKey("target")==true)savedInstanceState.getString("target") else intent.getStringExtra("target_id")
        type=savedInstanceState?.getString("type") ?: "all";unread=savedInstanceState?.getBoolean("unread") ?: false
        eventLimit=savedInstanceState?.getInt("event_limit",50) ?: 50;checkLimit=savedInstanceState?.getInt("check_limit",50) ?: 50
        expanded.addAll(savedInstanceState?.getStringArrayList("expanded").orEmpty())
        build();load()
    }
    private fun build() {
        val root=column().apply {
            setBackgroundColor(AppUi.background)
            setPadding(dp(18),dp(12),dp(18),dp(12))
            setOnApplyWindowInsetsListener {v,insets ->
                @Suppress("DEPRECATION")
                v.setPadding(dp(18)+insets.systemWindowInsetLeft,dp(12)+insets.systemWindowInsetTop,dp(18)+insets.systemWindowInsetRight,dp(12)+insets.systemWindowInsetBottom)
                insets
            }
        }
        val heading=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL}
        heading.addView(AppUi.iconButton(this,"back",getString(R.string.list_back)){finish()},LinearLayout.LayoutParams(dp(48),dp(48)))
        heading.addView(AppUi.heading(this,getString(if(notices)R.string.parity_notifications else R.string.timeline_title),24f),LinearLayout.LayoutParams(0,-2,1f))
        heading.addView(AppUi.iconButton(this,"refresh",getString(R.string.parity_refresh_history)){load()},LinearLayout.LayoutParams(dp(48),dp(48)))
        root.addView(heading)
        ownerButton=AppUi.button(this,getString(R.string.timeline_all),false){chooseOwner()};root.addView(ownerButton)
        choices=LinearLayout(this);root.addView(choices)
        filters=LinearLayout(this);root.addView(HorizontalScrollView(this).apply {isHorizontalScrollBarEnabled=false;addView(filters)})
        status=AppUi.text(this,getString(R.string.daily_loading),12f,AppUi.muted).apply {setPadding(0,dp(10),0,dp(8))};root.addView(status)
        list=ListView(this).apply {divider=null;dividerHeight=dp(8);clipToPadding=false;setPadding(0,0,0,dp(8));AppUi.scrollStyle(this)}
        footer=column();list.addFooterView(footer,null,false);list.adapter=adapter
        root.addView(list,LinearLayout.LayoutParams(-1,0,1f));root.addView(UsageNotes.link(this))
        setContentView(root);root.requestApplyInsets();renderFilters()
    }
    private fun load() {
        if(loading || isFinishing || isDestroyed)return
        loading=true;val ticket=++epoch;status.text=getString(R.string.daily_loading)
        worker.execute {
            val result=runCatching {
                val saved=vault.read() ?: error("No account")
                require(saved.session.userId==intent.getStringExtra("viewer_id"))
                require(reference==null || saved.session==reference)
                Triple(saved,vault.journal.read(saved.session),vault.journal.readKeys(saved.session))
            }
            main.post {
                if(ticket!=epoch || isFinishing || isDestroyed)return@post
                loading=false
                result.onSuccess { (saved,history,read) ->
                    reference=saved.session;account=saved;events=history;readKeys=read
                    renderFilters();renderRows()
                }.onFailure {status.text=getString(R.string.timeline_unavailable);if(reference==null)finish()}
            }
        }
    }
    private fun labels(): Map<String,String> = buildMap {
        account?.let { saved -> (listOf(saved.profile)+saved.people.map {it.latest}).forEach {put(it.userId,it.username)} }
        events.sortedByDescending {it.analyzedAt}.forEach {putIfAbsent(it.target,it.username)}
    }
    private fun chooseOwner() {
        val people=labels().toList()
        AlertDialog.Builder(this).setTitle(R.string.timeline_filter)
            .setSingleChoiceItems((listOf(getString(R.string.timeline_all))+people.map {"@${it.second}"}).toTypedArray(),people.indexOfFirst {it.first==target}+1) {dialog,index ->
                dialog.dismiss();target=if(index==0)null else people[index-1].first;eventLimit=50;checkLimit=50;renderFilters();renderRows(true)
            }.setNegativeButton(R.string.ui_cancel,null).show()
    }
    private fun renderFilters() {
        ownerButton.text=target?.let {"@${labels()[it] ?: it}"} ?: getString(R.string.timeline_all)
        choices.removeAllViews()
        if(notices)for((value,label) in listOf(false to R.string.ui_all,true to R.string.parity_unread))
            choices.addView(AppUi.button(this,getString(label),unread==value){unread=value;eventLimit=50;renderFilters();renderRows(true)},LinearLayout.LayoutParams(0,-2,1f))
        filters.removeAllViews()
        val types=if(notices)listOf("all" to R.string.parity_all_types,"profile" to R.string.parity_profile_changes,"lists" to R.string.parity_lists)
            else listOf("all" to R.string.ui_all,"profile" to R.string.parity_profile_changes,"lists" to R.string.parity_lists,"checks" to R.string.parity_checks,"errors" to R.string.parity_errors)
        for((value,label) in types)filters.addView(AppUi.button(this,getString(label),type==value){type=value;eventLimit=50;checkLimit=50;renderFilters();renderRows(true)})
    }
    private fun renderRows(reset: Boolean=false) {
        val first=if(reset)0 else list.firstVisiblePosition;val top=if(reset)0 else list.getChildAt(0)?.top ?: 0
        val source=(if(notices)HistoryPresentation.notifications(events) else events).filter {target==null || it.target==target}
            .filter {!notices || !unread || it.key !in readKeys}
        val chosen=source.filter {when(type){"profile"->it.kind==CheckKind.PROFILE && HistoryPresentation.changed(it);"lists"->it.kind==CheckKind.LIST;"checks"->HistoryPresentation.routine(it);"errors"->it.kind==CheckKind.FAILURE;else->true}}
            .sortedByDescending {it.analyzedAt}
        val changes=chosen.filterNot {HistoryPresentation.routine(it)};val checks=chosen.filter {HistoryPresentation.routine(it)}
        val visible=(changes.take(eventLimit)+checks.take(checkLimit)).sortedByDescending {it.analyzedAt}
        val dayFormat=SimpleDateFormat("yyyy-MM-dd",AppLanguage.locale(this));val older=SimpleDateFormat("d MMM yyyy",AppLanguage.locale(this))
        val today=Calendar.getInstance();val yesterday=Calendar.getInstance().apply {add(Calendar.DAY_OF_YEAR,-1)}
        val groups=visible.groupBy {dayFormat.format(Date(it.observedAt ?: it.analyzedAt))}
        rows=buildList {
            groups.forEach { (day,items) ->
                val at=Date(items.first().observedAt ?: items.first().analyzedAt)
                add(Row.Day(when(day){dayFormat.format(today.time)->getString(R.string.parity_today);dayFormat.format(yesterday.time)->getString(R.string.parity_yesterday);else->older.format(at)}))
                items.filterNot {HistoryPresentation.routine(it)}.forEach {add(Row.Event(it))}
                val routine=items.filter {HistoryPresentation.routine(it)}
                if(routine.isNotEmpty()) {
                    if(type!="checks")add(Row.Routine(day,routine.size))
                    if(type=="checks" || day in expanded)routine.forEach {add(Row.Event(it))}
                }
            }
        }
        status.text=if(chosen.isEmpty())getString(if(notices)R.string.parity_no_notifications else R.string.timeline_empty) else resources.getQuantityString(R.plurals.parity_records,chosen.size,chosen.size)
        footer.removeAllViews()
        if(changes.size>eventLimit)footer.addView(AppUi.button(this,getString(R.string.parity_more_records),false){eventLimit+=50;renderRows()})
        if(checks.size>checkLimit)footer.addView(AppUi.button(this,getString(R.string.parity_more_checks),false){checkLimit+=50;renderRows()})
        if(target!=null || type!="all" || unread)footer.addView(AppUi.button(this,getString(R.string.parity_clear_filters),false){target=null;type="all";unread=false;renderFilters();renderRows(true)})
        adapter.notifyDataSetChanged();list.setSelectionFromTop(first,top)
    }
    private val adapter=object: BaseAdapter() {
        override fun getCount()=rows.size
        override fun getItem(position: Int)=rows[position]
        override fun getItemId(position: Int)=position.toLong()
        override fun getViewTypeCount()=3
        override fun getItemViewType(position: Int)=when(rows[position]){is Row.Day->0;is Row.Routine->1;is Row.Event->2}
        @android.annotation.SuppressLint("SetTextI18n") // Recorded usernames and visual disclosure glyphs, not prose.
        override fun getView(position: Int,convertView: View?,parent: ViewGroup): View=when(val item=rows[position]) {
            is Row.Day -> (convertView as? TextView ?: AppUi.heading(this@TimelineActivity,"",13f).apply {setTextColor(AppUi.muted);setPadding(0,dp(14),0,dp(6))}).apply {text=item.label}
            is Row.Routine -> (convertView as? android.widget.Button ?: AppUi.button(this@TimelineActivity,"",false){}).apply {
                text=getString(R.string.parity_routine_count,item.count)+(if(item.day in expanded || type=="checks")" ▴" else " ▾")
                setOnClickListener {if(item.day in expanded)expanded.remove(item.day) else expanded.add(item.day);renderRows()}
            }
            is Row.Event -> {
                val card=convertView as? LinearLayout ?: LinearLayout(this@TimelineActivity).apply {
                    gravity=Gravity.CENTER_VERTICAL;orientation=LinearLayout.HORIZONTAL;setPadding(dp(14),dp(16),dp(14),dp(16));isFocusable=true
                    val photo=ProfileAvatarView(this@TimelineActivity,42);addView(photo)
                    val copy=column().apply {setPadding(dp(12),0,dp(8),0)}
                    val name=AppUi.text(this@TimelineActivity,"",14f,AppUi.ink,true);val body=AppUi.text(this@TimelineActivity,"",13f,AppUi.muted).apply {maxLines=5;ellipsize=android.text.TextUtils.TruncateAt.END}
                    val badge=AppUi.text(this@TimelineActivity,"",11f,AppUi.violet);copy.addView(name);copy.addView(badge);copy.addView(body);addView(copy,LinearLayout.LayoutParams(0,-2,1f))
                    val time=AppUi.numeric(AppUi.text(this@TimelineActivity,"",11f,AppUi.muted));addView(time);tag=Holder(photo,name,body,time,badge)
                }
                val holder=card.tag as Holder;val event=item.event;val saved=account!!
                val profile=if(saved.profile.userId==event.target)saved.profile else saved.people.firstOrNull {it.latest.userId==event.target}?.latest
                holder.photo.bind(images,saved.session.userId,event.target,event.username,profile?.details?.pictureUrl?.value,profile?.details?.pictureId?.value)
                holder.name.text="@${event.username}";holder.body.text=HistoryViews.summary(this@TimelineActivity,event).take(3).joinToString("\n")
                holder.time.text=SimpleDateFormat("HH:mm",AppLanguage.locale(this@TimelineActivity)).format(Date(event.observedAt ?: event.analyzedAt))
                val isUnread=event.key !in readKeys;holder.state.visibility=if(notices)View.VISIBLE else View.GONE;holder.state.text=getString(if(isUnread)R.string.parity_unread else R.string.parity_read)
                card.background=AppUi.touch(this@TimelineActivity,AppUi.shape(this@TimelineActivity,if(notices && isUnread)AppUi.violetSoft else AppUi.surface,18,AppUi.line))
                card.contentDescription="@${event.username}. ${holder.body.text}. ${ObservationTimes.format(this@TimelineActivity,event.observedAt ?: event.analyzedAt)}"
                card.setOnClickListener {
                    HistoryViews.detail(this@TimelineActivity,event,images,saved.session.userId,profile)
                    if(notices && isUnread) {
                        val ticket=epoch
                        worker.execute {runCatching {vault.inspect {current -> if(current?.session==saved.session)vault.journal.markRead(saved.session,event.key)}}.onSuccess {
                            main.post {if(ticket==epoch && !isFinishing && !isDestroyed){readKeys=readKeys+event.key;renderRows()}}
                        }}
                    }
                };card
            }
        }
    }
    override fun onResume() {
        super.onResume()
        if(::vault.isInitialized) {listening=vault.journal.observe {main.removeCallbacks(reload);main.postDelayed(reload,120)};load()}
    }
    override fun onPause() {listening?.close();listening=null;epoch++;loading=false;main.removeCallbacks(reload);if(::images.isInitialized)images.cancelAll();super.onPause()}
    override fun onDestroy() {epoch++;worker.shutdownNow();images.close();main.removeCallbacksAndMessages(null);super.onDestroy()}
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState);outState.putString("target",target);outState.putString("type",type);outState.putBoolean("unread",unread)
        outState.putInt("event_limit",eventLimit);outState.putInt("check_limit",checkLimit);outState.putStringArrayList("expanded",ArrayList(expanded))
    }
}
