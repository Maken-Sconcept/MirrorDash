package com.sconcept.mirrordash.airplay

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.SystemClock
import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.ArrayDeque
import java.util.Collections
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Called from native code to bridge Android-only concerns:
 * - network advertisement through NsdManager
 * - session / frame callbacks into Kotlin's MediaCodec pipeline
 *
 * Ported from BerthierOptions' class of the same name; the only structural change is
 * [receiverNameOverride] replacing a direct `Config` read, since MirrorDash's settings are a
 * DataStore [kotlinx.coroutines.flow.Flow] rather than a synchronous SharedPreferences wrapper -
 * [AirPlayEngine] keeps this field current instead.
 */
class AirPlayNsdBridge(context: Context) {

    data class SessionSnapshot(
        val receiverRunning: Boolean = false,
        val receiverPort: Int = 0,
        val receiverName: String = "",
        val receiverIp: String = "",
        val clientDeviceId: String = "",
        val clientModel: String = "",
        val clientName: String = "",
        val pairingPin: String = "",
        val codec: Int = 0,
        val videoWidth: Int = 0,
        val videoHeight: Int = 0,
        val bitrateMbps: Double = 0.0,
        val lastFrameAtMs: Long = 0L,
    ) {
        val clientLabel: String
            get() = listOf(clientName, clientModel).firstOrNull { it.isNotBlank() } ?: clientDeviceId

        val hasActiveVideo: Boolean
            get() = lastFrameAtMs > 0L && SystemClock.elapsedRealtime() - lastFrameAtMs <= 5000L

        val isSessionActive: Boolean
            get() = pairingPin.isNotBlank() || clientLabel.isNotBlank() || hasActiveVideo
    }

    interface Listener {
        fun onReceiverRunningChanged(running: Boolean, port: Int)
        fun onClientChanged(deviceId: String, model: String, name: String)
        fun onVideoFormatChanged(codec: Int, width: Int, height: Int)
        fun onVideoFrame(data: ByteArray, ptsUs: Long)
        fun onVideoSessionEnded()
        fun onPinRequested(pin: String)

        /** AAC-ELD (see [AirPlayNsdBridge.onAudioFrame]'s doc) - default no-op since most
         * listeners (e.g. [AirPlayMirrorSurface]) only care about video. */
        fun onAudioFrame(data: ByteArray, ptsUs: Long) = Unit
    }

    companion object {
        private const val TAG = "AirPlayNsdBridge"
    }

    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val activeListeners = mutableMapOf<String, NsdManager.RegistrationListener>()
    private val listeners = CopyOnWriteArraySet<Listener>()
    private val stateLock = Any()
    private val bitrateWindow = ArrayDeque<Pair<Long, Int>>()

    /** Kept current by [AirPlayEngine] from its DataStore-backed settings snapshot - read here
     * only for [snapshot]'s display purposes, not for anything protocol-relevant (the name
     * actually advertised is whatever was passed to `nativeStartReceiver`). */
    @Volatile
    var receiverNameOverride: String = ""

    @Volatile
    var receiverRunning: Boolean = false
        private set

    @Volatile
    var receiverPort: Int = 0
        private set

    @Volatile
    var lastClientLabel: String = ""
        private set

    @Volatile
    private var receiverIp: String = ""

    @Volatile
    private var lastClientDeviceId: String = ""

    @Volatile
    private var lastClientModel: String = ""

    @Volatile
    private var lastClientName: String = ""

    @Volatile
    private var pendingPin: String = ""

    @Volatile
    private var videoCodec: Int = 0

    @Volatile
    private var videoWidth: Int = 0

    @Volatile
    private var videoHeight: Int = 0

    @Volatile
    private var lastFrameAtMs: Long = 0L

    @Volatile
    private var bitrateMbps: Double = 0.0

    /** The SPS/PPS parameter-set NAL units are sent by the sender exactly once per session -
     * prepended to the next encrypted IDR packet right after [onVideoFormatChanged] fires (see
     * raop_rtp_mirror.c's prepend_sps_pps comment) - never repeated unless the format changes
     * again. A decoder that starts listening even one frame late (e.g. [AirPlayMirrorSurface]
     * mounting only once [SessionSnapshot.hasActiveVideo] is already true) would otherwise never
     * get another chance to configure MediaCodec for the rest of the session. Caching that one
     * frame here - reset each time the format (re)announces, captured on the very next frame -
     * lets a late-mounting listener catch up, the same way [snapshot] already lets it catch up
     * on the codec/width/height metadata. */
    @Volatile
    private var cachedFirstFrame: ByteArray? = null

    @Volatile
    private var cachedFirstFramePtsUs: Long = 0L

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    /** Paired with [snapshot] for a newly-mounted [AirPlayMirrorSurface]: the metadata alone
     * (already carried on [SessionSnapshot]) isn't enough to configure a decoder, since
     * `MediaCodec` also needs an actual sample containing the SPS/PPS NAL units - see
     * [cachedFirstFrame]'s doc for why this is otherwise unrecoverable mid-session. */
    fun cachedFirstFrameOrNull(): Pair<ByteArray, Long>? =
        cachedFirstFrame?.let { it to cachedFirstFramePtsUs }

    fun snapshot(): SessionSnapshot {
        val liveBitrate = synchronized(stateLock) {
            trimBitrateWindow(SystemClock.elapsedRealtime())
            bitrateMbps
        }
        return SessionSnapshot(
            receiverRunning = receiverRunning,
            receiverPort = receiverPort,
            receiverName = receiverNameOverride,
            receiverIp = receiverIp,
            clientDeviceId = lastClientDeviceId,
            clientModel = lastClientModel,
            clientName = lastClientName,
            pairingPin = pendingPin,
            codec = videoCodec,
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            bitrateMbps = liveBitrate,
            lastFrameAtMs = lastFrameAtMs,
        )
    }

    /** Called from dnssd_android.c's call_register_service(). */
    fun registerService(serviceType: String, serviceName: String, port: Int, keys: Array<String>, values: Array<String>) {
        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = serviceName
            this.serviceType = "$serviceType."
            this.port = port
        }
        for (i in keys.indices) {
            try {
                serviceInfo.setAttribute(keys[i], values.getOrElse(i) { "" })
            } catch (e: Exception) {
                Log.w(TAG, "failed to set TXT attribute ${keys[i]}: ${e.message}")
            }
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.i(TAG, "registered: ${info.serviceName} ($serviceType)")
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "registration failed for ${info.serviceName} ($serviceType): error $errorCode")
                activeListeners.remove(serviceName)
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {
                Log.i(TAG, "unregistered: ${info.serviceName}")
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "unregistration failed for ${info.serviceName}: error $errorCode")
            }
        }

        activeListeners[serviceName] = listener
        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.e(TAG, "registerService threw for $serviceName ($serviceType): ${e.message}")
            activeListeners.remove(serviceName)
        }
    }

    /** Called from dnssd_android.c's call_unregister_service(). */
    fun unregisterService(serviceName: String) {
        val listener = activeListeners.remove(serviceName) ?: return
        try {
            nsdManager.unregisterService(listener)
        } catch (e: Exception) {
            Log.w(TAG, "unregisterService threw for $serviceName: ${e.message}")
        }
    }

    fun onReceiverStarted(port: Int) {
        receiverRunning = true
        receiverPort = port
        receiverIp = resolveReceiverIp()
        listeners.forEach { it.onReceiverRunningChanged(true, port) }
    }

    fun onReceiverStopped() {
        receiverRunning = false
        receiverPort = 0
        receiverIp = ""
        lastClientLabel = ""
        clearSessionState(clearClient = true)
        listeners.forEach { it.onReceiverRunningChanged(false, 0) }
        listeners.forEach { it.onVideoSessionEnded() }
    }

    fun onClientChanged(deviceId: String, model: String, name: String) {
        lastClientLabel = listOf(name, model).firstOrNull { it.isNotBlank() } ?: deviceId
        lastClientDeviceId = deviceId
        lastClientModel = model
        lastClientName = name
        pendingPin = ""
        listeners.forEach { it.onClientChanged(deviceId, model, name) }
    }

    fun onVideoFormatChanged(codec: Int, width: Int, height: Int) {
        videoCodec = codec
        videoWidth = width
        videoHeight = height
        cachedFirstFrame = null
        listeners.forEach { it.onVideoFormatChanged(codec, width, height) }
    }

    fun onVideoFrame(data: ByteArray, ptsUs: Long) {
        recordFrameSize(data.size)
        if (cachedFirstFrame == null) {
            cachedFirstFrame = data
            cachedFirstFramePtsUs = ptsUs
        }
        listeners.forEach { it.onVideoFrame(data, ptsUs) }
    }

    fun onVideoSessionEnded() {
        clearSessionState(clearClient = true)
        listeners.forEach { it.onVideoSessionEnded() }
    }

    /** Called from dnssd_android.c's... no - from airplay_jni.c's audio_process(), already
     * filtered there to AAC-ELD (`ct` 8) only, the only compression type screen mirroring
     * actually negotiates in practice. Unlike video's SPS/PPS, AAC-ELD's codec-specific-data is a
     * fixed constant rather than something the stream announces once, so there's no equivalent
     * "missed the one-time config packet" hazard here - no caching/priming needed for a
     * late-mounting listener. */
    fun onAudioFrame(data: ByteArray, ptsUs: Long) {
        listeners.forEach { it.onAudioFrame(data, ptsUs) }
    }

    fun onPinRequested(pin: String) {
        pendingPin = pin
        listeners.forEach { it.onPinRequested(pin) }
    }

    private fun clearSessionState(clearClient: Boolean) {
        pendingPin = ""
        videoCodec = 0
        videoWidth = 0
        videoHeight = 0
        lastFrameAtMs = 0L
        cachedFirstFrame = null
        synchronized(stateLock) {
            bitrateWindow.clear()
            bitrateMbps = 0.0
        }
        if (clearClient) {
            lastClientDeviceId = ""
            lastClientModel = ""
            lastClientName = ""
            lastClientLabel = ""
        }
    }

    private fun recordFrameSize(byteCount: Int) {
        val now = SystemClock.elapsedRealtime()
        lastFrameAtMs = now
        synchronized(stateLock) {
            bitrateWindow.addLast(now to byteCount)
            trimBitrateWindow(now)
            val oldest = bitrateWindow.firstOrNull()?.first ?: now
            val durationMs = (now - oldest).coerceAtLeast(1L)
            val totalBytes = bitrateWindow.sumOf { it.second.toLong() }
            bitrateMbps = (totalBytes * 8.0) / durationMs / 1000.0
        }
    }

    private fun trimBitrateWindow(now: Long) {
        while (bitrateWindow.isNotEmpty() && now - bitrateWindow.first().first > 3000L) {
            bitrateWindow.removeFirst()
        }
    }

    private fun resolveReceiverIp(): String {
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.let { Collections.list(it) }.orEmpty()
            val preferred = interfaces
                .filter { it.isUp && !it.isLoopback }
                .flatMap { Collections.list(it.inetAddresses) }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            preferred?.hostAddress.orEmpty()
        }.getOrDefault("")
    }
}
