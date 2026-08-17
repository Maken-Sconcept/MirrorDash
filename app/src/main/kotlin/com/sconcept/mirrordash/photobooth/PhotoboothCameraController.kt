package com.sconcept.mirrordash.photobooth

import android.content.Context
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

private const val TAG = "PhotoboothCamera"

/**
 * Drives the live preview + still capture for the real Photobooth flow (brief §41). CameraX-only
 * for v1: [CameraCapabilityDetector.testCameraXBind] already tells [PhotoboothViewModel] whether
 * CameraX can bind on this hardware at all (brief §5), so a failed [bind] here just surfaces as
 * an error state rather than falling back to a full Camera2-based *preview* UI, which is out of
 * scope for this phase.
 */
class PhotoboothCameraController(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null

    suspend fun bind(previewView: PreviewView, lifecycleOwner: LifecycleOwner, lensFacing: LensFacingKind): Boolean {
        val provider = runCatching { getProcessCameraProvider(context) }.getOrNull() ?: return false
        cameraProvider = provider
        val selector = when (lensFacing) {
            LensFacingKind.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
            else -> CameraSelector.DEFAULT_BACK_CAMERA
        }
        if (!provider.hasCamera(selector)) return false

        // Deliberately modest target sizes (brief §5's "don't assume" extends to "don't assume
        // the HAL can configure two concurrent high-res streams quickly"): on this LEGACY-level
        // hardware, requesting Preview + ImageCapture near the sensor's max JPEG size together
        // made session configuration exceed CameraX's internal 5s timeout and silently fail
        // (single-surface capture at full res, as the Phase 1 diagnostics probe does, was fine -
        // it's the concurrent two-surface session that's expensive here). A photobooth montage
        // has no need for the sensor's full resolution anyway.
        @Suppress("DEPRECATION")
        val preview = Preview.Builder()
            .setTargetResolution(Size(640, 480))
            .build()
            .also { it.surfaceProvider = previewView.surfaceProvider }
        @Suppress("DEPRECATION")
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetResolution(Size(1280, 960))
            .build()

        return try {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
            imageCapture = capture
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to bind camera preview", e)
            false
        }
    }

    suspend fun capturePhoto(): ByteArray {
        val capture = imageCapture ?: error("Camera not bound")
        return suspendCancellableCoroutine { cont ->
            capture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val bytes = runCatching {
                            val buffer = image.planes[0].buffer
                            ByteArray(buffer.remaining()).also { buffer.get(it) }
                        }
                        image.close()
                        bytes.fold(
                            onSuccess = { if (cont.isActive) cont.resume(it) },
                            onFailure = { error -> if (cont.isActive) cont.resumeWithException(error) },
                        )
                    }

                    override fun onError(exception: ImageCaptureException) {
                        if (cont.isActive) cont.resumeWithException(exception)
                    }
                },
            )
        }
    }

    fun unbind() {
        runCatching { cameraProvider?.unbindAll() }
        imageCapture = null
    }

    private suspend fun getProcessCameraProvider(context: Context): ProcessCameraProvider =
        suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(context))
        }
}
