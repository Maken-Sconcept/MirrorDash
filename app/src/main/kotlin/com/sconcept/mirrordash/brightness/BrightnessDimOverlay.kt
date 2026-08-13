package com.sconcept.mirrordash.brightness

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color

/**
 * Layer 2 of the brightness system (see [BacklightController]'s doc comment for the full
 * picture) - a plain full-screen black [Box] drawn above everything else in MirrorDash's own
 * composition. No `SYSTEM_ALERT_WINDOW` needed, since it's just app content sitting on top of
 * this app's own UI, not a system overlay dimming other apps the way BerthierOptions' equivalent
 * has to. Carries no pointer-input modifiers of its own, so touches (drag-to-reposition on clock
 * widgets, the failsafe hold-anywhere timer, taps to enter Travel mode) pass straight through to
 * whatever's underneath.
 */
@Composable
fun BrightnessDimOverlay(brightnessLevel255: Int, extraDimPercent: Int, modifier: Modifier = Modifier) {
    val autoAlpha = BrightnessMath.backlightValueToOverlayAlpha(brightnessLevel255)
    val manualAlpha = BrightnessMath.extraDimPercentToAlpha(extraDimPercent)
    val alpha = maxOf(autoAlpha, manualAlpha) / 255f
    if (alpha <= 0f) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(alpha)
            .background(Color.Black),
    )
}
