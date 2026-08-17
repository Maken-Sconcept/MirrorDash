package com.sconcept.mirrordash.mirrordrop.protocol

import com.sconcept.mirrordash.mirrordrop.MIRRORDROP_PROTOCOL_VERSION
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

/**
 * The `/signal` WebSocket protocol (brief §19/§46) - an explicit, typed message set instead of
 * passing arbitrary JSON around. Every message carries [protocolVersion] so the Android and
 * browser clients can reject a mismatch instead of misbehaving on unrecognized fields.
 *
 * Only `hello`/`peerJoined`/`peerLeft`/`heartbeat`/`error` are actually handled as of this phase;
 * the rest of the shapes (offer/answer/ICE, manifest, transfer) are defined now so the protocol
 * doesn't need reshaping later, but their handling lands in subsequent phases.
 */
@Serializable
sealed class SignalMessage {
    abstract val protocolVersion: Int
}

/** Client -> server, sent once right after the socket opens. No client-supplied peer identity -
 * the server assigns [Welcome.peerId] itself so a browser can't spoof another peer's ID. */
@Serializable
@SerialName("hello")
data class Hello(
    override val protocolVersion: Int = MIRRORDROP_PROTOCOL_VERSION,
    val deviceName: String,
) : SignalMessage()

@Serializable
@SerialName("welcome")
data class Welcome(
    override val protocolVersion: Int = MIRRORDROP_PROTOCOL_VERSION,
    val peerId: String,
    val deviceName: String,
) : SignalMessage()

@Serializable
@SerialName("peerJoined")
data class PeerJoined(
    override val protocolVersion: Int = MIRRORDROP_PROTOCOL_VERSION,
    val peerId: String,
) : SignalMessage()

@Serializable
@SerialName("peerLeft")
data class PeerLeft(
    override val protocolVersion: Int = MIRRORDROP_PROTOCOL_VERSION,
    val peerId: String,
) : SignalMessage()

@Serializable
@SerialName("offer")
data class Offer(
    override val protocolVersion: Int = MIRRORDROP_PROTOCOL_VERSION,
    val fromPeerId: String? = null,
    val toPeerId: String? = null,
    val sdp: String,
) : SignalMessage()

@Serializable
@SerialName("answer")
data class Answer(
    override val protocolVersion: Int = MIRRORDROP_PROTOCOL_VERSION,
    val fromPeerId: String? = null,
    val toPeerId: String? = null,
    val sdp: String,
) : SignalMessage()

@Serializable
@SerialName("iceCandidate")
data class IceCandidateMessage(
    override val protocolVersion: Int = MIRRORDROP_PROTOCOL_VERSION,
    val fromPeerId: String? = null,
    val toPeerId: String? = null,
    val candidate: String,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null,
) : SignalMessage()

@Serializable
@SerialName("requestManifest")
data class RequestManifest(
    override val protocolVersion: Int = MIRRORDROP_PROTOCOL_VERSION,
) : SignalMessage()

@Serializable
data class ManifestFileEntry(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long,
)

@Serializable
@SerialName("manifest")
data class ManifestMessage(
    override val protocolVersion: Int = MIRRORDROP_PROTOCOL_VERSION,
    val sessionLabel: String,
    val files: List<ManifestFileEntry>,
) : SignalMessage()

@Serializable
@SerialName("requestFiles")
data class RequestFiles(
    override val protocolVersion: Int = MIRRORDROP_PROTOCOL_VERSION,
    val fileIds: List<String>,
) : SignalMessage()

@Serializable
@SerialName("transferReady")
data class TransferReady(
    override val protocolVersion: Int = MIRRORDROP_PROTOCOL_VERSION,
    val transferId: String,
    val fileId: String,
) : SignalMessage()

@Serializable
@SerialName("transferComplete")
data class TransferComplete(
    override val protocolVersion: Int = MIRRORDROP_PROTOCOL_VERSION,
    val transferId: String,
    val success: Boolean,
) : SignalMessage()

@Serializable
@SerialName("heartbeat")
data class Heartbeat(
    override val protocolVersion: Int = MIRRORDROP_PROTOCOL_VERSION,
) : SignalMessage()

@Serializable
@SerialName("error")
data class SignalError(
    override val protocolVersion: Int = MIRRORDROP_PROTOCOL_VERSION,
    val code: String,
    val message: String,
) : SignalMessage()

object SignalErrorCodes {
    const val UNSUPPORTED_PROTOCOL_VERSION = "unsupported_protocol_version"
    const val MESSAGE_TOO_LARGE = "message_too_large"
    const val MALFORMED_MESSAGE = "malformed_message"
    const val UNAUTHORIZED = "unauthorized"
    const val NOT_FOUND = "not_found"

    // ShareSession authorization outcomes (brief §6/§43) - distinct from the generic UNAUTHORIZED
    // above so the browser can react differently (e.g. show a PIN prompt vs. a dead-link message).
    const val NO_ACTIVE_SESSION = "no_active_session"
    const val INVALID_TOKEN = "invalid_token"
    const val SESSION_EXPIRED = "session_expired"
    const val PIN_REQUIRED = "pin_required"
    const val INVALID_PIN = "invalid_pin"
}

/** Shared (de)serializer for the whole app - unknown fields ignored so a newer browser client
 * sending extra fields doesn't break an older Android build, and vice versa. */
val mirrorDropJson = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** Signaling messages are small control frames, not file payloads - bounding them here catches
 * a malformed/hostile client rather than letting an oversized text frame through (brief §36). */
const val MAX_SIGNAL_MESSAGE_BYTES = 64 * 1024
