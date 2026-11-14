package dev.kaankaya.instagramtracker

import android.app.Activity
import android.app.DatePickerDialog
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.text.TextWatcher
import android.text.Editable
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class InstagramArchiveActivity : LocalizedActivity() {
    private val reader = Executors.newSingleThreadExecutor()
    private var uri: Uri? = null
    private var archive: InstagramArchive? = null
    private var loading = false
    private var failed = false
    private var selected = 0
    private var order = 0
    private var fromDay: Long? = null
    private var toDay: Long? = null
    private var searchText = ""

    private data class ListOption(
        val label: String,
        val rows: List<InstagramArchiveRow>,
        val followsYou: Boolean,
        val mutual: Boolean = false,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppUi.systemBars(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        uri = savedInstanceState?.getString("archive_uri")?.let(Uri::parse) ?: intent.data
        val restored = uri
        if (restored == null) render() else load(restored)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        uri?.let { outState.putString("archive_uri", it.toString()) }
    }

    override fun onDestroy() {
        reader.shutdownNow()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun choose() {
        val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
        }
        try { startActivityForResult(picker, 7) }
        catch (_: android.content.ActivityNotFoundException) { failed = true; render() }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 7 && resultCode == Activity.RESULT_OK) data?.data?.let(::load)
    }

    private fun load(document: Uri) {
        uri = document
        archive = null
        loading = true
        failed = false
        selected = 0
        order = 0
        fromDay = null
        toDay = null
        searchText = ""
        render()
        reader.execute {
            val result = try {
                val (browser, temporary) = ExportBrowser.openDocument(this, document)
                try { browser.use { it.relationships() } }
                finally { temporary?.delete() }
            } catch (_: Exception) { null }
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                archive = result
                loading = false
                failed = result == null
                render()
            }
        }
    }

    private fun render() {
        val padding = (20 * resources.displayMetrics.density).toInt()
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(AppUi.background)
        }
        page.addView(AppUi.button(this, getString(R.string.ui_close), false) { finish() })
        page.addView(AppUi.heading(this, getString(R.string.instagram_archive_title), 26f))
        page.addView(AppUi.text(this, getString(R.string.instagram_archive_scope), 14f, AppUi.muted))
        page.addView(AppUi.button(this, getString(R.string.instagram_archive_open), true, !loading, action = ::choose))
        if (loading) page.addView(AppUi.text(this, getString(R.string.instagram_archive_loading), 14f, AppUi.muted))
        if (failed) page.addView(AppUi.text(this, getString(R.string.instagram_archive_error), 14f, AppUi.ink))
        archive?.let {
            page.addView(AppUi.text(this, getString(R.string.instagram_archive_snapshot), 13f, AppUi.muted))
            showLists(page, it)
        }
        setContentView(ScrollView(this).apply { addView(page) })
    }

    private fun showLists(page: LinearLayout, archive: InstagramArchive) {
        val followers = archive.followers
        val following = archive.following
        val followingDates = following.orEmpty().associate { it.username to it.timestamp }
        val options = buildList {
            followers?.let { add(ListOption(getString(R.string.list_followers), it, true)) }
            following?.let { add(ListOption(getString(R.string.list_following), it, false)) }
            if (followers != null && following != null) {
                val followerNames = followers.mapTo(hashSetOf()) { it.username }
                val followingOnly = following.filterNot { it.username in followerNames }
                val followersOnly = followers.filterNot { it.username in followingDates }
                val mutual = followers.filter { it.username in followingDates }
                add(ListOption(getString(R.string.list_following_only, followingOnly.size), followingOnly, false))
                add(ListOption(getString(R.string.list_followers_only, followersOnly.size), followersOnly, true))
                add(ListOption(getString(R.string.list_mutual, mutual.size), mutual, true, true))
            }
        }
        val option = options[selected]
        val tabs = AppUi.button(this, option.label, false) {
            android.app.AlertDialog.Builder(this).setTitle(R.string.instagram_archive_title)
                .setSingleChoiceItems(options.map { it.label }.toTypedArray(), selected) { dialog, position ->
                    dialog.dismiss()
                    selected = position
                    render()
                }.show()
        }
        page.addView(tabs)
        val search = EditText(this).apply {
            setSingleLine()
            setHint(R.string.instagram_archive_search)
            filters = arrayOf(InputFilter.LengthFilter(30))
            setText(searchText)
        }
        page.addView(search)
        val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, resources.configuration.locales[0])
        val hasDates = option.rows.any { it.timestamp != null }
        if (option.rows.isNotEmpty() && !hasDates &&
            (!option.mutual || option.rows.none { followingDates[it.username] != null })) {
            page.addView(AppUi.text(this, getString(R.string.instagram_archive_undated), 13f, AppUi.muted))
        }
        val dateControls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (hasDates) page.addView(dateControls)
        val invalid = AppUi.text(this, getString(R.string.instagram_archive_invalid_range), 13f, AppUi.ink)
        page.addView(invalid)
        val count = AppUi.text(this, "", 13f, AppUi.muted)
        page.addView(count)
        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(rows)
        var filtered = emptyList<InstagramArchiveRow>()
        var end = 0
        fun addDate(label: Int, timestamp: Long?) {
            if (timestamp != null) {
                rows.addView(AppUi.text(this, getString(label) + " · " + dateFormat.format(Date(timestamp * 1000)), 13f, AppUi.muted))
            }
        }
        fun update() {
            rows.removeAllViews()
            val start = ((end - 1).coerceAtLeast(0) / 100) * 100
            for ((name, timestamp) in filtered.subList(start, end)) {
                rows.addView(AppUi.text(this, "@$name", 15f, AppUi.ink))
                addDate(if (option.followsYou) R.string.instagram_archive_followed_you else R.string.instagram_archive_you_followed, timestamp)
                if (option.mutual) addDate(R.string.instagram_archive_you_followed, followingDates[name])
            }
            count.text = getString(R.string.instagram_archive_count, if (end == 0) 0 else start + 1, end, filtered.size)
            if (start > 0) rows.addView(AppUi.button(this, getString(R.string.instagram_archive_previous), false) {
                end = start
                update()
            })
            if (end < filtered.size) rows.addView(AppUi.button(this, getString(R.string.instagram_archive_next), false) {
                end = minOf(end + 100, filtered.size)
                update()
            })
        }
        fun selectRows() {
            val query = searchText.trim().removePrefix("@").lowercase(Locale.ROOT)
            val from = fromDay.takeIf { hasDates }
            val to = toDay.takeIf { hasDates }
            val reversed = from != null && to != null && to < from
            invalid.visibility = if (reversed) View.VISIBLE else View.GONE
            val endOfRange = to?.let { Calendar.getInstance().apply {
                timeInMillis = it
                add(Calendar.DATE, 1)
            }.timeInMillis }
            filtered = if (reversed) emptyList() else option.rows.filter { row ->
                val millis = row.timestamp?.times(1000)
                query in row.username && ((from == null && endOfRange == null) ||
                    (millis != null && (from == null || millis >= from) && (endOfRange == null || millis < endOfRange)))
            }
            if (hasDates && order != 0) {
                val timestamps = if (order == 1) compareBy<InstagramArchiveRow> { it.timestamp?.let { value -> -value } ?: Long.MAX_VALUE }
                    else compareBy { it.timestamp ?: Long.MAX_VALUE }
                filtered = filtered.sortedWith(timestamps)
            }
            end = minOf(100, filtered.size)
            update()
        }
        if (hasDates) {
            val orders = intArrayOf(R.string.instagram_archive_sort_name, R.string.instagram_archive_sort_newest, R.string.instagram_archive_sort_oldest)
            fun sortLabel() = getString(R.string.instagram_archive_sort, getString(orders[order]))
            val sort = AppUi.button(this, sortLabel(), false) {}
            sort.setOnClickListener {
                android.app.AlertDialog.Builder(this).setTitle(R.string.instagram_archive_sort_label)
                    .setSingleChoiceItems(orders.map { getString(it) }.toTypedArray(), order) { dialog, position ->
                        dialog.dismiss()
                        order = position
                        selectRows()
                        sort.text = sortLabel()
                    }.show()
            }
            dateControls.addView(sort)
            fun dateButton(label: Int, value: () -> Long?, set: (Long?) -> Unit) {
                fun title() = value()?.let { getString(label) + " · " + dateFormat.format(Date(it)) } ?: getString(label)
                val button = AppUi.button(this, title(), false) {}
                button.setOnClickListener {
                    val initial = Calendar.getInstance().apply { value()?.let { timeInMillis = it } }
                    fun save(day: Long?) {
                        set(day)
                        button.text = title()
                        selectRows()
                    }
                    DatePickerDialog(this, { _, year, month, day ->
                        save(Calendar.getInstance().apply { clear(); set(year, month, day) }.timeInMillis)
                    }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH)).apply {
                        setButton(DialogInterface.BUTTON_NEUTRAL, getString(R.string.instagram_archive_clear_date)) { _, _ -> save(null) }
                    }.show()
                }
                dateControls.addView(button)
            }
            dateButton(R.string.instagram_archive_from, { fromDay }, { fromDay = it })
            dateButton(R.string.instagram_archive_to, { toDay }, { toDay = it })
            if (option.mutual) {
                dateControls.addView(AppUi.text(this, getString(R.string.instagram_archive_mutual_range), 13f, AppUi.muted))
            }
        }
        selectRows()
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchText = s.toString()
                selectRows()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

}
