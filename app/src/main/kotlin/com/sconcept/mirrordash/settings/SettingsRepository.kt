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
import com.sconcept.mirrordash.clock.CustomTextWidget
import com.sconcept.mirrordash.clock.WeatherWidget
import com.sconcept.mirrordash.clock.defaultWeatherWidget
import com.sconcept.mirrordash.iptv.DEFAULT_PARENTAL_CONTROL_PIN
import com.sconcept.mirrordash.iptv.ParentalControlMode
import com.sconcept.mirrordash.iptv.RecordingDestinationMode
import com.sconcept.mirrordash.iptv.ScheduledRecording
import com.sconcept.mirrordash.nas.model.SmbShare
import com.sconcept.mirrordash.security.SecureCredentialStore
import com.sconcept.mirrordash.security.SessionCredentialHolder
import com.sconcept.mirrordash.ui.theme.DEFAULT_CLOCK_FONT_SIZE_SP
import com.sconcept.mirrordash.walkietalkie.DEFAULT_WALKIE_TALKIE_CHIME
import com.sconcept.mirrordash.walkietalkie.model.WalkieTalkiePeer
import com.sconcept.mirrordash.wedding.DEFAULT_WEDDING_IDLE_TIMEOUT_SECONDS
import com.sconcept.mirrordash.wedding.WeddingProfile
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

const val AIRPLAY_AUTH_MODE_OPEN = "open"
const val AIRPLAY_AUTH_MODE_RANDOM_PIN = "random_pin"
const val AIRPLAY_AUTH_MODE_PASSWORD = "password"
const val AIRPLAY_MIRROR_PRESET_DISPLAY = "display"
const val AIRPLAY_MIRROR_PRESET_720P = "720p"
const val AIRPLAY_MIRROR_PRESET_1080P = "1080p"
const val AIRPLAY_MIRROR_PRESET_4K = "4k"
const val DEFAULT_DEVICE_NAME = "MirrorDash"
const val DEFAULT_MIRRORDROP_PORT = 8765

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
    val clockTextColorArgb: Int = 0xFFF5F3EF.toInt(),
    val clockBackgroundColorArgb: Int = 0xFF0B0C0E.toInt(),
    val clockBackgroundMode: String = CLOCK_BACKGROUND_MODE_SOLID,
    val clockAnchorX: Float = DEFAULT_CLOCK_ANCHOR_X,
    val clockAnchorY: Float = DEFAULT_CLOCK_ANCHOR_Y,
    val weatherAnchorX: Float = DEFAULT_WEATHER_ANCHOR_X,
    val weatherAnchorY: Float = DEFAULT_WEATHER_ANCHOR_Y,
    val weatherWidgets: List<WeatherWidget> = emptyList(),
    val customTextWidgets: List<CustomTextWidget> = emptyList(),

    // Weather
    val weatherEnabled: Boolean = true,
    val weatherLocationQuery: String = "",
    val weatherLocationLabel: String = "",
    val weatherLatitude: String = "",
    val weatherLongitude: String = "",
    val weatherUseFahrenheit: Boolean = false,

    // NAS connection
    val smbHost: String = "",
    val smbShareName: String = "",
    val smbUsername: String = "",
    val smbDomain: String = "",
    val smbRememberConnection: Boolean = false,

    // Photorama
    val photoramaEnabled: Boolean = false,
    val photoramaFolderPath: String = "",
    val photoramaIncludeSubfolders: Boolean = false,
    val photoramaIntervalSeconds: Int = 60,
    val photoramaShuffle: Boolean = true,
    val photoramaCacheSizeMb: Int = 250,

    // Walkie-Talkie
    val walkieTalkieEnabled: Boolean = false,
    val walkieTalkiePeers: List<WalkieTalkiePeer> = emptyList(),
    val walkieTalkieTarget: String = WALKIE_TALKIE_TARGET_ALL,
    val walkieTalkiePort: Int = DEFAULT_WALKIE_TALKIE_PORT,
    val walkieTalkieMicBoostPercent: Int = 100,
    val walkieTalkieIncomingChimeEnabled: Boolean = true,
    val walkieTalkieIncomingChime: String = DEFAULT_WALKIE_TALKIE_CHIME,
    val walkieTalkieOverlayEnabled: Boolean = false,
    val walkieTalkiePttAnchorX: Float = 0.92f,
    val walkieTalkiePttAnchorY: Float = 0.82f,

    // AirPlay
    val airplayEnabled: Boolean = false,
    val airplayHwAddressHex: String = "",
    val airplayAuthMode: String = AIRPLAY_AUTH_MODE_OPEN,
    val airplayPassword: String = "",
    val airplayMirrorPreset: String = AIRPLAY_MIRROR_PRESET_DISPLAY,
    val airplayAllowHevc: Boolean = false,
    val airplayShowClockWidget: Boolean = false,

    // Browser
    val browserEnabled: Boolean = false,
    val browserHomeUrl: String = "",
    val browserLastVisitedUrl: String = "",

    // Jellyfin
    val jellyfinEnabled: Boolean = false,
    val jellyfinServerUrl: String = "",
    val jellyfinStartPath: String = "",
    val jellyfinDesktopMode: Boolean = false,
    val jellyfinReloadOnOpen: Boolean = false,
    val jellyfinOpenExternalLinks: Boolean = true,

    // Home Assistant
    val homeAssistantEnabled: Boolean = false,
    val homeAssistantUrl: String = "",

    // Photobooth (see the photobooth package) - camera capture + local sharing are two separate
    // concerns (brief §40): this only gates the tab/camera, MirrorDrop has its own enable switch.
    val photoboothEnabled: Boolean = false,

    // MirrorDrop (see the mirrordrop package) - local Snapdrop-style sharing. Its own enable
    // switch, independent of photoboothEnabled, so other future features can drive it too.
    val mirrorDropEnabled: Boolean = false,
    val mirrorDropPort: Int = DEFAULT_MIRRORDROP_PORT,

    // Wedding Mode is a persisted launcher state. Its host PIN is deliberately stored outside
    // DataStore by WeddingPinStore so this otherwise-readable preferences file never contains it.
    val weddingModeEnabled: Boolean = false,
    val weddingPartnerOne: String = "",
    val weddingPartnerTwo: String = "",
    val weddingDateText: String = "",
    val weddingLocation: String = "",
    val weddingWelcomeMessage: String = "Welcome to our wedding",
    val weddingIdleTimeoutSeconds: Int = DEFAULT_WEDDING_IDLE_TIMEOUT_SECONDS,

    // IPTV (Stalker/Ministra portal - see the iptv package)
    val iptvEnabled: Boolean = false,
    val iptvPortalUrl: String = "",
    val iptvMacAddress: String = "",
    val iptvSleepTimeoutSeconds: Int = DEFAULT_IPTV_SLEEP_TIMEOUT_SECONDS,
    // Remembered across app restarts so reopening the tab resumes where it left off, rather than
    // always full volume / the first channel - see IptvViewModel's connect flow.
    val iptvVolume: Float = 1f,
    val iptvLastChannelId: String = "",
    // "Always open on mute first" - overrides iptvVolume as the *starting* volume on connect
    // without touching the remembered value itself, so muting-by-default doesn't clobber what
    // gets restored the next time this is turned back off.
    val iptvOpenMuted: Boolean = false,
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
    val iptvScheduledRecordings: List<ScheduledRecording> = emptyList(),

    // Launcher
    val launcherFavoriteApps: List<String> = emptyList(),
    val launcherHiddenApps: Set<String> = emptySet(),
    val lastVisitedPageIndex: Int = 0,
    val displayOrientationMode: String = "AUTO",

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
) {
    val weddingProfile: WeddingProfile
        get() = WeddingProfile(
            partnerOne = weddingPartnerOne,
            partnerTwo = weddingPartnerTwo,
            dateText = weddingDateText,
            location = weddingLocation,
            welcomeMessage = weddingWelcomeMessage,
            idleTimeoutSeconds = weddingIdleTimeoutSeconds,
        )

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
            secureCredentialStore.loadCredentials() ?: ""
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
            secureCredentialStore.saveCredentials(share.password)
            SessionCredentialHolder.smbPassword = null
        } else {
            secureCredentialStore.deleteCredentials()
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
        secureCredentialStore.deleteCredentials()
        SessionCredentialHolder.smbPassword = null
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
        val scheduledRecordingsRaw = this[Keys.IPTV_SCHEDULED_RECORDINGS]
        val scheduledRecordings = scheduledRecordingsRaw?.let { raw ->
            runCatching { json.decodeFromString<List<ScheduledRecording>>(raw) }.getOrDefault(emptyList())
        } ?: emptyList()

        return MirrorDashSettings(
            deviceName = this[Keys.DEVICE_NAME] ?: defaults.deviceName,
            clockFontSizeSp = this[Keys.CLOCK_FONT_SIZE_SP] ?: defaults.clockFontSizeSp,
            clockTextColorArgb = this[Keys.CLOCK_TEXT_COLOR] ?: defaults.clockTextColorArgb,
            clockBackgroundColorArgb = this[Keys.CLOCK_BACKGROUND_COLOR] ?: defaults.clockBackgroundColorArgb,
            clockBackgroundMode = this[Keys.CLOCK_BACKGROUND_MODE] ?: defaults.clockBackgroundMode,
            clockAnchorX = this[Keys.CLOCK_ANCHOR_X] ?: defaults.clockAnchorX,
            clockAnchorY = this[Keys.CLOCK_ANCHOR_Y] ?: defaults.clockAnchorY,
            weatherAnchorX = this[Keys.WEATHER_ANCHOR_X] ?: defaults.weatherAnchorX,
            weatherAnchorY = this[Keys.WEATHER_ANCHOR_Y] ?: defaults.weatherAnchorY,
            weatherWidgets = normalizeWeatherWidgets(weatherWidgets),
            customTextWidgets = textWidgets,
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
            photoramaFolderPath = this[Keys.PHOTORAMA_FOLDER_PATH] ?: defaults.photoramaFolderPath,
            photoramaIncludeSubfolders = this[Keys.PHOTORAMA_INCLUDE_SUBFOLDERS] ?: defaults.photoramaIncludeSubfolders,
            photoramaIntervalSeconds = this[Keys.PHOTORAMA_INTERVAL_SECONDS] ?: defaults.photoramaIntervalSeconds,
            photoramaShuffle = this[Keys.PHOTORAMA_SHUFFLE] ?: defaults.photoramaShuffle,
            photoramaCacheSizeMb = this[Keys.PHOTORAMA_CACHE_SIZE_MB] ?: defaults.photoramaCacheSizeMb,
            walkieTalkieEnabled = this[Keys.WALKIE_TALKIE_ENABLED] ?: defaults.walkieTalkieEnabled,
            walkieTalkiePeers = peers,
            walkieTalkieTarget = this[Keys.WALKIE_TALKIE_TARGET] ?: defaults.walkieTalkieTarget,
            walkieTalkiePort = this[Keys.WALKIE_TALKIE_PORT] ?: defaults.walkieTalkiePort,
            walkieTalkieMicBoostPercent = this[Keys.WALKIE_TALKIE_MIC_BOOST] ?: defaults.walkieTalkieMicBoostPercent,
            walkieTalkieIncomingChimeEnabled = this[Keys.WALKIE_TALKIE_INCOMING_CHIME_ENABLED] ?: defaults.walkieTalkieIncomingChimeEnabled,
            walkieTalkieIncomingChime = this[Keys.WALKIE_TALKIE_INCOMING_CHIME] ?: defaults.walkieTalkieIncomingChime,
            walkieTalkieOverlayEnabled = this[Keys.WALKIE_TALKIE_OVERLAY_ENABLED] ?: defaults.walkieTalkieOverlayEnabled,
            walkieTalkiePttAnchorX = this[Keys.WALKIE_TALKIE_PTT_ANCHOR_X] ?: defaults.walkieTalkiePttAnchorX,
            walkieTalkiePttAnchorY = this[Keys.WALKIE_TALKIE_PTT_ANCHOR_Y] ?: defaults.walkieTalkiePttAnchorY,
            airplayEnabled = this[Keys.AIRPLAY_ENABLED] ?: defaults.airplayEnabled,
            airplayHwAddressHex = this[Keys.AIRPLAY_HW_ADDRESS_HEX] ?: defaults.airplayHwAddressHex,
            airplayAuthMode = this[Keys.AIRPLAY_AUTH_MODE] ?: defaults.airplayAuthMode,
            airplayPassword = this[Keys.AIRPLAY_PASSWORD] ?: defaults.airplayPassword,
            airplayMirrorPreset = this[Keys.AIRPLAY_MIRROR_PRESET] ?: defaults.airplayMirrorPreset,
            airplayAllowHevc = this[Keys.AIRPLAY_ALLOW_HEVC] ?: defaults.airplayAllowHevc,
            airplayShowClockWidget = this[Keys.AIRPLAY_SHOW_CLOCK_WIDGET] ?: defaults.airplayShowClockWidget,
            browserEnabled = this[Keys.BROWSER_ENABLED] ?: defaults.browserEnabled,
            browserHomeUrl = this[Keys.BROWSER_HOME_URL] ?: defaults.browserHomeUrl,
            browserLastVisitedUrl = this[Keys.BROWSER_LAST_VISITED_URL] ?: defaults.browserLastVisitedUrl,
            jellyfinEnabled = this[Keys.JELLYFIN_ENABLED] ?: defaults.jellyfinEnabled,
            jellyfinServerUrl = this[Keys.JELLYFIN_SERVER_URL] ?: defaults.jellyfinServerUrl,
            jellyfinStartPath = this[Keys.JELLYFIN_START_PATH] ?: defaults.jellyfinStartPath,
            jellyfinDesktopMode = this[Keys.JELLYFIN_DESKTOP_MODE] ?: defaults.jellyfinDesktopMode,
            jellyfinReloadOnOpen = this[Keys.JELLYFIN_RELOAD_ON_OPEN] ?: defaults.jellyfinReloadOnOpen,
            jellyfinOpenExternalLinks = this[Keys.JELLYFIN_OPEN_EXTERNAL_LINKS] ?: defaults.jellyfinOpenExternalLinks,
            homeAssistantEnabled = this[Keys.HOME_ASSISTANT_ENABLED] ?: defaults.homeAssistantEnabled,
            homeAssistantUrl = this[Keys.HOME_ASSISTANT_URL] ?: defaults.homeAssistantUrl,
            photoboothEnabled = this[Keys.PHOTOBOOTH_ENABLED] ?: defaults.photoboothEnabled,
            mirrorDropEnabled = this[Keys.MIRRORDROP_ENABLED] ?: defaults.mirrorDropEnabled,
            mirrorDropPort = this[Keys.MIRRORDROP_PORT] ?: defaults.mirrorDropPort,
            weddingModeEnabled = this[Keys.WEDDING_MODE_ENABLED] ?: defaults.weddingModeEnabled,
            weddingPartnerOne = this[Keys.WEDDING_PARTNER_ONE] ?: defaults.weddingPartnerOne,
            weddingPartnerTwo = this[Keys.WEDDING_PARTNER_TWO] ?: defaults.weddingPartnerTwo,
            weddingDateText = this[Keys.WEDDING_DATE_TEXT] ?: defaults.weddingDateText,
            weddingLocation = this[Keys.WEDDING_LOCATION] ?: defaults.weddingLocation,
            weddingWelcomeMessage = this[Keys.WEDDING_WELCOME_MESSAGE] ?: defaults.weddingWelcomeMessage,
            weddingIdleTimeoutSeconds = this[Keys.WEDDING_IDLE_TIMEOUT_SECONDS] ?: defaults.weddingIdleTimeoutSeconds,
            iptvEnabled = this[Keys.IPTV_ENABLED] ?: defaults.iptvEnabled,
            iptvPortalUrl = this[Keys.IPTV_PORTAL_URL] ?: defaults.iptvPortalUrl,
            iptvMacAddress = this[Keys.IPTV_MAC_ADDRESS] ?: defaults.iptvMacAddress,
            iptvSleepTimeoutSeconds = this[Keys.IPTV_SLEEP_TIMEOUT_SECONDS] ?: defaults.iptvSleepTimeoutSeconds,
            iptvVolume = this[Keys.IPTV_VOLUME] ?: defaults.iptvVolume,
            iptvLastChannelId = this[Keys.IPTV_LAST_CHANNEL_ID] ?: defaults.iptvLastChannelId,
            iptvOpenMuted = this[Keys.IPTV_OPEN_MUTED] ?: defaults.iptvOpenMuted,
            iptvRecordingPortalUrl = this[Keys.IPTV_RECORDING_PORTAL_URL] ?: defaults.iptvRecordingPortalUrl,
            iptvRecordingMacAddress = this[Keys.IPTV_RECORDING_MAC_ADDRESS] ?: defaults.iptvRecordingMacAddress,
            parentalControlPin = this[Keys.PARENTAL_CONTROL_PIN] ?: defaults.parentalControlPin,
            parentalControlMode = this[Keys.PARENTAL_CONTROL_MODE] ?: defaults.parentalControlMode,
            iptvRecordingDestination = this[Keys.IPTV_RECORDING_DESTINATION] ?: defaults.iptvRecordingDestination,
            iptvRecordingSmbFolder = this[Keys.IPTV_RECORDING_SMB_FOLDER] ?: defaults.iptvRecordingSmbFolder,
            iptvRecordingLocalCapMb = this[Keys.IPTV_RECORDING_LOCAL_CAP_MB] ?: defaults.iptvRecordingLocalCapMb,
            iptvScheduledRecordings = scheduledRecordings,
            launcherFavoriteApps = favorites,
            launcherHiddenApps = this[Keys.LAUNCHER_HIDDEN_APPS] ?: defaults.launcherHiddenApps,
            lastVisitedPageIndex = this[Keys.LAST_VISITED_PAGE_INDEX] ?: defaults.lastVisitedPageIndex,
            displayOrientationMode = this[Keys.DISPLAY_ORIENTATION_MODE] ?: defaults.displayOrientationMode,
            brightnessLevel255 = this[Keys.BRIGHTNESS_LEVEL_255] ?: defaults.brightnessLevel255,
            brightnessExtraDimPercent = this[Keys.BRIGHTNESS_EXTRA_DIM_PERCENT] ?: defaults.brightnessExtraDimPercent,
            brightnessDimTarget = this[Keys.BRIGHTNESS_DIM_TARGET] ?: defaults.brightnessDimTarget,
            nightClockBrightnessLevel255 = this[Keys.NIGHT_CLOCK_BRIGHTNESS_LEVEL_255] ?: defaults.nightClockBrightnessLevel255,
            nightClockTextDimPercent = this[Keys.NIGHT_CLOCK_TEXT_DIM_PERCENT] ?: defaults.nightClockTextDimPercent,
            nightClockAnchorX = this[Keys.NIGHT_CLOCK_ANCHOR_X] ?: defaults.nightClockAnchorX,
            nightClockAnchorY = this[Keys.NIGHT_CLOCK_ANCHOR_Y] ?: defaults.nightClockAnchorY,
            nightClockWeatherAnchorX = this[Keys.NIGHT_CLOCK_WEATHER_ANCHOR_X] ?: defaults.nightClockWeatherAnchorX,
            nightClockWeatherAnchorY = this[Keys.NIGHT_CLOCK_WEATHER_ANCHOR_Y] ?: defaults.nightClockWeatherAnchorY,
        )
    }

    internal object Keys {
        val DEVICE_NAME = stringPreferencesKey("device_name")

        val CLOCK_FONT_SIZE_SP = intPreferencesKey("clock_font_size_sp")
        val CLOCK_TEXT_COLOR = intPreferencesKey("clock_text_color")
        val CLOCK_BACKGROUND_COLOR = intPreferencesKey("clock_background_color")
        val CLOCK_BACKGROUND_MODE = stringPreferencesKey("clock_background_mode")
        val CLOCK_ANCHOR_X = floatPreferencesKey("clock_anchor_x")
        val CLOCK_ANCHOR_Y = floatPreferencesKey("clock_anchor_y")
        val WEATHER_ANCHOR_X = floatPreferencesKey("weather_anchor_x")
        val WEATHER_ANCHOR_Y = floatPreferencesKey("weather_anchor_y")
        val WEATHER_WIDGETS = stringPreferencesKey("weather_widgets_json")

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
        val PHOTORAMA_FOLDER_PATH = stringPreferencesKey("photorama_folder_path")
        val PHOTORAMA_INCLUDE_SUBFOLDERS = booleanPreferencesKey("photorama_include_subfolders")
        val PHOTORAMA_INTERVAL_SECONDS = intPreferencesKey("photorama_interval_seconds")
        val PHOTORAMA_SHUFFLE = booleanPreferencesKey("photorama_shuffle")
        val PHOTORAMA_CACHE_SIZE_MB = intPreferencesKey("photorama_cache_size_mb")

        val WALKIE_TALKIE_ENABLED = booleanPreferencesKey("walkie_talkie_enabled")
        val WALKIE_TALKIE_PEERS = stringPreferencesKey("walkie_talkie_peers_json")
        val WALKIE_TALKIE_TARGET = stringPreferencesKey("walkie_talkie_target")
        val WALKIE_TALKIE_PORT = intPreferencesKey("walkie_talkie_port")
        val WALKIE_TALKIE_MIC_BOOST = intPreferencesKey("walkie_talkie_mic_boost_percent")
        val WALKIE_TALKIE_INCOMING_CHIME_ENABLED = booleanPreferencesKey("walkie_talkie_incoming_chime_enabled")
        val WALKIE_TALKIE_INCOMING_CHIME = stringPreferencesKey("walkie_talkie_incoming_chime")
        val WALKIE_TALKIE_OVERLAY_ENABLED = booleanPreferencesKey("walkie_talkie_overlay_enabled")
        val WALKIE_TALKIE_PTT_ANCHOR_X = floatPreferencesKey("walkie_talkie_ptt_anchor_x")
        val WALKIE_TALKIE_PTT_ANCHOR_Y = floatPreferencesKey("walkie_talkie_ptt_anchor_y")

        val AIRPLAY_ENABLED = booleanPreferencesKey("airplay_enabled")
        val AIRPLAY_HW_ADDRESS_HEX = stringPreferencesKey("airplay_hw_address_hex")
        val AIRPLAY_AUTH_MODE = stringPreferencesKey("airplay_auth_mode")
        val AIRPLAY_PASSWORD = stringPreferencesKey("airplay_password")
        val AIRPLAY_MIRROR_PRESET = stringPreferencesKey("airplay_mirror_preset")
        val AIRPLAY_ALLOW_HEVC = booleanPreferencesKey("airplay_allow_hevc")
        val AIRPLAY_SHOW_CLOCK_WIDGET = booleanPreferencesKey("airplay_show_clock_widget")

        val BROWSER_ENABLED = booleanPreferencesKey("browser_enabled")
        val BROWSER_HOME_URL = stringPreferencesKey("browser_home_url")
        val BROWSER_LAST_VISITED_URL = stringPreferencesKey("browser_last_visited_url")

        val JELLYFIN_ENABLED = booleanPreferencesKey("jellyfin_enabled")
        val JELLYFIN_SERVER_URL = stringPreferencesKey("jellyfin_server_url")
        val JELLYFIN_START_PATH = stringPreferencesKey("jellyfin_start_path")
        val JELLYFIN_DESKTOP_MODE = booleanPreferencesKey("jellyfin_desktop_mode")
        val JELLYFIN_RELOAD_ON_OPEN = booleanPreferencesKey("jellyfin_reload_on_open")
        val JELLYFIN_OPEN_EXTERNAL_LINKS = booleanPreferencesKey("jellyfin_open_external_links")

        val HOME_ASSISTANT_ENABLED = booleanPreferencesKey("home_assistant_enabled")
        val HOME_ASSISTANT_URL = stringPreferencesKey("home_assistant_url")

        val PHOTOBOOTH_ENABLED = booleanPreferencesKey("photobooth_enabled")

        val MIRRORDROP_ENABLED = booleanPreferencesKey("mirrordrop_enabled")
        val MIRRORDROP_PORT = intPreferencesKey("mirrordrop_port")

        val WEDDING_MODE_ENABLED = booleanPreferencesKey("wedding_mode_enabled")
        val WEDDING_PARTNER_ONE = stringPreferencesKey("wedding_partner_one")
        val WEDDING_PARTNER_TWO = stringPreferencesKey("wedding_partner_two")
        val WEDDING_DATE_TEXT = stringPreferencesKey("wedding_date_text")
        val WEDDING_LOCATION = stringPreferencesKey("wedding_location")
        val WEDDING_WELCOME_MESSAGE = stringPreferencesKey("wedding_welcome_message")
        val WEDDING_IDLE_TIMEOUT_SECONDS = intPreferencesKey("wedding_idle_timeout_seconds")

        val IPTV_ENABLED = booleanPreferencesKey("iptv_enabled")
        val IPTV_PORTAL_URL = stringPreferencesKey("iptv_portal_url")
        val IPTV_MAC_ADDRESS = stringPreferencesKey("iptv_mac_address")
        val IPTV_SLEEP_TIMEOUT_SECONDS = intPreferencesKey("iptv_sleep_timeout_seconds")
        val IPTV_VOLUME = floatPreferencesKey("iptv_volume")
        val IPTV_LAST_CHANNEL_ID = stringPreferencesKey("iptv_last_channel_id")
        val IPTV_OPEN_MUTED = booleanPreferencesKey("iptv_open_muted")
        val IPTV_RECORDING_PORTAL_URL = stringPreferencesKey("iptv_recording_portal_url")
        val IPTV_RECORDING_MAC_ADDRESS = stringPreferencesKey("iptv_recording_mac_address")
        val PARENTAL_CONTROL_PIN = stringPreferencesKey("parental_control_pin")
        val PARENTAL_CONTROL_MODE = stringPreferencesKey("parental_control_mode")
        val IPTV_RECORDING_DESTINATION = stringPreferencesKey("iptv_recording_destination")
        val IPTV_RECORDING_SMB_FOLDER = stringPreferencesKey("iptv_recording_smb_folder")
        val IPTV_RECORDING_LOCAL_CAP_MB = intPreferencesKey("iptv_recording_local_cap_mb")
        val IPTV_SCHEDULED_RECORDINGS = stringPreferencesKey("iptv_scheduled_recordings_json")

        val CUSTOM_TEXT_WIDGETS = stringPreferencesKey("custom_text_widgets_json")

        val LAUNCHER_FAVORITE_APPS = stringPreferencesKey("launcher_favorite_apps_json")
        val LAUNCHER_HIDDEN_APPS = stringSetPreferencesKey("launcher_hidden_apps")
        val LAST_VISITED_PAGE_INDEX = intPreferencesKey("last_visited_page_index")
        val DISPLAY_ORIENTATION_MODE = stringPreferencesKey("display_orientation_mode")

        val BRIGHTNESS_LEVEL_255 = intPreferencesKey("brightness_level_255")
        val BRIGHTNESS_EXTRA_DIM_PERCENT = intPreferencesKey("brightness_extra_dim_percent")
        val BRIGHTNESS_DIM_TARGET = stringPreferencesKey("brightness_dim_target")
        val NIGHT_CLOCK_BRIGHTNESS_LEVEL_255 = intPreferencesKey("night_clock_brightness_level_255")
        val NIGHT_CLOCK_TEXT_DIM_PERCENT = intPreferencesKey("night_clock_text_dim_percent")
        val NIGHT_CLOCK_ANCHOR_X = floatPreferencesKey("night_clock_anchor_x")
        val NIGHT_CLOCK_ANCHOR_Y = floatPreferencesKey("night_clock_anchor_y")
        val NIGHT_CLOCK_WEATHER_ANCHOR_X = floatPreferencesKey("night_clock_weather_anchor_x")
        val NIGHT_CLOCK_WEATHER_ANCHOR_Y = floatPreferencesKey("night_clock_weather_anchor_y")
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
    var clockTextColorArgb: Int by PrefDelegate(SettingsRepository.Keys.CLOCK_TEXT_COLOR, prefs, defaults.clockTextColorArgb)
    var clockBackgroundColorArgb: Int by PrefDelegate(SettingsRepository.Keys.CLOCK_BACKGROUND_COLOR, prefs, defaults.clockBackgroundColorArgb)
    var clockBackgroundMode: String by PrefDelegate(SettingsRepository.Keys.CLOCK_BACKGROUND_MODE, prefs, defaults.clockBackgroundMode)
    var clockAnchorX: Float by PrefDelegate(SettingsRepository.Keys.CLOCK_ANCHOR_X, prefs, defaults.clockAnchorX)
    var clockAnchorY: Float by PrefDelegate(SettingsRepository.Keys.CLOCK_ANCHOR_Y, prefs, defaults.clockAnchorY)
    var weatherAnchorX: Float by PrefDelegate(SettingsRepository.Keys.WEATHER_ANCHOR_X, prefs, defaults.weatherAnchorX)
    var weatherAnchorY: Float by PrefDelegate(SettingsRepository.Keys.WEATHER_ANCHOR_Y, prefs, defaults.weatherAnchorY)

    var weatherEnabled: Boolean by PrefDelegate(SettingsRepository.Keys.WEATHER_ENABLED, prefs, defaults.weatherEnabled)
    var weatherLocationQuery: String by PrefDelegate(SettingsRepository.Keys.WEATHER_LOCATION_QUERY, prefs, defaults.weatherLocationQuery)
    var weatherLocationLabel: String by PrefDelegate(SettingsRepository.Keys.WEATHER_LOCATION_LABEL, prefs, defaults.weatherLocationLabel)
    var weatherLatitude: String by PrefDelegate(SettingsRepository.Keys.WEATHER_LATITUDE, prefs, defaults.weatherLatitude)
    var weatherLongitude: String by PrefDelegate(SettingsRepository.Keys.WEATHER_LONGITUDE, prefs, defaults.weatherLongitude)
    var weatherUseFahrenheit: Boolean by PrefDelegate(SettingsRepository.Keys.WEATHER_USE_FAHRENHEIT, prefs, defaults.weatherUseFahrenheit)

    var photoramaEnabled: Boolean by PrefDelegate(SettingsRepository.Keys.PHOTORAMA_ENABLED, prefs, defaults.photoramaEnabled)
    var photoramaFolderPath: String by PrefDelegate(SettingsRepository.Keys.PHOTORAMA_FOLDER_PATH, prefs, defaults.photoramaFolderPath)
    var photoramaIncludeSubfolders: Boolean by PrefDelegate(SettingsRepository.Keys.PHOTORAMA_INCLUDE_SUBFOLDERS, prefs, defaults.photoramaIncludeSubfolders)
    var photoramaIntervalSeconds: Int by PrefDelegate(SettingsRepository.Keys.PHOTORAMA_INTERVAL_SECONDS, prefs, defaults.photoramaIntervalSeconds)
    var photoramaShuffle: Boolean by PrefDelegate(SettingsRepository.Keys.PHOTORAMA_SHUFFLE, prefs, defaults.photoramaShuffle)
    var photoramaCacheSizeMb: Int by PrefDelegate(SettingsRepository.Keys.PHOTORAMA_CACHE_SIZE_MB, prefs, defaults.photoramaCacheSizeMb)

    var walkieTalkieEnabled: Boolean by PrefDelegate(SettingsRepository.Keys.WALKIE_TALKIE_ENABLED, prefs, defaults.walkieTalkieEnabled)
    var walkieTalkieTarget: String by PrefDelegate(SettingsRepository.Keys.WALKIE_TALKIE_TARGET, prefs, defaults.walkieTalkieTarget)
    var walkieTalkiePort: Int by PrefDelegate(SettingsRepository.Keys.WALKIE_TALKIE_PORT, prefs, defaults.walkieTalkiePort)
    var walkieTalkieMicBoostPercent: Int by PrefDelegate(SettingsRepository.Keys.WALKIE_TALKIE_MIC_BOOST, prefs, defaults.walkieTalkieMicBoostPercent)
    var walkieTalkieIncomingChimeEnabled: Boolean by PrefDelegate(SettingsRepository.Keys.WALKIE_TALKIE_INCOMING_CHIME_ENABLED, prefs, defaults.walkieTalkieIncomingChimeEnabled)
    var walkieTalkieIncomingChime: String by PrefDelegate(SettingsRepository.Keys.WALKIE_TALKIE_INCOMING_CHIME, prefs, defaults.walkieTalkieIncomingChime)
    var walkieTalkieOverlayEnabled: Boolean by PrefDelegate(SettingsRepository.Keys.WALKIE_TALKIE_OVERLAY_ENABLED, prefs, defaults.walkieTalkieOverlayEnabled)
    var walkieTalkiePttAnchorX: Float by PrefDelegate(SettingsRepository.Keys.WALKIE_TALKIE_PTT_ANCHOR_X, prefs, defaults.walkieTalkiePttAnchorX)
    var walkieTalkiePttAnchorY: Float by PrefDelegate(SettingsRepository.Keys.WALKIE_TALKIE_PTT_ANCHOR_Y, prefs, defaults.walkieTalkiePttAnchorY)

    var airplayEnabled: Boolean by PrefDelegate(SettingsRepository.Keys.AIRPLAY_ENABLED, prefs, defaults.airplayEnabled)
    var airplayHwAddressHex: String by PrefDelegate(SettingsRepository.Keys.AIRPLAY_HW_ADDRESS_HEX, prefs, defaults.airplayHwAddressHex)
    var airplayAuthMode: String by PrefDelegate(SettingsRepository.Keys.AIRPLAY_AUTH_MODE, prefs, defaults.airplayAuthMode)
    var airplayPassword: String by PrefDelegate(SettingsRepository.Keys.AIRPLAY_PASSWORD, prefs, defaults.airplayPassword)
    var airplayMirrorPreset: String by PrefDelegate(SettingsRepository.Keys.AIRPLAY_MIRROR_PRESET, prefs, defaults.airplayMirrorPreset)
    var airplayAllowHevc: Boolean by PrefDelegate(SettingsRepository.Keys.AIRPLAY_ALLOW_HEVC, prefs, defaults.airplayAllowHevc)
    var airplayShowClockWidget: Boolean by PrefDelegate(SettingsRepository.Keys.AIRPLAY_SHOW_CLOCK_WIDGET, prefs, defaults.airplayShowClockWidget)

    var browserEnabled: Boolean by PrefDelegate(SettingsRepository.Keys.BROWSER_ENABLED, prefs, defaults.browserEnabled)
    var browserHomeUrl: String by PrefDelegate(SettingsRepository.Keys.BROWSER_HOME_URL, prefs, defaults.browserHomeUrl)
    var browserLastVisitedUrl: String by PrefDelegate(SettingsRepository.Keys.BROWSER_LAST_VISITED_URL, prefs, defaults.browserLastVisitedUrl)

    var jellyfinEnabled: Boolean by PrefDelegate(SettingsRepository.Keys.JELLYFIN_ENABLED, prefs, defaults.jellyfinEnabled)
    var jellyfinServerUrl: String by PrefDelegate(SettingsRepository.Keys.JELLYFIN_SERVER_URL, prefs, defaults.jellyfinServerUrl)
    var jellyfinStartPath: String by PrefDelegate(SettingsRepository.Keys.JELLYFIN_START_PATH, prefs, defaults.jellyfinStartPath)
    var jellyfinDesktopMode: Boolean by PrefDelegate(SettingsRepository.Keys.JELLYFIN_DESKTOP_MODE, prefs, defaults.jellyfinDesktopMode)
    var jellyfinReloadOnOpen: Boolean by PrefDelegate(SettingsRepository.Keys.JELLYFIN_RELOAD_ON_OPEN, prefs, defaults.jellyfinReloadOnOpen)
    var jellyfinOpenExternalLinks: Boolean by PrefDelegate(SettingsRepository.Keys.JELLYFIN_OPEN_EXTERNAL_LINKS, prefs, defaults.jellyfinOpenExternalLinks)

    var homeAssistantEnabled: Boolean by PrefDelegate(SettingsRepository.Keys.HOME_ASSISTANT_ENABLED, prefs, defaults.homeAssistantEnabled)
    var homeAssistantUrl: String by PrefDelegate(SettingsRepository.Keys.HOME_ASSISTANT_URL, prefs, defaults.homeAssistantUrl)

    var photoboothEnabled: Boolean by PrefDelegate(SettingsRepository.Keys.PHOTOBOOTH_ENABLED, prefs, defaults.photoboothEnabled)

    var mirrorDropEnabled: Boolean by PrefDelegate(SettingsRepository.Keys.MIRRORDROP_ENABLED, prefs, defaults.mirrorDropEnabled)
    var mirrorDropPort: Int by PrefDelegate(SettingsRepository.Keys.MIRRORDROP_PORT, prefs, defaults.mirrorDropPort)

    var weddingModeEnabled: Boolean by PrefDelegate(SettingsRepository.Keys.WEDDING_MODE_ENABLED, prefs, defaults.weddingModeEnabled)
    var weddingPartnerOne: String by PrefDelegate(SettingsRepository.Keys.WEDDING_PARTNER_ONE, prefs, defaults.weddingPartnerOne)
    var weddingPartnerTwo: String by PrefDelegate(SettingsRepository.Keys.WEDDING_PARTNER_TWO, prefs, defaults.weddingPartnerTwo)
    var weddingDateText: String by PrefDelegate(SettingsRepository.Keys.WEDDING_DATE_TEXT, prefs, defaults.weddingDateText)
    var weddingLocation: String by PrefDelegate(SettingsRepository.Keys.WEDDING_LOCATION, prefs, defaults.weddingLocation)
    var weddingWelcomeMessage: String by PrefDelegate(SettingsRepository.Keys.WEDDING_WELCOME_MESSAGE, prefs, defaults.weddingWelcomeMessage)
    var weddingIdleTimeoutSeconds: Int by PrefDelegate(
        SettingsRepository.Keys.WEDDING_IDLE_TIMEOUT_SECONDS,
        prefs,
        defaults.weddingIdleTimeoutSeconds,
    )

    var iptvEnabled: Boolean by PrefDelegate(SettingsRepository.Keys.IPTV_ENABLED, prefs, defaults.iptvEnabled)
    var iptvPortalUrl: String by PrefDelegate(SettingsRepository.Keys.IPTV_PORTAL_URL, prefs, defaults.iptvPortalUrl)
    var iptvMacAddress: String by PrefDelegate(SettingsRepository.Keys.IPTV_MAC_ADDRESS, prefs, defaults.iptvMacAddress)
    var iptvSleepTimeoutSeconds: Int by PrefDelegate(SettingsRepository.Keys.IPTV_SLEEP_TIMEOUT_SECONDS, prefs, defaults.iptvSleepTimeoutSeconds)
    var iptvVolume: Float by PrefDelegate(SettingsRepository.Keys.IPTV_VOLUME, prefs, defaults.iptvVolume)
    var iptvLastChannelId: String by PrefDelegate(SettingsRepository.Keys.IPTV_LAST_CHANNEL_ID, prefs, defaults.iptvLastChannelId)
    var iptvOpenMuted: Boolean by PrefDelegate(SettingsRepository.Keys.IPTV_OPEN_MUTED, prefs, defaults.iptvOpenMuted)
    var iptvRecordingPortalUrl: String by PrefDelegate(SettingsRepository.Keys.IPTV_RECORDING_PORTAL_URL, prefs, defaults.iptvRecordingPortalUrl)
    var iptvRecordingMacAddress: String by PrefDelegate(SettingsRepository.Keys.IPTV_RECORDING_MAC_ADDRESS, prefs, defaults.iptvRecordingMacAddress)
    var parentalControlPin: String by PrefDelegate(SettingsRepository.Keys.PARENTAL_CONTROL_PIN, prefs, defaults.parentalControlPin)
    var parentalControlMode: String by PrefDelegate(SettingsRepository.Keys.PARENTAL_CONTROL_MODE, prefs, defaults.parentalControlMode)
    var iptvRecordingDestination: String by PrefDelegate(SettingsRepository.Keys.IPTV_RECORDING_DESTINATION, prefs, defaults.iptvRecordingDestination)
    var iptvRecordingSmbFolder: String by PrefDelegate(SettingsRepository.Keys.IPTV_RECORDING_SMB_FOLDER, prefs, defaults.iptvRecordingSmbFolder)
    var iptvRecordingLocalCapMb: Int by PrefDelegate(SettingsRepository.Keys.IPTV_RECORDING_LOCAL_CAP_MB, prefs, defaults.iptvRecordingLocalCapMb)

    var launcherHiddenApps: Set<String> by PrefDelegate(SettingsRepository.Keys.LAUNCHER_HIDDEN_APPS, prefs, defaults.launcherHiddenApps)
    var lastVisitedPageIndex: Int by PrefDelegate(SettingsRepository.Keys.LAST_VISITED_PAGE_INDEX, prefs, defaults.lastVisitedPageIndex)
    var displayOrientationMode: String by PrefDelegate(SettingsRepository.Keys.DISPLAY_ORIENTATION_MODE, prefs, defaults.displayOrientationMode)

    var brightnessLevel255: Int by PrefDelegate(SettingsRepository.Keys.BRIGHTNESS_LEVEL_255, prefs, defaults.brightnessLevel255)
    var brightnessExtraDimPercent: Int by PrefDelegate(SettingsRepository.Keys.BRIGHTNESS_EXTRA_DIM_PERCENT, prefs, defaults.brightnessExtraDimPercent)
    var brightnessDimTarget: String by PrefDelegate(SettingsRepository.Keys.BRIGHTNESS_DIM_TARGET, prefs, defaults.brightnessDimTarget)
    var nightClockBrightnessLevel255: Int by PrefDelegate(SettingsRepository.Keys.NIGHT_CLOCK_BRIGHTNESS_LEVEL_255, prefs, defaults.nightClockBrightnessLevel255)
    var nightClockTextDimPercent: Int by PrefDelegate(SettingsRepository.Keys.NIGHT_CLOCK_TEXT_DIM_PERCENT, prefs, defaults.nightClockTextDimPercent)
    var nightClockAnchorX: Float by PrefDelegate(SettingsRepository.Keys.NIGHT_CLOCK_ANCHOR_X, prefs, defaults.nightClockAnchorX)
    var nightClockAnchorY: Float by PrefDelegate(SettingsRepository.Keys.NIGHT_CLOCK_ANCHOR_Y, prefs, defaults.nightClockAnchorY)
    var nightClockWeatherAnchorX: Float by PrefDelegate(SettingsRepository.Keys.NIGHT_CLOCK_WEATHER_ANCHOR_X, prefs, defaults.nightClockWeatherAnchorX)
    var nightClockWeatherAnchorY: Float by PrefDelegate(SettingsRepository.Keys.NIGHT_CLOCK_WEATHER_ANCHOR_Y, prefs, defaults.nightClockWeatherAnchorY)

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

    var iptvScheduledRecordings: List<com.sconcept.mirrordash.iptv.ScheduledRecording>
        get() = prefs[SettingsRepository.Keys.IPTV_SCHEDULED_RECORDINGS]
            ?.let { runCatching { json.decodeFromString<List<com.sconcept.mirrordash.iptv.ScheduledRecording>>(it) }.getOrNull() }
            .orEmpty()
        set(value) {
            prefs[SettingsRepository.Keys.IPTV_SCHEDULED_RECORDINGS] = json.encodeToString(value)
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
