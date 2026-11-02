package dev.kaankaya.instagramtracker

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** A list member is a temporary profile, never an implicitly registered/watchable person. */
class MemberProfileActivity : LocalizedActivity() {
    private data class ProfileRead(val profile: Profile, val fetched: Boolean, val startedAt: Long? = null)
    private data class Retained(val owner: String, val binding: String?, val member: FollowUser, val profile: Profile?)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var vault: SessionVault
    private lateinit var gate: RequestGate
    private lateinit var api: InstagramApi
    private lateinit var avatars: AvatarImages
    private lateinit var queuedRead: ForegroundReadQueue
    private lateinit var reference: Session
    private lateinit var member: FollowUser
    private var profile: Profile? = null
    private var account: SavedAccount? = null
    private var preview = false
    private var loading = false
    private var attempted = false
    private var generation = 0
    private var pending: Future<*>? = null
    private var message: String? = null
    private var pendingWatch = false
    private var resumedOnce = false
    private fun dp(value: Int) = AppUi.dp(this, value)
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun LinearLayout.gap(value: Int) { addView(View(this@MemberProfileActivity), LinearLayout.LayoutParams(1, dp(value))) }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state); AppUi.systemBars(this)
        if (!BuildConfig.DEBUG) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        preview = PreviewMode.enabled(this)
        vault = SessionVault(this, if (preview) "previewaccount" else "account")
        gate = RequestGate(this, if (preview) "preview_gate" else "request_gate")
        api = PreviewMode.api(gate, preview)
        avatars = AvatarImages(this, PreviewMode.avatarFetcher(preview), if (preview) "previewavatars" else "avatars")
        queuedRead = ForegroundReadQueue(this, gate) { reason -> message = reason; render() }
        attempted = state?.getBoolean("attempted") ?: false
        pendingWatch = state?.getBoolean("pendingWatch") ?: false
        try {
            val saved = vault.read() ?: throw ApiFailure(ApiFailure.Kind.SESSION_EXPIRED)
            if (intent.getStringExtra("viewer_id") != saved.session.userId) throw ApiFailure(ApiFailure.Kind.WRONG_ACCOUNT)
            reference = saved.session; account = saved
            val id = InstagramIdentity.id(intent.getStringExtra("member_id"))
            val retained = lastNonConfigurationInstance as? Retained
            val target = InstagramIdentity.id(intent.getStringExtra("list_target_id"))
            val direction = FollowDirection.valueOf(intent.getStringExtra("list_direction") ?: "")
            if (target != saved.session.userId && saved.people.none { it.latest.userId == target }) throw ApiFailure(ApiFailure.Kind.NOT_FOUND)
            val row = FollowListVault(this, if (preview) "previewlists" else "followlists").read(reference, target)
                ?.run(direction)?.users?.firstOrNull { it.id == id }
            member = row ?: retained?.takeIf { it.owner == reference.userId && it.binding == reference.connectionBinding && it.member.id == id }?.member
                ?: throw ApiFailure(ApiFailure.Kind.NOT_FOUND)
            profile = ViewedProfiles.latest(saved, id)
            retained?.takeIf { it.owner == reference.userId && it.binding == reference.connectionBinding && it.member.id == id }
                ?.profile?.let { if (profile == null || it.observedAt > profile!!.observedAt) profile = it }
        } catch (failure: Exception) {
            setContentView(AppUi.notice(this, getString(R.string.ui_could_not_open_profile), (failure as? ApiFailure)?.userMessage(this@MemberProfileActivity) ?: getString(R.string.list_storage_error), true))
            return
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) onBackInvokedDispatcher.registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) { finish() }
        render()
        if (!attempted && (profile == null || PeoplePolicy.needsRefresh(profile!!, System.currentTimeMillis()))) {
            attempted = true
            if (readAllowed()) readProfile()
            else if (queuedRead.canWait() && queuedRead.enqueue { readProfile() }) render()
            else { message = waitReason(); render() }
        }
    }
    private fun readAllowed() = !loading && !gate.automationHeld() && !gate.requiresConnection() &&
        gate.nextAllowedAt() <= System.currentTimeMillis() && (profile == null || PeoplePolicy.needsRefresh(profile!!, System.currentTimeMillis()))
    private fun waitReason(): String = when {
        gate.requiresConnection() -> ApiFailure(ApiFailure.Kind.SESSION_EXPIRED).userMessage(this@MemberProfileActivity)
        gate.automationHeld() -> getString(R.string.ui_an_account_connection_is_in_progress_the)
        gate.blockedUntil() > System.currentTimeMillis() -> ApiFailure(ApiFailure.Kind.LIMITED).userMessage(this@MemberProfileActivity)
        else -> getString(R.string.ui_waiting_for_the_current_request_interval_before)
    }
    private val wake = Runnable { render() }
    private fun requestProfile() {
        if (loading || queuedRead.pending) return
        if (queuedRead.canWait() && queuedRead.enqueue { readProfile() }) { render(); return }
        if (readAllowed()) readProfile() else { message = waitReason(); render() }
    }
    private fun render() {
        if (isDestroyed || isFinishing || !::member.isInitialized) return
        val page = column().apply { setPadding(dp(22), dp(16), dp(22), dp(24)); setBackgroundColor(AppUi.background) }
        val scroll = ScrollView(this).apply {
            isFillViewport = true; addView(page)
            setOnApplyWindowInsetsListener { view, insets ->
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                insets
            }
        }
        setContentView(scroll); scroll.requestApplyInsets()
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(AppUi.iconButton(this, "back", getString(R.string.ui_back_to_list)) { finish() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(AppUi.heading(this, getString(R.string.ui_profile), 24f).apply { setPadding(dp(12), 0, 0, 0) }); page.addView(header); page.gap(24)
        if (preview) { page.addView(AppUi.pill(this, getString(R.string.ui_demo_sample_data))); page.gap(16) }
        val shown = profile
        val username = shown?.username ?: member.username
        val picture = shown?.details?.pictureUrl?.value ?: member.picture.takeIf { shown == null }
        page.addView(ProfileAvatarView(this, 76).apply { bind(avatars, reference.userId, member.id, username ?: member.name ?: "?",
            picture.takeUnless { shown?.details?.anonymousPicture == true }, shown?.details?.pictureId?.value) }); page.gap(16)
        page.addView(AppUi.heading(this, username?.let { "@$it" } ?: getString(R.string.list_username_unknown), 28f)); page.gap(8)
        member.name?.takeIf { it.isNotBlank() }?.let { page.addView(AppUi.text(this, it, 15f, AppUi.muted)); page.gap(12) }
        message?.let { page.addView(AppUi.notice(this, getString(R.string.ui_profile_status), it, true)); page.gap(16) }
        if (loading) { page.addView(AppUi.text(this, getString(R.string.ui_loading_profile_information), 14f, AppUi.violet)); page.gap(16) }
        val details = if (shown != null) ProfileSummary.view(this, shown) else AppUi.card(this).apply {
            addView(AppUi.text(this@MemberProfileActivity, getString(R.string.ui_profile_information_has_not_been_read_yet), 15f, AppUi.muted))
        }
        page.addView(details); page.gap(18)
        if (queuedRead.pending) { page.addView(queuedRead.view(this)); page.gap(12) }
        else {
            val needsRead = shown == null || PeoplePolicy.needsRefresh(shown, System.currentTimeMillis())
            if (needsRead) {
                page.addView(AppUi.button(this, if (shown == null) getString(R.string.ui_load_profile) else getString(R.string.ui_refresh_profile), false,
                    !loading && (readAllowed() || queuedRead.canWait())) { requestProfile() }); page.gap(12)
            }
        }
        val registered = account?.let { it.profile.userId == member.id || it.people.any { person -> person.latest.userId == member.id } } == true
        if (!registered) {
            page.addView(AppUi.button(this, getString(R.string.ui_watch_for_changes), true, shown != null && !loading) { requestWatch() }); page.gap(12)
            page.addView(AppUi.text(this, getString(R.string.ui_enabling_monitoring_saves_this_person_and_notifies), 12f, AppUi.muted)); page.gap(12)
            page.addView(AppUi.button(this, getString(R.string.ui_only_add_to_people), false, shown != null && !loading) { addPerson() }); page.gap(12)
        } else {
            val person = account?.people?.firstOrNull { it.latest.userId == member.id }
            if (person != null) {
                page.addView(AppUi.button(this, if (person.watching) getString(R.string.ui_monitoring_on) else getString(R.string.ui_watch_for_changes), !person.watching, !loading) {
                    if (person.watching) stopWatch() else requestWatch()
                }); page.gap(12)
            }
            page.addView(AppUi.button(this, getString(R.string.ui_view_their_lists), false, !loading) {
                startActivity(Intent(this, FollowListActivity::class.java).putExtra("target_id", member.id).putExtra("offline_preview", preview))
            }); page.gap(12)
        }
        if (shown == null) page.addView(ProfileLinkViews.view(this, username, emptyList()))
        main.removeCallbacks(wake)
        val next = maxOf(gate.nextAllowedAt(), shown?.observedAt?.plus(PeoplePolicy.FRESH_MS) ?: 0)
        if (next > System.currentTimeMillis()) main.postDelayed(wake, next - System.currentTimeMillis())
    }
    private fun readProfile() {
        if (!readAllowed()) return
        attempted = true; loading = true; message = null; render(); val ticket = ++generation
        pending = worker.submit {
            var startedAt: Long? = null
            val result = try {
                val saved = vault.read()?.takeIf { it.session == reference } ?: throw ApiFailure(ApiFailure.Kind.CANCELLED)
                val cached = ViewedProfiles.latest(saved, member.id)
                Result.success(if (cached != null && !PeoplePolicy.needsRefresh(cached, System.currentTimeMillis())) ProfileRead(cached, false)
                    else { startedAt = System.currentTimeMillis(); ProfileRead(api.readId(reference, member.id), true, startedAt) })
            } catch (failure: ApiFailure) {
                vault.recordFailure(reference, member.id, startedAt, failure)
                Result.failure(failure)
            }
            catch (_: Exception) { Result.failure(ApiFailure(ApiFailure.Kind.INVALID_RESPONSE)) }
            main.post {
                if (isDestroyed || isFinishing || ticket != generation) return@post
                loading = false
                result.onSuccess { read ->
                    try {
                        vault.inspect { current ->
                            if (current?.session != reference) throw ApiFailure(ApiFailure.Kind.CANCELLED)
                            val fresh = read.profile
                            profile = fresh
                            if (!read.fetched) account = current
                            else if (current.profile.userId == member.id && fresh.observedAt > current.profile.observedAt)
                                account = Observations.own(vault, reference, fresh, read.startedAt).account
                            else if (current.people.any { it.latest.userId == member.id }) {
                                val commit = Observations.person(vault, reference, fresh, existingRequired = true, startedAt = read.startedAt)
                                account = commit.account
                                commit.change?.let { change -> Observations.deliver(vault, reference, change, ChangeNotifications(this, preview)::allowed, ChangeNotifications(this, preview)::post) }
                            }
                            if (read.fetched) ViewedProfiles.put(reference, profile!!)
                        }
                    } catch (_: Exception) { message = getString(R.string.ui_could_not_save_the_profile_read_existing) }
                }.onFailure { message = (it as ApiFailure).userMessage(this@MemberProfileActivity) }
                render()
            }
        }
    }
    private fun addPerson() {
        val shown = profile ?: return
        try {
            account = vault.update(reference) { current ->
                if (current.profile.userId == shown.userId || current.people.any { it.latest.userId == shown.userId }) current
                else current.copy(people = PeoplePolicy.record(current.people, shown))
            }
            message = getString(R.string.ui_added_to_your_people_automatic_monitoring_is)
        } catch (failure: ApiFailure) { message = failure.userMessage(this@MemberProfileActivity) }
        catch (_: Exception) { message = ApiFailure(ApiFailure.Kind.STORAGE).userMessage(this@MemberProfileActivity) }
        render()
    }
    private fun requestWatch() {
        if (profile == null) return
        ChangeNotifications(this, preview).ensureChannel()
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendingWatch = true; requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1403)
        } else enableWatch()
    }
    private fun enableWatch() {
        pendingWatch = false
        if (!ChangeNotifications(this, preview).allowed()) { message = getString(R.string.ui_allow_notifications_to_enable_monitoring); render(); return }
        val shown = profile ?: return
        try {
            account = vault.update(reference) { current ->
                val people = if (current.people.any { it.latest.userId == shown.userId }) current.people else PeoplePolicy.record(current.people, shown)
                current.copy(people = people.map { if (it.latest.userId == shown.userId) WatchPreference.set(it, it.intervalMinutes, true) else it })
            }
            if (preview) PreviewMode.sync(this, account, gate) else MonitorScheduler.sync(this, account, gate)
            message = getString(R.string.ui_monitoring_enabled_you_will_be_notified_about)
        } catch (failure: ApiFailure) { message = failure.userMessage(this@MemberProfileActivity) }
        catch (_: Exception) { message = ApiFailure(ApiFailure.Kind.STORAGE).userMessage(this@MemberProfileActivity) }
        render()
    }
    private fun stopWatch() {
        android.app.AlertDialog.Builder(this).setTitle(getString(R.string.ui_monitoring_on)).setMessage(getString(R.string.ui_this_person_s_profile_is_being_checked))
            .setNegativeButton(getString(R.string.ui_close), null).setPositiveButton(getString(R.string.ui_stop_monitoring)) { _, _ ->
                try {
                    account = vault.update(reference) { current -> current.copy(people = current.people.map {
                        if (it.latest.userId == member.id) WatchPreference.set(it, it.intervalMinutes, false) else it
                    }) }
                    ChangeNotifications(this, preview).cancel(reference.userId, member.id)
                    if (preview) PreviewMode.sync(this, account, gate) else MonitorScheduler.sync(this, account, gate)
                } catch (_: Exception) { message = getString(R.string.ui_could_not_save_the_monitoring_setting) }
                render()
            }.show()
    }
    @Deprecated("Platform permission callback")
    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code == 1403 && pendingWatch) enableWatch()
    }
    override fun onResume() {
        super.onResume(); if (recreatingForInterface || !::member.isInitialized) return
        if (!resumedOnce) { resumedOnce = true; return }
        try { account = vault.read(); if (account?.session != reference) { finish(); return } }
        catch (_: Exception) { finish(); return }
        ViewedProfiles.latest(account!!, member.id)?.let { if (profile == null || it.observedAt > profile!!.observedAt) profile = it }
        render()
    }
    override fun onPause() { queuedRead.cancel(false); generation++; api.cancel(); pending?.cancel(true); loading = false; main.removeCallbacks(wake); avatars.cancelAll(); super.onPause() }
    override fun onDestroy() { avatars.close(); main.removeCallbacksAndMessages(null); worker.shutdownNow(); super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("attempted", attempted); outState.putBoolean("pendingWatch", pendingWatch); super.onSaveInstanceState(outState)
    }
    override fun onRetainNonConfigurationInstance(): Any? = if (::member.isInitialized) Retained(reference.userId, reference.connectionBinding, member, profile) else null
}
