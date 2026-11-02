package dev.kaankaya.instagramtracker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.LinearLayout
import android.widget.Toast

/** Local logos and external-browser actions. No site metadata or favicon is downloaded. */
object ProfileLinkViews {
    fun view(context: Context, username: String?, sites: List<ProfileSite>): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        fun open(url: String) {
            if (ProfileLinks.webUrl(url) == null) return
            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)) }
            catch (_: android.content.ActivityNotFoundException) { Toast.makeText(context, R.string.profile_link_unavailable, Toast.LENGTH_SHORT).show() }
        }
        fun link(label: String, url: String, brand: String): android.widget.Button {
            return AppUi.button(context, label, false, action = { open(url) }).apply {
                val drawable = context.getDrawable(ProfileBrandIcons.drawable(brand))!!.mutate().apply { setTint(AppUi.ink) }
                setCompoundDrawablesRelativeWithIntrinsicBounds(drawable, null, null, null)
                compoundDrawablePadding = AppUi.dp(context, 8)
                contentDescription = "$label · $url"
                if (android.os.Build.VERSION.SDK_INT >= 26) tooltipText = url
                setOnLongClickListener { Toast.makeText(context, url, Toast.LENGTH_LONG).show(); true }
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = AppUi.dp(context, 8) }
            }
        }
        val threadUrl = ProfileLinks.threads(sites)
        for (site in sites.filter { it.url != threadUrl }) {
            val (name, brand) = ProfileLinks.brand(site.url)
            addView(link(name, site.url, brand))
        }
        ProfileLinks.url(username)?.let { url -> addView(link(context.getString(R.string.ui_open_in_instagram), url, "instagram")) }
        if (threadUrl != null) addView(link(context.getString(R.string.profile_threads_open), threadUrl, "threads"))
    }
}
