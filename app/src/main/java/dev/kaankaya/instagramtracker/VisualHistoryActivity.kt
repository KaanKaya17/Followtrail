package dev.kaankaya.instagramtracker

import android.app.AlertDialog
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import org.json.JSONObject

/** Local-only encrypted album and biography diff. Opening it sends no account API requests. */
class VisualHistoryActivity : LocalizedActivity() {
    private lateinit var vault: SessionVault
    private lateinit var loader: AvatarImages
    private var reference: Session? = null
    private var selected = ""
    private var error = false
    private var preview = false
    private var compareBefore = 0L
    private var compareAfter = 0L
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); AppUi.systemBars(this)
        compareBefore = savedInstanceState?.getLong("compare_before") ?: 0L
        compareAfter = savedInstanceState?.getLong("compare_after") ?: 0L
        if (!BuildConfig.DEBUG) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        preview = PreviewMode.enabled(this)
        vault = SessionVault(this, if (preview) "previewaccount" else "account")
        loader = AvatarImages(this, PreviewMode.avatarFetcher(preview), if (preview) "previewavatars" else "avatars")
        selected = intent.getStringExtra("target_id").orEmpty()
        val saved = try { vault.read() } catch (_: Exception) { null }
        if (saved == null || intent.getStringExtra("viewer_id") != saved.session.userId ||
            (saved.profile.userId != selected && saved.people.none { it.latest.userId == selected })) { finish(); return }
        reference = saved.session
        render()
    }
    private fun date(at: Long) = ObservationTimes.format(this,at)
    private fun diff(before: String, after: String) = formatDiff(BiographyDiff.compare(before, after))
    private fun formatDiff(parts: List<BiographyDiff.Part>): CharSequence {
        val text = SpannableStringBuilder()
        parts.forEach { part ->
            val start=text.length
            text.append(if (part.kind=="add") "+${part.text}" else if (part.kind=="remove") "−${part.text}" else part.text)
            if (part.kind!="same") {
                text.setSpan(BackgroundColorSpan(if (part.kind=="add") UiPalette.addedBg else UiPalette.removedBg),start,text.length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                text.setSpan(ForegroundColorSpan(if (part.kind=="add") UiPalette.addedText else UiPalette.removedText),start,text.length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (part.kind=="remove") text.setSpan(StrikethroughSpan(),start,text.length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return text
    }
    private fun comparison(container: LinearLayout, records: List<ProfileComparisonRecord>, images: JSONObject) {
        container.removeAllViews()
        if (records.size < 2) {
            container.addView(AppUi.text(this, getString(R.string.profile_compare_unavailable), 13f, AppUi.muted))
            return
        }
        val after = records.firstOrNull { it.at == compareAfter && it.at > records.first().at } ?: records.last()
        val before = records.firstOrNull { it.at == compareBefore && it.at < after.at } ?: records.last { it.at < after.at }
        compareBefore = before.at
        compareAfter = after.at

        fun pair(): LinearLayout = LinearLayout(this).also { container.addView(it) }
        fun cell(row: LinearLayout, second: Boolean): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val margin = if (second) AppUi.dp(this@VisualHistoryActivity, 8) else 0
            row.addView(this, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = margin })
        }
        fun picker(row: LinearLayout, second: Boolean, label: Int, current: Long, options: List<ProfileComparisonRecord>) {
            val column = cell(row, second)
            column.addView(AppUi.text(this, getString(label), 12f, AppUi.muted))
            column.addView(AppUi.button(this, date(current), false) {
                val candidates = options.asReversed()
                AlertDialog.Builder(this).setTitle(label)
                    .setSingleChoiceItems(candidates.map { date(it.at) }.toTypedArray(), candidates.indexOfFirst { it.at == current }) { dialog, index ->
                        if (second) compareAfter = candidates[index].at else compareBefore = candidates[index].at
                        dialog.dismiss()
                        comparison(container, records, images)
                    }.setNegativeButton(R.string.ui_cancel, null).show()
            })
        }
        val pickers = pair()
        picker(pickers, false, R.string.profile_compare_before, before.at, records.filter { it.at < after.at })
        picker(pickers, true, R.string.profile_compare_after, after.at, records.filter { it.at > before.at })

        val number = java.text.NumberFormat.getIntegerInstance(AppLanguage.locale(this))
        val missing = getString(R.string.profile_compare_missing)
        val empty = getString(R.string.profile_compare_empty)
        fun cells(label: Int): Pair<LinearLayout, LinearLayout> {
            val title = AppUi.text(this, getString(label), 13f, AppUi.muted, true)
            title.setPadding(0, AppUi.dp(this, 10), 0, AppUi.dp(this, 4))
            container.addView(title)
            val row = pair()
            val left = cell(row, false); val right = cell(row, true)
            for (column in listOf(left, right)) {
                val padding = AppUi.dp(this, 9)
                column.setPadding(padding, padding, padding, padding)
                column.background = AppUi.shape(this, AppUi.surface, 12, AppUi.line)
            }
            return left to right
        }
        fun show(column: LinearLayout, value: CharSequence) {
            column.addView(AppUi.text(this, "", 13f).apply {
                text = value
                setTextIsSelectable(true)
            })
        }
        fun plain(value: String?): CharSequence = value?.ifEmpty { empty } ?: missing
        fun textField(label: Int, a: String?, b: String?) {
            val (left, right) = cells(label)
            val changed = a != null && b != null && a != b
            show(left, if (changed) formatDiff(listOf(BiographyDiff.Part("remove", plain(a).toString()))) else plain(a))
            show(right, if (changed) formatDiff(listOf(BiographyDiff.Part("add", plain(b).toString()))) else plain(b))
        }
        val (olderPhoto, newerPhoto) = cells(R.string.profile_compare_photo)
        val changedPicture = if (before.picture != null && after.picture != null) before.picture != after.picture
            else before.image != null && after.image != null && before.image != after.image
        for ((side, record, kind) in listOf(Triple(olderPhoto, before, "remove"), Triple(newerPhoto, after, "add"))) {
            val raw = record.image?.let { key -> runCatching { Base64.decode(images.getString(key), Base64.NO_WRAP) }.getOrNull() }
            val bitmap = raw?.takeIf { it.size <= 64 * 1024 }?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            if (bitmap != null) {
                side.addView(ImageView(this).apply {
                    setImageBitmap(bitmap)
                    contentDescription = getString(R.string.visual_photo)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }, LinearLayout.LayoutParams(AppUi.dp(this, 54), AppUi.dp(this, 54)))
            } else {
                show(side, if (record.picture == "anonymous") getString(R.string.profile_compare_default_photo) else missing)
            }
            if (changedPicture) show(side, formatDiff(listOf(BiographyDiff.Part(kind, getString(R.string.profile_compare_photo_changed)))))
        }
        textField(R.string.profile_compare_username, before.username?.let { "@$it" }, after.username?.let { "@$it" })
        textField(R.string.profile_compare_name, before.name, after.name)
        val (olderBio, newerBio) = cells(R.string.profile_compare_bio)
        val parts = if (before.bio != null && after.bio != null) BiographyDiff.compare(before.bio, after.bio) else null
        show(olderBio, parts?.let { formatDiff(it.filterNot { part -> part.kind == "add" }) }?.takeIf { it.isNotEmpty() } ?: plain(before.bio))
        show(newerBio, parts?.let { formatDiff(it.filterNot { part -> part.kind == "remove" }) }?.takeIf { it.isNotEmpty() } ?: plain(after.bio))
        textField(R.string.profile_compare_links, before.links?.joinToString("\n") { it.url }, after.links?.joinToString("\n") { it.url })
        textField(R.string.list_followers, before.followers?.let(number::format), after.followers?.let(number::format))
        textField(R.string.list_following, before.following?.let(number::format), after.following?.let(number::format))
        textField(R.string.profile_compare_posts, before.posts?.let(number::format), after.posts?.let(number::format))
    }
    private fun change(action: () -> Unit) {
        try {
            vault.inspect {current->
                if(current==null || current.session!=reference)throw ApiFailure(ApiFailure.Kind.CANCELLED)
                action()
            }
            error=false
        }catch (_: Exception) {error=true}
        render()
    }
    private fun render() {
        val saved=try { vault.read() } catch (_: Exception) { null }
        if (saved==null || saved.session!=reference) { finish(); return }
        val profile=if (saved.profile.userId==selected) saved.profile else saved.people.firstOrNull { it.latest.userId==selected }?.latest
        if (profile==null) { finish();return }
        val data=try { vault.visual.read(saved.session.userId) } catch (_: Exception) { error=true; VisualHistory.empty(saved.session.userId) }
        val record=data.getJSONObject("profiles").optJSONObject(selected)
        val enabled=record?.optBoolean("enabled")==true
        val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(AppUi.dp(this@VisualHistoryActivity,20),AppUi.dp(this@VisualHistoryActivity,20),AppUi.dp(this@VisualHistoryActivity,20),AppUi.dp(this@VisualHistoryActivity,30)) }
        val scroll=ScrollView(this).apply { addView(page) }; setContentView(scroll)
        scroll.setOnApplyWindowInsetsListener { view,insets ->
            @Suppress("DEPRECATION")
            view.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom)
            insets
        }; scroll.requestApplyInsets()
        page.addView(AppUi.button(this,getString(R.string.visual_back),false) { finish() })
        page.addView(AppUi.heading(this,getString(R.string.visual_title),26f))
        page.addView(AppUi.text(this,"@${profile.username}",16f,AppUi.violet))
        page.addView(AppUi.text(this,getString(R.string.visual_scope),13f,AppUi.muted))
        page.addView(AppUi.button(this,getString(if (enabled) R.string.visual_stop else R.string.visual_enable),true) {
            change { vault.visual.configure(saved,selected,!enabled) }
            if (!enabled) profile.details.pictureUrl.value?.let { url ->
                loader.load(AvatarSource(saved.session.userId,selected,url,profile.details.pictureId.value)) { render() }
            }
        })
        page.addView(AppUi.button(this,getString(R.string.visual_clear),false) {
            AlertDialog.Builder(this).setTitle(R.string.visual_clear).setMessage(R.string.visual_clear_hint)
                .setNegativeButton(R.string.ui_cancel,null).setPositiveButton(R.string.visual_clear_confirm) { _,_ -> change { vault.visual.clear(saved.session.userId,selected) } }.show()
        })
        if (error || VisualHistoryStore.writeFailed) page.addView(AppUi.text(this,getString(R.string.visual_failed),14f,AppUi.coral))
        if (record?.optBoolean("full")==true) page.addView(AppUi.text(this,getString(R.string.visual_full),14f,AppUi.coral))
        val entries=record?.getJSONArray("entries")
        val rows=if (entries==null) emptyList() else (0 until entries.length()).map(entries::getJSONObject)
        val images = data.getJSONObject("images")
        val own = selected == saved.profile.userId
        val history = saved.people.firstOrNull { it.latest.userId == selected }?.history ?: listOf(profile)
        val checks = if (own) runCatching { vault.journal.read(saved.session).filter { it.target == selected } }.getOrDefault(emptyList()) else emptyList()
        page.addView(AppUi.heading(this, getString(R.string.profile_compare_title), 22f))
        page.addView(AppUi.text(this, getString(R.string.visual_legend), 13f, AppUi.muted))
        val comparisonView = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(comparisonView)
        comparison(comparisonView, ProfileComparison.records(history, rows, checks), images)
        page.addView(AppUi.heading(this,getString(R.string.visual_avatars),22f))
        val groups=rows.filter { it.has("image") }.groupBy { it.getString("image") }
        if (groups.isEmpty()) page.addView(AppUi.text(this,getString(R.string.visual_no_photos),13f,AppUi.muted))
        var bytes=0
        groups.forEach { (key, observations) ->
            val raw=try { Base64.decode(images.getString(key),Base64.NO_WRAP) } catch (_: Exception) { null }
            if (raw!=null && raw.size<=64*1024) {
                bytes+=raw.size
                val bitmap=BitmapFactory.decodeByteArray(raw,0,raw.size)
                val card=AppUi.card(this)
                card.addView(ImageView(this).apply { setImageBitmap(bitmap); contentDescription=getString(R.string.visual_photo); adjustViewBounds=true; maxHeight=AppUi.dp(this@VisualHistoryActivity,180) },LinearLayout.LayoutParams(-1,AppUi.dp(this,180)))
                card.addView(AppUi.text(this,getString(R.string.visual_first_last,date(observations.first().getLong("at")),date(observations.last().getLong("lastSeen"))),12f,AppUi.muted))
                page.addView(card)
            }
        }
        page.addView(AppUi.text(this,getString(R.string.visual_size,groups.size,(bytes+1023)/1024),12f,AppUi.muted))
        page.addView(AppUi.heading(this,getString(R.string.visual_bios),22f))
        page.addView(AppUi.text(this,getString(R.string.visual_legend),13f,AppUi.muted))
        val edits=rows.mapIndexedNotNull { index, entry ->
            val old=rows.getOrNull(index-1)
            if (!entry.has("bio") || (old?.has("bio")==true && old.getString("bio")==entry.getString("bio"))) null else entry to old
        }
        edits.reversed().forEach { (entry,old) ->
            val card=AppUi.card(this)
            card.addView(AppUi.text(this,date(entry.getLong("at")),12f,AppUi.muted))
            val text=entry.getString("bio")
            card.addView(AppUi.text(this,"",15f).apply { setText(if (old?.has("bio")==true) diff(old.getString("bio"),text) else text.ifEmpty { getString(R.string.profile_empty_bio) }); setTextIsSelectable(true) })
            if (old?.has("bio")!=true) card.addView(AppUi.text(this,getString(R.string.visual_baseline),12f,AppUi.muted))
            page.addView(card)
        }
        if (edits.isEmpty()) page.addView(AppUi.text(this,getString(R.string.visual_empty),13f,AppUi.muted))
        page.addView(AppUi.text(this,getString(R.string.visual_limits,VisualHistory.MAX_ENTRIES,VisualHistory.MAX_IMAGES),12f,AppUi.muted))
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("compare_before", compareBefore)
        outState.putLong("compare_after", compareAfter)
        super.onSaveInstanceState(outState)
    }
    override fun onResume() {super.onResume();if(reference!=null)render()}
    override fun onDestroy() { loader.close(); super.onDestroy() }
}
