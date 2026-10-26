package dev.kaankaya.instagramtracker

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors

/** Presentation of existing local records. No scheduler or network operation belongs to this owner. */
class ProfileStatusCards(private val context: Context, private val vault: SessionVault,
    private val lists: FollowListVault, private val gate: RequestGate, private val preview: Boolean,
    private val quick: () -> Pair<String?, Boolean>, private val timeline: (String) -> Unit,
    private val openLists: (Profile) -> Unit) {
    private data class Snapshot(val account: SavedAccount, val archive: ListArchive?, val events: List<CheckEvent>?)
    private data class Binding(val session: Session, val id: String, val label: TextView)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val bindings = mutableListOf<Binding>()
    private var snapshot: Snapshot? = null
    private var visible = false
    private var generation = 0
    private var reading = false
    fun clear() { bindings.clear(); generation++ }
    fun view(session: Session, id: String): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(AppUi.dp(context, 12), AppUi.dp(context, 10), AppUi.dp(context, 12), AppUi.dp(context, 10))
            background = AppUi.touch(context, AppUi.shape(context, AppUi.violetSoft, 16))
            isFocusable = true
            contentDescription = context.getString(R.string.profile_status_open)
            tag = "profile-status:$id"
        }
        val label = AppUi.text(context, context.getString(R.string.profile_status_loading), 12f, AppUi.ink).apply {
            setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, AppUi.Icon(context, "chevron", AppUi.violet, 16), null)
            compoundDrawablePadding = AppUi.dp(context, 8)
        }
        card.addView(label)
        val binding = Binding(session, id, label); bindings += binding
        snapshot?.takeIf { it.account.session == session }?.let { update(binding, it) }
        card.setOnClickListener { details(binding) }
        requestSoon()
        return card
    }
    fun resume() { visible = true; requestSoon() }
    fun pause() { visible = false; generation++; main.removeCallbacks(refresh) }
    private fun requestSoon() {
        main.removeCallbacks(refresh)
        if (visible) main.post(refresh)
    }
    private val refresh = object : Runnable {
        override fun run() {
            if (!visible || bindings.isEmpty()) return
            if (reading) { main.postDelayed(this, 1000); return }
            val session = bindings.first().session; val ticket = generation
            reading = true
            worker.execute {
                val result = try {
                    vault.read()?.takeIf { it.session == session }?.let { saved ->
                        Snapshot(saved, try { lists.archive(session) } catch (_: Exception) { null },
                            try { vault.journal.read(session) } catch (_: Exception) { null })
                    }
                } catch (_: Exception) { null }
                main.post {
                    reading = false
                    if (!visible || ticket != generation) { requestSoon(); return@post }
                    snapshot = result
                    bindings.forEach { binding ->
                        if (result == null) binding.label.text = context.getString(R.string.profile_status_unavailable)
                        else update(binding, result)
                    }
                    main.postDelayed(this, 30_000)
                }
            }
        }
    }
    private fun profile(saved: SavedAccount, id: String) = if (saved.profile.userId == id) saved.profile
        else saved.people.firstOrNull { it.latest.userId == id }?.latest
    private fun conditions() = WatchConditions(ChangeNotifications(context, preview).allowed(), AppUi.offline(context) == true,
        gate.requiresConnection(), gate.automationHeld(), gate.blockedUntil(), gate.manualOnly(), gate.automaticNextAllowedAt())
    private fun status(saved: SavedAccount, id: String, now: Long): WatchStatus {
        val person = saved.people.firstOrNull { it.latest.userId == id } ?: TrackedPerson(listOf(saved.profile),
            watching = saved.automation.enabled && id in saved.automation.targets,
            nextCheckAt = maxOf(saved.automation.ownNextCheckAt, saved.profile.observedAt + ListAutomationPolicy.OWN_PROFILE_MS))
        return WatchStatusPolicy.status(person.copy(watching = person.watching ||
            (saved.automation.enabled && id in saved.automation.targets)), saved.people, conditions(), now)
    }
    private fun headline(value: Snapshot, id: String, now: Long): String {
        val saved = value.account
        val watching = saved.people.firstOrNull { it.latest.userId == id }?.watching == true ||
            (saved.automation.enabled && id in saved.automation.targets)
        val state = status(saved, id, now)
        val reason = when {
            !watching -> R.string.profile_status_off
            state.reason == WatchReason.NOTIFICATIONS_OFF -> R.string.profile_status_notifications
            gate.restrictionPending() -> R.string.profile_status_limited
            state.reason == WatchReason.CONNECTION_REQUIRED -> R.string.profile_status_connection
            state.reason == WatchReason.CONNECTION_IN_PROGRESS -> R.string.profile_status_link
            state.reason == WatchReason.MANUAL_ONLY -> R.string.profile_status_paused
            state.reason == WatchReason.OFFLINE -> R.string.profile_status_offline
            saved.people.firstOrNull { it.latest.userId == id }?.watching == false &&
                saved.automation.enabled && id in saved.automation.targets -> R.string.profile_status_lists_only
            quick().first == id -> if (quick().second) R.string.profile_status_checking else R.string.profile_status_quick
            quick().first != null -> R.string.profile_status_other_quick
            else -> null
        }
        if (reason != null) return context.getString(reason)
        val latest = value.events?.filter { it.target == id }?.maxByOrNull { it.analyzedAt }
        if (latest?.kind == CheckKind.FAILURE) return context.getString(R.string.profile_status_failed)
        val lastProfile = value.events?.filter { it.target == id && it.kind == CheckKind.PROFILE }
            ?.maxByOrNull { it.observedAt ?: 0 }
        val current = profile(saved, id)
        if (lastProfile?.observedAt == current?.observedAt && lastProfile?.previousAt != null &&
            lastProfile.followersDelta == 0L && lastProfile.followingDelta == 0L && lastProfile.fields.isEmpty())
            return context.getString(R.string.profile_status_unchanged)
        return context.getString(if (state.eligibleAt != null && state.eligibleAt <= now)
            R.string.profile_status_due else R.string.profile_status_scheduled)
    }
    private fun lastCheck(at: Long, now: Long): String {
        if (at > now) return context.getString(R.string.profile_status_last, ObservationTimes.format(context, at))
        val minutes = (now - at) / 60_000
        return if (minutes == 0L) context.getString(R.string.profile_status_just_now)
        else if (minutes < 60) context.resources.getQuantityString(R.plurals.profile_status_minutes, minutes.toInt(), minutes.toInt())
        else context.getString(R.string.profile_status_last, ObservationTimes.format(context, at))
    }
    private fun listLine(value: Snapshot, id: String, now: Long): String? {
        val enabled = value.account.automation.enabled && id in value.account.automation.targets
        if (!enabled) return null
        val archive = value.archive ?: return context.getString(R.string.profile_status_lists_unavailable)
        val target = archive.targets.firstOrNull { it.target == id }
        val runs = FollowDirection.entries.map { target?.snapshot?.run(it) }
        val resource = when {
            FollowDirection.entries.any { target?.scan(it)?.failure != null } -> R.string.profile_status_lists_failed
            runs.all { it?.coverage() == FollowCoverage.END_OBSERVED } -> {
                val p = profile(value.account, id)!!
                if (target != null && FollowDirection.entries.any {
                    AutomaticQueue.page(target, it, now, basis = ListBasis.of(p, it)) != null
                }) R.string.profile_status_lists_due else R.string.profile_status_lists_observed
            }
            runs.any { it != null && it.coverage() !in setOf(FollowCoverage.END_OBSERVED, FollowCoverage.MORE) } -> R.string.profile_status_lists_limited
            runs.any { it?.coverage() == FollowCoverage.MORE } -> R.string.profile_status_lists_pending
            else -> R.string.profile_status_lists_initial
        }
        return context.getString(resource)
    }
    private fun update(binding: Binding, value: Snapshot) {
        if (binding.session != value.account.session) return
        val p = profile(value.account, binding.id) ?: return
        val now = System.currentTimeMillis()
        binding.label.text = listOfNotNull(lastCheck(p.observedAt, now), headline(value, p.userId, now),
            listLine(value, p.userId, now)).joinToString("\n")
        (binding.label.parent as? View)?.contentDescription = binding.label.text.toString() + ". " + context.getString(R.string.profile_status_open)
    }
    private fun details(binding: Binding) {
        val value = snapshot?.takeIf { it.account.session == binding.session } ?: return
        val p = profile(value.account, binding.id) ?: return
        val now = System.currentTimeMillis(); val state = status(value.account, p.userId, now)
        val body = mutableListOf(headline(value, p.userId, now), context.getString(R.string.watch_last_success,
            ObservationTimes.format(context, p.observedAt)))
        when (state.reason) {
            WatchReason.NOTIFICATIONS_OFF -> body += context.getString(R.string.watch_reason_notifications)
            WatchReason.LIMITED -> body += context.getString(R.string.watch_reason_limited, ObservationTimes.format(context, gate.blockedUntil()))
            WatchReason.CONNECTION_REQUIRED -> body += context.getString(R.string.watch_reason_connection)
            WatchReason.CONNECTION_IN_PROGRESS -> body += context.getString(R.string.watch_reason_link)
            WatchReason.MANUAL_ONLY -> body += context.getString(R.string.watch_reason_manual)
            WatchReason.OFFLINE -> body += context.getString(R.string.watch_reason_offline)
            else -> Unit
        }
        if (quick().first == p.userId) body += context.getString(R.string.quick_hint)
        else state.eligibleAt?.let { body += context.getString(R.string.watch_target_at, ObservationTimes.format(context, it)) }
        val target = value.archive?.targets?.firstOrNull { it.target == p.userId }
        for (direction in FollowDirection.entries) {
            val label = context.getString(if (direction == FollowDirection.FOLLOWERS) R.string.list_followers else R.string.list_following)
            val run = target?.snapshot?.run(direction)
            body += if (run == null) context.getString(R.string.profile_status_list_missing, label)
            else context.getString(R.string.profile_status_list_detail, label, java.text.NumberFormat.getIntegerInstance(AppLanguage.locale(context)).format(run.users.size),
                ObservationTimes.format(context, run.latest.observedAt), context.getString(when (run.coverage()) {
                    FollowCoverage.MORE -> R.string.list_more; FollowCoverage.END_OBSERVED -> R.string.list_end
                    FollowCoverage.LIMITED -> R.string.list_limited; FollowCoverage.UNKNOWN -> R.string.list_unknown
                    FollowCoverage.STALLED -> R.string.list_stalled
                }))
        }
        value.events?.filter { it.target == p.userId }?.maxByOrNull { it.analyzedAt }?.takeIf { it.kind == CheckKind.FAILURE }?.let {
            body += context.getString(R.string.profile_status_last_failure, ObservationTimes.format(context, it.analyzedAt),
                it.failure?.let { kind -> ApiFailure(kind).userMessage(context) }.orEmpty())
        }
        if (value.archive == null) body += context.getString(R.string.profile_status_lists_unavailable)
        body += context.getString(R.string.profile_status_scope)
        AlertDialog.Builder(context).setTitle(context.getString(R.string.profile_status_title, p.username)).setMessage(body.joinToString("\n\n"))
            .setPositiveButton(R.string.list_close, null)
            .setNeutralButton(R.string.timeline_title) { _, _ -> timeline(p.userId) }
            .setNegativeButton(R.string.list_open) { _, _ -> openLists(p) }.show()
    }
    fun close() { pause(); bindings.clear(); snapshot = null; main.removeCallbacksAndMessages(null); worker.shutdownNow() }
}
