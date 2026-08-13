package com.sconcept.mirrordash.brightness

import kotlin.math.pow
import kotlin.math.roundToInt

/** Pure brightness math, ported from BerthierOptions' `BrightnessMath` (proven on this same
 * hardware family) - no UI or Android framework dependency. */
object BrightnessMath {

    /** Perceptually-spaced levels on the 0-255 backlight scale (dense at the dim end, where the
     * eye is most sensitive to small changes, sparse at the bright end) - the Brightness slider
     * snaps to these rather than 101 linear percent steps. */
    val QUICK_CONTROL_STEPS = intArrayOf(
        0, 1, 2, 3, 4, 5, 6, 8, 10, 12, 14, 18, 22, 28, 36, 48, 64, 80, 96, 112, 128, 160, 192, 224, 255,
    )

    /** Index of the step closest to [value255]. */
    fun stepIndexFor(value255: Int): Int {
        val clamped = value255.coerceIn(0, 255)
        var closest = 0
        var closestDelta = Int.MAX_VALUE
        for (i in QUICK_CONTROL_STEPS.indices) {
            val delta = kotlin.math.abs(QUICK_CONTROL_STEPS[i] - clamped)
            if (delta < closestDelta) {
                closestDelta = delta
                closest = i
            }
        }
        return closest
    }

    /** How dark the manual "extra dim" overlay should be, purely from the real backlight level -
     * fully transparent once the level is at least ~38% (96/255), ramping non-linearly to fully
     * opaque as the level approaches 0, so a low backlight is reinforced by the overlay rather
     * than leaving a washed-out, still-visible panel. */
    fun backlightValueToOverlayAlpha(value255: Int): Int {
        val clamped = value255.coerceIn(0, 255)
        if (clamped >= 96) return 0
        if (clamped == 0) return 255
        val progress = 1.0 - (clamped / 96.0).coerceIn(0.0, 1.0)
        return (progress.pow(1.35) * 235.0).roundToInt().coerceIn(0, 255)
    }

    /** The user's own manual "dim even more" slider, 0-100%, mapped straight to an alpha byte. */
    fun extraDimPercentToAlpha(percent: Int): Int = (percent.coerceIn(0, 100) * 255 / 100)
}
