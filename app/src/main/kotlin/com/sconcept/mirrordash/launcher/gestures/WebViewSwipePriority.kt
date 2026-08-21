package com.sconcept.mirrordash.launcher.gestures

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private val PAGE_SWIPE_THRESHOLD = 56.dp

// How much the distance between the two fingers is allowed to drift and still count as a
// parallel two-finger swipe (two-finger mode only) - real swipes keep roughly the same finger
// spacing throughout, while pinch-to-zoom is defined by that spacing changing.
private val PINCH_SPREAD_TOLERANCE = 24.dp

/**
 * A WebView (Jellyfin, Browser) locks every touch to itself via
 * `parent.requestDisallowInterceptTouchEvent(true)` so its own vertical scrolling/carousels
 * aren't stolen mid-drag - but that flag is a classic Android View-system mechanism, and
 * [LauncherGestureHost]'s HorizontalPager is pure Compose, a separate gesture architecture the
 * flag has no leverage over. Toggling it conditionally (an earlier attempt at this) measurably
 * did nothing.
 *
 * Rather than fight the WebView for ownership of its touch stream - which Compose has no clean
 * way to win, and which the removed swipe-intent filter proved is actively dangerous to attempt
 * mid-gesture (Compose's own detectors, including the pager's, permanently abandon a gesture the
 * first time they see it consumed, even if consumption later stops) - this watches the SAME raw
 * pointer stream in parallel, purely passively (`PointerEventPass.Initial`, never consuming),
 * exactly the technique [LauncherTabBar]'s own swipe detector already uses to coexist with its
 * label chips underneath.
 *
 * Two selectable modes (see [requireTwoFingers], driven by Settings > Launcher > "Two-finger
 * swipe" - the original single-finger behavior is kept exactly as it was, not replaced, so
 * switching the setting back off returns to it byte-for-byte):
 * - **Single finger** ([awaitSingleFingerSwipe]): one finger, fires the moment the running drag
 *   first crosses the threshold as clearly horizontal - not on release - because some WebView
 *   content (Jellyfin's own edge-swipe drawer, in particular) reacts to the SAME raw drag with
 *   its own JS gesture recognizer, racing us for it. Once that JS calls `preventDefault()` on its
 *   own touchmove, Chromium can stop forwarding further MotionEvents through the normal Android
 *   dispatch path this observer relies on - so the fix has to win the race by committing earlier,
 *   not just by watching harder.
 * - **Two fingers** ([awaitTwoFingerSwipe]): requires exactly two fingers moving together - a
 *   single finger never triggers this mode at all (left entirely to the page's own content:
 *   scrolling, tapping, whatever it normally does), and a third finger joining bails out too. The
 *   two fingers' spacing is tracked from the moment the second one lands; if it drifts past
 *   [PINCH_SPREAD_TOLERANCE] this is treated as a pinch-to-zoom and never fires, no matter how far
 *   the fingers travel. Only once both fingers are moving the same horizontal direction, spacing
 *   held roughly constant, does the *average* of their two displacements get compared against
 *   [PAGE_SWIPE_THRESHOLD].
 */
internal fun Modifier.pageSwipePriority(
    enabled: Boolean,
    requireTwoFingers: Boolean,
    onSwipe: (forward: Boolean) -> Unit,
): Modifier = composed {
    if (!enabled) return@composed this
    val onSwipeState = rememberUpdatedState(onSwipe)
    pointerInput(requireTwoFingers) {
        val thresholdPx = PAGE_SWIPE_THRESHOLD.toPx()
        val pinchTolerancePx = PINCH_SPREAD_TOLERANCE.toPx()
        awaitEachGesture {
            if (requireTwoFingers) {
                awaitTwoFingerSwipe(thresholdPx, pinchTolerancePx) { onSwipeState.value(it) }
            } else {
                awaitSingleFingerSwipe(thresholdPx) { onSwipeState.value(it) }
            }
        }
    }
}

private suspend fun AwaitPointerEventScope.awaitSingleFingerSwipe(thresholdPx: Float, onSwipe: (forward: Boolean) -> Unit) {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    var totalDx = 0f
    var totalDy = 0f
    var fired = false
    while (true) {
        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == down.id } ?: break
        if (change.changedToUpIgnoreConsumed()) break
        totalDx += change.position.x - change.previousPosition.x
        totalDy += change.position.y - change.previousPosition.y
        if (!fired && abs(totalDx) >= thresholdPx && abs(totalDx) > abs(totalDy)) {
            fired = true
            onSwipe(totalDx < 0f)
        }
    }
}

private suspend fun AwaitPointerEventScope.awaitTwoFingerSwipe(
    thresholdPx: Float,
    pinchTolerancePx: Float,
    onSwipe: (forward: Boolean) -> Unit,
) {
    var firstId: PointerId? = null
    var firstStart = Offset.Zero
    var secondId: PointerId? = null
    var secondStart = Offset.Zero
    var initialSpread = 0f
    var disqualified = false
    var fired = false

    while (true) {
        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
        val changes = event.changes

        val firstLifted = firstId != null && changes.any { it.id == firstId && it.changedToUpIgnoreConsumed() }
        val secondLifted = secondId != null && changes.any { it.id == secondId && it.changedToUpIgnoreConsumed() }
        if (firstLifted || secondLifted) break

        when {
            firstId == null -> {
                val down = changes.firstOrNull { it.changedToDownIgnoreConsumed() }
                if (down != null) {
                    firstId = down.id
                    firstStart = down.position
                }
            }
            secondId == null -> {
                val down = changes.firstOrNull { it.id != firstId && it.pressed }
                if (down != null) {
                    secondId = down.id
                    secondStart = down.position
                    val firstNow = changes.firstOrNull { it.id == firstId }?.position ?: firstStart
                    initialSpread = (firstNow - secondStart).getDistance()
                }
            }
            disqualified || fired -> Unit
            changes.count { it.pressed } > 2 -> disqualified = true
            else -> {
                val firstChange = changes.firstOrNull { it.id == firstId }
                val secondChange = changes.firstOrNull { it.id == secondId }
                if (firstChange != null && secondChange != null) {
                    val currentSpread = (firstChange.position - secondChange.position).getDistance()
                    if (abs(currentSpread - initialSpread) > pinchTolerancePx) {
                        disqualified = true
                    } else {
                        val dx1 = firstChange.position.x - firstStart.x
                        val dx2 = secondChange.position.x - secondStart.x
                        val dy1 = firstChange.position.y - firstStart.y
                        val dy2 = secondChange.position.y - secondStart.y
                        val sameDirection = (dx1 >= 0f) == (dx2 >= 0f)
                        val avgDx = (dx1 + dx2) / 2f
                        val avgDy = (dy1 + dy2) / 2f
                        if (sameDirection && abs(avgDx) >= thresholdPx && abs(avgDx) > abs(avgDy)) {
                            fired = true
                            onSwipe(avgDx < 0f)
                        }
                    }
                }
            }
        }
    }
}
