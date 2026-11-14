package dev.kaankaya.instagramtracker

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
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
    companion object {
        const val EXTRA_OPEN_PICKER = "open_archive_picker"
        private const val PICK_ARCHIVE = 21
        private const val SAVE_ENTRY = 22
        private const val PICK_COMPARISON = 23
    }

    private data class PickerResult(val requestCode: Int, val uri: Uri)
    private data class EntryReference(val index: Int, val name: String)

    private val reader = Executors.newSingleThreadExecutor()
    private var archive: ExportBrowser? = null
    private var archiveCopy: File? = null
    private var selectedUri: Uri? = null
    private var openingUri: Uri? = null
    private var relationships: InstagramArchive? = null
    private var coverage: ExportBrowser.Coverage? = null
    private var recordCounts: Map<Int, Int> = emptyMap()
    private var likedMonths: List<Pair<String, Int?>> = emptyList()
    private var comparison: Pair<InstagramArchive.Difference?, InstagramArchive.Difference?>? = null
    private var comparisonUri: Uri? = null
    private var restoreComparison = false
    private var comparisonConfirmed = false
    private var comparisonReversed = false
    private var comparisonDialog: AlertDialog? = null
    private var previewFile: File? = null
    private var player: VideoView? = null
    private var saveEntry: EntryReference? = null
    private var pickerResult: PickerResult? = null
    private var pending = false
    private var category = ""
    private var query = ""
    private var visible = 40
    private var contentHistory: ExportBrowser.ContentHistory? = null
    private var localStoryOwner: String? = null
    private var localStories = emptyList<ArchivedStory>()
    private lateinit var page: LinearLayout
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppUi.systemBars(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        directory()
        if (savedInstanceState == null) {
            if (intent.getBooleanExtra(EXTRA_OPEN_PICKER, false)) pick()
            return
        }
        comparisonUri = savedInstanceState.getString("comparison_uri")?.let(Uri::parse)
        restoreComparison = comparisonUri != null
        comparisonConfirmed = savedInstanceState.getBoolean("comparison_confirmed")
        comparisonReversed = savedInstanceState.getBoolean("comparison_reversed")
        saveEntry = savedInstanceState.getString("save_name")?.let {
            EntryReference(savedInstanceState.getInt("save_index"), it)
        }
        pickerResult = savedInstanceState.getString("picker_uri")?.let {
            PickerResult(savedInstanceState.getInt("picker_code"), Uri.parse(it))
        }
        val source = savedInstanceState.getString("archive_uri")?.let(Uri::parse)
        if (source != null) loadArchive(source, restoring = true) else resumePickerWork()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("archive_uri", (openingUri ?: selectedUri)?.toString())
        if (openingUri == null || selectedUri == null) {
            outState.putString("comparison_uri", comparisonUri?.toString())
            outState.putBoolean("comparison_confirmed", comparisonConfirmed)
            outState.putBoolean("comparison_reversed", comparisonReversed)
        }
        saveEntry?.let {
            outState.putInt("save_index", it.index)
            outState.putString("save_name", it.name)
        }
        pickerResult?.let {
            outState.putInt("picker_code", it.requestCode)
            outState.putString("picker_uri", it.uri.toString())
        }
    }

    override fun onDestroy() {
        comparisonDialog?.setOnDismissListener(null)
        comparisonDialog?.dismiss()
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
        base()
        action(R.string.ui_close) { finish() }
        page.addView(AppUi.heading(this, getString(R.string.export_browser_title), 26f))
        page.addView(AppUi.text(this, getString(R.string.export_browser_intro), 14f, AppUi.muted))
        action(R.string.instagram_archive_open, true) { pick() }
        action(R.string.archive_content_title) { openContentHistory() }
        page.addView(status)
        val files = archive?.files ?: return
        action(R.string.archive_search_all) { showArchiveSearch() }
        val categories = files.groupingBy { group(it) }.eachCount()
        val categoryKeys = listOf("") + categories.keys
        showOverview(files.size, categories.size)
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

    private fun showOverview(fileCount: Int, categoryCount: Int) {
        val summary = AppUi.card(this)
        summary.addView(AppUi.heading(this, getString(R.string.export_browser_overview), 20f))
        coverage?.let { info ->
            val stamp = info.exportedAt?.let {
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, resources.configuration.locales[0])
                    .format(Date(java.time.Instant.parse(it).toEpochMilli()))
            }
            val details = listOfNotNull(info.name, info.username?.let { "@$it" },
                stamp?.let { getString(R.string.archive_exported_at, it) },
                info.filenameDate?.let { getString(R.string.archive_filename_date, it) })
            if (details.isNotEmpty()) summary.addView(AppUi.text(this, details.joinToString(" · "), 14f))
            fun scope(label: Int, value: String) {
                summary.addView(AppUi.heading(this, "${getString(label)} · $value", 16f))
            }
            fun listCount(rows: List<InstagramArchiveRow>?, exists: Boolean): String = rows?.size?.toString()
                ?: getString(if (exists) R.string.archive_scope_unreadable else R.string.archive_scope_missing)
            scope(R.string.list_followers, listCount(relationships?.followers, info.hasFollowers))
            scope(R.string.list_following, listCount(relationships?.following, info.hasFollowing))
            scope(R.string.export_browser_messages, getString(when {
                info.hasMessages -> R.string.archive_scope_present
                info.emptyMessages -> R.string.archive_scope_empty
                else -> R.string.archive_scope_missing
            }))
        }
        summary.addView(AppUi.text(this, getString(R.string.export_browser_overview_counts, fileCount, categoryCount), 14f, AppUi.muted))
        summary.addView(AppUi.text(this, getString(R.string.instagram_archive_snapshot), 13f, AppUi.muted))
        val lists = relationships
        if (lists != null) {
            summary.addView(AppUi.button(this, getString(R.string.export_browser_follow_lists), false) {
                if (!pending) startActivity(Intent(this, InstagramArchiveActivity::class.java)
                    .setData(selectedUri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            })
            summary.addView(AppUi.text(this, getString(R.string.export_browser_compare_hint), 13f, AppUi.muted))
            summary.addView(AppUi.button(this, getString(R.string.export_browser_compare), false) {
                if (!pending) pick(PICK_COMPARISON)
            })
        }
        recordCounts.forEach { (label, count) ->
            summary.addView(AppUi.text(this, "${getString(label)} · $count", 14f))
        }
        if (likedMonths.isNotEmpty()) {
            summary.addView(AppUi.heading(this, getString(R.string.archive_likes_by_month), 18f))
            val maximum = likedMonths.maxOf { it.second ?: 0 }
            val monthFormat = java.time.format.DateTimeFormatter.ofPattern("LLL yyyy", resources.configuration.locales[0])
            for ((month, count) in likedMonths) {
                val label = java.time.YearMonth.parse(month).format(monthFormat)
                summary.addView(AppUi.text(this, "$label · ${count ?: getString(R.string.archive_scope_empty)}", 14f))
                if (count != null) summary.addView(android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = maximum
                    progress = count
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                })
            }
        }
        comparison?.let { (followers, following) ->
            summary.addView(AppUi.heading(this, getString(R.string.export_browser_compare_results), 18f))
            showDifference(summary, R.string.list_followers, followers)
            showDifference(summary, R.string.list_following, following)
        }
        page.addView(summary)
    }

    private fun openContentHistory() {
        val opened = archive
        work(R.string.export_browser_loading, {
            val history = contentHistory ?: opened?.contentHistory() ?: ExportBrowser.ContentHistory(emptyList(), 0)
            val local = runCatching {
                val vault = SessionVault(this)
                val own = vault.read()?.takeIf { coverage?.username == null || coverage?.username.equals(it.profile.username, true) }
                own?.session?.let { it.userId to vault.storyStore(it).read(it.userId).items }
            }.getOrNull()
            history to local
        }) { (history, local) ->
            contentHistory = history
            localStoryOwner = local?.first
            localStories = local?.second.orEmpty()
            val latest = listOfNotNull(history.items.firstOrNull()?.let { contentDay(it.time) },
                localStories.maxOfOrNull { java.time.LocalDate.parse(it.day) }).maxOrNull()
                ?: java.time.LocalDate.now(java.time.ZoneOffset.UTC)
            showContentMonth(history, java.time.YearMonth.from(latest), latest)
        }
    }

    private fun contentDay(time: Long): java.time.LocalDate =
        java.time.Instant.ofEpochSecond(time).atOffset(java.time.ZoneOffset.UTC).toLocalDate()

    private fun showContentMonth(
        history: ExportBrowser.ContentHistory, month: java.time.YearMonth, selected: java.time.LocalDate, offset: Int = 0,
    ) {
        releasePreview()
        base()
        action(R.string.export_browser_back, false, ::directory)
        page.addView(AppUi.heading(this, getString(R.string.archive_content_title)))
        page.addView(status)
        if (history.skipped > 0) page.addView(AppUi.text(this, getString(R.string.archive_content_partial), 13f, AppUi.muted))
        val owner = localStoryOwner
        if (owner != null) {
            action(R.string.archive_content_local_stories) {
                startActivity(Intent(this, StoryArchiveActivity::class.java)
                    .putExtra("viewer_id", owner).putExtra("target_id", owner))
            }
        }
        val monthItems = history.items.filter { java.time.YearMonth.from(contentDay(it.time)) == month }
        val byDay = monthItems.groupBy { contentDay(it.time) }
        val localByDay = localStories.groupBy { it.day }
        val header = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        fun monthButton(label: Int, step: Long) = AppUi.button(this, getString(label), false) {
            if (!pending) {
                val next = month.plusMonths(step)
                showContentMonth(history, next, next.atDay(1))
            }
        }
        header.addView(monthButton(R.string.story_previous_month, -1), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(monthButton(R.string.story_next_month, 1), LinearLayout.LayoutParams(0, -2, 1f))
        page.addView(header)
        val locale = resources.configuration.locales[0]
        page.addView(AppUi.heading(this, month.format(java.time.format.DateTimeFormatter.ofPattern("LLLL yyyy", locale)), 19f))
        val firstWeekday = month.atDay(1).dayOfWeek.value - 1
        val days = java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.LONG).withLocale(locale)
        for (weekStart in 0 until firstWeekday + month.lengthOfMonth() step 7) {
            val week = LinearLayout(this)
            repeat(7) { column ->
                val number = weekStart + column - firstWeekday + 1
                val child = if (number in 1..month.lengthOfMonth()) {
                    val day = month.atDay(number)
                    val count = byDay[day].orEmpty().size + localByDay[day.toString()].orEmpty().size
                    AppUi.button(this, if (count == 0) "$number" else "$number\n·$count", day == selected) {
                        if (!pending) showContentMonth(history, month, day)
                    }.apply {
                        contentDescription = days.format(day) + if (count > 0) " · $count" else ""
                        minWidth = 0
                        minimumWidth = 0
                        setPadding(0, paddingTop, 0, paddingBottom)
                    }
                } else android.view.View(this)
                week.addView(child, LinearLayout.LayoutParams(0, -2, 1f))
            }
            page.addView(week)
        }
        page.addView(AppUi.heading(this, days.format(selected), 18f))
        val items = byDay[selected].orEmpty()
        val stories = localByDay[selected.toString()].orEmpty()
        if (items.isEmpty() && stories.isEmpty()) page.addView(AppUi.text(this, getString(R.string.archive_content_empty), 14f, AppUi.muted))
        if (owner != null && offset == 0) for (story in stories) {
            val card = AppUi.card(this)
            card.addView(AppUi.heading(this, getString(R.string.archive_content_local_stories), 16f))
            card.addView(AppUi.text(this, DateFormat.getTimeInstance(DateFormat.SHORT, locale)
                .format(Date((story.facts.publishedAt ?: story.capturedAt) * 1000)), 14f))
            card.addView(AppUi.button(this, getString(R.string.export_browser_preview), false) {
                startActivity(Intent(this, StoryArchiveActivity::class.java).putExtra("viewer_id", owner)
                    .putExtra("target_id", owner).putExtra("archive_day", story.day).putExtra("archive_story", story.facts.id))
            })
            page.addView(card)
        }
        val metrics = mapOf("views" to R.string.archive_content_views, "plays" to R.string.archive_content_plays,
            "likes" to R.string.archive_content_likes, "comments" to R.string.archive_content_comments,
            "reactions" to R.string.archive_content_reactions)
        for (item in items.drop(offset).take(24)) {
            val card = AppUi.card(this)
            val label = when (item.kind) {
                "reels" -> R.string.archive_content_reels
                "posts" -> R.string.archive_content_posts
                else -> R.string.archive_content_stories
            }
            card.addView(AppUi.heading(this, getString(label), 16f))
            if (item.caption.isNotEmpty()) card.addView(AppUi.text(this, item.caption, 14f))
            for ((key, count) in item.metrics) {
                card.addView(AppUi.text(this, "${getString(metrics.getValue(key))} · $count", 14f))
            }
            val media = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            for (entry in item.media) card.addView(AppUi.button(this, getString(R.string.export_browser_preview), false) {
                if (!pending) showMedia(entry, media)
            })
            card.addView(media)
            page.addView(card)
        }
        if (offset > 0) action(R.string.instagram_archive_previous) { showContentMonth(history, month, selected, offset - 24) }
        if (offset + 24 < items.size) action(R.string.instagram_archive_next) { showContentMonth(history, month, selected, offset + 24) }
    }

    private fun showDifference(parent: LinearLayout, label: Int, difference: InstagramArchive.Difference?) {
        parent.addView(AppUi.heading(this, getString(label), 16f))
        if (difference == null) {
            parent.addView(AppUi.text(this, getString(R.string.export_browser_compare_missing), 13f, AppUi.muted))
            return
        }
        parent.addView(AppUi.text(this, getString(R.string.export_browser_compare_counts, difference.before, difference.after), 14f))
        if (difference.added.isEmpty() && difference.removed.isEmpty()) {
            parent.addView(AppUi.text(this, getString(R.string.export_browser_compare_unchanged), 13f, AppUi.muted))
        }
        for ((change, names) in listOf(R.string.export_browser_compare_second to difference.added,
            R.string.export_browser_compare_first to difference.removed)) {
            if (names.isEmpty()) continue
            val title = getString(change, names.size)
            parent.addView(AppUi.button(this, title, false) {
                AlertDialog.Builder(this).setTitle(title)
                    .setAdapter(android.widget.ArrayAdapter(this, android.R.layout.simple_list_item_1, names), null)
                    .setPositiveButton(R.string.ui_close, null).show()
            })
        }
    }

    private fun group(file: ExportBrowser.Entry): String {
        val path = file.name
        return when {
            path.startsWith("connections/followers_and_following/") -> "follow_lists"
            path.startsWith("connections/contacts/") -> "contacts"
            path.startsWith("your_instagram_activity/messages/") -> "messages"
            path.startsWith("your_instagram_activity/media/") -> "media"
            path.startsWith("personal_information/personal_information/") -> "profile"
            else -> path.substringBefore('/')
        }
    }
    private fun pretty(value: String) = value.replace('_', ' ')

    private fun categoryLabel(key: String, count: Int?): String {
        val label = when (key) {
            "" -> getString(R.string.export_browser_all)
            "connections" -> getString(R.string.export_browser_connections)
            "follow_lists" -> getString(R.string.export_browser_follow_lists)
            "contacts" -> getString(R.string.export_browser_contacts)
            "messages" -> getString(R.string.export_browser_messages)
            "profile" -> getString(R.string.export_browser_profile)
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
    private fun pick(requestCode: Int = PICK_ARCHIVE) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
        }
        try { startActivityForResult(intent, requestCode) }
        catch (_: android.content.ActivityNotFoundException) { status.setText(R.string.export_browser_error) }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode !in PICK_ARCHIVE..PICK_COMPARISON) return
        val uri = data?.data?.takeIf { resultCode == Activity.RESULT_OK }
        if (uri == null) {
            if (requestCode == SAVE_ENTRY) saveEntry = null
            return
        }
        pickerResult = PickerResult(requestCode, uri)
        resumePickerWork()
    }

    private fun resumePickerWork() {
        if (pending) return
        val result = pickerResult
        pickerResult = null
        when (result?.requestCode) {
            PICK_ARCHIVE -> loadArchive(result.uri)
            PICK_COMPARISON -> {
                restoreComparison = false
                comparisonConfirmed = false
                comparisonReversed = false
                compareArchive(result.uri)
            }
            SAVE_ENTRY -> saveEntryTo(result.uri)
            else -> if (restoreComparison) {
                restoreComparison = false
                comparisonUri?.let(::compareArchive)
            }
        }
    }

    private fun loadArchive(uri: Uri, restoring: Boolean = false) {
        openingUri = uri
        work(R.string.instagram_archive_loading, {
            val opened = ExportBrowser.openDocument(this, uri)
            val lists = runCatching { opened.first.relationships() }.getOrNull()
            val counts = buildMap {
                val sources = mapOf(
                    R.string.export_browser_profile_changes to "personal_information/personal_information/profile_changes.json",
                    R.string.export_browser_liked_posts to "your_instagram_activity/likes/liked_posts.json",
                    R.string.export_browser_saved_posts to "your_instagram_activity/saved/saved_posts.json",
                )
                for ((label, path) in sources) {
                    runCatching { opened.first.recordCount(path) }.getOrNull()?.let { put(label, it) }
                }
            }
            archive?.close()
            archiveCopy?.delete()
            archive = opened.first
            archiveCopy = opened.second
            Triple(lists, counts, opened.first.coverage(ExportBrowser.documentName(this, uri)) to opened.first.likedMonths())
        }, failure = {
            openingUri = null
            if (restoring) {
                restoreComparison = false
                comparisonUri = null
                comparisonConfirmed = false
                comparisonReversed = false
                saveEntry = null
            }
        }) { (lists, counts, details) ->
            openingUri = null
            selectedUri = uri
            relationships = lists
            coverage = details.first
            likedMonths = details.second
            contentHistory = null
            recordCounts = counts
            if (!restoring) {
                comparison = null
                comparisonUri = null
                restoreComparison = false
                category = ""
                query = ""
                visible = 40
            }
            directory()
        }
    }

    private fun compareArchive(uri: Uri) {
        val before = relationships
        if (before == null) {
            status.setText(R.string.export_browser_error)
            return
        }
        comparisonUri = uri
        comparison = null
        work(R.string.instagram_archive_loading, {
            val (next, temporary) = ExportBrowser.openDocument(this, uri)
            try { next.use { it.coverage(ExportBrowser.documentName(this, uri)) to it.relationships() } }
            finally { temporary?.delete() }
        }, failure = { comparisonUri = null }) { (nextInfo, nextLists) ->
            if (comparisonConfirmed) applyComparison(before, nextLists)
            else showComparisonPreview(requireNotNull(coverage), nextInfo, before, nextLists)
        }
    }

    private fun applyComparison(first: InstagramArchive, second: InstagramArchive) {
        comparisonConfirmed = true
        val reverse = comparisonReversed
        work(R.string.instagram_archive_loading, {
            if (reverse) second.compare(first) else first.compare(second)
        }, failure = { comparisonConfirmed = false }) {
            comparison = it
            directory()
        }
    }

    private fun showComparisonPreview(
        first: ExportBrowser.Coverage, second: ExportBrowser.Coverage,
        before: InstagramArchive, after: InstagramArchive,
    ) {
        val mismatch = first.username != null && second.username != null && first.username != second.username
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = AppUi.dp(this@ExportBrowserActivity, 16)
            setPadding(padding, 0, padding, 0)
        }
        val dialog = AlertDialog.Builder(this).setTitle(R.string.export_browser_compare)
            .setView(ScrollView(this).apply { addView(content) })
            .setNegativeButton(R.string.list_cancel, null)
            .setPositiveButton(if (mismatch) R.string.archive_compare_anyway else R.string.archive_compare_start) { _, _ ->
                applyComparison(before, after)
            }.create()
        fun renderPreview() {
            content.removeAllViews()
            val (older, newer) = if (comparisonReversed) second to first else first to second
            fun addSource(label: Int, info: ExportBrowser.Coverage) {
                val card = AppUi.card(this)
                card.addView(AppUi.heading(this, getString(label), 16f))
                val date = info.exportedAt?.let { getString(R.string.archive_exported_at, it.replace("T", " ")) }
                    ?: info.filenameDate?.let { getString(R.string.archive_filename_date, it) }
                val range = info.dateRange?.let { getString(R.string.archive_date_range, it.first, it.second) }
                for (line in listOfNotNull(info.name, info.username?.let { "@$it" }, date, range)) {
                    card.addView(AppUi.text(this, line, 14f))
                }
                content.addView(card)
            }
            addSource(R.string.archive_older_zip, older)
            addSource(R.string.archive_newer_zip, newer)
            val firstDate = older.exportedAt?.take(10) ?: older.filenameDate
            val secondDate = newer.exportedAt?.take(10) ?: newer.filenameDate
            if (firstDate != null && secondDate != null && firstDate > secondDate ||
                older.exportedAt != null && newer.exportedAt != null && older.exportedAt > newer.exportedAt) {
                content.addView(AppUi.text(this, getString(R.string.archive_compare_order), 14f, AppUi.muted))
            }
            if (mismatch) content.addView(AppUi.text(this, getString(R.string.archive_compare_accounts), 14f, AppUi.muted))
            content.addView(AppUi.button(this, getString(R.string.archive_compare_swap), false) {
                comparisonReversed = !comparisonReversed
                renderPreview()
            })
        }
        dialog.setOnDismissListener {
            comparisonDialog = null
            if (!comparisonConfirmed) {
                comparisonUri = null
                comparison = null
                directory()
            }
        }
        renderPreview()
        comparisonDialog = dialog
        dialog.show()
    }

    private fun saveEntryTo(uri: Uri) {
        val reference = saveEntry
        saveEntry = null
        val opened = archive
        val file = reference?.let { entry ->
            opened?.files?.firstOrNull { it.index == entry.index && it.name == entry.name }
        }
        if (opened == null || file == null || uri == selectedUri) {
            status.setText(R.string.export_browser_error)
            return
        }
        work(R.string.export_browser_loading, {
            val temporary = File.createTempFile("export-entry-", ".tmp", cacheDir)
            try {
                temporary.outputStream().use { opened.copy(file, it) }
                requireNotNull(contentResolver.openOutputStream(uri, "wt")).use { output ->
                    temporary.inputStream().use { it.copyTo(output) }
                }
            } finally {
                temporary.delete()
            }
        }) { status.setText(R.string.export_browser_saved) }
    }

    private fun <T> work(
        label: Int, task: () -> T, discard: (T) -> Unit = {}, failure: () -> Unit = {}, success: (T) -> Unit,
    ) {
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
                }, onFailure = {
                    failure()
                    status.setText(R.string.export_browser_error)
                })
                resumePickerWork()
            }
        }
    }

    private fun showArchiveSearch() {
        val opened = archive ?: return
        releasePreview()
        base()
        action(R.string.export_browser_back, false, ::directory)
        val input = EditText(this).apply {
            setSingleLine()
            setHint(R.string.archive_search_all)
            filters = arrayOf(android.text.InputFilter.LengthFilter(160))
        }
        page.addView(input)
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        var term = ""
        fun find(file: Int, record: Int) {
            work(R.string.export_browser_loading, { opened.search(term, file, record) }) { found ->
                results.removeAllViews()
                if (found.hits.isEmpty()) results.addView(AppUi.text(this, getString(R.string.archive_search_empty), 14f))
                if (found.skipped > 0) results.addView(AppUi.text(this, getString(R.string.archive_content_partial), 13f, AppUi.muted))
                for (hit in found.hits) {
                    results.addView(AppUi.button(this, pretty(hit.file.name.substringAfterLast('/')), false) {
                        if (!pending) detail(hit.file, hit.record ?: 0)
                    })
                    if (hit.excerpt.isNotEmpty()) results.addView(AppUi.text(this, hit.excerpt, 14f))
                }
                if (found.nextFile < opened.files.size) results.addView(AppUi.button(this, getString(R.string.export_browser_more), false) {
                    if (!pending) find(found.nextFile, found.nextRecord)
                })
            }
        }
        action(R.string.archive_search_start, true) {
            term = input.text.toString().trim()
            if (term.length < 2) input.error = getString(R.string.archive_search_minimum)
            else find(0, -1)
        }
        page.addView(status)
        page.addView(results)
    }

    private fun detail(file: ExportBrowser.Entry, record: Int = 0) {
        releasePreview()
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
                showText(file, content, record, listOf(0))
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
        saveEntry = EntryReference(file.index, file.name)
        try { startActivityForResult(intent, SAVE_ENTRY) }
        catch (_: android.content.ActivityNotFoundException) {
            saveEntry = null
            status.setText(R.string.export_browser_error)
        }
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
