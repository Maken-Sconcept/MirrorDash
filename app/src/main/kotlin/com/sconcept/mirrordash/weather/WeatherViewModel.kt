package com.sconcept.mirrordash.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sconcept.mirrordash.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes

/**
 * Drives the Clock page's weather line. Deliberately thin compared to BerthierOptions'
 * PhotoClockView-embedded weather rendering: this only tracks what the minimal Clock weather
 * line needs ([WeatherUiState]), refreshing on a timer and marking itself stale (rather than
 * blank) when a refresh fails so the last known reading stays visible per the brief's offline
 * requirements.
 */
class WeatherViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    private val repository = WeatherRepository()

    private val _uiState = MutableStateFlow(WeatherUiState())
    val uiState: StateFlow<WeatherUiState> = _uiState

    private data class LocationConfig(
        val query: String,
        val label: String,
        val latitude: String,
        val longitude: String,
        val useFahrenheit: Boolean,
        val enabled: Boolean,
    )

    private val locationConfig = settingsRepository.settings
        .map {
            LocationConfig(it.weatherLocationQuery, it.weatherLocationLabel, it.weatherLatitude, it.weatherLongitude, it.weatherUseFahrenheit, it.weatherEnabled)
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, LocationConfig("", "", "", "", false, true))

    private var lastKnownCondition: WeatherCondition? = null
    private var lastKnownTemperature: Int? = null
    private var lastKnownLabel: String? = null
    private var lastKnownSnapshot: WeatherSnapshot? = null

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
        val config = locationConfig.value
        if (!config.enabled) {
            _uiState.value = WeatherUiState(isConfigured = false)
            return
        }
        var lat = config.latitude.toDoubleOrNull()
        var lon = config.longitude.toDoubleOrNull()
        if ((lat == null || lon == null) && config.query.isNotBlank()) {
            repository.resolveLocation(config.query).getOrNull()?.let { resolved ->
                lat = resolved.latitude
                lon = resolved.longitude
                settingsRepository.update {
                    weatherLocationQuery = resolved.query
                    weatherLocationLabel = resolved.label
                    weatherLatitude = resolved.latitude.toString()
                    weatherLongitude = resolved.longitude.toString()
                    weatherEnabled = true
                }
            }
        }
        if (lat == null || lon == null) {
            _uiState.value = WeatherUiState(isConfigured = false)
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, isConfigured = true)
        val location = WeatherLocation(config.query, config.label, lat, lon)
        val result = repository.fetchWeather(location, config.useFahrenheit)

        result.fold(
            onSuccess = { snapshot ->
                lastKnownCondition = snapshot.condition
                lastKnownTemperature = snapshot.currentTemperature.roundToInt()
                lastKnownLabel = config.label.ifBlank { null }
                lastKnownSnapshot = snapshot
                _uiState.value = WeatherUiState(
                    isLoading = false,
                    temperature = lastKnownTemperature,
                    condition = lastKnownCondition,
                    locationLabel = lastKnownLabel,
                    snapshot = snapshot,
                    isStale = false,
                    isConfigured = true,
                )
            },
            onFailure = { error ->
                _uiState.value = WeatherUiState(
                    isLoading = false,
                    temperature = lastKnownTemperature,
                    condition = lastKnownCondition,
                    locationLabel = lastKnownLabel,
                    snapshot = lastKnownSnapshot,
                    isStale = lastKnownTemperature != null,
                    errorMessage = error.message,
                    isConfigured = true,
                )
            },
        )
    }

    companion object {
        private const val REFRESH_INTERVAL_MINUTES = 20

        fun factory(settingsRepository: SettingsRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    WeatherViewModel(settingsRepository) as T
            }
    }
}
