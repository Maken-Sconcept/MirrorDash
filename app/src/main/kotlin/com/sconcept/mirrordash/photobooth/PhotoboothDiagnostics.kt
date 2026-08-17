package com.sconcept.mirrordash.photobooth

import android.content.Context
import android.os.Build
import com.sconcept.mirrordash.mirrordrop.MirrorDropNetworkUtils
import java.io.File

data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val androidRelease: String,
    val apiLevel: Int,
    val supportedAbis: List<String>,
    val device: String,
    val board: String,
    val hardware: String,
) {
    companion object {
        fun current(): DeviceInfo = DeviceInfo(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            androidRelease = Build.VERSION.RELEASE.orEmpty(),
            apiLevel = Build.VERSION.SDK_INT,
            supportedAbis = Build.SUPPORTED_ABIS?.toList().orEmpty(),
            device = Build.DEVICE.orEmpty(),
            board = Build.BOARD.orEmpty(),
            hardware = Build.HARDWARE.orEmpty(),
        )
    }
}

enum class HardwareTestStatus { PASS, WARNING, FAIL }

data class HardwareTestSection(val status: HardwareTestStatus, val message: String)

data class HardwareTestReport(
    val timestampMs: Long,
    val overall: HardwareTestStatus,
    val camera: HardwareTestSection,
    val storage: HardwareTestSection,
    val network: HardwareTestSection,
)

/**
 * "Run Hardware Test" (brief §48) - since we're deploying to unverified Rockchip/Echelon
 * hardware, this exercises the real camera/storage/network paths instead of assuming emulator
 * behavior carries over. The embedded MirrorDrop server/WebRTC sections of §48 are covered once
 * those subsystems exist (later phases); this covers Camera + Storage + Network only for now.
 */
object PhotoboothHardwareTest {

    suspend fun run(context: Context): HardwareTestReport {
        val camera = testCamera(context)
        val storage = testStorage(context)
        val network = testNetwork(context)
        val overall = listOf(camera, storage, network).map { it.status }.worst()
        return HardwareTestReport(
            timestampMs = System.currentTimeMillis(),
            overall = overall,
            camera = camera,
            storage = storage,
            network = network,
        )
    }

    private suspend fun testCamera(context: Context): HardwareTestSection {
        val cameras = CameraCapabilityDetector.enumerateCameras(context)
        if (cameras.isEmpty()) {
            return HardwareTestSection(HardwareTestStatus.FAIL, "No cameras were reported by the system.")
        }
        if (!CameraCapabilityDetector.hasCameraPermission(context)) {
            return HardwareTestSection(
                HardwareTestStatus.WARNING,
                "${cameras.size} camera(s) detected, but camera permission isn't granted yet - capture not verified.",
            )
        }
        // Prefer the same order Photobooth's own selection logic will use (front first, since
        // this is a mirror app, then back, then whatever else is left) rather than hard-coding
        // camera "0" (brief §5).
        val ordered = cameras.sortedBy {
            when (it.lensFacing) {
                LensFacingKind.FRONT -> 0
                LensFacingKind.BACK -> 1
                LensFacingKind.EXTERNAL -> 2
                LensFacingKind.UNKNOWN -> 3
            }
        }
        val results = ordered.map { CameraCapabilityDetector.captureTestPhoto(context, it.cameraId) }
        val firstSuccess = results.firstOrNull { it.success }
        return if (firstSuccess != null) {
            HardwareTestSection(
                HardwareTestStatus.PASS,
                "Captured a test photo from camera ${firstSuccess.cameraId} at ${firstSuccess.resolution} " +
                    "(${firstSuccess.jpegSizeBytes} bytes). ${cameras.size} camera(s) detected total.",
            )
        } else {
            val errors = results.joinToString("; ") { "camera ${it.cameraId}: ${it.error}" }
            HardwareTestSection(HardwareTestStatus.FAIL, "No camera produced an image. $errors")
        }
    }

    private fun testStorage(context: Context): HardwareTestSection {
        return runCatching {
            val probe = File(context.filesDir, "photobooth_diagnostics_probe.tmp")
            val payload = "mirrordash-photobooth-${System.nanoTime()}".toByteArray()
            probe.writeBytes(payload)
            val readBack = probe.readBytes()
            probe.delete()
            if (readBack.contentEquals(payload)) {
                HardwareTestSection(HardwareTestStatus.PASS, "Wrote and read back a test file in app storage.")
            } else {
                HardwareTestSection(HardwareTestStatus.FAIL, "Read-back bytes didn't match what was written.")
            }
        }.getOrElse { error ->
            HardwareTestSection(HardwareTestStatus.FAIL, "Storage write/read failed: ${error.message}")
        }
    }

    private fun testNetwork(context: Context): HardwareTestSection {
        val wifiConnected = MirrorDropNetworkUtils.isWifiConnected(context)
        val ip = MirrorDropNetworkUtils.getLocalIpv4Address()
        return when {
            !wifiConnected -> HardwareTestSection(
                HardwareTestStatus.WARNING,
                "Not connected to Wi-Fi. Connect this mirror to the same Wi-Fi network as guest devices to use MirrorDrop.",
            )
            ip == null -> HardwareTestSection(
                HardwareTestStatus.WARNING,
                "Wi-Fi is connected but no LAN IPv4 address could be determined yet.",
            )
            else -> HardwareTestSection(HardwareTestStatus.PASS, "Wi-Fi connected, LAN address $ip.")
        }
    }

    private fun List<HardwareTestStatus>.worst(): HardwareTestStatus = when {
        any { it == HardwareTestStatus.FAIL } -> HardwareTestStatus.FAIL
        any { it == HardwareTestStatus.WARNING } -> HardwareTestStatus.WARNING
        else -> HardwareTestStatus.PASS
    }
}
