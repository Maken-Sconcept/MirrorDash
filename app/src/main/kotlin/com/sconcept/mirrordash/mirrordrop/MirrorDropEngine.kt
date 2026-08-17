package com.sconcept.mirrordash.mirrordrop

import android.content.Context
import android.util.Log
import com.sconcept.mirrordash.settings.DeviceNameHelper
import com.sconcept.mirrordash.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

private const val TAG = "MirrorDropServer"

sealed class MirrorDropServerState {
    data object Stopped : MirrorDropServerState()
    data object Starting : MirrorDropServerState()
    data class Running(val port: Int) : MirrorDropServerState()
    data object Stopping : MirrorDropServerState()
    data class Error(val message: String) : MirrorDropServerState()
}

data class MirrorDropUiState(
    val serverState: MirrorDropServerState = MirrorDropServerState.Stopped,
    val deviceName: String = "",
    val localIp: String? = null,
    val wifiConnected: Boolean = false,
    val port: Int = 8765,
    val connectedPeers: List<MirrorDropPeer> = emptyList(),
    val activeShareSession: ShareSession? = null,
)

/** Just the settings this engine actually reacts to (see [com.sconcept.mirrordash.walkietalkie.WalkieTalkieEngine]
 * for why this narrowing + distinctUntilChanged matters on this hardware - an unrelated settings
 * write elsewhere in the app must not bounce the server). */
private data class RelevantMirrorDropSettings(val enabled: Boolean, val port: Int, val deviceName: String) {
    constructor(settings: com.sconcept.mirrordash.settings.MirrorDashSettings) : this(
        enabled = settings.mirrorDropEnabled,
        port = settings.mirrorDropPort,
        deviceName = settings.deviceName,
    )
}

/**
 * Process-wide singleton owning MirrorDrop's server lifecycle (brief §27): STOPPED / STARTING /
 * RUNNING / STOPPING / ERROR, start/stop idempotent, never more than one [MirrorDropServer] bound
 * to a port at a time. Deliberately does not depend on Photobooth's camera/capture code at all
 * (brief §40) - this only needs [SettingsRepository] and a [Context].
 */
class MirrorDropEngine private constructor(
    context: Context,
    private val settingsRepository: SettingsRepository,
    private val photoboothSource: MirrorDropPhotoboothSource,
) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var server: MirrorDropServer? = null
    private var signalingServer: MirrorDropSignalingServer? = null
    private var webRtcManager: MirrorDropWebRtcManager? = null
    private var transferManager: MirrorDropTransferManager? = null
    private var peerObserverJob: Job? = null
    private var currentDeviceName: String = DeviceNameHelper.defaultDeviceName(appContext)

    /** Only ever used as the [MirrorDropTransferManager]'s starting placeholder before any
     * [ShareMode] has been chosen; [startSharing] immediately replaces it. */
    private val contentSource: MirrorDropContentSource = MirrorDropDevTestContentSource()

    /** Outlives individual server start/stop cycles so a session started right before a
     * transient Wi-Fi hiccup doesn't need to be re-created - but see [stopServer], which still
     * revokes it, since a session nobody can reach isn't meaningfully "active". */
    private val shareSessionManager = MirrorDropShareSessionManager()

    private val _uiState = MutableStateFlow(MirrorDropUiState())
    val uiState: StateFlow<MirrorDropUiState> = _uiState

    init {
        scope.launch {
            shareSessionManager.session.collect { session ->
                _uiState.update { it.copy(activeShareSession = session) }
            }
        }
        scope.launch {
            settingsRepository.settings
                .map { RelevantMirrorDropSettings(it) }
                .distinctUntilChanged()
                .collect { settings ->
                    currentDeviceName = settings.deviceName.ifBlank { DeviceNameHelper.defaultDeviceName(appContext) }
                    _uiState.update {
                        it.copy(
                            deviceName = currentDeviceName,
                            port = settings.port,
                            wifiConnected = MirrorDropNetworkUtils.isWifiConnected(appContext),
                            localIp = MirrorDropNetworkUtils.getLocalIpv4Address(),
                        )
                    }
                    if (settings.enabled) {
                        startServer(settings.port)
                    } else {
                        stopServer()
                    }
                }
        }
    }

    /** Starts a new [ShareSession] scoped to exactly the files [mode] resolves to (brief §6/§13),
     * returning the plaintext PIN (if any) once, for the caller to show alongside the QR code -
     * the engine itself never stores it beyond this. */
    fun startSharing(requirePin: Boolean, mode: ShareMode = ShareMode.EntireLibrary): MirrorDropShareStartResult {
        transferManager?.updateShareContent(mode.label, MirrorDropLiveContentSource(mode, photoboothSource))
        return shareSessionManager.start(requirePin)
    }

    /** Called after a new Photobooth session finishes saving (brief §25 - "auto-share the newest
     * montage"): if a share is currently active, immediately pushes a fresh manifest to every
     * connected peer so an already-open browser tab sees new content without needing a manual
     * reload. A no-op if nothing is being shared right now. */
    fun notifyPhotoboothContentChanged() {
        val signaling = signalingServer ?: return
        val transfer = transferManager ?: return
        if (shareSessionManager.session.value == null) return
        signaling.peerManager.peers.value.forEach { peer -> transfer.sendManifest(peer.peerId) }
    }

    /** Immediately invalidates the active token and drops every connected peer (brief §6/§43),
     * without stopping the embedded server - a new session can start right away. */
    fun stopSharing() {
        shareSessionManager.revoke()
        signalingServer?.disconnectAllPeers("Sharing stopped")
    }

    private suspend fun startServer(port: Int) {
        val current = server
        if (current != null && current.isAlive && _uiState.value.serverState is MirrorDropServerState.Running) {
            // Already running on the port we want - idempotent no-op (brief §27).
            if ((_uiState.value.serverState as MirrorDropServerState.Running).port == port) return
            stopServer()
        }
        _uiState.update { it.copy(serverState = MirrorDropServerState.Starting) }
        val signaling = MirrorDropSignalingServer { currentDeviceName }
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val newServer = MirrorDropServer(appContext, port, { currentDeviceName }, signaling, shareSessionManager)
                newServer.start(fi.iki.elonen.NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                newServer
            }
        }
        result.fold(
            onSuccess = { started ->
                server = started
                signalingServer = signaling
                val webRtc = MirrorDropWebRtcManager(appContext) { peerId, message -> signaling.sendTo(peerId, message) }
                webRtcManager = webRtc
                val transfer = MirrorDropTransferManager(contentSource, webRtc::dataChannelFor, signaling::sendTo)
                transferManager = transfer
                signaling.onPeerHello = webRtc::createOfferForPeer
                signaling.onRemoteAnswer = webRtc::onRemoteAnswer
                signaling.onRemoteIceCandidate = webRtc::onRemoteIceCandidate
                signaling.onPeerGone = webRtc::closePeer
                signaling.onRequestManifest = transfer::sendManifest
                signaling.onRequestFiles = transfer::sendFiles
                signaling.start()
                peerObserverJob = scope.launch {
                    signaling.peerManager.peers.collect { peers ->
                        _uiState.update { it.copy(connectedPeers = peers) }
                    }
                }
                _uiState.update { it.copy(serverState = MirrorDropServerState.Running(port)) }
                Log.i(TAG, "MirrorDrop server started on port $port")
            },
            onFailure = { error ->
                server = null
                val message = when (error) {
                    is IOException -> "Couldn't bind to port $port - it may already be in use."
                    else -> error.message ?: "Unknown error starting the server"
                }
                _uiState.update { it.copy(serverState = MirrorDropServerState.Error(message)) }
                Log.w(TAG, "Failed to start MirrorDrop server on port $port", error)
            },
        )
    }

    private suspend fun stopServer() {
        val current = server ?: run {
            if (_uiState.value.serverState !is MirrorDropServerState.Stopped) {
                _uiState.update { it.copy(serverState = MirrorDropServerState.Stopped) }
            }
            return
        }
        _uiState.update { it.copy(serverState = MirrorDropServerState.Stopping) }
        shareSessionManager.revoke()
        peerObserverJob?.cancel()
        peerObserverJob = null
        webRtcManager?.closeAll()
        webRtcManager = null
        transferManager = null
        signalingServer?.stop()
        signalingServer = null
        withContext(Dispatchers.IO) {
            runCatching { current.stop() }
        }
        server = null
        _uiState.update { it.copy(serverState = MirrorDropServerState.Stopped, connectedPeers = emptyList()) }
        Log.i(TAG, "MirrorDrop server stopped")
    }

    companion object {
        @Volatile
        private var instance: MirrorDropEngine? = null

        fun get(context: Context, settingsRepository: SettingsRepository, photoboothSource: MirrorDropPhotoboothSource): MirrorDropEngine =
            instance ?: synchronized(this) {
                instance ?: MirrorDropEngine(context.applicationContext, settingsRepository, photoboothSource).also { instance = it }
            }
    }
}
