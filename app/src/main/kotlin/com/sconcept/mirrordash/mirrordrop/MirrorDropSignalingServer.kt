package com.sconcept.mirrordash.mirrordrop

import android.util.Log
import com.sconcept.mirrordash.mirrordrop.protocol.Answer
import com.sconcept.mirrordash.mirrordrop.protocol.Heartbeat
import com.sconcept.mirrordash.mirrordrop.protocol.Hello
import com.sconcept.mirrordash.mirrordrop.protocol.IceCandidateMessage
import com.sconcept.mirrordash.mirrordrop.protocol.MAX_SIGNAL_MESSAGE_BYTES
import com.sconcept.mirrordash.mirrordrop.protocol.PeerJoined
import com.sconcept.mirrordash.mirrordrop.protocol.PeerLeft
import com.sconcept.mirrordash.mirrordrop.protocol.RequestFiles
import com.sconcept.mirrordash.mirrordrop.protocol.RequestManifest
import com.sconcept.mirrordash.mirrordrop.protocol.SignalError
import com.sconcept.mirrordash.mirrordrop.protocol.SignalErrorCodes
import com.sconcept.mirrordash.mirrordrop.protocol.SignalMessage
import com.sconcept.mirrordash.mirrordrop.protocol.Welcome
import com.sconcept.mirrordash.mirrordrop.protocol.mirrorDropJson
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException

private const val TAG = "MirrorDropSignal"
private const val HEARTBEAT_INTERVAL_MS = 15_000L
private const val HEARTBEAT_TIMEOUT_MS = 45_000L

/**
 * Owns the `/signal` protocol (brief §19): peer identity assignment, hello/welcome, peer presence
 * broadcast, protocol-version rejection, message-size limits, and a ping/timeout heartbeat.
 * WebRTC offer/answer/ICE relay (Phase 4) and manifest/transfer coordination (Phase 5/7) hang off
 * the same [handleMessage] dispatch once those phases add their `when` branches - this class
 * doesn't touch WebRTC or file I/O itself, only routes typed messages between peers and Android.
 */
class MirrorDropSignalingServer(private val deviceNameProvider: () -> String) {

    val peerManager = MirrorDropPeerManager()

    /** Wired up by [MirrorDropEngine] to a [MirrorDropWebRtcManager] once both exist - kept as
     * plain function references rather than a formal listener interface since there's exactly
     * one implementation and no need to support more than one. */
    var onPeerHello: ((peerId: String) -> Unit)? = null
    var onRemoteAnswer: ((peerId: String, sdp: String) -> Unit)? = null
    var onRemoteIceCandidate: ((peerId: String, candidate: String, sdpMid: String?, sdpMLineIndex: Int) -> Unit)? = null
    var onPeerGone: ((peerId: String) -> Unit)? = null
    var onRequestManifest: ((peerId: String) -> Unit)? = null
    var onRequestFiles: ((peerId: String, fileIds: List<String>) -> Unit)? = null

    private val connections = ConcurrentHashMap<String, MirrorDropSocketHandle>()
    private val lastPongAtMs = ConcurrentHashMap<String, Long>()
    private var heartbeatJob: Job? = null
    private var scope: CoroutineScope? = null

    /** Minimal surface the NanoWSD-specific [MirrorDropWebSocketConnection] exposes back to this
     * class, so nothing here depends on NanoWSD types directly. */
    interface MirrorDropSocketHandle {
        fun sendText(text: String)
        fun sendPing()
        fun closeWith(reason: String)
    }

    fun start() {
        val newScope = CoroutineScope(Dispatchers.Default)
        scope = newScope
        heartbeatJob = newScope.launch { heartbeatLoop() }
    }

    fun stop() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        connections.values.toList().forEach { it.closeWith("Server stopping") }
        connections.clear()
        lastPongAtMs.clear()
        peerManager.clear()
        scope = null
    }

    /** "Stop Sharing" (brief §6/§43): drop every currently-connected peer immediately without
     * tearing down the embedded server itself, so a new share session can start fresh right after.
     * Each close flows through the normal [onConnectionClosed] path (broadcast PeerLeft, notify
     * [onPeerGone] so the WebRTC side tears down too) via the connection's own onClose callback. */
    fun disconnectAllPeers(reason: String) {
        connections.values.toList().forEach { it.closeWith(reason) }
    }

    fun onConnectionOpened(handle: MirrorDropSocketHandle): String {
        val peerId = UUID.randomUUID().toString()
        connections[peerId] = handle
        lastPongAtMs[peerId] = System.currentTimeMillis()
        Log.i(TAG, "Peer $peerId connected (${connections.size} total)")
        return peerId
    }

    fun onConnectionClosed(peerId: String) {
        connections.remove(peerId)
        lastPongAtMs.remove(peerId)
        if (peerManager.peers.value.any { it.peerId == peerId }) {
            peerManager.peerDisconnected(peerId)
            broadcastExcept(peerId, PeerLeft(peerId = peerId))
        }
        onPeerGone?.invoke(peerId)
        Log.i(TAG, "Peer $peerId disconnected (${connections.size} remaining)")
    }

    fun onPong(peerId: String) {
        lastPongAtMs[peerId] = System.currentTimeMillis()
    }

    fun handleMessage(peerId: String, raw: String) {
        if (raw.toByteArray(Charsets.UTF_8).size > MAX_SIGNAL_MESSAGE_BYTES) {
            sendError(peerId, SignalErrorCodes.MESSAGE_TOO_LARGE, "Message exceeds the maximum size")
            return
        }
        val message = runCatching { mirrorDropJson.decodeFromString(SignalMessage.serializer(), raw) }
            .getOrElse { error ->
                if (error is SerializationException || error is IllegalArgumentException) {
                    sendError(peerId, SignalErrorCodes.MALFORMED_MESSAGE, "Couldn't parse that message")
                } else {
                    Log.w(TAG, "Unexpected error parsing signal message", error)
                }
                return
            }
        if (message.protocolVersion != MIRRORDROP_PROTOCOL_VERSION) {
            sendError(
                peerId,
                SignalErrorCodes.UNSUPPORTED_PROTOCOL_VERSION,
                "This mirror speaks protocol version $MIRRORDROP_PROTOCOL_VERSION",
            )
            connections[peerId]?.closeWith("Unsupported protocol version")
            return
        }

        when (message) {
            is Hello -> {
                peerManager.peerConnected(peerId, message.deviceName.ifBlank { "Guest" })
                sendTo(peerId, Welcome(peerId = peerId, deviceName = deviceNameProvider()))
                broadcastExcept(peerId, PeerJoined(peerId = peerId))
                onPeerHello?.invoke(peerId)
            }
            is Answer -> onRemoteAnswer?.invoke(peerId, message.sdp)
            is IceCandidateMessage -> onRemoteIceCandidate?.invoke(peerId, message.candidate, message.sdpMid, message.sdpMLineIndex ?: 0)
            is Heartbeat -> lastPongAtMs[peerId] = System.currentTimeMillis()
            is RequestManifest -> onRequestManifest?.invoke(peerId)
            is RequestFiles -> onRequestFiles?.invoke(peerId, message.fileIds)
            // Manifest/transfer *replies* (manifest, transferReady, transferComplete) are only ever
            // sent by the mirror, never received from a browser - no-op if one somehow arrives.
            else -> Unit
        }
    }

    fun sendTo(peerId: String, message: SignalMessage) {
        connections[peerId]?.sendText(mirrorDropJson.encodeToString(SignalMessage.serializer(), message))
    }

    private fun broadcastExcept(excludedPeerId: String?, message: SignalMessage) {
        val encoded = mirrorDropJson.encodeToString(SignalMessage.serializer(), message)
        connections.forEach { (peerId, handle) ->
            if (peerId != excludedPeerId) handle.sendText(encoded)
        }
    }

    private fun sendError(peerId: String, code: String, humanMessage: String) {
        sendTo(peerId, SignalError(code = code, message = humanMessage))
    }

    private suspend fun heartbeatLoop() {
        val currentScope = scope ?: return
        while (currentScope.isActive) {
            delay(HEARTBEAT_INTERVAL_MS)
            val now = System.currentTimeMillis()
            val stale = mutableListOf<String>()
            connections.forEach { (peerId, handle) ->
                val last = lastPongAtMs[peerId] ?: now
                if (now - last > HEARTBEAT_TIMEOUT_MS) {
                    stale.add(peerId)
                } else {
                    handle.sendPing()
                }
            }
            stale.forEach { peerId ->
                Log.i(TAG, "Closing stale peer $peerId (no pong within timeout)")
                connections[peerId]?.closeWith("Heartbeat timeout")
                onConnectionClosed(peerId)
            }
        }
    }
}
