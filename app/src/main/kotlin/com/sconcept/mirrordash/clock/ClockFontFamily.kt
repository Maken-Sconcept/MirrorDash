package com.sconcept.mirrordash.clock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily

/** Resolves [fontId] (a system font, a starter-pack Google Font, or one the user downloaded)
 * against [ClockFontLibrary] and wraps it as a Compose [FontFamily] - the same platform
 * [android.graphics.Typeface] the clock digits themselves use (see ClockTextBlock), just bridged
 * into Compose's text APIs so ordinary [androidx.compose.material3.Text] can use it too. */
@Composable
internal fun rememberClockFontFamily(fontId: String, downloadedFonts: List<DownloadedClockFont>): FontFamily {
    val context = LocalContext.current
    return remember(fontId, downloadedFonts) {
        FontFamily(ClockFontLibrary.typeface(context, fontId, downloadedFonts))
    }
}
