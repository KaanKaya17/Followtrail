package dev.kaankaya.instagramtracker

import android.content.Context
import android.content.res.Configuration

/** Local display preference. Never touches account data or request scheduling. */
object AppAppearance {
    val choices = listOf("system", "light", "dark", "oled")
    private const val PREFERENCES = "interface_appearance"
    fun selected(context: Context): String = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        .getString("theme", "system")?.takeIf { it in choices } ?: "system"
    fun resolved(context: Context): String = when (val mode = selected(context)) {
        "system" -> if (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) "dark" else "light"
        else -> mode
    }
    fun apply(context: Context): String = resolved(context).also(UiPalette::select)
    fun style(mode: String): Int = when (mode) {
        "dark" -> R.style.AppThemeDark
        "oled" -> R.style.AppThemeOled
        else -> R.style.AppTheme
    }
    fun set(context: Context, mode: String) {
        require(mode in choices)
        check(context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putString("theme", mode).commit())
    }
}
