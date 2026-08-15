package com.sconcept.mirrordash.airplay

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.nio.ByteBuffer
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

private const val TAG = "AirPlayVideoDecoder"
private const val MIME_AVC = "video/avc"
private const val MIME_HEVC = "video/hevc"
private const val MAX_PENDING_FRAMES = 10

class AirPlayVideoDecoder(
    private val surfaceView: SurfaceView,
    private val onError: (String) -> Unit,
    private val onVideoSizeChanged: (width: Int, height: Int) -> Unit = { _, _ -> },
) : SurfaceHolder.Callback {

    private data class Frame(val data: ByteArray, val ptsUs: Long)
    private data class PendingFormat(val codec: Int, val width: Int, val height: Int)

    private val thread = HandlerThread("AirPlayVideoDecoder").apply { start() }
    private val handler = Handler(thread.looper)
    private val queuedFrames = ArrayDeque<Frame>()
    private val drainScheduled = AtomicBoolean(false)
    private val released = AtomicBoolean(false)

    @Volatile
    private var surface: Surface? = null

    private var pendingFormat: PendingFormat? = null
    private var codec: MediaCodec? = null
    private var configuredCodecType: Int? = null
    private var renderedFrameCount: Int = 0
    private var announcedVideoWidth: Int = 0
    private var announcedVideoHeight: Int = 0

    init {
        surfaceView.holder.addCallback(this)
        val holderSurface = surfaceView.holder.surface
        if (holderSurface != null && holderSurface.isValid) {
            surface = holderSurface
        }
    }

    /** [release] quits [thread] - a stray [SurfaceHolder.Callback] delivery (or a queued frame
     * still arriving) after that must never touch [handler] again, since posting to an already-
     * quit `HandlerThread` throws internally and Android just drops the work silently (visible
     * only as a "sending message to a Handler on a dead thread" logcat warning) - previously this
     * meant an already-released decoder could eat real frames/callbacks with no visible failure
     * beyond that easy-to-miss warning. */
    private fun postIfAlive(block: () -> Unit) {
        if (released.get()) return
        handler.post(block)
    }

    fun onVideoFormatChanged(codec: Int, width: Int, height: Int) {
        postIfAlive {
            val next = PendingFormat(codec, max(width, 16), max(height, 16))
            if (pendingFormat != next) {
                pendingFormat = next
                releaseCodec()
            }
        }
    }

    fun onVideoFrame(data: ByteArray, ptsUs: Long) {
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
            renderedFrameCount = 0
            announcedVideoWidth = 0
            announcedVideoHeight = 0
            releaseCodec()
        }
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        surfaceView.holder.removeCallback(this)
        handler.post {
            synchronized(queuedFrames) {
                queuedFrames.clear()
            }
            renderedFrameCount = 0
            announcedVideoWidth = 0
            announcedVideoHeight = 0
            releaseCodec()
        }
        thread.quitSafely()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surface = holder.surface
        scheduleDrain()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        scheduleDrain()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surface = null
        postIfAlive { releaseCodec() }
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
                if (!ensureCodecConfigured(frame.data)) {
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

    private fun ensureCodecConfigured(sample: ByteArray): Boolean {
        if (codec != null) {
            return true
        }

        val targetSurface = surface ?: return false
        val format = pendingFormat ?: return false
        val codecConfig = parseCodecConfig(format.codec, sample) ?: return false
        val mime = when (format.codec) {
            AirPlayNative.CODEC_H264 -> MIME_AVC
            AirPlayNative.CODEC_H265 -> MIME_HEVC
            else -> return false
        }

        try {
            val codecName = selectDecoderName(mime)
            val mediaCodec = if (codecName != null) {
                MediaCodec.createByCodecName(codecName)
            } else {
                MediaCodec.createDecoderByType(mime)
            }
            val configuredWidth = max(format.width, 720)
            val configuredHeight = max(format.height, 720)
            val mediaFormat = MediaFormat.createVideoFormat(mime, configuredWidth, configuredHeight)
            mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, max(sample.size * 2, 1024 * 1024))
            codecConfig.forEachIndexed { index, csd ->
                mediaFormat.setByteBuffer("csd-$index", ByteBuffer.wrap(csd))
            }
            mediaCodec.configure(mediaFormat, targetSurface, null, 0)
            mediaCodec.start()
            codec = mediaCodec
            configuredCodecType = format.codec
            renderedFrameCount = 0
            announceVideoSize(format.width, format.height)
            Log.i(
                TAG,
                "Configured decoder ${mediaCodec.name} for $mime at ${configuredWidth}x${configuredHeight} " +
                    "(reported ${format.width}x${format.height})"
            )
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure decoder", e)
            onError("Video decoder setup failed: ${e.message ?: mime}")
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
                Log.w(TAG, "Dropping oversized frame: ${frame.data.size} > ${inputBuffer.capacity()}")
                return
            }

            inputBuffer.clear()
            inputBuffer.put(frame.data)
            activeCodec.queueInputBuffer(inputIndex, 0, frame.data.size, frame.ptsUs, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to queue frame", e)
            releaseCodec()
        }
    }

    private fun drainOutput(activeCodec: MediaCodec) {
        val bufferInfo = MediaCodec.BufferInfo()
        while (true) {
            val outputIndex = activeCodec.dequeueOutputBuffer(bufferInfo, 0)
            when {
                outputIndex >= 0 -> {
                    activeCodec.releaseOutputBuffer(outputIndex, true)
                    renderedFrameCount += 1
                    if (renderedFrameCount == 1 || renderedFrameCount % 60 == 0) {
                        Log.i(TAG, "Rendered frame count: $renderedFrameCount")
                    }
                }
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val outputFormat = activeCodec.outputFormat
                    Log.i(TAG, "Output format changed: $outputFormat")
                    announceVideoSize(
                        width = outputFormat.videoWidth(),
                        height = outputFormat.videoHeight(),
                        rotationDegrees = outputFormat.rotationDegrees(),
                    )
                }
                else -> return
            }
        }
    }

    private fun releaseCodec() {
        codec?.runCatching { stop() }
        codec?.runCatching { release() }
        codec = null
        configuredCodecType = null
    }

    private fun parseCodecConfig(codec: Int, sample: ByteArray): List<ByteArray>? {
        return when (codec) {
            AirPlayNative.CODEC_H264 -> {
                val sps = findAnnexBNalUnits(sample).firstOrNull { (it[4].toInt() and 0x1F) == 7 }
                val pps = findAnnexBNalUnits(sample).firstOrNull { (it[4].toInt() and 0x1F) == 8 }
                if (sps != null && pps != null) listOf(sps, pps) else null
            }

            AirPlayNative.CODEC_H265 -> {
                val nalUnits = findAnnexBNalUnits(sample)
                val vps = nalUnits.firstOrNull { ((it[4].toInt() ushr 1) and 0x3F) == 32 }
                val sps = nalUnits.firstOrNull { ((it[4].toInt() ushr 1) and 0x3F) == 33 }
                val pps = nalUnits.firstOrNull { ((it[4].toInt() ushr 1) and 0x3F) == 34 }
                if (vps != null && sps != null && pps != null) listOf(vps, sps, pps) else null
            }

            else -> null
        }
    }

    private fun findAnnexBNalUnits(sample: ByteArray): List<ByteArray> {
        val nalUnits = mutableListOf<ByteArray>()
        var start = findStartCode(sample, 0)
        while (start >= 0) {
            val next = findStartCode(sample, start + 4)
            val end = if (next >= 0) next else sample.size
            if (end - start > 4) {
                nalUnits += sample.copyOfRange(start, end)
            }
            if (next < 0) {
                break
            }
            start = next
        }
        return nalUnits
    }

    private fun findStartCode(sample: ByteArray, fromIndex: Int): Int {
        var i = fromIndex
        while (i + 3 < sample.size) {
            if (sample[i] == 0.toByte() &&
                sample[i + 1] == 0.toByte() &&
                sample[i + 2] == 0.toByte() &&
                sample[i + 3] == 1.toByte()
            ) {
                return i
            }
            i++
        }
        return -1
    }

    private fun selectDecoderName(mime: String): String? {
        val codecInfos = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { !it.isEncoder && it.supportedTypes.any { type -> type.equals(mime, ignoreCase = true) } }

        val preferredSoftware = codecInfos.firstOrNull { info ->
            info.isSoftwareOnlyCompat() || info.name.startsWith("OMX.google.", ignoreCase = true)
        }
        if (preferredSoftware != null) {
            return preferredSoftware.name
        }

        return codecInfos.firstOrNull()?.name
    }

    private fun MediaCodecInfo.isSoftwareOnlyCompat(): Boolean {
        return try {
            isSoftwareOnly
        } catch (_: Throwable) {
            name.startsWith("c2.android.", ignoreCase = true) ||
                name.startsWith("OMX.google.", ignoreCase = true)
        }
    }

    private fun announceVideoSize(width: Int, height: Int, rotationDegrees: Int = 0) {
        if (width <= 0 || height <= 0) {
            return
        }
        val shouldSwap = rotationDegrees == 90 || rotationDegrees == 270
        val finalWidth = if (shouldSwap) height else width
        val finalHeight = if (shouldSwap) width else height
        if (announcedVideoWidth == finalWidth && announcedVideoHeight == finalHeight) {
            return
        }
        announcedVideoWidth = finalWidth
        announcedVideoHeight = finalHeight
        onVideoSizeChanged(finalWidth, finalHeight)
    }

    private fun MediaFormat.videoWidth(): Int {
        val cropLeft = getIntegerSafely(MediaFormat.KEY_CROP_LEFT)
        val cropRight = getIntegerSafely(MediaFormat.KEY_CROP_RIGHT)
        if (cropLeft != null && cropRight != null && cropRight >= cropLeft) {
            return cropRight - cropLeft + 1
        }
        return getIntegerSafely(MediaFormat.KEY_WIDTH) ?: 0
    }

    private fun MediaFormat.videoHeight(): Int {
        val cropTop = getIntegerSafely(MediaFormat.KEY_CROP_TOP)
        val cropBottom = getIntegerSafely(MediaFormat.KEY_CROP_BOTTOM)
        if (cropTop != null && cropBottom != null && cropBottom >= cropTop) {
            return cropBottom - cropTop + 1
        }
        return getIntegerSafely(MediaFormat.KEY_HEIGHT) ?: 0
    }

    private fun MediaFormat.rotationDegrees(): Int {
        return getIntegerSafely(MediaFormat.KEY_ROTATION) ?: 0
    }

    private fun MediaFormat.getIntegerSafely(key: String): Int? {
        return runCatching {
            if (containsKey(key)) getInteger(key) else null
        }.getOrNull()
    }
}
