package com.sconcept.mirrordash.clock

import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import kotlinx.serialization.Serializable

const val TEXT_WIDGET_FONT_DEFAULT = "DEFAULT"
const val TEXT_WIDGET_FONT_SANS_SERIF = "SANS_SERIF"
const val TEXT_WIDGET_FONT_SERIF = "SERIF"
const val TEXT_WIDGET_FONT_MONOSPACE = "MONOSPACE"
const val TEXT_WIDGET_FONT_CURSIVE = "CURSIVE"

const val TEXT_WIDGET_ANIMATION_NONE = "NONE"
const val TEXT_WIDGET_ANIMATION_FADE = "FADE"
const val TEXT_WIDGET_ANIMATION_TYPEWRITER = "TYPEWRITER"
const val TEXT_WIDGET_ANIMATION_BLINK = "BLINK"
const val TEXT_WIDGET_ANIMATION_PULSE = "PULSE"
const val TEXT_WIDGET_ANIMATION_HANDWRITE = "HANDWRITE"

/** A user-added freeform text overlay on the Clock page - as many as the user wants, each with
 * its own content, size, color, font, position (drag-to-place, same [OverlayAnchor] mechanism as
 * the clock/weather clusters), and an optional looping animation. Persisted as a list rather than
 * fixed fields since there's no cap on how many a user might add. */
@Serializable
data class CustomTextWidget(
    val id: String,
    val text: String = "New text",
    val fontSizeSp: Int = 28,
    val colorArgb: Int = 0xFFF5F3EF.toInt(),
    val fontFamily: String = TEXT_WIDGET_FONT_DEFAULT,
    val animation: String = TEXT_WIDGET_ANIMATION_NONE,
    val anchorX: Float = 0.5f,
    val anchorY: Float = 0.5f,
)

object TextWidgetFonts {
    val ALL = listOf(
        TEXT_WIDGET_FONT_DEFAULT,
        TEXT_WIDGET_FONT_SANS_SERIF,
        TEXT_WIDGET_FONT_SERIF,
        TEXT_WIDGET_FONT_MONOSPACE,
        TEXT_WIDGET_FONT_CURSIVE,
    )

    fun label(id: String): String = when (id) {
        TEXT_WIDGET_FONT_SANS_SERIF -> "Sans-serif"
        TEXT_WIDGET_FONT_SERIF -> "Serif"
        TEXT_WIDGET_FONT_MONOSPACE -> "Monospace"
        TEXT_WIDGET_FONT_CURSIVE -> "Cursive"
        else -> "Default"
    }

    fun family(id: String): FontFamily = when (id) {
        TEXT_WIDGET_FONT_SANS_SERIF -> FontFamily.SansSerif
        TEXT_WIDGET_FONT_SERIF -> FontFamily.Serif
        TEXT_WIDGET_FONT_MONOSPACE -> FontFamily.Monospace
        TEXT_WIDGET_FONT_CURSIVE -> FontFamily.Cursive
        else -> FontFamily.Default
    }

    /** Platform [Typeface] for the same font choice, needed only by the Handwrite animation -
     * it traces actual glyph outlines via [android.graphics.Paint.getTextPath], which is a
     * platform Canvas API with no Compose equivalent, so it works in `android.graphics` types
     * rather than Compose's [FontFamily]. */
    fun androidTypeface(id: String): Typeface = when (id) {
        TEXT_WIDGET_FONT_SANS_SERIF -> Typeface.SANS_SERIF
        TEXT_WIDGET_FONT_SERIF -> Typeface.SERIF
        TEXT_WIDGET_FONT_MONOSPACE -> Typeface.MONOSPACE
        TEXT_WIDGET_FONT_CURSIVE -> Typeface.create("cursive", Typeface.NORMAL)
        else -> Typeface.DEFAULT
    }
}

object TextWidgetAnimations {
    val ALL = listOf(
        TEXT_WIDGET_ANIMATION_NONE,
        TEXT_WIDGET_ANIMATION_FADE,
        TEXT_WIDGET_ANIMATION_TYPEWRITER,
        TEXT_WIDGET_ANIMATION_HANDWRITE,
        TEXT_WIDGET_ANIMATION_BLINK,
        TEXT_WIDGET_ANIMATION_PULSE,
    )

    fun label(id: String): String = when (id) {
        TEXT_WIDGET_ANIMATION_FADE -> "Fade in/out"
        TEXT_WIDGET_ANIMATION_TYPEWRITER -> "Type itself"
        TEXT_WIDGET_ANIMATION_HANDWRITE -> "Handwrite"
        TEXT_WIDGET_ANIMATION_BLINK -> "Blink"
        TEXT_WIDGET_ANIMATION_PULSE -> "Pulse"
        else -> "None"
    }
}
