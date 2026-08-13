package com.sconcept.mirrordash.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic design tokens for MirrorDash. Ambient/dark-first: the launcher's screen is on for
 * hours at a time, so the palette favors low-contrast surfaces with a single warm accent
 * rather than saturated Material colors. Composables read these via [LocalMirrorDashColors]
 * instead of hard-coding [Color] literals.
 */
data class MirrorDashColors(
    val background: Color,
    val backgroundElevated: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val scrim: Color,
    val divider: Color,
    val accent: Color,
    val onAccent: Color,
    val weatherAccent: Color,
    val danger: Color,
)

val MirrorDashDarkColors = MirrorDashColors(
    background = Color(0xFF0B0C0E),
    backgroundElevated = Color(0xFF16181C),
    surface = Color(0xFF1C1F24),
    surfaceElevated = Color(0xFF262A31),
    textPrimary = Color(0xFFF5F3EF),
    textSecondary = Color(0xFFAEB2BB),
    // 9096A0 not 787D87: the darker value measured 4.00:1 against `surface` - below WCAG AA's
    // 4.5:1 for normal text, and this token is used at caption size (IP addresses, timestamps)
    // read from a few feet away. 9096A0 clears AA against every background/surface token.
    textTertiary = Color(0xFF9096A0),
    scrim = Color(0xCC05060A),
    divider = Color(0x1FFFFFFF),
    accent = Color(0xFFE8A659),
    onAccent = Color(0xFF231404),
    weatherAccent = Color(0xFF7FB6D9),
    danger = Color(0xFFE0665A),
)

val LocalMirrorDashColors = staticCompositionLocalOf { MirrorDashDarkColors }

/** Preset accent/clock colors offered in Settings, in addition to a free color picker. */
val ClockColorPresets = listOf(
    Color(0xFFF5F3EF),
    Color(0xFFE8A659),
    Color(0xFF7FB6D9),
    Color(0xFFA8C97F),
    Color(0xFFD98CC0),
    Color(0xFFE0665A),
)

/** Preset solid background colors offered for the Clock page. */
val ClockBackgroundPresets = listOf(
    Color(0xFF0B0C0E),
    Color(0xFF11151C),
    Color(0xFF1A1410),
    Color(0xFF101613),
    Color(0xFF17131C),
    Color(0xFF000000),
)
