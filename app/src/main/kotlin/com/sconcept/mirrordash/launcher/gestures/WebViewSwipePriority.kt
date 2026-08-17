package com.sconcept.mirrordash.launcher.gestures

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private val PAGE_SWIPE_THRESHOLD = 40.dp

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
 * Fires the moment the running drag first crosses the threshold as clearly horizontal - not on
 * release - because some WebView content (Jellyfin's own edge-swipe drawer, in particular) reacts
 * to the SAME raw drag with its own JS gesture recognizer, racing us for it. Once that JS calls
 * `preventDefault()` on its own touchmove, Chromium can stop forwarding further MotionEvents
 * through the normal Android dispatch path our Initial-pass observer relies on - so the fix has
 * to win the race by committing earlier, not just by watching harder. Only while awake, so it
 * never interferes with the tap-and-hold wake gesture or ordinary use of the page while ambient.
 */
internal fun Modifier.pageSwipePriority(enabled: Boolean, onSwipe: (forward: Boolean) -> Unit): Modifier = composed {
    if (!enabled) return@composed this
    val onSwipeState = rememberUpdatedState(onSwipe)
    pointerInput(Unit) {
        val thresholdPx = PAGE_SWIPE_THRESHOLD.toPx()
        awaitEachGesture {
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
                    onSwipeState.value(totalDx < 0f)
                }
            }
        }
    }
}
