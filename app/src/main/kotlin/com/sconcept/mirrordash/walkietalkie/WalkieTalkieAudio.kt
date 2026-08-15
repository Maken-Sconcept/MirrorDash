package com.sconcept.mirrordash.walkietalkie

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.math.roundToInt

/**
 * Minimal UDP push-to-talk audio transport, ported unchanged from BerthierOptions: raw 16kHz
 * mono PCM, a one-byte protocol marker, a length-prefixed sender name, then the audio payload.
 * No compression codec - this is LAN-only, bandwidth is a non-issue, and skipping a codec
 * avoids its encode/decode latency entirely, which matters for feeling "instant". "Broadcast to
 * all" is implemented as unicast to every configured peer rather than a real network broadcast,
 * since subnet broadcast is blocked on some routers/APs.
 *
 * Structural change from the original: port/device-name/mic-boost are passed in by the caller
 * instead of read from a synchronous `Context.config` - this class no longer needs to know
 * settings storage exists at all.
 */
class WalkieTalkieAudio(private val context: Context) {

    companion object {
        private const val TAG = "WalkieTalkieAudio"
        private const val PROTOCOL_MARKER = 0xA5
        private const val SAMPLE_RATE = 16000
        private const val CHUNK_MS = 20
        private const val SPEAKER_BOOST_GAIN = 2f
        private const val INCOMING_CHIME_GAP_MS = 750L
    }

    private var receiveThread: Thread? = null
    private var receiveSocket: DatagramSocket? = null
    private val toneLock = Any()
    private var incomingChime: ToneGenerator? = null
    private var incomingChimeVolume: Int? = null

    @Volatile
    private var receiving = false

    @Volatile
    private var lastIncomingPacketAtMs = 0L
    @Volatile
    private var incomingChimeEnabled = true
    @Volatile
    private var incomingChimeTone = DEFAULT_WALKIE_TALKIE_CHIME

    private var transmitThread: Thread? = null

    @Volatile
    private var transmitting = false

    var onIncomingFrom: ((String, String) -> Unit)? = null

    fun updateIncomingChime(enabled: Boolean, toneKey: String) {
        incomingChimeEnabled = enabled
        incomingChimeTone = toneKey
    }

    fun previewIncomingChime(toneKey: String) {
        playIncomingChime(WalkieTalkieChimes.specFor(toneKey))
    }

    fun hasMicPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun startReceiving(port: Int) {
        if (receiving || !hasMicPermission()) return
        receiving = true
        receiveThread = Thread {
            try {
                val socket = DatagramSocket(port)
                receiveSocket = socket

                val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                val track = AudioTrack(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build(),
                    minBuf.coerceAtLeast(1024),
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE,
                )
                track.play()

                val buffer = ByteArray(4096)
                while (receiving) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: Exception) {
                        continue
                    }
                    if (packet.length < 2 || (packet.data[0].toInt() and 0xFF) != PROTOCOL_MARKER) continue
                    val nameLen = packet.data[1].toInt() and 0xFF
                    val headerLen = 2 + nameLen
                    if (packet.length <= headerLen) continue
                    val senderName = String(packet.data, 2, nameLen, Charsets.UTF_8)
                    val senderIp = packet.address?.hostAddress.orEmpty()
                    maybePlayIncomingChime()
                    onIncomingFrom?.invoke(senderName, senderIp)
                    applyMicBoost(packet.data, packet.length, SPEAKER_BOOST_GAIN, startIndex = headerLen)
                    track.write(packet.data, headerLen, packet.length - headerLen)
                }
                track.stop()
                track.release()
                socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "receive loop failed", e)
            } finally {
                releaseIncomingChime()
            }
        }
        receiveThread?.start()
    }

    fun stopReceiving() {
        receiving = false
        receiveSocket?.close()
        receiveThread = null
        lastIncomingPacketAtMs = 0L
        releaseIncomingChime()
    }

    fun startTransmitting(targetIps: List<String>, port: Int, deviceName: String, micBoostPercent: Int) {
        if (transmitting || targetIps.isEmpty() || !hasMicPermission()) return
        transmitting = true
        transmitThread = Thread {
            var record: AudioRecord? = null
            var socket: DatagramSocket? = null
            try {
                val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                record = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf.coerceAtLeast(1024),
                )
                socket = DatagramSocket()
                val nameBytes = deviceName.take(255).toByteArray(Charsets.UTF_8)
                val chunkSamples = SAMPLE_RATE * CHUNK_MS / 1000
                val audioBuf = ByteArray(chunkSamples * 2)
                val headerLen = 2 + nameBytes.size
                val packetBuf = ByteArray(headerLen + audioBuf.size)
                packetBuf[0] = PROTOCOL_MARKER.toByte()
                packetBuf[1] = nameBytes.size.toByte()
                System.arraycopy(nameBytes, 0, packetBuf, 2, nameBytes.size)

                val addresses = targetIps.mapNotNull {
                    try {
                        InetSocketAddress(InetAddress.getByName(it), port)
                    } catch (_: Exception) {
                        null
                    }
                }

                val micBoost = micBoostPercent / 100f
                record.startRecording()
                while (transmitting) {
                    val read = record.read(audioBuf, 0, audioBuf.size)
                    if (read <= 0) continue
                    applyMicBoost(audioBuf, read, micBoost)
                    System.arraycopy(audioBuf, 0, packetBuf, headerLen, read)
                    addresses.forEach { addr ->
                        try {
                            socket.send(DatagramPacket(packetBuf, headerLen + read, addr))
                        } catch (_: Exception) {
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "transmit loop failed", e)
            } finally {
                record?.stop()
                record?.release()
                socket?.close()
            }
        }
        transmitThread?.start()
    }

    fun stopTransmitting() {
        transmitting = false
        transmitThread = null
    }

    private fun applyMicBoost(buffer: ByteArray, length: Int, gain: Float, startIndex: Int = 0) {
        if (gain <= 1f) return

        var index = startIndex
        while (index + 1 < length) {
            val sample = ((buffer[index + 1].toInt() shl 8) or (buffer[index].toInt() and 0xFF)).toShort()
            val boosted = (sample * gain).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            buffer[index] = (boosted and 0xFF).toByte()
            buffer[index + 1] = ((boosted shr 8) and 0xFF).toByte()
            index += 2
        }
    }

    private fun maybePlayIncomingChime() {
        if (!incomingChimeEnabled) return
        val now = SystemClock.elapsedRealtime()
        val shouldPlay = now - lastIncomingPacketAtMs >= INCOMING_CHIME_GAP_MS
        lastIncomingPacketAtMs = now
        if (!shouldPlay) return
        playIncomingChime(WalkieTalkieChimes.specFor(incomingChimeTone))
    }

    private fun playIncomingChime(spec: WalkieTalkieChimeSpec) {
        synchronized(toneLock) {
            val tone = if (incomingChime == null || incomingChimeVolume != spec.volume) {
                incomingChime?.release()
                runCatching {
                    ToneGenerator(AudioManager.STREAM_NOTIFICATION, spec.volume)
                }.getOrNull()?.also {
                    incomingChime = it
                    incomingChimeVolume = spec.volume
                }
            } else {
                incomingChime
            } ?: return

            runCatching {
                tone.startTone(spec.tone, spec.durationMs)
            }
        }
    }

    private fun releaseIncomingChime() {
        synchronized(toneLock) {
            incomingChime?.release()
            incomingChime = null
            incomingChimeVolume = null
        }
    }
}
