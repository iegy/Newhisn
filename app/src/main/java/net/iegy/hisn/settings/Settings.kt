package net.iegy.hisn.settings

import net.iegy.hisn.browser.SearchEngine

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    ARABIC("ar"),
    ENGLISH("en"),
}

/** What a new tab shows. */
enum class NewTabPage { START_PAGE, CUSTOM_URL }

/** What the app shows when it is launched. */
enum class StartupBehavior { START_PAGE, RESTORE_TABS }

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    val blockingEnabled: Boolean = true,
    val autoUpdateFilters: Boolean = true,
    /** Normalised hosts on which blocking is disabled. */
    val allowlist: Set<String> = emptySet(),
    val newTabPage: NewTabPage = NewTabPage.START_PAGE,
    val customHomeUrl: String = "",
    val startup: StartupBehavior = StartupBehavior.START_PAGE,
    val desktopByDefault: Boolean = false,
    val showRecent: Boolean = true,
    /** Epoch millis of the last successful filter-list update, 0 if never. */
    val lastFilterUpdate: Long = 0L,
    val shortcutsSeeded: Boolean = false,
)
