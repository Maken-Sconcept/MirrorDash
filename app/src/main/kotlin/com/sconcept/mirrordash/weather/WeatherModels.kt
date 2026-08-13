package com.sconcept.mirrordash.weather

enum class WeatherCondition {
    CLEAR,
    CLOUDY,
    FOG,
    RAIN,
    SNOW,
    THUNDER,
}

data class WeatherLocation(
    val query: String,
    val label: String,
    val latitude: Double,
    val longitude: Double,
)

data class WeatherDayForecast(
    val dayLabel: String,
    val weatherCode: Int,
    val condition: WeatherCondition,
    val maxTemperature: Double,
    val minTemperature: Double,
)

data class WeatherSnapshot(
    val location: WeatherLocation,
    val currentTemperature: Double,
    val weatherCode: Int,
    val condition: WeatherCondition,
    val isDay: Boolean,
    val forecast: List<WeatherDayForecast>,
)

/** What the Clock page actually renders - deliberately minimal per the brief ("do not build a
 * giant forecast dashboard on the Clock page"). Forecast/day breakdown lives in [WeatherSnapshot]
 * for a future richer weather page, not here. */
data class WeatherUiState(
    val isLoading: Boolean = false,
    val temperature: Int? = null,
    val condition: WeatherCondition? = null,
    val locationLabel: String? = null,
    val isStale: Boolean = false,
    val errorMessage: String? = null,
    val isConfigured: Boolean = false,
)
