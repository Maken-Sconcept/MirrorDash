package com.sconcept.mirrordash.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember

@Composable
fun MirrorDashTheme(
    clockFontSizeSp: Int = DEFAULT_CLOCK_FONT_SIZE_SP,
    content: @Composable () -> Unit,
) {
    val colors = MirrorDashDarkColors
    val typography = remember(clockFontSizeSp) { mirrorDashTypography(clockFontSizeSp) }
    val materialScheme = remember(colors) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceElevated,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.divider,
            error = colors.danger,
        )
    }

    CompositionLocalProvider(
        LocalMirrorDashColors provides colors,
        LocalMirrorDashTypography provides typography,
    ) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = MirrorDashMaterialTypography,
            content = content,
        )
    }
}

/** Access point for the semantic tokens from inside any composable: `MDTheme.colors.accent`. */
object MDTheme {
    val colors: MirrorDashColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMirrorDashColors.current

    val type: MirrorDashTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalMirrorDashTypography.current
}
