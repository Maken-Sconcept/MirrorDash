package com.sconcept.mirrordash.launcher.gestures

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.compose.runtime.State
import kotlin.math.abs

/**
 * A WebView (Jellyfin, Browser) locks every touch to itself via
 * `parent.requestDisallowInterceptTouchEvent(true)` so its own vertical scrolling/carousels
 * aren't stolen mid-drag by an ancestor - but that also means, once awake, a deliberate
 * left/right swipe meant for [LauncherGestureHost]'s page pager never reaches it either ("hard
 * to swipe from tab" while inside an app). This classifies the gesture the instant it clears
 * touch slop - same "decide once, never fight after" approach as the edge-zone arbiter in
 * [LauncherGestureHost] - and only relinquishes the lock when it's a clearly horizontal drag AND
 * [isAwake] is true; a tap, a vertical scroll, or anything while still Ambient behaves exactly as
 * before. Unlike the swipe-intent filter removed from LauncherGestureHost itself, this never
 * touches Compose's own `PointerInputChange.consume()` - it only flips the classic Android
 * View-system interception flag, which HorizontalPager's interop boundary already honors.
 */
internal fun swipePriorityTouchListener(context: Context, isAwake: State<Boolean>): View.OnTouchListener {
    val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    var downX = 0f
    var downY = 0f
    var classified = false
    return View.OnTouchListener { view, motionEvent ->
        when (motionEvent.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = motionEvent.x
                downY = motionEvent.y
                classified = false
                view.parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!classified) {
                    val dx = motionEvent.x - downX
                    val dy = motionEvent.y - downY
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        classified = true
                        val isPageSwipe = isAwake.value && abs(dx) > abs(dy)
                        view.parent?.requestDisallowInterceptTouchEvent(!isPageSwipe)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
        }
        false
    }
}
