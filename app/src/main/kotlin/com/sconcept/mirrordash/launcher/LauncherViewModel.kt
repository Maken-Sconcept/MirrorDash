package com.sconcept.mirrordash.launcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sconcept.mirrordash.launcher.navigation.LauncherPage
import com.sconcept.mirrordash.launcher.navigation.LauncherPages
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
        val includeBrowser: Boolean,
        val includeJellyfin: Boolean,
        val includeHomeAssistant: Boolean,
        val includeIptv: Boolean,
        val includeKodi: Boolean,
    )

    // Optional pages come and go with settings, so persistence is keyed off the actual page
    // identity at each index, not a hardcoded slot number. Settings and Kodi deliberately fall
    // back to Clock on relaunch: Settings so Home doesn't strand you mid-configuration, Kodi
    // because returning Home from the external Kodi app should not immediately launch it again.
    private var currentOrderedPages = LauncherPages.ordered(
        includeBrowserPage = true,
        includeJellyfinPage = true,
        includeHomeAssistantPage = true,
        includeIptvPage = true,
        includeKodiPage = true,
    )

    init {
        viewModelScope.launch {
            settingsRepository.settings
                .map {
                    PageAvailability(
                        includeBrowser = it.browserEnabled,
                        includeJellyfin = it.jellyfinEnabled,
                        includeHomeAssistant = it.homeAssistantEnabled,
                        includeIptv = it.iptvEnabled,
                        includeKodi = it.kodiEnabled,
                    )
                }
                .distinctUntilChanged()
                .collect { availability ->
                    currentOrderedPages = LauncherPages.ordered(
                        includeBrowserPage = availability.includeBrowser,
                        includeJellyfinPage = availability.includeJellyfin,
                        includeHomeAssistantPage = availability.includeHomeAssistant,
                        includeIptvPage = availability.includeIptv,
                        includeKodiPage = availability.includeKodi,
                    )
                    if (_initialPageIndex.value == null) {
                        val stored = settingsRepository.settings.first().lastVisitedPageIndex
                        _initialPageIndex.value = stored.coerceIn(0, currentOrderedPages.lastIndex)
                    }
                }
        }
    }

    fun onPageSettled(index: Int) {
        val page = currentOrderedPages.getOrNull(index)
        val toPersist = if (page == LauncherPage.Settings || page == LauncherPage.Kodi) 0 else index
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
