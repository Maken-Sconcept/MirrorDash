package com.sconcept.mirrordash.mirrordrop

import android.content.Context
import android.util.Log
import com.sconcept.mirrordash.mirrordrop.protocol.SignalError
import com.sconcept.mirrordash.mirrordrop.protocol.SignalErrorCodes
import com.sconcept.mirrordash.mirrordrop.protocol.mirrorDropJson
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import java.io.ByteArrayInputStream
import java.io.IOException
import org.json.JSONObject

private const val TAG = "MirrorDropServer"
const val MIRRORDROP_PROTOCOL_VERSION = 1
const val MIRRORDROP_SIGNAL_PATH = "/signal"
private const val REJECT_AFTER_OPEN_DELAY_MS = 50L

/**
 * Serves the MirrorDrop receiver web app (bundled as plain assets, no JS build step - brief §11)
 * plus a couple of small JSON routes, and hosts the `/signal` WebSocket endpoint on the same port
 * (brief §45's route table). Static routes are a fixed whitelist of exact paths rather than
 * mapping the request URI onto an asset path directly - even though [Context.getAssets] has no
 * `../` escape of its own, keeping a strict route table matches the "never resolve a browser-
 * supplied path" posture the rest of MirrorDrop follows (brief §14/§45).
 */
class MirrorDropServer(
    private val context: Context,
    port: Int,
    private val deviceNameProvider: () -> String,
    private val signalingServer: MirrorDropSignalingServer,
    private val shareSessionManager: MirrorDropShareSessionManager,
) : NanoWSD(port) {

    private val staticRoutes = mapOf(
        "/" to ("mirrordrop/index.html" to "text/html"),
        "/index.html" to ("mirrordrop/index.html" to "text/html"),
        "/app.js" to ("mirrordrop/app.js" to "text/javascript"),
        "/style.css" to ("mirrordrop/style.css" to "text/css"),
    )

    override fun serveHttp(session: IHTTPSession): Response {
        val uri = session.uri
        return when {
            uri == "/health" -> healthResponse()
            staticRoutes.containsKey(uri) -> serveStaticAsset(uri)
            else -> NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
        }
    }

    override fun openWebSocket(handshake: IHTTPSession): WebSocket =
        MirrorDropWebSocketConnection(
            handshakeRequest = handshake,
            signalingServer = signalingServer,
            isSignalPath = handshake.uri == MIRRORDROP_SIGNAL_PATH,
            shareSessionManager = shareSessionManager,
            token = handshake.parms["token"],
            pin = handshake.parms["pin"],
        )

    private fun healthResponse(): Response {
        val body = JSONObject().apply {
            put("status", "ok")
            put("deviceName", deviceNameProvider())
            put("protocolVersion", MIRRORDROP_PROTOCOL_VERSION)
        }
        return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json", body.toString())
    }

    private fun serveStaticAsset(uri: String): Response {
        val (assetPath, mime) = staticRoutes.getValue(uri)
        return try {
            val bytes = context.assets.open(assetPath).use { it.readBytes() }
            NanoHTTPD.newFixedLengthResponse(Response.Status.OK, mime, ByteArrayInputStream(bytes), bytes.size.toLong())
        } catch (e: IOException) {
            Log.w(TAG, "Failed to serve asset $assetPath", e)
            NanoHTTPD.newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Server error")
        }
    }
}

/**
 * NanoWSD-specific glue: translates [NanoWSD.WebSocket] lifecycle callbacks into
 * [MirrorDropSignalingServer] calls. Any upgrade request to a path other than [MIRRORDROP_SIGNAL_PATH]
 * closes immediately - MirrorDrop only exposes one WebSocket endpoint.
 */
private class MirrorDropWebSocketConnection(
    handshakeRequest: NanoHTTPD.IHTTPSession,
    private val signalingServer: MirrorDropSignalingServer,
    private val isSignalPath: Boolean,
    private val shareSessionManager: MirrorDropShareSessionManager,
    private val token: String?,
    private val pin: String?,
) : NanoWSD.WebSocket(handshakeRequest) {

    private var peerId: String? = null

    override fun onOpen() {
        if (!isSignalPath) {
            close(NanoWSD.WebSocketFrame.CloseCode.PolicyViolation, "Unknown path", false)
            return
        }
        val authResult = shareSessionManager.authorize(token, pin)
        if (authResult != ShareAuthResult.OK) {
            val code = when (authResult) {
                ShareAuthResult.NO_ACTIVE_SESSION -> SignalErrorCodes.NO_ACTIVE_SESSION
                ShareAuthResult.INVALID_TOKEN -> SignalErrorCodes.INVALID_TOKEN
                ShareAuthResult.EXPIRED -> SignalErrorCodes.SESSION_EXPIRED
                ShareAuthResult.PIN_REQUIRED -> SignalErrorCodes.PIN_REQUIRED
                ShareAuthResult.INVALID_PIN -> SignalErrorCodes.INVALID_PIN
                ShareAuthResult.OK -> error("unreachable")
            }
            Log.i(TAG, "Rejected /signal connection: $authResult (token present=${token != null}, pin present=${pin != null})")
            // Closing synchronously from inside onOpen() races NanoWSD's own completion of the
            // WS opening handshake - some clients (observed with Chrome) then see a bare abrupt
            // reset (close code 1006, no frame at all) instead of a real close frame carrying
            // [code]/[authFailureMessage] as its reason, because the reject frame gets written
            // before the handshake response NanoWSD is still sending has finished. Letting onOpen()
            // return first, then rejecting from a separate thread once the handshake has settled,
            // is what makes the close reason actually reach the browser.
            Thread {
                runCatching { Thread.sleep(REJECT_AFTER_OPEN_DELAY_MS) }
                runCatching {
                    send(mirrorDropJson.encodeToString(SignalError.serializer(), SignalError(code = code, message = authFailureMessage(authResult))))
                }
                runCatching { close(NanoWSD.WebSocketFrame.CloseCode.PolicyViolation, code, false) }
            }.start()
            return
        }
        peerId = signalingServer.onConnectionOpened(object : MirrorDropSignalingServer.MirrorDropSocketHandle {
            override fun sendText(text: String) {
                runCatching { send(text) }
            }

            override fun sendPing() {
                runCatching { ping(ByteArray(0)) }
            }

            override fun closeWith(reason: String) {
                runCatching { close(NanoWSD.WebSocketFrame.CloseCode.NormalClosure, reason, false) }
            }
        })
    }

    override fun onClose(code: NanoWSD.WebSocketFrame.CloseCode, reason: String, initiatedByRemote: Boolean) {
        peerId?.let { signalingServer.onConnectionClosed(it) }
    }

    override fun onMessage(message: NanoWSD.WebSocketFrame) {
        val id = peerId ?: return
        val text = message.textPayload ?: return
        signalingServer.handleMessage(id, text)
    }

    override fun onPong(pong: NanoWSD.WebSocketFrame) {
        peerId?.let { signalingServer.onPong(it) }
    }

    override fun onException(exception: IOException) {
        Log.w(TAG, "WebSocket exception for peer $peerId", exception)
    }
}

private fun authFailureMessage(result: ShareAuthResult): String = when (result) {
    ShareAuthResult.NO_ACTIVE_SESSION -> "Sharing isn't active on this mirror right now."
    ShareAuthResult.INVALID_TOKEN -> "This share link is no longer valid."
    ShareAuthResult.EXPIRED -> "This share link has expired."
    ShareAuthResult.PIN_REQUIRED -> "Enter the PIN shown on the mirror."
    ShareAuthResult.INVALID_PIN -> "That PIN doesn't match."
    ShareAuthResult.OK -> ""
}
