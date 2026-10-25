package dev.kaankaya.instagramtracker

import android.annotation.SuppressLint
import android.app.Activity
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Manual list experiment. Opening/reopening this screen never sends a request. */
class FollowListActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var gate: RequestGate
    private lateinit var accountVault: SessionVault
    private lateinit var lists: FollowListVault
    private lateinit var source: FollowListSource
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
    private var keyboardVisible = false
    private val bg = AppUi.background
    private val fg = AppUi.ink
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppUi.systemBars(this)
        if (!BuildConfig.DEBUG) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        preview = PreviewMode.enabled(this)
        gate = RequestGate(this, if (preview) "preview_gate" else "request_gate")
        accountVault = SessionVault(this, if (preview) "previewaccount" else "account")
        lists = FollowListVault(this, if (preview) "previewlists" else "followlists")
        source = PreviewMode.followLists(gate, preview)
        direction = FollowDirection.entries.firstOrNull { it.name == (savedInstanceState?.getString("direction") ?: intent.getStringExtra("initial_direction")) } ?: FollowDirection.FOLLOWERS
        query = savedInstanceState?.getString("query").orEmpty()
        if (android.os.Build.VERSION.SDK_INT >= 33) onBackInvokedDispatcher.registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) { finish() }
        try {
            val account = accountVault.read() ?: throw ApiFailure(ApiFailure.Kind.SESSION_EXPIRED)
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
    private data class UserRow(val initial: TextView, val name: TextView, val detail: TextView)
    private fun render() {
        if (isDestroyed || !::target.isInitialized) return
        val searchFocused = visibleSearch?.isFocused == true
        visibleSearch = null; actionDock = null; statusCard = null
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
                insets
            }
        }
        setContentView(root); root.requestApplyInsets(); root.requestFocus()
        val header = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        header.addView(AppUi.iconButton(this, "back", getString(R.string.list_back)) { finish() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        val title = column().apply { setPadding(dp(12), 0, 0, 0) }
        title.addView(AppUi.text(this, "Bağlantılar", 22f, fg, true)); title.gap(4)
        title.addView(AppUi.text(this, "@${target.username}", 12f, AppUi.muted).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f)); root.addView(header); root.gap(18)
        if (preview) { root.addView(AppUi.text(this, getString(R.string.list_demo), 10f, AppUi.violet, true)); root.gap(10) }
        val tabs = LinearLayout(this).apply { background = AppUi.shape(this@FollowListActivity, AppUi.violetSoft, 18); setPadding(dp(4), dp(4), dp(4), dp(4)) }
        FollowDirection.entries.forEach { selected -> tabs.addView(button(directionName(selected), !loading) {
            direction = selected; query = ""; visibleList = null; visibleSearch = null; message = null; render()
        }.apply {
            isSelected = direction == selected
            background = AppUi.touch(this@FollowListActivity, AppUi.shape(this@FollowListActivity, if (isSelected) AppUi.surface else AppUi.violetSoft, 14))
            setTextColor(if (isSelected) AppUi.violet else AppUi.muted); minimumHeight = dp(46); minHeight = dp(46)
            setPadding(dp(8), dp(10), dp(8), dp(10)); textSize = 13f
        }, LinearLayout.LayoutParams(0, -2, 1f)) }
        root.addView(tabs); root.gap(14)
        val run = snapshot?.run(direction)
        val status = column().apply { setPadding(dp(14), dp(12), dp(14), dp(12)); background = AppUi.shape(this@FollowListActivity, AppUi.surface, 18, AppUi.line) }
        status.addView(AppUi.text(this, if (run == null) getString(R.string.list_not_read) else getString(R.string.list_count, run.users.size, run.pages.size), 13f, fg, true))
        if (run != null) {
            status.gap(5)
            status.addView(AppUi.text(this, getString(when (run.coverage()) {
                FollowCoverage.MORE -> R.string.list_more
                FollowCoverage.END_OBSERVED -> R.string.list_end
                FollowCoverage.LIMITED -> R.string.list_limited
                FollowCoverage.UNKNOWN -> R.string.list_unknown
                FollowCoverage.STALLED -> R.string.list_stalled
            }), 12f, if (run.coverage() == FollowCoverage.END_OBSERVED) AppUi.teal else AppUi.violet))
            status.gap(4)
            status.addView(AppUi.text(this, getString(R.string.list_observed, SimpleDateFormat("d MMM HH:mm", Locale.forLanguageTag("tr-TR")).format(Date(run.latest.observedAt))), 10f, AppUi.muted))
        }
        statusCard = status; status.visibility = if (keyboardVisible) View.GONE else View.VISIBLE
        val listHeader = column().apply { setBackgroundColor(bg) }
        listHeader.addView(status); listHeader.gap(10)
        message?.let { listHeader.addView(AppUi.text(this, it, 13f, AppUi.coral)); listHeader.gap(8) }
        val empty = column().apply { gravity = android.view.Gravity.CENTER; setPadding(dp(20), dp(24), dp(20), dp(24)) }
        if (run == null) { empty.addView(AppUi.avatar(this, "·", 52, AppUi.mint, AppUi.teal)); empty.gap(12) }
        empty.addView(AppUi.text(this, if (run == null) "Listeni keşfet" else "Gösterilecek hesap yok", 17f, fg, true).apply { gravity = android.view.Gravity.CENTER })
        empty.gap(6); empty.addView(AppUi.text(this, if (run == null) "Hazır olduğunda ilk sayfayı oku." else "Yalnız alınan kayıtlar burada görünür.", 12f, AppUi.muted).apply { gravity = android.view.Gravity.CENTER })
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
                    val initial = AppUi.avatar(this@FollowListActivity, "?", 42)
                    addView(initial)
                    val labels = column().apply { setPadding(dp(12), 0, 0, 0) }
                    val name = AppUi.text(this@FollowListActivity, "", 14f, fg, true).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }
                    val detail = AppUi.text(this@FollowListActivity, "", 12f, AppUi.muted).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }
                    labels.addView(name); labels.gap(4); labels.addView(detail)
                    addView(labels, LinearLayout.LayoutParams(0, -2, 1f)); tag = UserRow(initial, name, detail)
                }
                val user = items[position]; val holder = row.tag as UserRow
                holder.initial.text = (user.username ?: user.name ?: "?").take(1).uppercase(Locale.forLanguageTag("tr-TR"))
                holder.initial.background = AppUi.shape(this@FollowListActivity, if (position % 2 == 0) AppUi.violetSoft else AppUi.mint, 24)
                holder.initial.setTextColor(if (position % 2 == 0) AppUi.violet else AppUi.teal)
                holder.name.text = user.username?.let { "@$it" } ?: getString(R.string.list_username_unknown)
                holder.detail.text = user.name?.takeIf { it.isNotBlank() } ?: "Ad bilgisi yok"
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
            val search = EditText(this).apply {
                hint = "Alınan hesaplarda ara"; contentDescription = "Alınan hesaplarda ara"; textSize = 13f
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
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s.toString(); items = filtered(); empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE; adapter.notifyDataSetChanged() }
                    override fun afterTextChanged(s: Editable?) = Unit
                })
            }
            visibleSearch = search
            if (searchFocused) { search.requestFocus(); search.setSelection(search.text.length) }
            root.addView(search); root.gap(10)
        }
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        listHeader.addView(empty)
        list.addHeaderView(listHeader, null, false); list.adapter = adapter
        list.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        // Header 0 is not selectable. Selecting it in keyboard mode would skip to user 1.
        if (first > 0) list.setSelectionFromTop(first, top)
        else if (top < 0) list.post { list.scrollListBy(-top) }
        val actions = column().apply { setPadding(0, dp(12), 0, 0) }
        val allowed = !loading && gate.nextAllowedAt() <= System.currentTimeMillis() && !gate.automationHeld()
        fun restart() {
            AlertDialog.Builder(this).setTitle(R.string.list_restart).setMessage(R.string.list_restart_message)
                .setNegativeButton(R.string.list_cancel, null).setPositiveButton(R.string.list_first) { _, _ -> read(null) }.show()
        }
        if (loading) actions.addView(button(getString(R.string.list_cancel), primary = true) { cancel(); message = getString(R.string.list_cancelled); render() })
        else if (run?.coverage() == FollowCoverage.MORE) actions.addView(button(getString(R.string.list_next), allowed, true) { read(run.cursor()) })
        else if (run == null) actions.addView(button(getString(R.string.list_first), allowed, true) { read(null) })
        else actions.addView(button(getString(R.string.list_restart_action), allowed, true) { restart() })
        if (run != null) {
            val secondary = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
            secondary.addView(button(getString(R.string.list_compare_action), !loading && snapshot?.let(FollowAnalysis::compare) != null) { compare() }, LinearLayout.LayoutParams(0, -2, 1f))
            if (run.coverage() == FollowCoverage.MORE) secondary.addView(AppUi.iconButton(this, "refresh", getString(R.string.list_restart)) { restart() }.apply {
                isEnabled = allowed; alpha = if (allowed) 1f else .48f
            }, LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginStart = dp(8) })
            actions.gap(8); actions.addView(secondary)
        }
        if (!loading && gate.nextAllowedAt() > System.currentTimeMillis()) {
            actions.gap(6); actions.addView(AppUi.text(this, getString(R.string.list_wait,
                SimpleDateFormat("HH:mm:ss", Locale.forLanguageTag("tr-TR")).format(Date(gate.nextAllowedAt()))), 11f, AppUi.muted))
        }
        actions.gap(6); actions.addView(AppUi.text(this, getString(R.string.list_scope), 10f, AppUi.muted))
        actionDock = actions; actions.visibility = if (keyboardVisible) View.GONE else View.VISIBLE
        root.addView(actions)
        main.removeCallbacks(wake)
        val wait = gate.nextAllowedAt() - System.currentTimeMillis()
        if (wait > 0) main.postDelayed(wake, wait)
    }
    private val wake = Runnable { render() }
    private fun directionName(value: FollowDirection) = getString(if (value == FollowDirection.FOLLOWERS) R.string.list_followers else R.string.list_following)
    private fun userLabel(user: FollowUser) = (user.username?.let { "@$it" } ?: getString(R.string.list_username_unknown)) +
        user.name?.takeIf { it.isNotBlank() }?.let { "\n$it" }.orEmpty()
    private fun currentAccount(account: SavedAccount?) = account?.session == reference &&
        (account.profile.userId == target.userId || account.people.any { it.latest.userId == target.userId })
    private fun read(cursor: String?) {
        if (loading) return
        if (gate.automationHeld()) { message = getString(R.string.list_finish_connection); render(); return }
        if (!preview && reference.mobileBinding == null) { message = ApiFailure(ApiFailure.Kind.MOBILE_REQUIRED).userMessage(); render(); return }
        val selected = direction
        val ticket = ++generation
        loading = true; message = null; render()
        pending = worker.submit {
            val result = try {
                accountVault.inspect { if (!currentAccount(it)) throw ApiFailure(ApiFailure.Kind.CANCELLED) }
                if (gate.requiresConnection()) throw ApiFailure(ApiFailure.Kind.SESSION_EXPIRED)
                val page = source.read(reference, target.userId, selected, cursor)
                Result.success(page)
            } catch (failure: ApiFailure) { Result.failure(failure) }
            catch (_: Exception) { Result.failure(ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)) }
            main.post {
                if (isDestroyed || ticket != generation) return@post
                loading = false
                result.onSuccess { page ->
                    try {
                        accountVault.inspect { current ->
                            if (!currentAccount(current)) throw ApiFailure(ApiFailure.Kind.CANCELLED)
                            snapshot = lists.commit(reference, page)
                        }
                    } catch (failure: ApiFailure) { message = failure.userMessage() }
                    catch (_: Exception) { message = ApiFailure(ApiFailure.Kind.STORAGE).userMessage() }
                }.onFailure { message = (it as ApiFailure).userMessage() }
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
    override fun onPause() { if (::source.isInitialized) cancel(); main.removeCallbacks(wake); super.onPause() }
    override fun onResume() {
        super.onResume()
        if (!::target.isInitialized) return
        try {
            if (!currentAccount(accountVault.read())) { finish(); return }
            snapshot = lists.read(reference, target.userId)
        } catch (_: Exception) { message = ApiFailure(ApiFailure.Kind.STORAGE).userMessage() }
        render()
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("direction", direction.name); outState.putString("query", query); super.onSaveInstanceState(outState) }
    @SuppressLint("GestureBackNavigation")
    @Deprecated("Legacy callback for Android 7–12; API 33+ uses OnBackInvokedCallback")
    override fun onBackPressed() { finish() }
    override fun onDestroy() { if (::source.isInitialized) cancel(); main.removeCallbacksAndMessages(null); worker.shutdownNow(); super.onDestroy() }
}
