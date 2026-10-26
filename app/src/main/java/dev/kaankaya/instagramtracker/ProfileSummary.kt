package dev.kaankaya.instagramtracker

import android.content.Context
import android.widget.LinearLayout

/** Shared profile presentation for registered people and temporarily viewed list members. */
object ProfileSummary {
    fun view(context: Context, profile: Profile): LinearLayout = AppUi.card(context).apply {
        val counts = LinearLayout(context)
        for ((title, count, tint) in listOf(Triple("Takipçi", profile.followers, AppUi.violetSoft), Triple("Takip edilen", profile.following, AppUi.peach))) {
            counts.addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; background = AppUi.shape(context, tint, 18)
                contentDescription = "$title · $count"
                setPadding(AppUi.dp(context, 14), AppUi.dp(context, 16), AppUi.dp(context, 14), AppUi.dp(context, 16))
                addView(AppUi.text(context, java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag("tr-TR")).format(count), 26f, AppUi.ink, true))
                addView(AppUi.text(context, title, 12f, AppUi.muted))
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = AppUi.dp(context, 6) })
        }
        addView(counts)
        profile.posts?.let { addView(AppUi.text(context, "Gönderi · $it", 12f, AppUi.muted).apply { setPadding(0, AppUi.dp(context, 12), 0, 0) }) }
        val bio = profile.details.biography
        val biography = when (bio.state) {
            ProfileTextState.VALUE -> bio.value!!.ifEmpty { context.getString(R.string.profile_empty_bio) }
            ProfileTextState.MISSING -> context.getString(R.string.profile_field_missing)
            ProfileTextState.NULL -> context.getString(R.string.profile_field_null)
            ProfileTextState.UNAVAILABLE -> context.getString(R.string.profile_field_unavailable)
        }
        addView(AppUi.text(context, biography, 15f)
            .apply { setPadding(0, AppUi.dp(context, 16), 0, 0) })
        addView(AppUi.text(context, "Son kayıt · " + java.text.SimpleDateFormat("d MMM · HH:mm", java.util.Locale.forLanguageTag("tr-TR")).format(java.util.Date(profile.observedAt)), 11f, AppUi.muted)
            .apply { setPadding(0, AppUi.dp(context, 12), 0, 0) })
    }
}
