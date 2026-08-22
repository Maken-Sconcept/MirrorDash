package com.sconcept.mirrordash.weather

import androidx.annotation.DrawableRes
import com.sconcept.mirrordash.R

/** One entry of the FBgo icon theme - the full-color, flat-palette weather set lifted from Meta
 * Portal Go's Superframe ambient app. [id] is stable across app versions (used nowhere as a
 * pref key today, but kept distinct from [label] in case a future picker needs it). */
data class FbgoWeatherIconEntry(
    val id: String,
    val label: String,
    @DrawableRes val drawableRes: Int,
)

/** The bundled 15-icon FBgo set and the WMO weather-code -> icon mapping for it. [all] enumerates
 * every bundled drawable, including the two ("hazy") that [drawableRes] never resolves at runtime
 * since no Open-Meteo WMO code maps cleanly to haze - kept for a future browsable icon list. */
object FbgoWeatherIcons {
    val all: List<FbgoWeatherIconEntry> = listOf(
        FbgoWeatherIconEntry("clear_day", "Clear, day", R.drawable.ic_weather_fbgo_clear_day),
        FbgoWeatherIconEntry("partly_clear_day", "Partly clear, day", R.drawable.ic_weather_fbgo_partly_clear_day),
        FbgoWeatherIconEntry("partly_cloudy_day", "Partly cloudy, day", R.drawable.ic_weather_fbgo_partly_cloudy_day),
        FbgoWeatherIconEntry("hazy_day", "Hazy, day", R.drawable.ic_weather_fbgo_hazy_day),
        FbgoWeatherIconEntry("mostly_cloudy", "Mostly cloudy", R.drawable.ic_weather_fbgo_mostly_cloudy),
        FbgoWeatherIconEntry("fog", "Fog", R.drawable.ic_weather_fbgo_fog),
        FbgoWeatherIconEntry("rain", "Rain", R.drawable.ic_weather_fbgo_rain),
        FbgoWeatherIconEntry("sleet", "Sleet", R.drawable.ic_weather_fbgo_sleet),
        FbgoWeatherIconEntry("snow", "Snow", R.drawable.ic_weather_fbgo_snow),
        FbgoWeatherIconEntry("flurries", "Flurries", R.drawable.ic_weather_fbgo_flurries),
        FbgoWeatherIconEntry("thunderstorm", "Thunderstorm", R.drawable.ic_weather_fbgo_thunderstorm),
        FbgoWeatherIconEntry("clear_night", "Clear, night", R.drawable.ic_weather_fbgo_clear_night),
        FbgoWeatherIconEntry("partly_clear_night", "Partly clear, night", R.drawable.ic_weather_fbgo_partly_clear_night),
        FbgoWeatherIconEntry("partly_cloudy_night", "Partly cloudy, night", R.drawable.ic_weather_fbgo_partly_cloudy_night),
        FbgoWeatherIconEntry("hazy_night", "Hazy, night", R.drawable.ic_weather_fbgo_hazy_night),
    )

    /** Maps an Open-Meteo WMO [weatherCode] (see [weatherConditionForCode]) plus day/night to the
     * closest FBgo drawable. Finer-grained than [WeatherCondition] alone - e.g. it tells overcast
     * from partly cloudy, and freezing rain/drizzle from plain rain - since the FBgo set actually
     * distinguishes those, unlike the six-bucket enum built for the procedural Canvas icon. */
    @DrawableRes
    fun drawableRes(weatherCode: Int, isDay: Boolean): Int = when (weatherCode) {
        0 -> if (isDay) R.drawable.ic_weather_fbgo_clear_day else R.drawable.ic_weather_fbgo_clear_night
        1 -> if (isDay) R.drawable.ic_weather_fbgo_partly_clear_day else R.drawable.ic_weather_fbgo_partly_clear_night
        2 -> if (isDay) R.drawable.ic_weather_fbgo_partly_cloudy_day else R.drawable.ic_weather_fbgo_partly_cloudy_night
        3 -> R.drawable.ic_weather_fbgo_mostly_cloudy
        45, 48 -> R.drawable.ic_weather_fbgo_fog
        51, 53, 55, 61, 63, 65, 80, 81, 82 -> R.drawable.ic_weather_fbgo_rain
        56, 57, 66, 67 -> R.drawable.ic_weather_fbgo_sleet
        71, 73, 75, 85 -> R.drawable.ic_weather_fbgo_snow
        77, 86 -> R.drawable.ic_weather_fbgo_flurries
        95, 96, 99 -> R.drawable.ic_weather_fbgo_thunderstorm
        else -> R.drawable.ic_weather_fbgo_mostly_cloudy
    }
}
