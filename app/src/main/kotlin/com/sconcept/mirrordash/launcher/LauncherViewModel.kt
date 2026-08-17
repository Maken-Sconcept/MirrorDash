package com.sconcept.mirrordash.launcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sconcept.mirrordash.launcher.navigation.LauncherPages
import com.sconcept.mirrordash.settings.CLOCK_BACKGROUND_MODE_PHOTORAMA
import com.sconcept.mirrordash.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Owns which page the pager should open on. Persists the last-visited page (brief section 26:
 * "returning from another app... preserve the previously selected launcher page where sensible")
 * but never Settings or the App Drawer itself, so relaunching Home doesn't strand the user
 * mid-configuration - Clock is always a safe, sensible fallback for those.
 */
class LauncherViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    private val _initialPageIndex = MutableStateFlow<Int?>(null)
    val initialPageIndex: StateFlow<Int?> = _initialPageIndex

    private data class PageAvailability(
        val includePhotorama: Boolean,
        val includeBrowser: Boolean,
        val includeJellyfin: Boolean,
        val includeHomeAssistant: Boolean,
        val includeIptv: Boolean,
    )

    // Settings is always the last page regardless of how many pages exist, so "is this the last
    // page" is all onPageSettled needs - tracked here rather than looked up by page identity,
    // since the page list's length itself changes as optional pages toggle on/off.
    private var currentPageCount = LauncherPages.ordered(
        includePhotoramaPage = true,
        includeBrowserPage = true,
        includeJellyfinPage = true,
        includeHomeAssistantPage = true,
        includeIptvPage = true,
    ).size

    init {
        viewModelScope.launch {
            settingsRepository.settings
                .map {
                    PageAvailability(
                        includePhotorama = it.clockBackgroundMode != CLOCK_BACKGROUND_MODE_PHOTORAMA,
                        includeBrowser = it.browserEnabled,
                        includeJellyfin = it.jellyfinEnabled,
                        includeHomeAssistant = it.homeAssistantEnabled,
                        includeIptv = it.iptvEnabled,
                    )
                }
                .distinctUntilChanged()
                .collect { availability ->
                    currentPageCount = LauncherPages.ordered(
                        includePhotoramaPage = availability.includePhotorama,
                        includeBrowserPage = availability.includeBrowser,
                        includeJellyfinPage = availability.includeJellyfin,
                        includeHomeAssistantPage = availability.includeHomeAssistant,
                        includeIptvPage = availability.includeIptv,
                    ).size
                    if (_initialPageIndex.value == null) {
                        val stored = settingsRepository.settings.first().lastVisitedPageIndex
                        _initialPageIndex.value = stored.coerceIn(0, currentPageCount - 1)
                    }
                }
        }
    }

    fun onPageSettled(index: Int) {
        val toPersist = if (index == currentPageCount - 1) 0 else index
        viewModelScope.launch {
            settingsRepository.update { lastVisitedPageIndex = toPersist }
        }
    }

    companion object {
        fun factory(settingsRepository: SettingsRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    LauncherViewModel(settingsRepository) as T
            }
    }
}
