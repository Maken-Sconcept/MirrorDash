package com.sconcept.mirrordash.airplay

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.nio.ByteBuffer
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

private const val TAG = "AirPlayAudioDecoder"
private const val MIME_AAC = "audio/mp4a-latm"
private const val SAMPLE_RATE = 44100
private const val CHANNEL_COUNT = 2
private const val MAX_PENDING_FRAMES = 30

/**
 * Decodes the AAC-ELD audio AirPlay mirroring always negotiates (see
 * [AirPlayNsdBridge.onAudioFrame]'s doc) and plays it through [AudioTrack]. Structurally mirrors
 * [AirPlayVideoDecoder] (own [HandlerThread], queue + drain loop, `released` guard against posting
 * to a dead thread after [release]) but is simpler in one respect: AAC-ELD's codec-specific-data
 * is a fixed constant for this wire format rather than something the stream announces once, so
 * unlike video there's no "must not miss the one-time config packet" hazard - the codec can be
 * configured immediately, with no format priming needed for a late subscriber.
 */
class AirPlayAudioDecoder(private val onError: (String) -> Unit) {

    private data class Frame(val data: ByteArray, val ptsUs: Long)

    private val thread = HandlerThread("AirPlayAudioDecoder").apply { start() }
    private val handler = Handler(thread.looper)
    private val queuedFrames = ArrayDeque<Frame>()
    private val drainScheduled = AtomicBoolean(false)
    private val released = AtomicBoolean(false)

    private var codec: MediaCodec? = null
    private var audioTrack: AudioTrack? = null

    private fun postIfAlive(block: () -> Unit) {
        if (released.get()) return
        handler.post(block)
    }

    fun onAudioFrame(data: ByteArray, ptsUs: Long) {
        if (released.get()) return
        synchronized(queuedFrames) {
            if (queuedFrames.size >= MAX_PENDING_FRAMES) {
                queuedFrames.removeFirst()
            }
            queuedFrames.addLast(Frame(data, ptsUs))
        }
        scheduleDrain()
    }

    fun resetSession() {
        postIfAlive {
            synchronized(queuedFrames) {
                queuedFrames.clear()
            }
            releaseCodec()
        }
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        handler.post {
            synchronized(queuedFrames) {
                queuedFrames.clear()
            }
            releaseCodec()
        }
        thread.quitSafely()
    }

    private fun scheduleDrain() {
        if (released.get()) return
        if (drainScheduled.compareAndSet(false, true)) {
            postIfAlive(::drainFrames)
        }
    }

    private fun drainFrames() {
        try {
            while (true) {
                val frame = synchronized(queuedFrames) {
                    if (queuedFrames.isEmpty()) null else queuedFrames.removeFirst()
                } ?: break
                if (!ensureCodecConfigured()) {
                    continue
                }

                val activeCodec = codec ?: break
                queueFrame(activeCodec, frame)
                drainOutput(activeCodec)
            }
        } finally {
            drainScheduled.set(false)
            synchronized(queuedFrames) {
                if (queuedFrames.isNotEmpty()) {
                    scheduleDrain()
                }
            }
        }
    }

    private fun ensureCodecConfigured(): Boolean {
        if (codec != null) {
            return true
        }

        try {
            val mediaFormat = MediaFormat.createAudioFormat(MIME_AAC, SAMPLE_RATE, CHANNEL_COUNT)
            // AudioSpecificConfig for AAC-ELD 44100/2 - AirPlay mirroring's audio format is fixed,
            // unlike video there is no in-stream announcement to parse this out of (see class doc).
            mediaFormat.setByteBuffer("csd-0", ByteBuffer.wrap(byteArrayOf(0xf8.toByte(), 0xe8.toByte(), 0x50.toByte(), 0x00.toByte())))
            mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectELD)
            mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192)

            val mediaCodec = MediaCodec.createDecoderByType(MIME_AAC)
            mediaCodec.configure(mediaFormat, null, null, 0)
            mediaCodec.start()
            codec = mediaCodec

            val channelConfig = AudioFormat.CHANNEL_OUT_STEREO
            val encoding = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, channelConfig, encoding)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(channelConfig)
                        .setEncoding(encoding)
                        .build()
                )
                .setBufferSizeInBytes(max(minBufferSize, 1) * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track.play()
            audioTrack = track
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure audio decoder", e)
            onError("Audio decoder setup failed: ${e.message ?: MIME_AAC}")
            releaseCodec()
            return false
        }
    }

    private fun queueFrame(activeCodec: MediaCodec, frame: Frame) {
        try {
            val inputIndex = activeCodec.dequeueInputBuffer(0)
            if (inputIndex < 0) {
                return
            }

            val inputBuffer = activeCodec.getInputBuffer(inputIndex) ?: return
            if (frame.data.size > inputBuffer.capacity()) {
                Log.w(TAG, "Dropping oversized audio frame: ${frame.data.size} > ${inputBuffer.capacity()}")
                return
            }

            inputBuffer.clear()
            inputBuffer.put(frame.data)
            activeCodec.queueInputBuffer(inputIndex, 0, frame.data.size, frame.ptsUs, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to queue audio frame", e)
            releaseCodec()
        }
    }

    private fun drainOutput(activeCodec: MediaCodec) {
        val bufferInfo = MediaCodec.BufferInfo()
        while (true) {
            val outputIndex = activeCodec.dequeueOutputBuffer(bufferInfo, 0)
            when {
                outputIndex >= 0 -> {
                    val outputBuffer = activeCodec.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        val pcm = ByteArray(bufferInfo.size)
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        outputBuffer.get(pcm)
                        audioTrack?.write(pcm, 0, pcm.size)
                    }
                    activeCodec.releaseOutputBuffer(outputIndex, false)
                }
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    Log.i(TAG, "Audio output format changed: ${activeCodec.outputFormat}")
                }
                else -> return
            }
        }
    }

    private fun releaseCodec() {
        codec?.runCatching { stop() }
        codec?.runCatching { release() }
        codec = null
        audioTrack?.runCatching { stop() }
        audioTrack?.runCatching { release() }
        audioTrack = null
    }
}
