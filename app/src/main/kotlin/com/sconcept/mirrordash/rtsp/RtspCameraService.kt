package com.sconcept.mirrordash.rtsp

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.pedro.common.ConnectChecker
import com.pedro.rtspserver.RtspServerCamera1

const val RTSP_CAMERA_PORT = 8554
private const val RTSP_ENCODER_PORT = 8555
const val RTSP_QUALITY_LOW = "low"
const val RTSP_QUALITY_MEDIUM = "medium"
const val RTSP_QUALITY_HIGH = "high"
private const val TAG = "RtspCameraService"

data class RtspVideoProfile(
    val key: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrate: Int,
)

object RtspStreamQuality {
    val low = RtspVideoProfile(RTSP_QUALITY_LOW, 640, 480, 15, 800 * 1024)
    val medium = RtspVideoProfile(RTSP_QUALITY_MEDIUM, 848, 480, 30, 1_200 * 1024)
    val high = RtspVideoProfile(RTSP_QUALITY_HIGH, 1280, 720, 30, 2_000 * 1024)

    fun profile(key: String): RtspVideoProfile = when (key) {
        RTSP_QUALITY_LOW -> low
        RTSP_QUALITY_HIGH -> high
        else -> medium
    }
}

/** Owns the camera and local RTSP listener, including AAC microphone audio. */
class RtspCameraService : Service(), ConnectChecker {
    private var camera: RtspServerCamera1? = null
    private var activeProfile: RtspVideoProfile? = null
    private var compatibilityProxy: RtspCompatibilityProxy? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val allowedClients = intent?.getStringArrayListExtra(EXTRA_ALLOWED_CLIENT_IPS).orEmpty()
        val profile = RtspStreamQuality.profile(intent?.getStringExtra(EXTRA_QUALITY).orEmpty())
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        // Consumer devices often intentionally deny their installed apps access to `su`, while
        // fleet provisioning can still install the same LAN-only rule through ADB. Keep trying
        // the rule here, but do not disable a previously provisioned listener merely because the
        // app itself cannot invoke su.
        if (!RtspLanFirewall.install(RTSP_CAMERA_PORT, allowedClients)) {
            Log.w(TAG, "RTSP firewall must be provisioned by device management; app su unavailable")
        }
        if (camera != null && activeProfile != profile) {
            camera?.stopStream()
            camera = null
            activeProfile = null
        }
        if (camera == null) {
            val server = RtspServerCamera1(this, this, RTSP_ENCODER_PORT)
            // The server's per-client queue rejects a literal zero-delay cache. A single 100 ms
            // buffer is the library's safe low-latency minimum and prevents negotiation failures.
            server.streamClient.setDelay(100)
            if (!server.prepareVideo(profile.width, profile.height, profile.fps, profile.bitrate, 0)) {
                RtspLanFirewall.remove(RTSP_CAMERA_PORT)
                stopSelf(startId)
                return START_NOT_STICKY
            }
            if (!server.prepareAudio()) {
                RtspLanFirewall.remove(RTSP_CAMERA_PORT)
                stopSelf(startId)
                return START_NOT_STICKY
            }
            server.startStream()
            camera = server
            activeProfile = profile
            val publicIp = localRtspIpv4Address()
            if (publicIp == null) {
                stopSelf(startId)
                return START_NOT_STICKY
            }
            val proxy = RtspCompatibilityProxy(RTSP_CAMERA_PORT, RTSP_ENCODER_PORT, publicIp)
            if (!proxy.start()) {
                Log.e(TAG, "RTSP compatibility port $RTSP_CAMERA_PORT is unavailable; service will not start")
                server.stopStream()
                camera = null
                activeProfile = null
                RtspLanFirewall.remove(RTSP_CAMERA_PORT)
                stopSelf(startId)
                return START_NOT_STICKY
            }
            compatibilityProxy = proxy
        }
        return START_STICKY
    }

    override fun onDestroy() {
        camera?.stopStream()
        camera = null
        activeProfile = null
        compatibilityProxy?.stop()
        compatibilityProxy = null
        RtspLanFirewall.remove(RTSP_CAMERA_PORT)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onConnectionStarted(url: String) = Unit
    override fun onConnectionSuccess() = Unit
    override fun onConnectionFailed(reason: String) = Unit
    override fun onNewBitrate(bitrate: Long) = Unit
    override fun onDisconnect() = Unit
    override fun onAuthError() = Unit
    override fun onAuthSuccess() = Unit

    companion object {
        const val EXTRA_ALLOWED_CLIENT_IPS = "com.sconcept.mirrordash.rtsp.ALLOWED_CLIENT_IPS"
        const val EXTRA_QUALITY = "com.sconcept.mirrordash.rtsp.QUALITY"

        fun intent(
            context: android.content.Context,
            allowedClientIps: String,
            quality: String = RTSP_QUALITY_MEDIUM,
        ): Intent =
            Intent(context, RtspCameraService::class.java).putStringArrayListExtra(
                EXTRA_ALLOWED_CLIENT_IPS,
                ArrayList(allowedClientIps.split(',', '\n').map { it.trim() }.filter { it.isNotBlank() }),
            ).putExtra(EXTRA_QUALITY, quality)
    }
}
