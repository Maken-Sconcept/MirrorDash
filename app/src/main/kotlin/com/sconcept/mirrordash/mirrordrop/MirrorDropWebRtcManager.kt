package com.sconcept.mirrordash.mirrordrop

import android.content.Context
import android.util.Log
import com.sconcept.mirrordash.mirrordrop.protocol.SignalMessage
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

private const val TAG = "MirrorDropRTC"
const val MIRRORDROP_DATA_CHANNEL_LABEL = "mirrordrop-transfer"

enum class MirrorDropRtcConnectionState { NEW, CONNECTING, CONNECTED, DISCONNECTED, FAILED, CLOSED }

/**
 * Owns the native WebRTC side (brief §20/§34): one process-wide [PeerConnectionFactory], one
 * [PeerConnection] + [DataChannel] per connected browser peer. Android is always the offerer -
 * it creates the DataChannel and SDP offer as soon as a peer says `hello` (see
 * [MirrorDropSignalingServer.onPeerHello]); the browser only answers.
 *
 * LAN-first (brief §18/§31): [iceServers] defaults to empty (host candidates only, no STUN/TURN
 * needed on a local network), but stays an injectable constructor parameter for a future
 * remote-sharing mode.
 */
class MirrorDropWebRtcManager(
    context: Context,
    private val iceServers: List<PeerConnection.IceServer> = emptyList(),
    private val sendSignal: (peerId: String, message: SignalMessage) -> Unit,
) {
    private val appContext = context.applicationContext
    private val factory: PeerConnectionFactory by lazy { createFactory() }
    private val peerConnections = ConcurrentHashMap<String, PeerConnection>()
    private val dataChannels = ConcurrentHashMap<String, DataChannel>()

    private val _connectionStates = MutableStateFlow<Map<String, MirrorDropRtcConnectionState>>(emptyMap())
    val connectionStates: StateFlow<Map<String, MirrorDropRtcConnectionState>> = _connectionStates

    private val _dataChannelOpenPeerIds = MutableStateFlow<Set<String>>(emptySet())
    val dataChannelOpenPeerIds: StateFlow<Set<String>> = _dataChannelOpenPeerIds

    private fun createFactory(): PeerConnectionFactory {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions(),
        )
        return PeerConnectionFactory.builder().createPeerConnectionFactory()
    }

    fun createOfferForPeer(peerId: String) {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        val connection = factory.createPeerConnection(rtcConfig, PeerObserver(peerId)) ?: run {
            Log.w(TAG, "createPeerConnection returned null for $peerId")
            return
        }
        peerConnections[peerId] = connection
        updateState(peerId, MirrorDropRtcConnectionState.NEW)

        val channel = connection.createDataChannel(MIRRORDROP_DATA_CHANNEL_LABEL, DataChannel.Init().apply { ordered = true })
        dataChannels[peerId] = channel
        channel.registerObserver(DataChannelObserver(peerId, channel))

        connection.createOffer(
            object : SdpObserver by NoopSdpObserver {
                override fun onCreateSuccess(desc: SessionDescription) {
                    connection.setLocalDescription(
                        object : SdpObserver by NoopSdpObserver {
                            override fun onSetSuccess() {
                                sendSignal(peerId, com.sconcept.mirrordash.mirrordrop.protocol.Offer(sdp = desc.description))
                            }

                            override fun onSetFailure(error: String) {
                                Log.w(TAG, "setLocalDescription(offer) failed for $peerId: $error")
                            }
                        },
                        desc,
                    )
                }

                override fun onCreateFailure(error: String) {
                    Log.w(TAG, "createOffer failed for $peerId: $error")
                }
            },
            MediaConstraints(),
        )
    }

    fun onRemoteAnswer(peerId: String, sdp: String) {
        val connection = peerConnections[peerId] ?: return
        connection.setRemoteDescription(NoopSdpObserver, SessionDescription(SessionDescription.Type.ANSWER, sdp))
    }

    fun onRemoteIceCandidate(peerId: String, candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
        val connection = peerConnections[peerId] ?: return
        connection.addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, candidate))
    }

    /** Only [MirrorDropTransferManager] calls this, and only once the channel is OPEN - a peer
     * mid-negotiation or already closed simply gets no file bytes sent its way. */
    fun dataChannelFor(peerId: String): DataChannel? =
        dataChannels[peerId]?.takeIf { it.state() == DataChannel.State.OPEN }

    fun closePeer(peerId: String) {
        dataChannels.remove(peerId)?.let { runCatching { it.close() } }
        peerConnections.remove(peerId)?.let { runCatching { it.close() } }
        _dataChannelOpenPeerIds.update { it - peerId }
        updateState(peerId, MirrorDropRtcConnectionState.CLOSED)
    }

    fun closeAll() {
        peerConnections.keys.toList().forEach { closePeer(it) }
    }

    private fun updateState(peerId: String, state: MirrorDropRtcConnectionState) {
        _connectionStates.update { it + (peerId to state) }
    }

    private inner class PeerObserver(private val peerId: String) : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {
            sendSignal(
                peerId,
                com.sconcept.mirrordash.mirrordrop.protocol.IceCandidateMessage(
                    candidate = candidate.sdp,
                    sdpMid = candidate.sdpMid,
                    sdpMLineIndex = candidate.sdpMLineIndex,
                ),
            )
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
            updateState(peerId, newState.toMirrorDropState())
        }

        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) = Unit
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) = Unit
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
        override fun onAddStream(stream: MediaStream) = Unit
        override fun onRemoveStream(stream: MediaStream) = Unit
        override fun onDataChannel(channel: DataChannel) = Unit
        override fun onRenegotiationNeeded() = Unit
        override fun onSignalingChange(newState: PeerConnection.SignalingState) = Unit
        override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) = Unit
    }

    private inner class DataChannelObserver(
        private val peerId: String,
        private val channel: DataChannel,
    ) : DataChannel.Observer {
        override fun onStateChange() {
            Log.i(TAG, "DataChannel for $peerId -> ${channel.state()}")
            if (channel.state() == DataChannel.State.OPEN) {
                _dataChannelOpenPeerIds.update { it + peerId }
            } else {
                _dataChannelOpenPeerIds.update { it - peerId }
            }
        }

        override fun onMessage(buffer: DataChannel.Buffer) {
            val bytes = ByteArray(buffer.data.remaining())
            buffer.data.get(bytes)
            Log.i(TAG, "DataChannel message from $peerId: ${bytes.size} bytes")
        }

        override fun onBufferedAmountChange(previousAmount: Long) = Unit
    }
}

private fun PeerConnection.PeerConnectionState.toMirrorDropState(): MirrorDropRtcConnectionState = when (this) {
    PeerConnection.PeerConnectionState.NEW -> MirrorDropRtcConnectionState.NEW
    PeerConnection.PeerConnectionState.CONNECTING -> MirrorDropRtcConnectionState.CONNECTING
    PeerConnection.PeerConnectionState.CONNECTED -> MirrorDropRtcConnectionState.CONNECTED
    PeerConnection.PeerConnectionState.DISCONNECTED -> MirrorDropRtcConnectionState.DISCONNECTED
    PeerConnection.PeerConnectionState.FAILED -> MirrorDropRtcConnectionState.FAILED
    PeerConnection.PeerConnectionState.CLOSED -> MirrorDropRtcConnectionState.CLOSED
}

private object NoopSdpObserver : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) = Unit
    override fun onSetSuccess() = Unit
    override fun onCreateFailure(p0: String?) = Unit
    override fun onSetFailure(p0: String?) = Unit
}
