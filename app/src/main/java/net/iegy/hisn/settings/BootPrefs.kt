package net.iegy.hisn.settings

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Receives the two settings that must be known synchronously, before DataStore has loaded. */
interface BootMirror {
    fun onThemeChanged(theme: ThemeMode)
    fun onLanguageChanged(language: AppLanguage)
}

/**
 * Tiny SharedPreferences mirror of the theme and language choices.
 *
 * A locale has to be applied in Activity.attachBaseContext, before any coroutine could read DataStore,
 * and the first frame should already use the right theme. DataStore stays the source of truth;
 * this file only holds those two values.
 */
/** commit() rather than apply(): the private-browsing process reads this file right after it starts. */
class BootPrefs(context: Context) : BootMirror {
    private val prefs = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    override fun onThemeChanged(theme: ThemeMode) {
        prefs.edit().putString(KEY_THEME, theme.name).commit()
    }

    override fun onLanguageChanged(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.name).commit()
    }

    companion object {
        private const val NAME = "hisn_boot"
        private const val KEY_THEME = "theme"
        private const val KEY_LANGUAGE = "language"

        fun theme(context: Context): ThemeMode = read(context, KEY_THEME, ThemeMode.SYSTEM)

        fun language(context: Context): AppLanguage = read(context, KEY_LANGUAGE, AppLanguage.SYSTEM)

        /** Settings known before DataStore loads; everything else keeps its default until the real values arrive. */
        fun initialSettings(context: Context) = Settings(theme = theme(context), language = language(context))

        private inline fun <reified T : Enum<T>> read(context: Context, key: String, default: T): T {
            val stored = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE).getString(key, null)
            return enumValues<T>().firstOrNull { it.name == stored } ?: default
        }
    }
}

object LocaleHelper {
    /** Returns a context whose resources use the user's in-app language (Arabic gets RTL layout automatically). */
    fun wrap(base: Context): Context {
        val tag = BootPrefs.language(base).tag ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}
