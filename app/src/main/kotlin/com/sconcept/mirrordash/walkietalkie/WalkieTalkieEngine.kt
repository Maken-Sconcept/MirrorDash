package com.sconcept.mirrordash.walkietalkie

import android.content.Context
import com.sconcept.mirrordash.settings.DEFAULT_WALKIE_TALKIE_PORT
import com.sconcept.mirrordash.settings.DeviceNameHelper
import com.sconcept.mirrordash.settings.MirrorDashSettings
import com.sconcept.mirrordash.settings.SettingsRepository
import com.sconcept.mirrordash.settings.WALKIE_TALKIE_TARGET_ALL
import com.sconcept.mirrordash.walkietalkie.model.WalkieTalkieDiscoveredPeer
import com.sconcept.mirrordash.walkietalkie.model.WalkieTalkiePeer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class WalkieTalkieUiState(
    val enabled: Boolean = false,
    val hasMicPermission: Boolean = false,
    val deviceName: String = "",
    val peers: List<WalkieTalkiePeer> = emptyList(),
    val discoveredPeers: List<WalkieTalkieDiscoveredPeer> = emptyList(),
    val target: String = WALKIE_TALKIE_TARGET_ALL,
    val isTransmitting: Boolean = false,
    val lastIncomingFrom: String? = null,
    val overlayEnabled: Boolean = false,
    val pttAnchorX: Float = 0.92f,
    val pttAnchorY: Float = 0.82f,
)

/**
 * Process-wide singleton owning the *one* [WalkieTalkieAudio] instance (and its one bound UDP
 * receive socket) for the whole app. Both the in-app PTT control and
 * [PttOverlayAccessibilityService]'s floating button - which can be alive independently of any
 * Activity - read from and drive this same engine, rather than each starting their own
 * receiver and fighting over the port.
 */
class WalkieTalkieEngine private constructor(context: Context, private val settingsRepository: SettingsRepository) {

    private val appContext = context.applicationContext
    private val audio = WalkieTalkieAudio(appContext)
    private val discovery = WalkieTalkieDiscoveryBridge.get(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _isTransmitting = MutableStateFlow(false)
    private val _lastIncomingFrom = MutableStateFlow<String?>(null)
    private val _discoveredPeers = MutableStateFlow<List<WalkieTalkieDiscoveredPeer>>(emptyList())

    private var latestSettings = MirrorDashSettings()

    private val _uiState = MutableStateFlow(WalkieTalkieUiState())
    val uiState: StateFlow<WalkieTalkieUiState> = _uiState

    private val discoveryListener = object : WalkieTalkieDiscoveryBridge.Listener {
        override fun onNearbyPeersChanged(peers: List<WalkieTalkieDiscoveredPeer>) {
            _discoveredPeers.value = peers
        }
    }

    init {
        audio.onIncomingFrom = { sender -> _lastIncomingFrom.value = sender }
        discovery.addListener(discoveryListener)

        scope.launch {
            combine(
                settingsRepository.settings,
                _isTransmitting,
                _lastIncomingFrom,
                _discoveredPeers,
            ) { settings, transmitting, lastIncoming, discovered ->
                latestSettings = settings
                WalkieTalkieUiState(
                    enabled = settings.walkieTalkieEnabled,
                    hasMicPermission = audio.hasMicPermission(),
                    deviceName = settings.deviceName.ifBlank { DeviceNameHelper.defaultDeviceName(appContext) },
                    peers = settings.walkieTalkiePeers,
                    discoveredPeers = discovered,
                    target = settings.walkieTalkieTarget,
                    isTransmitting = transmitting,
                    lastIncomingFrom = lastIncoming,
                    overlayEnabled = settings.walkieTalkieOverlayEnabled,
                    pttAnchorX = settings.walkieTalkiePttAnchorX,
                    pttAnchorY = settings.walkieTalkiePttAnchorY,
                )
            }.collect { _uiState.value = it }
        }

        scope.launch {
            settingsRepository.settings.collect { settings ->
                discovery.updateAdvertisedInfo(
                    settings.deviceName.ifBlank { DeviceNameHelper.defaultDeviceName(appContext) },
                    settings.walkieTalkiePort,
                )
                discovery.setActive(settings.walkieTalkieEnabled)
                if (settings.walkieTalkieEnabled) {
                    audio.startReceiving(settings.walkieTalkiePort)
                } else {
                    audio.stopReceiving()
                    audio.stopTransmitting()
                    _isTransmitting.value = false
                }
            }
        }
    }

    fun pressToTalk() {
        val state = _uiState.value
        if (!state.enabled || !state.hasMicPermission) return
        val targetIps = resolveTargetIps(state)
        if (targetIps.isEmpty()) return

        audio.startTransmitting(
            targetIps = targetIps,
            port = latestSettings.walkieTalkiePort.takeIf { it > 0 } ?: DEFAULT_WALKIE_TALKIE_PORT,
            deviceName = state.deviceName,
            micBoostPercent = latestSettings.walkieTalkieMicBoostPercent,
        )
        _isTransmitting.value = true
    }

    fun releaseToTalk() {
        audio.stopTransmitting()
        _isTransmitting.value = false
    }

    private fun resolveTargetIps(state: WalkieTalkieUiState): List<String> {
        return if (state.target == WALKIE_TALKIE_TARGET_ALL) {
            state.peers.map { it.ip }
        } else {
            state.peers.filter { it.ip == state.target }.map { it.ip }
        }
    }

    companion object {
        @Volatile
        private var instance: WalkieTalkieEngine? = null

        fun get(context: Context, settingsRepository: SettingsRepository): WalkieTalkieEngine =
            instance ?: synchronized(this) {
                instance ?: WalkieTalkieEngine(context.applicationContext, settingsRepository).also { instance = it }
            }
    }
}
