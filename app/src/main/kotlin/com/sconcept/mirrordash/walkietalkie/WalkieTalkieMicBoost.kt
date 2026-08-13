package com.sconcept.mirrordash.walkietalkie

object WalkieTalkieMicBoost {
    val presets = listOf(100, 125, 150, 175, 200, 250, 300)

    fun label(percent: Int): String {
        return if (percent == 100) "Normal" else "$percent%"
    }
}
