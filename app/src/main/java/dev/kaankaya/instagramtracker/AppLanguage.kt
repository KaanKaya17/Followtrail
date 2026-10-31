package dev.kaankaya.instagramtracker

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import java.util.Locale

/** Owns only presentation language. Account data, request gates and monitoring settings are untouched. */
object AppLanguage {
    private const val PREFERENCES = "interface_language"
    private const val KEY = "language"
    val choices = listOf("tr", "en", "es", "pt-BR")

    private fun supported(tag: String?): String? = choices.firstOrNull { it.equals(tag, ignoreCase = true) }

    fun selected(context: Context): String {
        if (Build.VERSION.SDK_INT >= 33) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (!locales.isEmpty) return supported(locales[0].toLanguageTag()) ?: "tr"
        }
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(KEY, "tr")
            ?.let(::supported) ?: "tr"
    }

    fun wrap(context: Context): Context {
        val locale = Locale.forLanguageTag(selected(context))
        val configuration = Configuration(context.resources.configuration)
        // Each supported locale has a complete resource pack. Do not silently mix languages.
        configuration.setLocales(LocaleList(locale))
        return context.createConfigurationContext(configuration)
    }

    fun set(context: Context, language: String) {
        val selected = requireNotNull(supported(language))
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(selected)
            // Android owns persistence on 13+. Remove an older-OS preference after handing it off.
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().remove(KEY).apply()
        } else {
            check(context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putString(KEY, selected).commit())
        }
    }

    fun migrate(context: Context) {
        if (Build.VERSION.SDK_INT < 33) return
        val saved = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(KEY, null) ?: return
        val manager = context.getSystemService(LocaleManager::class.java)
        supported(saved)?.let { selected -> if (manager.applicationLocales.isEmpty) manager.applicationLocales = LocaleList.forLanguageTags(selected) }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    fun locale(context: Context): Locale = context.resources.configuration.locales[0]
}

/** Every app-owned screen receives the same localized resources, including Android 7–12. */
abstract class LocalizedActivity : Activity() {
    private var createdLanguage = "tr"
    private var createdAppearance = "light"
    private var createdAppearanceChoice = "system"
    protected var interfaceChangedOnRecreation = false
        private set
    protected var recreatingForInterface = false
        private set

    override fun attachBaseContext(newBase: Context) {
        AppLanguage.migrate(newBase)
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        createdAppearance = AppAppearance.apply(this)
        createdAppearanceChoice = AppAppearance.selected(this)
        setTheme(AppAppearance.style(createdAppearance))
        createdLanguage = AppLanguage.selected(this)
        interfaceChangedOnRecreation = savedInstanceState?.getString("interface_language_at_save")
            ?.let { it != createdLanguage } == true || savedInstanceState?.getString("interface_theme_at_save")
            ?.let { it != createdAppearance } == true
            || savedInstanceState?.getString("interface_theme_choice_at_save")?.let { it != createdAppearanceChoice } == true
        super.onCreate(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("interface_language_at_save", createdLanguage)
        outState.putString("interface_theme_at_save", createdAppearance)
        outState.putString("interface_theme_choice_at_save", createdAppearanceChoice)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (AppLanguage.locale(this).language != AppLanguage.selected(this) || createdAppearance != AppAppearance.resolved(this) || createdAppearanceChoice != AppAppearance.selected(this)) {
            recreatingForInterface = true
            recreate()
        }
    }
}
