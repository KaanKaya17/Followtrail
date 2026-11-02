package dev.kaankaya.instagramtracker

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.EditText
import android.text.InputFilter
import java.io.ByteArrayInputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.Executors

class MainActivity : LocalizedActivity() {
    private data class ProfileRead(val profile: Profile, val fetched: Boolean, val startedAt: Long? = null)
    private val bg get() = AppUi.background
    private val panel get() = AppUi.surface
    private val accent get() = AppUi.violet
    private val ink get() = AppUi.ink
    private val muted get() = AppUi.muted
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var api: InstagramApi
    private lateinit var gate: RequestGate
    private lateinit var vault: SessionVault
    private lateinit var avatars: AvatarImages
    private lateinit var queuedRead: ForegroundReadQueue
    private lateinit var quickWatch: QuickWatch
    private lateinit var preparation: InitialListPreparation
    private lateinit var profileStatuses: ProfileStatusCards
    private lateinit var root: FrameLayout
    private var account: SavedAccount? = null
    private var loading = true
    private var message: String? = null
    private var needsConnection = false
    private var web: WebView? = null
    private var loginStatus: TextView? = null
    private var loginHeading: TextView? = null
    private var loginProgress: ProgressBar? = null
    private var loginSecurityReset = false
    private val loginLoadTimeout = Runnable {
        if (web != null && !loginLoaded && (!loginRelayIssue || loginChallenge?.authorizedSecurityPage==true) && !loginChecking)
            endLoginVerificationUnavailable(R.string.web_login_page_timeout)
    }
    private var completionButton: Button? = null
    @Volatile private var generation = 0
    private var webAttempt: WebConnectionAttempt? = null
    private var webComparison = false
    private var browserProbe: BrowserProfileProbe? = null
    private var browserProof: BrowserConnectionProof? = null
    private var nativeComparisonStarted = false
    private var webFixture = false
    private var loginChecking = false
    private var loginAttempted = false
    private var loginLoaded = false
    private var loginTrace: LoginPageTrace? = null
    private var loginRelay: WebLoginRelay? = null
    private var loginRelayIssue = false
    private var loginAuthenticating = false
    private var autoVerifyLogin = false
    @Volatile private var loginChallenge: WebLoginChallenge? = null
    private var challengeButton: Button? = null
    private var directNativeButton: Button? = null
    private var localAccountView = false
    private var loginTraceFailed = false
    private var loginPageEpoch = 0L
    private val inspectPage = Runnable { inspectLoginPage() }
    private var peopleScreen = false
    private var peopleQuery = ""
    private var personSearch: ProfileSearchDialog? = null
    private var selectedPersonId: String? = null
    private var visiblePeople = 20
    private var rangeDays = 0
    private var chartFollowers = true
    private var preview = false
    private var pendingMobile = false
    private var pendingWeb = false
    private var pendingWatchId: String? = null
    private var pendingWatchOwner: String? = null
    private var pendingWatchMinutes = 15
    private var pendingQuickTarget: String? = null
    private var visible = false
    private var pendingAutomationOwner: String? = null
    private var pendingAutomationTargets: Set<String>? = null
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppUi.systemBars(this)
        if (!BuildConfig.DEBUG) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        root = FrameLayout(this).apply { setBackgroundColor(bg) }
        root.setOnApplyWindowInsetsListener { view, insets ->
            @Suppress("DEPRECATION")
            view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        setContentView(root)
        root.requestApplyInsets()
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                navigateBack()
            }
        }
        preview = PreviewMode.enabled(this)
        localAccountView=intent.getBooleanExtra("local_account_switch",false)
        if(!preview)intent.getStringExtra("notification_owner")?.let {owner->
            try {if(AccountCatalog(this).entries().any {it.id==owner})AccountCatalog(this).select(owner)}catch (_: Exception) { }
        }
        avatars = AvatarImages(this, PreviewMode.avatarFetcher(preview), if (preview) "previewavatars" else "avatars")
        peopleScreen = savedInstanceState?.getBoolean("people") ?: false
        peopleQuery = savedInstanceState?.getString("peopleQuery").orEmpty()
        selectedPersonId = savedInstanceState?.getString("person")
        if(localAccountView) {peopleScreen=false;selectedPersonId=null}
        visiblePeople = (savedInstanceState?.getInt("visiblePeople", 20) ?: 20).coerceIn(20, PeoplePolicy.MAX_PEOPLE)
        rangeDays = savedInstanceState?.getInt("range") ?: 0
        pendingWatchId = savedInstanceState?.getString("pendingWatch")
        pendingWatchOwner = savedInstanceState?.getString("pendingOwner")
        pendingWatchMinutes = savedInstanceState?.getInt("pendingMinutes", 15) ?: 15
        pendingQuickTarget = savedInstanceState?.getString("pendingQuickTarget")
        pendingAutomationOwner = savedInstanceState?.getString("pendingAutomationOwner")
        pendingAutomationTargets = savedInstanceState?.getStringArrayList("pendingAutomationTargets")?.toSet()
        vault = SessionVault(this, if (preview) "previewaccount" else "account")
        gate = RequestGate(this, if (preview) "preview_gate" else "request_gate")
        queuedRead = ForegroundReadQueue(this, gate) { reason -> message = reason; render() }
        api = PreviewMode.api(gate, preview)
        quickWatch = QuickWatch(vault, FollowListVault(this, if (preview) "previewlists" else "followlists"), gate,
            { PreviewMode.api(gate, preview) }, { PreviewMode.followLists(gate, preview) }, ChangeNotifications(this, preview), changed = { failure ->
            if (!isDestroyed && !isFinishing) {
                try { account = vault.read() } catch (_: Exception) { message = ApiFailure(ApiFailure.Kind.STORAGE).userMessage(this) }
                if (failure != null) message = failure.userMessage(this)
                syncMonitoring(); render()
            }
        })
        profileStatuses = ProfileStatusCards(this, vault, FollowListVault(this, if (preview) "previewlists" else "followlists"),
            gate, preview, { quickWatch.target.takeIf { quickWatch.active } to quickWatch.busy }, ::openTimeline, { openLists(it) })
        preparation = InitialListPreparation(this, vault, FollowListVault(this, if (preview) "previewlists" else "followlists"), gate,
            { PreviewMode.followLists(gate, preview) }, { visible && !loading && web == null && !quickWatch.active && !queuedRead.pending && !WebLoginMode.enabled(this) }, {
                try { account = vault.read(); syncMonitoring(); render() } catch (_: Exception) { needsConnection = true }
            }, { saved, difference -> ChangeNotifications(this, preview).postList(saved.session, saved.profile, difference) })
        if (BuildConfig.DEBUG && intent.getBooleanExtra("connection_lab_preview", false)) {
            intent.removeExtra("connection_lab_preview")
            startActivity(Intent(this, ConnectionLabActivity::class.java).putExtra("offline_lab", true))
        }
        render()
        worker.execute {
            val saved = try { vault.read() ?: PreviewMode.seed(preview)?.also { vault.write(it) } } catch (_: Exception) {
                main.post { message = ApiFailure(ApiFailure.Kind.STORAGE).userMessage(this@MainActivity); needsConnection = true }
                null
            }
            val pendingConnection = try { !preview && MobileSessions(this).pending() != null } catch (_: Exception) { false }
            val pendingWebConnection = try { !preview && WebSessions(this).pending() != null } catch (_: Exception) { false }
            main.post {
                if (isDestroyed) return@post
                account = saved; pendingMobile = pendingConnection; pendingWeb = pendingWebConnection; loading = false; needsConnection = needsConnection || gate.requiresConnection()
                if (saved != null) { quickWatch.restore(savedInstanceState, saved); if (visible) quickWatch.resume() }
                openNotification(saved); syncMonitoring(); render()
                // An outstanding pause is restored without issuing a network request.
                if (!localAccountView && !quickWatch.active && !interfaceChangedOnRecreation && saved != null && !peopleScreen && gate.autoAllowed(saved.profile)) refresh(automatic = true)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (web != null) { main.removeCallbacks(inspectPage); main.postDelayed(inspectPage, 12000) }
        visible = true
        if (web != null) { scheduleGateWake(); continueAuthenticatedLogin() }
        if (::profileStatuses.isInitialized) profileStatuses.resume()
        if (recreatingForInterface) return
        if (!::gate.isInitialized || web != null || loading) return
        val previousOwner=account?.session?.userId
        try { account = vault.read(); pendingMobile = !preview && MobileSessions(this).pending() != null; pendingWeb = !preview && WebSessions(this).pending() != null; needsConnection = gate.requiresConnection() }
        catch (_: Exception) { message = ApiFailure(ApiFailure.Kind.STORAGE).userMessage(this@MainActivity); needsConnection = true }
        if(!preview && previousOwner!=null && previousOwner!=account?.session?.userId) {intent.putExtra("local_account_switch",true);recreate();return}
        if (::quickWatch.isInitialized) quickWatch.resume()
        syncMonitoring()
        if (!localAccountView && !quickWatch.active && account != null && !peopleScreen && !needsConnection && gate.autoAllowed(account!!.profile)) refresh(automatic = true)
        else render()
    }

    private fun shape(color: Int, radius: Int = 24, stroke: Int? = null) = AppUi.shape(this, color, radius, stroke)
    private fun avatar(profile: Profile, size: Int, tint: Int = AppUi.violetSoft, foreground: Int = accent) = ProfileAvatarView(this, size).apply {
        bind(avatars, account!!.session.userId, profile.userId, profile.username,
            profile.details.pictureUrl.value.takeUnless { profile.details.anonymousPicture == true }, profile.details.pictureId.value, tint, foreground)
    }
    private fun text(value: String, size: Float, color: Int = ink, medium: Boolean = false) = AppUi.text(this, value, size, color, medium)
    private fun stack() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun LinearLayout.gap(value: Int) { addView(View(this@MainActivity), LinearLayout.LayoutParams(1, dp(value))) }
    private fun button(label: String, primary: Boolean = true, networkAction: Boolean = false, action: () -> Unit) =
        AppUi.button(this, label, primary, !loading && (!networkAction || gate.nextAllowedAt() <= System.currentTimeMillis() ||
            (label in listOf(getString(R.string.ui_refresh), getString(R.string.ui_refresh_person)) && queuedRead.canWait())),
            when (label) { getString(R.string.ui_refresh), getString(R.string.ui_refresh_person) -> "refresh"; getString(R.string.ui_add_person) -> "plus"; getString(R.string.ui_view_lists) -> "list"; else -> null }, action)
    private fun badge(label: String, color: Int = accent) = AppUi.pill(this, label, color)
    private var visibleScroll: ScrollView? = null
    private var screenIdentity = ""
    private var queuedScreen = ""
    private fun queueRead(action: () -> Unit): Boolean {
        if (queuedRead.pending) return true
        if (!queuedRead.canWait()) return false
        val session = account?.session ?: return false
        queuedScreen = if (peopleScreen) "people:$selectedPersonId" else "overview"
        if (!queuedRead.enqueue {
                val screen = if (peopleScreen) "people:$selectedPersonId" else "overview"
                if (!isDestroyed && !isFinishing && account?.session == session && screen == queuedScreen) action()
            }) return false
        render(); return true
    }
    private fun render() {
        if (web != null || isDestroyed) return
        val identity = if (peopleScreen) "people:$selectedPersonId" else "overview"
        val position = if (identity == screenIdentity) visibleScroll?.scrollY ?: 0 else 0
        screenIdentity = identity
        if (::queuedRead.isInitialized && queuedRead.pending && queuedScreen != identity) queuedRead.cancel(false)
        if (::profileStatuses.isInitialized) profileStatuses.clear()
        root.removeAllViews()
        val saved = account
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false; AppUi.scrollStyle(this) }
        visibleScroll = scroll
        val page = stack().apply { setPadding(dp(22), dp(16), dp(22), dp(28)) }
        val shell = stack()
        root.addView(shell, FrameLayout.LayoutParams(-1, -1))
        scroll.addView(page, ViewGroup.LayoutParams(-1, -2))
        shell.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(text("", 20f).apply {
            background = AppUi.brandSurface(this@MainActivity, radius = 16); gravity = Gravity.CENTER
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setCompoundDrawablesWithIntrinsicBounds(AppUi.Icon(this@MainActivity, "chart", UiPalette.onAccent, 24), null, null, null)
            contentDescription = "InstagramTracker"
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        val brand = stack().apply { setPadding(dp(12), 0, 0, 0) }
        brand.addView(text("Tracker", 21f, ink, true)); brand.gap(3); brand.addView(text(getString(R.string.ui_your_social_circle_together), 11f, muted))
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(AppUi.iconButton(this, "more", getString(R.string.ui_options)) { options() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        page.addView(header); page.gap(24)
        if(!preview) {
            page.addView(button(getString(R.string.accounts_current,saved?.profile?.username ?: getString(R.string.accounts_none)),false) {accountPicker()})
            page.gap(12)
        }
        if (preview) { page.addView(badge(getString(R.string.ui_demo_sample_data)).apply { contentDescription = getString(R.string.ui_offline_demo_sample_data_no_instagram_requests) }); page.gap(16) }
        if (::quickWatch.isInitialized && quickWatch.active && saved != null) renderQuickWatch(page, saved)
        if (::queuedRead.isInitialized && queuedRead.pending) { page.addView(queuedRead.view(this)); page.gap(16) }
        val pause = pauseMessage()
        if (pause != null) {
            val notice = stack().apply { background = shape(AppUi.peach, 20); setPadding(dp(18), dp(16), dp(18), dp(16)) }
            notice.addView(text(if (gate.blockedUntil() > System.currentTimeMillis()) getString(R.string.ui_time_for_a_break) else getString(R.string.ui_only_when_you_refresh), 16f, AppUi.coral, true))
            notice.gap(6); notice.addView(text(pause, 13f, ink))
            notice.gap(5); notice.addView(text(getString(R.string.ui_no_automatic_retry_when_the_wait_ends), 12f, muted))
            notice.contentDescription = getString(R.string.ui_limit_information); notice.setOnClickListener { requestInfo() }
            page.addView(notice); page.gap(16)
        }
        if (saved != null && AppUi.offline(this) == true) {
            page.addView(AppUi.notice(this, getString(R.string.ui_you_are_viewing_offline), getString(R.string.ui_showing_records_on_this_device_an_internet))); page.gap(16)
        }
        if (saved == null) {
            page.addView(text(getString(R.string.ui_your_account_nat_a_glance), 38f, ink, true)); page.gap(12)
            page.addView(text(getString(R.string.ui_your_followers_and_following_in_one_place), 16f, muted))
            page.addView(OrbitView(this), LinearLayout.LayoutParams(-1, dp(204)))
        } else if (peopleScreen) renderPeople(page, saved)
        else renderOverview(page, saved)
        if (loading) {
            val progress = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            progress.addView(ProgressBar(this).apply { indeterminateTintList = android.content.res.ColorStateList.valueOf(accent) }, LinearLayout.LayoutParams(dp(20), dp(20)))
            progress.addView(text(getString(R.string.ui_updating), 14f, muted)); page.addView(progress); page.gap(16)
        }
        message?.takeIf { it != pause }?.let {
            page.addView(text(it, 14f, accent).apply { background = shape(AppUi.violetSoft, 16); setPadding(dp(16), dp(14), dp(16), dp(14)) }); page.gap(14)
        }
        if (pendingWeb) {
            page.addView(button(getString(R.string.ui_complete_connection)) {
                startActivity(Intent(this, WebPendingConnectionActivity::class.java))
            }, LinearLayout.LayoutParams(-1, -2))
        }
        if (saved == null || needsConnection) {
            if (!pendingWeb) page.addView(button(if (needsConnection) getString(R.string.ui_reconnect_account) else if (pendingMobile) getString(R.string.ui_complete_connection) else getString(R.string.ui_connect_account)) {
                if (pendingWeb) startActivity(Intent(this, WebPendingConnectionActivity::class.java))
                else if (pendingMobile && !needsConnection) startActivity(Intent(this, DesktopLinkActivity::class.java)) else connectionOptions()
            }, LinearLayout.LayoutParams(-1, -2))
        } else if (!peopleScreen) {
            val allowed = maxOf(gate.nextAllowedAt(), saved.profile.observedAt + PeoplePolicy.FRESH_MS)
            val refreshRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            val detail = stack()
            detail.addView(text(getString(R.string.ui_refresh_title), 11f, muted)); detail.gap(4); detail.addView(text(getString(R.string.ui_update_saved_record), 12f, ink, true))
            refreshRow.addView(detail, LinearLayout.LayoutParams(0, -2, 1f))
            refreshRow.addView(button(getString(R.string.ui_refresh), false, networkAction = true) { refresh() }.apply {
                isEnabled = !loading && !queuedRead.pending && PeoplePolicy.needsRefresh(saved.profile, System.currentTimeMillis()) &&
                    (allowed <= System.currentTimeMillis() || queuedRead.canWait()); alpha = if (isEnabled) 1f else .48f
            }, LinearLayout.LayoutParams(-2, -2))
            page.addView(refreshRow)
            if (allowed > System.currentTimeMillis() && pause == null) {
                page.gap(8); page.addView(text(getString(R.string.ui_refresh_available_after, clock(allowed)), 11f, muted))
            }
        }
        if (gate.mobileLinkActive() || gate.prototypeActive() || gate.phoneLoginActive()) {
            page.gap(16); page.addView(button(getString(R.string.ui_finish_the_pending_connection), false) {
                if (gate.phoneLoginActive()) startActivity(Intent(this, PhoneLoginActivity::class.java))
                else if (gate.mobileLinkActive()) startActivity(Intent(this, DesktopLinkActivity::class.java)) else openLab()
            }, LinearLayout.LayoutParams(-1, -2))
        }
        page.gap(20)
        page.addView(UsageNotes.link(this))
        if (saved == null) { page.gap(8); page.addView(text(getString(R.string.ui_your_data_is_encrypted_on_this_device), 12f, muted).apply { gravity = Gravity.CENTER; setOnClickListener { privacy() } }) }
        if (saved != null) {
            val navigation = LinearLayout(this).apply {
                background = shape(panel, 24, AppUi.line); setPadding(dp(8), dp(7), dp(8), dp(7))
            }
            fun tab(label: String, icon: String, selected: Boolean, action: () -> Unit) = button(label, false, action = action).apply {
                background = AppUi.touch(this@MainActivity, shape(if (selected) AppUi.violetSoft else panel, 18))
                setTextColor(if (selected) accent else muted); isSelected = selected; textSize = 13f
                setCompoundDrawablesRelativeWithIntrinsicBounds(AppUi.Icon(this@MainActivity, icon, if (selected) accent else muted, 20), null, null, null)
                compoundDrawablePadding = dp(8)
                maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(10), dp(8), dp(10), dp(8))
            }
            navigation.addView(tab(getString(R.string.ui_overview), "home", !peopleScreen) { peopleScreen = false; selectedPersonId = null; message = null; render() }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
            navigation.addView(tab(getString(R.string.ui_people), "people", peopleScreen) { peopleScreen = true; selectedPersonId = null; message = null; render() }, LinearLayout.LayoutParams(0, -2, 1f))
            navigation.tag = "primary_navigation"
            shell.addView(navigation, LinearLayout.LayoutParams(-1, -2).apply {
                leftMargin = dp(22); rightMargin = dp(22); topMargin = dp(8); bottomMargin = dp(12)
            })
        }
        scroll.post { scroll.scrollTo(0, position) }; scheduleGateWake()
        if (!localAccountView && ::preparation.isInitialized && visible && !loading && web == null) preparation.resume()
    }

    private fun renderPreparation(page: LinearLayout, saved: SavedAccount) {
        val lists = FollowListVault(this, if (preview) "previewlists" else "followlists")
        val target = try { lists.target(saved.session, saved.session.userId) } catch (_: Exception) { return }
        val pending = InitialLists.pending(target)
        val enabled = InitialLists.selected(saved)
        val held = gate.manualOnly() || gate.requiresConnection() || gate.restrictionPending() || preparation.failure != null
        val card = AppUi.card(this)
        card.addView(AppUi.heading(this, getString(when {
            !enabled -> R.string.preparation_paused
            held -> R.string.preparation_waiting
            pending.isEmpty() -> R.string.preparation_ready
            else -> R.string.preparation_title
        }), 20f)); card.gap(8)
        for (direction in FollowDirection.entries) {
            val run = target?.snapshot?.run(direction)
            val title = getString(if (direction == FollowDirection.FOLLOWERS) R.string.ui_followers else R.string.ui_following)
            card.gap(8)
            card.addView(AppUi.numeric(text("$title · " + when {
                target?.scan(direction)?.failure != null -> getString(R.string.preparation_incomplete)
                InitialLists.ready(target, direction) -> getString(R.string.preparation_baseline)
                run != null -> getString(R.string.preparation_progress, run.pages.size, run.users.size)
                else -> getString(R.string.preparation_queued)
            }, 13f, ink, true)))
        }
        val status = text(getString(when {
            held -> R.string.preparation_held
            preparation.busy -> R.string.preparation_reading
            pending.isEmpty() -> R.string.preparation_daily
            else -> R.string.preparation_background
        }), 12f, muted).apply { setPadding(0, dp(10), 0, dp(8)) }
        preparation.failure?.let { status.text = ApiFailure(it).userMessage(this) }
        card.addView(status); preparation.bind(status)
        card.addView(AppUi.button(this, getString(if (enabled) R.string.preparation_pause else R.string.preparation_resume), false,
            !loading && !preparation.busy) {
            try {
                preparation.pause()
                account = vault.update(saved.session) { current ->
                    val targets = if (enabled) current.automation.targets - current.session.userId else current.automation.targets + current.session.userId
                    current.copy(automation = current.automation.copy(targets = targets, enabled = targets.isNotEmpty()))
                }
                syncMonitoring(); render()
            } catch (_: Exception) { message = ApiFailure(ApiFailure.Kind.STORAGE).userMessage(this); render() }
        })
        card.gap(8); card.addView(button(getString(R.string.ui_view_lists), false) { openLists(saved.profile) })
        page.addView(card)
    }

    private fun renderOverview(page: LinearLayout, saved: SavedAccount) {
        page.addView(AppUi.heading(this, getString(R.string.ui_at_a_glance), 28f)); page.gap(8)
        val identity = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        identity.addView(avatar(saved.profile, 40))
        identity.addView(text("@${saved.profile.username}", 15f, ink, true).apply { setPadding(dp(12), 0, 0, 0) }, LinearLayout.LayoutParams(0, -2, 1f))
        page.addView(identity); page.gap(12)
        val row = LinearLayout(this)
        fun stat(label: String, value: Long, direction: FollowDirection, purple: Boolean) = stack().apply {
            val foreground = if (purple) UiPalette.onAccent else ink
            background = AppUi.touch(this@MainActivity, if (purple) AppUi.brandSurface(this@MainActivity) else shape(AppUi.peach, 24))
            setPadding(dp(17), dp(18), dp(17), dp(18)); minimumHeight = dp(144)
            val title = LinearLayout(this@MainActivity).apply { gravity = Gravity.CENTER_VERTICAL }
            title.addView(text(label, 13f, foreground, true).apply {
                // Reserve the same label space when accessibility text wraps the second card.
                if (resources.configuration.fontScale > 1.3f) minLines = 2
            }, LinearLayout.LayoutParams(0, -2, 1f))
            title.addView(text("", 13f).apply {
                setCompoundDrawablesRelativeWithIntrinsicBounds(AppUi.Icon(this@MainActivity, "people", foreground, 18), null, null, null)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
            addView(title); gap(18)
            addView(AppUi.numeric(text(number(value), 38f, foreground, true)).apply {
                maxLines = 1
                if (android.os.Build.VERSION.SDK_INT >= 26) setAutoSizeTextTypeUniformWithConfiguration(16, 38, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            }); gap(10)
            addView(text(getString(R.string.ui_open_list), 11f, foreground).apply {
                setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, AppUi.Icon(this@MainActivity, "arrow", foreground, 16), null)
            })
            contentDescription = getString(R.string.ui_open_list_description, label, number(value)); isFocusable = true
            setOnClickListener { openLists(saved.profile, direction) }
        }
        row.addView(stat(getString(R.string.ui_followers), saved.profile.followers, FollowDirection.FOLLOWERS, true), LinearLayout.LayoutParams(0, -1, 1f).apply { marginEnd = dp(12) })
        row.addView(stat(getString(R.string.ui_following), saved.profile.following, FollowDirection.FOLLOWING, false), LinearLayout.LayoutParams(0, -1, 1f))
        page.addView(row, LinearLayout.LayoutParams(-1, -2)); page.gap(16)
        page.addView(profileStatuses.view(saved.session, saved.profile.userId)); page.gap(16)
        renderPreparation(page, saved); page.gap(16)
        page.addView(button(getString(R.string.quick_start), false) { startQuickWatch(saved.profile.userId) }); page.gap(8)
        page.gap(12)
        renderFavorites(page, saved)
        renderRecentActivity(page, saved)
        renderAutomation(page, saved)
        val quick = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; background = AppUi.touch(this@MainActivity, shape(panel, 22, AppUi.line))
            setPadding(dp(16), dp(16), dp(16), dp(16)); isFocusable = true
            setOnClickListener { peopleScreen = true; selectedPersonId = null; message = null; render() }
            contentDescription = resources.getQuantityString(R.plurals.ui_people_saved_profiles_open_people, saved.people.size, saved.people.size)
        }
        quick.addView(AppUi.avatar(this, "K", 42, AppUi.mint, AppUi.teal))
        val quickText = stack().apply { setPadding(dp(12), 0, dp(8), 0) }
        quickText.addView(text(getString(R.string.ui_explore_your_circle), 16f, ink, true)); quickText.gap(5)
        quickText.addView(text(if (saved.people.isEmpty()) getString(R.string.ui_add_your_first_person_and_take_a) else resources.getQuantityString(R.plurals.ui_saved_profiles, saved.people.size, saved.people.size), 12f, muted))
        quick.addView(quickText, LinearLayout.LayoutParams(0, -2, 1f))
        quick.addView(text("", 12f).apply { setCompoundDrawablesWithIntrinsicBounds(AppUi.Icon(this@MainActivity, "chevron", muted, 18), null, null, null) })
        page.addView(quick); page.gap(20)
    }

    private fun renderAutomation(page: LinearLayout, saved: SavedAccount) {
        val card = AppUi.card(this)
        val watching = saved.people.count { it.watching }
        val active = saved.automation.enabled || watching > 0
        val title = when {
            !active -> getString(R.string.ui_monitoring_off)
            !ChangeNotifications(this, preview).allowed() -> getString(R.string.ui_notification_permission_needed)
            gate.blockedUntil() > System.currentTimeMillis() -> getString(R.string.ui_instagram_cooldown)
            gate.requiresConnection() -> getString(R.string.ui_account_needs_reconnecting)
            gate.manualOnly() -> getString(R.string.ui_monitoring_paused)
            else -> getString(R.string.ui_monitoring_on)
        }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val labels = stack()
        labels.addView(text(title, 17f, if (title == getString(R.string.ui_monitoring_on)) AppUi.teal else ink, true)); labels.gap(5)
        labels.addView(text(getString(R.string.ui_profiles_lists_for_accounts, watching, if (saved.automation.enabled) saved.automation.targets.size else 0), 12f, muted))
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(AppUi.iconButton(this, "more", getString(R.string.ui_automatic_monitoring_settings)) { automationMenu() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        card.addView(row)
        card.contentDescription = getString(if (saved.automation.enabled) R.string.auto_enabled else R.string.auto_disabled)
        page.addView(card); page.gap(20)
    }
    private fun automationMenu() {
        val saved = account ?: return
        val options = mutableListOf(getString(R.string.auto_settings))
        if (saved.automation.enabled) { options += getString(R.string.auto_progress); options += getString(R.string.auto_stop) }
        AlertDialog.Builder(this).setTitle(getString(R.string.ui_automatic_monitoring)).setItems(options.toTypedArray()) { _, index ->
            when (options[index]) {
                getString(R.string.auto_settings) -> automationSettings()
                getString(R.string.auto_progress) -> automationProgress()
                else -> {
                try {
                    quickWatch.stop(false)
                    account = vault.update(saved.session) { it.copy(automation = it.automation.copy(enabled = false)) }
                    saved.automation.targets.forEach { ChangeNotifications(this, preview).cancelLists(saved.session.userId, it) }
                    syncMonitoring()
                } catch (_: Exception) { message = ApiFailure(ApiFailure.Kind.STORAGE).userMessage(this@MainActivity) }
                render()
                }
            }
        }.setNegativeButton(getString(R.string.ui_close), null).show()
    }
    private fun renderFavorites(page: LinearLayout, saved: SavedAccount) {
        val favorites = saved.people.filter { it.favorite }.take(6)
        if (favorites.isEmpty()) return
        page.addView(AppUi.heading(this, getString(R.string.ui_your_favorites), 21f)); page.gap(10)
        val row = LinearLayout(this)
        favorites.forEach { person ->
            val tile = stack().apply {
                gravity = Gravity.CENTER; setPadding(dp(12), dp(12), dp(12), dp(12))
                background = AppUi.touch(this@MainActivity, shape(panel, 18, AppUi.line))
                addView(avatar(person.latest, 40)); gap(8)
                addView(text("@${person.latest.username}", 12f, accent, true).apply { gravity = Gravity.CENTER; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                contentDescription = getString(R.string.ui_open_favorite, person.latest.username); isFocusable = true
                setOnClickListener { peopleScreen = true; selectedPersonId = person.latest.userId; message = null; render() }
            }
            row.addView(tile, LinearLayout.LayoutParams(dp(138), -2).apply { marginEnd = dp(8) })
        }
        page.addView(android.widget.HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) }); page.gap(20)
    }
    private fun openTimeline(target: String? = null) {
        val saved = account ?: return
        startActivity(Intent(this, TimelineActivity::class.java).putExtra("viewer_id", saved.session.userId)
            .putExtra("target_id", target).putExtra("offline_preview", preview))
    }
    private fun startQuickWatch(target: String) {
        val saved = account ?: return
        if (!preview && !saved.session.supportsLists) { message = getString(R.string.auto_mobile_required); render(); return }
        if (gate.manualOnly() || gate.automationHeld() || gate.requiresConnection()) {
            message = getString(R.string.quick_restriction); render(); return
        }
        if (loading) return
        pendingQuickTarget = target; pendingAutomationOwner = saved.session.userId
        pendingAutomationTargets = (if (saved.automation.enabled) saved.automation.targets else emptySet()) + target
        ChangeNotifications(this, preview).ensureChannel()
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1402)
        else finishAutomationPermission()
    }
    private fun renderQuickWatch(page: LinearLayout, saved: SavedAccount) {
        val id = quickWatch.target ?: return
        val profile = if (id == saved.profile.userId) saved.profile else saved.people.firstOrNull { it.latest.userId == id }?.latest
        if (profile == null) { quickWatch.stop(false); return }
        val card = AppUi.card(this)
        card.addView(AppUi.heading(this, getString(R.string.quick_title, profile.username), 18f)); card.gap(8)
        fun label(): String {
            val seconds = quickWatch.secondsLeft
            val remaining = String.format(AppLanguage.locale(this), "%02d:%02d", seconds / 60, seconds % 60)
            return if (quickWatch.busy) getString(R.string.quick_busy, remaining)
                else if (quickWatch.waitingForSlot) getString(R.string.quick_delayed, remaining)
                else if (quickWatch.secondsToCheck == 0L) getString(R.string.quick_due, remaining)
                else getString(R.string.quick_status, remaining, quickWatch.secondsToCheck.toString())
        }
        quickWatch.status = ::label
        quickWatch.label = text(label(), 13f, muted).also { card.addView(it) }; card.gap(10)
        card.addView(text(getString(R.string.quick_visible_scope), 12f, muted)); card.gap(10)
        card.addView(button(getString(R.string.quick_stop), false) { quickWatch.stop() }); card.gap(8)
        card.addView(button(getString(R.string.timeline_title), false) { openTimeline(id) })
        page.addView(card); page.gap(18)
    }
    private fun automationSettings() {
        val saved = account ?: return
        if (!preview && !saved.session.supportsLists) {
            message = getString(R.string.auto_mobile_required); render(); return
        }
        val profiles = listOf(saved.profile) + saved.people.map { it.latest }
        val selected = profiles.map { it.userId in saved.automation.targets || saved.automation.targets.isEmpty() }.toBooleanArray()
        AlertDialog.Builder(this).setTitle(R.string.auto_choose_targets)
            .setMultiChoiceItems(profiles.map { "@${it.username}" + if (it.userId == saved.session.userId) getString(R.string.ui_your_account) else "" }.toTypedArray(), selected) { _, i, checked -> selected[i] = checked }
            .setNeutralButton(R.string.auto_how) { _, _ -> AlertDialog.Builder(this).setTitle(R.string.auto_title)
                .setMessage(getString(R.string.auto_full_scope) + "\n\n" + getString(R.string.adaptive_scope)).setPositiveButton(R.string.list_close, null).show() }
            .setNegativeButton(R.string.list_cancel, null).setPositiveButton(R.string.auto_start) { _, _ ->
                val targets = profiles.filterIndexed { index, _ -> selected[index] }.map { it.userId }.toSet()
                if (targets.isEmpty()) { message = getString(R.string.auto_select_one); render(); return@setPositiveButton }
                pendingAutomationOwner = saved.session.userId; pendingAutomationTargets = targets
                ChangeNotifications(this, preview).ensureChannel()
                if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1402)
                else finishAutomationPermission()
            }.show()
    }
    private fun finishAutomationPermission() {
        val owner = pendingAutomationOwner; val targets = pendingAutomationTargets
        val quickTarget = pendingQuickTarget; pendingQuickTarget = null
        pendingAutomationOwner = null; pendingAutomationTargets = null
        val saved = account ?: return
        if (saved.session.userId != owner || targets == null) return
        if (!ChangeNotifications(this, preview).allowed()) { message = getString(R.string.watch_reason_notifications); render(); return }
        try {
            account = vault.update(saved.session) { current ->
                val valid = targets.filter { id -> id == current.session.userId || current.people.any { it.latest.userId == id } }.toSet()
                if (valid.isEmpty()) throw ApiFailure(ApiFailure.Kind.CANCELLED)
                current.copy(automation = current.automation.copy(enabled = true, targets = valid,
                    ownNextCheckAt = minOf(current.automation.ownNextCheckAt, current.profile.observedAt + ListAutomationPolicy.OWN_PROFILE_MS)), people = current.people.map { person ->
                    if (person.latest.userId in valid && (quickTarget == null || person.latest.userId == quickTarget)) WatchPreference.set(person, 15, true)
                    else if (person.latest.userId !in valid || person.watching) person else person.copy(watching = true,
                        nextCheckAt = maxOf(System.currentTimeMillis(), person.latest.observedAt + WatchIntervals.durationMs(person.intervalMinutes)))
                })
            }
            saved.automation.targets.filterNot { it in targets }.forEach { ChangeNotifications(this, preview).cancelLists(saved.session.userId, it) }
            syncMonitoring(); message = getString(R.string.auto_started)
            if (quickTarget != null && quickTarget in account!!.automation.targets) {
                if (preview) PreviewMode.cancel(this) else MonitorScheduler.cancel(this)
                quickWatch.start(saved.session, quickTarget); message = null; syncMonitoring()
            }
        } catch (failure: ApiFailure) { message = failure.userMessage(this@MainActivity) }
        catch (_: Exception) { message = ApiFailure(ApiFailure.Kind.STORAGE).userMessage(this@MainActivity) }
        render()
    }
    private fun automationProgress() {
        try {
            account = vault.read(); syncMonitoring()
            val saved = account ?: return
            val archive = FollowListVault(this, if (preview) "previewlists" else "followlists").archive(saved.session)
            val profiles = (listOf(saved.profile) + saved.people.map { it.latest }).filter { it.userId in saved.automation.targets }
            val rows = profiles.flatMap { profile -> FollowDirection.entries.map { profile to it } }
            val descriptions = rows.map { (profile, direction) ->
                val record = archive.targets.firstOrNull { it.target == profile.userId }
                val run = record?.snapshot?.run(direction); val state = record?.scan(direction)
                val label = getString(if (direction == FollowDirection.FOLLOWERS) R.string.list_followers else R.string.list_following)
                val detail = when {
                    state?.failure != null -> ApiFailure(state.failure).userMessage(this@MainActivity)
                    run == null -> getString(R.string.auto_first_pending)
                    run.coverage() == FollowCoverage.MORE -> getString(R.string.auto_scan_progress, run.pages.size.toString(), run.users.size.toString())
                    run.coverage() == FollowCoverage.END_OBSERVED -> getString(R.string.auto_scan_end, run.users.size.toString(), date(state!!.nextAt))
                    else -> getString(R.string.auto_scan_partial, run.pages.size.toString())
                }
                "@${profile.username} · $label\n$detail"
            }
            val gateReason = when {
                !ChangeNotifications(this, preview).allowed() -> getString(R.string.watch_reason_notifications)
                gate.listCapacityPaused() -> ApiFailure(ApiFailure.Kind.CAPACITY).userMessage(this@MainActivity)
                gate.blockedUntil() > System.currentTimeMillis() -> getString(R.string.watch_reason_limited, date(gate.blockedUntil()))
                gate.requiresConnection() -> getString(R.string.watch_reason_connection)
                gate.manualOnly() -> getString(R.string.watch_reason_manual)
                gate.automationHeld() -> getString(R.string.watch_reason_link)
                AppUi.offline(this) == true -> getString(R.string.watch_reason_offline)
                else -> getString(R.string.auto_progress_hint)
            }
            AlertDialog.Builder(this).setTitle(R.string.auto_progress).setItems(descriptions.toTypedArray()) { _, index ->
                val (profile, direction) = rows[index]; openLists(profile, direction)
            }.setNeutralButton(R.string.auto_how) { _, _ -> AlertDialog.Builder(this).setTitle(R.string.auto_progress)
                .setMessage(gateReason + "\n\n" + getString(R.string.auto_full_scope) + "\n\n" + getString(R.string.adaptive_scope)).setPositiveButton(R.string.list_close, null)
                .setNegativeButton(R.string.auto_clear_cache) { _, _ -> AlertDialog.Builder(this).setTitle(R.string.auto_clear_cache)
                    .setMessage(R.string.auto_clear_cache_confirm).setNegativeButton(R.string.list_cancel, null)
                    .setPositiveButton(R.string.auto_clear_cache) { _, _ ->
                        try {
                            vault.inspect { current ->
                                if (current?.session != saved.session) throw ApiFailure(ApiFailure.Kind.CANCELLED)
                                FollowListVault(this, if (preview) "previewlists" else "followlists").clear()
                                gate.clearListCapacityPause()
                            }
                            syncMonitoring()
                            archive.targets.forEach { ChangeNotifications(this, preview).cancelLists(saved.session.userId, it.target) }
                            message = getString(R.string.auto_cache_cleared)
                        }
                        catch (_: Exception) { message = ApiFailure(ApiFailure.Kind.STORAGE).userMessage(this@MainActivity) }; render()
                    }.show() }.show() }
                .setNegativeButton(R.string.auto_refresh_status) { _, _ -> automationProgress() }
                .setPositiveButton(R.string.list_close, null).show()
        } catch (_: Exception) { message = ApiFailure(ApiFailure.Kind.STORAGE).userMessage(this@MainActivity); render() }
    }

    private fun favoriteButton(person: TrackedPerson) = AppUi.iconButton(this,
        if (person.favorite) "star_filled" else "star",
        getString(if (person.favorite) R.string.favorite_remove else R.string.favorite_add, person.latest.username)) {
        val id = person.latest.userId
        if (savePeople(reschedule = false) { people -> people.map { if (it.latest.userId == id) it.copy(favorite = !it.favorite) else it } }) {
            val selected = account?.people?.firstOrNull { it.latest.userId == id }
            if (selected != null) {
                if (selected.favorite && peopleScreen && selectedPersonId == null) screenIdentity = ""
                android.widget.Toast.makeText(this, getString(if (selected.favorite) R.string.favorite_added else R.string.favorite_removed,
                    selected.latest.username), android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        render()
    }.apply {
        isSelected = person.favorite
        if (person.favorite) background = AppUi.touch(this@MainActivity, shape(AppUi.violetSoft, 16))
    }
    private fun editTitle(edit: ProfileEdit): String = getString(when (edit.field) {
        ProfileChangeField.USERNAME -> R.string.profile_username_changed
        ProfileChangeField.BIOGRAPHY -> R.string.profile_bio_changed
        ProfileChangeField.PICTURE -> R.string.profile_picture_changed
    })
    private fun showProfileEdit(edit: ProfileEdit) {
        val value = if (edit.field == ProfileChangeField.PICTURE) getString(R.string.profile_picture_evidence) else
            getString(R.string.profile_edit_values, edit.before.ifEmpty { getString(R.string.profile_empty_bio) },
                edit.after.ifEmpty { getString(R.string.profile_empty_bio) })
        AlertDialog.Builder(this).setTitle(editTitle(edit)).setMessage(value + "\n\n" +
            getString(R.string.profile_observation_range, date(edit.previousAt), date(edit.observedAt)))
            .setPositiveButton(R.string.list_close, null).show()
    }
    private fun renderProfileDetails(page: LinearLayout, person: TrackedPerson) {
        val profile = person.latest
        val card = AppUi.card(this)
        card.addView(AppUi.heading(this, getString(R.string.profile_details_title), 20f)); card.gap(12)
        val pictureComparable = ProfileChanges.pictureComparable(profile)
        card.addView(text(getString(if (pictureComparable) R.string.profile_picture_available else R.string.profile_picture_unknown), 12f, muted))
        card.gap(12)
        val edits = ProfileChanges.history(person.history).sortedWith(compareByDescending<ProfileEdit> { it.observedAt }.thenBy { it.key })
        card.addView(button(getString(R.string.profile_history), false) {
            if (edits.isEmpty()) AlertDialog.Builder(this).setTitle(R.string.profile_history).setMessage(R.string.profile_history_empty)
                .setPositiveButton(R.string.list_close, null).show()
            else AlertDialog.Builder(this).setTitle(R.string.profile_history).setItems(edits.map {
                "${editTitle(it)}\n${date(it.observedAt)}"
            }.toTypedArray()) { _, index -> showProfileEdit(edits[index]) }.setNegativeButton(R.string.list_close, null).show()
        }, LinearLayout.LayoutParams(-1, -2))
        page.addView(card); page.gap(16)
    }
    private fun activityDetail(item: ActivityItem): String = when (item) {
        is ActivityItem.Counts -> listOfNotNull(
            item.followersDelta.takeIf { it != 0L }?.let { getString(R.string.activity_followers, delta(it)) },
            item.followingDelta.takeIf { it != 0L }?.let { getString(R.string.activity_following, delta(it)) }).joinToString(" · ")
        is ActivityItem.ProfileChanged -> editTitle(item.edit)
        is ActivityItem.ListRead -> getString(R.string.activity_list_page,
            getString(if (item.direction == FollowDirection.FOLLOWERS) R.string.list_followers else R.string.list_following), item.pageNumber)
    }
    private fun openActivity(item: ActivityItem) {
        val saved = account ?: return
        val profile = (listOf(saved.profile) + saved.people.map { it.latest }).firstOrNull { it.userId == item.profileId } ?: return
        when (item) {
            is ActivityItem.Counts -> { peopleScreen = true; selectedPersonId = item.profileId; rangeDays = 0; message = null; render() }
            is ActivityItem.ProfileChanged -> showProfileEdit(item.edit)
            is ActivityItem.ListRead -> openLists(profile, item.direction)
        }
    }
    private fun renderRecentActivity(page: LinearLayout, saved: SavedAccount) {
        var listsUnavailable = false
        val snapshots = try { FollowListVault(this, if (preview) "previewlists" else "followlists").archive(saved.session).targets.mapNotNull { it.snapshot } }
            catch (_: Exception) { listsUnavailable = true; emptyList() }
        val entries = RecentActivity.all(saved, snapshots)
        val card = AppUi.card(this)
        card.addView(AppUi.heading(this, getString(R.string.activity_title), 21f)); card.gap(6)
        card.gap(10)
        if (entries.isEmpty()) {
            card.addView(text(getString(R.string.activity_empty), 14f, muted))
        } else {
            for (item in entries.take(3)) {
                val row = LinearLayout(this).apply {
                    gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(76); setPadding(0, dp(10), 0, dp(10))
                    background = AppUi.touch(this@MainActivity, shape(panel, 14)); isFocusable = true
                    tag = "activity:${item.key}"; contentDescription = "@${item.username}. ${activityDetail(item)}. ${date(item.observedAt)}"
                    setOnClickListener { openActivity(item) }
                }
                row.addView(AppUi.avatar(this, item.username, 36, if (item is ActivityItem.Counts) AppUi.mint else AppUi.violetSoft,
                    if (item is ActivityItem.Counts) AppUi.teal else accent))
                val labels = stack().apply { setPadding(dp(10), 0, 0, 0) }
                labels.addView(text("@${item.username}", 14f, ink, true)); labels.gap(5)
                labels.addView(text(activityDetail(item), 13f, if (item is ActivityItem.Counts) AppUi.teal else accent)); labels.gap(5)
                labels.addView(text(getString(R.string.activity_observed, date(item.observedAt)), 10f, muted))
                row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f)); card.addView(row)
            }
            card.gap(10)
            card.addView(button(getString(R.string.activity_all), false) {
                val rows = entries.map { "@${it.username}\n${activityDetail(it)}\n${date(it.observedAt)}" }.toTypedArray()
                AlertDialog.Builder(this).setTitle(R.string.activity_title).setItems(rows) { _, index -> openActivity(entries[index]) }
                    .setNeutralButton(R.string.activity_scope) { _, _ ->
                        AlertDialog.Builder(this).setTitle(R.string.activity_scope).setMessage(R.string.activity_scope_body)
                            .setPositiveButton(R.string.list_close, null).show()
                    }.setNegativeButton(R.string.list_close, null).show()
            }, LinearLayout.LayoutParams(-1, -2))
        }
        if (listsUnavailable) { card.gap(10); card.addView(text(getString(R.string.activity_lists_unavailable), 12f, AppUi.coral)) }
        page.addView(card); page.gap(20)
    }

    private fun clock(time: Long) = SimpleDateFormat("HH:mm", AppLanguage.locale(this)).format(Date(time))
    private fun connectionOptions(replacePending: Boolean = false) {
        if (preview) { android.widget.Toast.makeText(this, getString(R.string.ui_account_connection_is_unavailable_in_demo_mode), android.widget.Toast.LENGTH_SHORT).show(); return }
        val labels = arrayOf(getString(R.string.web_connection_menu), getString(R.string.phone_menu), getString(R.string.ui_connect_from_computer_mobile_session))
        AlertDialog.Builder(this).setTitle(getString(R.string.ui_connect_your_account)).setItems(labels) { _, index ->
            if (replacePending && index != 1) {
                try { MobileSessions(this).discardPending(); DesktopLink(this).clear(); pendingMobile = false }
                catch (_: Exception) { showFailure(ApiFailure(ApiFailure.Kind.STORAGE)); render(); return@setItems }
            }
            when (index) {
                0 -> startLogin()
                1 -> startActivity(Intent(this, PhoneLoginActivity::class.java))
                else -> startActivity(Intent(this, DesktopLinkActivity::class.java))
            }
        }.setNegativeButton(getString(R.string.ui_cancel), null).show()
    }
    private fun options() {
        val options = mutableListOf(R.string.ui_manage_connection, R.string.ui_refresh_settings, R.string.ui_request_information, R.string.language_menu, R.string.appearance_title, R.string.usage_notes_title, R.string.ui_privacy, R.string.ui_advanced)
        if(!preview)options.add(0,R.string.accounts_title)
        if (account != null) options.add(R.string.timeline_menu)
        if (account != null) options.add(R.string.ui_my_lists)
        if (BuildConfig.DEBUG && !preview) options.add(R.string.ui_open_offline_demo)
        AlertDialog.Builder(this).setTitle(R.string.ui_options).setItems(options.map { getString(it) }.toTypedArray()) { _, index ->
            when (options[index]) {
                R.string.accounts_title -> accountPicker()
                R.string.ui_manage_connection -> {
                    try {
                        val exists = account != null || vault.exists() || (!preview && MobileSessions(this).exists())
                        val actions = if (exists) arrayOf(getString(R.string.ui_connect_another_account), getString(R.string.ui_remove_connection)) else arrayOf(getString(R.string.ui_connect_account))
                        AlertDialog.Builder(this).setTitle(R.string.ui_connection).setItems(actions) { _, item ->
                            if (item == 0) connectionOptions(replacePending = true) else confirmRemove()
                        }.setNegativeButton(R.string.ui_close, null).show()
                    }catch (_: Exception) {showFailure(ApiFailure(ApiFailure.Kind.STORAGE))}
                }
                R.string.ui_refresh_settings -> AlertDialog.Builder(this).setTitle(R.string.ui_refresh_settings)
                    .setMultiChoiceItems(arrayOf(getString(R.string.ui_refresh_automatically_on_launch)), booleanArrayOf(gate.ownAutoRefresh())) { _, _, enabled ->
                        try { gate.setOwnAutoRefresh(enabled) } catch (_: Exception) { showFailure(ApiFailure(ApiFailure.Kind.STORAGE)) }
                    }
                    .setPositiveButton(R.string.ui_ok, null).show()
                R.string.language_menu -> languagePicker()
                R.string.appearance_title -> appearancePicker()
                R.string.timeline_menu -> openTimeline()
                R.string.ui_request_information -> requestInfo()
                R.string.ui_privacy -> privacy()
                R.string.usage_notes_title -> UsageNotes.open(this)
                R.string.ui_advanced -> AlertDialog.Builder(this).setTitle(R.string.ui_advanced).setItems(arrayOf(getString(R.string.web_compare_menu),getString(R.string.ui_web_connection_experiment))) { _, item -> if(item == 0) startLogin(compare = true) else openLab() }.setNegativeButton(R.string.ui_close, null).show()
                R.string.ui_my_lists -> account?.profile?.let(::openLists)
                R.string.ui_open_offline_demo -> startActivity(Intent(this, MainActivity::class.java).putExtra("offline_preview", true))
            }
        }.setNegativeButton(R.string.ui_close, null).show()
    }
    private fun accountPicker() {
        if(loading || web!=null)return
        try {
            val catalog=AccountCatalog(this);val entries=catalog.entries();val active=catalog.active()
            AlertDialog.Builder(this).setTitle(R.string.accounts_title)
                .setSingleChoiceItems(entries.map {"@${it.username}"}.toTypedArray(),entries.indexOfFirst {it.id==active}) {dialog,index->
                    dialog.dismiss();if(entries[index].id!=active)switchAccount(entries[index].id)
                }.setNeutralButton(R.string.accounts_add) {_,_->connectionOptions(replacePending=true)}
                .setPositiveButton(R.string.ui_close,null).show()
        }catch (_: Exception) {showFailure(ApiFailure(ApiFailure.Kind.STORAGE))}
    }
    private fun switchAccount(owner: String) {
        if(loading || web!=null)return
        queuedRead.cancel(false);quickWatch.stop(false);preparation.pause();api.cancel();avatars.cancelAll()
        generation++;loading=true;render()
        worker.execute {
            val failure=try {AccountCatalog(this).select(owner);null}catch (_: Exception) {ApiFailure(ApiFailure.Kind.STORAGE)}
            main.post {
                if(isDestroyed)return@post
                loading=false
                if(failure!=null) {showFailure(failure);render()}
                else {intent.putExtra("local_account_switch",true);recreate()}
            }
        }
    }
    private fun languagePicker() {
        val choices = arrayOf(getString(R.string.language_turkish), getString(R.string.language_english),
            getString(R.string.language_spanish), getString(R.string.language_portuguese_brazil))
        AlertDialog.Builder(this).setTitle(R.string.language_title)
            .setSingleChoiceItems(choices, AppLanguage.choices.indexOf(AppLanguage.selected(this))) { dialog, index ->
                dialog.dismiss()
                val language = AppLanguage.choices[index]
                if (language == AppLanguage.selected(this)) return@setSingleChoiceItems
                try {
                    AppLanguage.set(this, language)
                    if (android.os.Build.VERSION.SDK_INT < 33) recreate()
                } catch (_: Exception) { message = getString(R.string.language_save_failed); render() }
            }.setNegativeButton(R.string.ui_cancel, null).show()
    }
    private fun appearancePicker() {
        val choices = intArrayOf(R.string.appearance_system, R.string.appearance_light, R.string.appearance_dark, R.string.appearance_oled)
        AlertDialog.Builder(this).setTitle(R.string.appearance_title)
            .setSingleChoiceItems(choices.map { getString(it) }.toTypedArray(), AppAppearance.choices.indexOf(AppAppearance.selected(this))) { dialog, index ->
                dialog.dismiss()
                val mode = AppAppearance.choices[index]
                if (mode == AppAppearance.selected(this)) return@setSingleChoiceItems
                try { AppAppearance.set(this, mode); recreate() }
                catch (_: Exception) { message = getString(R.string.appearance_save_failed); render() }
            }.setNegativeButton(R.string.ui_cancel, null).show()
    }
    private fun requestInfo() {
        recordLoginPage { it.flush() }
        val now = System.currentTimeMillis()
        val lines = mutableListOf(getString(R.string.ui_read_attempts_started, number(gate.attempts())),
            getString(R.string.web_compare_last_counted_http, gate.lastHttpStatus()?.toString() ?: getString(R.string.ui_no_response_yet)))
        lines += getString(R.string.web_transport_selected, getString(if(WebRequestClients.platformAvailable()) R.string.web_transport_android else R.string.web_transport_standard))
        lines += getString(R.string.security_budget_info, RequestBudgetPolicy.HOURLY, RequestBudgetPolicy.DAILY)
        if (gate.budgetNextAt() > now) lines += getString(R.string.security_budget_next, date(gate.budgetNextAt()))
        if (gate.lastAttemptAt() > 0) lines += getString(R.string.ui_last_attempt, date(gate.lastAttemptAt()))
        if (!preview) {
            try {
                val saved = PhoneLoginJournal(this).read()
                lines += if (saved == null) getString(R.string.phone_diagnostic_empty)
                    else getString(R.string.phone_diagnostic_saved, saved.operation.code,
                        saved.status?.toString() ?: getString(R.string.ui_no_response_yet), date(saved.attemptedAt))
            } catch (_: Exception) { lines += getString(R.string.phone_diagnostic_unavailable) }
        }
        if (!preview) try {
            val report = WebConnectionJournal(this).read()
            if(WebConnectionJournal(this).legacyAttribution()) lines += getString(R.string.web_compare_legacy_attribution)
            lines += if (report.isEmpty()) getString(R.string.web_compare_no_report) else getString(R.string.web_compare_report) + "\n" + report.joinToString("\n") {
                getString(R.string.web_compare_report_row, when(it.step) {WebConnectionJournal.Step.PAGE->"W00";WebConnectionJournal.Step.BROWSER->"W01";WebConnectionJournal.Step.NATIVE->"W02"},
                    getString(when(it.outcome) {
                        WebConnectionJournal.Outcome.STARTED -> R.string.web_compare_started
                        WebConnectionJournal.Outcome.SUCCESS -> R.string.web_compare_success
                        WebConnectionJournal.Outcome.LIMITED -> R.string.web_compare_limited
                        WebConnectionJournal.Outcome.AUTH -> R.string.web_compare_auth
                        WebConnectionJournal.Outcome.INVALID -> R.string.web_compare_invalid
                        WebConnectionJournal.Outcome.CONNECTION -> R.string.web_compare_connection
                        WebConnectionJournal.Outcome.CANCELLED -> R.string.web_compare_cancel
                    }), it.http?.toString() ?: "—",date(it.at))
            }
        } catch (_: Exception) { lines += getString(R.string.web_compare_journal_failed) }
        if (!preview) try {
            val report = if (loginTraceFailed) null else LoginPageTrace.read(this)
            if (loginTraceFailed) lines += getString(R.string.page_trace_unavailable)
            else if (report != null) {
                // Keep semantic sections separate: some devices expose the long AlertDialog message as empty.
                lines += getString(R.string.page_trace_report, "")
                val fields = org.json.JSONObject(report)
                fields.keys().forEach { key ->
                    val value = fields.get(key)
                    val formatted = when (value) {
                        is org.json.JSONObject -> value.toString(2)
                        is org.json.JSONArray -> value.toString(2)
                        is String -> org.json.JSONObject.quote(value)
                        else -> value.toString()
                    }
                    lines += "LP01 · $key\n$formatted"
                }
            }
        } catch (_: Exception) { lines += getString(R.string.page_trace_unavailable) }
        if (gate.restrictionPending()) {
            lines += getString(R.string.ui_signal, gate.limitReasonMessage(this))
            lines += getString(R.string.ui_last_limit, date(gate.lastLimitedAt()))
            lines += getString(R.string.ui_server_wait_information, gate.serverRetryAt()?.let { date(it) } ?: getString(R.string.ui_not_specified))
            lines += if (gate.blockedUntil() > now) getString(R.string.ui_app_cooldown_until, date(gate.blockedUntil())) else getString(R.string.ui_the_local_cooldown_has_ended)
            lines += getString(R.string.ui_this_time_does_not_guarantee_instagram_access)
        }
        lines += getString(R.string.ui_the_counter_covers_profile_and_list_page)
        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), dp(8))
            lines.forEach { line ->
                addView(text(line, 14f).apply { setTextIsSelectable(true) },
                    LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
            }
        }
        AlertDialog.Builder(this).setTitle(getString(R.string.ui_request_information))
            .setView(ScrollView(this).apply { addView(details); AppUi.scrollStyle(this) })
            .setPositiveButton(getString(R.string.ui_ok), null).show()
    }
    private fun openLab() {
        try {
            if (!preview) { gate.setPrototypeActive(true); syncMonitoring() }
            startActivity(Intent(this, ConnectionLabActivity::class.java).putExtra("offline_lab", preview))
        } catch (_: Exception) { showFailure(ApiFailure(ApiFailure.Kind.STORAGE)); render() }
    }

    private fun date(time: Long) = SimpleDateFormat("d MMM · HH:mm", AppLanguage.locale(this)).format(Date(time))
    private fun number(value: Long) = NumberFormat.getIntegerInstance(AppLanguage.locale(this)).format(value)
    private fun delta(value: Long) = (if (value > 0) "+" else "") + number(value)
    private fun openLists(profile: Profile, direction: FollowDirection = FollowDirection.FOLLOWERS) {
        startActivity(Intent(this, FollowListActivity::class.java).putExtra("target_id", profile.userId).putExtra("initial_direction", direction.name).putExtra("offline_preview", preview))
    }

    private fun renderPeople(page: LinearLayout, saved: SavedAccount) {
        val person = saved.people.firstOrNull { it.latest.userId == selectedPersonId }
        if (person == null) {
            selectedPersonId = null
            page.addView(AppUi.heading(this, getString(R.string.ui_people_you_choose), 30f)); page.gap(8)
            page.addView(text(getString(R.string.ui_a_space_for_the_profiles_you_care), 14f, muted)); page.gap(20)
            val actions = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            actions.addView(text(getString(R.string.people_summary, saved.people.size, saved.people.count { it.favorite }), 13f, muted), LinearLayout.LayoutParams(0, -2, 1f))
            if (saved.people.isNotEmpty()) actions.addView(button(getString(R.string.ui_add_person), networkAction = true) { addPerson() }, LinearLayout.LayoutParams(-2, -2))
            page.addView(actions); page.gap(12)
            page.gap(8)
            if (saved.people.isEmpty()) {
                val empty = stack().apply { background = shape(panel, 24, AppUi.line); setPadding(dp(24), dp(28), dp(24), dp(28)) }
                empty.addView(AppUi.avatar(this, "+", 56, AppUi.mint, AppUi.teal)); empty.gap(18)
                empty.addView(text(getString(R.string.ui_ready_to_add_your_first_person), 20f, ink, true)); empty.gap(8)
                empty.addView(text(getString(R.string.ui_add_a_profile_to_view_its_lists), 14f, muted))
                empty.gap(16)
                empty.addView(button(getString(R.string.ui_add_person), networkAction = true) { addPerson() })
                empty.gap(8)
                empty.addView(button(getString(R.string.navigation_own_followers), false) { openLists(saved.profile) })
                page.addView(empty)
            }
            val filter=input(getString(R.string.search_saved_hint),256).apply {setSingleLine(true);setText(peopleQuery);tag="saved-profile-search"}
            page.addView(filter);page.gap(12)
            val results=stack();page.addView(results)
            fun populate() {
                profileStatuses.clear();results.removeAllViews()
                val matches=PeoplePolicy.favoritesFirst(saved.people).filter {ProfileSearch.matches(it.latest.username+" "+it.latest.name.orEmpty()+" "+it.group,peopleQuery)}
                if(matches.isEmpty() && saved.people.isNotEmpty())results.addView(text(getString(R.string.search_none),14f,muted))
            for ((index, entry) in matches.take(visiblePeople).withIndex()) {
                val p = entry.latest
                val card = stack().apply { background = AppUi.cardSurface(this@MainActivity); setPadding(dp(18), dp(18), dp(18), dp(16)) }
                val identity = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
                identity.addView(avatar(p, 46, if (index % 2 == 0) AppUi.violetSoft else AppUi.peach, if (index % 2 == 0) accent else AppUi.coral))
                val name = stack().apply { setPadding(dp(12), 0, 0, 0) }
                name.addView(text("@${p.username}", 17f, ink, true).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
                name.gap(5); name.addView(text(listOfNotNull(p.name?.takeIf {it.isNotBlank()},entry.group.takeIf {it.isNotBlank()}).joinToString(" · ").ifBlank {getString(R.string.ui_saved_profile)},12f,muted))
                identity.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
                identity.addView(favoriteButton(entry), LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(8) })
                identity.addView(AppUi.iconButton(this, "trash", getString(R.string.person_remove_description, p.username)) { removePerson(saved.session, p) }
                    .apply { setCompoundDrawablesRelativeWithIntrinsicBounds(AppUi.Icon(this@MainActivity, "trash", AppUi.coral), null, null, null) },
                    LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(4) })
                card.addView(identity); card.gap(18)
                card.addView(AppUi.numeric(text(getString(R.string.ui_followers_following, number(p.followers), number(p.following)), 14f, ink, true))); card.gap(8)
                card.addView(text(getString(R.string.ui_last_record, date(p.observedAt)), 11f, muted))
                card.gap(10); card.addView(profileStatuses.view(saved.session, p.userId))
                card.gap(16)
                card.addView(button(getString(R.string.ui_open_analysis, p.username), false) { selectedPersonId = p.userId; rangeDays = 0; message = null; render() }, LinearLayout.LayoutParams(-1, -2))
                results.addView(card); results.gap(14)
            }
            if (matches.size > visiblePeople) results.addView(button(getString(R.string.people_more), false) {
                visiblePeople = (visiblePeople + 20).coerceAtMost(PeoplePolicy.MAX_PEOPLE); populate()
            })
            }
            val update=Runnable {if(filter.isAttachedToWindow)populate()}
            filter.addTextChangedListener(object:android.text.TextWatcher {
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {peopleQuery=s.toString();visiblePeople=20;main.removeCallbacks(update);main.postDelayed(update,120)}
                override fun afterTextChanged(s:android.text.Editable?) {}
            })
            populate()
            return
        }
        val p = person.latest
        page.addView(button(getString(R.string.ui_all_people), false) { selectedPersonId = null; message = null; render() }.apply {
            background = null; gravity = Gravity.START or Gravity.CENTER_VERTICAL; setPadding(0, dp(8), dp(12), dp(8))
            setCompoundDrawablesRelativeWithIntrinsicBounds(AppUi.Icon(this@MainActivity, "back", accent, 18), null, null, null)
            compoundDrawablePadding = dp(6)
        }); page.gap(12)
        val personHeading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        personHeading.addView(avatar(p, 48), LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(12) })
        personHeading.addView(AppUi.heading(this, "@${p.username}", 27f), LinearLayout.LayoutParams(0, -2, 1f))
        personHeading.addView(favoriteButton(person), LinearLayout.LayoutParams(dp(48), dp(48)))
        page.addView(personHeading); page.gap(8)
        page.addView(badge(person.group.ifBlank { getString(R.string.ui_profile_analysis) })); page.gap(20)
        page.addView(ProfileSummary.view(this, p)); page.gap(12)
        page.addView(button(getString(R.string.visual_title),false) {
            startActivity(Intent(this,VisualHistoryActivity::class.java).putExtra("viewer_id",saved.session.userId).putExtra("target_id",p.userId).putExtra("offline_preview",preview))
        }); page.gap(12)
        page.addView(profileStatuses.view(saved.session, p.userId)); page.gap(16)
        val fastActions = LinearLayout(this)
        fastActions.addView(button(getString(R.string.quick_start), false) { startQuickWatch(p.userId) }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
        fastActions.addView(button(getString(R.string.timeline_title), false) { openTimeline(p.userId) }, LinearLayout.LayoutParams(0, -2, 1f))
        page.addView(fastActions); page.gap(8)
        page.addView(text(getString(R.string.quick_hint), 12f, muted)); page.gap(16)
        page.addView(button(getString(R.string.list_open), false) { openLists(p) }); page.gap(16)
        if (person.history.size >= 2) {
            val previous = person.history[person.history.lastIndex - 1]
            page.addView(text(getString(R.string.ui_compared_with_the_previous_record), 12f, muted)); page.gap(6)
            page.addView(text(getString(R.string.ui_followers_following, delta(p.followers - previous.followers), delta(p.following - previous.following)), 17f, ink, true))
            page.gap(6); page.addView(text("${date(previous.observedAt)} → ${date(p.observedAt)}", 12f, muted))
        } else page.addView(text(getString(R.string.ui_no_comparison_yet), 14f, muted))
        page.gap(20)
        renderWatchStatus(page, person, saved)
        renderProfileDetails(page, person)
        page.addView(button(getString(R.string.ui_refresh_person), false, networkAction = true) { readPerson(person = person) }.apply {
            isEnabled = !loading && !queuedRead.pending && (gate.nextAllowedAt() <= System.currentTimeMillis() || queuedRead.canWait()) && PeoplePolicy.needsRefresh(p, System.currentTimeMillis())
            alpha = if (isEnabled) 1f else .5f
        }, LinearLayout.LayoutParams(-1, -2)); page.gap(24)
        val historyCard = AppUi.card(this)
        historyCard.addView(AppUi.heading(this, getString(R.string.ui_observation_history), 22f)); historyCard.gap(10)
        val filters = LinearLayout(this)
        for ((label, days) in listOf(getString(R.string.ui_7_days) to 7, getString(R.string.ui_30_days) to 30, getString(R.string.ui_all) to 0)) {
            filters.addView(button(label, rangeDays == days) { rangeDays = days; render() }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(5) })
        }
        historyCard.addView(filters); historyCard.gap(10)
        val observations = person.history.filter { rangeDays == 0 || it.observedAt >= System.currentTimeMillis() - rangeDays * 86400000L }
        val coverage = ObservationCoverage.of(observations)
        if (coverage.count > 0) {
            val minutes = coverage.spanMinutes
            val summary = if (coverage.count == 1) getString(R.string.history_single_scope, date(coverage.firstAt!!))
                else getString(R.string.history_actual_scope, coverage.count, minutes / 1440, minutes % 1440 / 60, minutes % 60,
                    date(coverage.firstAt!!), date(coverage.lastAt!!))
            historyCard.addView(text(summary, 13f, ink)); historyCard.gap(10)
        }
        if (observations.isNotEmpty()) {
            val metric = LinearLayout(this)
            metric.addView(button(getString(R.string.ui_followers), chartFollowers) { chartFollowers = true; render() }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
            metric.addView(button(getString(R.string.ui_following), !chartFollowers) { chartFollowers = false; render() }, LinearLayout.LayoutParams(0, -2, 1f))
            historyCard.addView(metric); historyCard.gap(12)
            if (observations.size >= 2) {
                historyCard.addView(HistoryChartView(this, observations, chartFollowers, accent, muted).apply {
                    setPadding(0, dp(8), 0, dp(8))
                }, LinearLayout.LayoutParams(-1, dp(224)))
                val first = observations.first(); val last = observations.last()
                historyCard.addView(text(getString(R.string.ui_net_change_in_period_followers_following, delta(last.followers - first.followers), delta(last.following - first.following)), 13f, accent)); historyCard.gap(10)
            }
            historyCard.addView(button(getString(R.string.ui_view_records), false) {
                val records = observations.takeLast(10).asReversed().joinToString("\n\n") {
                    getString(R.string.ui_n_followers_following, it.username, date(it.observedAt), number(it.followers), number(it.following))
                }
                AlertDialog.Builder(this).setTitle(getString(R.string.ui_recent_records)).setMessage(records).setPositiveButton(getString(R.string.ui_close), null).show()
            })
        } else historyCard.addView(text(getString(R.string.ui_no_records_in_this_date_range_choose), 14f, muted))
        historyCard.gap(12)
        page.addView(historyCard); page.gap(16)
        val notesCard = AppUi.card(this)
        notesCard.addView(AppUi.heading(this, getString(R.string.ui_your_private_note), 18f)); notesCard.gap(8)
        if (person.note.isNotBlank()) { notesCard.addView(text(person.note, 14f, muted)); notesCard.gap(10) }
        notesCard.addView(button(getString(R.string.ui_edit_note_and_group), false) { editPerson(person) }, LinearLayout.LayoutParams(-1, -2)); notesCard.gap(10)
        page.addView(notesCard); page.gap(16)
        page.addView(button(getString(R.string.ui_remove_person), false) { removePerson(saved.session, p) }
            .apply { background = null; setTextColor(AppUi.coral) }, LinearLayout.LayoutParams(-1, -2)); page.gap(20)
    }

    private fun removePerson(session: Session, profile: Profile) {
        AlertDialog.Builder(this).setTitle(getString(R.string.ui_remove_this_person)).setMessage(getString(R.string.person_remove_confirm, profile.username))
                .setNegativeButton(getString(R.string.ui_cancel), null).setPositiveButton(getString(R.string.ui_remove)) { _, _ ->
                    if (account?.session != session) return@setPositiveButton
                    if (quickWatch.target == profile.userId) quickWatch.stop(false)
                    if (savePeople { people -> people.filterNot { it.latest.userId == profile.userId } }) {
                        try {
                            vault.journal.clearTarget(session, profile.userId)
                            ViewedProfiles.clear(session.userId, profile.userId)
                            avatars.remove(session.userId, profile.userId)
                            FollowListVault(this, if (preview) "previewlists" else "followlists").clearTarget(session, profile.userId)
                        }
                        catch (_: Exception) { showFailure(ApiFailure(ApiFailure.Kind.STORAGE)) }
                        ChangeNotifications(this, preview).cancel(session.userId, profile.userId)
                        if (selectedPersonId == profile.userId) selectedPersonId = null
                    }
                    render()
                }.show()
    }

    private fun input(hintValue: String, limit: Int) = EditText(this).apply {
        hint = hintValue; setTextColor(ink); setHintTextColor(muted); textSize = 16f
        background = shape(panel, 14, AppUi.line); setPadding(dp(14), dp(14), dp(14), dp(14))
        minimumHeight = dp(52)
        filters = arrayOf(InputFilter.LengthFilter(limit))
        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        if (android.os.Build.VERSION.SDK_INT >= 26) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
    }
    private fun addPerson() {
        val saved = account ?: return
        if (saved.people.size >= PeoplePolicy.MAX_PEOPLE) { message = resources.getQuantityString(R.plurals.ui_this_version_stores_up_to_people_you, PeoplePolicy.MAX_PEOPLE, PeoplePolicy.MAX_PEOPLE); render(); return }
        personSearch?.close()
        val reader=PreviewMode.api(gate,preview)
        personSearch=ProfileSearchDialog(this,
            saved.people.map {ProfileCandidate(it.latest.userId,it.latest.username,it.latest.name.orEmpty(),it.latest.details?.pictureUrl?.value)},
            search={q->if(preview)throw ApiFailure(ApiFailure.Kind.CONNECTION) else reader.search(saved.session,q)},
            cancel=reader::cancel,
            gate=gate,owner=saved.session.userId,
            select={candidate->if(account?.session==saved.session)readPerson(userId=candidate.id)},
            direct={username->if(account?.session==saved.session)readPerson(username=username)},
            dismissed={personSearch=null;needsConnection=gate.requiresConnection();if(visible && !isDestroyed && !isFinishing)render()}).also {it.show()}
    }

    private fun readPerson(username: String? = null, person: TrackedPerson? = null, userId: String? = null) {
        val saved = account ?: return
        if (loading) return
        if (needsConnection) { message = getString(R.string.ui_reconnect_your_account_first); render(); return }
        if (person != null && !PeoplePolicy.needsRefresh(person.latest, System.currentTimeMillis())) return
        if (gate.nextAllowedAt() > System.currentTimeMillis()) {
            if (queueRead { readPerson(username, person, userId) }) return
            message = ApiFailure(ApiFailure.Kind.TOO_SOON).userMessage(this@MainActivity); render(); return
        }
        loading = true; message = null; render()
        val ticket = ++generation
        worker.execute {
            var startedAt: Long? = null
            val result = try {
                val latest = vault.read()?.takeIf { it.session == saved.session } ?: throw ApiFailure(ApiFailure.Kind.CANCELLED)
                val cached = if (person != null) latest.people.firstOrNull { it.latest.userId == person.latest.userId }?.latest
                    ?: throw ApiFailure(ApiFailure.Kind.CANCELLED)
                    else latest.people.firstOrNull { if(userId!=null)it.latest.userId==userId else it.latest.username.equals(username, true) }?.latest
                Result.success(if (cached != null && !PeoplePolicy.needsRefresh(cached, System.currentTimeMillis())) ProfileRead(cached, false)
                    else { startedAt = System.currentTimeMillis(); ProfileRead(if (person != null) api.readId(saved.session, person.latest.userId)
                        else if(userId!=null)api.readId(saved.session,userId) else api.readUsername(saved.session, requireNotNull(username)), true, startedAt) })
            } catch (failure: ApiFailure) {
                if (person != null) vault.recordFailure(saved.session, person.latest.userId, startedAt, failure)
                Result.failure(failure)
            }
            catch (_: Exception) { Result.failure(ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)) }
            main.post {
                if (isDestroyed || ticket != generation) return@post
                loading = false
                result.onSuccess { read ->
                    try {
                        if(read.profile.userId==saved.session.userId) {peopleScreen=false;selectedPersonId=null;account=vault.read();return@onSuccess}
                        if (read.fetched) {
                            val committed = Observations.person(vault, saved.session, read.profile, existingRequired = person != null, startedAt = read.startedAt)
                            account = committed.account; syncMonitoring()
                            committed.change?.let { change -> notifyCurrent(saved.session, change) }
                            message = getString(R.string.ui_a_new_observation_was_saved_on_this)
                        } else { account = vault.read(); message = null }
                        selectedPersonId = read.profile.userId
                    } catch (_: Exception) { message = getString(R.string.ui_could_not_save_the_observation_previous_records) }
                }.onFailure { failure ->
                    val error = failure as ApiFailure
                    if (error.kind in setOf(ApiFailure.Kind.SESSION_EXPIRED, ApiFailure.Kind.VERIFICATION)) showFailure(error)
                    else message = if (error.kind == ApiFailure.Kind.WRONG_ACCOUNT) getString(R.string.ui_the_response_did_not_match_the_requested) else error.userMessage(this@MainActivity)
                }
                render()
            }
        }
    }
    private fun savePeople(reschedule: Boolean = true, update: (List<TrackedPerson>) -> List<TrackedPerson>): Boolean {
        val saved = account ?: return false
        return try { account = vault.update(saved.session) {
            val people = update(it.people)
            val targets = it.automation.targets.filter { id -> id == it.session.userId || people.any { person -> person.latest.userId == id } }.toSet()
            it.copy(people = people, automation = it.automation.copy(targets = targets, enabled = it.automation.enabled && targets.isNotEmpty()))
        }; if (reschedule) syncMonitoring(); true }
        catch (_: Exception) { message = getString(R.string.ui_could_not_save_people_previous_records_are); false }
    }
    private fun editPerson(person: TrackedPerson) {
        val group = input(getString(R.string.ui_group_optional), 40).apply { setSingleLine(true); setText(person.group) }
        val note = input(getString(R.string.ui_your_note_stored_only_on_this_device), 500).apply { minLines = 3; setText(person.note); inputType = inputType or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE }
        val box = stack().apply { setPadding(dp(24), dp(12), dp(24), 0); addView(group); gap(12); addView(note) }
        AlertDialog.Builder(this).setTitle(getString(R.string.ui_note_and_group)).setView(box).setNegativeButton(getString(R.string.ui_cancel), null)
            .setPositiveButton(getString(R.string.ui_save)) { _, _ ->
                savePeople { people -> people.map { if (it.latest.userId == person.latest.userId) it.copy(note = note.text.toString(), group = group.text.toString()) else it } }; render()
            }.show()
    }

    private fun syncMonitoring() {
        try {
            if (preview) PreviewMode.sync(this, account, gate) else MonitorScheduler.sync(this, account, gate)
        } catch (_: Exception) { if(message==null)message = getString(R.string.ui_could_not_schedule_the_background_task_check) }
    }
    private fun notifyCurrent(session: Session, change: ObservedProfileChange) {
        val notices = ChangeNotifications(this, preview)
        Observations.deliver(vault, session, change, notices::allowed, notices::post)
    }
    private fun intervalLabel(minutes: Int, choice: Boolean = false): String {
        val amount = if (minutes < 60) minutes else minutes / 60
        return resources.getQuantityString(
            if (minutes < 60) { if (choice) R.plurals.watch_choice_minutes else R.plurals.watch_interval_minutes }
            else { if (choice) R.plurals.watch_choice_hours else R.plurals.watch_interval_hours }, amount, amount)
    }

    private fun renderWatchStatus(page: LinearLayout, person: TrackedPerson, saved: SavedAccount) {
        val now = System.currentTimeMillis()
        val conditions = WatchConditions(ChangeNotifications(this, preview).allowed(), AppUi.offline(this) == true,
            gate.requiresConnection(), gate.automationHeld(), gate.blockedUntil(), gate.manualOnly(), gate.automaticNextAllowedAt(),
            notificationsRequired = !saved.automation.enabled)
        val status = WatchStatusPolicy.status(person, saved.people, conditions, now)
        val card = AppUi.card(this)
        card.addView(AppUi.heading(this, getString(R.string.watch_status_title), 20f)); card.gap(10)
        card.addView(text(getString(R.string.watch_last_success, date(person.latest.observedAt)), 13f, muted)); card.gap(8)
        val watch = android.widget.Switch(this).apply {
            setText(R.string.watch_toggle); textSize = 16f; setTextColor(ink)
            isChecked = person.watching; minimumHeight = dp(56)
            setOnClickListener {
                isChecked = person.watching
                if (person.watching) setWatch(person.latest.userId, saved.session.userId, person.intervalMinutes, false)
                else watchSettings(person)
            }
        }
        card.addView(watch, LinearLayout.LayoutParams(-1, -2))
        if (saved.automation.enabled && person.latest.userId in saved.automation.targets) {
            card.addView(text(getString(R.string.auto_person_scope), 12f, AppUi.teal)); card.gap(8)
        }
        if (person.watching) {
            card.addView(text(intervalLabel(person.intervalMinutes), 14f, accent, true)); card.gap(8)
            card.addView(text(when {
                status.eligibleAt == null -> getString(R.string.watch_target_paused)
                status.eligibleAt <= now -> getString(R.string.watch_target_due)
                else -> getString(R.string.watch_target_at, date(status.eligibleAt))
            }, 13f, ink)); card.gap(8)
        }
        card.addView(text(when (status.reason) {
            WatchReason.OFF -> getString(R.string.watch_reason_off)
            WatchReason.NOTIFICATIONS_OFF -> getString(R.string.watch_reason_notifications)
            WatchReason.LIMITED -> getString(R.string.watch_reason_limited, date(conditions.blockedUntil))
            WatchReason.CONNECTION_REQUIRED -> getString(R.string.watch_reason_connection)
            WatchReason.CONNECTION_IN_PROGRESS -> getString(R.string.watch_reason_link)
            WatchReason.MANUAL_ONLY -> getString(R.string.watch_reason_manual)
            WatchReason.OFFLINE -> getString(R.string.watch_reason_offline)
            WatchReason.INTERVAL -> getString(R.string.watch_reason_interval)
            WatchReason.SHARED_BUDGET -> getString(R.string.watch_reason_budget)
            WatchReason.QUEUED -> resources.getQuantityString(R.plurals.watch_reason_queue, status.ahead, status.ahead)
            WatchReason.READY -> getString(R.string.watch_reason_ready)
        }, 13f, if (status.eligibleAt == null && person.watching) AppUi.coral else muted)); card.gap(10)
        if (status.ahead > 0 && status.reason != WatchReason.QUEUED) {
            card.addView(text(resources.getQuantityString(R.plurals.watch_reason_queue, status.ahead, status.ahead), 13f, muted)); card.gap(10)
        }
        if (person.watching) {
            card.addView(button(getString(R.string.watch_change_interval), false) { watchSettings(person) }); card.gap(8)
        }
        card.addView(button(getString(R.string.watch_refresh_status), false) {
            try { account = vault.read(); syncMonitoring() }
            catch (_: Exception) { message = ApiFailure(ApiFailure.Kind.STORAGE).userMessage(this@MainActivity) }
            render()
        })
        page.addView(card); page.gap(16)
    }

    private fun watchSettings(person: TrackedPerson) {
        var selection = WatchIntervals.minutes.indexOf(person.intervalMinutes).coerceAtLeast(0)
        AlertDialog.Builder(this).setTitle(getString(R.string.ui_change_notifications))
            .setSingleChoiceItems(WatchIntervals.minutes.map { intervalLabel(it, true) }.toTypedArray(), selection) { _, index -> selection = index }
            .setNegativeButton(getString(R.string.ui_cancel), null).setPositiveButton(getString(R.string.ui_enable_monitoring)) { _, _ ->
                val saved = account ?: return@setPositiveButton
                pendingWatchId = person.latest.userId; pendingWatchOwner = saved.session.userId; pendingWatchMinutes = WatchIntervals.minutes[selection]
                val notices = ChangeNotifications(this, preview); notices.ensureChannel()
                if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1401)
                } else finishWatchPermission()
            }.show()
    }
    private fun finishWatchPermission() {
        val id = pendingWatchId; val owner = pendingWatchOwner; val minutes = pendingWatchMinutes
        pendingWatchId = null; pendingWatchOwner = null
        if (id == null || owner == null) return
        if (!ChangeNotifications(this, preview).allowed()) {
            message = getString(R.string.ui_notification_permission_or_the_channel_is_off); render(); return
        }
        setWatch(id, owner, minutes, true)
    }
    private fun setWatch(id: String, owner: String, minutes: Int, enabled: Boolean) {
        if (account?.session?.userId != owner) return
        if (!enabled && quickWatch.target == id) quickWatch.stop(false)
        if (savePeople { people -> people.map { person -> if (person.latest.userId != id) person else WatchPreference.set(person, minutes, enabled)
            } }) {
            message = if (enabled) getString(R.string.ui_monitoring_enabled_android_scheduling_may_delay_checks) else getString(R.string.ui_background_monitoring_disabled_for_this_person)
            if (!enabled) ChangeNotifications(this, preview).cancel(owner, id)
        }
        render()
    }
    @Deprecated("Platform permission callback retained without an additional UI dependency")
    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1401) finishWatchPermission()
        if (requestCode == 1402) finishAutomationPermission()
    }
    private fun openNotification(saved: SavedAccount?) {
        val owner = intent.getStringExtra("notification_owner")
        val person = intent.getStringExtra("notification_person")
        intent.removeExtra("notification_owner"); intent.removeExtra("notification_person")
        if (saved != null && saved.session.userId == owner) {
            if (saved.profile.userId == person) { peopleScreen = false; selectedPersonId = null }
            else if (saved.people.any { it.latest.userId == person }) { peopleScreen = true; selectedPersonId = person }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        if (!::vault.isInitialized || loading || web != null) return
        if(!preview)intent.getStringExtra("notification_owner")?.takeIf {it!=account?.session?.userId}?.let {owner->
            try {if(AccountCatalog(this).entries().any {it.id==owner}) {switchAccount(owner);return}}catch (_: Exception) {showFailure(ApiFailure(ApiFailure.Kind.STORAGE));return}
        }
        try { account = vault.read(); openNotification(account); render() }
        catch (_: Exception) { message = ApiFailure(ApiFailure.Kind.STORAGE).userMessage(this@MainActivity); render() }
    }

    private fun pauseMessage(): String? {
        if (!gate.restrictionPending()) return null
        return if (gate.blockedUntil() > System.currentTimeMillis()) getString(R.string.ui_local_cooldown, date(gate.blockedUntil()))
        else getString(R.string.ui_automatic_retries_are_off_refresh_when_you)
    }
    private val gateWake = Runnable {
        if (!isDestroyed) {
            if (web == null) render()
            else {
                updateLoginCompletion()
                // No automatic retry when a pause expires.
                if (!loginAttempted && !loginRelayIssue) loginStatus?.text = if(webComparison && browserProof != null && gate.nextAllowedAt() <= System.currentTimeMillis())
                    getString(R.string.web_compare_native_ready) else pauseMessage() ?: getString(if(webComparison) R.string.web_compare_instruction else R.string.connection_ready)
                continueAuthenticatedLogin()
            }
        }
    }
    private fun scheduleGateWake() {
        main.removeCallbacks(gateWake)
        val displayed = if (peopleScreen) account?.people?.firstOrNull { it.latest.userId == selectedPersonId }?.latest else account?.profile
        val freshUntil = displayed?.observedAt?.plus(PeoplePolicy.FRESH_MS) ?: 0L
        val next = if (web != null) loginNextAllowedAt() else maxOf(gate.nextAllowedAt(), freshUntil)
        val wait = next - System.currentTimeMillis()
        if (wait > 0) main.postDelayed(gateWake, wait)
    }

    private fun refresh(automatic: Boolean = false) {
        if(!automatic) {localAccountView=false;intent.removeExtra("local_account_switch")}
        val saved = account ?: return
        if (loading) return
        if (!PeoplePolicy.needsRefresh(saved.profile, System.currentTimeMillis())) return
        if (gate.nextAllowedAt() > System.currentTimeMillis()) {
            if (!automatic && queueRead { refresh() }) return
            message = pauseMessage() ?: ApiFailure(ApiFailure.Kind.TOO_SOON).userMessage(this@MainActivity); render(); return
        }
        if (automatic) {
            try { if (!gate.reserveAutomatic()) return }
            catch (failure: ApiFailure) { showFailure(failure); render(); return }
        }
        loading = true; message = null; render()
        val ticket = ++generation
        worker.execute {
            var startedAt: Long? = null
            val result = try {
                val latest = vault.read()?.takeIf { it.session == saved.session } ?: throw ApiFailure(ApiFailure.Kind.CANCELLED)
                Result.success(if (!PeoplePolicy.needsRefresh(latest.profile, System.currentTimeMillis())) ProfileRead(latest.profile, false)
                    else { startedAt = System.currentTimeMillis(); ProfileRead(api.read(saved.session), true, startedAt) })
            } catch (failure: ApiFailure) {
                vault.recordFailure(saved.session, saved.session.userId, startedAt, failure)
                Result.failure(failure)
            }
            main.post {
                if (isDestroyed || ticket != generation) return@post
                loading = false
                result.onSuccess {
                    try {
                        account = if (it.fetched) Observations.own(vault, saved.session, it.profile, it.startedAt).account else vault.read()
                        needsConnection = gate.requiresConnection(); syncMonitoring()
                    }
                    catch (_: Exception) { showFailure(ApiFailure(ApiFailure.Kind.STORAGE)) }
                }
                    .onFailure { showFailure(it as ApiFailure) }
                render()
            }
        }
    }

    private fun showFailure(failure: ApiFailure) {
        message = pauseMessage() ?: failure.userMessage(this@MainActivity)
        needsConnection = failure.kind in setOf(ApiFailure.Kind.SESSION_EXPIRED, ApiFailure.Kind.VERIFICATION,
            ApiFailure.Kind.WRONG_ACCOUNT, ApiFailure.Kind.STORAGE)
        syncMonitoring()
    }

    private fun privacy() {
        AlertDialog.Builder(this).setTitle(getString(R.string.ui_privacy_in_plain_language))
            .setMessage(getString(R.string.ui_web_sign_in_uses_instagram_s_own) +
                getString(R.string.ui_your_session_chosen_people_history_and_notes) +
                getString(R.string.ui_requests_go_to_instagram_we_have_no) +
                getString(R.string.ui_visible_profile_photos_may_load_from_the) +
                getString(R.string.ui_this_is_an_independent_app_instagram_may) + "\n\n" + getString(R.string.journal_privacy) + "\n\n" + getString(R.string.accounts_privacy))
            .setPositiveButton(getString(R.string.ui_got_it), null).show()
    }

    private fun confirmRemove() {
        if (loading) return
        AlertDialog.Builder(this).setTitle(getString(R.string.ui_confirm_remove_connection))
            .setMessage(getString(if(preview)R.string.ui_your_session_people_lists_history_and_notes else R.string.accounts_remove_warning) + "\n\n" + getString(R.string.security_remove_local))
            .setNegativeButton(getString(R.string.ui_cancel), null).setPositiveButton(getString(R.string.ui_remove)) { _, _ ->
                val removedOwner=account?.session?.userId
                quickWatch.stop(false)
                avatars.close()
                loading = true; render()
                worker.execute {
                    val ok = try {
                        ViewedProfiles.clear()
                        avatars.remove(if(preview)null else removedOwner)
                        if (preview) PreviewMode.cancel(this) else MonitorScheduler.cancel(this)
                        if (!preview) { DesktopLink(this).clear(); if(AccountCatalog(this).entries().size<=1)PhoneIdentity.clear(this); gate.setPhoneLoginActive(false) }
                        if(preview) {
                            FollowListVault(this,"previewlists").clear();gate.clearListCapacityPause();vault.clear();ChangeNotifications(this,true).clear()
                        } else removedOwner?.let {owner->
                            SessionVault(this,owner=owner).clear();ChangeNotifications(this).clearOwner(owner)
                        }
                        true
                    } catch (_: Exception) { false }
                    main.post {
                        if (isDestroyed) return@post
                        avatars = AvatarImages(this, PreviewMode.avatarFetcher(preview), if (preview) "previewavatars" else "avatars")
                        loading = false
                        if(ok && !preview) {intent.putExtra("local_account_switch",true);recreate();return@post}
                        if (ok) { account = null; peopleScreen = false; selectedPersonId = null; needsConnection = false; message = getString(R.string.ui_the_connection_on_this_device_was_removed) }
                        else message = getString(R.string.ui_could_not_remove_the_connection_please_try)
                        render()
                    }
                }
            }.show()
    }

    private fun recordLoginPage(trace: LoginPageTrace? = loginTrace, action: (LoginPageTrace) -> Unit) {
        try { trace?.let(action) } catch (_: Exception) { if (loginTrace === trace) loginTraceFailed = true }
    }
    private fun loginFeedback(heading: Int, detail: Int, busy: Boolean = false) {
        loginHeading?.setText(heading)
        loginStatus?.setText(detail)
        loginProgress?.visibility = if (busy) View.VISIBLE else View.GONE
    }
    private fun inspectLoginPage() {
        val browser = web ?: return
        if (!visible || loginChecking || loginAttempted || browserProof != null) return
        val epoch = loginPageEpoch
        val trace = loginTrace
        // Presence/visibility only: never read input values, page text, cookies or URLs into diagnostics.
        browser.evaluateJavascript("(function(){function visible(s){return Array.prototype.some.call(document.querySelectorAll(s),function(n){return n.getClientRects().length>0})}return visible('input[autocomplete=one-time-code],input[name=verificationCode],input[name=security_code],input[name=verification_code]')?'code':visible('input[name=username],input[name=password],input[name=email],input[type=email],input[type=password]')?'form':'other'})()") { raw ->
            if (web !== browser || loginPageEpoch != epoch) return@evaluateJavascript
            val form = when (raw) { "\"form\"" -> true; "\"code\"", "\"other\"" -> false; else -> null }
            recordLoginPage(trace) { it.form(form) }
            if (raw == "\"code\"" && !loginAuthenticating && !loginChecking) {
                loginFeedback(R.string.web_login_step_code, R.string.web_login_two_factor)
                return@evaluateJavascript
            }
            if (!loginChecking && !loginAttempted && browserProof == null && !loginRelayIssue) {
                if (form == true) loginFeedback(R.string.web_login_step_credentials, if (webComparison) R.string.web_compare_instruction else R.string.web_connection_instruction)
                else if (form == false && try { java.net.URI(browser.url ?: "").rawPath == "/accounts/login/" } catch (_: Exception) { false })
                    loginStatus?.setText(R.string.page_trace_waiting)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun startLogin(compare: Boolean = false) {
        if (preview) { message = getString(R.string.ui_real_account_connections_are_unavailable_in_demo); render(); return }
        if (loading || web != null) return
        try {
            if (WebSessions(this).pending() != null) {
                startActivity(Intent(this, WebPendingConnectionActivity::class.java)); return
            }
        } catch (_: Exception) { showFailure(ApiFailure(ApiFailure.Kind.STORAGE)); return }
        if (gate.nextWebAuthenticationAt() > System.currentTimeMillis()) { message = getString(R.string.ui_next_connection_attempt, clock(gate.nextWebAuthenticationAt())); render(); return }
        try { gate.pauseAutomatic(); if(compare)WebConnectionJournal(this).reset(); syncMonitoring() } catch (_: Exception) { showFailure(ApiFailure(ApiFailure.Kind.STORAGE)); render(); return }
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        loading = false; loginChecking = false; loginAttempted = false; loginLoaded = false
        webComparison = compare; browserProbe = null; browserProof = null; nativeComparisonStarted = false
        webFixture = WebLoginMode.enabled(this)
        val page = stack().apply { setBackgroundColor(bg) }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8)) }
        header.addView(button(getString(R.string.ui_cancel), false) { leaveLogin() }, LinearLayout.LayoutParams(-2, dp(48)))
        header.addView(text("  instagram.com", 16f, accent, true), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(button(getString(R.string.page_trace_open), false) { requestInfo() }, LinearLayout.LayoutParams(-2, dp(48)))
        page.addView(header)
        loginHeading = text(getString(R.string.web_login_step_loading), 17f, ink, true).apply { setPadding(dp(18), dp(4), dp(18), 0) }
        page.addView(loginHeading)
        loginProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { isIndeterminate = true }
        page.addView(loginProgress, LinearLayout.LayoutParams(-1, dp(3)))
        loginStatus = text(getString(if(compare) R.string.web_compare_instruction else R.string.web_connection_instruction), 13f, muted).apply { setPadding(dp(18), dp(8), dp(18), dp(8)) }
        page.addView(loginStatus)
        page.addView(text(getString(R.string.web_connection_privacy), 12f, muted).apply { setPadding(dp(18), 0, dp(18), dp(8)) })
        val browser = try { WebView(this) } catch (_: Exception) {
            message = getString(R.string.ui_could_not_open_android_system_webview_update)
            if (BuildConfig.DEBUG) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            render(); return
        }
        web = browser
        loginTraceFailed = false
        loginRelayIssue = false
        loginAuthenticating = false; autoVerifyLogin = false; loginChallenge = null; loginSecurityReset=false
        directNativeButton = null
        val pageTrace = LoginPageTrace(this)
        loginTrace = pageTrace
        recordLoginPage(pageTrace) { it.flush() }
        WebView.setWebContentsDebuggingEnabled(false)
        LoginBrowserResources.restrictWorkers()
        browser.setBackgroundColor(Color.WHITE)
        browser.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true
            userAgentString = LoginBrowserResources.desktopUserAgent(userAgentString)
            useWideViewPort = true; loadWithOverviewMode = true
            setSupportZoom(true); builtInZoomControls = true; displayZoomControls = false
            allowFileAccess = false; allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_NO_CACHE
            setSupportMultipleWindows(false); javaScriptCanOpenWindowsAutomatically = false
            @Suppress("DEPRECATION") setSaveFormData(false)
        }
        CookieManager.getInstance().apply { setAcceptCookie(true); setAcceptThirdPartyCookies(browser, false) }
        challengeButton = button(getString(R.string.web_login_show_challenge), false) { showLoginChallenge() }.apply { visibility = View.GONE }
        page.addView(challengeButton)
        if(compare) {
            directNativeButton=button(getString(R.string.web_compare_direct_native),false,networkAction=true) {checkLogin(compareBrowser=false)}
            page.addView(directNativeButton)
        }
        loginRelay = WebLoginRelay(this, browser, gate, worker, pageTrace, { problem, status ->
            if (web === browser) {
                loginAuthenticating = false
                loginRelayIssue = true
                loginProgress?.visibility = View.GONE
                loginHeading?.setText(R.string.web_login_step_attention)
                if (problem.kind in setOf(ApiFailure.Kind.LIMITED, ApiFailure.Kind.CONNECTION, ApiFailure.Kind.STORAGE)) webLoginFailed(problem, status)
                else if (problem.kind == ApiFailure.Kind.TOO_SOON) endRejectedLogin(R.string.web_login_not_sent)
                else loginStatus?.text = when (problem.kind) {
                    ApiFailure.Kind.POLICY -> getString(R.string.web_relay_blocked)
                    ApiFailure.Kind.INVALID_RESPONSE -> getString(R.string.web_relay_invalid_response)
                    else -> problem.userMessage(this)
                }
                updateLoginCompletion()
                scheduleGateWake()
            }
        }, client = WebLoginMode.relayClient(this, webFixture), started = {
            if (web === browser) {
                loginAuthenticating = true; loginRelayIssue = true
                loginFeedback(R.string.web_login_step_waiting, R.string.web_login_sending, true); updateLoginCompletion()
            }
        }, completed = completion@{ facts, challenge ->
            if (web === browser) {
                loginAuthenticating = false; loginRelayIssue = true
                loginProgress?.visibility = View.GONE
                loginChallenge?.close(); loginChallenge = challenge
                challengeButton?.visibility = if (challenge != null) View.VISIBLE else View.GONE
                completionButton?.visibility = if (facts.captchaNeeded == true || facts.twoFactorPayloadPresent == true) View.GONE else View.VISIBLE
                when {
                    facts.captchaNeeded == true && challenge == null -> {
                        endLoginVerificationUnavailable(R.string.web_login_captcha_unavailable)
                        return@completion
                    }
                    facts.captchaNeeded == true -> loginFeedback(R.string.web_login_step_captcha, R.string.web_login_captcha)
                    facts.twoFactorPayloadPresent == true -> {
                        autoVerifyLogin = !webComparison
                        loginFeedback(R.string.web_login_step_code, R.string.web_login_two_factor)
                    }
                    facts.authenticated == true && facts.sessionCookieReceived && facts.ownerCookieReceived && facts.cookiesApplied == true -> {
                        autoVerifyLogin = !webComparison
                        loginFeedback(R.string.web_login_step_profile, if (webComparison) R.string.web_login_comparison_ready else R.string.web_login_profile_pending, !webComparison)
                    }
                    facts.authenticated == false -> {
                        endRejectedLogin(R.string.web_login_rejected)
                        return@completion
                    }
                    else -> loginStatus?.setText(R.string.web_relay_invalid_response)
                }
                updateLoginCompletion(); scheduleGateWake(); continueAuthenticatedLogin()
            } else challenge?.close()
        }, securityScope={url->loginChallenge?.navigation("GET",url)==true}, securityBlocked={
            if(web===browser) loginFeedback(R.string.web_login_step_attention,R.string.web_login_security_unsupported_request)
        }, securityContext={url->loginChallenge?.viewContext(url)}, securityStarted={
            if(web===browser) {
                loginAuthenticating=true
                if(!webComparison)completionButton?.visibility=View.GONE
                loginFeedback(R.string.web_login_step_waiting,R.string.web_login_security_loading,true);updateLoginCompletion()
            }
        }, securityCompleted={
            if(web===browser) {
                loginAuthenticating=false;autoVerifyLogin=!webComparison
                loginFeedback(R.string.web_login_step_code,R.string.web_login_verification_code_ready)
                updateLoginCompletion();scheduleGateWake();continueAuthenticatedLogin()
                main.removeCallbacks(inspectPage);main.postDelayed(inspectPage,500)
            }
        }, codeStarted={
            if(web===browser) {
                loginAuthenticating=true
                completionButton?.visibility=View.GONE
                loginFeedback(R.string.web_login_step_waiting,R.string.web_login_submitting_code,true)
                updateLoginCompletion()
            }
        }).also { if (!it.install()) { loginRelayIssue = true; loginFeedback(R.string.web_login_step_attention, R.string.web_relay_unavailable) } }
        browser.webViewClient = object : WebViewClient() {
            @androidx.annotation.RequiresApi(26)
            override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                if(web===view) {
                    recordLoginPage(pageTrace) {it.error(if(detail.didCrash()) -1001 else -1002,true)}
                    message=getString(R.string.web_login_renderer_stopped)
                    leaveLogin(rendererGone = true)
                }
                return true
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url=request.url.toString()
                val allowed=loginSecurityReset && request.isForMainFrame && url=="about:blank" || LoginBrowserPolicy.navigation(url) ||
                    request.isForMainFrame && (loginChallenge?.navigation(request.method,url)==true ||
                        loginChallenge?.followRedirect(request.method,url,true,request.isRedirect)==true) ||
                    !request.isForMainFrame && loginChallenge?.subframe(url)==true
                if(!allowed || request.isRedirect) recordLoginPage(pageTrace) {it.navigated(allowed,request.isRedirect,WebLoginChallenge.pageRoute(url))}
                if (!allowed && !loginRelayIssue) loginStatus?.setText(R.string.security_login_scope)
                return !allowed
            }
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val decision = if (loginChallenge?.resource(request.method, request.url.toString()) == true)
                    LoginBrowserPolicy.ResourceDecision(true, LoginBrowserPolicy.ResourceKind.CAPTCHA)
                    else LoginBrowserPolicy.resourceDecision(request.method, request.url.toString())
                if (web === view) recordLoginPage(pageTrace) { it.resource(request.method, decision) }
                return if (!decision.allowed) LoginBrowserResources.blocked()
                else WebLoginMode.intercept(request, webFixture, this@MainActivity)
                    ?: if (request.method == "GET" && LoginBrowserPolicy.landing(request.url.toString())) LoginBrowserResources.landing() else null
            }
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                if (web === view) recordLoginPage(pageTrace) { it.error(WebViewClient.ERROR_FAILED_SSL_HANDSHAKE, true) }
                main.post { if (web === view) { message = getString(R.string.connection_tls_error); leaveLogin() } }
            }
            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                if (web !== view) return
                loginRelay?.cancel()
                loginPageEpoch++
                recordLoginPage(pageTrace) { it.started(WebLoginChallenge.pageRoute(url)) }
                main.removeCallbacks(inspectPage); main.postDelayed(inspectPage, 12000)
                loginLoaded = false; updateLoginCompletion()
                main.removeCallbacks(loginLoadTimeout)
                main.postDelayed(loginLoadTimeout, 45000)
                main.removeCallbacks(gateWake)
                if (webComparison && (browserProbe?.running == true || browserProof != null)) {
                    message = getString(R.string.web_compare_cancelled); leaveLogin()
                }
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (web === view && response.statusCode in 400..599) recordLoginPage(pageTrace) {
                    it.http(response.statusCode, request.isForMainFrame,
                        loginChallenge?.resource(request.method, request.url.toString()) == true || LoginBrowserPolicy.resourceDecision(request.method, request.url.toString()).allowed)
                }
                if (response.statusCode != 429 || !NavigationPolicy.isInstagram(request.url.toString())) return
                val retry = response.responseHeaders?.entries?.firstOrNull { it.key.equals("Retry-After", true) }?.value
                val responseUrl = request.url.toString()
                main.post {
                    if (web !== view) return@post
                    try {
                        if(webComparison && browserProbe?.receivedHttpError(responseUrl,429,retry)==true)return@post
                        gate.limited(retry, reason = "HTTP 429 / Instagram sayfa kaynağı")
                        if (webComparison) {
                            val journal=WebConnectionJournal(this@MainActivity)
                            journal.begin(WebConnectionJournal.Step.PAGE)
                            journal.end(WebConnectionJournal.Step.PAGE,WebConnectionJournal.Outcome.LIMITED,429)
                        }
                        webLoginFailed(ApiFailure(ApiFailure.Kind.LIMITED), 429)
                    }
                    catch (_: Exception) { webLoginFailed(ApiFailure(ApiFailure.Kind.STORAGE), 429) }
                }
            }
            override fun onPageFinished(view: WebView, url: String) {
                if (web !== view) return
                recordLoginPage(pageTrace) { it.finished(WebLoginChallenge.pageRoute(url)) }
                if(loginSecurityReset) {
                    if(url=="about:blank") {
                        loginSecurityReset=false
                        loginChallenge?.takeIf {it.authorizedSecurityPage}?.let {view.loadUrl(it.page)}
                    }
                    return
                }
                main.removeCallbacks(loginLoadTimeout)
                if (!loginAuthenticating && !loginChecking && !autoVerifyLogin) loginProgress?.visibility = View.GONE
                main.removeCallbacks(inspectPage); main.postDelayed(inspectPage, 12000)
                if (NavigationPolicy.isInstagram(url)) {
                    loginLoaded = true
                    updateLoginCompletion()
                    scheduleGateWake()
                    continueAuthenticatedLogin()
                    if(loginChallenge?.authorizedSecurityPage==true && WebLoginChallenge.pageRoute(url)==WebLoginChallenge.Companion.Route.LOGIN &&
                        !loginChecking && readLoginSession(view)==null) {
                        autoVerifyLogin=false
                        loginFeedback(R.string.web_login_step_attention,R.string.web_login_security_returned_login)
                    }
                    inspectLoginPage()
                }
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (web !== view) return
                recordLoginPage(pageTrace) { it.error(error.errorCode, request.isForMainFrame) }
                if (request.isForMainFrame) main.post {
                    if (web === view) {
                        main.removeCallbacks(loginLoadTimeout)
                        if (!loginRelayIssue) loginFeedback(R.string.web_login_step_attention, R.string.web_login_page_timeout)
                        else { loginProgress?.visibility = View.GONE }
                    }
                }
            }
        }
        page.addView(browser, LinearLayout.LayoutParams(-1, 0, 1f))
        completionButton = button(getString(if(compare) R.string.web_compare_browser else R.string.web_connection_verify), false, networkAction = true) { checkLogin() }
        updateLoginCompletion()
        page.addView(completionButton, LinearLayout.LayoutParams(-1, dp(54)))
        root.removeAllViews(); root.addView(page)
        CookieManager.getInstance().removeAllCookies {
            if (web === browser) {
                WebStorage.getInstance().deleteAllData(); browser.clearCache(true)
                browser.loadUrl("https://www.instagram.com/accounts/login/")
            }
        }
    }

    private fun updateLoginCompletion() {
        val enabled = web != null && loginLoaded && !loginAttempted && !loginChecking && !loginAuthenticating &&
            loginNextAllowedAt() <= System.currentTimeMillis()
        completionButton?.isEnabled = enabled
        completionButton?.alpha = if (enabled) 1f else .5f
        directNativeButton?.apply {
            visibility=if(browserProof==null && completionButton?.visibility==View.VISIBLE)View.VISIBLE else View.GONE
            isEnabled=enabled;alpha=if(enabled)1f else .5f
        }
    }

    private fun loginNextAllowedAt(): Long = if (webComparison && browserProof != null) gate.nextAllowedAt() else gate.nextPrototypeAt()

    private fun showLoginChallenge() {
        val browser = web ?: return
        val challenge = loginChallenge ?: return
        AlertDialog.Builder(this).setTitle(R.string.web_login_show_challenge).setMessage(R.string.web_login_challenge_privacy)
            .setNegativeButton(R.string.ui_cancel, null).setPositiveButton(R.string.web_login_show_challenge) { _, _ ->
                if (web === browser && loginChallenge === challenge) {
                    challenge.authorize(); challengeButton?.visibility = View.GONE
                    completionButton?.visibility = if(webComparison || !challenge.securityPage)View.VISIBLE else View.GONE
                    autoVerifyLogin = !webComparison
                    loginFeedback(R.string.web_login_step_captcha, R.string.web_login_complete_challenge)
                    if(challenge.securityPage) {
                        loginSecurityReset=true; loginLoaded=false; updateLoginCompletion()
                        browser.stopLoading(); browser.loadUrl("about:blank")
                    } else browser.loadUrl(challenge.page)
                }
            }.show()
    }

    private fun endLoginVerificationUnavailable(reason: Int) {
        val retryAt = gate.nextWebAuthenticationAt()
        val details = getString(reason) + if (retryAt > System.currentTimeMillis())
            "\n\n" + getString(R.string.ui_next_connection_attempt, clock(retryAt)) else ""
        message = details
        leaveLogin()
        AlertDialog.Builder(this).setTitle(R.string.web_login_step_attention).setMessage(details)
            .setNeutralButton(R.string.ui_request_information) { _, _ -> requestInfo() }
            .setNegativeButton(R.string.ui_close, null)
            .setPositiveButton(R.string.phone_open_instagram) { _, _ ->
                try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.instagram.com/"))) }
                catch (_: android.content.ActivityNotFoundException) { message = getString(reason); render() }
            }.show()
    }

    private fun endRejectedLogin(reason: Int) {
        val details = getString(reason) + "\n\n" + getString(R.string.ui_next_connection_attempt, clock(gate.nextWebAuthenticationAt()))
        message = details
        leaveLogin()
        AlertDialog.Builder(this).setTitle(R.string.web_login_not_accepted).setMessage(details)
            .setNeutralButton(R.string.ui_request_information) { _, _ -> requestInfo() }
            .setPositiveButton(R.string.ui_close, null).show()
    }

    private fun continueAuthenticatedLogin() {
        if (!visible || !autoVerifyLogin || webComparison || !loginLoaded || loginSecurityReset || loginAttempted || loginChecking || loginAuthenticating) return
        val browser = web ?: return
        if (readLoginSession(browser)==null) return
        if (loginNextAllowedAt() > System.currentTimeMillis()) { scheduleGateWake(); return }
        autoVerifyLogin = false
        checkLogin()
    }
    private fun readLoginSession(browser: WebView): WebSession? = try {
        WebSessionCodec.fromHeader(CookieManager.getInstance().getCookie("https://www.instagram.com/"),browser.settings.userAgentString)
            .copy(claim = loginRelay?.sessionClaim ?: "0")
    } catch (_: Exception) {null}

    private fun startBrowserComparison(browser: WebView, state: WebSession) {
        if (browserProbe != null) return
        val probe = BrowserProfileProbe(this,browser,gate,WebConnectionJournal(this)) { result, proof ->
            if (web !== browser || isDestroyed) return@BrowserProfileProbe
            loginChecking = false
            result.onSuccess { profile ->
                browserProof = proof; loginAttempted = false
                loginProgress?.visibility = View.GONE
                completionButton?.setText(R.string.web_compare_native)
                loginStatus?.text = getString(R.string.web_compare_browser_success,number(profile.followers),number(profile.following))
                updateLoginCompletion()
                scheduleGateWake()
            }.onFailure { webLoginFailed(it as? ApiFailure ?: ApiFailure(ApiFailure.Kind.INVALID_RESPONSE),gate.lastHttpStatus()) }
        }
        browserProbe = probe;loginChecking = true;loginAttempted = true
        updateLoginCompletion();loginFeedback(R.string.web_login_step_profile,R.string.web_compare_reading,true)
        try { probe.start(state) }
        catch (error: Exception) { loginChecking = false;webLoginFailed(error as? ApiFailure ?: ApiFailure(ApiFailure.Kind.STORAGE),null) }
    }

    private fun checkLogin(compareBrowser: Boolean = true) {
        val browser = web ?: return
        if (!loginLoaded || loginChecking || loginAttempted) return
        if (loginNextAllowedAt() > System.currentTimeMillis()) {
            loginStatus?.text = getString(R.string.connection_next_try, clock(loginNextAllowedAt()))
            scheduleGateWake(); return
        }
        val state = readLoginSession(browser)
        if (state == null) {
            loginStatus?.setText(R.string.connection_incomplete)
            return
        }
        if (webComparison && browserProof == null && compareBrowser) { startBrowserComparison(browser,state); return }
        val proof = browserProof
        if (webComparison) {
            try { WebConnectionJournal(this).begin(WebConnectionJournal.Step.NATIVE); nativeComparisonStarted = true }
            catch (_: Exception) { webLoginFailed(ApiFailure(ApiFailure.Kind.STORAGE),null); return }
        }
        loginChecking = true; loginAttempted = true
        updateLoginCompletion()
        loginFeedback(R.string.web_login_step_profile,R.string.connection_checking,true)
        val ticket = ++generation
        val expected = account?.session
        val attemptsBefore = gate.attempts()
        val attempt = WebLoginMode.attempt(this, gate, webFixture)
        webAttempt = attempt
        worker.execute {
            val result = try {
                Result.success(attempt.connect(state, expected, proof))
            } catch (failure: ApiFailure) { Result.failure(failure) }
            catch (_: Exception) { Result.failure(ApiFailure(ApiFailure.Kind.STORAGE)) }
            main.post {
                if (isDestroyed || ticket != generation || web !== browser) return@post
                loginChecking = false
                if (webComparison) {
                    val http = gate.lastHttpStatus().takeIf { gate.attempts() > attemptsBefore }
                    try { WebConnectionJournal(this).end(WebConnectionJournal.Step.NATIVE,
                        if (result.isSuccess) WebConnectionJournal.Outcome.SUCCESS else WebConnectionJournal.outcome((result.exceptionOrNull() as? ApiFailure)?.kind ?: ApiFailure.Kind.STORAGE),http) }
                    catch (_: Exception) { message = getString(R.string.web_compare_journal_failed) }
                    nativeComparisonStarted = false
                }
                result.onSuccess {
                    // The attempt owns the commit; this ticket only publishes its result to the current screen.
                    try {
                        account = it
                        localAccountView=false;intent.removeExtra("local_account_switch")
                        message = getString(R.string.accounts_added)
                        needsConnection = false; syncMonitoring(); leaveLogin()
                    } catch (_: Exception) {
                        loginProgress?.visibility = View.GONE; loginHeading?.setText(R.string.web_login_step_attention)
                        loginStatus?.text = ApiFailure(ApiFailure.Kind.STORAGE).userMessage(this@MainActivity)
                    }
                }.onFailure {
                    val status = gate.lastHttpStatus().takeIf { gate.attempts() > attemptsBefore }
                    webLoginFailed(it as ApiFailure, status)
                }
                updateLoginCompletion()
                scheduleGateWake()
            }
        }
    }

    private fun webLoginFailed(failure: ApiFailure, status: Int?) {
        pendingWeb = try { WebSessions(this).pending() != null } catch (_: Exception) { false }
        val details = listOfNotNull(failure.userMessage(this),
            status?.let { getString(R.string.ui_last_http_response, it.toString()) },
            if (failure.kind in setOf(ApiFailure.Kind.LIMITED, ApiFailure.Kind.TOO_SOON)) pauseMessage() else null,
            getString(R.string.web_connection_failed_scope)).joinToString("\n\n")
        val title = if (pendingWeb) R.string.web_pending_title else R.string.web_connection_failed
        val explanation = if (pendingWeb) getString(R.string.web_pending_body) + "\n\n" + failure.userMessage(this) else details
        message = getString(title) + "\n\n" + explanation
        leaveLogin()
        AlertDialog.Builder(this).setTitle(title).setMessage(explanation)
            .setNeutralButton(R.string.ui_request_information) { _, _ -> requestInfo() }
            .setPositiveButton(if (pendingWeb) R.string.ui_complete_connection else R.string.ui_close) { _, _ ->
                if (pendingWeb) startActivity(Intent(this, WebPendingConnectionActivity::class.java))
            }.show()
    }

    private fun leaveLogin(rendererGone: Boolean = false) {
        main.removeCallbacks(loginLoadTimeout)
        main.removeCallbacks(inspectPage)
        main.removeCallbacks(gateWake)
        loginChallenge?.close(); loginChallenge = null; challengeButton = null
        loginSecurityReset=false
        directNativeButton=null
        autoVerifyLogin = false; loginAuthenticating = false
        if(rendererGone) loginRelay?.rendererGone() else loginRelay?.close()
        loginRelay = null
        recordLoginPage { it.close() }; loginTrace = null
        try { browserProbe?.cancel(pageAvailable = !rendererGone) } catch (_: Exception) { message = getString(R.string.web_compare_journal_failed) }
        browserProbe = null; browserProof = null
        if (nativeComparisonStarted) {
            try { WebConnectionJournal(this).end(WebConnectionJournal.Step.NATIVE,WebConnectionJournal.Outcome.CANCELLED,null) } catch (_: Exception) { }
            nativeComparisonStarted = false
        }
        webAttempt?.cancel(); webAttempt = null
        ++generation; api.cancel()
        val browser = web
        web = null; loginStatus = null; loginHeading = null; loginProgress = null; completionButton = null; loginChecking = false
        browser?.apply {
            if(!rendererGone) {stopLoading(); loadUrl("about:blank"); clearHistory(); clearCache(true)}
            (parent as? ViewGroup)?.removeView(this); removeAllViews(); destroy()
        }
        CookieManager.getInstance().removeAllCookies { CookieManager.getInstance().flush() }
        WebStorage.getInstance().deleteAllData()
        if (BuildConfig.DEBUG) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        loading = false; render()
    }

    @Deprecated("Back handling retained for API 24-32 compatibility")
    @SuppressLint("GestureBackNavigation") // API 33+ uses the registered platform callback; this is the legacy path.
    @Suppress("DEPRECATION")
    override fun onBackPressed() { navigateBack() }

    private fun navigateBack() {
        if (web != null) leaveLogin()
        else if (peopleScreen && selectedPersonId != null) { selectedPersonId = null; message = null; render() }
        else if (peopleScreen) { peopleScreen = false; message = null; render() }
        else finish()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        if (::quickWatch.isInitialized) quickWatch.save(outState)
        outState.putString("pendingQuickTarget", pendingQuickTarget)
        outState.putBoolean("people", peopleScreen); outState.putString("person", selectedPersonId); outState.putInt("range", rangeDays)
        outState.putInt("visiblePeople", visiblePeople)
        outState.putString("peopleQuery",peopleQuery)
        outState.putString("pendingWatch", pendingWatchId); outState.putString("pendingOwner", pendingWatchOwner); outState.putInt("pendingMinutes", pendingWatchMinutes)
        outState.putString("pendingAutomationOwner", pendingAutomationOwner)
        pendingAutomationTargets?.let { outState.putStringArrayList("pendingAutomationTargets", ArrayList(it)) }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        loginSecurityReset=false
        loginChallenge?.close(); loginChallenge = null
        autoVerifyLogin = false; loginAuthenticating = false
        loginRelay?.close(); loginRelay = null
        recordLoginPage { it.close() }; loginTrace = null
        if (::preparation.isInitialized) preparation.close()
        if (::profileStatuses.isInitialized) profileStatuses.close()
        if (::quickWatch.isInitialized) quickWatch.close()
        if (::avatars.isInitialized) avatars.close()
        try { browserProbe?.cancel() } catch (_: Exception) { }
        browserProbe = null; browserProof = null
        webAttempt?.cancel(); webAttempt = null
        ++generation; main.removeCallbacksAndMessages(null); api.cancel()
        if (web != null) {
            CookieManager.getInstance().removeAllCookies { CookieManager.getInstance().flush() }
            WebStorage.getInstance().deleteAllData()
        }
        web?.apply { stopLoading(); (parent as? ViewGroup)?.removeView(this); destroy() }
        web = null
        worker.shutdownNow()
        super.onDestroy()
    }
    override fun onPause() {
        personSearch?.close();personSearch=null
        visible = false
        loginRelay?.cancel()
        main.removeCallbacks(inspectPage)
        if (::preparation.isInitialized) preparation.pause()
        if (webComparison && web != null && (loginChecking || browserProof != null)) {
            message = getString(R.string.web_compare_cancelled); leaveLogin()
        }
        if (::profileStatuses.isInitialized) profileStatuses.pause()
        if (::quickWatch.isInitialized) { quickWatch.pause(); if (::vault.isInitialized) syncMonitoring() }
        super.onPause()
    }
    override fun onStop() { if (::queuedRead.isInitialized) queuedRead.cancel(false); if (::avatars.isInitialized) avatars.cancelAll(); super.onStop() }
}
