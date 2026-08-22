package com.sconcept.mirrordash.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sconcept.mirrordash.clock.CalendarWidget
import com.sconcept.mirrordash.clock.CLOCK_FONT_GOOGLE_PREFIX
import com.sconcept.mirrordash.clock.CLOCK_FONT_SYSTEM_DEFAULT
import com.sconcept.mirrordash.clock.CLOCK_STYLE_BIG_SMALL
import com.sconcept.mirrordash.clock.CustomTextWidget
import com.sconcept.mirrordash.clock.IconWidget
import com.sconcept.mirrordash.clock.DownloadedClockFont
import com.sconcept.mirrordash.clock.NewsWidget
import com.sconcept.mirrordash.clock.StocksWidget
import com.sconcept.mirrordash.clock.TasksWidget
import com.sconcept.mirrordash.clock.WeatherWidget
import com.sconcept.mirrordash.clock.defaultWeatherWidget
import com.sconcept.mirrordash.gym.FitnessDevicePreference
import com.sconcept.mirrordash.gym.GymFeatureSettings
import com.sconcept.mirrordash.gym.GymProfile
import com.sconcept.mirrordash.gym.GymSessionRecord
import com.sconcept.mirrordash.gym.defaultGymDevicePreferences
import com.sconcept.mirrordash.gym.defaultGymProfiles
import com.sconcept.mirrordash.iptv.CompletedRecording
import com.sconcept.mirrordash.iptv.DEFAULT_PARENTAL_CONTROL_PIN
import com.sconcept.mirrordash.iptv.ParentalControlMode
import com.sconcept.mirrordash.iptv.RecordingDestinationMode
import com.sconcept.mirrordash.iptv.ScheduledRecording
import com.sconcept.mirrordash.iptv.VodViewMode
import com.sconcept.mirrordash.iptv.player.PlayerBackend
import com.sconcept.mirrordash.nas.model.SmbShare
import com.sconcept.mirrordash.security.CredentialId
import com.sconcept.mirrordash.security.SecureCredentialStore
import com.sconcept.mirrordash.security.SessionCredentialHolder
import com.sconcept.mirrordash.ui.theme.DEFAULT_CLOCK_FONT_SIZE_SP
import com.sconcept.mirrordash.walkietalkie.DEFAULT_WALKIE_TALKIE_CHIME
import com.sconcept.mirrordash.walkietalkie.model.WalkieTalkiePeer
import com.sconcept.mirrordash.weather.WEATHER_ICON_STYLE_ANIMATED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "mirrordash_settings")

const val WALKIE_TALKIE_TARGET_ALL = "ALL"
const val DEFAULT_WALKIE_TALKIE_PORT = 45454

const val CLOCK_BACKGROUND_MODE_SOLID = "SOLID"
const val CLOCK_BACKGROUND_MODE_PHOTORAMA = "PHOTORAMA"

const val PHOTORAMA_SOURCE_NAS = "nas"
const val PHOTORAMA_SOURCE_LOCAL = "local"
const val DEFAULT_PHOTORAMA_SCHEDULE_START_MINUTES = 8 * 60
const val DEFAULT_PHOTORAMA_SCHEDULE_END_MINUTES = 22 * 60

const val AIRPLAY_AUTH_MODE_OPEN = "open"
const val AIRPLAY_AUTH_MODE_RANDOM_PIN = "random_pin"
const val AIRPLAY_AUTH_MODE_PASSWORD = "password"
const val AIRPLAY_MIRROR_PRESET_DISPLAY = "display"
const val AIRPLAY_MIRROR_PRESET_720P = "720p"
const val AIRPLAY_MIRROR_PRESET_1080P = "1080p"
const val AIRPLAY_MIRROR_PRESET_4K = "4k"
const val DEFAULT_DEVICE_NAME = "MirrorDash"
const val DEFAULT_KODI_PACKAGE_NAME = "org.xbmc.kodi"

const val BRIGHTNESS_DIM_TARGET_WHOLE_SCREEN = "WHOLE_SCREEN"
const val BRIGHTNESS_DIM_TARGET_TEXT_ONLY = "TEXT_ONLY"
const val BROWSER_EMPTY_START_PAGE_URL = "about:blank"

/** "Shut off completely (no memory) when not in view for more than 2 min" from the brief -
 * parametrable via [MirrorDashSettings.iptvSleepTimeoutSeconds], this is just the shipped default. */
const val DEFAULT_IPTV_SLEEP_TIMEOUT_SECONDS = 120

/** The local recording fallback's cap, not a target - only ~2.8GB is free on the reference
 * hardware, so this default leaves the OS/app plenty of headroom rather than trying to use all
 * of it. Oldest local recordings are deleted first once this is exceeded. */
const val DEFAULT_RECORDING_LOCAL_CAP_MB = 500

/** Bottom-start, matching the visual layout the Clock page ships with before anyone drags
 * anything - the single, only default (see the plan's note on why BerthierOptions' clock
 * position bug happened: two of these that disagreed with each other). */
const val DEFAULT_CLOCK_ANCHOR_X = 0.06f
const val DEFAULT_CLOCK_ANCHOR_Y = 0.82f
const val DEFAULT_WEATHER_ANCHOR_X = 0.92f
const val DEFAULT_WEATHER_ANCHOR_Y = 0.08f

/** Night Clock's own clock/weather positions - deliberately separate from the two above so
 * dragging on that hidden tab never moves the daytime Clock page's layout. Defaults stack the
 * two roughly centered, time above weather, matching a Nest Hub's ambient layout. */
const val DEFAULT_NIGHT_CLOCK_ANCHOR_X = 0.5f
const val DEFAULT_NIGHT_CLOCK_ANCHOR_Y = 0.42f
const val DEFAULT_NIGHT_CLOCK_WEATHER_ANCHOR_X = 0.5f
const val DEFAULT_NIGHT_CLOCK_WEATHER_ANCHOR_Y = 0.58f

/** Out-of-the-box weather location - without this, [weatherLatitude]/[weatherLongitude] default
 * to blank, so [com.sconcept.mirrordash.weather.WeatherViewModel] never has anything to fetch and
 * "isConfigured" stays false forever (no weather glyph/temperature anywhere on the Clock page)
 * until someone manually types a city into Settings. Pre-resolved coordinates (not just the query
 * string) so the very first refresh can fetch weather directly, with no geocoding round-trip - and
 * therefore no dependency on the geocoding API being reachable - before anything shows up.
 * Overridable per-unit via the provisioning config file's `weather` block. */
const val DEFAULT_WEATHER_LOCATION_QUERY = "Montreal"
const val DEFAULT_WEATHER_LOCATION_LABEL = "Montreal, Quebec, Canada"
const val DEFAULT_WEATHER_LATITUDE = "45.5019"
const val DEFAULT_WEATHER_LONGITUDE = "-73.5674"

private fun normalizeWeatherWidgets(widgets: List<WeatherWidget>): List<WeatherWidget> {
    if (widgets.size <= 1) return widgets
    val allSameAnchor = widgets.map { it.anchorX to it.anchorY }.distinct().size == 1
    if (!allSameAnchor) return widgets

    return widgets.mapIndexed { index, widget ->
        when {
            index == 0 -> widget.copy(anchorX = DEFAULT_WEATHER_ANCHOR_X, anchorY = DEFAULT_WEATHER_ANCHOR_Y)
            widget.mode == com.sconcept.mirrordash.clock.WEATHER_WIDGET_MODE_FORECAST_CARD ->
                widget.copy(anchorX = 0.46f, anchorY = 0.08f + ((index - 1) * 0.08f))
            else -> widget.copy(anchorX = 0.78f - (index * 0.12f), anchorY = 0.16f + (index * 0.05f))
        }
    }
}

data class MirrorDashSettings(
    // Device identity - one name shared by every network-facing feature (AirPlay, Walkie-Talkie,
    // and anything added later) rather than each feature keeping its own, so a unit only has one
    // name to recognize across every list it shows up in.
    val deviceName: String = "",

    // Appearance / Clock
    val clockFontSizeSp: Int = DEFAULT_CLOCK_FONT_SIZE_SP,
    val clockFontId: String = "${CLOCK_FONT_GOOGLE_PREFIX}worksans",
    val clockStyleId: String = CLOCK_STYLE_BIG_SMALL,
    val clockTextColorArgb: Int = 0xFFF5F3EF.toInt(),
    val clockAccentColorArgb: Int = 0xFFE8A659.toInt(),
    val clockBackgroundColorArgb: Int = 0xFF0B0C0E.toInt(),
    val clockBackgroundMode: String = CLOCK_BACKGROUND_MODE_SOLID,
    val clockAnchorX: Float = DEFAULT_CLOCK_ANCHOR_X,
    val clockAnchorY: Float = DEFAULT_CLOCK_ANCHOR_Y,
    val clockRotationDegrees: Float = 0f,
    val weatherAnchorX: Float = DEFAULT_WEATHER_ANCHOR_X,
    val weatherAnchorY: Float = DEFAULT_WEATHER_ANCHOR_Y,
    val weatherWidgets: List<WeatherWidget> = emptyList(),
    val weatherIconStyle: String = WEATHER_ICON_STYLE_ANIMATED,
    val downloadedClockFonts: List<DownloadedClockFont> = emptyList(),
    val customTextWidgets: List<CustomTextWidget> = emptyList(),
    val iconWidgets: List<IconWidget> = emptyList(),
    val calendarWidgets: List<CalendarWidget> = emptyList(),
    val tasksWidgets: List<TasksWidget> = emptyList(),
    val stocksWidgets: List<StocksWidget> = emptyList(),
    val newsWidgets: List<NewsWidget> = emptyList(),

    // Weather
    val weatherEnabled: Boolean = true,
    val weatherLocationQuery: String = DEFAULT_WEATHER_LOCATION_QUERY,
    val weatherLocationLabel: String = DEFAULT_WEATHER_LOCATION_LABEL,
    val weatherLatitude: String = DEFAULT_WEATHER_LATITUDE,
    val weatherLongitude: String = DEFAULT_WEATHER_LONGITUDE,
    val weatherUseFahrenheit: Boolean = false,

    // NAS connection
    val smbHost: String = "",
    val smbShareName: String = "",
    val smbUsername: String = "",
    val smbDomain: String = "",
    val smbRememberConnection: Boolean = false,

    // Photorama - fused into Clock settings, not its own tab (see ClockViewModel/ClockScreen).
    // photoramaEnabled is the one on/off switch, shared by the Clock's "Use Photorama as
    // background" toggle - there is deliberately no separate enable flag anymore.
    val photoramaEnabled: Boolean = false,
    // "" means unset - the Clock settings chooser (Local storage / NAS) hasn't been answered yet.
    val photoramaSource: String = "",
    val photoramaFolderPath: String = "",
    // SAF tree URI string (content://...) for the LOCAL source - "" until a folder is picked.
    // Persisted alongside a ContentResolver.takePersistableUriPermission grant so it survives
    // reboots (see LocalPhotoRepository).
    val photoramaLocalFolderUri: String = "",
    val photoramaIncludeSubfolders: Boolean = false,
    val photoramaPlayLivePhotos: Boolean = true,
    val photoramaIntervalSeconds: Int = 60,
    val photoramaShuffle: Boolean = true,
    val photoramaReplayLongVideoSegments: Boolean = true,
    // When on, a photo/video is only shown while the device's current orientation matches its
    // own (landscape media in landscape mode, portrait media in portrait mode) - see
    // PhotoramaViewModel.orientationMatchesDevice. Default true so the slideshow never shows a
    // sideways-cropped photo out of the box.
    val photoramaMatchOrientation: Boolean = true,
    // When on, the slideshow only indexes video files, skipping still images entirely. Default
    // off so existing photo libraries keep behaving the way they always have.
    val photoramaVideoOnly: Boolean = false,
    // A draggable/resizable "N / total" overlay, positioned and sized the same way every other
    // Clock-page widget is (see AnchoredWidget) - but single-instance like the clock digits
    // themselves rather than list-backed, and its on/off switch lives in Photorama settings
    // instead of the Widgets list, since it only ever makes sense while Photorama is the
    // background (see ClockScreen's showPhotoramaImageCount gating).
    val photoramaShowImageCount: Boolean = false,
    val photoramaImageCountFontSizeSp: Int = 24,
    val photoramaImageCountFontId: String = CLOCK_FONT_SYSTEM_DEFAULT,
    val photoramaImageCountColorArgb: Int = 0xFFF5F3EF.toInt(),
    val photoramaImageCountAnchorX: Float = 0.5f,
    val photoramaImageCountAnchorY: Float = 0.94f,
    val photoramaImageCountRotationDegrees: Float = 0f,
    val photoramaVideosMuted: Boolean = true,
    val photoramaCacheSizeMb: Int = 250,
    // Optional refinement on top of photoramaEnabled - off means "always on when enabled" (the
    // original behavior), on gates it to a daily start/end window (see PhotoramaSchedule).
    val photoramaScheduleEnabled: Boolean = false,
    val photoramaScheduleStartMinutes: Int = DEFAULT_PHOTORAMA_SCHEDULE_START_MINUTES,
    val photoramaScheduleEndMinutes: Int = DEFAULT_PHOTORAMA_SCHEDULE_END_MINUTES,

    // Clock page visibility - global, not Photorama-specific, though turning both off is how a
    // fullscreen Photorama photo-frame look (nothing overlaid) is achieved.
    val clockShowTime: Boolean = true,
    val clockShowWidgets: Boolean = true,

    // Walkie-Talkie
    val walkieTalkieEnabled: Boolean = false,
    val walkieTalkiePeers: List<WalkieTalkiePeer> = emptyList(),
    /** Peer IPs shown as shortcuts on the Clock home surface. They never affect peer discovery
     * or walkie-talkie eligibility. The peer registry currently owns IP identity. */
    val walkieTalkieHomeShortcutIps: Set<String> = emptySet(),
    /** Optional broadcast control alongside the selected-room buttons. Off by default so the
     * room stack replaces the former always-visible all-devices mic. */
    val walkieTalkieShowTalkToAllButton: Boolean = false,
    val walkieTalkieRoomShortcutIconSizePx: Int = 88,
    val walkieTalkieTarget: String = WALKIE_TALKIE_TARGET_ALL,
    val walkieTalkiePort: Int = DEFAULT_WALKIE_TALKIE_PORT,
    val walkieTalkieMicBoostPercent: Int = 100,
    val walkieTalkieIncomingChimeEnabled: Boolean = true,
    val walkieTalkieIncomingChime: String = DEFAULT_WALKIE_TALKIE_CHIME,
    val walkieTalkieOverlayEnabled: Boolean = false,
    val walkieTalkiePttAnchorX: Float = 0.92f,
    val walkieTalkiePttAnchorY: Float = 0.82f,
    // When on, every peer NSD discovery finds is added to walkieTalkiePeers automatically -
    // see the auto-add collector in WalkieTalkieEngine's init block.
    val walkieTalkieAutoAddDiscovered: Boolean = true,

    // AirPlay
    val airplayEnabled: Boolean = false,
    val airplayHwAddressHex: String = "",
    val airplayAuthMode: String = AIRPLAY_AUTH_MODE_OPEN,
    val airplayPassword: String = "",
    val airplayMirrorPreset: String = AIRPLAY_MIRROR_PRESET_DISPLAY,
    val airplayAllowHevc: Boolean = false,
    val airplayShowClockWidget: Boolean = false,

    // Camera RTSP is intentionally opt-in and LAN-only; the service refuses to listen unless
    // its kernel firewall rule is active.
    val rtspCameraEnabled: Boolean = false,
    /** Comma-separated IPv4 allowlist. Empty means the RTSP firewall accepts no clients. */
    val rtspAllowedClientIps: String = "",
    /** Low favors live responsiveness; medium is the default balance; high favors detail. */
    val rtspQuality: String = com.sconcept.mirrordash.rtsp.RTSP_QUALITY_MEDIUM,

    // Browser
    val browserEnabled: Boolean = false,
    val browserHomeUrl: String = "",
    val browserLastVisitedUrl: String = "",

    // Gym & Workouts
    val gymEnabled: Boolean = false,
    val gymFeatureSettings: GymFeatureSettings = GymFeatureSettings(),
    val gymProfiles: List<GymProfile> = defaultGymProfiles(),
    val gymDevicePreferences: List<FitnessDevicePreference> = defaultGymDevicePreferences(),
    val gymSessionHistory: List<GymSessionRecord> = emptyList(),

    // Jellyfin - the password never lives here, it goes through SecureCredentialStore
    // (CredentialId.JELLYFIN), same as the SMB password.
    val jellyfinEnabled: Boolean = false,
    val jellyfinServerUrl: String = "",
    val jellyfinStartPath: String = "",
    val jellyfinDesktopMode: Boolean = false,
    val jellyfinReloadOnOpen: Boolean = false,
    val jellyfinOpenExternalLinks: Boolean = true,
    val jellyfinUsername: String = "",
    val jellyfinAutoAuth: Boolean = true,

    // Home Assistant - password via SecureCredentialStore (CredentialId.HOME_ASSISTANT).
    val homeAssistantEnabled: Boolean = false,
    val homeAssistantUrl: String = "",
    val homeAssistantUsername: String = "",
    val homeAssistantAutoAuth: Boolean = true,

    // Kodi runs as its own Android app, so this tab is a launcher surface, not an embedded view.
    val kodiEnabled: Boolean = false,
    val kodiPackageName: String = DEFAULT_KODI_PACKAGE_NAME,
    val kodiAutoLaunchOnOpen: Boolean = true,

    // IPTV (Stalker/Ministra portal - see the iptv package)
    val iptvEnabled: Boolean = false,
    val iptvPortalUrl: String = "",
    val iptvMacAddress: String = "",
    val iptvSleepTimeoutSeconds: Int = DEFAULT_IPTV_SLEEP_TIMEOUT_SECONDS,
    // Remembered across app restarts so reopening the tab resumes where it left off, rather than
    // always full volume / the first channel - see IptvViewModel's connect flow.
    val iptvVolume: Float = 1f,
    val iptvLastChannelId: String = "",
    // Default player engine (see iptv/player) - IptvViewModel.setPlayerBackend also writes this
    // when the in-player quick switcher is used, so it's remembered as the new default too.
    val iptvPlayerBackend: String = PlayerBackend.EXOPLAYER.storageKey,
    // Recording (see IptvRecordingEngine). Both destinations are always implemented - this only
    // picks which is tried first, the other is the automatic fallback if it fails to open.
    //
    // This portal allows exactly one active session per MAC (confirmed live: a second handshake
    // silently kills the first) - so recording a different channel than what's live needs a MAC
    // of its own to avoid contending for that one slot. Blank means no second connection exists;
    // recording then shares the live-view session (IptvSessionCoordinator) and, if it must, takes
    // it over. A blank recording portal URL means "same portal as live viewing", only the MAC
    // differs - most providers issue extra MACs on the same portal, not a whole second portal.
    val iptvRecordingPortalUrl: String = "",
    val iptvRecordingMacAddress: String = "",
    // Parental control (see ParentalControlMode) - gates channels/genres the portal itself marks
    // `censored`, entirely client-side since the portal never checks any PIN on its own.
    val parentalControlPin: String = DEFAULT_PARENTAL_CONTROL_PIN,
    val parentalControlMode: String = ParentalControlMode.DISABLED.storageKey,
    val iptvRecordingDestination: String = RecordingDestinationMode.SMB_PRIMARY.storageKey,
    val iptvRecordingSmbFolder: String = "MirrorDash Recordings",
    val iptvRecordingLocalCapMb: Int = DEFAULT_RECORDING_LOCAL_CAP_MB,
    // Blank means "use the same folder live recordings do" - see IptvRecordingEngine.downloadVodItem.
    val iptvDownloadFolderName: String = "",
    // Grid vs list, remembered independently per content type - see IptvViewModel.setViewMode.
    val iptvMoviesViewMode: String = VodViewMode.GRID.storageKey,
    val iptvSeriesViewMode: String = VodViewMode.GRID.storageKey,
    val iptvShowRecordingStatusPill: Boolean = true,
    val iptvScheduledRecordings: List<ScheduledRecording> = emptyList(),
    // Newest first, capped at IPTV_RECORDING_HISTORY_LIMIT entries - see IptvRecordingEngine's
    // finally block, the one place this ever gets appended to.
    val iptvRecordingHistory: List<CompletedRecording> = emptyList(),

    // Launcher
    val launcherFavoriteApps: List<String> = emptyList(),
    val launcherHiddenApps: Set<String> = emptySet(),
    val lastVisitedPageIndex: Int = 0,
    val displayOrientationMode: String = "AUTO",
    val pageSwipeRequireTwoFingers: Boolean = true,

    // Brightness
    val brightnessLevel255: Int = 255,
    val brightnessExtraDimPercent: Int = 0,
    val brightnessDimTarget: String = BRIGHTNESS_DIM_TARGET_WHOLE_SCREEN,

    // Night Clock - a separate, uniquely-controlled brightness pair from the main Brightness
    // section above, applied only while the hidden Night Clock tab is showing (see
    // LauncherGestureHost). Defaults deliberately dim: this tab exists specifically to be dimmer
    // than anything the main brightness controls would normally be left at.
    val nightClockBrightnessLevel255: Int = 15,
    val nightClockTextDimPercent: Int = 50,
    val nightClockAnchorX: Float = DEFAULT_NIGHT_CLOCK_ANCHOR_X,
    val nightClockAnchorY: Float = DEFAULT_NIGHT_CLOCK_ANCHOR_Y,
    val nightClockWeatherAnchorX: Float = DEFAULT_NIGHT_CLOCK_WEATHER_ANCHOR_X,
    val nightClockWeatherAnchorY: Float = DEFAULT_NIGHT_CLOCK_WEATHER_ANCHOR_Y,

    // Provisioning - see the provisioning package. Set once the on-device config file (if any)
    // has been applied, so it only auto-seeds settings once, not on every boot.
    val provisioningAppliedOnce: Boolean = false,
) {
    val smbShare: SmbShare
        get() = SmbShare(
            host = smbHost,
            share = smbShareName,
            username = smbUsername,
            password = "",
            domain = smbDomain,
        )
}

/**
 * DataStore-backed replacement for BerthierOptions' `Config` (a synchronous SharedPreferences
 * wrapper built on Fossify's `BaseConfig`). Structural differences from the original:
 * - Reactive by default: [settings] is a cold [Flow], so UI observes changes instead of
 *   re-reading a getter on every recomposition.
 * - The SMB password never lives in this store; it's resolved through [SecureCredentialStore]
 *   / [SessionCredentialHolder] exactly as in BerthierOptions, and merged in by callers that
 *   need it (see `smbShareWithPassword`).
 * - No brightness/backlight/legacy-clock-position fields at all - see the plan's "explicitly
 *   not ported" list.
 */
class SettingsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dataStore = appContext.dataStore
    private val secureCredentialStore by lazy { SecureCredentialStore(appContext) }
    private val json = Json { ignoreUnknownKeys = true }

    val settings: Flow<MirrorDashSettings> = dataStore.data.map { it.toSettings() }

    suspend fun smbShareWithPassword(): SmbShare {
        val current = settings.first()
        return current.smbShare.copy(password = resolveSmbPassword(current.smbRememberConnection))
    }

    private fun resolveSmbPassword(remember: Boolean): String {
        return if (remember) {
            secureCredentialStore.loadCredentials(CredentialId.SMB) ?: ""
        } else {
            SessionCredentialHolder.smbPassword ?: ""
        }
    }

    suspend fun updateSmbConnection(share: SmbShare, remember: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.SMB_HOST] = share.host
            prefs[Keys.SMB_SHARE] = share.share
            prefs[Keys.SMB_USERNAME] = share.username
            prefs[Keys.SMB_DOMAIN] = share.domain
            prefs[Keys.SMB_REMEMBER] = remember
        }
        if (remember) {
            secureCredentialStore.saveCredentials(CredentialId.SMB, share.password)
            SessionCredentialHolder.smbPassword = null
        } else {
            secureCredentialStore.deleteCredentials(CredentialId.SMB)
            SessionCredentialHolder.smbPassword = share.password
        }
    }

    suspend fun forgetSmbConnection() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.SMB_HOST)
            prefs.remove(Keys.SMB_SHARE)
            prefs.remove(Keys.SMB_USERNAME)
            prefs.remove(Keys.SMB_DOMAIN)
            prefs.remove(Keys.SMB_REMEMBER)
            prefs.remove(Keys.PHOTORAMA_FOLDER_PATH)
            prefs[Keys.PHOTORAMA_ENABLED] = false
        }
        secureCredentialStore.deleteCredentials(CredentialId.SMB)
        SessionCredentialHolder.smbPassword = null
    }

    suspend fun jellyfinPassword(): String = secureCredentialStore.loadCredentials(CredentialId.JELLYFIN) ?: ""

    suspend fun saveJellyfinPassword(password: String) {
        secureCredentialStore.saveCredentials(CredentialId.JELLYFIN, password)
    }

    suspend fun homeAssistantPassword(): String = secureCredentialStore.loadCredentials(CredentialId.HOME_ASSISTANT) ?: ""

    suspend fun saveHomeAssistantPassword(password: String) {
        secureCredentialStore.saveCredentials(CredentialId.HOME_ASSISTANT, password)
    }

    suspend fun update(transform: MirrorDashSettingsEditor.() -> Unit) {
        dataStore.edit { prefs -> MirrorDashSettingsEditor(prefs).apply(transform) }
    }

    private fun Preferences.toSettings(): MirrorDashSettings {
        val defaults = MirrorDashSettings()
        val peersRaw = this[Keys.WALKIE_TALKIE_PEERS]
        val peers = peersRaw?.let { raw ->
            runCatching { json.decodeFromString<List<WalkieTalkiePeer>>(raw) }.getOrDefault(emptyList())
        } ?: emptyList()
        val favoritesRaw = this[Keys.LAUNCHER_FAVORITE_APPS]
        val favorites = favoritesRaw?.let { raw ->
            runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
        } ?: emptyList()
        val weatherWidgetDefaults = listOf(
            defaultWeatherWidget(
                anchorX = this[Keys.WEATHER_ANCHOR_X] ?: defaults.weatherAnchorX,
                anchorY = this[Keys.WEATHER_ANCHOR_Y] ?: defaults.weatherAnchorY,
            ),
        )
        val weatherWidgetsRaw = this[Keys.WEATHER_WIDGETS]
        val weatherWidgets = weatherWidgetsRaw?.let { raw ->
            runCatching { json.decodeFromString<List<WeatherWidget>>(raw) }.getOrDefault(weatherWidgetDefaults)
        } ?: weatherWidgetDefaults
        val textWidgetsRaw = this[Keys.CUSTOM_TEXT_WIDGETS]
        val textWidgets = textWidgetsRaw?.let { raw ->
            runCatching { json.decodeFromString<List<CustomTextWidget>>(raw) }.getOrDefault(emptyList())
        } ?: emptyList()
        val iconWidgets = this[Keys.ICON_WIDGETS]
            ?.let { raw -> runCatching { json.decodeFromString<List<IconWidget>>(raw) }.getOrDefault(emptyList()) }
            ?: emptyList()
        val downloadedClockFontsRaw = this[Keys.DOWNLOADED_CLOCK_FONTS]
        val downloadedClockFonts = downloadedClockFontsRaw?.let { raw ->
            runCatching { json.decodeFromString<List<DownloadedClockFont>>(raw) }.getOrDefault(emptyList())
        } ?: emptyList()
        val calendarWidgetsRaw = this[Keys.CALENDAR_WIDGETS]
        val calendarWidgets = calendarWidgetsRaw?.let { raw ->
            runCatching { json.decodeFromString<List<CalendarWidget>>(raw) }.getOrDefault(emptyList())
        } ?: emptyList()
        val tasksWidgetsRaw = this[Keys.TASKS_WIDGETS]
        val tasksWidgets = tasksWidgetsRaw?.let { raw ->
            runCatching { json.decodeFromString<List<TasksWidget>>(raw) }.getOrDefault(emptyList())
        } ?: emptyList()
        val stocksWidgetsRaw = this[Keys.STOCKS_WIDGETS]
        val stocksWidgets = stocksWidgetsRaw?.let { raw ->
            runCatching { json.decodeFromString<List<StocksWidget>>(raw) }.getOrDefault(emptyList())
        } ?: emptyList()
        val newsWidgetsRaw = this[Keys.NEWS_WIDGETS]
        val newsWidgets = newsWidgetsRaw?.let { raw ->
            runCatching { json.decodeFromString<List<NewsWidget>>(raw) }.getOrDefault(emptyList())
        } ?: emptyList()
        val scheduledRecordingsRaw = this[Keys.IPTV_SCHEDULED_RECORDINGS]
        val scheduledRecordings = scheduledRecordingsRaw?.let { raw ->
            runCatching { json.decodeFromString<List<ScheduledRecording>>(raw) }.getOrDefault(emptyList())
        } ?: emptyList()
        val recordingHistoryRaw = this[Keys.IPTV_RECORDING_HISTORY]
        val recordingHistory = recordingHistoryRaw?.let { raw ->
            runCatching { json.decodeFromString<List<CompletedRecording>>(raw) }.getOrDefault(emptyList())
        } ?: emptyList()

        return MirrorDashSettings(
            deviceName = this[Keys.DEVICE_NAME] ?: defaults.deviceName,
            clockFontSizeSp = this[Keys.CLOCK_FONT_SIZE_SP] ?: defaults.clockFontSizeSp,
            clockFontId = this[Keys.CLOCK_FONT_ID] ?: defaults.clockFontId,
            clockStyleId = this[Keys.CLOCK_STYLE_ID] ?: defaults.clockStyleId,
            clockTextColorArgb = this[Keys.CLOCK_TEXT_COLOR] ?: defaults.clockTextColorArgb,
            clockAccentColorArgb = this[Keys.CLOCK_ACCENT_COLOR] ?: defaults.clockAccentColorArgb,
            clockBackgroundColorArgb = this[Keys.CLOCK_BACKGROUND_COLOR] ?: defaults.clockBackgroundColorArgb,
            clockBackgroundMode = this[Keys.CLOCK_BACKGROUND_MODE] ?: defaults.clockBackgroundMode,
            clockAnchorX = this[Keys.CLOCK_ANCHOR_X] ?: defaults.clockAnchorX,
            clockAnchorY = this[Keys.CLOCK_ANCHOR_Y] ?: defaults.clockAnchorY,
            clockRotationDegrees = this[Keys.CLOCK_ROTATION_DEGREES] ?: defaults.clockRotationDegrees,
            weatherAnchorX = this[Keys.WEATHER_ANCHOR_X] ?: defaults.weatherAnchorX,
            weatherAnchorY = this[Keys.WEATHER_ANCHOR_Y] ?: defaults.weatherAnchorY,
            weatherWidgets = normalizeWeatherWidgets(weatherWidgets),
            weatherIconStyle = this[Keys.WEATHER_ICON_STYLE] ?: defaults.weatherIconStyle,
            downloadedClockFonts = downloadedClockFonts,
            customTextWidgets = textWidgets,
            iconWidgets = iconWidgets,
            calendarWidgets = calendarWidgets,
            tasksWidgets = tasksWidgets,
            stocksWidgets = stocksWidgets,
            newsWidgets = newsWidgets,
            weatherEnabled = this[Keys.WEATHER_ENABLED] ?: defaults.weatherEnabled,
            weatherLocationQuery = this[Keys.WEATHER_LOCATION_QUERY] ?: defaults.weatherLocationQuery,
            weatherLocationLabel = this[Keys.WEATHER_LOCATION_LABEL] ?: defaults.weatherLocationLabel,
            weatherLatitude = this[Keys.WEATHER_LATITUDE] ?: defaults.weatherLatitude,
            weatherLongitude = this[Keys.WEATHER_LONGITUDE] ?: defaults.weatherLongitude,
            weatherUseFahrenheit = this[Keys.WEATHER_USE_FAHRENHEIT] ?: defaults.weatherUseFahrenheit,
            smbHost = this[Keys.SMB_HOST] ?: defaults.smbHost,
            smbShareName = this[Keys.SMB_SHARE] ?: defaults.smbShareName,
            smbUsername = this[Keys.SMB_USERNAME] ?: defaults.smbUsername,
            smbDomain = this[Keys.SMB_DOMAIN] ?: defaults.smbDomain,
            smbRememberConnection = this[Keys.SMB_REMEMBER] ?: defaults.smbRememberConnection,
            photoramaEnabled = this[Keys.PHOTORAMA_ENABLED] ?: defaults.photoramaEnabled,
            photoramaSource = this[Keys.PHOTORAMA_SOURCE] ?: defaults.photoramaSource,
            photoramaFolderPath = this[Keys.PHOTORAMA_FOLDER_PATH] ?: defaults.photoramaFolderPath,
            photoramaLocalFolderUri = this[Keys.PHOTORAMA_LOCAL_FOLDER_URI] ?: defaults.photoramaLocalFolderUri,
            photoramaIncludeSubfolders = this[Keys.PHOTORAMA_INCLUDE_SUBFOLDERS] ?: defaults.photoramaIncludeSubfolders,
            photoramaPlayLivePhotos = this[Keys.PHOTORAMA_PLAY_LIVE_PHOTOS] ?: defaults.photoramaPlayLivePhotos,
            photoramaIntervalSeconds = this[Keys.PHOTORAMA_INTERVAL_SECONDS] ?: defaults.photoramaIntervalSeconds,
            photoramaShuffle = this[Keys.PHOTORAMA_SHUFFLE] ?: defaults.photoramaShuffle,
            photoramaReplayLongVideoSegments = this[Keys.PHOTORAMA_REPLAY_LONG_VIDEO_SEGMENTS] ?: defaults.photoramaReplayLongVideoSegments,
            photoramaMatchOrientation = this[Keys.PHOTORAMA_MATCH_ORIENTATION] ?: defaults.photoramaMatchOrientation,
            photoramaVideoOnly = this[Keys.PHOTORAMA_VIDEO_ONLY] ?: defaults.photoramaVideoOnly,
            photoramaShowImageCount = this[Keys.PHOTORAMA_SHOW_IMAGE_COUNT] ?: defaults.photoramaShowImageCount,
            photoramaImageCountFontSizeSp = this[Keys.PHOTORAMA_IMAGE_COUNT_FONT_SIZE_SP] ?: defaults.photoramaImageCountFontSizeSp,
            photoramaImageCountFontId = this[Keys.PHOTORAMA_IMAGE_COUNT_FONT_ID] ?: defaults.photoramaImageCountFontId,
            photoramaImageCountColorArgb = this[Keys.PHOTORAMA_IMAGE_COUNT_COLOR] ?: defaults.photoramaImageCountColorArgb,
            photoramaImageCountAnchorX = this[Keys.PHOTORAMA_IMAGE_COUNT_ANCHOR_X] ?: defaults.photoramaImageCountAnchorX,
            photoramaImageCountAnchorY = this[Keys.PHOTORAMA_IMAGE_COUNT_ANCHOR_Y] ?: defaults.photoramaImageCountAnchorY,
            photoramaImageCountRotationDegrees = this[Keys.PHOTORAMA_IMAGE_COUNT_ROTATION_DEGREES] ?: defaults.photoramaImageCountRotationDegrees,
            photoramaVideosMuted = this[Keys.PHOTORAMA_VIDEOS_MUTED] ?: defaults.photoramaVideosMuted,
            photoramaCacheSizeMb = this[Keys.PHOTORAMA_CACHE_SIZE_MB] ?: defaults.photoramaCacheSizeMb,
            photoramaScheduleEnabled = this[Keys.PHOTORAMA_SCHEDULE_ENABLED] ?: defaults.photoramaScheduleEnabled,
            photoramaScheduleStartMinutes = this[Keys.PHOTORAMA_SCHEDULE_START_MINUTES] ?: defaults.photoramaScheduleStartMinutes,
            photoramaScheduleEndMinutes = this[Keys.PHOTORAMA_SCHEDULE_END_MINUTES] ?: defaults.photoramaScheduleEndMinutes,
            clockShowTime = this[Keys.CLOCK_SHOW_TIME] ?: defaults.clockShowTime,
            clockShowWidgets = this[Keys.CLOCK_SHOW_WIDGETS] ?: defaults.clockShowWidgets,
            walkieTalkieEnabled = this[Keys.WALKIE_TALKIE_ENABLED] ?: defaults.walkieTalkieEnabled,
            walkieTalkiePeers = peers,
            walkieTalkieHomeShortcutIps = this[Keys.WALKIE_TALKIE_HOME_SHORTCUT_IPS] ?: defaults.walkieTalkieHomeShortcutIps,
            walkieTalkieShowTalkToAllButton = this[Keys.WALKIE_TALKIE_SHOW_TALK_TO_ALL_BUTTON] ?: defaults.walkieTalkieShowTalkToAllButton,
            walkieTalkieRoomShortcutIconSizePx = (this[Keys.WALKIE_TALKIE_ROOM_SHORTCUT_ICON_SIZE_PX]
                ?: defaults.walkieTalkieRoomShortcutIconSizePx).coerceIn(8, 200),
            walkieTalkieTarget = this[Keys.WALKIE_TALKIE_TARGET] ?: defaults.walkieTalkieTarget,
            walkieTalkiePort = this[Keys.WALKIE_TALKIE_PORT] ?: defaults.walkieTalkiePort,
            walkieTalkieMicBoostPercent = this[Keys.WALKIE_TALKIE_MIC_BOOST] ?: defaults.walkieTalkieMicBoostPercent,
            walkieTalkieIncomingChimeEnabled = this[Keys.WALKIE_TALKIE_INCOMING_CHIME_ENABLED] ?: defaults.walkieTalkieIncomingChimeEnabled,
            walkieTalkieIncomingChime = this[Keys.WALKIE_TALKIE_INCOMING_CHIME] ?: defaults.walkieTalkieIncomingChime,
            walkieTalkieOverlayEnabled = this[Keys.WALKIE_TALKIE_OVERLAY_ENABLED] ?: defaults.walkieTalkieOverlayEnabled,
            walkieTalkiePttAnchorX = this[Keys.WALKIE_TALKIE_PTT_ANCHOR_X] ?: defaults.walkieTalkiePttAnchorX,
            walkieTalkiePttAnchorY = this[Keys.WALKIE_TALKIE_PTT_ANCHOR_Y] ?: defaults.walkieTalkiePttAnchorY,
            walkieTalkieAutoAddDiscovered = this[Keys.WALKIE_TALKIE_AUTO_ADD_DISCOVERED] ?: defaults.walkieTalkieAutoAddDiscovered,
            airplayEnabled = this[Keys.AIRPLAY_ENABLED] ?: defaults.airplayEnabled,
            airplayHwAddressHex = this[Keys.AIRPLAY_HW_ADDRESS_HEX] ?: defaults.airplayHwAddressHex,
            airplayAuthMode = this[Keys.AIRPLAY_AUTH_MODE] ?: defaults.airplayAuthMode,
            airplayPassword = this[Keys.AIRPLAY_PASSWORD] ?: defaults.airplayPassword,
            airplayMirrorPreset = this[Keys.AIRPLAY_MIRROR_PRESET] ?: defaults.airplayMirrorPreset,
            airplayAllowHevc = this[Keys.AIRPLAY_ALLOW_HEVC] ?: defaults.airplayAllowHevc,
            airplayShowClockWidget = this[Keys.AIRPLAY_SHOW_CLOCK_WIDGET] ?: defaults.airplayShowClockWidget,
            rtspCameraEnabled = this[Keys.RTSP_CAMERA_ENABLED] ?: defaults.rtspCameraEnabled,
            rtspAllowedClientIps = this[Keys.RTSP_ALLOWED_CLIENT_IPS] ?: defaults.rtspAllowedClientIps,
            rtspQuality = this[Keys.RTSP_QUALITY] ?: defaults.rtspQuality,
            browserEnabled = this[Keys.BROWSER_ENABLED] ?: defaults.browserEnabled,
            browserHomeUrl = this[Keys.BROWSER_HOME_URL] ?: defaults.browserHomeUrl,
            browserLastVisitedUrl = this[Keys.BROWSER_LAST_VISITED_URL] ?: defaults.browserLastVisitedUrl,
            gymEnabled = this[Keys.GYM_ENABLED] ?: defaults.gymEnabled,
            gymFeatureSettings = this[Keys.GYM_FEATURE_SETTINGS]?.let { raw ->
                runCatching { json.decodeFromString<GymFeatureSettings>(raw) }.getOrDefault(defaults.gymFeatureSettings)
            } ?: defaults.gymFeatureSettings,
            gymProfiles = this[Keys.GYM_PROFILES]?.let { raw ->
                runCatching { json.decodeFromString<List<GymProfile>>(raw) }.getOrDefault(defaults.gymProfiles)
            } ?: defaults.gymProfiles,
            gymDevicePreferences = this[Keys.GYM_DEVICE_PREFERENCES]?.let { raw ->
                runCatching { json.decodeFromString<List<FitnessDevicePreference>>(raw) }.getOrDefault(defaults.gymDevicePreferences)
            } ?: defaults.gymDevicePreferences,
            gymSessionHistory = this[Keys.GYM_SESSION_HISTORY]?.let { raw ->
                runCatching { json.decodeFromString<List<GymSessionRecord>>(raw) }.getOrDefault(defaults.gymSessionHistory)
            } ?: defaults.gymSessionHistory,
            jellyfinEnabled = this[Keys.JELLYFIN_ENABLED] ?: defaults.jellyfinEnabled,
            jellyfinServerUrl = this[Keys.JELLYFIN_SERVER_URL] ?: defaults.jellyfinServerUrl,
            jellyfinStartPath = this[Keys.JELLYFIN_START_PATH] ?: defaults.jellyfinStartPath,
            jellyfinDesktopMode = this[Keys.JELLYFIN_DESKTOP_MODE] ?: defaults.jellyfinDesktopMode,
            jellyfinReloadOnOpen = this[Keys.JELLYFIN_RELOAD_ON_OPEN] ?: defaults.jellyfinReloadOnOpen,
            jellyfinOpenExternalLinks = this[Keys.JELLYFIN_OPEN_EXTERNAL_LINKS] ?: defaults.jellyfinOpenExternalLinks,
            jellyfinUsername = this[Keys.JELLYFIN_USERNAME] ?: defaults.jellyfinUsername,
            jellyfinAutoAuth = this[Keys.JELLYFIN_AUTO_AUTH] ?: defaults.jellyfinAutoAuth,
            homeAssistantEnabled = this[Keys.HOME_ASSISTANT_ENABLED] ?: defaults.homeAssistantEnabled,
            homeAssistantUrl = this[Keys.HOME_ASSISTANT_URL] ?: defaults.homeAssistantUrl,
            homeAssistantUsername = this[Keys.HOME_ASSISTANT_USERNAME] ?: defaults.homeAssistantUsername,
            homeAssistantAutoAuth = this[Keys.HOME_ASSISTANT_AUTO_AUTH] ?: defaults.homeAssistantAutoAuth,
            kodiEnabled = this[Keys.KODI_ENABLED] ?: defaults.kodiEnabled,
            kodiPackageName = this[Keys.KODI_PACKAGE_NAME] ?: defaults.kodiPackageName,
            kodiAutoLaunchOnOpen = this[Keys.KODI_AUTO_LAUNCH_ON_OPEN] ?: defaults.kodiAutoLaunchOnOpen,
            iptvEnabled = this[Keys.IPTV_ENABLED] ?: defaults.iptvEnabled,
            iptvPortalUrl = this[Keys.IPTV_PORTAL_URL] ?: defaults.iptvPortalUrl,
            iptvMacAddress = this[Keys.IPTV_MAC_ADDRESS] ?: defaults.iptvMacAddress,
            iptvSleepTimeoutSeconds = this[Keys.IPTV_SLEEP_TIMEOUT_SECONDS] ?: defaults.iptvSleepTimeoutSeconds,
            iptvVolume = this[Keys.IPTV_VOLUME] ?: defaults.iptvVolume,
            iptvLastChannelId = this[Keys.IPTV_LAST_CHANNEL_ID] ?: defaults.iptvLastChannelId,
            iptvPlayerBackend = this[Keys.IPTV_PLAYER_BACKEND] ?: defaults.iptvPlayerBackend,
            iptvRecordingPortalUrl = this[Keys.IPTV_RECORDING_PORTAL_URL] ?: defaults.iptvRecordingPortalUrl,
            iptvRecordingMacAddress = this[Keys.IPTV_RECORDING_MAC_ADDRESS] ?: defaults.iptvRecordingMacAddress,
            parentalControlPin = this[Keys.PARENTAL_CONTROL_PIN] ?: defaults.parentalControlPin,
            parentalControlMode = this[Keys.PARENTAL_CONTROL_MODE] ?: defaults.parentalControlMode,
            iptvRecordingDestination = this[Keys.IPTV_RECORDING_DESTINATION] ?: defaults.iptvRecordingDestination,
            iptvRecordingSmbFolder = this[Keys.IPTV_RECORDING_SMB_FOLDER] ?: defaults.iptvRecordingSmbFolder,
            iptvRecordingLocalCapMb = this[Keys.IPTV_RECORDING_LOCAL_CAP_MB] ?: defaults.iptvRecordingLocalCapMb,
            iptvDownloadFolderName = this[Keys.IPTV_DOWNLOAD_FOLDER_NAME] ?: defaults.iptvDownloadFolderName,
            iptvMoviesViewMode = this[Keys.IPTV_MOVIES_VIEW_MODE] ?: defaults.iptvMoviesViewMode,
            iptvSeriesViewMode = this[Keys.IPTV_SERIES_VIEW_MODE] ?: defaults.iptvSeriesViewMode,
            iptvShowRecordingStatusPill = this[Keys.IPTV_SHOW_RECORDING_STATUS_PILL] ?: defaults.iptvShowRecordingStatusPill,
            iptvScheduledRecordings = scheduledRecordings,
            iptvRecordingHistory = recordingHistory,
            launcherFavoriteApps = favorites,
            launcherHiddenApps = this[Keys.LAUNCHER_HIDDEN_APPS] ?: defaults.launcherHiddenApps,
            lastVisitedPageIndex = this[Keys.LAST_VISITED_PAGE_INDEX] ?: defaults.lastVisitedPageIndex,
            displayOrientationMode = this[Keys.DISPLAY_ORIENTATION_MODE] ?: defaults.displayOrientationMode,
            pageSwipeRequireTwoFingers = this[Keys.PAGE_SWIPE_REQUIRE_TWO_FINGERS] ?: defaults.pageSwipeRequireTwoFingers,
            brightnessLevel255 = this[Keys.BRIGHTNESS_LEVEL_255] ?: defaults.brightnessLevel255,
            brightnessExtraDimPercent = this[Keys.BRIGHTNESS_EXTRA_DIM_PERCENT] ?: defaults.brightnessExtraDimPercent,
            brightnessDimTarget = this[Keys.BRIGHTNESS_DIM_TARGET] ?: defaults.brightnessDimTarget,
            nightClockBrightnessLevel255 = this[Keys.NIGHT_CLOCK_BRIGHTNESS_LEVEL_255] ?: defaults.nightClockBrightnessLevel255,
            nightClockTextDimPercent = this[Keys.NIGHT_CLOCK_TEXT_DIM_PERCENT] ?: defaults.nightClockTextDimPercent,
            nightClockAnchorX = this[Keys.NIGHT_CLOCK_ANCHOR_X] ?: defaults.nightClockAnchorX,
            nightClockAnchorY = this[Keys.NIGHT_CLOCK_ANCHOR_Y] ?: defaults.nightClockAnchorY,
            nightClockWeatherAnchorX = this[Keys.NIGHT_CLOCK_WEATHER_ANCHOR_X] ?: defaults.nightClockWeatherAnchorX,
            nightClockWeatherAnchorY = this[Keys.NIGHT_CLOCK_WEATHER_ANCHOR_Y] ?: defaults.nightClockWeatherAnchorY,
            provisioningAppliedOnce = this[Keys.PROVISIONING_APPLIED_ONCE] ?: defaults.provisioningAppliedOnce,
        )
    }

    internal object Keys {
        val DEVICE_NAME = stringPreferencesKey("device_name")

        val CLOCK_FONT_SIZE_SP = intPreferencesKey("clock_font_size_sp")
        val CLOCK_FONT_ID = stringPreferencesKey("clock_font_id")
        val CLOCK_STYLE_ID = stringPreferencesKey("clock_style_id")
        val CLOCK_TEXT_COLOR = intPreferencesKey("clock_text_color")
        val CLOCK_ACCENT_COLOR = intPreferencesKey("clock_accent_color")
        val CLOCK_BACKGROUND_COLOR = intPreferencesKey("clock_background_color")
        val CLOCK_BACKGROUND_MODE = stringPreferencesKey("clock_background_mode")
        val CLOCK_ANCHOR_X = floatPreferencesKey("clock_anchor_x")
        val CLOCK_ANCHOR_Y = floatPreferencesKey("clock_anchor_y")
        val CLOCK_ROTATION_DEGREES = floatPreferencesKey("clock_rotation_degrees")
        val WEATHER_ANCHOR_X = floatPreferencesKey("weather_anchor_x")
        val WEATHER_ANCHOR_Y = floatPreferencesKey("weather_anchor_y")
        val WEATHER_WIDGETS = stringPreferencesKey("weather_widgets_json")
        val WEATHER_ICON_STYLE = stringPreferencesKey("weather_icon_style")
        val CALENDAR_WIDGETS = stringPreferencesKey("calendar_widgets_json")
        val TASKS_WIDGETS = stringPreferencesKey("tasks_widgets_json")
        val STOCKS_WIDGETS = stringPreferencesKey("stocks_widgets_json")
        val NEWS_WIDGETS = stringPreferencesKey("news_widgets_json")
        val DOWNLOADED_CLOCK_FONTS = stringPreferencesKey("downloaded_clock_fonts_json")

        val WEATHER_ENABLED = booleanPreferencesKey("weather_enabled")
        val WEATHER_LOCATION_QUERY = stringPreferencesKey("weather_location_query")
        val WEATHER_LOCATION_LABEL = stringPreferencesKey("weather_location_label")
        val WEATHER_LATITUDE = stringPreferencesKey("weather_latitude")
        val WEATHER_LONGITUDE = stringPreferencesKey("weather_longitude")
        val WEATHER_USE_FAHRENHEIT = booleanPreferencesKey("weather_use_fahrenheit")

        val SMB_HOST = stringPreferencesKey("smb_host")
        val SMB_SHARE = stringPreferencesKey("smb_share")
        val SMB_USERNAME = stringPreferencesKey("smb_username")
        val SMB_DOMAIN = stringPreferencesKey("smb_domain")
        val SMB_REMEMBER = booleanPreferencesKey("smb_remember_connection")

        val PHOTORAMA_ENABLED = booleanPreferencesKey("photorama_enabled")
        val PHOTORAMA_SOURCE = stringPreferencesKey("photorama_source")
        val PHOTORAMA_FOLDER_PATH = stringPreferencesKey("photorama_folder_path")
        val PHOTORAMA_LOCAL_FOLDER_URI = stringPreferencesKey("photorama_local_folder_uri")
        val PHOTORAMA_INCLUDE_SUBFOLDERS = booleanPreferencesKey("photorama_include_subfolders")
        val PHOTORAMA_PLAY_LIVE_PHOTOS = booleanPreferencesKey("photorama_play_live_photos")
        val PHOTORAMA_INTERVAL_SECONDS = intPreferencesKey("photorama_interval_seconds")
        val PHOTORAMA_SHUFFLE = booleanPreferencesKey("photorama_shuffle")
        val PHOTORAMA_REPLAY_LONG_VIDEO_SEGMENTS = booleanPreferencesKey("photorama_replay_long_video_segments")
        val PHOTORAMA_MATCH_ORIENTATION = booleanPreferencesKey("photorama_match_orientation")
        val PHOTORAMA_VIDEO_ONLY = booleanPreferencesKey("photorama_video_only")
        val PHOTORAMA_SHOW_IMAGE_COUNT = booleanPreferencesKey("photorama_show_image_count")
        val PHOTORAMA_IMAGE_COUNT_FONT_SIZE_SP = intPreferencesKey("photorama_image_count_font_size_sp")
        val PHOTORAMA_IMAGE_COUNT_FONT_ID = stringPreferencesKey("photorama_image_count_font_id")
        val PHOTORAMA_IMAGE_COUNT_COLOR = intPreferencesKey("photorama_image_count_color")
        val PHOTORAMA_IMAGE_COUNT_ANCHOR_X = floatPreferencesKey("photorama_image_count_anchor_x")
        val PHOTORAMA_IMAGE_COUNT_ANCHOR_Y = floatPreferencesKey("photorama_image_count_anchor_y")
        val PHOTORAMA_IMAGE_COUNT_ROTATION_DEGREES = floatPreferencesKey("photorama_image_count_rotation_degrees")
        val PHOTORAMA_VIDEOS_MUTED = booleanPreferencesKey("photorama_videos_muted")
        val PHOTORAMA_CACHE_SIZE_MB = intPreferencesKey("photorama_cache_size_mb")
        val PHOTORAMA_SCHEDULE_ENABLED = booleanPreferencesKey("photorama_schedule_enabled")
        val PHOTORAMA_SCHEDULE_START_MINUTES = intPreferencesKey("photorama_schedule_start_minutes")
        val PHOTORAMA_SCHEDULE_END_MINUTES = intPreferencesKey("photorama_schedule_end_minutes")

        val CLOCK_SHOW_TIME = booleanPreferencesKey("clock_show_time")
        val CLOCK_SHOW_WIDGETS = booleanPreferencesKey("clock_show_widgets")

        val WALKIE_TALKIE_ENABLED = booleanPreferencesKey("walkie_talkie_enabled")
        val WALKIE_TALKIE_PEERS = stringPreferencesKey("walkie_talkie_peers_json")
        val WALKIE_TALKIE_HOME_SHORTCUT_IPS = stringSetPreferencesKey("walkie_talkie_home_shortcut_ips")
        val WALKIE_TALKIE_SHOW_TALK_TO_ALL_BUTTON = booleanPreferencesKey("walkie_talkie_show_talk_to_all_button")
        val WALKIE_TALKIE_ROOM_SHORTCUT_ICON_SIZE_PX = intPreferencesKey("walkie_talkie_room_shortcut_icon_size_px")
        val WALKIE_TALKIE_TARGET = stringPreferencesKey("walkie_talkie_target")
        val WALKIE_TALKIE_PORT = intPreferencesKey("walkie_talkie_port")
        val WALKIE_TALKIE_MIC_BOOST = intPreferencesKey("walkie_talkie_mic_boost_percent")
        val WALKIE_TALKIE_INCOMING_CHIME_ENABLED = booleanPreferencesKey("walkie_talkie_incoming_chime_enabled")
        val WALKIE_TALKIE_INCOMING_CHIME = stringPreferencesKey("walkie_talkie_incoming_chime")
        val WALKIE_TALKIE_OVERLAY_ENABLED = booleanPreferencesKey("walkie_talkie_overlay_enabled")
        val WALKIE_TALKIE_PTT_ANCHOR_X = floatPreferencesKey("walkie_talkie_ptt_anchor_x")
        val WALKIE_TALKIE_PTT_ANCHOR_Y = floatPreferencesKey("walkie_talkie_ptt_anchor_y")
        val WALKIE_TALKIE_AUTO_ADD_DISCOVERED = booleanPreferencesKey("walkie_talkie_auto_add_discovered")

        val AIRPLAY_ENABLED = booleanPreferencesKey("airplay_enabled")
        val AIRPLAY_HW_ADDRESS_HEX = stringPreferencesKey("airplay_hw_address_hex")
        val AIRPLAY_AUTH_MODE = stringPreferencesKey("airplay_auth_mode")
        val AIRPLAY_PASSWORD = stringPreferencesKey("airplay_password")
        val AIRPLAY_MIRROR_PRESET = stringPreferencesKey("airplay_mirror_preset")
        val AIRPLAY_ALLOW_HEVC = booleanPreferencesKey("airplay_allow_hevc")
        val AIRPLAY_SHOW_CLOCK_WIDGET = booleanPreferencesKey("airplay_show_clock_widget")

        val RTSP_CAMERA_ENABLED = booleanPreferencesKey("rtsp_camera_enabled")
        val RTSP_ALLOWED_CLIENT_IPS = stringPreferencesKey("rtsp_allowed_client_ips")
        val RTSP_QUALITY = stringPreferencesKey("rtsp_quality")

        val BROWSER_ENABLED = booleanPreferencesKey("browser_enabled")
        val BROWSER_HOME_URL = stringPreferencesKey("browser_home_url")
        val BROWSER_LAST_VISITED_URL = stringPreferencesKey("browser_last_visited_url")

        val GYM_ENABLED = booleanPreferencesKey("gym_enabled")
        val GYM_FEATURE_SETTINGS = stringPreferencesKey("gym_feature_settings_json")
        val GYM_PROFILES = stringPreferencesKey("gym_profiles_json")
        val GYM_DEVICE_PREFERENCES = stringPreferencesKey("gym_device_preferences_json")
        val GYM_SESSION_HISTORY = stringPreferencesKey("gym_session_history_json")

        val JELLYFIN_ENABLED = booleanPreferencesKey("jellyfin_enabled")
        val JELLYFIN_SERVER_URL = stringPreferencesKey("jellyfin_server_url")
        val JELLYFIN_START_PATH = stringPreferencesKey("jellyfin_start_path")
        val JELLYFIN_DESKTOP_MODE = booleanPreferencesKey("jellyfin_desktop_mode")
        val JELLYFIN_RELOAD_ON_OPEN = booleanPreferencesKey("jellyfin_reload_on_open")
        val JELLYFIN_OPEN_EXTERNAL_LINKS = booleanPreferencesKey("jellyfin_open_external_links")
        val JELLYFIN_USERNAME = stringPreferencesKey("jellyfin_username")
        val JELLYFIN_AUTO_AUTH = booleanPreferencesKey("jellyfin_auto_auth")

        val HOME_ASSISTANT_ENABLED = booleanPreferencesKey("home_assistant_enabled")
        val HOME_ASSISTANT_URL = stringPreferencesKey("home_assistant_url")
        val HOME_ASSISTANT_USERNAME = stringPreferencesKey("home_assistant_username")
        val HOME_ASSISTANT_AUTO_AUTH = booleanPreferencesKey("home_assistant_auto_auth")

        val KODI_ENABLED = booleanPreferencesKey("kodi_enabled")
        val KODI_PACKAGE_NAME = stringPreferencesKey("kodi_package_name")
        val KODI_AUTO_LAUNCH_ON_OPEN = booleanPreferencesKey("kodi_auto_launch_on_open")

        val IPTV_ENABLED = booleanPreferencesKey("iptv_enabled")
        val IPTV_PORTAL_URL = stringPreferencesKey("iptv_portal_url")
        val IPTV_MAC_ADDRESS = stringPreferencesKey("iptv_mac_address")
        val IPTV_SLEEP_TIMEOUT_SECONDS = intPreferencesKey("iptv_sleep_timeout_seconds")
        val IPTV_VOLUME = floatPreferencesKey("iptv_volume")
        val IPTV_LAST_CHANNEL_ID = stringPreferencesKey("iptv_last_channel_id")
        val IPTV_PLAYER_BACKEND = stringPreferencesKey("iptv_player_backend")
        val IPTV_RECORDING_PORTAL_URL = stringPreferencesKey("iptv_recording_portal_url")
        val IPTV_RECORDING_MAC_ADDRESS = stringPreferencesKey("iptv_recording_mac_address")
        val PARENTAL_CONTROL_PIN = stringPreferencesKey("parental_control_pin")
        val PARENTAL_CONTROL_MODE = stringPreferencesKey("parental_control_mode")
        val IPTV_RECORDING_DESTINATION = stringPreferencesKey("iptv_recording_destination")
        val IPTV_RECORDING_SMB_FOLDER = stringPreferencesKey("iptv_recording_smb_folder")
        val IPTV_RECORDING_LOCAL_CAP_MB = intPreferencesKey("iptv_recording_local_cap_mb")
        val IPTV_DOWNLOAD_FOLDER_NAME = stringPreferencesKey("iptv_download_folder_name")
        val IPTV_MOVIES_VIEW_MODE = stringPreferencesKey("iptv_movies_view_mode")
        val IPTV_SERIES_VIEW_MODE = stringPreferencesKey("iptv_series_view_mode")
        val IPTV_SHOW_RECORDING_STATUS_PILL = booleanPreferencesKey("iptv_show_recording_status_pill")
        val IPTV_SCHEDULED_RECORDINGS = stringPreferencesKey("iptv_scheduled_recordings_json")
        val IPTV_RECORDING_HISTORY = stringPreferencesKey("iptv_recording_history_json")

        val CUSTOM_TEXT_WIDGETS = stringPreferencesKey("custom_text_widgets_json")
        val ICON_WIDGETS = stringPreferencesKey("icon_widgets_json")

        val LAUNCHER_FAVORITE_APPS = stringPreferencesKey("launcher_favorite_apps_json")
        val LAUNCHER_HIDDEN_APPS = stringSetPreferencesKey("launcher_hidden_apps")
        val LAST_VISITED_PAGE_INDEX = intPreferencesKey("last_visited_page_index")
        val DISPLAY_ORIENTATION_MODE = stringPreferencesKey("display_orientation_mode")
        val PAGE_SWIPE_REQUIRE_TWO_FINGERS = booleanPreferencesKey("page_swipe_require_two_fingers")

        val BRIGHTNESS_LEVEL_255 = intPreferencesKey("brightness_level_255")
        val BRIGHTNESS_EXTRA_DIM_PERCENT = intPreferencesKey("brightness_extra_dim_percent")
        val BRIGHTNESS_DIM_TARGET = stringPreferencesKey("brightness_dim_target")
        val NIGHT_CLOCK_BRIGHTNESS_LEVEL_255 = intPreferencesKey("night_clock_brightness_level_255")
        val NIGHT_CLOCK_TEXT_DIM_PERCENT = intPreferencesKey("night_clock_text_dim_percent")
        val NIGHT_CLOCK_ANCHOR_X = floatPreferencesKey("night_clock_anchor_x")
        val NIGHT_CLOCK_ANCHOR_Y = floatPreferencesKey("night_clock_anchor_y")
        val NIGHT_CLOCK_WEATHER_ANCHOR_X = floatPreferencesKey("night_clock_weather_anchor_x")
        val NIGHT_CLOCK_WEATHER_ANCHOR_Y = floatPreferencesKey("night_clock_weather_anchor_y")

        val PROVISIONING_APPLIED_ONCE = booleanPreferencesKey("provisioning_applied_once")
    }
}

/** Scoped mutation surface passed to [SettingsRepository.update] so call sites read as
 * `settings.update { clockFontSizeSp = 96 }` instead of a long list of individual setter
 * methods on the repository itself. */
class MirrorDashSettingsEditor internal constructor(private val prefs: androidx.datastore.preferences.core.MutablePreferences) {
    private val json = Json { ignoreUnknownKeys = true }
    private val defaults = MirrorDashSettings()

    var deviceName: String by PrefDelegate(SettingsRepository.Keys.DEVICE_NAME, prefs, defaults.deviceName)

    var clockFontSizeSp: Int by PrefDelegate(SettingsRepository.Keys.CLOCK_FONT_SIZE_SP, prefs, defaults.clockFontSizeSp)
    var clockFontId: String by PrefDelegate(SettingsRepository.Keys.CLOCK_FONT_ID, prefs, defaults.clockFontId)
    var clockStyleId: String by PrefDelegate(SettingsRepository.Keys.CLOCK_STYLE_ID, prefs, defaults.clockStyleId)
    var clockTextColorArgb: Int by PrefDelegate(SettingsRepository.Keys.CLOCK_TEXT_COLOR, prefs, defaults.clockTextColorArgb)
    var clockAccentColorArgb: Int by PrefDelegate(SettingsRepository.Keys.CLOCK_ACCENT_COLOR, prefs, defaults.clockAccentColorArgb)
    var clockBackgroundColorArgb: Int by PrefDelegate(SettingsRepository.Keys.CLOCK_BACKGROUND_COLOR, prefs, defaults.clockBackgroundColorArgb)
    var clockBackgroundMode: String by PrefDelegate(SettingsRepository.Keys.CLOCK_BACKGROUND_MODE, prefs, defaults.clockBackgroundMode)
    var clockAnchorX: Float by PrefDelegate(SettingsRepository.Keys.CLOCK_ANCHOR_X, prefs, defaults.clockAnchorX)
    var clockAnchorY: Float by PrefDelegate(SettingsRepository.Keys.CLOCK_ANCHOR_Y, prefs, defaults.clockAnchorY)
    var clockRotationDegrees: Float by PrefDelegate(SettingsRepository.Keys.CLOCK_ROTATION_DEGREES, prefs, defaults.clockRotationDegrees)
    var weatherAnchorX: Float by PrefDelegate(SettingsRepository.Keys.WEATHER_ANCHOR_X, prefs, defaults.weatherAnchorX)
    var weatherAnchorY: Float by PrefDelegate(SettingsRepository.Keys.WEATHER_ANCHOR_Y, prefs, defaults.weatherAnchorY)
    var weatherIconStyle: String by PrefDelegate(SettingsRepository.Keys.WEATHER_ICON_STYLE, prefs, defaults.weatherIconStyle)

    var weatherEnabled: Boolean by PrefDelegate(SettingsRepository.Keys.WEATHER_ENABLED, prefs, defaults.weatherEnabled)
    var weatherLocationQuery: String by PrefDelegate(SettingsRepository.Keys.WEATHER_LOCATION_QUERY, prefs, defaults.weatherLocationQuery)
    var weatherLocationLabel: String by PrefDelegate(SettingsRepository.Keys.WEATHER_LOCATION_LABEL, prefs, defaults.weatherLocationLabel)
    var weatherLatitude: String by PrefDelegate(SettingsRepository.Keys.WEATHER_LATITUDE, prefs, defaults.weatherLatitude)
    var weatherLongitude: String by PrefDelegate(SettingsRepository.Keys.WEATHER_LONGITUDE, prefs, defaults.weatherLongitude)
    var weatherUseFahrenheit: Boolean by PrefDelegate(SettingsRepository.Keys.WEATHER_USE_FAHRENHEIT, prefs, defaults.weatherUseFahrenheit)

    var photoramaEnabled: Boolean by PrefDelegate(SettingsRepository.Keys.PHOTORAMA_ENABLED, prefs, defaults.photoramaEnabled)
    var photoramaSource: String by PrefDelegate(SettingsRepository.Keys.PHOTORAMA_SOURCE, prefs, defaults.photoramaSource)
    var photoramaFolderPath: String by PrefDelegate(SettingsRepository.Keys.PHOTORAMA_FOLDER_PATH, prefs, defaults.photoramaFolderPath)
    var photoramaLocalFolderUri: String by PrefDelegate(SettingsRepository.Keys.PHOTORAMA_LOCAL_FOLDER_URI, prefs, defaults.photoramaLocalFolderUri)
    var photoramaIncludeSubfolders: Boolean by PrefDelegate(SettingsRepository.Keys.PHOTORAMA_INCLUDE_SUBFOLDERS, prefs, defaults.photoramaIncludeSubfolders)
    var photoramaPlayLivePhotos: Boolean by PrefDelegate(SettingsRepository.Keys.PHOTORAMA_PLAY_LIVE_PHOTOS, prefs, defaults.photoramaPlayLivePhotos)
    var photoramaIntervalSeconds: Int by PrefDelegate(SettingsRepository.Keys.PHOTORAMA_INTERVAL_SECONDS, prefs, defaults.photoramaIntervalSeconds)
    var photoramaShuffle: Boolean by PrefDelegate(SettingsRepository.Keys.PHOTORAMA_SHUFFLE, prefs, defaults.photoramaShuffle)
    var photoramaReplayLongVideoSegments: Boolean by PrefDelegate(SettingsRepository.Keys.PHOTORAMA_REPLAY_LONG_VIDEO_SEGMENTS, prefs, defaults.photoramaReplayLongVideoSegments)
    var photoramaMatchOrientation: Boolean by PrefDelegate(SettingsRepository.Keys.PHOTORAMA_MATCH_ORIENTATION, prefs, defaults.photoramaMatchOrientation)
    var photoramaVideoOnly: Boolean by PrefDelegate(SettingsRepository.Keys.PHOTORAMA_VIDEO_ONLY, prefs, defaults.photoramaVideoOnly)
    var photoramaShowImageCount: Boolean by PrefDelegate(SettingsRepository.Keys.PHOTORAMA_SHOW_IMAGE_COUNT, prefs, defaults.photoramaShowImageCount)
    var photoramaImageCountFontSizeSp: Int by PrefDelegate(SettingsRepository.Keys.PHOTORAMA_IMAGE_COUNT_FONT_SIZE_SP, prefs, defaults.photoramaImageCountFontSizeSp)
    var photoramaImageCountFontId: String by PrefDelegate(SettingsRepository.Keys.PHOTORAMA_IMAGE_COUNT_FONT_ID, prefs, defaults.photoramaImageCountFontId)
    var photoramaImageCountColorArgb: Int by PrefDelegate(SettingsRepository.Keys.PHOTORAMA_IMAGE_COUNT_COLOR, prefs, defaults.photoramaImageCountColorArgb)
    var photoramaImageCountAnchorX: Float by PrefDelegate(SettingsRepository.Keys.PHOTORAMA_IMAGE_COUNT_ANCHOR_X, prefs, defaults.photoramaImageCountAnchorX)
    var photoramaImageCountAnchorY: Float by PrefDelegate(SettingsRepository.Keys.PHOTORAMA_IMAGE_COUNT_ANCHOR_Y, prefs, defaults.photoramaImageCountAnchorY)
    var photoramaImageCountRotationDegrees: Float by PrefDelegate(SettingsRepository.Keys.PHOTORAMA_IMAGE_COUNT_ROTATION_DEGREES, prefs, defaults.photoramaImageCountRotationDegrees)
    var photoramaVideosMuted: Boolean by PrefDelegate(SettingsRepository.Keys.PHOTORAMA_VIDEOS_MUTED, prefs, defaults.photoramaVideosMuted)
    var photoramaCacheSizeMb: Int by PrefDelegate(SettingsRepository.Keys.PHOTORAMA_CACHE_SIZE_MB, prefs, defaults.photoramaCacheSizeMb)
    var photoramaScheduleEnabled: Boolean by PrefDelegate(SettingsRepository.Keys.PHOTORAMA_SCHEDULE_ENABLED, prefs, defaults.photoramaScheduleEnabled)
    var photoramaScheduleStartMinutes: Int by PrefDelegate(SettingsRepository.Keys.PHOTORAMA_SCHEDULE_START_MINUTES, prefs, defaults.photoramaScheduleStartMinutes)
    var photoramaScheduleEndMinutes: Int by PrefDelegate(SettingsRepository.Keys.PHOTORAMA_SCHEDULE_END_MINUTES, prefs, defaults.photoramaScheduleEndMinutes)

    var clockShowTime: Boolean by PrefDelegate(SettingsRepository.Keys.CLOCK_SHOW_TIME, prefs, defaults.clockShowTime)
    var clockShowWidgets: Boolean by PrefDelegate(SettingsRepository.Keys.CLOCK_SHOW_WIDGETS, prefs, defaults.clockShowWidgets)

    var walkieTalkieEnabled: Boolean by PrefDelegate(SettingsRepository.Keys.WALKIE_TALKIE_ENABLED, prefs, defaults.walkieTalkieEnabled)
    var walkieTalkieHomeShortcutIps: Set<String> by PrefDelegate(
        SettingsRepository.Keys.WALKIE_TALKIE_HOME_SHORTCUT_IPS,
        prefs,
        defaults.walkieTalkieHomeShortcutIps,
    )
    var walkieTalkieShowTalkToAllButton: Boolean by PrefDelegate(
        SettingsRepository.Keys.WALKIE_TALKIE_SHOW_TALK_TO_ALL_BUTTON,
        prefs,
        defaults.walkieTalkieShowTalkToAllButton,
    )
    var walkieTalkieRoomShortcutIconSizePx: Int by PrefDelegate(
        SettingsRepository.Keys.WALKIE_TALKIE_ROOM_SHORTCUT_ICON_SIZE_PX,
        prefs,
        defaults.walkieTalkieRoomShortcutIconSizePx,
    )
    var walkieTalkieTarget: String by PrefDelegate(SettingsRepository.Keys.WALKIE_TALKIE_TARGET, prefs, defaults.walkieTalkieTarget)
    var walkieTalkiePort: Int by PrefDelegate(SettingsRepository.Keys.WALKIE_TALKIE_PORT, prefs, defaults.walkieTalkiePort)
    var walkieTalkieMicBoostPercent: Int by PrefDelegate(SettingsRepository.Keys.WALKIE_TALKIE_MIC_BOOST, prefs, defaults.walkieTalkieMicBoostPercent)
    var walkieTalkieIncomingChimeEnabled: Boolean by PrefDelegate(SettingsRepository.Keys.WALKIE_TALKIE_INCOMING_CHIME_ENABLED, prefs, defaults.walkieTalkieIncomingChimeEnabled)
    var walkieTalkieIncomingChime: String by PrefDelegate(SettingsRepository.Keys.WALKIE_TALKIE_INCOMING_CHIME, prefs, defaults.walkieTalkieIncomingChime)
    var walkieTalkieOverlayEnabled: Boolean by PrefDelegate(SettingsRepository.Keys.WALKIE_TALKIE_OVERLAY_ENABLED, prefs, defaults.walkieTalkieOverlayEnabled)
    var walkieTalkiePttAnchorX: Float by PrefDelegate(SettingsRepository.Keys.WALKIE_TALKIE_PTT_ANCHOR_X, prefs, defaults.walkieTalkiePttAnchorX)
    var walkieTalkiePttAnchorY: Float by PrefDelegate(SettingsRepository.Keys.WALKIE_TALKIE_PTT_ANCHOR_Y, prefs, defaults.walkieTalkiePttAnchorY)
    var walkieTalkieAutoAddDiscovered: Boolean by PrefDelegate(SettingsRepository.Keys.WALKIE_TALKIE_AUTO_ADD_DISCOVERED, prefs, defaults.walkieTalkieAutoAddDiscovered)

    var airplayEnabled: Boolean by PrefDelegate(SettingsRepository.Keys.AIRPLAY_ENABLED, prefs, defaults.airplayEnabled)
    var airplayHwAddressHex: String by PrefDelegate(SettingsRepository.Keys.AIRPLAY_HW_ADDRESS_HEX, prefs, defaults.airplayHwAddressHex)
    var airplayAuthMode: String by PrefDelegate(SettingsRepository.Keys.AIRPLAY_AUTH_MODE, prefs, defaults.airplayAuthMode)
    var airplayPassword: String by PrefDelegate(SettingsRepository.Keys.AIRPLAY_PASSWORD, prefs, defaults.airplayPassword)
    var airplayMirrorPreset: String by PrefDelegate(SettingsRepository.Keys.AIRPLAY_MIRROR_PRESET, prefs, defaults.airplayMirrorPreset)
    var airplayAllowHevc: Boolean by PrefDelegate(SettingsRepository.Keys.AIRPLAY_ALLOW_HEVC, prefs, defaults.airplayAllowHevc)
    var airplayShowClockWidget: Boolean by PrefDelegate(SettingsRepository.Keys.AIRPLAY_SHOW_CLOCK_WIDGET, prefs, defaults.airplayShowClockWidget)
    var rtspCameraEnabled: Boolean by PrefDelegate(SettingsRepository.Keys.RTSP_CAMERA_ENABLED, prefs, defaults.rtspCameraEnabled)
    var rtspAllowedClientIps: String by PrefDelegate(SettingsRepository.Keys.RTSP_ALLOWED_CLIENT_IPS, prefs, defaults.rtspAllowedClientIps)
    var rtspQuality: String by PrefDelegate(SettingsRepository.Keys.RTSP_QUALITY, prefs, defaults.rtspQuality)

    var browserEnabled: Boolean by PrefDelegate(SettingsRepository.Keys.BROWSER_ENABLED, prefs, defaults.browserEnabled)
    var browserHomeUrl: String by PrefDelegate(SettingsRepository.Keys.BROWSER_HOME_URL, prefs, defaults.browserHomeUrl)
    var browserLastVisitedUrl: String by PrefDelegate(SettingsRepository.Keys.BROWSER_LAST_VISITED_URL, prefs, defaults.browserLastVisitedUrl)
    var gymEnabled: Boolean by PrefDelegate(SettingsRepository.Keys.GYM_ENABLED, prefs, defaults.gymEnabled)

    var jellyfinEnabled: Boolean by PrefDelegate(SettingsRepository.Keys.JELLYFIN_ENABLED, prefs, defaults.jellyfinEnabled)
    var jellyfinServerUrl: String by PrefDelegate(SettingsRepository.Keys.JELLYFIN_SERVER_URL, prefs, defaults.jellyfinServerUrl)
    var jellyfinStartPath: String by PrefDelegate(SettingsRepository.Keys.JELLYFIN_START_PATH, prefs, defaults.jellyfinStartPath)
    var jellyfinDesktopMode: Boolean by PrefDelegate(SettingsRepository.Keys.JELLYFIN_DESKTOP_MODE, prefs, defaults.jellyfinDesktopMode)
    var jellyfinReloadOnOpen: Boolean by PrefDelegate(SettingsRepository.Keys.JELLYFIN_RELOAD_ON_OPEN, prefs, defaults.jellyfinReloadOnOpen)
    var jellyfinOpenExternalLinks: Boolean by PrefDelegate(SettingsRepository.Keys.JELLYFIN_OPEN_EXTERNAL_LINKS, prefs, defaults.jellyfinOpenExternalLinks)
    var jellyfinUsername: String by PrefDelegate(SettingsRepository.Keys.JELLYFIN_USERNAME, prefs, defaults.jellyfinUsername)
    var jellyfinAutoAuth: Boolean by PrefDelegate(SettingsRepository.Keys.JELLYFIN_AUTO_AUTH, prefs, defaults.jellyfinAutoAuth)

    var homeAssistantEnabled: Boolean by PrefDelegate(SettingsRepository.Keys.HOME_ASSISTANT_ENABLED, prefs, defaults.homeAssistantEnabled)
    var homeAssistantUrl: String by PrefDelegate(SettingsRepository.Keys.HOME_ASSISTANT_URL, prefs, defaults.homeAssistantUrl)
    var homeAssistantUsername: String by PrefDelegate(SettingsRepository.Keys.HOME_ASSISTANT_USERNAME, prefs, defaults.homeAssistantUsername)
    var homeAssistantAutoAuth: Boolean by PrefDelegate(SettingsRepository.Keys.HOME_ASSISTANT_AUTO_AUTH, prefs, defaults.homeAssistantAutoAuth)

    var kodiEnabled: Boolean by PrefDelegate(SettingsRepository.Keys.KODI_ENABLED, prefs, defaults.kodiEnabled)
    var kodiPackageName: String by PrefDelegate(SettingsRepository.Keys.KODI_PACKAGE_NAME, prefs, defaults.kodiPackageName)
    var kodiAutoLaunchOnOpen: Boolean by PrefDelegate(SettingsRepository.Keys.KODI_AUTO_LAUNCH_ON_OPEN, prefs, defaults.kodiAutoLaunchOnOpen)

    var iptvEnabled: Boolean by PrefDelegate(SettingsRepository.Keys.IPTV_ENABLED, prefs, defaults.iptvEnabled)
    var iptvPortalUrl: String by PrefDelegate(SettingsRepository.Keys.IPTV_PORTAL_URL, prefs, defaults.iptvPortalUrl)
    var iptvMacAddress: String by PrefDelegate(SettingsRepository.Keys.IPTV_MAC_ADDRESS, prefs, defaults.iptvMacAddress)
    var iptvSleepTimeoutSeconds: Int by PrefDelegate(SettingsRepository.Keys.IPTV_SLEEP_TIMEOUT_SECONDS, prefs, defaults.iptvSleepTimeoutSeconds)
    var iptvVolume: Float by PrefDelegate(SettingsRepository.Keys.IPTV_VOLUME, prefs, defaults.iptvVolume)
    var iptvLastChannelId: String by PrefDelegate(SettingsRepository.Keys.IPTV_LAST_CHANNEL_ID, prefs, defaults.iptvLastChannelId)
    var iptvPlayerBackend: String by PrefDelegate(SettingsRepository.Keys.IPTV_PLAYER_BACKEND, prefs, defaults.iptvPlayerBackend)
    var iptvRecordingPortalUrl: String by PrefDelegate(SettingsRepository.Keys.IPTV_RECORDING_PORTAL_URL, prefs, defaults.iptvRecordingPortalUrl)
    var iptvRecordingMacAddress: String by PrefDelegate(SettingsRepository.Keys.IPTV_RECORDING_MAC_ADDRESS, prefs, defaults.iptvRecordingMacAddress)
    var parentalControlPin: String by PrefDelegate(SettingsRepository.Keys.PARENTAL_CONTROL_PIN, prefs, defaults.parentalControlPin)
    var parentalControlMode: String by PrefDelegate(SettingsRepository.Keys.PARENTAL_CONTROL_MODE, prefs, defaults.parentalControlMode)
    var iptvRecordingDestination: String by PrefDelegate(SettingsRepository.Keys.IPTV_RECORDING_DESTINATION, prefs, defaults.iptvRecordingDestination)
    var iptvRecordingSmbFolder: String by PrefDelegate(SettingsRepository.Keys.IPTV_RECORDING_SMB_FOLDER, prefs, defaults.iptvRecordingSmbFolder)
    var iptvRecordingLocalCapMb: Int by PrefDelegate(SettingsRepository.Keys.IPTV_RECORDING_LOCAL_CAP_MB, prefs, defaults.iptvRecordingLocalCapMb)
    var iptvDownloadFolderName: String by PrefDelegate(SettingsRepository.Keys.IPTV_DOWNLOAD_FOLDER_NAME, prefs, defaults.iptvDownloadFolderName)
    var iptvMoviesViewMode: String by PrefDelegate(SettingsRepository.Keys.IPTV_MOVIES_VIEW_MODE, prefs, defaults.iptvMoviesViewMode)
    var iptvSeriesViewMode: String by PrefDelegate(SettingsRepository.Keys.IPTV_SERIES_VIEW_MODE, prefs, defaults.iptvSeriesViewMode)
    var iptvShowRecordingStatusPill: Boolean by PrefDelegate(SettingsRepository.Keys.IPTV_SHOW_RECORDING_STATUS_PILL, prefs, defaults.iptvShowRecordingStatusPill)

    var launcherHiddenApps: Set<String> by PrefDelegate(SettingsRepository.Keys.LAUNCHER_HIDDEN_APPS, prefs, defaults.launcherHiddenApps)
    var lastVisitedPageIndex: Int by PrefDelegate(SettingsRepository.Keys.LAST_VISITED_PAGE_INDEX, prefs, defaults.lastVisitedPageIndex)
    var displayOrientationMode: String by PrefDelegate(SettingsRepository.Keys.DISPLAY_ORIENTATION_MODE, prefs, defaults.displayOrientationMode)
    var pageSwipeRequireTwoFingers: Boolean by PrefDelegate(
        SettingsRepository.Keys.PAGE_SWIPE_REQUIRE_TWO_FINGERS,
        prefs,
        defaults.pageSwipeRequireTwoFingers,
    )

    var brightnessLevel255: Int by PrefDelegate(SettingsRepository.Keys.BRIGHTNESS_LEVEL_255, prefs, defaults.brightnessLevel255)
    var brightnessExtraDimPercent: Int by PrefDelegate(SettingsRepository.Keys.BRIGHTNESS_EXTRA_DIM_PERCENT, prefs, defaults.brightnessExtraDimPercent)
    var brightnessDimTarget: String by PrefDelegate(SettingsRepository.Keys.BRIGHTNESS_DIM_TARGET, prefs, defaults.brightnessDimTarget)
    var nightClockBrightnessLevel255: Int by PrefDelegate(SettingsRepository.Keys.NIGHT_CLOCK_BRIGHTNESS_LEVEL_255, prefs, defaults.nightClockBrightnessLevel255)
    var nightClockTextDimPercent: Int by PrefDelegate(SettingsRepository.Keys.NIGHT_CLOCK_TEXT_DIM_PERCENT, prefs, defaults.nightClockTextDimPercent)
    var nightClockAnchorX: Float by PrefDelegate(SettingsRepository.Keys.NIGHT_CLOCK_ANCHOR_X, prefs, defaults.nightClockAnchorX)
    var nightClockAnchorY: Float by PrefDelegate(SettingsRepository.Keys.NIGHT_CLOCK_ANCHOR_Y, prefs, defaults.nightClockAnchorY)
    var nightClockWeatherAnchorX: Float by PrefDelegate(SettingsRepository.Keys.NIGHT_CLOCK_WEATHER_ANCHOR_X, prefs, defaults.nightClockWeatherAnchorX)
    var nightClockWeatherAnchorY: Float by PrefDelegate(SettingsRepository.Keys.NIGHT_CLOCK_WEATHER_ANCHOR_Y, prefs, defaults.nightClockWeatherAnchorY)

    var provisioningAppliedOnce: Boolean by PrefDelegate(SettingsRepository.Keys.PROVISIONING_APPLIED_ONCE, prefs, defaults.provisioningAppliedOnce)

    var walkieTalkiePeers: List<WalkieTalkiePeer>
        get() = prefs[SettingsRepository.Keys.WALKIE_TALKIE_PEERS]
            ?.let { runCatching { json.decodeFromString<List<WalkieTalkiePeer>>(it) }.getOrNull() }
            .orEmpty()
        set(value) {
            prefs[SettingsRepository.Keys.WALKIE_TALKIE_PEERS] = json.encodeToString(value)
        }

    var launcherFavoriteApps: List<String>
        get() = prefs[SettingsRepository.Keys.LAUNCHER_FAVORITE_APPS]
            ?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }
            .orEmpty()
        set(value) {
            prefs[SettingsRepository.Keys.LAUNCHER_FAVORITE_APPS] = json.encodeToString(value)
        }

    var weatherWidgets: List<WeatherWidget>
        get() = prefs[SettingsRepository.Keys.WEATHER_WIDGETS]
            ?.let { runCatching { json.decodeFromString<List<WeatherWidget>>(it) }.getOrNull() }
            .orEmpty()
        set(value) {
            prefs[SettingsRepository.Keys.WEATHER_WIDGETS] = json.encodeToString(value)
        }

    var customTextWidgets: List<CustomTextWidget>
        get() = prefs[SettingsRepository.Keys.CUSTOM_TEXT_WIDGETS]
            ?.let { runCatching { json.decodeFromString<List<CustomTextWidget>>(it) }.getOrNull() }
            .orEmpty()
        set(value) {
            prefs[SettingsRepository.Keys.CUSTOM_TEXT_WIDGETS] = json.encodeToString(value)
        }

    var iconWidgets: List<IconWidget>
        get() = prefs[SettingsRepository.Keys.ICON_WIDGETS]
            ?.let { runCatching { json.decodeFromString<List<IconWidget>>(it) }.getOrNull() }
            .orEmpty()
        set(value) {
            prefs[SettingsRepository.Keys.ICON_WIDGETS] = json.encodeToString(value)
        }

    var downloadedClockFonts: List<DownloadedClockFont>
        get() = prefs[SettingsRepository.Keys.DOWNLOADED_CLOCK_FONTS]
            ?.let { runCatching { json.decodeFromString<List<DownloadedClockFont>>(it) }.getOrNull() }
            .orEmpty()
        set(value) {
            prefs[SettingsRepository.Keys.DOWNLOADED_CLOCK_FONTS] = json.encodeToString(value)
        }

    var gymFeatureSettings: GymFeatureSettings
        get() = prefs[SettingsRepository.Keys.GYM_FEATURE_SETTINGS]
            ?.let { runCatching { json.decodeFromString<GymFeatureSettings>(it) }.getOrNull() }
            ?: defaults.gymFeatureSettings
        set(value) {
            prefs[SettingsRepository.Keys.GYM_FEATURE_SETTINGS] = json.encodeToString(value)
        }

    var gymProfiles: List<GymProfile>
        get() = prefs[SettingsRepository.Keys.GYM_PROFILES]
            ?.let { runCatching { json.decodeFromString<List<GymProfile>>(it) }.getOrNull() }
            ?: defaults.gymProfiles
        set(value) {
            prefs[SettingsRepository.Keys.GYM_PROFILES] = json.encodeToString(value)
        }

    var gymDevicePreferences: List<FitnessDevicePreference>
        get() = prefs[SettingsRepository.Keys.GYM_DEVICE_PREFERENCES]
            ?.let { runCatching { json.decodeFromString<List<FitnessDevicePreference>>(it) }.getOrNull() }
            ?: defaults.gymDevicePreferences
        set(value) {
            prefs[SettingsRepository.Keys.GYM_DEVICE_PREFERENCES] = json.encodeToString(value)
        }

    var gymSessionHistory: List<GymSessionRecord>
        get() = prefs[SettingsRepository.Keys.GYM_SESSION_HISTORY]
            ?.let { runCatching { json.decodeFromString<List<GymSessionRecord>>(it) }.getOrNull() }
            ?: defaults.gymSessionHistory
        set(value) {
            prefs[SettingsRepository.Keys.GYM_SESSION_HISTORY] = json.encodeToString(value)
        }

    var calendarWidgets: List<CalendarWidget>
        get() = prefs[SettingsRepository.Keys.CALENDAR_WIDGETS]
            ?.let { runCatching { json.decodeFromString<List<CalendarWidget>>(it) }.getOrNull() }
            .orEmpty()
        set(value) {
            prefs[SettingsRepository.Keys.CALENDAR_WIDGETS] = json.encodeToString(value)
        }

    var tasksWidgets: List<TasksWidget>
        get() = prefs[SettingsRepository.Keys.TASKS_WIDGETS]
            ?.let { runCatching { json.decodeFromString<List<TasksWidget>>(it) }.getOrNull() }
            .orEmpty()
        set(value) {
            prefs[SettingsRepository.Keys.TASKS_WIDGETS] = json.encodeToString(value)
        }

    var stocksWidgets: List<StocksWidget>
        get() = prefs[SettingsRepository.Keys.STOCKS_WIDGETS]
            ?.let { runCatching { json.decodeFromString<List<StocksWidget>>(it) }.getOrNull() }
            .orEmpty()
        set(value) {
            prefs[SettingsRepository.Keys.STOCKS_WIDGETS] = json.encodeToString(value)
        }

    var newsWidgets: List<NewsWidget>
        get() = prefs[SettingsRepository.Keys.NEWS_WIDGETS]
            ?.let { runCatching { json.decodeFromString<List<NewsWidget>>(it) }.getOrNull() }
            .orEmpty()
        set(value) {
            prefs[SettingsRepository.Keys.NEWS_WIDGETS] = json.encodeToString(value)
        }

    var iptvScheduledRecordings: List<com.sconcept.mirrordash.iptv.ScheduledRecording>
        get() = prefs[SettingsRepository.Keys.IPTV_SCHEDULED_RECORDINGS]
            ?.let { runCatching { json.decodeFromString<List<com.sconcept.mirrordash.iptv.ScheduledRecording>>(it) }.getOrNull() }
            .orEmpty()
        set(value) {
            prefs[SettingsRepository.Keys.IPTV_SCHEDULED_RECORDINGS] = json.encodeToString(value)
        }

    var iptvRecordingHistory: List<CompletedRecording>
        get() = prefs[SettingsRepository.Keys.IPTV_RECORDING_HISTORY]
            ?.let { runCatching { json.decodeFromString<List<CompletedRecording>>(it) }.getOrNull() }
            .orEmpty()
        set(value) {
            prefs[SettingsRepository.Keys.IPTV_RECORDING_HISTORY] = json.encodeToString(value)
        }
}

private class PrefDelegate<T>(
    private val key: Preferences.Key<T>,
    private val prefs: androidx.datastore.preferences.core.MutablePreferences,
    private val default: T,
) {
    operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): T =
        prefs[key] ?: default

    operator fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: T) {
        prefs[key] = value
    }
}
