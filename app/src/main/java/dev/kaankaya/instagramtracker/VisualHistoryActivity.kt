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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); AppUi.systemBars(this)
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
    private fun diff(before: String, after: String): CharSequence {
        val text = SpannableStringBuilder()
        BiographyDiff.compare(before,after).forEach { part ->
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
    private fun change(action: () -> Unit) { try { action(); error=false } catch (_: Exception) { error=true }; render() }
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
        page.addView(AppUi.heading(this,getString(R.string.visual_avatars),22f))
        val images=data.getJSONObject("images")
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
    override fun onDestroy() { loader.close(); super.onDestroy() }
}
