package dev.kaankaya.instagramtracker

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
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
    private val bg = Color.rgb(16, 19, 27)
    private val fg = Color.rgb(242, 244, 235)
    private val mint = Color.rgb(186, 243, 213)
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.DEBUG) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        preview = PreviewMode.enabled(this)
        gate = RequestGate(this, if (preview) "preview_gate" else "request_gate")
        accountVault = SessionVault(this, if (preview) "previewaccount" else "account")
        lists = FollowListVault(this, if (preview) "previewlists" else "followlists")
        source = PreviewMode.followLists(gate, preview)
        direction = FollowDirection.entries.firstOrNull { it.name == savedInstanceState?.getString("direction") } ?: FollowDirection.FOLLOWERS
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
    private fun label(value: String, size: Float = 14f) = TextView(this).apply {
        text = value; textSize = size; setTextColor(fg); setPadding(0, dp(3), 0, dp(3))
    }
    private fun button(title: String, enabled: Boolean = true, primary: Boolean = false, action: () -> Unit) = Button(this).apply {
        text = title; isAllCaps = false; isEnabled = enabled; minimumHeight = dp(46); textSize = 14f
        background = GradientDrawable().apply { setColor(if (primary) mint else Color.rgb(28, 33, 43)); cornerRadius = dp(12).toFloat() }
        setTextColor(if (primary) bg else fg); alpha = if (enabled) 1f else .45f
        setOnClickListener { action() }
    }
    private fun render() {
        if (isDestroyed || !::target.isInitialized) return
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(bg); setPadding(dp(20), dp(12), dp(20), dp(12))
            setOnApplyWindowInsetsListener { view, insets ->
                @Suppress("DEPRECATION")
                view.setPadding(dp(20) + insets.systemWindowInsetLeft, dp(12) + insets.systemWindowInsetTop,
                    dp(20) + insets.systemWindowInsetRight, dp(12) + insets.systemWindowInsetBottom)
                insets
            }
        }
        setContentView(root); root.requestApplyInsets()
        val header = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        header.addView(button("‹") { finish() }.apply { contentDescription = getString(R.string.list_back) }, LinearLayout.LayoutParams(dp(48), dp(46)))
        header.addView(label("  @${target.username}", 22f), LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(header)
        if (preview) root.addView(label(getString(R.string.list_demo), 11f).apply { setTextColor(mint) })
        val tabs = LinearLayout(this)
        FollowDirection.entries.forEach { selected -> tabs.addView(button(directionName(selected), !loading, direction == selected) {
            direction = selected; message = null; render()
        }.apply { isSelected = direction == selected }, LinearLayout.LayoutParams(0, -2, 1f)) }
        root.addView(tabs)
        val run = snapshot?.run(direction)
        root.addView(label(if (run == null) getString(R.string.list_not_read) else getString(R.string.list_count, run.users.size, run.pages.size)))
        if (run != null) {
            root.addView(label(getString(when (run.coverage()) {
                FollowCoverage.MORE -> R.string.list_more
                FollowCoverage.END_OBSERVED -> R.string.list_end
                FollowCoverage.LIMITED -> R.string.list_limited
                FollowCoverage.UNKNOWN -> R.string.list_unknown
                FollowCoverage.STALLED -> R.string.list_stalled
            })))
            root.addView(label(getString(R.string.list_observed, SimpleDateFormat("d MMM HH:mm", Locale.forLanguageTag("tr-TR")).format(Date(run.latest.observedAt))), 12f))
        }
        message?.let { root.addView(label(it)) }
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, run?.users.orEmpty().map(::userLabel)) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = super.getView(position, convertView, parent).apply {
                (this as TextView).setTextColor(fg)
            }
        }
        val view = ListView(this).apply { this.adapter = adapter; dividerHeight = 0; setBackgroundColor(Color.rgb(28, 33, 43)) }
        root.addView(view, LinearLayout.LayoutParams(-1, 0, 1f))
        val allowed = !loading && gate.nextAllowedAt() <= System.currentTimeMillis() && !gate.automationHeld()
        if (run?.coverage() == FollowCoverage.MORE) root.addView(button(getString(R.string.list_next), allowed, true) { read(run.cursor()) })
        root.addView(button(getString(if (run == null) R.string.list_first else R.string.list_restart), allowed, run == null) {
            if (run == null) read(null) else AlertDialog.Builder(this).setTitle(R.string.list_restart)
                .setMessage(R.string.list_restart_message).setNegativeButton(R.string.list_cancel, null)
                .setPositiveButton(R.string.list_first) { _, _ -> read(null) }.show()
        })
        root.addView(button(getString(R.string.list_compare), !loading && snapshot?.let(FollowAnalysis::compare) != null) { compare() })
        if (loading) root.addView(button(getString(R.string.list_cancel)) { cancel(); message = getString(R.string.list_cancelled); render() })
        else if (gate.nextAllowedAt() > System.currentTimeMillis()) root.addView(label(getString(R.string.list_wait,
            SimpleDateFormat("HH:mm:ss", Locale.forLanguageTag("tr-TR")).format(Date(gate.nextAllowedAt())))))
        root.addView(label(getString(R.string.list_scope), 12f))
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
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("direction", direction.name); super.onSaveInstanceState(outState) }
    @SuppressLint("GestureBackNavigation")
    @Deprecated("Legacy callback for Android 7–12; API 33+ uses OnBackInvokedCallback")
    override fun onBackPressed() { finish() }
    override fun onDestroy() { if (::source.isInitialized) cancel(); main.removeCallbacksAndMessages(null); worker.shutdownNow(); super.onDestroy() }
}
