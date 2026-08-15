package com.sconcept.mirrordash.airplay

import android.content.Context
import android.util.Log
import com.sconcept.mirrordash.settings.AIRPLAY_AUTH_MODE_OPEN
import com.sconcept.mirrordash.settings.AIRPLAY_AUTH_MODE_PASSWORD
import com.sconcept.mirrordash.settings.AIRPLAY_AUTH_MODE_RANDOM_PIN
import com.sconcept.mirrordash.settings.AIRPLAY_MIRROR_PRESET_DISPLAY
import com.sconcept.mirrordash.settings.DeviceNameHelper
import com.sconcept.mirrordash.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.security.SecureRandom

private const val TAG = "AirPlayEngine"

data class AirPlayUiState(
    val enabled: Boolean = false,
    val deviceName: String = "",
    val authMode: String = AIRPLAY_AUTH_MODE_OPEN,
    val password: String = "",
    val mirrorPreset: String = AIRPLAY_MIRROR_PRESET_DISPLAY,
    val allowHevc: Boolean = false,
    val showClockWidget: Boolean = false,
    val receiverRunning: Boolean = false,
    val receiverIp: String = "",
    val clientLabel: String = "",
    val pairingPin: String = "",
    val hasActiveVideo: Boolean = false,
    val lastError: String? = null,
) {
    val statusLabel: String
        get() = when {
            !enabled -> "Off"
            pairingPin.isNotBlank() -> "Enter PIN $pairingPin on your device"
            clientLabel.isNotBlank() -> "Connected to $clientLabel"
            receiverRunning -> "Ready for AirPlay"
            lastError != null -> lastError
            else -> "Starting…"
        }

    /** Mirrors [AirPlayNsdBridge.SessionSnapshot.isSessionActive] - a PIN prompt or a connected
     * client both mean there's something on the Clock page's [AirPlayStatusWidget]/mirror surface
     * worth seeing, even before video traffic actually starts flowing. */
    val isSessionActive: Boolean
        get() = pairingPin.isNotBlank() || clientLabel.isNotBlank() || hasActiveVideo
}

/**
 * Process-wide owner of the *one* native AirPlay receiver (the vendored UxPlay stack only
 * supports a single global session - see app/src/main/cpp/jni-bridge/airplay_jni.c's static
 * `g_receiver`). Mirrors [com.sconcept.mirrordash.walkietalkie.WalkieTalkieEngine]'s shape:
 * settings drive a reactive start/stop/restart pipeline instead of every UI surface (Settings,
 * the Clock widget, [AirPlayReceiverService]) independently calling into the native layer and
 * fighting over the one receiver slot.
 *
 * Unlike BerthierOptions' `AirPlayReceiverController` (which permanently flipped `airplayEnabled`
 * off on the first native start failure), a failure here is surfaced via [AirPlayUiState.lastError]
 * without touching the persisted setting - [AirPlayReceiverService] retries periodically while
 * still enabled, since most real-world failures on a device that boots before its network is
 * fully up are transient, not permanent misconfiguration.
 */
class AirPlayEngine private constructor(context: Context, private val settingsRepository: SettingsRepository) {

    private data class StartConfig(
        val enabled: Boolean,
        val deviceName: String,
        val hwAddressHex: String,
        val authMode: String,
        val password: String,
        val mirrorPreset: String,
        val allowHevc: Boolean,
    )

    private val appContext = context.applicationContext
    private val bridge = AirPlaySessionRegistry.getBridge(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Process-wide like [bridge] itself and this engine's own [bridgeListener] - audio has no
     * on-screen surface to mount/unmount around the way [AirPlayMirrorSurface] does for video, so
     * it's simplest to just always be listening, matching how [AirPlayReceiverService] expects
     * this engine to own the receiver's Android-side lifecycle for as long as the process runs. */
    private val audioDecoder = AirPlayAudioDecoder(onError = { message -> Log.e(TAG, message) })

    private val _sessionSnapshot = MutableStateFlow(bridge.snapshot())
    private val _lastError = MutableStateFlow<String?>(null)

    private val _uiState = MutableStateFlow(AirPlayUiState())
    val uiState: StateFlow<AirPlayUiState> = _uiState

    private val bridgeListener = object : AirPlayNsdBridge.Listener {
        override fun onReceiverRunningChanged(running: Boolean, port: Int) {
            _sessionSnapshot.value = bridge.snapshot()
        }

        override fun onClientChanged(deviceId: String, model: String, name: String) {
            _sessionSnapshot.value = bridge.snapshot()
        }

        override fun onVideoFormatChanged(codec: Int, width: Int, height: Int) {
            _sessionSnapshot.value = bridge.snapshot()
        }

        override fun onVideoFrame(data: ByteArray, ptsUs: Long) = Unit

        override fun onAudioFrame(data: ByteArray, ptsUs: Long) {
            audioDecoder.onAudioFrame(data, ptsUs)
        }

        override fun onVideoSessionEnded() {
            audioDecoder.resetSession()
            _sessionSnapshot.value = bridge.snapshot()
        }

        override fun onPinRequested(pin: String) {
            _sessionSnapshot.value = bridge.snapshot()
        }
    }

    init {
        bridge.addListener(bridgeListener)

        scope.launch {
            ensureHwAddressGenerated()

            launch {
                combine(settingsRepository.settings, _sessionSnapshot, _lastError) { settings, session, error ->
                    AirPlayUiState(
                        enabled = settings.airplayEnabled,
                        deviceName = settings.deviceName.ifBlank { DeviceNameHelper.defaultDeviceName(appContext) },
                        authMode = settings.airplayAuthMode,
                        password = settings.airplayPassword,
                        mirrorPreset = settings.airplayMirrorPreset,
                        allowHevc = settings.airplayAllowHevc,
                        showClockWidget = settings.airplayShowClockWidget,
                        receiverRunning = session.receiverRunning,
                        receiverIp = session.receiverIp,
                        clientLabel = session.clientLabel,
                        pairingPin = session.pairingPin,
                        hasActiveVideo = session.hasActiveVideo,
                        lastError = error,
                    )
                }.collect { _uiState.value = it }
            }

            // hasActiveVideo is time-based (stale 5s after the last frame) - without this, the
            // snapshot only gets recomputed on sparse discrete bridge events (client changed,
            // format changed, session ended) and would freeze at whatever it was, never
            // reflecting "video went quiet" if a client disconnects without a clean teardown.
            launch {
                while (isActive) {
                    kotlinx.coroutines.delay(1000)
                    _sessionSnapshot.value = bridge.snapshot()
                }
            }

            launch {
                settingsRepository.settings
                    .map {
                        StartConfig(
                            enabled = it.airplayEnabled,
                            deviceName = it.deviceName,
                            hwAddressHex = it.airplayHwAddressHex,
                            authMode = it.airplayAuthMode,
                            password = it.airplayPassword,
                            mirrorPreset = it.airplayMirrorPreset,
                            allowHevc = it.airplayAllowHevc,
                        )
                    }
                    .distinctUntilChanged()
                    .collectLatest { config ->
                        if (AirPlayNative.nativeIsRunning()) {
                            AirPlayNative.nativeStopReceiver()
                        }
                        if (config.enabled) {
                            startReceiver(config)
                        } else {
                            _lastError.value = null
                        }
                    }
            }
        }
    }

    /** Called periodically by [AirPlayReceiverService] - restarts the receiver if it's supposed
     * to be running but isn't (native start failure, or the process losing the receiver for any
     * other reason), without needing a settings change to re-trigger the reactive pipeline above. */
    suspend fun watchdogCheck() {
        val settings = settingsRepository.settings.first()
        if (settings.airplayEnabled && !AirPlayNative.nativeIsRunning()) {
            Log.w(TAG, "watchdog: enabled but not running, retrying")
            startReceiver(
                StartConfig(
                    enabled = true,
                    deviceName = settings.deviceName,
                    hwAddressHex = settings.airplayHwAddressHex,
                    authMode = settings.airplayAuthMode,
                    password = settings.airplayPassword,
                    mirrorPreset = settings.airplayMirrorPreset,
                    allowHevc = settings.airplayAllowHevc,
                )
            )
        }
    }

    private suspend fun startReceiver(config: StartConfig) {
        val authMode = when (config.authMode) {
            AIRPLAY_AUTH_MODE_RANDOM_PIN -> AirPlayNative.AUTH_RANDOM_PIN
            AIRPLAY_AUTH_MODE_PASSWORD -> AirPlayNative.AUTH_PASSWORD
            else -> AirPlayNative.AUTH_OPEN
        }
        if (authMode == AirPlayNative.AUTH_PASSWORD && config.password.length < 6) {
            _lastError.value = "Set a password of at least 6 characters, or switch auth mode"
            return
        }

        val name = config.deviceName.ifBlank { DeviceNameHelper.defaultDeviceName(appContext) }
        bridge.receiverNameOverride = name

        val hwAddr = config.hwAddressHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val metrics = appContext.resources.displayMetrics
        val (width, height) = AirPlayMirrorPreset.resolveSize(config.mirrorPreset, metrics.widthPixels, metrics.heightPixels)

        val result = AirPlayNative.nativeStartReceiver(name, hwAddr, width, height, authMode, config.password, config.allowHevc)
        _lastError.value = if (result != 0) "AirPlay receiver failed to start (error $result)" else null
        if (result != 0) {
            Log.w(TAG, "nativeStartReceiver failed: $result")
        }
    }

    private suspend fun ensureHwAddressGenerated() {
        val current = settingsRepository.settings.first().airplayHwAddressHex
        if (current.length == 12) return
        val generated = ByteArray(6).also { SecureRandom().nextBytes(it) }
        val hex = generated.joinToString("") { "%02x".format(it) }
        settingsRepository.update { airplayHwAddressHex = hex }
    }

    companion object {
        @Volatile
        private var instance: AirPlayEngine? = null

        fun get(context: Context, settingsRepository: SettingsRepository): AirPlayEngine =
            instance ?: synchronized(this) {
                instance ?: AirPlayEngine(context.applicationContext, settingsRepository).also { instance = it }
            }
    }
}
