package com.sconcept.mirrordash.weather

const val WEATHER_ICON_STYLE_ANIMATED = "animated"
const val WEATHER_ICON_STYLE_FBGO = "fbgo"

object WeatherIconStyles {
    val ALL = listOf(WEATHER_ICON_STYLE_ANIMATED, WEATHER_ICON_STYLE_FBGO)

    fun label(id: String): String = when (id) {
        WEATHER_ICON_STYLE_FBGO -> "FBgo"
        else -> "Animated"
    }

    fun subtitle(id: String): String = when (id) {
        WEATHER_ICON_STYLE_FBGO -> "Flat color icons from Portal Go's ambient screen"
        else -> "MirrorDash's own hand-drawn, animated icons"
    }
}
