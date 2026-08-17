package com.sconcept.mirrordash.launcher.navigation

/**
 * The horizontally-paged surfaces of the launcher. A registry rather than hard-coded pager
 * logic so more pages (Home controls, Media, Calendar, ...) can be inserted later without
 * touching the pager/gesture code - see brief section 6/42. The one invariant every caller can
 * rely on: Clock is always first, Settings is always last.
 */
sealed class LauncherPage(val id: String, val label: String) {
    data object Clock : LauncherPage("clock", "Clock")
    data object Photorama : LauncherPage("photorama", "Photorama")
    data object Browser : LauncherPage("browser", "Web")
    data object Jellyfin : LauncherPage("jellyfin", "Jellyfin")
    data object HomeAssistant : LauncherPage("home_assistant", "Home Assistant")
    data object Iptv : LauncherPage("iptv", "IPTV")
    data object Photobooth : LauncherPage("photobooth", "Photobooth")
    data object Settings : LauncherPage("settings", "Settings")
}

object LauncherPages {
    /** Clock first, Settings last, everything else in between - enforced here once instead of
     * at every call site that builds the pager. Every optional page is a function parameter
     * rather than a fixed entry: Photorama drops out while its slideshow is being used as the
     * Clock's own background instead (a standalone page showing the same photos the clock is
     * already showing behind itself would be a redundant, confusing extra swipe), while Browser,
     * Jellyfin, Home Assistant, and IPTV are opt-in entirely - "each tab can be enabled or
     * disabled in the settings, but the clock and settings must always remain." */
    fun ordered(
        includePhotoramaPage: Boolean,
        includeBrowserPage: Boolean,
        includeJellyfinPage: Boolean,
        includeHomeAssistantPage: Boolean,
        includeIptvPage: Boolean,
        includePhotoboothPage: Boolean,
    ): List<LauncherPage> = buildList {
        add(LauncherPage.Clock)
        if (includePhotoramaPage) add(LauncherPage.Photorama)
        if (includeBrowserPage) add(LauncherPage.Browser)
        if (includeJellyfinPage) add(LauncherPage.Jellyfin)
        if (includeHomeAssistantPage) add(LauncherPage.HomeAssistant)
        if (includeIptvPage) add(LauncherPage.Iptv)
        if (includePhotoboothPage) add(LauncherPage.Photobooth)
        add(LauncherPage.Settings)
    }
}
