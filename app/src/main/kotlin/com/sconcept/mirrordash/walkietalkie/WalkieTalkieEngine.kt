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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Just the fields the NSD-registration/receive-socket collector below actually reads, so
 * [kotlinx.coroutines.flow.distinctUntilChanged] can tell "irrelevant settings write elsewhere in
 * the app" apart from "something walkie-talkie actually cares about changed". */
private data class RelevantWalkieSettings(
    val deviceName: String,
    val port: Int,
    val enabled: Boolean,
    val chimeEnabled: Boolean,
    val chimeToneKey: String,
) {
    constructor(settings: MirrorDashSettings) : this(
        deviceName = settings.deviceName,
        port = settings.walkieTalkiePort,
        enabled = settings.walkieTalkieEnabled,
        chimeEnabled = settings.walkieTalkieIncomingChimeEnabled,
        chimeToneKey = settings.walkieTalkieIncomingChime,
    )
}

data class WalkieTalkieUiState(
    val enabled: Boolean = false,
    val hasMicPermission: Boolean = false,
    val deviceName: String = "",
    val peers: List<WalkieTalkiePeer> = emptyList(),
    val discoveredPeers: List<WalkieTalkieDiscoveredPeer> = emptyList(),
    val target: String = WALKIE_TALKIE_TARGET_ALL,
    val isTransmitting: Boolean = false,
    val lastIncomingFrom: String? = null,
    val activeIncomingPeerIp: String? = null,
    val activeIncomingPeerName: String? = null,
    val activeTransmitTarget: String? = null,
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
    private val _activeIncomingPeerIp = MutableStateFlow<String?>(null)
    private val _activeIncomingPeerName = MutableStateFlow<String?>(null)
    private val _activeTransmitTarget = MutableStateFlow<String?>(null)
    private val _discoveredPeers = MutableStateFlow<List<WalkieTalkieDiscoveredPeer>>(emptyList())
    private var activeIncomingClearJob: Job? = null

    private var latestSettings = MirrorDashSettings()

    private val _uiState = MutableStateFlow(WalkieTalkieUiState())
    val uiState: StateFlow<WalkieTalkieUiState> = _uiState

    private val discoveryListener = object : WalkieTalkieDiscoveryBridge.Listener {
        override fun onNearbyPeersChanged(peers: List<WalkieTalkieDiscoveredPeer>) {
            _discoveredPeers.value = peers
        }
    }

    init {
        audio.onIncomingFrom = { senderName, senderIp ->
            _lastIncomingFrom.value = senderName
            _activeIncomingPeerName.value = senderName
            _activeIncomingPeerIp.value = senderIp.ifBlank { null }
            activeIncomingClearJob?.cancel()
            activeIncomingClearJob = scope.launch {
                delay(280)
                clearActiveIncomingPeer()
            }
        }
        discovery.addListener(discoveryListener)

        scope.launch {
            combine(
                settingsRepository.settings,
                _isTransmitting,
                _lastIncomingFrom,
                _activeIncomingPeerIp,
                _activeIncomingPeerName,
                _activeTransmitTarget,
                _discoveredPeers,
            ) { values ->
                val settings = values[0] as MirrorDashSettings
                val transmitting = values[1] as Boolean
                val lastIncoming = values[2] as String?
                val activeIncomingIp = values[3] as String?
                val activeIncomingName = values[4] as String?
                val activeTransmitTarget = values[5] as String?
                @Suppress("UNCHECKED_CAST")
                val discovered = values[6] as List<WalkieTalkieDiscoveredPeer>
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
                    activeIncomingPeerIp = activeIncomingIp,
                    activeIncomingPeerName = activeIncomingName,
                    activeTransmitTarget = activeTransmitTarget,
                    overlayEnabled = settings.walkieTalkieOverlayEnabled,
                    pttAnchorX = settings.walkieTalkiePttAnchorX,
                    pttAnchorY = settings.walkieTalkiePttAnchorY,
                )
            }.collect { _uiState.value = it }
        }

        scope.launch {
            // Scoped to only what this block actually reads, with distinctUntilChanged - without
            // it, this ran on *every* settings write app-wide (volume, last channel, anything),
            // not just walkie-talkie ones, each one doing a full NSD unregister+register cycle on
            // Main.immediate. On this hardware a burst of those (e.g. several unrelated settings
            // writes landing close together) was enough to visibly freeze the screen - confirmed
            // live via logcat showing rapid NsdManager register/unregister churn during a freeze.
            settingsRepository.settings
                .map { RelevantWalkieSettings(it) }
                .distinctUntilChanged()
                .collect { settings ->
                    discovery.updateAdvertisedInfo(
                        settings.deviceName.ifBlank { DeviceNameHelper.defaultDeviceName(appContext) },
                        settings.port,
                    )
                    audio.updateIncomingChime(enabled = settings.chimeEnabled, toneKey = settings.chimeToneKey)
                    discovery.setActive(settings.enabled)
                    if (settings.enabled) {
                        audio.startReceiving(settings.port)
                    } else {
                        audio.stopReceiving()
                        audio.stopTransmitting()
                        _isTransmitting.value = false
                        _activeTransmitTarget.value = null
                        clearActiveIncomingPeer()
                    }
                }
        }
    }

    fun pressToTalk(target: String = _uiState.value.target) {
        val state = _uiState.value
        if (!state.enabled || !state.hasMicPermission) return
        val targetIps = resolveTargetIps(state.peers, target)
        if (targetIps.isEmpty()) return

        audio.startTransmitting(
            targetIps = targetIps,
            port = latestSettings.walkieTalkiePort.takeIf { it > 0 } ?: DEFAULT_WALKIE_TALKIE_PORT,
            deviceName = state.deviceName,
            micBoostPercent = latestSettings.walkieTalkieMicBoostPercent,
        )
        _isTransmitting.value = true
        _activeTransmitTarget.value = target
    }

    fun releaseToTalk() {
        audio.stopTransmitting()
        _isTransmitting.value = false
        _activeTransmitTarget.value = null
    }

    fun previewIncomingChime(toneKey: String) {
        audio.previewIncomingChime(toneKey)
    }

    private fun resolveTargetIps(peers: List<WalkieTalkiePeer>, target: String): List<String> {
        return if (target == WALKIE_TALKIE_TARGET_ALL) {
            peers.map { it.ip }
        } else {
            peers.filter { it.ip == target }.map { it.ip }
        }
    }

    private fun clearActiveIncomingPeer() {
        activeIncomingClearJob?.cancel()
        activeIncomingClearJob = null
        _activeIncomingPeerIp.value = null
        _activeIncomingPeerName.value = null
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
