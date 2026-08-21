package com.sconcept.mirrordash.weather

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sconcept.mirrordash.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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
        // Reads the settings Flow directly (suspending for its real first emission) rather than
        // through a StateFlow snapshot - DataStore's first read is async disk I/O, so a
        // SharingStarted.Eagerly-shared StateFlow could still hold its blank initial placeholder
        // the moment this runs from init{}, silently losing the race and reporting "not
        // configured" for the *entire* 20-minute refresh interval before ever seeing the real
        // persisted location.
        val settings = settingsRepository.settings.first()
        val config = LocationConfig(
            settings.weatherLocationQuery,
            settings.weatherLocationLabel,
            settings.weatherLatitude,
            settings.weatherLongitude,
            settings.weatherUseFahrenheit,
            settings.weatherEnabled,
        )
        Log.i(TAG, "refreshInternal: enabled=${config.enabled} query='${config.query}' lat=${config.latitude} lon=${config.longitude}")
        if (!config.enabled) {
            _uiState.value = WeatherUiState(isConfigured = false)
            return
        }
        var lat = config.latitude.toDoubleOrNull()
        var lon = config.longitude.toDoubleOrNull()
        if ((lat == null || lon == null) && config.query.isNotBlank()) {
            repository.resolveLocation(config.query)
                .onFailure { Log.w(TAG, "Could not resolve weather location '${config.query}'", it) }
                .getOrNull()?.let { resolved ->
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
                Log.i(TAG, "refreshInternal: success temp=${snapshot.currentTemperature} condition=${snapshot.condition}")
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
                Log.w(TAG, "Weather fetch failed for ${config.label.ifBlank { config.query }} ($lat, $lon)", error)
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
        private const val TAG = "WeatherViewModel"
        private const val REFRESH_INTERVAL_MINUTES = 20

        fun factory(settingsRepository: SettingsRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    WeatherViewModel(settingsRepository) as T
            }
    }
}
