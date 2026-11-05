package dev.kaankaya.instagramtracker

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Month/day browsing and playback are local. Only the confirmed fetch action reads Instagram. */
class StoryArchiveActivity : LocalizedActivity() {
    private val main = Handler(Looper.getMainLooper())
    private fun executor(name: String) = ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,ArrayBlockingQueue(2),
        { task -> Thread(task,name) },ThreadPoolExecutor.AbortPolicy()).apply { allowCoreThreadTimeOut(true) }
    private val network = executor("tracker-story-fetch")
    private val local = executor("tracker-story-local")
    private lateinit var vault: SessionVault
    private lateinit var gate: RequestGate
    private var store: StoryArchiveStore? = null
    private var account: SavedAccount? = null
    private var profile: Profile? = null
    private var snapshot = StoryTarget()
    private var feed: StoryFeedSource? = null
    private var cdn: StoryMediaSource? = null
    private var pending: Future<*>? = null
    private var localPending: Future<*>? = null
    private var video: StoryVideoView? = null
    private var image: ImageView? = null
    private var fetching = false
    private var visible = false
    private var preview = false
    private var previewSeeded = false
    private var loading = true
    private var message: String? = null
    private var progressText = ""
    private var generation = 0L
    private var mediaGeneration = 0L
    private var selectedDay: String? = null
    private var selectedId: String? = null
    private var month = StoryCalendar.month(System.currentTimeMillis())
    private var renderedMedia: String? = null
    private var target = ""
    private lateinit var heading: TextView
    private lateinit var status: TextView
    private lateinit var check: android.widget.Button
    private lateinit var calendar: LinearLayout
    private lateinit var viewer: LinearLayout
    private lateinit var picker: LinearLayout
    private lateinit var mediaBox: LinearLayout
    private lateinit var metadata: LinearLayout
    private val utc = TimeZone.getTimeZone("UTC")
    private fun dp(value: Int) = AppUi.dp(this,value)
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun date(value: Long, pattern: String = "d MMM yyyy · HH:mm") =
        SimpleDateFormat(pattern,AppLanguage.locale(this)).apply { timeZone = utc }.format(Date(value))
    private fun gap(parent: LinearLayout, amount: Int = 12) { parent.addView(View(this),LinearLayout.LayoutParams(1,dp(amount))) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); AppUi.systemBars(this)
        if (!BuildConfig.DEBUG) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        preview = PreviewMode.enabled(this)
        target = intent.getStringExtra("target_id").orEmpty()
        val owner = intent.getStringExtra("viewer_id").orEmpty()
        if (!target.matches(Regex("[1-9][0-9]{0,29}")) || !owner.matches(Regex("[1-9][0-9]{0,29}"))) { finish(); return }
        vault = SessionVault(this,if (preview) "previewaccount" else "account",if (preview) null else owner)
        gate = RequestGate(this,if (preview) "preview_gate" else "request_gate").forAccount(owner)
        savedInstanceState?.getString("month")?.takeIf { runCatching { StoryCalendar.start(it) }.isSuccess }?.let { month = it }
        selectedDay = savedInstanceState?.getString("day")?.takeIf(StoryCalendar::validDay)
        selectedId = savedInstanceState?.getString("story")
        if (android.os.Build.VERSION.SDK_INT >= 33) onBackInvokedDispatcher.registerOnBackInvokedCallback(0) { finish() }
        build(); render()
    }
    private fun build() {
        val page = column().apply { setPadding(dp(20),dp(14),dp(20),dp(24)); setBackgroundColor(AppUi.background) }
        val scroll = ScrollView(this).apply { addView(page) }; setContentView(scroll)
        scroll.setOnApplyWindowInsetsListener { view,insets ->
            @Suppress("DEPRECATION")
            view.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom)
            insets
        }; scroll.requestApplyInsets()
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(AppUi.iconButton(this,"back",getString(R.string.list_back)) { finish() },LinearLayout.LayoutParams(dp(48),dp(48)))
        header.addView(AppUi.heading(this,getString(R.string.story_title),24f),LinearLayout.LayoutParams(0,-2,1f))
        header.addView(AppUi.iconButton(this,"info",getString(R.string.story_info)) { info() },LinearLayout.LayoutParams(dp(48),dp(48)))
        page.addView(header)
        heading = AppUi.text(this,"",16f,AppUi.ink,true); page.addView(heading); gap(page,6)
        page.addView(AppUi.text(this,getString(R.string.story_experimental),11f,AppUi.violet)); gap(page)
        check = AppUi.button(this,getString(if (preview) R.string.story_demo_load else R.string.story_check),true) { confirmFetch() }
        page.addView(check); gap(page,8)
        status = AppUi.text(this,"",13f,AppUi.muted); page.addView(status); gap(page)
        val body = LinearLayout(this).apply { orientation = if (resources.configuration.screenWidthDp >= 720) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }
        calendar = column(); viewer = column(); picker = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        mediaBox = column(); metadata = column()
        viewer.addView(picker); gap(viewer); viewer.addView(mediaBox); gap(viewer); viewer.addView(metadata)
        if (body.orientation == LinearLayout.HORIZONTAL) {
            body.addView(calendar,LinearLayout.LayoutParams(dp(300),-2).apply { marginEnd = dp(20) })
            body.addView(viewer,LinearLayout.LayoutParams(0,-2,1f))
        } else { body.addView(calendar); gap(body); body.addView(viewer) }
        page.addView(body); gap(page)
        page.addView(AppUi.button(this,getString(R.string.story_clear),false) { confirmClear() })
    }
    override fun onResume() {
        super.onResume(); visible = true
        if (!::vault.isInitialized || recreatingForInterface) return
        load()
    }
    private fun load() {
        val ticket = ++generation
        localPending?.cancel(true); local.purge()
        loading = true; render()
        try { localPending = local.submit {
            val result = runCatching {
                val saved = vault.read() ?: throw ApiFailure(ApiFailure.Kind.SESSION_EXPIRED)
                if (saved.session.userId != intent.getStringExtra("viewer_id")) throw ApiFailure(ApiFailure.Kind.WRONG_ACCOUNT)
                val selected = (listOf(saved.profile)+saved.people.map { it.latest }).firstOrNull { it.userId == target }
                    ?: throw ApiFailure(ApiFailure.Kind.CANCELLED)
                val archive = vault.storyStore(saved.session)
                if (preview && !previewSeeded) PreviewMode.seedStories(this,true,saved,archive,target)
                Triple(saved,selected,archive) to archive.read(target)
            }
            main.post {
                if (!visible || isDestroyed || ticket != generation) return@post
                loading = false
                result.onSuccess { (context,data) ->
                    if (!preview && AccountCatalog.selected(this) != context.first.session.userId) { finish(); return@onSuccess }
                    account = context.first; profile = context.second; store = context.third; snapshot = data; previewSeeded = preview
                    if (selectedDay == null && data.items.isNotEmpty()) month = data.items.maxOf { it.day }.take(7)
                    renderedMedia = null
                }.onFailure { message = error(it) }
                render()
            }
        } } catch (_: RejectedExecutionException) { loading = false; message = getString(R.string.story_local_busy); render() }
    }
    private fun render() {
        if (!::heading.isInitialized || isFinishing || isDestroyed) return
        heading.text = profile?.let { "@${it.username}" } ?: getString(R.string.story_loading)
        check.text = getString(if (fetching) R.string.story_cancel else if (preview) R.string.story_demo_load else R.string.story_check)
        check.isEnabled = !loading && account != null && (fetching || preview || !gate.automationHeld() && !gate.requiresConnection() && gate.blockedUntil() <= gate.now())
        val hold = if (preview || loading || fetching) null else when {
            gate.blockedUntil() > gate.now() -> getString(R.string.list_pause,date(gate.blockedUntil()) + " UTC")
            gate.requiresConnection() -> ApiFailure(ApiFailure.Kind.SESSION_EXPIRED).userMessage(this)
            gate.automationHeld() -> getString(R.string.story_local_busy)
            else -> null
        }
        status.text = message ?: hold ?: if (fetching) progressText else snapshot.checkedAt?.let {
            getString(R.string.story_checked,date(it)) + (snapshot.summary?.let { summary -> "\n" + getString(R.string.story_summary,summary.saved,summary.existing,summary.metadata) } ?: "")
        } ?: if (loading) getString(R.string.story_loading) else getString(R.string.story_empty)
        renderCalendar(); renderViewer()
    }
    private fun renderCalendar() {
        calendar.removeAllViews()
        val narrow = resources.configuration.screenWidthDp < 720
        if (narrow && selectedDay != null) {
            calendar.addView(AppUi.button(this,getString(R.string.story_calendar_back),false) { selectedDay = null; selectedId = null; render() })
            return
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(AppUi.button(this,"‹",false,month != "1970-01") { month = StoryCalendar.shift(month,-1); render() }
            .apply { contentDescription = getString(R.string.story_previous_month) },LinearLayout.LayoutParams(dp(48),-2))
        header.addView(AppUi.text(this,date(StoryCalendar.start(month).timeInMillis,"LLLL yyyy"),16f,AppUi.ink,true)
            .apply { gravity = Gravity.CENTER },LinearLayout.LayoutParams(0,-2,1f))
        header.addView(AppUi.button(this,"›",false,month != "9999-12") { month = StoryCalendar.shift(month,1); render() }
            .apply { contentDescription = getString(R.string.story_next_month) },LinearLayout.LayoutParams(dp(48),-2))
        calendar.addView(header)
        val weekdays = LinearLayout(this)
        val names = DateFormatSymbols(AppLanguage.locale(this)).shortWeekdays
        (0..6).forEach { i -> weekdays.addView(AppUi.text(this,names[(i+1)%7+1],10f,AppUi.muted).apply { gravity = Gravity.CENTER },LinearLayout.LayoutParams(0,dp(32),1f)) }
        calendar.addView(weekdays)
        val counts = snapshot.items.groupingBy { it.day }.eachCount()
        StoryCalendar.days(month).chunked(7).forEach { week ->
            val row = LinearLayout(this)
            (0..6).forEach { i ->
                val day = week.getOrNull(i); val count = day?.let { counts[it] } ?: 0
                val cell = AppUi.text(this,day?.takeLast(2)?.trimStart('0').orEmpty() + if (count>0) "\n$count" else "",13f,
                    if (count>0) AppUi.violet else AppUi.muted,count>0).apply {
                    gravity = Gravity.CENTER; minHeight = dp(52)
                    if (day != null && count>0) {
                        isClickable = true; isFocusable = true
                        background = AppUi.touch(this@StoryArchiveActivity,AppUi.shape(this@StoryArchiveActivity,if (day == selectedDay) AppUi.violetSoft else AppUi.surface,12))
                        contentDescription = day + " · " + resources.getQuantityString(R.plurals.story_count,count,count)
                        setOnClickListener { selectedDay = day; selectedId = null; render() }
                    }
                }
                row.addView(cell,LinearLayout.LayoutParams(0,dp(54),1f))
            }
            calendar.addView(row)
        }
        calendar.addView(AppUi.text(this,getString(R.string.story_utc),10f,AppUi.muted))
        if (snapshot.items.isNotEmpty()) calendar.addView(AppUi.button(this,getString(R.string.story_latest),false) {
            selectedDay = snapshot.items.maxOf { it.day }; month = selectedDay!!.take(7); selectedId = null; render()
        })
    }
    private fun renderViewer() {
        val day = selectedDay
        viewer.visibility = if (day == null || loading) View.GONE else View.VISIBLE
        if (day == null || loading) { releaseMedia(); renderedMedia = null; return }
        val rows = snapshot.items.filter { it.day == day }.sortedBy { it.facts.publishedAt ?: it.capturedAt }
        val row = rows.firstOrNull { it.facts.id == selectedId } ?: rows.firstOrNull { it.saved } ?: rows.firstOrNull()
        picker.removeAllViews(); metadata.removeAllViews()
        if (row == null) { releaseMedia(); renderedMedia = null; return }
        selectedId = row.facts.id; val position = rows.indexOf(row)
        picker.addView(AppUi.button(this,"‹",false,position>0) { selectedId = rows[position-1].facts.id; render() }
            .apply { contentDescription = getString(R.string.story_previous) },LinearLayout.LayoutParams(dp(48),-2))
        picker.addView(AppUi.text(this,getString(R.string.story_position,position+1,rows.size),14f,AppUi.ink,true).apply {
            gravity = Gravity.CENTER; isClickable = true; isFocusable = true
            setOnClickListener { AlertDialog.Builder(this@StoryArchiveActivity).setTitle(R.string.story_choose)
                .setSingleChoiceItems(rows.map { date(it.facts.publishedAt ?: it.capturedAt,"HH:mm") + " · " + kind(it.facts.kind) }.toTypedArray(),position) { dialog,index ->
                    dialog.dismiss(); selectedId = rows[index].facts.id; render()
                }.setNegativeButton(R.string.ui_close,null).show() }
        },LinearLayout.LayoutParams(0,-2,1f))
        picker.addView(AppUi.button(this,"›",false,position+1<rows.size) { selectedId = rows[position+1].facts.id; render() }
            .apply { contentDescription = getString(R.string.story_next) },LinearLayout.LayoutParams(dp(48),-2))
        metadata.addView(AppUi.heading(this,date(row.facts.publishedAt ?: row.capturedAt),18f)); gap(metadata,6)
        metadata.addView(AppUi.text(this,getString(R.string.story_captured,date(row.capturedAt)),11f,AppUi.muted)); gap(metadata)
        row.facts.mentions?.takeIf { it.isNotEmpty() }?.let { values ->
            links(getString(R.string.story_mentions,values.size),values.map { "@${it.username}" to ProfileLinks.url(it.username) })
        }
        row.facts.links?.takeIf { it.isNotEmpty() }?.let { values ->
            links(getString(R.string.story_links,values.size),values.map { ProfileLinks.brand(it).first to it })
        }
        row.facts.shared?.takeIf { it.isNotEmpty() }?.let { values ->
            links(getString(R.string.story_shared,values.size),values.map { item ->
                kind(if (item.type == "clips") StoryKind.VIDEO else StoryKind.IMAGE) to item.code?.takeIf { item.type in setOf("feed","clips") }
                    ?.let { "https://www.instagram.com/" + (if (item.type == "clips") "reel/" else "p/") + "$it/" }
            })
        }
        row.facts.hashtags?.takeIf { it.isNotEmpty() }?.let { values -> textSection(getString(R.string.story_hashtags),values.map { "#$it" }) }
        row.facts.locations?.takeIf { it.isNotEmpty() }?.let { textSection(getString(R.string.story_locations),it) }
        if (row.facts.incomplete.isNotEmpty()) metadata.addView(AppUi.text(this,getString(R.string.story_partial_metadata),11f,AppUi.muted))
        val key = "${row.facts.id}/${row.digest}/${row.saved}"
        if (renderedMedia == key) return
        releaseMedia(); renderedMedia = key; mediaBox.removeAllViews()
        if (!row.saved) { mediaBox.addView(AppUi.notice(this,getString(R.string.story_metadata_only),getString(R.string.story_media_missing))); return }
        mediaBox.addView(AppUi.text(this,getString(R.string.story_loading),13f,AppUi.muted))
        val ticket = ++mediaGeneration; val archive = store ?: return
        localPending?.cancel(true); local.purge()
        try { localPending = local.submit {
            val result = runCatching { val (item,bytes) = archive.media(target,row.facts.id)
                item to if (item.facts.kind == StoryKind.IMAGE) StoryMedia.bitmap(bytes) else bytes }
            main.post {
                if (!visible || isDestroyed || ticket != mediaGeneration || renderedMedia != key) {
                    (result.getOrNull()?.second as? android.graphics.Bitmap)?.recycle(); return@post
                }
                mediaBox.removeAllViews()
                result.onSuccess { (item,data) ->
                    if (data is android.graphics.Bitmap) {
                        image = ImageView(this).apply { adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER; setImageBitmap(data); contentDescription = getString(R.string.story_image) }
                        mediaBox.addView(image,LinearLayout.LayoutParams(-1,-2))
                    } else if (item.facts.kind == StoryKind.VIDEO) {
                        runCatching { StoryVideoView(this,data as ByteArray) }.onSuccess {
                            video = it; mediaBox.addView(it)
                        }.onFailure { mediaBox.addView(AppUi.text(this,getString(R.string.story_video_error),13f,AppUi.muted)) }
                    }
                }.onFailure { mediaBox.addView(AppUi.notice(this,getString(R.string.story_media_error),error(it),true)) }
            }
        } } catch (_: RejectedExecutionException) { mediaBox.removeAllViews(); mediaBox.addView(AppUi.text(this,getString(R.string.story_local_busy),13f,AppUi.muted)); renderedMedia = null }
    }
    private fun kind(value: StoryKind) = getString(when (value) { StoryKind.IMAGE -> R.string.story_image; StoryKind.VIDEO -> R.string.story_video; else -> R.string.story_other })
    private fun links(title: String, values: List<Pair<String,String?>>) {
        metadata.addView(AppUi.button(this,title,false) { AlertDialog.Builder(this).setTitle(title)
            .setItems(values.map { it.first }.toTypedArray()) { _,index -> values[index].second?.let(::external) }
            .setNegativeButton(R.string.ui_close,null).show() })
    }
    private fun textSection(title: String, values: List<String>) {
        metadata.addView(AppUi.button(this,"$title · ${values.size}",false) { AlertDialog.Builder(this).setTitle(title)
            .setItems(values.toTypedArray(),null).setPositiveButton(R.string.ui_close,null).show() })
    }
    private fun external(value: String) {
        val safe = ProfileLinks.webUrl(value) ?: return
        try { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(safe))) } catch (_: android.content.ActivityNotFoundException) { message = getString(R.string.story_link_unavailable); render() }
    }
    private fun confirmFetch() {
        if (fetching) { cancelFetch(); message = getString(R.string.story_cancelled); render(); return }
        if (preview) { previewSeeded = false; load(); return }
        AlertDialog.Builder(this).setTitle(R.string.story_check).setMessage(R.string.story_scope)
            .setPositiveButton(R.string.story_check) { _,_ -> fetch() }.setNegativeButton(R.string.ui_cancel,null).show()
    }
    private fun fetch() {
        val saved = account ?: return; val archive = store ?: return
        val ticket = ++generation
        fetching = true; message = null; progressText = getString(R.string.story_fetching); render()
        val operationFeed = AccountStorySource(gate); val operationCdn = StoryCdnSource(gate)
        feed = operationFeed; cdn = operationCdn
        try { pending = network.submit {
            val result = runCatching { StoryFetch(vault,archive,gate,operationFeed,operationCdn).read(saved.session,target,
                { ticket != generation || Thread.currentThread().isInterrupted }) { done,total ->
                    val data = archive.read(target)
                    main.post { if (visible && ticket == generation) { snapshot = data; progressText = getString(R.string.story_progress,done,total); render() } }
                } }
            val data = runCatching { archive.read(target) }.getOrNull()
            main.post {
                if (!visible || isDestroyed || ticket != generation) return@post
                fetching = false; data?.let { snapshot = it }
                result.onSuccess { message = getString(if (it.returned == 0) R.string.story_no_accessible else R.string.story_done) }
                    .onFailure { message = error(it) }
                render()
            }
        } } catch (_: RejectedExecutionException) { fetching = false; message = getString(R.string.story_local_busy); render() }
    }
    private fun confirmClear() {
        if (fetching || loading) return
        AlertDialog.Builder(this).setTitle(R.string.story_clear).setMessage(R.string.story_clear_body)
            .setPositiveButton(R.string.story_clear) { _,_ ->
                releaseMedia(); renderedMedia = null
                val saved = account ?: return@setPositiveButton; val archive = store ?: return@setPositiveButton
                val ticket = ++generation; loading = true; render()
                localPending?.cancel(true); local.purge()
                try { localPending = local.submit {
                    val result = runCatching { vault.inspect { if (it?.session != saved.session) throw ApiFailure(ApiFailure.Kind.CANCELLED); archive.clearTarget(target) } }
                    main.post { if (visible && ticket == generation) { loading = false; result.onSuccess { snapshot = StoryTarget(); selectedDay = null; selectedId = null; message = null }.onFailure { message = error(it) }; render() } }
                } } catch (_: RejectedExecutionException) { loading = false; message = getString(R.string.story_local_busy); render() }
            }.setNegativeButton(R.string.ui_cancel,null).show()
    }
    private fun info() = AlertDialog.Builder(this).setTitle(R.string.story_info).setMessage(getString(R.string.story_scope) + "\n\n" +
        getString(R.string.story_limits,StoryLimits.MAX_ITEMS,StoryLimits.MAX_MEDIA/1024/1024,StoryLimits.MAX_BYTES/1024/1024))
        .setPositiveButton(R.string.ui_ok,null).show()
    private fun error(failure: Throwable) = when (failure) {
        is ApiFailure -> if (failure.kind == ApiFailure.Kind.CAPACITY) getString(R.string.story_capacity) else failure.userMessage(this)
        else -> ApiFailure(ApiFailure.Kind.STORAGE).userMessage(this)
    }
    private fun cancelFetch() { generation++; feed?.cancel(); cdn?.cancel(); pending?.cancel(true); network.purge(); fetching = false }
    private fun releaseMedia() {
        mediaGeneration++; localPending?.cancel(true); local.purge()
        video?.close(); video = null; image?.setImageDrawable(null); image = null
    }
    override fun onPause() { visible = false; cancelFetch(); releaseMedia(); renderedMedia = null; super.onPause() }
    override fun onDestroy() { cancelFetch(); releaseMedia(); main.removeCallbacksAndMessages(null); network.shutdownNow(); local.shutdownNow(); super.onDestroy() }
    override fun onSaveInstanceState(out: Bundle) { out.putString("month",month); out.putString("day",selectedDay); out.putString("story",selectedId); super.onSaveInstanceState(out) }
}
