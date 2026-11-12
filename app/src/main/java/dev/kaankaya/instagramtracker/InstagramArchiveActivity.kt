package dev.kaankaya.instagramtracker

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.text.TextWatcher
import android.text.Editable
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import java.util.Locale
import java.util.concurrent.Executors

class InstagramArchiveActivity : LocalizedActivity() {
    private val reader = Executors.newSingleThreadExecutor()
    private var uri: Uri? = null
    private var archive: InstagramArchive? = null
    private var loading = false
    private var failed = false
    private var selected = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppUi.systemBars(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        uri = savedInstanceState?.getString("archive_uri")?.let(Uri::parse)
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
        render()
        reader.execute {
            val result = try {
                contentResolver.openInputStream(document)?.use { InstagramArchive.read(it, cacheDir) }
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
        archive?.let { showLists(page, it) }
        setContentView(ScrollView(this).apply { addView(page) })
    }

    private fun showLists(page: LinearLayout, archive: InstagramArchive) {
        val followers = archive.followers
        val following = archive.following
        val options = buildList {
            followers?.let { add(getString(R.string.list_followers) to it) }
            following?.let { add(getString(R.string.list_following) to it) }
            if (followers != null && following != null) {
                val followerNames = followers.toHashSet()
                val followingNames = following.toHashSet()
                val followingOnly = following.filterNot(followerNames::contains)
                val followersOnly = followers.filterNot(followingNames::contains)
                val mutual = followers.filter(followingNames::contains)
                add(getString(R.string.list_following_only, followingOnly.size) to followingOnly)
                add(getString(R.string.list_followers_only, followersOnly.size) to followersOnly)
                add(getString(R.string.list_mutual, mutual.size) to mutual)
            }
        }
        val tabs = AppUi.button(this, options[selected].first, false) {
            android.app.AlertDialog.Builder(this).setTitle(R.string.instagram_archive_title)
                .setSingleChoiceItems(options.map { it.first }.toTypedArray(), selected) { dialog, position ->
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
        }
        page.addView(search)
        val count = AppUi.text(this, "", 13f, AppUi.muted)
        page.addView(count)
        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(rows)
        val names = options[selected].second
        var filtered = names
        var end = 0
        fun update() {
            rows.removeAllViews()
            val start = ((end - 1).coerceAtLeast(0) / 100) * 100
            for (name in filtered.subList(start, end)) {
                rows.addView(AppUi.text(this, "@$name", 15f, AppUi.ink))
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
        end = minOf(100, filtered.size)
        update()
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim().removePrefix("@").lowercase(Locale.ROOT)
                filtered = names.filter { query in it }
                end = minOf(100, filtered.size)
                update()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }
}
