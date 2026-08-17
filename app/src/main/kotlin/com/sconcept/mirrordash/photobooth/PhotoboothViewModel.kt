package com.sconcept.mirrordash.photobooth

import android.app.Application
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sconcept.mirrordash.launcher.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val COUNTDOWN_SECONDS = 3
private const val PHOTOS_PER_SESSION = 3
private const val POST_CAPTURE_PAUSE_MS = 600L

enum class PhotoboothCapturePhase { IDLE, COUNTDOWN, CAPTURING, PROCESSING, PREVIEW, ERROR }

data class PhotoboothCaptureState(
    val phase: PhotoboothCapturePhase = PhotoboothCapturePhase.IDLE,
    val countdownValue: Int = 0,
    val photoIndex: Int = 0,
    val montagePreview: PhotoboothImage? = null,
    val errorMessage: String? = null,
)

data class PhotoboothUiState(
    val hasCameraPermission: Boolean = false,
    val cameras: List<CameraCapabilitySummary> = emptyList(),
    val deviceInfo: DeviceInfo = DeviceInfo.current(),
    val isRunningHardwareTest: Boolean = false,
    val lastHardwareTestReport: HardwareTestReport? = null,
    val capture: PhotoboothCaptureState = PhotoboothCaptureState(),
)

/**
 * Backs both the Photobooth tab itself (Phase 8 adds the actual capture flow on top of this) and
 * the Photobooth diagnostics section under Settings (brief §6). Camera enumeration is cheap
 * (Camera2 characteristics only, no permission needed) so it runs eagerly on init; the capture
 * test only runs on demand via [runHardwareTest] since it actually opens the camera hardware.
 */
class PhotoboothViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PhotoboothUiState())
    val uiState: StateFlow<PhotoboothUiState> = _uiState

    private val cameraController = PhotoboothCameraController(application)
    private val repository = AppContainer.get(application).photoboothRepository
    private var captureJob: Job? = null

    init {
        refreshCameraState()
    }

    /** Call when returning to the Photobooth tab/settings screen (e.g. after a permission
     * prompt) so [hasCameraPermission] and the camera list reflect current reality. */
    fun refreshCameraState() {
        val context = getApplication<Application>()
        _uiState.update {
            it.copy(hasCameraPermission = CameraCapabilityDetector.hasCameraPermission(context))
        }
        viewModelScope.launch {
            val cameras = withContext(Dispatchers.IO) { CameraCapabilityDetector.enumerateCameras(context) }
            _uiState.update { it.copy(cameras = cameras) }
        }
    }

    fun runHardwareTest() {
        if (_uiState.value.isRunningHardwareTest) return
        _uiState.update { it.copy(isRunningHardwareTest = true) }
        viewModelScope.launch {
            val report = withContext(Dispatchers.IO) { PhotoboothHardwareTest.run(getApplication()) }
            _uiState.update { it.copy(isRunningHardwareTest = false, lastHardwareTestReport = report) }
            refreshCameraState()
        }
    }

    /** Binds the live preview as soon as [PhotoboothScreen] has a [PreviewView] to give it -
     * front-facing preferred (a mirror's photobooth is a selfie camera by nature), falling back to
     * whatever's first if there's no front camera. */
    fun bindCamera(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        val preferred = _uiState.value.cameras.firstOrNull { it.lensFacing == LensFacingKind.FRONT }?.lensFacing
            ?: _uiState.value.cameras.firstOrNull()?.lensFacing
            ?: LensFacingKind.BACK
        viewModelScope.launch {
            val bound = cameraController.bind(previewView, lifecycleOwner, preferred)
            if (!bound) {
                _uiState.update {
                    it.copy(capture = it.capture.copy(phase = PhotoboothCapturePhase.ERROR, errorMessage = "Couldn't start the camera preview."))
                }
            }
        }
    }

    fun unbindCamera() {
        captureJob?.cancel()
        cameraController.unbind()
    }

    /** Runs the whole 3-photo countdown → capture → montage flow (brief §41). Cancellable via
     * [retake] mid-flight (e.g. the user backs out during the countdown). */
    fun startCaptureSession() {
        if (captureJob?.isActive == true) return
        captureJob = viewModelScope.launch {
            val photos = mutableListOf<ByteArray>()
            try {
                repeat(PHOTOS_PER_SESSION) { index ->
                    _uiState.update { it.copy(capture = PhotoboothCaptureState(phase = PhotoboothCapturePhase.COUNTDOWN, photoIndex = index)) }
                    for (remaining in COUNTDOWN_SECONDS downTo 1) {
                        _uiState.update { it.copy(capture = it.capture.copy(countdownValue = remaining)) }
                        delay(1_000L)
                    }
                    _uiState.update { it.copy(capture = it.capture.copy(phase = PhotoboothCapturePhase.CAPTURING)) }
                    photos += cameraController.capturePhoto()
                    delay(POST_CAPTURE_PAUSE_MS)
                }

                _uiState.update { it.copy(capture = it.capture.copy(phase = PhotoboothCapturePhase.PROCESSING)) }
                val montageBytes = withContext(Dispatchers.Default) { MontageGenerator.generate(photos) }
                val session = withContext(Dispatchers.IO) { repository.saveSession(photos, montageBytes) }
                // Brief §25 - if a share is already active (e.g. Latest Montage), connected
                // browsers see this session without needing to reload the page.
                AppContainer.get(getApplication()).mirrorDropEngine.notifyPhotoboothContentChanged()
                val montageImage = PhotoboothImage(
                    id = "${session.id}/${session.montageFileName}",
                    sessionId = session.id,
                    name = session.montageFileName,
                    bytes = montageBytes,
                )
                _uiState.update {
                    it.copy(capture = PhotoboothCaptureState(phase = PhotoboothCapturePhase.PREVIEW, montagePreview = montageImage))
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(capture = PhotoboothCaptureState(phase = PhotoboothCapturePhase.ERROR, errorMessage = e.message ?: "Capture failed"))
                }
            }
        }
    }

    /** Discards the in-progress or just-finished attempt and returns to idle, ready to shoot
     * again - the camera stays bound throughout, only the capture state resets. */
    fun retake() {
        captureJob?.cancel()
        _uiState.update { it.copy(capture = PhotoboothCaptureState()) }
    }

    fun dismissPreview() {
        _uiState.update { it.copy(capture = PhotoboothCaptureState()) }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PhotoboothViewModel(application) as T
            }
    }
}
