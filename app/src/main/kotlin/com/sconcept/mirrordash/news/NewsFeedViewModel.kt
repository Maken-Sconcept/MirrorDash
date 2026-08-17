package com.sconcept.mirrordash.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sconcept.mirrordash.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.minutes

data class NewsUiState(
    val headlinesByFeedUrl: Map<String, List<NewsHeadline>> = emptyMap(),
    val isLoading: Boolean = false,
)

/** Drives every News widget on the Clock page. Unlike Stocks (one shared symbol universe), each
 * widget can point at a different feed URL, so this fetches each distinct configured URL
 * separately (in parallel) on the same shared poll shape as
 * [com.sconcept.mirrordash.weather.WeatherViewModel]. */
class NewsFeedViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    private val repository = NewsFeedRepository()

    private val _uiState = MutableStateFlow(NewsUiState())
    val uiState: StateFlow<NewsUiState> = _uiState

    private val feedUrls = settingsRepository.settings
        .map { it.newsWidgets.map { widget -> widget.feedUrl.trim() }.filter { it.isNotBlank() }.distinct() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            while (true) {
                refreshInternal()
                kotlinx.coroutines.delay(REFRESH_INTERVAL_MINUTES.minutes)
            }
        }
    }

    fun refreshNow() {
        viewModelScope.launch { refreshInternal() }
    }

    private suspend fun refreshInternal() {
        val urls = feedUrls.value
        if (urls.isEmpty()) {
            _uiState.value = NewsUiState()
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true)
        val results = withContext(Dispatchers.IO) {
            urls.map { url -> async { url to repository.fetchHeadlines(url, MAX_HEADLINES_PER_FEED) } }.awaitAll()
        }
        val byUrl = results.mapNotNull { (url, result) -> result.getOrNull()?.let { url to it } }.toMap()
        _uiState.value = NewsUiState(
            headlinesByFeedUrl = _uiState.value.headlinesByFeedUrl + byUrl,
            isLoading = false,
        )
    }

    companion object {
        private const val REFRESH_INTERVAL_MINUTES = 20
        private const val MAX_HEADLINES_PER_FEED = 20

        fun factory(settingsRepository: SettingsRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    NewsFeedViewModel(settingsRepository) as T
            }
    }
}
