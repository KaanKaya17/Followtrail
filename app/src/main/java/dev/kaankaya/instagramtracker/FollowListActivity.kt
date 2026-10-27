package dev.kaankaya.instagramtracker

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.text.Editable
import android.text.TextWatcher
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.ImageView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Manual list experiment. Opening/reopening this screen never sends a request. */
class FollowListActivity : LocalizedActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var gate: RequestGate
    private lateinit var accountVault: SessionVault
    private lateinit var lists: FollowListVault
    private lateinit var source: FollowListSource
    private lateinit var avatars: AvatarImages
    private lateinit var queuedRead: ForegroundReadQueue
    private lateinit var reference: Session
    private lateinit var target: Profile
    private var snapshot: FollowSnapshot? = null
    private var direction = FollowDirection.FOLLOWERS
    private var message: String? = null
    private var loading = false
    private var generation = 0
    private var pending: Future<*>? = null
    private var preview = false
    private var query = ""
    private var visibleList: ListView? = null
    private var visibleSearch: EditText? = null
    private var actionDock: View? = null
    private var statusCard: View? = null
    private var navigationChrome: List<View> = emptyList()
    private var keyboardVisible = false
    private val bg = AppUi.background
    private val fg = AppUi.ink
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppUi.systemBars(this)
        if (!BuildConfig.DEBUG) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        preview = PreviewMode.enabled(this)
        avatars = AvatarImages(this, PreviewMode.avatarFetcher(preview), if (preview) "previewavatars" else "avatars")
        gate = RequestGate(this, if (preview) "preview_gate" else "request_gate")
        queuedRead = ForegroundReadQueue(this, gate) { reason -> message = reason; render() }
        accountVault = SessionVault(this, if (preview) "previewaccount" else "account")
        lists = FollowListVault(this, if (preview) "previewlists" else "followlists")
        source = PreviewMode.followLists(gate, preview)
        direction = FollowDirection.entries.firstOrNull { it.name == (savedInstanceState?.getString("direction") ?: intent.getStringExtra("initial_direction")) } ?: FollowDirection.FOLLOWERS
        query = savedInstanceState?.getString("query").orEmpty()
        if (android.os.Build.VERSION.SDK_INT >= 33) onBackInvokedDispatcher.registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) { finish() }
        try {
            val account = accountVault.read() ?: throw ApiFailure(ApiFailure.Kind.SESSION_EXPIRED)
            intent.getStringExtra("notification_owner")?.let { if (it != account.session.userId) throw ApiFailure(ApiFailure.Kind.WRONG_ACCOUNT) }
            reference = account.session
            val id = intent.getStringExtra("target_id")
            target = (listOf(account.profile) + account.people.map { it.latest }).firstOrNull { it.userId == id }
                ?: throw ApiFailure(ApiFailure.Kind.NOT_FOUND)
            snapshot = lists.read(reference, target.userId)
        } catch (_: Exception) {
            val label = TextView(this).apply { text = getString(R.string.list_storage_error); setPadding(dp(24), dp(40), dp(24), dp(24)) }
            setContentView(label)
            return
        }
        render()
    }
    private fun button(title: String, enabled: Boolean = true, primary: Boolean = false, action: () -> Unit) =
        AppUi.button(this, title, primary, enabled, action = action)
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun LinearLayout.gap(n: Int) { addView(View(this@FollowListActivity), LinearLayout.LayoutParams(1, dp(n))) }
    private data class UserRow(val initial: ProfileAvatarView, val name: TextView, val detail: TextView)
    private fun render() {
        if (isDestroyed || isFinishing || !::target.isInitialized) return
        val searchFocused = visibleSearch?.isFocused == true
        visibleSearch = null; actionDock = null; statusCard = null; navigationChrome = emptyList()
        val first = visibleList?.firstVisiblePosition ?: 0
        val top = visibleList?.getChildAt(0)?.top ?: 0
        val root = column().apply {
            setBackgroundColor(bg); isFocusableInTouchMode = true
            setOnApplyWindowInsetsListener { view, insets ->
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                    val ime = insets.getInsets(android.view.WindowInsets.Type.ime())
                    keyboardVisible = insets.isVisible(android.view.WindowInsets.Type.ime())
                    view.setPadding(dp(22) + bars.left, dp(12) + bars.top, dp(22) + bars.right, dp(12) + maxOf(bars.bottom, ime.bottom))
                } else {
                    @Suppress("DEPRECATION")
                    keyboardVisible = insets.systemWindowInsetBottom > dp(160)
                    @Suppress("DEPRECATION")
                    view.setPadding(dp(22) + insets.systemWindowInsetLeft, dp(12) + insets.systemWindowInsetTop,
                        dp(22) + insets.systemWindowInsetRight, dp(12) + insets.systemWindowInsetBottom)
                }
                actionDock?.visibility = if (keyboardVisible) View.GONE else View.VISIBLE
                statusCard?.visibility = if (keyboardVisible) View.GONE else View.VISIBLE
                navigationChrome.forEach { it.visibility = if (keyboardVisible) View.GONE else View.VISIBLE }
                insets
            }
        }
        setContentView(root); root.requestApplyInsets(); root.requestFocus()
        val header = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        header.addView(AppUi.iconButton(this, "back", getString(R.string.list_back)) { finish() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        val title = column().apply { setPadding(dp(12), 0, 0, 0) }
        title.addView(AppUi.heading(this, getString(R.string.ui_lists), 22f)); title.gap(4)
        title.addView(AppUi.text(this, "@${target.username}", 12f, AppUi.muted).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(AppUi.iconButton(this, "info", getString(R.string.list_info_title)) { showScope() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        val toolbarBlock = column().apply { addView(header); gap(16) }; root.addView(toolbarBlock)
        if (preview) { root.addView(AppUi.text(this, getString(R.string.list_demo), 10f, AppUi.violet, true)); root.gap(10) }
        val tabs = LinearLayout(this).apply { background = AppUi.shape(this@FollowListActivity, AppUi.violetSoft, 18); setPadding(dp(4), dp(4), dp(4), dp(4)) }
        FollowDirection.entries.forEach { selected -> tabs.addView(button(directionName(selected), !loading && !queuedRead.pending) {
            direction = selected; query = ""; visibleList = null; visibleSearch = null; message = null; render()
        }.apply {
            isSelected = direction == selected
            background = AppUi.touch(this@FollowListActivity, AppUi.shape(this@FollowListActivity, if (isSelected) AppUi.surface else AppUi.violetSoft, 14))
            setTextColor(if (isSelected) AppUi.violet else AppUi.muted); minimumHeight = dp(48); minHeight = dp(48)
            setPadding(dp(8), dp(10), dp(8), dp(10)); textSize = 13f
        }, LinearLayout.LayoutParams(0, -2, 1f)) }
        val tabsBlock = column().apply { addView(tabs); gap(14) }; root.addView(tabsBlock)
        navigationChrome = listOf(toolbarBlock, tabsBlock)
        navigationChrome.forEach { it.visibility = if (keyboardVisible) View.GONE else View.VISIBLE }
        val run = snapshot?.run(direction)
        val status = column().apply { setPadding(dp(14), dp(12), dp(14), dp(12)); background = AppUi.shape(this@FollowListActivity, AppUi.surface, 18, AppUi.line) }
        status.addView(AppUi.text(this, getString(R.string.ui_saved_on_this_device), 11f, AppUi.muted, true)); status.gap(6)
        status.addView(AppUi.text(this, if (run == null) getString(R.string.list_not_read) else getString(R.string.list_count, run.users.size, run.pages.size), 13f, fg, true))
        if (run != null) {
            status.gap(5)
            status.addView(AppUi.text(this, coverageText(run.coverage()), 12f,
                if (run.coverage() == FollowCoverage.END_OBSERVED) AppUi.teal else AppUi.violet))
            status.gap(4)
            status.addView(AppUi.text(this, getString(R.string.list_observed, SimpleDateFormat("d MMM · HH:mm", AppLanguage.locale(this)).format(Date(run.latest.observedAt))), 10f, AppUi.muted))
        }
        statusCard = status; status.visibility = if (keyboardVisible) View.GONE else View.VISIBLE
        val listHeader = column().apply { setBackgroundColor(bg) }
        listHeader.addView(status); listHeader.gap(10)
        if (AppUi.offline(this) == true) {
            listHeader.addView(AppUi.notice(this, getString(R.string.ui_you_are_viewing_offline), getString(R.string.ui_you_can_view_saved_records_an_internet))); listHeader.gap(10)
        }
        message?.let { root.addView(AppUi.notice(this, getString(R.string.ui_last_action), it, warning = true)); root.gap(10) }
        if (loading) {
            val progress = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, dp(8)) }
            progress.addView(android.widget.ProgressBar(this).apply { indeterminateTintList = android.content.res.ColorStateList.valueOf(AppUi.violet) }, LinearLayout.LayoutParams(dp(20), dp(20)))
            progress.addView(AppUi.text(this, getString(R.string.ui_updating_list), 13f, AppUi.violet)); root.addView(progress)
        }
        val empty = column().apply { gravity = android.view.Gravity.CENTER; setPadding(dp(20), dp(24), dp(20), dp(24)) }
        if (run == null) { empty.addView(AppUi.avatar(this, "·", 52, AppUi.mint, AppUi.teal)); empty.gap(12) }
        val emptyTitle = AppUi.text(this, "", 17f, fg, true).apply { gravity = android.view.Gravity.CENTER }
        val emptyDetail = AppUi.text(this, "", 12f, AppUi.muted).apply { gravity = android.view.Gravity.CENTER }
        empty.addView(emptyTitle); empty.gap(6); empty.addView(emptyDetail)
        val searchSummary = AppUi.text(this, "", 11f, AppUi.muted).apply { setPadding(dp(2), 0, 0, dp(8)); accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        fun updateSearchState(count: Int) {
            empty.visibility = if (count == 0) View.VISIBLE else View.GONE
            emptyTitle.text = when { run == null -> getString(R.string.ui_explore_your_list); query.isNotBlank() -> getString(R.string.ui_no_accounts_match_your_search); else -> getString(R.string.ui_no_accounts_in_this_record) }
            emptyDetail.text = when { run == null -> getString(R.string.ui_read_the_first_page_when_you_are); query.isNotBlank() -> getString(R.string.ui_try_another_name_search_does_not_fetch); else -> getString(R.string.ui_only_records_from_downloaded_pages_are_shown) }
            searchSummary.visibility = if (query.isBlank()) View.GONE else View.VISIBLE
            searchSummary.text = getString(R.string.list_search_summary, count, run?.users?.size ?: 0)
        }
        fun filtered() = run?.users.orEmpty().filter {
            query.isBlank() || it.username.orEmpty().contains(query, true) || it.name.orEmpty().contains(query, true)
        }
        var items = filtered()
        val adapter = object : BaseAdapter() {
            override fun getCount() = items.size
            override fun getItem(position: Int) = items[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = convertView ?: LinearLayout(this@FollowListActivity).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(dp(14), dp(14), dp(14), dp(14))
                    val initial = ProfileAvatarView(this@FollowListActivity, 42)
                    addView(initial)
                    val labels = column().apply { setPadding(dp(12), 0, 0, 0) }
                    val name = AppUi.text(this@FollowListActivity, "", 14f, fg, true).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }
                    val detail = AppUi.text(this@FollowListActivity, "", 12f, AppUi.muted).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }
                    labels.addView(name); labels.gap(4); labels.addView(detail)
                    addView(labels, LinearLayout.LayoutParams(0, -2, 1f)); tag = UserRow(initial, name, detail)
                    addView(ImageView(this@FollowListActivity).apply {
                        setImageDrawable(AppUi.Icon(this@FollowListActivity, "arrow", AppUi.violet, 18))
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }, LinearLayout.LayoutParams(dp(24), dp(24)))
                }
                val user = items[position]; val holder = row.tag as UserRow
                holder.initial.bind(avatars, reference.userId, user.id, user.username ?: user.name ?: "?", user.picture,
                    tint = if (position % 2 == 0) AppUi.violetSoft else AppUi.mint, foreground = if (position % 2 == 0) AppUi.violet else AppUi.teal)
                holder.name.text = user.username?.let { "@$it" } ?: getString(R.string.list_username_unknown)
                holder.detail.text = user.name?.takeIf { it.isNotBlank() } ?: getString(R.string.ui_no_name_provided)
                row.contentDescription = user.username?.let { getString(R.string.profile_open_description, it) } ?: getString(R.string.list_username_unknown)
                return row
            }
        }
        val list = ListView(this).apply {
            divider = android.graphics.drawable.ColorDrawable(AppUi.line); dividerHeight = dp(1)
            background = AppUi.shape(this@FollowListActivity, AppUi.surface, 20); clipToOutline = true
            isVerticalScrollBarEnabled = false
        }
        visibleList = list
        if (run != null && run.users.isNotEmpty()) {
            val searchRow = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
            val search = EditText(this).apply {
                hint = getString(R.string.list_search_hint); contentDescription = getString(R.string.ui_search_downloaded_accounts); textSize = 13f
                setSingleLine(true); setTextColor(fg); setHintTextColor(AppUi.muted)
                background = AppUi.shape(this@FollowListActivity, AppUi.surface, 16, AppUi.line)
                setPadding(dp(14), dp(12), dp(14), dp(12)); minimumHeight = dp(48)
                setCompoundDrawablesRelativeWithIntrinsicBounds(AppUi.Icon(this@FollowListActivity, "search", AppUi.muted, 18), null, null, null)
                compoundDrawablePadding = dp(10); setText(query)
                imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                setOnEditorActionListener { _, action, _ ->
                    if (action == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                        (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(windowToken, 0)
                        clearFocus(); root.requestFocus(); true
                    } else false
                }
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                if (android.os.Build.VERSION.SDK_INT >= 26) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s.toString(); items = filtered(); updateSearchState(items.size); adapter.notifyDataSetChanged() }
                    override fun afterTextChanged(s: Editable?) = Unit
                })
            }
            visibleSearch = search
            if (searchFocused) { search.requestFocus(); search.setSelection(search.text.length) }
            searchRow.addView(search, LinearLayout.LayoutParams(0, -2, 1f))
            searchRow.addView(AppUi.iconButton(this, "close", getString(R.string.ui_clear_search)) { search.setText(""); search.requestFocus() }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(8) })
            root.addView(searchRow); root.gap(8); root.addView(searchSummary); root.gap(2)
        }
        updateSearchState(items.size)
        listHeader.addView(empty)
        list.addHeaderView(listHeader, null, false); list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            items.getOrNull(position - list.headerViewsCount)?.let(::openProfile)
        }
        list.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        // Header 0 is not selectable. Selecting it in keyboard mode would skip to user 1.
        if (first > 0) list.setSelectionFromTop(first, top)
        else if (top < 0) list.post { list.scrollListBy(-top) }
        val actions = column().apply { setPadding(0, dp(12), 0, 0) }
        val allowed = !loading && !queuedRead.pending && !gate.automationHeld() && !gate.requiresConnection() && gate.blockedUntil() <= System.currentTimeMillis()
        if (queuedRead.pending) actions.addView(queuedRead.view(this))
        else if (loading) actions.addView(button(getString(R.string.list_cancel), primary = true) {
            cancel(); snapshot = try { lists.read(reference, target.userId) } catch (_: Exception) { snapshot }
            message = getString(R.string.list_cancelled); render()
        })
        else {
            val row = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
            row.addView(button(getString(R.string.ui_update_list), allowed, true) { requestUpdate() }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(AppUi.iconButton(this, "more", getString(R.string.ui_list_actions)) { showActions() }, LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginStart = dp(8) })
            actions.addView(row)
        }
        if (!loading && gate.nextAllowedAt() > System.currentTimeMillis()) {
            val waitText = if (gate.blockedUntil() > System.currentTimeMillis()) {
                getString(R.string.list_pause, SimpleDateFormat("d MMM · HH:mm", AppLanguage.locale(this)).format(Date(gate.blockedUntil())))
            } else getString(R.string.list_wait, SimpleDateFormat("HH:mm:ss", AppLanguage.locale(this)).format(Date(gate.nextAllowedAt())))
            actions.gap(6); actions.addView(AppUi.text(this, waitText, 11f, AppUi.muted))
        }
        if (run != null) { actions.gap(6); actions.addView(AppUi.text(this, getString(R.string.ui_accounts_downloaded, run.users.size) + when (run.coverage()) {
            FollowCoverage.MORE -> if (loading) getString(R.string.ui_reading_more) else getString(R.string.ui_more_available)
            FollowCoverage.END_OBSERVED -> getString(R.string.ui_list_end_observed)
            else -> getString(R.string.ui_read_limited)
        }, 11f, AppUi.muted)) }
        actionDock = actions; actions.visibility = if (keyboardVisible) View.GONE else View.VISIBLE
        root.addView(actions)
        main.removeCallbacks(wake)
        val wait = gate.nextAllowedAt() - System.currentTimeMillis()
        if (wait > 0) main.postDelayed(wake, wait)
    }
    private val wake = Runnable { render() }
    private fun requestUpdate() {
        if (loading || queuedRead.pending) return
        val selected = direction
        fun start() {
            if (direction != selected || isFinishing || isDestroyed) return
            val current = try { lists.read(reference, target.userId)?.run(direction) } catch (_: Exception) { null }
            read(current?.takeIf { it.coverage() == FollowCoverage.MORE }?.cursor(), continuous = true)
        }
        if (queuedRead.canWait() && queuedRead.enqueue { start() }) render() else start()
    }
    private fun showActions() {
        val run = snapshot?.run(direction)
        val choices = mutableListOf(getString(R.string.ui_record_coverage), getString(R.string.list_first))
        if (run?.coverage() == FollowCoverage.MORE) choices += getString(R.string.list_next)
        if (snapshot?.let(FollowAnalysis::compare) != null) choices += getString(R.string.list_compare_action)
        AlertDialog.Builder(this).setTitle(getString(R.string.ui_list_actions)).setItems(choices.toTypedArray()) { _, index ->
            when (choices[index]) {
                getString(R.string.ui_record_coverage) -> showScope()
                getString(R.string.list_first) -> {
                    if (run == null) read(null) else AlertDialog.Builder(this).setTitle(R.string.list_restart).setMessage(R.string.list_restart_message)
                        .setNegativeButton(R.string.list_cancel, null).setPositiveButton(R.string.list_first) { _, _ -> read(null) }.show()
                }
                getString(R.string.list_next) -> read(run?.cursor())
                else -> compare()
            }
        }.setNegativeButton(getString(R.string.ui_close), null).show()
    }
    private fun coverageText(value: FollowCoverage) = getString(when (value) {
        FollowCoverage.MORE -> R.string.list_more
        FollowCoverage.END_OBSERVED -> R.string.list_end
        FollowCoverage.LIMITED -> R.string.list_limited
        FollowCoverage.UNKNOWN -> R.string.list_unknown
        FollowCoverage.STALLED -> R.string.list_stalled
    })
    private fun showScope() {
        val run = snapshot?.run(direction)
        val description = if (run == null) getString(R.string.list_not_read) else
            getString(R.string.list_count, run.users.size, run.pages.size) + "\n" + coverageText(run.coverage()) + "\n" +
                getString(R.string.list_observed, SimpleDateFormat("d MMM yyyy · HH:mm", AppLanguage.locale(this)).format(Date(run.latest.observedAt)))
        AlertDialog.Builder(this).setTitle(R.string.list_info_title)
            .setMessage(description + "\n\n" + getString(R.string.list_info_body))
            .setNeutralButton(R.string.auto_list_differences) { _, _ -> showListDifferences() }
            .setPositiveButton(R.string.list_close, null).show()
    }
    private fun showListDifferences() {
        val record = lists.target(reference, target.userId)
        val difference = record?.let { ListAutomationPolicy.difference(it, direction) }
        val body = if (difference == null) getString(R.string.auto_no_comparison) else {
            getString(R.string.auto_difference_counts, difference.added.size.toString(), difference.missing.size.toString()) + "\n\n" +
                getString(R.string.ui_newly_seen_n) + difference.added.joinToString("\n", transform = ::userLabel).ifEmpty { getString(R.string.ui_none) } +
                getString(R.string.ui_n_nmissing_from_this_read_n) + difference.missing.joinToString("\n", transform = ::userLabel).ifEmpty { getString(R.string.ui_none) }
        }
        AlertDialog.Builder(this).setTitle(R.string.auto_list_differences).setMessage(body + "\n\n" + getString(R.string.auto_difference_scope))
            .setNeutralButton(R.string.timeline_title) { _, _ ->
                startActivity(Intent(this, TimelineActivity::class.java).putExtra("viewer_id", reference.userId)
                    .putExtra("target_id", target.userId).putExtra("offline_preview", preview))
            }.setPositiveButton(R.string.list_close, null).show()
    }
    private fun directionName(value: FollowDirection) = getString(if (value == FollowDirection.FOLLOWERS) R.string.list_followers else R.string.list_following)
    private fun userLabel(user: FollowUser) = (user.username?.let { "@$it" } ?: getString(R.string.list_username_unknown)) +
        user.name?.takeIf { it.isNotBlank() }?.let { "\n$it" }.orEmpty()
    private fun currentAccount(account: SavedAccount?) = account?.session == reference &&
        (account.profile.userId == target.userId || account.people.any { it.latest.userId == target.userId })
    private fun read(cursor: String?, continuous: Boolean = false) {
        if (loading) return
        if (gate.automationHeld()) { message = getString(R.string.list_finish_connection); render(); return }
        if (!preview && !reference.supportsLists) { message = ApiFailure(ApiFailure.Kind.MOBILE_REQUIRED).userMessage(this@FollowListActivity); render(); return }
        val selected = direction
        val ticket = ++generation
        loading = true; message = null; render()
        pending = worker.submit {
            val startedAt = System.currentTimeMillis()
            val result = try {
                accountVault.inspect { if (!currentAccount(it)) throw ApiFailure(ApiFailure.Kind.CANCELLED) }
                if (gate.requiresConnection()) throw ApiFailure(ApiFailure.Kind.SESSION_EXPIRED)
                val result = ForegroundListScan(accountVault, lists, gate, source).read(reference, target.userId, selected, cursor,
                    pages = if (continuous) 6 else 1, cancelled = { ticket != generation || Thread.currentThread().isInterrupted },
                    progress = { updated -> main.post {
                        if (!isDestroyed && !isFinishing && ticket == generation) { snapshot = updated; render() }
                    } }, notify = { _, difference -> ChangeNotifications(this, preview).postList(reference, target, difference) })
                Result.success(result)
            } catch (failure: ApiFailure) {
                accountVault.recordFailure(reference, target.userId, startedAt, failure)
                Result.failure(failure)
            }
            catch (_: Exception) { Result.failure(ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)) }
            main.post {
                if (isDestroyed || ticket != generation) return@post
                loading = false
                result.onSuccess { snapshot = it }.onFailure {
                    snapshot = try { lists.read(reference, target.userId) } catch (_: Exception) { snapshot }
                    message = (it as ApiFailure).userMessage(this@FollowListActivity)
                }
                render()
            }
        }
    }
    private fun compare() {
        val value = snapshot ?: return
        val comparison = FollowAnalysis.compare(value) ?: return
        val options = arrayOf(getString(R.string.list_mutual, comparison.mutual.size),
            getString(R.string.list_followers_only, comparison.followersOnly.size), getString(R.string.list_following_only, comparison.followingOnly.size))
        AlertDialog.Builder(this).setTitle(R.string.list_compare).setItems(options) { _, index ->
            val users = when (index) { 0 -> comparison.mutual; 1 -> comparison.followersOnly; else -> comparison.followingOnly }
            AlertDialog.Builder(this).setTitle(options[index]).setMessage(getString(R.string.list_comparison_scope) + "\n\n" +
                users.joinToString("\n", transform = ::userLabel)).setPositiveButton(R.string.list_close, null).show()
        }.setNegativeButton(R.string.list_close, null).show()
    }
    private fun cancel() { generation++; source.cancel(); pending?.cancel(true); pending = null; loading = false }
    private fun openProfile(user: FollowUser) {
        startActivity(Intent(this, MemberProfileActivity::class.java).putExtra("viewer_id", reference.userId)
            .putExtra("list_target_id", target.userId).putExtra("list_direction", direction.name)
            .putExtra("member_id", user.id).putExtra("offline_preview", preview))
    }
    override fun onPause() { if (::queuedRead.isInitialized) queuedRead.cancel(false); if (::source.isInitialized) cancel(); main.removeCallbacks(wake); super.onPause() }
    override fun onResume() {
        super.onResume()
        if (recreatingForLanguage || !::target.isInitialized) return
        try {
            if (!currentAccount(accountVault.read())) { finish(); return }
            snapshot = lists.read(reference, target.userId)
        } catch (_: Exception) { message = ApiFailure(ApiFailure.Kind.STORAGE).userMessage(this@FollowListActivity) }
        render()
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("direction", direction.name); outState.putString("query", query); super.onSaveInstanceState(outState) }
    @SuppressLint("GestureBackNavigation")
    @Deprecated("Legacy callback for Android 7–12; API 33+ uses OnBackInvokedCallback")
    override fun onBackPressed() { finish() }
    override fun onDestroy() {
        if (::avatars.isInitialized) avatars.close()
        if (::source.isInitialized) cancel(); main.removeCallbacksAndMessages(null); worker.shutdownNow(); super.onDestroy()
    }
    override fun onStop() { if (::avatars.isInitialized) avatars.cancelAll(); super.onStop() }
}
