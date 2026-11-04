package dev.kaankaya.instagramtracker

import android.content.Context
import android.widget.LinearLayout

/** Shared profile presentation for registered people and temporarily viewed list members. */
object ProfileSummary {
    fun view(context: Context, profile: Profile): LinearLayout = AppUi.card(context).apply {
        val counts = LinearLayout(context)
        for ((title, count, tint) in listOf(Triple(context.getString(R.string.ui_followers), profile.followers, AppUi.violetSoft), Triple(context.getString(R.string.ui_following), profile.following, AppUi.peach)) + listOfNotNull(profile.posts?.let { Triple(context.getString(R.string.parity_posts),it,AppUi.violetSoft) })) {
            counts.addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; background = AppUi.shape(context, tint, 18)
                contentDescription = "$title · $count"
                setPadding(AppUi.dp(context, 14), AppUi.dp(context, 16), AppUi.dp(context, 14), AppUi.dp(context, 16))
                addView(AppUi.numeric(AppUi.text(context, java.text.NumberFormat.getIntegerInstance(AppLanguage.locale(context)).format(count), 23f, AppUi.ink, true)).apply {
                    maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                    if (android.os.Build.VERSION.SDK_INT >= 26) setAutoSizeTextTypeUniformWithConfiguration(14, 23, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
                })
                addView(AppUi.text(context, title, 12f, AppUi.muted))
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = AppUi.dp(context, 6) })
        }
        addView(counts)
        profile.details.biography.value?.takeIf { it.isNotBlank() }?.let { biography ->
            addView(AppUi.text(context, biography, 15f).apply { setPadding(0, AppUi.dp(context, 16), 0, 0) })
        }
        addView(AppUi.text(context, context.getString(R.string.ui_last_record, java.text.SimpleDateFormat("d MMM · HH:mm", AppLanguage.locale(context)).format(java.util.Date(profile.observedAt))), 11f, AppUi.muted)
            .apply { setPadding(0, AppUi.dp(context, 12), 0, 0) })
        addView(ProfileLinkViews.view(context, profile.username, profile.details.links))
    }
}
