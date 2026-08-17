package com.sconcept.mirrordash.settings

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sconcept.mirrordash.airplay.AirPlayUiState
import com.sconcept.mirrordash.clock.CustomTextWidget
import com.sconcept.mirrordash.clock.WEATHER_WIDGET_MODE_FORECAST_CARD
import com.sconcept.mirrordash.clock.WeatherWidget
import com.sconcept.mirrordash.clock.defaultWeatherWidget
import com.sconcept.mirrordash.nas.SmbRepository
import com.sconcept.mirrordash.nas.SmbPaths
import com.sconcept.mirrordash.nas.model.SmbConnectionState
import com.sconcept.mirrordash.nas.model.SmbFileItem
import com.sconcept.mirrordash.nas.model.SmbResult
import com.sconcept.mirrordash.nas.model.SmbShare
import com.sconcept.mirrordash.launcher.AppContainer
import com.sconcept.mirrordash.launcher.display.DisplayOrientationMode
import com.sconcept.mirrordash.launcher.display.storageKey
import com.sconcept.mirrordash.provisioning.ProvisioningConfig
import com.sconcept.mirrordash.provisioning.ProvisioningConfigLoader
import com.sconcept.mirrordash.walkietalkie.WalkieTalkieUiState
import com.sconcept.mirrordash.walkietalkie.model.WalkieTalkieDiscoveredPeer
import com.sconcept.mirrordash.walkietalkie.model.WalkieTalkiePeer
import com.sconcept.mirrordash.weather.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class NasTestResult { IDLE, TESTING, SUCCESS, FAILED }

data class ProvisioningStatus(
    val appliedAt: Long,
    val summary: String,
    val isError: Boolean = false,
)

data class NasBrowserState(
    val path: String = "",
    val items: List<SmbFileItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

data class SettingsUiState(
    val settings: MirrorDashSettings = MirrorDashSettings(),
    val settingsLoaded: Boolean = false,
    val nasTestResult: NasTestResult = NasTestResult.IDLE,
    val nasTestMessage: String? = null,
    val nasPasswordDraft: String = "",
    val jellyfinPasswordDraft: String = "",
    val homeAssistantPasswordDraft: String = "",
    val browser: NasBrowserState? = null,
    val weatherResolving: Boolean = false,
    val weatherError: String? = null,
    val nearbyWalkieTalkiePeers: List<WalkieTalkieDiscoveredPeer> = emptyList(),
    val walkieTalkieState: WalkieTalkieUiState = WalkieTalkieUiState(),
    val airPlayState: AirPlayUiState = AirPlayUiState(),
    val provisioningStatus: ProvisioningStatus? = null,
)

/**
 * Backs every Settings sub-screen (brief section 31). One ViewModel rather than one per
 * sub-screen since Settings is a single pager page with in-place sub-navigation, not separate
 * Activities like BerthierOptions' per-feature settings Activities.
 */
class SettingsViewModel(application: Application, private val settingsRepository: SettingsRepository) :
    AndroidViewModel(application) {

    private val smbRepository = SmbRepository(application)
    private val weatherRepository = WeatherRepository()
    private val walkieTalkieEngine = AppContainer.get(application).walkieTalkieEngine
    private val airPlayEngine = AppContainer.get(application).airPlayEngine

    private val _extra = MutableStateFlow(SettingsUiState())

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        _extra,
        walkieTalkieEngine.uiState,
        airPlayEngine.uiState,
    ) { settings, extra, walkieTalkie, airPlay ->
        extra.copy(
            settings = settings,
            settingsLoaded = true,
            nearbyWalkieTalkiePeers = walkieTalkie.discoveredPeers,
            walkieTalkieState = walkieTalkie,
            airPlayState = airPlay,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    // --- Device --------------------------------------------------------------------------------

    fun setDeviceName(name: String) = viewModelScope.launch {
        settingsRepository.update { deviceName = name }
    }

    // --- Appearance ------------------------------------------------------------------------

    fun setClockFontSize(sp: Int) = viewModelScope.launch {
        settingsRepository.update { clockFontSizeSp = sp }
    }

    fun setClockTextColor(color: Color) = viewModelScope.launch {
        settingsRepository.update { clockTextColorArgb = color.toArgb() }
    }

    fun setClockBackgroundColor(color: Color) = viewModelScope.launch {
        settingsRepository.update { clockBackgroundColorArgb = color.toArgb() }
    }

    /** Turning this on also enables the Photorama engine itself - otherwise the clock would sit
     * on a background that never receives a photo unless the user separately visited the
     * Photorama section and flipped its own switch too, same trap "Enable Photorama" used to be
     * before selecting a folder auto-enabled it. Turning it back off leaves Photorama's own
     * enabled state untouched, since that page becomes visible again and may still be wanted. */
    fun setClockBackgroundMode(usePhotorama: Boolean) = viewModelScope.launch {
        settingsRepository.update {
            clockBackgroundMode = if (usePhotorama) CLOCK_BACKGROUND_MODE_PHOTORAMA else CLOCK_BACKGROUND_MODE_SOLID
            if (usePhotorama) photoramaEnabled = true
        }
    }

    /** Long-press-and-drag directly on the Clock page moves the clock/weather clusters; this
     * is the escape hatch back to the shipped layout without hunting for the right drag. */
    fun resetClockLayout() = viewModelScope.launch {
        settingsRepository.update {
            clockAnchorX = DEFAULT_CLOCK_ANCHOR_X
            clockAnchorY = DEFAULT_CLOCK_ANCHOR_Y
            weatherAnchorX = DEFAULT_WEATHER_ANCHOR_X
            weatherAnchorY = DEFAULT_WEATHER_ANCHOR_Y
        }
    }

    // --- Text widgets ----------------------------------------------------------------------

    /** No cap - a user can add as many freeform text overlays as they want. Each starts stacked
     * near the middle so it's immediately visible and reachable to drag apart, rather than
     * appearing off in a corner the user has to go hunting for. */
    fun addTextWidget() = viewModelScope.launch {
        settingsRepository.update {
            customTextWidgets = customTextWidgets + CustomTextWidget(id = java.util.UUID.randomUUID().toString())
        }
    }

    fun updateTextWidget(id: String, transform: (CustomTextWidget) -> CustomTextWidget) = viewModelScope.launch {
        settingsRepository.update {
            customTextWidgets = customTextWidgets.map { if (it.id == id) transform(it) else it }
        }
    }

    fun removeTextWidget(id: String) = viewModelScope.launch {
        settingsRepository.update {
            customTextWidgets = customTextWidgets.filterNot { it.id == id }
        }
    }

    // --- Weather -----------------------------------------------------------------------------

    fun addWeatherWidget(mode: String = WEATHER_WIDGET_MODE_FORECAST_CARD) = viewModelScope.launch {
        val current = uiState.value.settings.weatherWidgets
        settingsRepository.update {
            val anchor = when {
                current.isEmpty() -> defaultWeatherWidget()
                mode == WEATHER_WIDGET_MODE_FORECAST_CARD -> defaultWeatherWidget(anchorX = 0.46f, anchorY = 0.14f)
                else -> defaultWeatherWidget(anchorX = 0.78f, anchorY = 0.08f + (current.size * 0.06f))
            }
            weatherWidgets = current + anchor.copy(mode = mode)
        }
    }

    fun updateWeatherWidget(id: String, transform: (WeatherWidget) -> WeatherWidget) = viewModelScope.launch {
        val current = uiState.value.settings.weatherWidgets
        settingsRepository.update {
            weatherWidgets = current.map { if (it.id == id) transform(it) else it }
        }
    }

    fun removeWeatherWidget(id: String) = viewModelScope.launch {
        val current = uiState.value.settings.weatherWidgets
        settingsRepository.update {
            weatherWidgets = current.filterNot { it.id == id }
        }
    }

    fun setWeatherEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.update { weatherEnabled = enabled }
    }

    fun setWeatherUnit(useFahrenheit: Boolean) = viewModelScope.launch {
        settingsRepository.update { weatherUseFahrenheit = useFahrenheit }
    }

    fun resolveAndSaveWeatherLocation(query: String) {
        _extra.update { it.copy(weatherResolving = true, weatherError = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { weatherRepository.resolveLocation(query) }
            result.fold(
                onSuccess = { location ->
                    settingsRepository.update {
                        weatherLocationQuery = location.query
                        weatherLocationLabel = location.label
                        weatherLatitude = location.latitude.toString()
                        weatherLongitude = location.longitude.toString()
                        weatherEnabled = true
                    }
                    _extra.update { it.copy(weatherResolving = false, weatherError = null) }
                },
                onFailure = { error ->
                    _extra.update { it.copy(weatherResolving = false, weatherError = error.message ?: "Couldn't find that location.") }
                },
            )
        }
    }

    // --- NAS / Photorama --------------------------------------------------------------------

    fun setNasPasswordDraft(password: String) {
        _extra.update { it.copy(nasPasswordDraft = password) }
    }

    fun testNasConnection(host: String, share: String, username: String, domain: String, rememberConnection: Boolean) {
        val candidate = SmbShare(host, share, username, _extra.value.nasPasswordDraft, domain)
        _extra.update { it.copy(nasTestResult = NasTestResult.TESTING, nasTestMessage = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { smbRepository.testConnection(candidate) }
            when (result) {
                is SmbResult.Success -> {
                    settingsRepository.updateSmbConnection(candidate, rememberConnection)
                    _extra.update { it.copy(nasTestResult = NasTestResult.SUCCESS, nasTestMessage = "Connected") }
                }
                is SmbResult.Failure -> {
                    _extra.update { it.copy(nasTestResult = NasTestResult.FAILED, nasTestMessage = result.message) }
                }
            }
        }
    }

    fun forgetNasConnection() = viewModelScope.launch {
        settingsRepository.forgetSmbConnection()
        _extra.update { it.copy(nasTestResult = NasTestResult.IDLE, nasTestMessage = null, nasPasswordDraft = "", browser = null) }
    }

    fun openFolderBrowser(startPath: String = "") {
        _extra.update { it.copy(browser = NasBrowserState(path = startPath, isLoading = true)) }
        browseTo(startPath)
    }

    fun closeFolderBrowser() {
        _extra.update { it.copy(browser = null) }
    }

    fun browseInto(item: SmbFileItem) {
        if (!item.isDirectory) return
        val newPath = SmbPaths.childPath(_extra.value.browser?.path.orEmpty(), item.name)
        browseTo(newPath)
    }

    fun browseUp() {
        val current = _extra.value.browser?.path.orEmpty()
        browseTo(SmbPaths.parentPath(current))
    }

    private fun browseTo(path: String) {
        _extra.update { it.copy(browser = (it.browser ?: NasBrowserState()).copy(path = path, isLoading = true, errorMessage = null)) }
        viewModelScope.launch {
            val share = settingsRepository.smbShareWithPassword()
            val result = withContext(Dispatchers.IO) { smbRepository.listDirectories(share, path) }
            when (result) {
                is SmbResult.Success -> _extra.update {
                    it.copy(browser = it.browser?.copy(items = result.value, isLoading = false))
                }
                is SmbResult.Failure -> _extra.update {
                    it.copy(browser = it.browser?.copy(isLoading = false, errorMessage = result.message))
                }
            }
        }
    }

    fun selectCurrentBrowserFolder() = viewModelScope.launch {
        val path = _extra.value.browser?.path ?: return@launch
        // Picking a folder is the last step of setup - there's no reason to leave the slideshow
        // configured-but-off after this, so it turns itself on here rather than requiring a
        // separate trip to the "Enable Photorama" switch further down the page.
        settingsRepository.update {
            photoramaFolderPath = path
            photoramaEnabled = true
        }
        _extra.update { it.copy(browser = null) }
    }

    fun setPhotoramaEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.update { photoramaEnabled = enabled }
    }

    fun setPhotoramaIncludeSubfolders(value: Boolean) = viewModelScope.launch {
        settingsRepository.update { photoramaIncludeSubfolders = value }
    }

    fun setPhotoramaIntervalSeconds(seconds: Int) = viewModelScope.launch {
        settingsRepository.update { photoramaIntervalSeconds = seconds }
    }

    fun setPhotoramaShuffle(value: Boolean) = viewModelScope.launch {
        settingsRepository.update { photoramaShuffle = value }
    }

    // --- Walkie-Talkie ------------------------------------------------------------------------

    fun setWalkieTalkieEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.update { walkieTalkieEnabled = enabled }
    }

    fun setWalkieTalkieTarget(target: String) = viewModelScope.launch {
        settingsRepository.update { walkieTalkieTarget = target }
    }

    fun setWalkieTalkieMicBoost(percent: Int) = viewModelScope.launch {
        settingsRepository.update { walkieTalkieMicBoostPercent = percent }
    }

    fun setWalkieTalkieIncomingChimeEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.update { walkieTalkieIncomingChimeEnabled = enabled }
    }

    fun setWalkieTalkieIncomingChime(chime: String) = viewModelScope.launch {
        settingsRepository.update { walkieTalkieIncomingChime = chime }
    }

    fun setWalkieTalkieOverlayEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.update { walkieTalkieOverlayEnabled = enabled }
    }

    fun setWalkieTalkieAutoAddDiscovered(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.update { walkieTalkieAutoAddDiscovered = enabled }
    }

    fun pressToTalk(target: String) {
        walkieTalkieEngine.pressToTalk(target)
    }

    fun releaseToTalk() {
        walkieTalkieEngine.releaseToTalk()
    }

    fun previewWalkieTalkieIncomingChime(chime: String) {
        walkieTalkieEngine.previewIncomingChime(chime)
    }

    fun addWalkieTalkiePeer(name: String, ip: String) = viewModelScope.launch {
        settingsRepository.update {
            walkieTalkiePeers = walkieTalkiePeers + WalkieTalkiePeer(name, ip)
        }
    }

    fun removeWalkieTalkiePeer(peer: WalkieTalkiePeer) = viewModelScope.launch {
        settingsRepository.update {
            walkieTalkiePeers = walkieTalkiePeers.filterNot { it.ip == peer.ip }
            if (walkieTalkieTarget == peer.ip) {
                walkieTalkieTarget = WALKIE_TALKIE_TARGET_ALL
            }
        }
    }

    // --- AirPlay -------------------------------------------------------------------------------

    fun setAirPlayEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.update { airplayEnabled = enabled }
    }

    fun setAirPlayAuthMode(mode: String) = viewModelScope.launch {
        settingsRepository.update { airplayAuthMode = mode }
    }

    fun setAirPlayPassword(password: String) = viewModelScope.launch {
        settingsRepository.update { airplayPassword = password }
    }

    fun setAirPlayMirrorPreset(preset: String) = viewModelScope.launch {
        settingsRepository.update { airplayMirrorPreset = preset }
    }

    fun setAirPlayAllowHevc(allow: Boolean) = viewModelScope.launch {
        settingsRepository.update { airplayAllowHevc = allow }
    }

    fun setAirPlayShowClockWidget(show: Boolean) = viewModelScope.launch {
        settingsRepository.update { airplayShowClockWidget = show }
    }

    // --- Browser -------------------------------------------------------------------------------

    fun setBrowserEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.update { browserEnabled = enabled }
    }

    fun setBrowserHomeUrl(url: String) = viewModelScope.launch {
        settingsRepository.update { browserHomeUrl = url }
    }

    fun setBrowserLastVisitedUrl(url: String) = viewModelScope.launch {
        settingsRepository.update { browserLastVisitedUrl = url }
    }

    fun clearBrowserSession() = viewModelScope.launch {
        settingsRepository.update { browserLastVisitedUrl = "" }
    }

    // --- Jellyfin ------------------------------------------------------------------------------

    fun setJellyfinEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.update { jellyfinEnabled = enabled }
    }

    fun setJellyfinServerUrl(url: String) = viewModelScope.launch {
        settingsRepository.update { jellyfinServerUrl = url }
    }

    fun setJellyfinStartPath(path: String) = viewModelScope.launch {
        settingsRepository.update { jellyfinStartPath = path }
    }

    fun setJellyfinDesktopMode(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.update { jellyfinDesktopMode = enabled }
    }

    fun setJellyfinReloadOnOpen(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.update { jellyfinReloadOnOpen = enabled }
    }

    fun setJellyfinOpenExternalLinks(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.update { jellyfinOpenExternalLinks = enabled }
    }

    fun setJellyfinUsername(username: String) = viewModelScope.launch {
        settingsRepository.update { jellyfinUsername = username }
    }

    fun setJellyfinAutoAuth(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.update { jellyfinAutoAuth = enabled }
    }

    fun setJellyfinPasswordDraft(password: String) {
        _extra.update { it.copy(jellyfinPasswordDraft = password) }
    }

    fun saveJellyfinPassword() = viewModelScope.launch {
        settingsRepository.saveJellyfinPassword(_extra.value.jellyfinPasswordDraft)
        _extra.update { it.copy(jellyfinPasswordDraft = "") }
    }

    suspend fun jellyfinPassword(): String = settingsRepository.jellyfinPassword()

    // --- Home Assistant --------------------------------------------------------------------------

    fun setHomeAssistantEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.update { homeAssistantEnabled = enabled }
    }

    fun setHomeAssistantUrl(url: String) = viewModelScope.launch {
        settingsRepository.update { homeAssistantUrl = url }
    }

    fun setHomeAssistantUsername(username: String) = viewModelScope.launch {
        settingsRepository.update { homeAssistantUsername = username }
    }

    fun setHomeAssistantAutoAuth(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.update { homeAssistantAutoAuth = enabled }
    }

    fun setHomeAssistantPasswordDraft(password: String) {
        _extra.update { it.copy(homeAssistantPasswordDraft = password) }
    }

    fun saveHomeAssistantPassword() = viewModelScope.launch {
        settingsRepository.saveHomeAssistantPassword(_extra.value.homeAssistantPasswordDraft)
        _extra.update { it.copy(homeAssistantPasswordDraft = "") }
    }

    suspend fun homeAssistantPassword(): String = settingsRepository.homeAssistantPassword()

    // --- IPTV ----------------------------------------------------------------------------------

    fun setIptvEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.update { iptvEnabled = enabled }
    }

    fun setIptvPortalUrl(url: String) = viewModelScope.launch {
        settingsRepository.update { iptvPortalUrl = url }
    }

    fun setIptvMacAddress(mac: String) = viewModelScope.launch {
        settingsRepository.update { iptvMacAddress = mac }
    }

    /** Generates a fresh Infomir-range MAC rather than leaving the field blank by default - most
     * portals identify/authorize a device by MAC, so a starting value the user can hand to their
     * provider (or that already matches a value they register) is more useful than an empty box. */
    fun regenerateIptvMac() = viewModelScope.launch {
        settingsRepository.update { iptvMacAddress = com.sconcept.mirrordash.iptv.IptvMac.generateRandom() }
    }

    fun setIptvSleepTimeoutSeconds(seconds: Int) = viewModelScope.launch {
        settingsRepository.update { iptvSleepTimeoutSeconds = seconds }
    }

    fun setIptvOpenMuted(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.update { iptvOpenMuted = enabled }
    }

    fun setIptvRecordingPortalUrl(url: String) = viewModelScope.launch {
        settingsRepository.update { iptvRecordingPortalUrl = url }
    }

    fun setIptvRecordingMacAddress(mac: String) = viewModelScope.launch {
        settingsRepository.update { iptvRecordingMacAddress = mac }
    }

    fun regenerateIptvRecordingMac() = viewModelScope.launch {
        settingsRepository.update { iptvRecordingMacAddress = com.sconcept.mirrordash.iptv.IptvMac.generateRandom() }
    }

    fun setIptvRecordingDestination(mode: com.sconcept.mirrordash.iptv.RecordingDestinationMode) = viewModelScope.launch {
        settingsRepository.update { iptvRecordingDestination = mode.storageKey }
    }

    /** Digits only, capped at [com.sconcept.mirrordash.iptv.MAX_PARENTAL_CONTROL_PIN_LENGTH] -
     * enforced here rather than trusting the numeric-keypad UI to only ever send valid input. */
    fun setParentalControlPin(pin: String) = viewModelScope.launch {
        val sanitized = pin.filter { it.isDigit() }.take(com.sconcept.mirrordash.iptv.MAX_PARENTAL_CONTROL_PIN_LENGTH)
        settingsRepository.update { parentalControlPin = sanitized }
    }

    fun resetParentalControlPin() = viewModelScope.launch {
        settingsRepository.update { parentalControlPin = com.sconcept.mirrordash.iptv.DEFAULT_PARENTAL_CONTROL_PIN }
    }

    fun setParentalControlMode(mode: com.sconcept.mirrordash.iptv.ParentalControlMode) = viewModelScope.launch {
        settingsRepository.update { parentalControlMode = mode.storageKey }
    }

    fun setIptvRecordingSmbFolder(folder: String) = viewModelScope.launch {
        settingsRepository.update { iptvRecordingSmbFolder = folder }
    }

    fun setIptvRecordingLocalCapMb(mb: Int) = viewModelScope.launch {
        settingsRepository.update { iptvRecordingLocalCapMb = mb }
    }

    // --- Launcher ------------------------------------------------------------------------------

    fun setDisplayOrientationMode(mode: DisplayOrientationMode) = viewModelScope.launch {
        settingsRepository.update { displayOrientationMode = mode.storageKey() }
    }

    // --- Brightness ------------------------------------------------------------------------------

    fun setBrightnessLevel(level255: Int) = viewModelScope.launch {
        settingsRepository.update { brightnessLevel255 = level255 }
    }

    fun setBrightnessExtraDim(percent: Int) = viewModelScope.launch {
        settingsRepository.update { brightnessExtraDimPercent = percent }
    }

    fun setBrightnessDimTarget(target: String) = viewModelScope.launch {
        settingsRepository.update { brightnessDimTarget = target }
    }

    // --- Night Clock -------------------------------------------------------------------------------

    fun setNightClockBrightnessLevel(level255: Int) = viewModelScope.launch {
        settingsRepository.update { nightClockBrightnessLevel255 = level255 }
    }

    fun setNightClockTextDimPercent(percent: Int) = viewModelScope.launch {
        settingsRepository.update { nightClockTextDimPercent = percent }
    }

    // --- Provisioning ----------------------------------------------------------------------

    /** Called once from [com.sconcept.mirrordash.launcher.MirrorDashActivity] on every cold
     * start where [MirrorDashSettings.provisioningAppliedOnce] is still false - deliberately
     * does NOT set that flag when no file is present, so a unit that's booted before its config
     * file was pushed just tries again next launch instead of missing its one shot. */
    fun autoApplyProvisioningConfigIfPresent() {
        val config = ProvisioningConfigLoader.load(getApplication<Application>()) ?: return
        applyProvisioningConfig(config)
    }

    /** The "Re-apply config file" button in Launcher settings - re-reads and re-applies
     * unconditionally, so editing the on-device file (e.g. a changed password) can be synced
     * without wiping app data. */
    fun reapplyProvisioningConfig() = viewModelScope.launch {
        val config = ProvisioningConfigLoader.load(getApplication<Application>())
        if (config == null) {
            _extra.update {
                it.copy(
                    provisioningStatus = ProvisioningStatus(
                        appliedAt = System.currentTimeMillis(),
                        summary = "No config file found at ${ProvisioningConfigLoader.configFile(getApplication<Application>()).absolutePath}",
                        isError = true,
                    ),
                )
            }
            return@launch
        }
        applyProvisioningConfig(config)
    }

    private fun applyProvisioningConfig(config: ProvisioningConfig) = viewModelScope.launch {
        val applied = mutableListOf<String>()

        config.jellyfin?.let { cfg ->
            settingsRepository.update {
                jellyfinServerUrl = cfg.url
                jellyfinUsername = cfg.username
                jellyfinAutoAuth = cfg.autoAuth
                jellyfinEnabled = true
            }
            settingsRepository.saveJellyfinPassword(cfg.password)
            applied += "Jellyfin"
        }

        config.homeAssistant?.let { cfg ->
            settingsRepository.update {
                homeAssistantUrl = cfg.url
                homeAssistantUsername = cfg.username
                homeAssistantAutoAuth = cfg.autoAuth
                homeAssistantEnabled = true
            }
            settingsRepository.saveHomeAssistantPassword(cfg.password)
            applied += "Home Assistant"
        }

        config.walkieTalkie?.let { cfg ->
            settingsRepository.update {
                walkieTalkieAutoAddDiscovered = cfg.autoAddDiscovered
                walkieTalkieEnabled = true
            }
            applied += "Walkie-Talkie"
        }

        config.iptv?.let { cfg ->
            settingsRepository.update {
                iptvPortalUrl = cfg.url
                iptvMacAddress = com.sconcept.mirrordash.iptv.IptvMac.normalize(cfg.mac)
                iptvOpenMuted = cfg.openMuted
                iptvEnabled = true
            }
            applied += "IPTV"
        }

        config.nas?.let { cfg ->
            setNasPasswordDraft(cfg.password)
            testNasConnection(cfg.server, cfg.shareName.trimStart('\\', '/'), cfg.username, "", true)
            applied += "NAS"
        }

        settingsRepository.update { provisioningAppliedOnce = true }
        _extra.update {
            it.copy(
                provisioningStatus = ProvisioningStatus(
                    appliedAt = System.currentTimeMillis(),
                    summary = if (applied.isEmpty()) "Config file had nothing to apply" else "Applied: ${applied.joinToString(", ")}",
                ),
            )
        }
    }

    companion object {
        fun factory(application: Application, settingsRepository: SettingsRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SettingsViewModel(application, settingsRepository) as T
            }
    }
}
