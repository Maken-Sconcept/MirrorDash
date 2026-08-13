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
import com.sconcept.mirrordash.nas.model.SmbShare
import com.sconcept.mirrordash.security.SecureCredentialStore
import com.sconcept.mirrordash.security.SessionCredentialHolder
import com.sconcept.mirrordash.ui.theme.DEFAULT_CLOCK_FONT_SIZE_SP
import com.sconcept.mirrordash.walkietalkie.model.WalkieTalkiePeer
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

const val BRIGHTNESS_DIM_TARGET_WHOLE_SCREEN = "WHOLE_SCREEN"
const val BRIGHTNESS_DIM_TARGET_TEXT_ONLY = "TEXT_ONLY"

/** Bottom-start, matching the visual layout the Clock page ships with before anyone drags
 * anything - the single, only default (see the plan's note on why BerthierOptions' clock
 * position bug happened: two of these that disagreed with each other). */
const val DEFAULT_CLOCK_ANCHOR_X = 0.06f
const val DEFAULT_CLOCK_ANCHOR_Y = 0.82f
const val DEFAULT_WEATHER_ANCHOR_X = 0.92f
const val DEFAULT_WEATHER_ANCHOR_Y = 0.08f

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

    // Home Assistant
    val homeAssistantEnabled: Boolean = false,
    val homeAssistantUrl: String = "",

    // Launcher
    val launcherFavoriteApps: List<String> = emptyList(),
    val launcherHiddenApps: Set<String> = emptySet(),
    val lastVisitedPageIndex: Int = 0,
    val displayOrientationMode: String = "AUTO",

    // Brightness
    val brightnessLevel255: Int = 255,
    val brightnessExtraDimPercent: Int = 0,
    val brightnessDimTarget: String = BRIGHTNESS_DIM_TARGET_WHOLE_SCREEN,
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
        val textWidgetsRaw = this[Keys.CUSTOM_TEXT_WIDGETS]
        val textWidgets = textWidgetsRaw?.let { raw ->
            runCatching { json.decodeFromString<List<CustomTextWidget>>(raw) }.getOrDefault(emptyList())
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
            homeAssistantEnabled = this[Keys.HOME_ASSISTANT_ENABLED] ?: defaults.homeAssistantEnabled,
            homeAssistantUrl = this[Keys.HOME_ASSISTANT_URL] ?: defaults.homeAssistantUrl,
            launcherFavoriteApps = favorites,
            launcherHiddenApps = this[Keys.LAUNCHER_HIDDEN_APPS] ?: defaults.launcherHiddenApps,
            lastVisitedPageIndex = this[Keys.LAST_VISITED_PAGE_INDEX] ?: defaults.lastVisitedPageIndex,
            displayOrientationMode = this[Keys.DISPLAY_ORIENTATION_MODE] ?: defaults.displayOrientationMode,
            brightnessLevel255 = this[Keys.BRIGHTNESS_LEVEL_255] ?: defaults.brightnessLevel255,
            brightnessExtraDimPercent = this[Keys.BRIGHTNESS_EXTRA_DIM_PERCENT] ?: defaults.brightnessExtraDimPercent,
            brightnessDimTarget = this[Keys.BRIGHTNESS_DIM_TARGET] ?: defaults.brightnessDimTarget,
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

        val HOME_ASSISTANT_ENABLED = booleanPreferencesKey("home_assistant_enabled")
        val HOME_ASSISTANT_URL = stringPreferencesKey("home_assistant_url")

        val CUSTOM_TEXT_WIDGETS = stringPreferencesKey("custom_text_widgets_json")

        val LAUNCHER_FAVORITE_APPS = stringPreferencesKey("launcher_favorite_apps_json")
        val LAUNCHER_HIDDEN_APPS = stringSetPreferencesKey("launcher_hidden_apps")
        val LAST_VISITED_PAGE_INDEX = intPreferencesKey("last_visited_page_index")
        val DISPLAY_ORIENTATION_MODE = stringPreferencesKey("display_orientation_mode")

        val BRIGHTNESS_LEVEL_255 = intPreferencesKey("brightness_level_255")
        val BRIGHTNESS_EXTRA_DIM_PERCENT = intPreferencesKey("brightness_extra_dim_percent")
        val BRIGHTNESS_DIM_TARGET = stringPreferencesKey("brightness_dim_target")
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

    var homeAssistantEnabled: Boolean by PrefDelegate(SettingsRepository.Keys.HOME_ASSISTANT_ENABLED, prefs, defaults.homeAssistantEnabled)
    var homeAssistantUrl: String by PrefDelegate(SettingsRepository.Keys.HOME_ASSISTANT_URL, prefs, defaults.homeAssistantUrl)

    var launcherHiddenApps: Set<String> by PrefDelegate(SettingsRepository.Keys.LAUNCHER_HIDDEN_APPS, prefs, defaults.launcherHiddenApps)
    var lastVisitedPageIndex: Int by PrefDelegate(SettingsRepository.Keys.LAST_VISITED_PAGE_INDEX, prefs, defaults.lastVisitedPageIndex)
    var displayOrientationMode: String by PrefDelegate(SettingsRepository.Keys.DISPLAY_ORIENTATION_MODE, prefs, defaults.displayOrientationMode)

    var brightnessLevel255: Int by PrefDelegate(SettingsRepository.Keys.BRIGHTNESS_LEVEL_255, prefs, defaults.brightnessLevel255)
    var brightnessExtraDimPercent: Int by PrefDelegate(SettingsRepository.Keys.BRIGHTNESS_EXTRA_DIM_PERCENT, prefs, defaults.brightnessExtraDimPercent)
    var brightnessDimTarget: String by PrefDelegate(SettingsRepository.Keys.BRIGHTNESS_DIM_TARGET, prefs, defaults.brightnessDimTarget)

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

    var customTextWidgets: List<CustomTextWidget>
        get() = prefs[SettingsRepository.Keys.CUSTOM_TEXT_WIDGETS]
            ?.let { runCatching { json.decodeFromString<List<CustomTextWidget>>(it) }.getOrNull() }
            .orEmpty()
        set(value) {
            prefs[SettingsRepository.Keys.CUSTOM_TEXT_WIDGETS] = json.encodeToString(value)
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
