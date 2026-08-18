package com.sconcept.mirrordash.clock

import com.sconcept.mirrordash.settings.DEFAULT_WEATHER_ANCHOR_X
import com.sconcept.mirrordash.settings.DEFAULT_WEATHER_ANCHOR_Y
import kotlinx.serialization.Serializable

const val WEATHER_WIDGET_MODE_LINE = "LINE"
const val WEATHER_WIDGET_MODE_FORECAST_CARD = "FORECAST_CARD"

const val WEATHER_WIDGET_FORECAST_HOURLY = "HOURLY"
const val WEATHER_WIDGET_FORECAST_DAILY = "DAILY"

@Serializable
data class WeatherWidget(
    override val id: String,
    val mode: String = WEATHER_WIDGET_MODE_LINE,
    val forecastMode: String = WEATHER_WIDGET_FORECAST_HOURLY,
    val itemCount: Int = 6,
    val scalePercent: Int = 100,
    val showLocation: Boolean = true,
    override val anchorX: Float = DEFAULT_WEATHER_ANCHOR_X,
    override val anchorY: Float = DEFAULT_WEATHER_ANCHOR_Y,
    override val rotationDegrees: Float = 0f,
) : AnchoredWidget

fun defaultWeatherWidget(
    id: String = java.util.UUID.randomUUID().toString(),
    anchorX: Float = DEFAULT_WEATHER_ANCHOR_X,
    anchorY: Float = DEFAULT_WEATHER_ANCHOR_Y,
): WeatherWidget = WeatherWidget(
    id = id,
    mode = WEATHER_WIDGET_MODE_LINE,
    forecastMode = WEATHER_WIDGET_FORECAST_HOURLY,
    itemCount = 6,
    scalePercent = 100,
    showLocation = true,
    anchorX = anchorX,
    anchorY = anchorY,
)

object WeatherWidgetModes {
    val ALL = listOf(WEATHER_WIDGET_MODE_LINE, WEATHER_WIDGET_MODE_FORECAST_CARD)

    fun label(id: String): String = when (id) {
        WEATHER_WIDGET_MODE_FORECAST_CARD -> "Forecast card"
        else -> "Simple line"
    }
}

object WeatherWidgetForecastModes {
    val ALL = listOf(WEATHER_WIDGET_FORECAST_HOURLY, WEATHER_WIDGET_FORECAST_DAILY)

    fun label(id: String): String = when (id) {
        WEATHER_WIDGET_FORECAST_DAILY -> "Next days"
        else -> "Next hours"
    }
}
