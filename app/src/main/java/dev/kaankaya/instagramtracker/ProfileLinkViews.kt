package dev.kaankaya.instagramtracker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import okhttp3.Call

/** Local logos and external-browser actions. No site metadata or favicon is downloaded. */
object ProfileLinkViews {
    fun view(context: Context, username: String?, sites: List<ProfileSite>): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        fun open(url: String) {
            if (ProfileLinks.webUrl(url) == null) return
            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)) }
            catch (_: android.content.ActivityNotFoundException) { Toast.makeText(context, R.string.profile_link_unavailable, Toast.LENGTH_SHORT).show() }
        }
        fun link(label: String, url: String, brand: String, action: () -> Unit): android.widget.Button {
            return AppUi.button(context, label, false, action = action).apply {
                val drawable = context.getDrawable(ProfileBrandIcons.drawable(brand))!!.mutate().apply { setTint(AppUi.ink) }
                setCompoundDrawablesRelativeWithIntrinsicBounds(drawable, null, null, null)
                compoundDrawablePadding = AppUi.dp(context, 8)
                contentDescription = "$label · $url"
                if (android.os.Build.VERSION.SDK_INT >= 26) tooltipText = url
                setOnLongClickListener { Toast.makeText(context, url, Toast.LENGTH_LONG).show(); true }
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = AppUi.dp(context, 8) }
            }
        }
        val threadUrl = ProfileLinks.threads(username, sites)
        for (site in sites.filter { ProfileLinks.threadsName(it.url) == null }) {
            val (name, brand) = ProfileLinks.brand(site.url)
            addView(link(name, site.url, brand) { open(site.url) })
        }
        ProfileLinks.url(username)?.let { url -> addView(link(context.getString(R.string.ui_open_in_instagram), url, "instagram") { open(url) }) }
        if (threadUrl != null) {
            val notice = AppUi.text(context, "", 12f, AppUi.muted).apply { visibility = View.GONE; setPadding(0, AppUi.dp(context, 8), 0, 0) }
            var call: Call? = null; var task: Thread? = null
            var result: ThreadsProfile.Result? = null
            lateinit var check: android.widget.Button
            check = link(context.getString(R.string.profile_threads_check), threadUrl, "threads") {
                if (result?.status == "visible") open(threadUrl)
                else if (task == null) {
                    check.isEnabled = false; notice.visibility = View.VISIBLE; notice.setText(R.string.profile_threads_loading)
                    task = Thread({
                        var message: String? = null
                        try {
                            if ((context as? android.app.Activity)?.let(PreviewMode::enabled) == true) result = ThreadsProfile.Result("unknown")
                            else {
                                val saved = SessionVault(context, "account").read() ?: throw ApiFailure(ApiFailure.Kind.SESSION_EXPIRED)
                                val gate = RequestGate(context).forAccount(saved.session.userId)
                                gate.claim().use {
                                    if (gate.automationHeld() || gate.requiresConnection()) throw ApiFailure(ApiFailure.Kind.TOO_SOON)
                                    gate.begin()
                                    if (Thread.currentThread().isInterrupted) return@Thread
                                    result = ThreadsProfile.probe(threadUrl) { call = it; if (Thread.currentThread().isInterrupted) it.cancel() }
                                    if (result?.http == 429) gate.limited(result?.retryAfter, reason = "Threads bekleme yanıtı")
                                }
                            }
                        } catch (failure: Exception) { message = (failure as? ApiFailure)?.userMessage(context) ?: context.getString(R.string.profile_threads_unknown) }
                        check.post {
                            task = null
                            if (!check.isAttachedToWindow) return@post
                            check.isEnabled = true
                            when (result?.status) {
                                "visible" -> { check.setText(R.string.profile_threads_open); notice.setText(R.string.profile_threads_found); if (check.hasWindowFocus()) open(threadUrl) }
                                "not_found" -> notice.setText(R.string.profile_threads_missing)
                                else -> {
                                    notice.text = message ?: context.getString(R.string.profile_threads_unknown)
                                    // Login walls and blocked responses cannot establish absence.
                                    if (message == null && childCount > 0 && findViewWithTag<View>("threads-browser") == null)
                                        addView(link(context.getString(R.string.profile_browser_open), threadUrl, "threads") { open(threadUrl) }.apply { tag = "threads-browser" })
                                }
                            }
                        }
                    }, "TrackerThreadsCheck").apply { start() }
                }
            }
            check.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) = Unit
                override fun onViewDetachedFromWindow(view: View) { task?.interrupt(); call?.cancel() }
            })
            addView(check); addView(notice)
        }
    }
}
