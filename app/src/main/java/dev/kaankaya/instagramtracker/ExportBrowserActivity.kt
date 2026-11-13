package dev.kaankaya.instagramtracker

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.ScrollView
import android.widget.TextView
import android.widget.VideoView
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class ExportBrowserActivity : LocalizedActivity() {
    private val reader = Executors.newSingleThreadExecutor()
    private var archive: ExportBrowser? = null
    private var archiveCopy: File? = null
    private var selectedUri: Uri? = null
    private var previewFile: File? = null
    private var player: VideoView? = null
    private var selected: ExportBrowser.Entry? = null
    private var pending = false
    private var category = ""
    private var query = ""
    private var visible = 40
    private lateinit var page: LinearLayout
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppUi.systemBars(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        directory()
    }

    override fun onDestroy() {
        releasePreview()
        // Serialize cleanup behind any active archive read.
        reader.execute {
            archive?.close()
            archiveCopy?.delete()
            archive = null
        }
        reader.shutdown()
        super.onDestroy()
    }

    private fun releasePreview() {
        player?.stopPlayback()
        player = null
        previewFile?.delete()
        previewFile = null
    }

    private fun base() {
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = AppUi.dp(this@ExportBrowserActivity, 16)
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(AppUi.background)
        }
        status = AppUi.text(this, "", 14f, AppUi.muted)
        status.accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
        setContentView(ScrollView(this).apply { addView(page) })
    }

    private fun action(label: Int, primary: Boolean = false, block: () -> Unit) {
        page.addView(AppUi.button(this, getString(label), primary) { if (!pending) block() })
    }

    private fun directory() {
        releasePreview()
        selected = null
        base()
        action(R.string.ui_close) { finish() }
        page.addView(AppUi.heading(this, getString(R.string.export_browser_title), 26f))
        page.addView(AppUi.text(this, getString(R.string.export_browser_intro), 14f, AppUi.muted))
        action(R.string.instagram_archive_open, true, ::pick)
        page.addView(status)
        val files = archive?.files ?: return
        val categories = files.groupingBy { group(it) }.eachCount()
        val categoryKeys = listOf("") + categories.keys
        page.addView(AppUi.button(this, categoryLabel(category, categories[category]), false) {
            if (!pending) AlertDialog.Builder(this).setTitle(R.string.export_browser_category)
                .setSingleChoiceItems(categoryKeys.map { categoryLabel(it, categories[it]) }.toTypedArray(),
                    categoryKeys.indexOf(category)) { dialog, index ->
                    dialog.dismiss()
                    category = categoryKeys[index]
                    visible = 40
                    directory()
                }.show()
        })
        val search = EditText(this).apply {
            setSingleLine()
            setHint(R.string.export_browser_search)
            setText(query)
        }
        page.addView(search)
        val result = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(result)
        fun update() {
            val needle = query.trim().lowercase(Locale.ROOT)
            val matches = files.filter {
                (category.isEmpty() || group(it) == category) &&
                    (needle in it.name.lowercase(Locale.ROOT) || needle in pretty(it.name).lowercase(Locale.ROOT))
            }
            result.removeAllViews()
            result.addView(AppUi.text(this, getString(R.string.export_browser_files, minOf(visible, matches.size), matches.size), 13f, AppUi.muted))
            for (file in matches.take(visible)) {
                val item = AppUi.card(this)
                item.addView(AppUi.heading(this, pretty(file.name.substringAfterLast('/')), 15f))
                item.addView(AppUi.text(this, file.name, 12f, AppUi.muted))
                item.setOnClickListener { if (!pending) detail(file) }
                item.isFocusable = true
                result.addView(item)
            }
            if (visible < matches.size) result.addView(AppUi.button(this, getString(R.string.export_browser_more), false) {
                visible += 40
                update()
            })
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun afterTextChanged(s: Editable?) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                query = s.toString()
                visible = 40
                update()
            }
        })
        update()
    }

    private fun group(file: ExportBrowser.Entry) = file.name.substringBefore('/')
    private fun pretty(value: String) = value.replace('_', ' ')

    private fun categoryLabel(key: String, count: Int?): String {
        val label = when (key) {
            "" -> getString(R.string.export_browser_all)
            "connections" -> getString(R.string.export_browser_connections)
            "your_instagram_activity" -> getString(R.string.export_browser_activity)
            "personal_information" -> getString(R.string.export_browser_personal)
            "logged_information" -> getString(R.string.export_browser_insights)
            "ads_information" -> getString(R.string.export_browser_ads)
            "security_and_login_information" -> getString(R.string.export_browser_security)
            "preferences" -> getString(R.string.export_browser_preferences)
            "apps_and_websites_off_of_instagram" -> getString(R.string.export_browser_apps)
            "media" -> getString(R.string.export_browser_media)
            else -> pretty(key)
        }
        return if (count == null) label else "$label · $count"
    }

    @Suppress("DEPRECATION")
    private fun pick() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
        }
        try { startActivityForResult(intent, 21) }
        catch (_: android.content.ActivityNotFoundException) { status.setText(R.string.export_browser_error) }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != Activity.RESULT_OK || pending) return
        if (requestCode == 21) {
            work(R.string.instagram_archive_loading, {
                val opened = open(uri)
                archive?.close()
                archiveCopy?.delete()
                archive = opened.first
                archiveCopy = opened.second
                selectedUri = uri
            }) {
                category = ""
                query = ""
                visible = 40
                directory()
            }
        } else if (requestCode == 22) {
            val file = selected ?: return
            if (uri == selectedUri) {
                status.setText(R.string.export_browser_error)
                return
            }
            work(R.string.export_browser_loading, {
                val temporary = File.createTempFile("export-entry-", ".tmp", cacheDir)
                try {
                    temporary.outputStream().use { archive!!.copy(file, it) }
                    requireNotNull(contentResolver.openOutputStream(uri, "wt")).use { output ->
                        temporary.inputStream().use { it.copyTo(output) }
                    }
                } finally {
                    temporary.delete()
                }
            }) { status.setText(R.string.export_browser_saved) }
        }
    }

    private fun open(uri: Uri): Pair<ExportBrowser, File?> {
        contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            if (descriptor.statSize > 0) {
                val direct = runCatching {
                    ExportBrowser.open(File("/proc/self/fd/${descriptor.fd}"), descriptor.statSize)
                }.getOrNull()
                if (direct != null) return direct to null
            }
        }
        val temporary = File.createTempFile("instagram-full-export-", ".zip", cacheDir)
        try {
            requireNotNull(contentResolver.openInputStream(uri)).use { ExportBrowser.spool(it, temporary) }
            return ExportBrowser.open(temporary) to temporary
        } catch (failure: Exception) {
            temporary.delete()
            throw failure
        }
    }

    private fun <T> work(label: Int, task: () -> T, discard: (T) -> Unit = {}, success: (T) -> Unit) {
        pending = true
        status.setText(label)
        reader.execute {
            val result = runCatching(task)
            runOnUiThread {
                if (isFinishing || isDestroyed) {
                    result.onSuccess(discard)
                    return@runOnUiThread
                }
                pending = false
                result.fold(onSuccess = {
                    status.text = ""
                    success(it)
                }, onFailure = { status.setText(R.string.export_browser_error) })
            }
        }
    }

    private fun detail(file: ExportBrowser.Entry) {
        releasePreview()
        selected = file
        base()
        action(R.string.export_browser_back, false, ::directory)
        page.addView(AppUi.heading(this, pretty(file.name.substringAfterLast('/'))))
        page.addView(AppUi.text(this, file.name, 13f, AppUi.muted))
        page.addView(AppUi.text(this, android.text.format.Formatter.formatFileSize(this, file.size), 13f, AppUi.muted))
        page.addView(status)
        if (file.size in 0..ExportBrowser.MAX_COPY) action(R.string.export_browser_save) { save(file) }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(content)
        when {
            file.kind in setOf("json", "text") && file.size <= ExportBrowser.MAX_TEXT ->
                showText(file, content, 0, listOf(0))
            file.kind in setOf("image", "video", "audio") && file.size <= ExportBrowser.MAX_PREVIEW ->
                action(R.string.export_browser_preview) { showMedia(file, content) }
            else -> content.addView(AppUi.text(this, getString(R.string.export_browser_no_preview), 14f, AppUi.muted))
        }
    }

    private fun showText(file: ExportBrowser.Entry, target: LinearLayout, record: Int, offsets: List<Int>, metadata: Boolean = false) {
        work(R.string.export_browser_reading, { archive!!.text(file, record, offsets.last(), metadata) }) { result ->
            target.removeAllViews()
            fun control(label: String, action: () -> Unit) {
                target.addView(AppUi.button(this, label, false) { if (!pending) action() })
            }
            if (result.section.isNotEmpty()) target.addView(AppUi.text(this, pretty(result.section), 13f, AppUi.muted))
            if (result.hasMetadata) {
                val label = if (metadata) R.string.export_browser_back_messages else R.string.export_browser_thread_info
                control(getString(label)) { showText(file, target, 0, listOf(0), !metadata) }
            }
            if (result.total > 1) {
                control(getString(R.string.export_browser_record, record + 1, result.total)) {
                    val index = EditText(this).apply {
                        inputType = android.text.InputType.TYPE_CLASS_NUMBER
                        setText((record + 1).toString())
                        selectAll()
                    }
                    AlertDialog.Builder(this).setTitle(R.string.export_browser_record_number).setView(index)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            val next = index.text.toString().toIntOrNull()
                            if (next != null && next in 1..result.total) showText(file, target, next - 1, listOf(0))
                        }.show()
                }
                if (record > 0) control(getString(R.string.export_browser_previous)) { showText(file, target, record - 1, listOf(0)) }
                if (record + 1 < result.total) control(getString(R.string.export_browser_next)) { showText(file, target, record + 1, listOf(0)) }
            }
            val text = if (result.total == 0) getString(R.string.export_browser_empty) else result.content
            val raw = AppUi.text(this, text, 14f).apply { setTextIsSelectable(true) }
            if (result.fields.isNotEmpty()) {
                val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, resources.configuration.locales[0])
                val fields = AppUi.card(this)
                for ((key, value) in result.fields) {
                    val timestamp = if (archiveTime.matches(key.substringAfterLast(" › "))) value.toLongOrNull() else null
                    val millis = timestamp?.let { if (it in 1..9_999_999_999L) it * 1000 else it }
                    val display = if (millis != null && millis in 946_684_800_000L until 4_102_444_800_000L)
                        formatter.format(Date(millis)) else value
                    fields.addView(AppUi.text(this, pretty(key), 12f, AppUi.muted))
                    fields.addView(AppUi.text(this, display, 14f).apply { setTextIsSelectable(true) })
                }
                target.addView(fields)
                raw.visibility = android.view.View.GONE
                control(getString(R.string.export_browser_raw)) {
                    raw.visibility = if (raw.visibility == android.view.View.VISIBLE) android.view.View.GONE else android.view.View.VISIBLE
                }
            }
            target.addView(raw)
            if (offsets.size > 1) control(getString(R.string.export_browser_previous_text)) { showText(file, target, record, offsets.dropLast(1), metadata) }
            result.next?.let { next ->
                control(getString(R.string.export_browser_next_text)) { showText(file, target, record, offsets + next, metadata) }
            }
        }
    }

    private val archiveTime = Regex("(?:.*_)?timestamp(?:_value|_ms)?", RegexOption.IGNORE_CASE)

    @Suppress("DEPRECATION")
    private fun save(file: ExportBrowser.Entry) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, file.name.substringAfterLast('/').substringAfterLast('\\'))
        }
        try { startActivityForResult(intent, 22) }
        catch (_: android.content.ActivityNotFoundException) { status.setText(R.string.export_browser_error) }
    }

    private fun showMedia(file: ExportBrowser.Entry, target: LinearLayout) {
        releasePreview()
        target.removeAllViews()
        work(R.string.export_browser_loading, {
            val suffix = file.name.substringAfterLast('.').lowercase(Locale.ROOT)
            val temporary = File.createTempFile("export-media-", ".$suffix", cacheDir)
            try {
                temporary.outputStream().use { archive!!.copy(file, it, ExportBrowser.MAX_PREVIEW) }
                temporary
            } catch (failure: Exception) {
                temporary.delete()
                throw failure
            }
        }, discard = { it.delete() }) { temporary ->
            previewFile = temporary
            if (file.kind == "image") {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(temporary.path, options)
                if (options.outWidth <= 0 || options.outHeight <= 0) {
                    status.setText(R.string.export_browser_error)
                    releasePreview()
                    return@work
                }
                options.inJustDecodeBounds = false
                options.inSampleSize = 1
                while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 2048) options.inSampleSize *= 2
                target.addView(ImageView(this).apply {
                    adjustViewBounds = true
                    setImageBitmap(BitmapFactory.decodeFile(temporary.path, options))
                    contentDescription = file.name.substringAfterLast('/')
                })
            } else {
                val video = VideoView(this)
                player = video
                video.setMediaController(MediaController(this).apply { setAnchorView(video) })
                video.setOnErrorListener { _, _, _ -> status.setText(R.string.export_browser_error); true }
                target.addView(video, LinearLayout.LayoutParams(-1, AppUi.dp(this, if (file.kind == "audio") 110 else 260)))
                video.setVideoPath(temporary.path)
                video.start()
            }
        }
    }
}
