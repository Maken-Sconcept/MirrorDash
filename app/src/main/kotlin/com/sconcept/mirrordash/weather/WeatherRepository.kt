package com.sconcept.mirrordash.weather

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Ported unchanged from BerthierOptions: free, keyless Open-Meteo geocoding + forecast APIs
 * over plain HttpURLConnection/org.json. No secrets to manage, no provider SDK to swap in.
 */
class WeatherRepository {
    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 10_000
        private const val FORECAST_DAYS = 4
    }

    fun resolveLocation(input: String): Result<WeatherLocation> = runCatching {
        val trimmed = input.trim()
        require(trimmed.isNotBlank()) { "Enter a city or coordinates first." }

        parseCoordinates(trimmed)?.let { (latitude, longitude) ->
            return@runCatching WeatherLocation(
                query = trimmed,
                label = String.format(Locale.US, "%.4f, %.4f", latitude, longitude),
                latitude = latitude,
                longitude = longitude,
            )
        }

        val encoded = URLEncoder.encode(trimmed, StandardCharsets.UTF_8.name())
        val response = readJson(
            "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=en&format=json",
        )
        val results = response.optJSONArray("results")
            ?: error("No matching city was found.")
        require(results.length() > 0) { "No matching city was found." }

        val best = results.getJSONObject(0)
        WeatherLocation(
            query = trimmed,
            label = buildLocationLabel(best),
            latitude = best.getDouble("latitude"),
            longitude = best.getDouble("longitude"),
        )
    }

    fun fetchWeather(location: WeatherLocation, useFahrenheit: Boolean): Result<WeatherSnapshot> = runCatching {
        val unit = if (useFahrenheit) "fahrenheit" else "celsius"
        val url = buildString {
            append("https://api.open-meteo.com/v1/forecast?")
            append("latitude=${location.latitude}")
            append("&longitude=${location.longitude}")
            append("&current=temperature_2m,weather_code,is_day")
            append("&daily=weather_code,temperature_2m_max,temperature_2m_min")
            append("&forecast_days=$FORECAST_DAYS")
            append("&timezone=auto")
            append("&temperature_unit=$unit")
        }
        val response = readJson(url)
        val current = response.getJSONObject("current")
        val weatherCode = current.getInt("weather_code")
        val isDay = current.optInt("is_day", 1) == 1
        val daily = response.getJSONObject("daily")

        WeatherSnapshot(
            location = location,
            currentTemperature = current.getDouble("temperature_2m"),
            weatherCode = weatherCode,
            condition = mapWeatherCode(weatherCode),
            isDay = isDay,
            forecast = parseForecast(daily),
        )
    }

    fun mapWeatherCode(code: Int): WeatherCondition = when (code) {
        0 -> WeatherCondition.CLEAR
        1, 2, 3 -> WeatherCondition.CLOUDY
        45, 48 -> WeatherCondition.FOG
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> WeatherCondition.RAIN
        71, 73, 75, 77, 85, 86 -> WeatherCondition.SNOW
        95, 96, 99 -> WeatherCondition.THUNDER
        else -> WeatherCondition.CLOUDY
    }

    fun conditionLabel(code: Int, isDay: Boolean): String = when (code) {
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

    private fun parseForecast(daily: JSONObject): List<WeatherDayForecast> {
        val times = daily.getJSONArray("time")
        val codes = daily.getJSONArray("weather_code")
        val maxTemps = daily.getJSONArray("temperature_2m_max")
        val minTemps = daily.getJSONArray("temperature_2m_min")
        val formatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
        val items = ArrayList<WeatherDayForecast>()
        for (index in 1 until minOf(times.length(), FORECAST_DAYS)) {
            val date = LocalDate.parse(times.getString(index))
            val code = codes.getInt(index)
            items.add(
                WeatherDayForecast(
                    dayLabel = formatter.format(date),
                    weatherCode = code,
                    condition = mapWeatherCode(code),
                    maxTemperature = maxTemps.getDouble(index),
                    minTemperature = minTemps.getDouble(index),
                ),
            )
        }
        return items
    }

    private fun buildLocationLabel(item: JSONObject): String {
        val parts = listOfNotNull(
            item.optString("name").ifBlank { null },
            item.optString("admin1").ifBlank { null },
            item.optString("country").ifBlank { null },
        )
        return parts.distinct().joinToString(", ")
    }

    private fun readJson(url: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty("Accept", "application/json")
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = BufferedReader(InputStreamReader(stream)).use { it.readText() }
            require(code in 200..299) { JSONObject(body).optString("reason").ifBlank { "Weather service error." } }
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseCoordinates(input: String): Pair<Double, Double>? {
        val parts = input.split(",").map { it.trim() }
        if (parts.size != 2) {
            return null
        }

        val latitude = parts[0].toDoubleOrNull() ?: return null
        val longitude = parts[1].toDoubleOrNull() ?: return null
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90." }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180." }
        return latitude to longitude
    }
}
