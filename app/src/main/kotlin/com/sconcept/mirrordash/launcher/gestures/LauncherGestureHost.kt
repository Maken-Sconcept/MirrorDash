package com.sconcept.mirrordash.launcher.gestures

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sconcept.mirrordash.brightness.BRIGHTNESS_FAILSAFE_HOLD_MS
import com.sconcept.mirrordash.brightness.BRIGHTNESS_FAILSAFE_WARNING_LEAD_MS
import com.sconcept.mirrordash.brightness.BrightnessFailsafe
import com.sconcept.mirrordash.ui.theme.MDTheme
import com.sconcept.mirrordash.ui.theme.MirrorDashMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class EdgeZone { NONE, TOP, BOTTOM }

private val EDGE_ZONE_HEIGHT = 48.dp
private val OPEN_DRAG_THRESHOLD_FRACTION = 0.32f

/**
 * The one place all of the launcher's touch gestures are arbitrated (brief section 29):
 * horizontal page navigation, bottom-up App Drawer, top-down Notifications, and tap-to-enter
 * Travel mode. Rather than several competing `pointerInput` modifiers guessing about each
 * other, a single overlay uses `PointerEventPass.Initial` to classify the gesture from its
 * start position *before* the pager's own scrollable ever sees it, then either gets out of the
 * way entirely (letting the pager handle ordinary horizontal drags) or takes explicit ownership
 * of the pointer for a vertical sheet drag.
 */
@Composable
fun LauncherGestureHost(
    pageCount: Int,
    initialPage: Int,
    pageLabels: List<String>,
    onPageSettled: (Int) -> Unit,
    pageContent: @Composable (pageIndex: Int, interactionState: LauncherInteractionState) -> Unit,
    appDrawerContent: @Composable (progress: Float, close: () -> Unit) -> Unit,
    notificationsContent: @Composable (progress: Float, close: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    onFailsafeHoldTriggered: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = initialPage) { pageCount }

    var interactionState by remember { mutableStateOf<LauncherInteractionState>(LauncherInteractionState.Ambient) }
    var lastInteractionAtMs by remember { mutableLongStateOf(0L) }

    // 0f = fully closed/off-screen, 1f = fully open. `xxxAnim` is the settled/animating value;
    // `xxxDragFraction` is non-null only while the finger owns the sheet, and always wins over
    // the Animatable while present. Two separate holders (rather than one Animatable driven via
    // snapTo mid-gesture) because AwaitPointerEventScope is a restricted suspend scope that
    // cannot call arbitrary suspend functions like Animatable.snapTo - see the gesture loop below.
    val drawerAnim = remember { Animatable(0f) }
    val notificationsAnim = remember { Animatable(0f) }
    var drawerDragFraction by remember { mutableStateOf<Float?>(null) }
    var notificationsDragFraction by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(pagerState.settledPage) {
        onPageSettled(pagerState.settledPage)
    }

    // Travel mode auto-collapses back to Ambient after a short idle period - but never while a
    // sheet is open/opening, and never while the pager itself is still settling a fling.
    LaunchedEffect(interactionState, pagerState.isScrollInProgress) {
        if (interactionState == LauncherInteractionState.Travel) {
            while (true) {
                kotlinx.coroutines.delay(200)
                val idleFor = System.currentTimeMillis() - lastInteractionAtMs
                if (idleFor >= MirrorDashMotion.TRAVEL_IDLE_TIMEOUT_MS &&
                    !pagerState.isScrollInProgress &&
                    !LauncherIdleTracker.hasFocusedField
                ) {
                    interactionState = LauncherInteractionState.Ambient
                    break
                }
            }
        }
    }

    val isTravel = interactionState == LauncherInteractionState.Travel ||
        interactionState == LauncherInteractionState.OpeningAppDrawer ||
        interactionState == LauncherInteractionState.OpeningNotifications

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MDTheme.colors.background)
            // A second, independent pointerInput block (rather than folding this into the
            // arbiter below) so the brightness failsafe is completely decoupled from
            // edge-zone/travel-mode logic - it tracks every touch anywhere on screen, never
            // consumes, and so never competes with the pager's drag or a DraggableAnchor's own
            // long-press-then-drag on individual clock/weather/text widgets. Cancels on release
            // or on real movement past touch slop, so a normal drag never accidentally holds
            // long enough to fire.
            .pointerInput(Unit) {
                val touchSlopPx = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val failsafeJob = scope.launch {
                        delay(BRIGHTNESS_FAILSAFE_WARNING_LEAD_MS)
                        BrightnessFailsafe.warn(context)
                        delay(BRIGHTNESS_FAILSAFE_HOLD_MS - BRIGHTNESS_FAILSAFE_WARNING_LEAD_MS)
                        onFailsafeHoldTriggered()
                    }
                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUpIgnoreConsumed()) break
                        val totalDelta = change.position - down.position
                        if (kotlin.math.abs(totalDelta.x) > touchSlopPx || kotlin.math.abs(totalDelta.y) > touchSlopPx) break
                    }
                    failsafeJob.cancel()
                }
            }
            // Attached directly to this ancestor Box (not a sibling overlay drawn on top) so it
            // participates in Initial-pass-before-children / Main-pass-after-children exactly
            // once, unambiguously, ahead of the pager's own drag handling below.
            .pointerInput(pageCount) {
                val edgeZonePx = EDGE_ZONE_HEIGHT.toPx()
                awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        lastInteractionAtMs = System.currentTimeMillis()
                        val screenHeightPx = size.height.toFloat()
                        val startedInDrawerZone = down.position.y >= screenHeightPx - edgeZonePx &&
                            interactionState != LauncherInteractionState.Notifications
                        val startedInNotificationsZone = down.position.y <= edgeZonePx &&
                            interactionState != LauncherInteractionState.AppDrawer
                        val zone = when {
                            startedInDrawerZone -> EdgeZone.BOTTOM
                            startedInNotificationsZone -> EdgeZone.TOP
                            else -> EdgeZone.NONE
                        }

                        if (zone == EdgeZone.NONE) {
                            if (interactionState == LauncherInteractionState.Ambient) {
                                interactionState = LauncherInteractionState.Travel
                            }
                            return@awaitEachGesture
                        }

                        // Being in an edge zone is not enough to claim the gesture outright -
                        // an interactive element (e.g. Settings' back button) can legitimately
                        // sit inside these zones too. Track movement WITHOUT consuming until it
                        // clears touch slop; a simple tap then falls through untouched to
                        // whatever's underneath, exactly like it would if we weren't here at
                        // all. Only a real drag gets claimed, retroactively, from that point on.
                        val touchSlopPx = viewConfiguration.touchSlop
                        var committed = false
                        var previousY = down.position.y
                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            lastInteractionAtMs = System.currentTimeMillis()
                            if (change.changedToUpIgnoreConsumed()) {
                                if (committed) change.consume()
                                break
                            }

                            if (!committed) {
                                val totalDy = change.position.y - down.position.y
                                if (kotlin.math.abs(totalDy) <= touchSlopPx) {
                                    // Still within slop: don't consume, let it reach descendants
                                    // normally (this is what makes a tap-in-the-zone work).
                                    previousY = change.position.y
                                    continue
                                }
                                committed = true
                                if (zone == EdgeZone.BOTTOM) drawerDragFraction = drawerAnim.value
                                if (zone == EdgeZone.TOP) notificationsDragFraction = notificationsAnim.value
                            }

                            val dy = change.position.y - previousY
                            previousY = change.position.y
                            change.consume()

                            when (zone) {
                                EdgeZone.BOTTOM -> {
                                    interactionState = LauncherInteractionState.OpeningAppDrawer
                                    drawerDragFraction = ((drawerDragFraction ?: 0f) - dy / screenHeightPx).coerceIn(0f, 1f)
                                }
                                EdgeZone.TOP -> {
                                    interactionState = LauncherInteractionState.OpeningNotifications
                                    notificationsDragFraction = ((notificationsDragFraction ?: 0f) + dy / screenHeightPx).coerceIn(0f, 1f)
                                }
                                EdgeZone.NONE -> Unit
                            }
                        }

                        if (!committed) return@awaitEachGesture

                        when (zone) {
                            EdgeZone.BOTTOM -> {
                                val shouldOpen = (drawerDragFraction ?: 0f) > OPEN_DRAG_THRESHOLD_FRACTION
                                val startFrom = drawerDragFraction ?: 0f
                                drawerDragFraction = null
                                interactionState = if (shouldOpen) LauncherInteractionState.AppDrawer else LauncherInteractionState.Ambient
                                scope.launch {
                                    drawerAnim.snapTo(startFrom)
                                    drawerAnim.animateTo(if (shouldOpen) 1f else 0f, MirrorDashMotion.settleSpring())
                                }
                            }
                            EdgeZone.TOP -> {
                                val shouldOpen = (notificationsDragFraction ?: 0f) > OPEN_DRAG_THRESHOLD_FRACTION
                                val startFrom = notificationsDragFraction ?: 0f
                                notificationsDragFraction = null
                                interactionState = if (shouldOpen) LauncherInteractionState.Notifications else LauncherInteractionState.Ambient
                                scope.launch {
                                    notificationsAnim.snapTo(startFrom)
                                    notificationsAnim.animateTo(if (shouldOpen) 1f else 0f, MirrorDashMotion.settleSpring())
                                }
                            }
                            EdgeZone.NONE -> Unit
                        }
                    }
                },
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                pagerSnapDistance = if (isTravel) PagerSnapDistance.atMost(pageCount) else PagerSnapDistance.atMost(1),
            ),
        ) { page ->
            TravelFrame(isTravel = isTravel) {
                pageContent(page, interactionState)
            }
        }

        LauncherTabBar(
            visible = isTravel,
            labels = pageLabels,
            pagerState = pagerState,
            scope = scope,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        SheetLayer(
            openFraction = { drawerDragFraction ?: drawerAnim.value },
            fromBottom = true,
        ) { progress ->
            appDrawerContent(progress) {
                scope.launch {
                    drawerAnim.animateTo(0f, MirrorDashMotion.settleSpring())
                    interactionState = LauncherInteractionState.Ambient
                }
            }
        }

        SheetLayer(
            openFraction = { notificationsDragFraction ?: notificationsAnim.value },
            fromBottom = false,
        ) { progress ->
            notificationsContent(progress) {
                scope.launch {
                    notificationsAnim.animateTo(0f, MirrorDashMotion.settleSpring())
                    interactionState = LauncherInteractionState.Ambient
                }
            }
        }
    }
}

@Composable
private fun TravelFrame(isTravel: Boolean, content: @Composable () -> Unit) {
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isTravel) MirrorDashMotion.travelModeScale else 1f,
        animationSpec = MirrorDashMotion.settleSpring(),
        label = "travelScale",
    )
    val corner by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isTravel) MirrorDashMotion.travelModeCornerRadius else 0.dp,
        animationSpec = MirrorDashMotion.settleSpring(),
        label = "travelCorner",
    )
    val outerPadding by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isTravel) MirrorDashMotion.travelModeOuterPadding else 0.dp,
        animationSpec = MirrorDashMotion.settleSpring(),
        label = "travelPadding",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(outerPadding)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shape = androidx.compose.foundation.shape.RoundedCornerShape(corner)
                clip = corner > 0.dp || outerPadding > 0.dp
            },
    ) {
        content()
    }
}

private val TAB_BAR_SWIPE_THRESHOLD = 56.dp

/** Shown only in Travel/"awake" mode, at the very top of the screen - tapping a label jumps
 * straight to that page; sliding directly on the bar itself pages forward/back one at a time,
 * independent of where on the bar the drag started. Its own `pointerInput` (rather than teaching
 * the main arbiter above about a third gesture) works because a purely horizontal drag never
 * accumulates the vertical delta the arbiter's Notifications-zone tracking watches for, so it's
 * never claimed/consumed there - Compose's own slop-based disambiguation does the rest, the same
 * way a row of clickable chips coexists with a scrollable parent. */
@Composable
private fun LauncherTabBar(
    visible: Boolean,
    labels: List<String>,
    pagerState: PagerState,
    scope: kotlinx.coroutines.CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = MirrorDashMotion.settleSpring(),
        label = "tabBarAlpha",
    )
    if (alpha <= 0.01f || labels.size <= 1) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(top = 20.dp)
            .wrapContentWidth()
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(22.dp))
            .background(MDTheme.colors.surface.copy(alpha = 0.78f))
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .pointerInput(labels.size) {
                val threshold = TAB_BAR_SWIPE_THRESHOLD.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var totalDx = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUpIgnoreConsumed()) break
                        totalDx += change.position.x - change.previousPosition.x
                    }
                    when {
                        totalDx <= -threshold -> {
                            val next = (pagerState.currentPage + 1).coerceAtMost(labels.lastIndex)
                            scope.launch { pagerState.animateScrollToPage(next) }
                        }
                        totalDx >= threshold -> {
                            val prev = (pagerState.currentPage - 1).coerceAtLeast(0)
                            scope.launch { pagerState.animateScrollToPage(prev) }
                        }
                    }
                }
            },
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == pagerState.currentPage
            Text(
                text = label,
                style = MDTheme.type.caption,
                color = if (selected) MDTheme.colors.accent else MDTheme.colors.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun SheetLayer(
    openFraction: () -> Float,
    fromBottom: Boolean,
    content: @Composable (progress: Float) -> Unit,
) {
    val progress = openFraction()
    if (progress <= 0f) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = size.height * (1f - progress) * if (fromBottom) 1f else -1f
            },
    ) {
        content(progress)
    }
}
