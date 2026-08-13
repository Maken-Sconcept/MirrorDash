package com.sconcept.mirrordash.clock

import kotlin.math.abs
import kotlin.math.max

data class OverlayAnchor(val x: Float, val y: Float)
private data class OverlayObstacle(val anchor: OverlayAnchor, val width: Int, val height: Int)
private data class OverlayPlacement(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Drag-to-position math for the Clock/weather overlays, ported from BerthierOptions'
 * `PhotoClockLayoutHelper` - the algorithm itself (obstacle-avoidance placement so the weather
 * line never gets dragged on top of the clock) was never the source of the position-reset bug;
 * that came from BerthierOptions storing the *default* clock position in two disagreeing places
 * (a dead anchor-enum, and a raw-float pair defaulting to the wrong corner). MirrorDash has
 * exactly one stored anchor per draggable element with exactly one default (see
 * `MirrorDashSettings`), so the bug class can't recur even with dragging restored.
 */
object ClockLayoutHelper {
    private val fallbackAnchors = buildList {
        add(OverlayAnchor(1f, 0f))
        add(OverlayAnchor(1f, 1f))
        add(OverlayAnchor(0f, 1f))
        add(OverlayAnchor(0.5f, 0f))
        add(OverlayAnchor(1f, 0.5f))
        add(OverlayAnchor(0.5f, 1f))
        add(OverlayAnchor(0f, 0.5f))
        add(OverlayAnchor(0.5f, 0.5f))
        add(OverlayAnchor(0f, 0f))
        val grid = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        grid.forEach { y -> grid.forEach { x -> add(OverlayAnchor(x, y)) } }
    }

    fun clamp(anchor: OverlayAnchor): OverlayAnchor =
        OverlayAnchor(anchor.x.coerceIn(0f, 1f), anchor.y.coerceIn(0f, 1f))

    /** Where a child of [childWidth]x[childHeight] should sit, in pixels, for a given [anchor]
     * fraction within a [parentWidth]x[parentHeight] area inset by [insetPx]. */
    fun placementPx(
        anchor: OverlayAnchor,
        childWidth: Int,
        childHeight: Int,
        parentWidth: Int,
        parentHeight: Int,
        insetPx: Float,
    ): Pair<Float, Float> {
        val widthRange = max(0f, parentWidth - childWidth - insetPx * 2f)
        val heightRange = max(0f, parentHeight - childHeight - insetPx * 2f)
        val clamped = clamp(anchor)
        return (insetPx + widthRange * clamped.x) to (insetPx + heightRange * clamped.y)
    }

    /** Converts a raw pixel position (e.g. mid-drag) back into a clamped anchor fraction. */
    fun anchorForPosition(
        left: Float,
        top: Float,
        childWidth: Int,
        childHeight: Int,
        parentWidth: Int,
        parentHeight: Int,
        insetPx: Float,
    ): OverlayAnchor {
        val widthRange = max(1f, parentWidth - childWidth - insetPx * 2f)
        val heightRange = max(1f, parentHeight - childHeight - insetPx * 2f)
        val clampedLeft = left.coerceIn(insetPx, insetPx + widthRange)
        val clampedTop = top.coerceIn(insetPx, insetPx + heightRange)
        return clamp(OverlayAnchor((clampedLeft - insetPx) / widthRange, (clampedTop - insetPx) / heightRange))
    }

    /** Nudges [desiredAnchor] away from [obstacleAnchor] (the clock) if placing the weather
     * line there would overlap it, trying nearby positions before falling back to the anchor
     * farthest from the desired one. */
    fun resolveAvoidingObstacle(
        desiredAnchor: OverlayAnchor,
        obstacleAnchor: OverlayAnchor,
        parentWidth: Int,
        parentHeight: Int,
        childWidth: Int,
        childHeight: Int,
        obstacleWidth: Int,
        obstacleHeight: Int,
        insetPx: Float,
        spacingPx: Float = insetPx * 0.75f,
    ): OverlayAnchor {
        val normalizedDesired = clamp(desiredAnchor)
        if (parentWidth <= 0 || parentHeight <= 0 || childWidth <= 0 || childHeight <= 0 || obstacleWidth <= 0 || obstacleHeight <= 0) {
            return normalizedDesired
        }

        fun placementFor(anchor: OverlayAnchor, w: Int, h: Int): OverlayPlacement {
            val (left, top) = placementPx(anchor, w, h, parentWidth, parentHeight, insetPx)
            return OverlayPlacement(left, top, left + w, top + h)
        }

        val obstaclePlacement = placementFor(obstacleAnchor, obstacleWidth, obstacleHeight)
        val desiredPlacement = placementFor(normalizedDesired, childWidth, childHeight)
        fun overlaps(a: OverlayPlacement, b: OverlayPlacement) =
            a.left < b.right + spacingPx && a.right + spacingPx > b.left && a.top < b.bottom + spacingPx && a.bottom + spacingPx > b.top

        if (!overlaps(obstaclePlacement, desiredPlacement)) return normalizedDesired

        val candidates = buildList {
            add(anchorForPosition(desiredPlacement.left, obstaclePlacement.bottom + spacingPx, childWidth, childHeight, parentWidth, parentHeight, insetPx))
            add(anchorForPosition(desiredPlacement.left, obstaclePlacement.top - childHeight - spacingPx, childWidth, childHeight, parentWidth, parentHeight, insetPx))
            add(anchorForPosition(obstaclePlacement.right + spacingPx, desiredPlacement.top, childWidth, childHeight, parentWidth, parentHeight, insetPx))
            add(anchorForPosition(obstaclePlacement.left - childWidth - spacingPx, desiredPlacement.top, childWidth, childHeight, parentWidth, parentHeight, insetPx))
            addAll(fallbackAnchors)
        }

        return candidates
            .map(::clamp)
            .distinct()
            .sortedBy { distanceSquared(it, normalizedDesired) }
            .firstOrNull { !overlaps(obstaclePlacement, placementFor(it, childWidth, childHeight)) }
            ?: fallbackAnchors.maxByOrNull { distanceSquared(it, normalizedDesired) }
            ?: normalizedDesired
    }

    private fun distanceSquared(a: OverlayAnchor, b: OverlayAnchor): Float =
        (a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y)
}
