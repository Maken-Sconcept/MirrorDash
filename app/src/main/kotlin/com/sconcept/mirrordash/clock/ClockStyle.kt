package com.sconcept.mirrordash.clock

const val CLOCK_STYLE_CLASSIC = "CLASSIC"
const val CLOCK_STYLE_BIG_SMALL = "BIG_SMALL"
const val CLOCK_STYLE_OVERLAP = "OVERLAP"
const val CLOCK_STYLE_VERTICAL = "VERTICAL"
const val CLOCK_STYLE_MINIMALIST = "MINIMALIST"
const val CLOCK_STYLE_FLIP = "FLIP"

data class ClockStyleDefinition(
    val id: String,
    val title: String,
    val subtitle: String,
)

object ClockStyleCatalog {
    val presets = listOf(
        ClockStyleDefinition(
            id = CLOCK_STYLE_CLASSIC,
            title = "Classic",
            subtitle = "Large time with a quiet info line",
        ),
        ClockStyleDefinition(
            id = CLOCK_STYLE_BIG_SMALL,
            title = "Big / Small",
            subtitle = "Niagara-like split hour and minute",
        ),
        ClockStyleDefinition(
            id = CLOCK_STYLE_OVERLAP,
            title = "Overlap",
            subtitle = "Layered digits with stronger personality",
        ),
        ClockStyleDefinition(
            id = CLOCK_STYLE_VERTICAL,
            title = "Vertical",
            subtitle = "A stacked 2x2 digit grid",
        ),
        ClockStyleDefinition(
            id = CLOCK_STYLE_MINIMALIST,
            title = "Minimalist",
            subtitle = "Soft ambient numerals in the background",
        ),
        ClockStyleDefinition(
            id = CLOCK_STYLE_FLIP,
            title = "Flip",
            subtitle = "Carded tiles with a clock-radio feel",
        ),
    )

    fun preset(id: String): ClockStyleDefinition =
        presets.firstOrNull { it.id == id } ?: presets.first { it.id == CLOCK_STYLE_BIG_SMALL }
}

data class ClockRenderData(
    val hour: String,
    val minute: String,
    val hourPadded: String,
    val minutePadded: String,
    val timeText: String,
    val infoLine: String,
    val weatherGlyph: String?,
)
