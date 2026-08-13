package com.sconcept.mirrordash.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Calm, legible type scale built on the platform system font (no bundled/proprietary
 * typeface). Optical weight is kept light/medium rather than bold so the clock and settings
 * text feel like part of one quiet surface rather than shouting for attention.
 */
private val displayFamily = FontFamily.SansSerif

data class MirrorDashTypography(
    val clock: TextStyle,
    val clockSeconds: TextStyle,
    val date: TextStyle,
    val weather: TextStyle,
    val pageLabel: TextStyle,
    val sectionTitle: TextStyle,
    val settingTitle: TextStyle,
    val settingSubtitle: TextStyle,
    val body: TextStyle,
    val caption: TextStyle,
)

/** Default clock text size in sp; overridden by the user's font-size preference at render time. */
const val DEFAULT_CLOCK_FONT_SIZE_SP = 120

fun mirrorDashTypography(clockFontSizeSp: Int = DEFAULT_CLOCK_FONT_SIZE_SP): MirrorDashTypography {
    return MirrorDashTypography(
        clock = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.Light,
            fontSize = clockFontSizeSp.sp,
            letterSpacing = (-1).sp,
        ),
        clockSeconds = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (clockFontSizeSp * 0.28f).sp,
        ),
        date = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 22.sp,
            letterSpacing = 0.2.sp,
        ),
        weather = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 24.sp,
        ),
        pageLabel = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            letterSpacing = 1.2.sp,
        ),
        sectionTitle = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
        ),
        settingTitle = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 17.sp,
        ),
        settingSubtitle = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
        ),
        body = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
        ),
        caption = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
        ),
    )
}

val LocalMirrorDashTypography = staticCompositionLocalOf { mirrorDashTypography() }

/** Material3 typography derived from the same family, used only where a stock M3 component
 * (e.g. TextField, Slider label) asks for one. */
val MirrorDashMaterialTypography = Typography()
