package com.sconcept.mirrordash.photobooth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val TAG = "PhotoboothCamera"

enum class LensFacingKind { FRONT, BACK, EXTERNAL, UNKNOWN }

enum class CameraHardwareLevel { LEGACY, LIMITED, FULL, LEVEL_3, EXTERNAL, UNKNOWN }

data class CameraCapabilitySummary(
    val cameraId: String,
    val lensFacing: LensFacingKind,
    val hardwareLevel: CameraHardwareLevel,
    val sensorOrientation: Int,
    val supportsAutoFocus: Boolean,
    val hasFlash: Boolean,
    val jpegSizes: List<String>,
    val previewSizes: List<String>,
    val exposureCompensationRange: String,
    val enumerationError: String? = null,
)

data class CaptureTestResult(
    val cameraId: String,
    val success: Boolean,
    val resolution: String? = null,
    val jpegSizeBytes: Int? = null,
    val error: String? = null,
)

/**
 * Enumerates every camera Camera2 knows about and can attempt a real still capture - the "don't
 * assume camera ID 0 exists, is FRONT-facing, has autofocus/flash, or that CameraX binds cleanly"
 * requirement (brief §5) for the unverified Echelon/Rockchip camera HAL. Camera2 characteristics
 * enumeration below needs no runtime permission; only [captureTestPhoto] and [testCameraXBind],
 * which actually open a camera, require [Manifest.permission.CAMERA] to already be granted.
 */
object CameraCapabilityDetector {

    fun hasCameraPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    fun enumerateCameras(context: Context): List<CameraCapabilitySummary> {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return emptyList()
        val ids = runCatching { manager.cameraIdList }.getOrElse {
            Log.w(TAG, "cameraIdList failed", it)
            return emptyList()
        }
        return ids.map { id -> describeCamera(manager, id) }
    }

    private fun describeCamera(manager: CameraManager, id: String): CameraCapabilitySummary {
        return runCatching {
            val chars = manager.getCameraCharacteristics(id)
            val facing = when (chars.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_FRONT -> LensFacingKind.FRONT
                CameraCharacteristics.LENS_FACING_BACK -> LensFacingKind.BACK
                CameraCharacteristics.LENS_FACING_EXTERNAL -> LensFacingKind.EXTERNAL
                else -> LensFacingKind.UNKNOWN
            }
            val hwLevel = when (chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> CameraHardwareLevel.LEGACY
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> CameraHardwareLevel.LIMITED
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> CameraHardwareLevel.FULL
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> CameraHardwareLevel.LEVEL_3
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> CameraHardwareLevel.EXTERNAL
                else -> CameraHardwareLevel.UNKNOWN
            }
            val sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: -1
            val afModes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: IntArray(0)
            val supportsAf = afModes.any { it != CameraMetadata.CONTROL_AF_MODE_OFF }
            val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
            val configMap = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val jpegSizes = configMap?.getOutputSizes(ImageFormat.JPEG).orEmpty()
                .sortedByDescending { it.width.toLong() * it.height }
                .take(8)
                .map { "${it.width}x${it.height}" }
            // SurfaceTexture is the format CameraX/Camera2 preview surfaces actually request.
            val previewSizes = configMap?.getOutputSizes(android.graphics.SurfaceTexture::class.java).orEmpty()
                .sortedByDescending { it.width.toLong() * it.height }
                .take(8)
                .map { "${it.width}x${it.height}" }
            val expRange = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
            CameraCapabilitySummary(
                cameraId = id,
                lensFacing = facing,
                hardwareLevel = hwLevel,
                sensorOrientation = sensorOrientation,
                supportsAutoFocus = supportsAf,
                hasFlash = hasFlash,
                jpegSizes = jpegSizes,
                previewSizes = previewSizes,
                exposureCompensationRange = expRange?.let { "${it.lower}..${it.upper}" } ?: "unknown",
            )
        }.getOrElse { error ->
            Log.w(TAG, "Failed describing camera $id", error)
            CameraCapabilitySummary(
                cameraId = id,
                lensFacing = LensFacingKind.UNKNOWN,
                hardwareLevel = CameraHardwareLevel.UNKNOWN,
                sensorOrientation = -1,
                supportsAutoFocus = false,
                hasFlash = false,
                jpegSizes = emptyList(),
                previewSizes = emptyList(),
                exposureCompensationRange = "unknown",
                enumerationError = error.message ?: error.javaClass.simpleName,
            )
        }
    }

    /**
     * Opens [cameraId] directly through Camera2, captures exactly one JPEG into an [ImageReader],
     * and reports whether the sensor actually produced an image - not just whether the HAL
     * *claims* to support it, per brief §5/§48 ("actually produce an image"). Bounded by an
     * overall timeout so a wedged HAL doesn't hang the diagnostics screen indefinitely.
     */
    suspend fun captureTestPhoto(context: Context, cameraId: String, timeoutMs: Long = 8_000L): CaptureTestResult {
        if (!hasCameraPermission(context)) {
            return CaptureTestResult(cameraId, success = false, error = "Camera permission not granted")
        }
        val result = withTimeoutOrNull(timeoutMs) { runCaptureTest(context, cameraId) }
        return result ?: CaptureTestResult(cameraId, success = false, error = "Timed out waiting for the camera")
    }

    private suspend fun runCaptureTest(context: Context, cameraId: String): CaptureTestResult {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val chars = runCatching { manager.getCameraCharacteristics(cameraId) }.getOrElse {
            return CaptureTestResult(cameraId, success = false, error = "No characteristics: ${it.message}")
        }
        val configMap = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val jpegSizes = configMap?.getOutputSizes(ImageFormat.JPEG).orEmpty()
        // Smallest available JPEG size, not the largest - a diagnostics probe should be cheap and
        // fast on constrained hardware; Photobooth's real capture path (Phase 8) picks its own
        // resolution independently.
        val size = jpegSizes.minByOrNull { it.width.toLong() * it.height }
            ?: return CaptureTestResult(cameraId, success = false, error = "No JPEG output sizes reported")

        val thread = HandlerThread("PhotoboothCameraTest-$cameraId").apply { start() }
        val handler = Handler(thread.looper)
        val reader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 1)
        try {
            return suspendCancellableCoroutine { cont ->
                var device: CameraDevice? = null
                var session: CameraCaptureSession? = null

                fun cleanup() {
                    runCatching { session?.close() }
                    runCatching { device?.close() }
                    runCatching { reader.close() }
                    runCatching { thread.quitSafely() }
                }

                fun finish(result: CaptureTestResult) {
                    cleanup()
                    if (cont.isActive) cont.resume(result)
                }

                reader.setOnImageAvailableListener({ r ->
                    val image = runCatching { r.acquireLatestImage() }.getOrNull()
                    if (image == null) {
                        finish(CaptureTestResult(cameraId, success = false, error = "No image delivered"))
                        return@setOnImageAvailableListener
                    }
                    val bytes = runCatching {
                        val buffer = image.planes[0].buffer
                        ByteArray(buffer.remaining()).also { buffer.get(it) }
                    }.getOrNull()
                    image.close()
                    if (bytes == null || bytes.isEmpty()) {
                        finish(CaptureTestResult(cameraId, success = false, error = "Empty image buffer"))
                    } else {
                        finish(
                            CaptureTestResult(
                                cameraId = cameraId,
                                success = true,
                                resolution = "${size.width}x${size.height}",
                                jpegSizeBytes = bytes.size,
                            ),
                        )
                    }
                }, handler)

                try {
                    manager.openCamera(
                        cameraId,
                        object : CameraDevice.StateCallback() {
                            override fun onOpened(cameraDevice: CameraDevice) {
                                device = cameraDevice
                                try {
                                    cameraDevice.createCaptureSession(
                                        listOf(reader.surface),
                                        object : CameraCaptureSession.StateCallback() {
                                            override fun onConfigured(configuredSession: CameraCaptureSession) {
                                                session = configuredSession
                                                try {
                                                    val request = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                                                        addTarget(reader.surface)
                                                    }.build()
                                                    configuredSession.capture(request, null, handler)
                                                } catch (e: Exception) {
                                                    finish(CaptureTestResult(cameraId, success = false, error = "Capture request failed: ${e.message}"))
                                                }
                                            }

                                            override fun onConfigureFailed(configuredSession: CameraCaptureSession) {
                                                finish(CaptureTestResult(cameraId, success = false, error = "Session configuration failed"))
                                            }
                                        },
                                        handler,
                                    )
                                } catch (e: Exception) {
                                    finish(CaptureTestResult(cameraId, success = false, error = "createCaptureSession failed: ${e.message}"))
                                }
                            }

                            override fun onDisconnected(cameraDevice: CameraDevice) {
                                finish(CaptureTestResult(cameraId, success = false, error = "Camera disconnected"))
                            }

                            override fun onError(cameraDevice: CameraDevice, error: Int) {
                                finish(CaptureTestResult(cameraId, success = false, error = "Camera error code $error"))
                            }
                        },
                        handler,
                    )
                } catch (e: SecurityException) {
                    finish(CaptureTestResult(cameraId, success = false, error = "Camera permission denied: ${e.message}"))
                } catch (e: Exception) {
                    finish(CaptureTestResult(cameraId, success = false, error = "openCamera failed: ${e.message}"))
                }

                cont.invokeOnCancellation { cleanup() }
            }
        } finally {
            // Best-effort second cleanup in case the coroutine above returned via the happy path
            // without the cancellation handler running - closing an already-closed reader/thread
            // is a harmless no-op.
            runCatching { reader.close() }
            runCatching { thread.quitSafely() }
        }
    }

    /**
     * Binds a throwaway [ImageCapture] use case through CameraX using a manually-driven
     * [LifecycleOwner] (never shown on screen) to answer "can CameraX bind on this hardware at
     * all" without needing a live preview surface - brief §5's "whether CameraX can bind
     * successfully" check. Immediately unbinds afterward regardless of outcome.
     */
    suspend fun testCameraXBind(context: Context, lensFacing: LensFacingKind): Boolean {
        if (!hasCameraPermission(context)) return false
        val provider = runCatching { getProcessCameraProvider(context) }.getOrNull() ?: return false
        val selector = when (lensFacing) {
            LensFacingKind.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
            else -> CameraSelector.DEFAULT_BACK_CAMERA
        }
        if (!provider.hasCamera(selector)) return false

        val owner = ProbeLifecycleOwner()
        return try {
            owner.moveTo(Lifecycle.State.STARTED)
            val useCase = ImageCapture.Builder().build()
            provider.bindToLifecycle(owner, selector, useCase)
            provider.unbind(useCase)
            true
        } catch (e: Exception) {
            Log.w(TAG, "CameraX bind failed", e)
            false
        } finally {
            owner.moveTo(Lifecycle.State.DESTROYED)
        }
    }

    private suspend fun getProcessCameraProvider(context: Context): ProcessCameraProvider =
        suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                { cont.resume(future.get()) },
                ContextCompat.getMainExecutor(context),
            )
        }
}

private class ProbeLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
    fun moveTo(state: Lifecycle.State) {
        registry.currentState = state
    }
}
