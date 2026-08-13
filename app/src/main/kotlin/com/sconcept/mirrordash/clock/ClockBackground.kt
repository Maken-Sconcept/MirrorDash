package com.sconcept.mirrordash.clock

import androidx.compose.ui.graphics.Color

/**
 * What fills the Clock page behind the time/weather. Only [SolidColor] is implemented in v1;
 * [Photo] and [Photorama] are here so the background system doesn't need reshaping when those
 * ship (brief section 12) - Settings/ClockViewModel already route through this sealed
 * interface rather than a hard-coded color property on the screen itself.
 */
sealed interface ClockBackground {
    data class SolidColor(val color: Color) : ClockBackground
    data class Photo(val uri: String) : ClockBackground
    data object Photorama : ClockBackground
}
