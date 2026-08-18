package com.sconcept.mirrordash.launcher.navigation

/**
 * The horizontally-paged surfaces of the launcher. A registry rather than hard-coded pager
 * logic so more pages (Home controls, Media, Calendar, ...) can be inserted later without
 * touching the pager/gesture code - see brief section 6/42. The one invariant every caller can
 * rely on: Clock is always first, Settings is always last.
 */
sealed class LauncherPage(val id: String, val label: String) {
    data object Clock : LauncherPage("clock", "Clock")
    data object Browser : LauncherPage("browser", "Web")
    data object Gym : LauncherPage("gym", "Gym")
    data object Jellyfin : LauncherPage("jellyfin", "Jellyfin")
    data object HomeAssistant : LauncherPage("home_assistant", "Home Assistant")
    data object Iptv : LauncherPage("iptv", "IPTV")
    data object Kodi : LauncherPage("kodi", "Kodi")
    data object Settings : LauncherPage("settings", "Settings")
}

object LauncherPages {
    /** Clock first, Settings last, everything else in between - enforced here once instead of
     * at every call site that builds the pager. Every optional page is a function parameter
     * rather than a fixed entry: Browser, Jellyfin, Home Assistant, and IPTV are opt-in - "each
     * tab can be enabled or disabled in the settings, but the clock and settings must always
     * remain." Photorama has no page of its own at all - it only ever exists as the Clock's own
     * background (see ClockViewModel/ClockScreen), configured from Clock settings directly. */
    fun ordered(
        includeBrowserPage: Boolean,
        includeGymPage: Boolean,
        includeJellyfinPage: Boolean,
        includeHomeAssistantPage: Boolean,
        includeIptvPage: Boolean,
        includeKodiPage: Boolean,
    ): List<LauncherPage> = buildList {
        add(LauncherPage.Clock)
        if (includeBrowserPage) add(LauncherPage.Browser)
        if (includeGymPage) add(LauncherPage.Gym)
        if (includeJellyfinPage) add(LauncherPage.Jellyfin)
        if (includeHomeAssistantPage) add(LauncherPage.HomeAssistant)
        if (includeIptvPage) add(LauncherPage.Iptv)
        if (includeKodiPage) add(LauncherPage.Kodi)
        add(LauncherPage.Settings)
    }
}
