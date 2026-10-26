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
    val choices = listOf("tr", "en")

    fun selected(context: Context): String {
        if (Build.VERSION.SDK_INT >= 33) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (!locales.isEmpty) return locales[0].language.takeIf { it in choices } ?: "tr"
        }
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(KEY, "tr")
            ?.takeIf { it in choices } ?: "tr"
    }

    fun wrap(context: Context): Context {
        val locale = Locale.forLanguageTag(selected(context))
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocales(LocaleList(locale))
        return context.createConfigurationContext(configuration)
    }

    fun set(context: Context, language: String) {
        require(language in choices)
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(language)
            // Android owns persistence on 13+. Remove an older-OS preference after handing it off.
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().remove(KEY).apply()
        } else {
            check(context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putString(KEY, language).commit())
        }
    }

    fun migrate(context: Context) {
        if (Build.VERSION.SDK_INT < 33) return
        val saved = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(KEY, null) ?: return
        val manager = context.getSystemService(LocaleManager::class.java)
        if (manager.applicationLocales.isEmpty && saved in choices) manager.applicationLocales = LocaleList.forLanguageTags(saved)
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    fun locale(context: Context): Locale = context.resources.configuration.locales[0]
}

/** Every app-owned screen receives the same localized resources, including Android 7–12. */
abstract class LocalizedActivity : Activity() {
    private var createdLanguage = "tr"
    protected var languageChangedOnRecreation = false
        private set
    protected var recreatingForLanguage = false
        private set

    override fun attachBaseContext(newBase: Context) {
        AppLanguage.migrate(newBase)
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        createdLanguage = AppLanguage.selected(this)
        languageChangedOnRecreation = savedInstanceState?.getString("interface_language_at_save")
            ?.let { it != createdLanguage } ?: false
        super.onCreate(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("interface_language_at_save", createdLanguage)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (AppLanguage.locale(this).language != AppLanguage.selected(this)) {
            recreatingForLanguage = true
            recreate()
        }
    }
}
