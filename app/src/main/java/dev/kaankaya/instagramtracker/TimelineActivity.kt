package dev.kaankaya.instagramtracker

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import java.text.NumberFormat

/** Reads only local, encrypted evidence. Opening or filtering history never sends a network request. */
class TimelineActivity : LocalizedActivity() {
    private lateinit var vault: SessionVault
    private var reference: Session? = null
    private var selected: String? = null
    private var preview = false
    private var scroll: ScrollView? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); AppUi.systemBars(this)
        if (!BuildConfig.DEBUG) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        preview = PreviewMode.enabled(this)
        vault = SessionVault(this, if (preview) "previewaccount" else "account")
        selected = if (savedInstanceState?.containsKey("selected_target") == true) savedInstanceState.getString("selected_target") else intent.getStringExtra("target_id")
        try {
            val saved = vault.read() ?: throw ApiFailure(ApiFailure.Kind.SESSION_EXPIRED)
            if (intent.getStringExtra("viewer_id") != saved.session.userId) throw ApiFailure(ApiFailure.Kind.WRONG_ACCOUNT)
            reference = saved.session
        } catch (_: Exception) { finish(); return }
        if (android.os.Build.VERSION.SDK_INT >= 33) onBackInvokedDispatcher.registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) { finish() }
        render()
    }
    private fun date(time: Long?) = ObservationTimes.format(this, time)
    private fun delta(value: Long?) = value?.let { (if (it > 0) "+" else "") + NumberFormat.getIntegerInstance(AppLanguage.locale(this)).format(it) }
        ?: getString(R.string.timeline_unknown)
    private fun field(field: ProfileChangeField) = getString(when (field) {
        ProfileChangeField.USERNAME -> R.string.profile_username_changed
        ProfileChangeField.BIOGRAPHY -> R.string.profile_bio_changed
        ProfileChangeField.PICTURE -> R.string.profile_picture_changed
    })
    private fun direction(value: FollowDirection?) = getString(if (value == FollowDirection.FOLLOWING) R.string.list_following else R.string.list_followers)
    private fun title(event: CheckEvent) = when (event.kind) {
        CheckKind.FAILURE -> getString(R.string.timeline_failure)
        CheckKind.LIST -> getString(if (event.added != null) R.string.timeline_list_compared else R.string.timeline_list, direction(event.direction))
        CheckKind.PROFILE -> getString(when {
            event.previousAt == null -> R.string.timeline_profile_initial
            event.followersDelta == 0L && event.followingDelta == 0L && event.fields.isEmpty() -> R.string.timeline_profile_unchanged
            else -> R.string.timeline_profile_changed
        })
    }
    private fun summary(event: CheckEvent): String = when (event.kind) {
        CheckKind.PROFILE -> (listOf(getString(R.string.timeline_counts, delta(event.followersDelta), delta(event.followingDelta))) + event.fields.map(::field)).joinToString("\n")
        CheckKind.LIST -> listOfNotNull(getString(R.string.timeline_list_counts, event.pages ?: 0, event.accounts ?: 0),
            event.added?.let { getString(R.string.auto_difference_counts, it.toString(), event.missing.toString()) },
            event.coverage?.let { getString(when (it) {
                FollowCoverage.MORE -> R.string.list_more; FollowCoverage.END_OBSERVED -> R.string.list_end
                FollowCoverage.LIMITED -> R.string.list_limited; FollowCoverage.UNKNOWN -> R.string.list_unknown; FollowCoverage.STALLED -> R.string.list_stalled
            }) }).joinToString("\n")
        CheckKind.FAILURE -> event.failure?.let { ApiFailure(it).userMessage(this) }.orEmpty()
    }
    private fun details(event: CheckEvent) {
        val lines = mutableListOf(summary(event), getString(R.string.timeline_started, date(event.startedAt)),
            getString(R.string.timeline_received, date(event.observedAt)),
            getString(if (event.kind == CheckKind.FAILURE) R.string.timeline_failed_at else R.string.timeline_analyzed, date(event.analyzedAt)))
        event.previousAt?.let {
            lines += getString(R.string.timeline_previous, date(it))
            val changed = when (event.kind) {
                CheckKind.PROFILE -> event.followersDelta?.let { count -> count != 0L } == true ||
                    event.followingDelta?.let { count -> count != 0L } == true || event.fields.isNotEmpty()
                CheckKind.LIST -> (event.added ?: 0) > 0 || (event.missing ?: 0) > 0
                CheckKind.FAILURE -> false
            }
            if (changed) lines += getString(R.string.timeline_window)
        }
        if (event.kind == CheckKind.LIST) {
            fun members(users: List<CheckMember>) = users.joinToString("\n") { it.username?.let { name -> "@$name" } ?: "#${it.id}" }.ifEmpty { getString(R.string.ui_none) }
            if (event.added != null) {
                lines += getString(R.string.ui_newly_seen_n) + members(event.newlySeen)
                lines += getString(R.string.ui_n_nmissing_from_this_read_n) + members(event.notSeen)
                if (event.added > event.newlySeen.size || (event.missing ?: 0) > event.notSeen.size) lines += resources.getQuantityString(R.plurals.timeline_member_sample, CheckEvent.MEMBER_SAMPLE, CheckEvent.MEMBER_SAMPLE)
            }
            lines += getString(R.string.auto_difference_scope)
        }
        AlertDialog.Builder(this).setTitle("@${event.username} · ${title(event)}").setMessage(lines.joinToString("\n\n"))
            .setPositiveButton(R.string.list_close, null).show()
    }
    private fun render() {
        if (isFinishing || isDestroyed) return
        val saved = try { vault.read() } catch (_: Exception) { null }
        if (saved == null || saved.session != reference) { finish(); return }
        val profiles = listOf(saved.profile) + saved.people.map { it.latest }
        if (selected != null && profiles.none { it.userId == selected }) { finish(); return }
        val position = scroll?.scrollY ?: 0
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(16), dp(22), dp(24))
            setBackgroundColor(AppUi.background)
        }
        val viewport = ScrollView(this).apply {
            isFillViewport = true; addView(page)
            setOnApplyWindowInsetsListener { view, insets ->
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                insets
            }
        }
        scroll = viewport; setContentView(viewport); viewport.requestApplyInsets()
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(AppUi.iconButton(this, "back", getString(R.string.list_back)) { finish() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(AppUi.heading(this, getString(R.string.timeline_title)), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        page.addView(header); page.gap(12)
        page.addView(AppUi.text(this, getString(R.string.timeline_subtitle), 13f, AppUi.muted)); page.gap(14)
        page.addView(AppUi.button(this, selected?.let { id -> "@${profiles.first { it.userId == id }.username}" } ?: getString(R.string.timeline_all), false) {
            val labels = listOf(getString(R.string.timeline_all)) + profiles.map { "@${it.username}" }
            AlertDialog.Builder(this).setTitle(R.string.timeline_filter).setSingleChoiceItems(labels.toTypedArray(),
                selected?.let { id -> profiles.indexOfFirst { it.userId == id } + 1 } ?: 0) { dialog, index ->
                dialog.dismiss(); selected = if (index == 0) null else profiles[index - 1].userId; scroll = null; render()
            }.setNegativeButton(R.string.list_cancel, null).show()
        }); page.gap(12)
        selected?.let { id ->
            val profile = profiles.first { it.userId == id }
            page.addView(AppUi.text(this, getString(R.string.timeline_profile_last, date(profile.observedAt)), 12f, AppUi.muted)); page.gap(12)
            page.addView(AppUi.button(this, getString(R.string.timeline_open_profile), false) {
                startActivity(Intent(this, MainActivity::class.java).putExtra("notification_owner", saved.session.userId)
                    .putExtra("notification_person", id).putExtra("offline_preview", preview)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)); finish()
            }); page.gap(12)
        }
        val events = try { vault.journal.read(saved.session).filter { selected == null || it.target == selected }.sortedByDescending { it.analyzedAt } }
        catch (_: Exception) {
            page.addView(AppUi.notice(this, getString(R.string.timeline_title), getString(R.string.timeline_unavailable), true)); emptyList()
        }
        if (vault.journal.writeFailed) { page.addView(AppUi.text(this, getString(R.string.timeline_write_failed), 12f, AppUi.coral)); page.gap(12) }
        if (events.isEmpty()) page.addView(AppUi.text(this, getString(R.string.timeline_empty), 15f, AppUi.muted))
        for (event in events) {
            val card = AppUi.card(this).apply {
                background = AppUi.touch(this@TimelineActivity, background)
                isFocusable = true; contentDescription = "@${event.username} · ${title(event)} · ${date(event.observedAt ?: event.analyzedAt)}"
                addView(AppUi.text(this@TimelineActivity, "@${event.username}", 13f, AppUi.violet, true)); gap(8)
                addView(AppUi.heading(this@TimelineActivity, title(event), 17f)); gap(8)
                addView(AppUi.text(this@TimelineActivity, summary(event), 13f)); gap(10)
                addView(AppUi.text(this@TimelineActivity, getString(R.string.timeline_observed, date(event.observedAt)), 11f, AppUi.muted)); gap(4)
                addView(AppUi.text(this@TimelineActivity, getString(R.string.timeline_saved, date(event.analyzedAt)), 11f, AppUi.muted))
                setOnClickListener { details(event) }
            }
            page.addView(card); page.gap(12)
        }
        page.gap(12); page.addView(AppUi.text(this, getString(R.string.timeline_scope), 12f, AppUi.muted))
        viewport.post { viewport.scrollTo(0, position) }
    }
    private fun dp(value: Int) = AppUi.dp(this, value)
    private fun LinearLayout.gap(value: Int) { addView(android.view.View(this@TimelineActivity), LinearLayout.LayoutParams(1, dp(value))) }
    override fun onResume() { super.onResume(); if (!recreatingForLanguage && reference != null) render() }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("selected_target", selected); super.onSaveInstanceState(outState) }
}
