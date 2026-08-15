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

data class WeatherHourForecast(
    val timeLabel: String,
    val weatherCode: Int,
    val condition: WeatherCondition,
    val temperature: Double,
    val isDay: Boolean,
)

data class WeatherSnapshot(
    val location: WeatherLocation,
    val currentTemperature: Double,
    val weatherCode: Int,
    val condition: WeatherCondition,
    val isDay: Boolean,
    val hourlyForecast: List<WeatherHourForecast>,
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
    val snapshot: WeatherSnapshot? = null,
    val isStale: Boolean = false,
    val errorMessage: String? = null,
    val isConfigured: Boolean = false,
)

fun weatherConditionForCode(code: Int): WeatherCondition = when (code) {
    0 -> WeatherCondition.CLEAR
    1, 2, 3 -> WeatherCondition.CLOUDY
    45, 48 -> WeatherCondition.FOG
    51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> WeatherCondition.RAIN
    71, 73, 75, 77, 85, 86 -> WeatherCondition.SNOW
    95, 96, 99 -> WeatherCondition.THUNDER
    else -> WeatherCondition.CLOUDY
}

fun weatherConditionLabel(code: Int, isDay: Boolean): String = when (code) {
    0 -> if (isDay) "Clear" else "Clear night"
    1 -> "Mainly clear"
    2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Fog"
    51, 53, 55 -> "Drizzle"
    56, 57 -> "Freezing drizzle"
    61, 63, 65 -> "Rain"
    66, 67 -> "Freezing rain"
    71, 73, 75 -> "Snow"
    77 -> "Snow grains"
    80, 81, 82 -> "Showers"
    85, 86 -> "Snow showers"
    95 -> "Thunderstorm"
    96, 99 -> "Storm with hail"
    else -> "Weather"
}
