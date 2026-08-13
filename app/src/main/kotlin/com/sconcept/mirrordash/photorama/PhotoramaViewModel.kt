package com.sconcept.mirrordash.photorama

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sconcept.mirrordash.nas.LanPhotoRepository
import com.sconcept.mirrordash.nas.PhotoCacheManager
import com.sconcept.mirrordash.nas.model.LanPhoto
import com.sconcept.mirrordash.nas.model.SmbConnectionState
import com.sconcept.mirrordash.nas.model.SmbResult
import com.sconcept.mirrordash.nas.model.SmbShare
import com.sconcept.mirrordash.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PhotoramaUiState(
    val isConfigured: Boolean = false,
    val connectionState: SmbConnectionState = SmbConnectionState.DISCONNECTED,
    val currentPhoto: File? = null,
    val hasNoPhotos: Boolean = false,
)

/**
 * Coroutine/StateFlow rewrite of BerthierOptions' `SlideshowController` (a Handler + callback-
 * Listener class). The algorithm is kept intact - indexed folder scan, shuffle that avoids
 * immediately repeating the just-shown photo, one-ahead preload, exponential-backoff reconnect
 * capped at 5 minutes, re-index every 15 minutes - only the plumbing changed. `context.config.*`
 * reads become a single `SettingsRepository.settings` snapshot per cycle.
 */
class PhotoramaViewModel(application: Application, private val settingsRepository: SettingsRepository) :
    AndroidViewModel(application) {

    private val lanPhotoRepository = LanPhotoRepository(application)
    private val cacheManager = PhotoCacheManager(application)

    private val _uiState = MutableStateFlow(PhotoramaUiState())
    val uiState: StateFlow<PhotoramaUiState> = _uiState

    private var photos: List<LanPhoto> = emptyList()
    private var order: List<Int> = emptyList()
    private var position = -1
    private var currentPhotoUrl: String? = null
    private var reconnectAttempt = 0
    private var lastIndexedAtMs = 0L
    private var watchJob: Job? = null

    private data class ConnectionConfig(
        val enabled: Boolean,
        val host: String,
        val share: String,
        val username: String,
        val domain: String,
        val rememberConnection: Boolean,
        val folderPath: String,
        val includeSubfolders: Boolean,
    )

    companion object {
        private const val INDEX_REFRESH_INTERVAL_MS = 15 * 60 * 1000L
        private const val MAX_RECONNECT_BACKOFF_SECONDS = 300L
        private const val MAX_RECONNECT_ATTEMPT_SHIFT = 5

        fun factory(application: Application, settingsRepository: SettingsRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PhotoramaViewModel(application, settingsRepository) as T
            }
    }

    /** Idempotent - safe to call from every composition of the Photorama page; only the first
     * call actually attaches the watcher. Unlike a one-shot settings read, this keeps observing
     * `settingsRepository.settings` for the rest of the process lifetime, so finishing setup in
     * Settings (a successful NAS test, picking a folder, or flipping "Enable Photorama") starts
     * the slideshow immediately instead of requiring an app restart to pick up config that didn't
     * exist yet the one time this used to check it. */
    fun ensureStarted() {
        if (watchJob != null) return
        watchJob = viewModelScope.launch {
            settingsRepository.settings
                .map { s ->
                    ConnectionConfig(
                        enabled = s.photoramaEnabled,
                        host = s.smbHost,
                        share = s.smbShareName,
                        username = s.smbUsername,
                        domain = s.smbDomain,
                        rememberConnection = s.smbRememberConnection,
                        folderPath = s.photoramaFolderPath,
                        includeSubfolders = s.photoramaIncludeSubfolders,
                    )
                }
                .distinctUntilChanged()
                .collectLatest { config ->
                    if (!config.enabled || config.host.isBlank() || config.share.isBlank() || config.folderPath.isBlank()) {
                        _uiState.value = PhotoramaUiState(isConfigured = false)
                        return@collectLatest
                    }
                    // Config changed out from under an in-flight index/loop (new folder, new
                    // share, credentials updated) - collectLatest already cancelled whatever
                    // suspend chain was running below, but the plain fields survive a
                    // cancellation, so they're reset explicitly to avoid showing a stale photo
                    // from the previous connection while the new one connects.
                    photos = emptyList()
                    order = emptyList()
                    position = -1
                    currentPhotoUrl = null
                    reconnectAttempt = 0
                    _uiState.value = _uiState.value.copy(isConfigured = true, currentPhoto = null, hasNoPhotos = false)
                    refreshIndex()
                }
        }
    }

    private suspend fun refreshIndex() {
        val settings = settingsRepository.settings.first()
        val share = settingsRepository.smbShareWithPassword()
        _uiState.value = _uiState.value.copy(
            connectionState = if (photos.isEmpty()) SmbConnectionState.CONNECTING else SmbConnectionState.RECONNECTING,
        )

        val result = withContext(Dispatchers.IO) {
            lanPhotoRepository.scanFolder(share, settings.photoramaFolderPath, settings.photoramaIncludeSubfolders)
        }
        when (result) {
            is SmbResult.Success -> onIndexed(result.value, settings.photoramaShuffle, settings.photoramaIntervalSeconds, share, settings.photoramaCacheSizeMb)
            is SmbResult.Failure -> onIndexFailed(result.state, settings.photoramaIntervalSeconds, share, settings.photoramaCacheSizeMb)
        }
    }

    private suspend fun onIndexed(newPhotos: List<LanPhoto>, shuffle: Boolean, intervalSeconds: Int, share: SmbShare, cacheSizeMb: Int) {
        reconnectAttempt = 0
        lastIndexedAtMs = System.currentTimeMillis()
        _uiState.value = _uiState.value.copy(connectionState = SmbConnectionState.CONNECTED)

        if (newPhotos.isEmpty()) {
            photos = emptyList()
            order = emptyList()
            currentPhotoUrl = null
            _uiState.value = _uiState.value.copy(hasNoPhotos = true, currentPhoto = null)
            scheduleReconnect(intervalSeconds, share, cacheSizeMb)
            return
        }

        val currentIndex = currentPhotoUrl?.let { url -> newPhotos.indexOfFirst { it.url == url }.takeIf { it >= 0 } }
        photos = newPhotos
        order = buildOrder(newPhotos.size, shuffle, avoidFirstIndex = currentIndex)

        if (currentIndex != null) {
            position = order.indexOf(currentIndex).coerceAtLeast(0)
        } else {
            if (position !in order.indices) position = 0
            showCurrent(share, cacheSizeMb)
        }
        runAdvanceLoop(intervalSeconds, shuffle, share, cacheSizeMb)
    }

    private suspend fun onIndexFailed(state: SmbConnectionState, intervalSeconds: Int, share: SmbShare, cacheSizeMb: Int) {
        _uiState.value = _uiState.value.copy(connectionState = state)
        if (photos.isNotEmpty()) {
            if (position !in order.indices) position = 0
            showCurrent(share, cacheSizeMb)
            runAdvanceLoop(intervalSeconds, false, share, cacheSizeMb, singleShot = true)
        }
        scheduleReconnect(intervalSeconds, share, cacheSizeMb)
    }

    private suspend fun runAdvanceLoop(intervalSeconds: Int, shuffle: Boolean, share: SmbShare, cacheSizeMb: Int, singleShot: Boolean = false) {
        val scope = viewModelScope
        if (!scope.isActive) return
        do {
            kotlinx.coroutines.delay((intervalSeconds * 1000L).coerceAtLeast(1000L))
            if (order.isEmpty()) {
                refreshIndex()
                return
            }
            if (System.currentTimeMillis() - lastIndexedAtMs > INDEX_REFRESH_INTERVAL_MS) {
                refreshIndex()
                return
            }
            val settings = settingsRepository.settings.first()
            if (settings.photoramaShuffle && order.size > 1 && position >= order.lastIndex) {
                val currentIndex = order.getOrNull(position)
                order = buildOrder(photos.size, true, avoidFirstIndex = currentIndex)
                position = -1
            }
            position = (position + 1) % order.size
            showCurrent(share, cacheSizeMb)
        } while (!singleShot)
    }

    private suspend fun showCurrent(share: SmbShare, cacheSizeMb: Int) {
        val photoIndex = order.getOrNull(position) ?: return
        val photo = photos.getOrNull(photoIndex) ?: return
        currentPhotoUrl = photo.url

        val cached = cacheManager.getCachedFile(photo)
        val ready = cached ?: withContext(Dispatchers.IO) {
            cacheManager.ensureCached(photo, share, cacheSizeMb * 1024L * 1024L)
        }
        if (ready != null) {
            _uiState.value = _uiState.value.copy(currentPhoto = ready, hasNoPhotos = false)
        }
        preloadNext(share, cacheSizeMb)
    }

    private fun preloadNext(share: SmbShare, cacheSizeMb: Int) {
        if (order.size <= 1) return
        val nextPos = (position + 1) % order.size
        val nextPhoto = photos.getOrNull(order[nextPos]) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (cacheManager.getCachedFile(nextPhoto) == null) {
                cacheManager.ensureCached(nextPhoto, share, cacheSizeMb * 1024L * 1024L)
            }
        }
    }

    private suspend fun scheduleReconnect(intervalSeconds: Int, share: SmbShare, cacheSizeMb: Int) {
        reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(MAX_RECONNECT_ATTEMPT_SHIFT)
        val backoffSeconds = (10L shl (reconnectAttempt - 1)).coerceAtMost(MAX_RECONNECT_BACKOFF_SECONDS)
        kotlinx.coroutines.delay(backoffSeconds * 1000L)
        refreshIndex()
    }

    private fun buildOrder(size: Int, shuffle: Boolean, avoidFirstIndex: Int?): List<Int> {
        val indices = (0 until size).toList()
        if (!shuffle || size <= 1) return indices

        val shuffled = indices.shuffled().toMutableList()
        if (avoidFirstIndex != null && shuffled.firstOrNull() == avoidFirstIndex) {
            val swapIndex = shuffled.indexOfFirst { it != avoidFirstIndex }
            if (swapIndex > 0) {
                val first = shuffled[0]
                shuffled[0] = shuffled[swapIndex]
                shuffled[swapIndex] = first
            }
        }
        return shuffled
    }
}
