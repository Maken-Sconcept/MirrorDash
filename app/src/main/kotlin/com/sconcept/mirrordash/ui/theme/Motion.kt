package com.sconcept.mirrordash.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared motion specs so every travel-mode / drawer / notification transition feels like the
 * same physical system rather than each screen inventing its own timing. Springs (not tweens)
 * drive anything the user's finger was just touching, so a mid-gesture direction change
 * continues smoothly instead of restarting an easing curve.
 */
object MirrorDashMotion {
    fun <T> settleSpring(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    fun <T> snappySpring(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    val emphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    val quickFade = tween<Float>(durationMillis = 180, easing = emphasizedEasing)
    val crossfade = tween<Float>(durationMillis = 900, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f))

    val travelModeOuterPadding: Dp = 28.dp
    val travelModeCornerRadius: Dp = 28.dp
    val travelModeScale = 0.94f

    /** How long the interface waits, with no touch input, before Travel mode settles back to
     * Ambient/fullscreen. Long enough to read a setting's description, type into a field, or
     * pause mid-task without the shrink/grow transition replaying on the next tap - short values
     * here are the actual cause of "the animation plays every time I tap," since it isn't a
     * replay at all, it's a genuine Travel->Ambient->Travel round trip triggered by a normal
     * pause exceeding the window. Only fires once real inactivity - not just no touches this
     * instant - has elapsed. */
    const val TRAVEL_IDLE_TIMEOUT_MS = 15000L
}
