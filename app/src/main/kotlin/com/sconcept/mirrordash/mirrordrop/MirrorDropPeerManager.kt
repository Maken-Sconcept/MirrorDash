package com.sconcept.mirrordash.mirrordrop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

enum class MirrorDropPeerConnectionState { CONNECTING, CONNECTED, DISCONNECTED }

data class MirrorDropPeer(
    val peerId: String,
    val displayName: String,
    val connectedAtMs: Long,
    val connectionState: MirrorDropPeerConnectionState = MirrorDropPeerConnectionState.CONNECTED,
)

/**
 * Pure state holder for who's currently connected (brief §24/§51) - the signaling server owns
 * the actual sockets and pushes state here rather than callers reaching into socket internals.
 * Kept intentionally dumb: no networking, no WebRTC, just a queryable/observable peer list.
 */
class MirrorDropPeerManager {

    private val _peers = MutableStateFlow<List<MirrorDropPeer>>(emptyList())
    val peers: StateFlow<List<MirrorDropPeer>> = _peers

    fun peerConnected(peerId: String, displayName: String) {
        _peers.update { current ->
            if (current.any { it.peerId == peerId }) return@update current
            current + MirrorDropPeer(peerId, displayName, System.currentTimeMillis())
        }
    }

    fun peerDisconnected(peerId: String) {
        _peers.update { current -> current.filterNot { it.peerId == peerId } }
    }

    fun clear() {
        _peers.update { emptyList() }
    }
}
