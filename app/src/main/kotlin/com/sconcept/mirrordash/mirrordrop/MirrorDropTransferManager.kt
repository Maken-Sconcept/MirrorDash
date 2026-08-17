package com.sconcept.mirrordash.mirrordrop

import android.util.Log
import com.sconcept.mirrordash.mirrordrop.protocol.ManifestMessage
import com.sconcept.mirrordash.mirrordrop.protocol.SignalMessage
import com.sconcept.mirrordash.mirrordrop.protocol.TransferComplete
import com.sconcept.mirrordash.mirrordrop.protocol.TransferReady
import com.sconcept.mirrordash.mirrordrop.protocol.mirrorDropJson
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.webrtc.DataChannel

private const val TAG = "MirrorDropTransfer"

/** Centralized, tunable per brief §22 - both the chunking loop below and any future tests read
 * this rather than a magic number. */
const val MIRRORDROP_CHUNK_SIZE_BYTES = 32 * 1024

private const val MAX_BUFFERED_AMOUNT_BYTES = 1L * 1024 * 1024
private const val BACKPRESSURE_POLL_DELAY_MS = 20L

private const val FRAME_TAG_FILE_START: Byte = 1
private const val FRAME_TAG_FILE_CHUNK: Byte = 2
private const val FRAME_TAG_FILE_END: Byte = 3

@Serializable
private data class FileStartInfo(val fileId: String, val name: String, val mimeType: String, val size: Long, val sha256: String)

@Serializable
private data class FileEndInfo(val sha256: String, val byteCount: Long)

/**
 * Owns the byte-moving half of MirrorDrop (brief §21/§22): resolves `requestFiles` against a
 * [MirrorDropContentSource] and streams each file over the peer's RTCDataChannel as
 * FILE_START/FILE_CHUNK/FILE_END binary frames. `requestManifest`/`requestFiles` themselves travel
 * over the `/signal` WebSocket (control plane, handled by [MirrorDropSignalingServer]); only the
 * raw chunk bytes go over the DataChannel (data plane) - this class is where those two planes meet.
 *
 * Frame layout (1-byte tag + payload per brief §22):
 *  - FILE_START: `[0x01][transferId:4][utf8 json FileStartInfo]`
 *  - FILE_CHUNK: `[0x02][transferId:4][seq:4][raw chunk bytes]`
 *  - FILE_END:   `[0x03][transferId:4][utf8 json FileEndInfo]`
 *
 * Backpressure (brief §22): before every chunk send, polls [DataChannel.bufferedAmount] and waits
 * for it to drop below [MAX_BUFFERED_AMOUNT_BYTES] rather than queueing unbounded memory on a slow
 * browser peer.
 */
class MirrorDropTransferManager(
    @Volatile private var contentSource: MirrorDropContentSource,
    private val dataChannelFor: (peerId: String) -> DataChannel?,
    private val sendSignal: (peerId: String, message: SignalMessage) -> Unit,
) {
    private val transferIdCounter = AtomicInteger(1)
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile private var sessionLabel: String = "MirrorDrop"

    /** Called once per [ShareMode] pick (brief §6/§13) - swaps what a fresh `requestManifest`
     * exposes without needing to restart the server or WebRTC layer underneath it. */
    fun updateShareContent(label: String, source: MirrorDropContentSource) {
        sessionLabel = label
        contentSource = source
    }

    fun sendManifest(peerId: String) {
        sendSignal(peerId, ManifestMessage(sessionLabel = sessionLabel, files = contentSource.listFiles()))
    }

    fun sendFiles(peerId: String, fileIds: List<String>) {
        val channel = dataChannelFor(peerId)
        if (channel == null) {
            Log.w(TAG, "No open DataChannel for $peerId, ignoring requestFiles")
            return
        }
        fileIds.forEach { fileId ->
            val handle = contentSource.openFile(fileId)
            if (handle == null) {
                Log.w(TAG, "Unknown fileId $fileId requested by $peerId")
                return@forEach
            }
            scope.launch { sendOneFile(peerId, channel, handle) }
        }
    }

    private suspend fun sendOneFile(peerId: String, channel: DataChannel, handle: MirrorDropFileHandle) {
        val transferId = transferIdCounter.getAndIncrement()
        sendSignal(peerId, TransferReady(transferId = transferId.toString(), fileId = handle.id))

        val startInfo = FileStartInfo(
            fileId = handle.id,
            name = handle.name,
            mimeType = handle.mimeType,
            size = handle.bytes.size.toLong(),
            sha256 = handle.sha256Hex,
        )
        sendFrame(channel, frameHeader(FRAME_TAG_FILE_START, transferId) + jsonBytes(FileStartInfo.serializer(), startInfo))

        var offset = 0
        var seq = 0
        while (offset < handle.bytes.size) {
            waitForBufferedAmountBelowThreshold(channel)
            val end = minOf(offset + MIRRORDROP_CHUNK_SIZE_BYTES, handle.bytes.size)
            val chunk = handle.bytes.copyOfRange(offset, end)
            sendFrame(channel, frameHeader(FRAME_TAG_FILE_CHUNK, transferId) + intBytes(seq) + chunk)
            offset = end
            seq++
        }

        val endInfo = FileEndInfo(sha256 = handle.sha256Hex, byteCount = handle.bytes.size.toLong())
        sendFrame(channel, frameHeader(FRAME_TAG_FILE_END, transferId) + jsonBytes(FileEndInfo.serializer(), endInfo))

        sendSignal(peerId, TransferComplete(transferId = transferId.toString(), success = true))
        Log.i(TAG, "Sent ${handle.name} (${handle.bytes.size} bytes, $seq chunks) to $peerId as transfer $transferId")
    }

    private suspend fun waitForBufferedAmountBelowThreshold(channel: DataChannel) {
        while (channel.bufferedAmount() > MAX_BUFFERED_AMOUNT_BYTES) {
            delay(BACKPRESSURE_POLL_DELAY_MS)
        }
    }

    private fun sendFrame(channel: DataChannel, bytes: ByteArray) {
        channel.send(DataChannel.Buffer(ByteBuffer.wrap(bytes), true))
    }

    private fun frameHeader(tag: Byte, transferId: Int): ByteArray = byteArrayOf(tag) + intBytes(transferId)

    private fun intBytes(value: Int): ByteArray = ByteBuffer.allocate(4).putInt(value).array()

    private fun <T> jsonBytes(serializer: kotlinx.serialization.KSerializer<T>, value: T): ByteArray =
        mirrorDropJson.encodeToString(serializer, value).toByteArray(StandardCharsets.UTF_8)
}
