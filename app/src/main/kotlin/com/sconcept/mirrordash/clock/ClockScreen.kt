package com.sconcept.mirrordash.clock

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.sconcept.mirrordash.airplay.AirPlayUiState
import com.sconcept.mirrordash.airplay.AirPlayMirrorSurface
import com.sconcept.mirrordash.ui.theme.MDTheme
import com.sconcept.mirrordash.ui.theme.MirrorDashTypography
import com.sconcept.mirrordash.ui.theme.mirrorDashTypography
import com.sconcept.mirrordash.weather.WeatherCondition
import com.sconcept.mirrordash.weather.WeatherUiState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// Matches BerthierOptions' PhotoClockView.OVERLAY_SAFE_INSET_DP - just enough margin to keep a
// dragged widget from clipping off-screen, not a "keep away from the corners" restriction.
private val EDGE_INSET = 18.dp

/**
 * The default Home surface (brief section 11). The clock and weather clusters are freely
 * draggable (long-press then drag) - restored at the user's request - each backed by exactly
 * one stored anchor with exactly one default, so there's no possible "position resets" bug the
 * way BerthierOptions had. Only font size, text color, and position are configurable; there is
 * still no separate legacy "default position" concept to drift out of sync.
 */
@Composable
fun ClockScreen(
    appearance: ClockAppearance,
    weather: WeatherUiState,
    showEdgeAffordance: Boolean,
    onClockAnchorChange: (OverlayAnchor) -> Unit,
    onWeatherWidgetAnchorChange: (id: String, anchor: OverlayAnchor) -> Unit,
    modifier: Modifier = Modifier,
    photoramaBackground: File? = null,
    airPlayStatus: AirPlayUiState? = null,
    onTextWidgetAnchorChange: (id: String, anchor: OverlayAnchor) -> Unit = { _, _ -> },
    contentDimAlpha: Float = 0f,
) {
    val typography = remember(appearance.fontSizeSp) { mirrorDashTypography(appearance.fontSizeSp) }
    val timeText by rememberMinuteTicker()
    val insetPx = with(androidx.compose.ui.platform.LocalDensity.current) { EDGE_INSET.toPx() }

    // While AirPlay is actively sending video, the Clock page itself becomes the mirrored
    // screen - unconditional on the (separate) idle-state status-chip setting, since the point
    // is mirroring actually being visible at all, not an opt-in indicator. Falls back to the
    // regular clock automatically once hasActiveVideo goes stale (see AirPlayEngine's ticker).
    val isMirroringTarget = airPlayStatus?.hasActiveVideo == true
    LaunchedEffect(isMirroringTarget) {
        android.util.Log.i("ClockScreen", "hasActiveVideo target -> $isMirroringTarget")
    }
    Crossfade(targetState = isMirroringTarget, label = "clockAirPlayTakeover", modifier = modifier.fillMaxSize()) { isMirroring ->
        if (isMirroring) {
            AirPlayMirrorSurface(modifier = Modifier.fillMaxSize())
        } else {
            ClockContent(
                appearance = appearance,
                weather = weather,
                showEdgeAffordance = showEdgeAffordance,
                onClockAnchorChange = onClockAnchorChange,
                onWeatherWidgetAnchorChange = onWeatherWidgetAnchorChange,
                photoramaBackground = photoramaBackground,
                airPlayStatus = airPlayStatus,
                onTextWidgetAnchorChange = onTextWidgetAnchorChange,
                contentDimAlpha = contentDimAlpha,
                typography = typography,
                timeText = timeText,
                insetPx = insetPx,
            )
        }
    }
}

@Composable
private fun ClockContent(
    appearance: ClockAppearance,
    weather: WeatherUiState,
    showEdgeAffordance: Boolean,
    onClockAnchorChange: (OverlayAnchor) -> Unit,
    onWeatherWidgetAnchorChange: (id: String, anchor: OverlayAnchor) -> Unit,
    photoramaBackground: File?,
    airPlayStatus: AirPlayUiState?,
    onTextWidgetAnchorChange: (id: String, anchor: OverlayAnchor) -> Unit,
    contentDimAlpha: Float,
    typography: MirrorDashTypography,
    timeText: String,
    insetPx: Float,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        when (appearance.background) {
            is ClockBackground.Photorama -> PhotoramaBackdrop(photoramaBackground)
            is ClockBackground.SolidColor -> Box(
                Modifier.fillMaxSize().background(appearance.background.color),
            )
            is ClockBackground.Photo -> Box(Modifier.fillMaxSize().background(Color.Black))
        }

        val parentWidthPx = constraints.maxWidth
        val parentHeightPx = constraints.maxHeight

        DraggableAnchor(
            anchor = appearance.clockAnchor,
            parentWidthPx = parentWidthPx,
            parentHeightPx = parentHeightPx,
            insetPx = insetPx,
            onAnchorChange = onClockAnchorChange,
        ) {
            Column(Modifier.alpha(1f - contentDimAlpha)) {
                Text(
                    text = timeText,
                    style = typography.clock,
                    color = appearance.textColor,
                    fontWeight = FontWeight.Light,
                )
                Spacer(Modifier.padding(top = 4.dp))
                Text(
                    text = todayLabel(),
                    style = typography.date,
                    color = appearance.textColor.copy(alpha = 0.72f),
                )
            }
        }

        appearance.weatherWidgets.forEach { widget ->
            DraggableAnchor(
                anchor = OverlayAnchor(widget.anchorX, widget.anchorY),
                parentWidthPx = parentWidthPx,
                parentHeightPx = parentHeightPx,
                insetPx = insetPx,
                onAnchorChange = { onWeatherWidgetAnchorChange(widget.id, it) },
            ) {
                Box(Modifier.alpha(1f - contentDimAlpha)) {
                    WeatherWidgetSurface(widget = widget, weather = weather, textColor = appearance.textColor)
                }
            }
        }

        appearance.textWidgets.forEach { widget ->
            DraggableAnchor(
                anchor = OverlayAnchor(widget.anchorX, widget.anchorY),
                parentWidthPx = parentWidthPx,
                parentHeightPx = parentHeightPx,
                insetPx = insetPx,
                onAnchorChange = { onTextWidgetAnchorChange(widget.id, it) },
            ) {
                Box(Modifier.alpha(1f - contentDimAlpha)) {
                    AnimatedWidgetText(widget)
                }
            }
        }

        if (showEdgeAffordance) {
            RightEdgeAffordance(color = appearance.textColor)
        }

        if (airPlayStatus != null && airPlayStatus.enabled && airPlayStatus.showClockWidget) {
            AirPlayStatusWidget(
                state = airPlayStatus,
                modifier = Modifier.align(Alignment.TopStart).padding(EDGE_INSET),
            )
        }
    }
}

/** Small, fixed, non-draggable info chip (top-left - the one corner the clock/weather defaults
 * don't already claim) shown only when the user opts in from Settings. Idle/waiting-state status
 * only, no video - once a session actually has active video, [ClockScreen] swaps to
 * [com.sconcept.mirrordash.airplay.AirPlayMirrorSurface] instead of rendering this at all. */
@Composable
private fun AirPlayStatusWidget(state: AirPlayUiState, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.32f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Icon(Icons.Filled.Cast, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(state.deviceName, style = MDTheme.type.settingSubtitle, color = Color.White)
            val detail = if (state.receiverIp.isNotBlank()) "${state.statusLabel} · ${state.receiverIp}" else state.statusLabel
            Text(detail, style = MDTheme.type.caption, color = Color.White.copy(alpha = 0.72f))
        }
    }
}

/** Live Photorama photos as the Clock backdrop instead of a flat color. A fixed dim scrim sits
 * over the photo regardless of which corner the clock/weather end up dragged to, since a photo's
 * own contrast can't be predicted the way a flat color's can - readability shouldn't depend on
 * where the anchor happens to be or what the current photo looks like. */
@Composable
private fun PhotoramaBackdrop(file: File?) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (file != null) {
            Crossfade(targetState = file, animationSpec = tween(900), label = "clockPhotoramaBackground") { current ->
                Image(
                    painter = rememberAsyncImagePainter(model = current),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f)))
    }
}

/** A long-press-then-drag positioned element, backed by a single [OverlayAnchor]. Shows a
 * quiet outline only while actively being dragged - otherwise indistinguishable from a
 * normal, fixed piece of UI, so dragging never feels like a discoverability puzzle nor a
 * constant visual distraction. Internal (not private) so [NightClockScreen] can reuse the exact
 * same drag mechanics instead of re-deriving them. */
@Composable
internal fun DraggableAnchor(
    anchor: OverlayAnchor,
    parentWidthPx: Int,
    parentHeightPx: Int,
    insetPx: Float,
    onAnchorChange: (OverlayAnchor) -> Unit,
    content: @Composable () -> Unit,
) {
    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    val basePosition = ClockLayoutHelper.placementPx(anchor, sizePx.width, sizePx.height, parentWidthPx, parentHeightPx, insetPx)
    val left = basePosition.first + (if (isDragging) dragOffset.x else 0f)
    val top = basePosition.second + (if (isDragging) dragOffset.y else 0f)

    Box(
        modifier = Modifier
            .offset { IntOffset(left.toInt(), top.toInt()) }
            .onSizeChanged { sizePx = it }
            .then(
                if (isDragging) {
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MDTheme.colors.accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(6.dp)
                } else {
                    Modifier.padding(6.dp)
                },
            )
            .pointerInput(anchor, parentWidthPx, parentHeightPx, sizePx) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        isDragging = true
                        dragOffset = Offset.Zero
                    },
                    onDrag = { change, delta ->
                        change.consume()
                        dragOffset += delta
                    },
                    onDragEnd = {
                        isDragging = false
                        val finalLeft = basePosition.first + dragOffset.x
                        val finalTop = basePosition.second + dragOffset.y
                        val newAnchor = ClockLayoutHelper.anchorForPosition(
                            finalLeft, finalTop, sizePx.width, sizePx.height, parentWidthPx, parentHeightPx, insetPx,
                        )
                        dragOffset = Offset.Zero
                        onAnchorChange(newAnchor)
                    },
                    onDragCancel = {
                        isDragging = false
                        dragOffset = Offset.Zero
                    },
                )
            },
    ) {
        content()
    }
}

@Composable
private fun WeatherLine(weather: WeatherUiState, textColor: androidx.compose.ui.graphics.Color) {
    if (!weather.isConfigured) return
    val glyph = weather.condition?.let { weatherGlyph(it) } ?: "…"
    val temp = weather.temperature
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = glyph, fontSize = 26.sp, color = textColor)
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (temp != null) "$temp°" else "--°",
            style = MDTheme.type.weather,
            color = textColor.copy(alpha = if (weather.isStale) 0.6f else 1f),
        )
    }
}

/** Renders one [CustomTextWidget] with whichever looping animation it's set to. Every variant
 * loops indefinitely rather than playing once - this sits on an always-on ambient display, so
 * "once at composition time" would mean "once, ever, until the app restarts." */
@Composable
private fun AnimatedWidgetText(widget: CustomTextWidget) {
    val color = Color(widget.colorArgb)
    val fontFamily = remember(widget.fontFamily) { TextWidgetFonts.family(widget.fontFamily) }
    val baseStyle = androidx.compose.ui.text.TextStyle(fontSize = widget.fontSizeSp.sp, fontFamily = fontFamily)

    when (widget.animation) {
        TEXT_WIDGET_ANIMATION_FADE -> {
            val transition = rememberInfiniteTransition(label = "textWidgetFade")
            val alpha by transition.animateFloat(
                initialValue = 0.15f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
                label = "alpha",
            )
            Text(widget.text, style = baseStyle, color = color, modifier = Modifier.alpha(alpha))
        }

        TEXT_WIDGET_ANIMATION_BLINK -> {
            val transition = rememberInfiniteTransition(label = "textWidgetBlink")
            val alpha by transition.animateFloat(
                initialValue = 1f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    keyframes {
                        durationMillis = 1200
                        1f at 0
                        1f at 700
                        0f at 750
                        0f at 1150
                    },
                ),
                label = "alpha",
            )
            Text(widget.text, style = baseStyle, color = color, modifier = Modifier.alpha(alpha))
        }

        TEXT_WIDGET_ANIMATION_PULSE -> {
            val transition = rememberInfiniteTransition(label = "textWidgetPulse")
            val scale by transition.animateFloat(
                initialValue = 1f,
                targetValue = 1.12f,
                animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                label = "scale",
            )
            Text(
                widget.text,
                style = baseStyle,
                color = color,
                modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
            )
        }

        TEXT_WIDGET_ANIMATION_TYPEWRITER -> {
            val visibleText by rememberTypewriterText(widget.text)
            // Reserves the full text's width/height up front (via a transparent full copy behind
            // the animated one) so the surrounding layout - and this widget's own drag handle -
            // doesn't reflow or jump around as characters are revealed one at a time.
            Box {
                Text(widget.text, style = baseStyle, color = Color.Transparent)
                Text(visibleText, style = baseStyle, color = color)
            }
        }

        TEXT_WIDGET_ANIMATION_HANDWRITE -> HandwriteText(widget.text, baseStyle, color, widget.fontFamily)

        else -> Text(widget.text, style = baseStyle, color = color)
    }
}

/** Traces the real pen-stroke outline of [text], not a per-character reveal like the typewriter
 * effect. [android.graphics.Paint.getTextPath] extracts the actual glyph outline path for the
 * chosen font (a platform Canvas API - Compose's own Path/Paint can't do this), and an animated
 * [android.graphics.DashPathEffect] phase progressively "draws on" that path the way a pen would,
 * looping indefinitely with a hold at fully-written before restarting. */
@Composable
private fun HandwriteText(text: String, textStyle: androidx.compose.ui.text.TextStyle, color: Color, fontFamilyId: String) {
    if (text.isEmpty()) return

    val density = LocalDensity.current
    val typeface = remember(fontFamilyId) { TextWidgetFonts.androidTypeface(fontFamilyId) }
    val fontSizePx = with(density) { textStyle.fontSize.toPx() }
    val colorArgb = color.toArgb()

    val nativePaint = remember(typeface, fontSizePx) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.textSize = fontSizePx
            this.style = android.graphics.Paint.Style.STROKE
            this.strokeWidth = fontSizePx * 0.045f
            this.strokeCap = android.graphics.Paint.Cap.ROUND
            this.strokeJoin = android.graphics.Paint.Join.ROUND
        }
    }
    val path = remember(text, nativePaint) {
        android.graphics.Path().also { nativePaint.getTextPath(text, 0, text.length, 0f, 0f, it) }
    }
    val bounds = remember(path) {
        android.graphics.RectF().also { path.computeBounds(it, true) }
    }
    val pathLength = remember(path) {
        android.graphics.PathMeasure(path, false).length.takeIf { it > 0f } ?: 1f
    }

    val revealMillis = (350 + text.length * 80).coerceIn(600, 3500)
    val holdMillis = 1800
    val totalMillis = revealMillis + holdMillis

    val transition = rememberInfiniteTransition(label = "textWidgetHandwrite")
    val phase by transition.animateFloat(
        initialValue = pathLength,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = totalMillis
                pathLength at 0
                0f at revealMillis
                0f at totalMillis
            },
        ),
        label = "phase",
    )

    val widthDp = with(density) { (bounds.width() + fontSizePx * 0.1f).toDp() }
    val heightDp = with(density) { (bounds.height() + fontSizePx * 0.4f).toDp() }

    Canvas(modifier = Modifier.size(width = widthDp.coerceAtLeast(4.dp), height = heightDp.coerceAtLeast(4.dp))) {
        nativePaint.color = colorArgb
        nativePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(pathLength, pathLength), phase)
        drawContext.canvas.nativeCanvas.apply {
            save()
            translate(-bounds.left + fontSizePx * 0.05f, -bounds.top + fontSizePx * 0.2f)
            drawPath(path, nativePaint)
            restore()
        }
    }
}

@Composable
private fun rememberTypewriterText(fullText: String) = produceState(initialValue = "", fullText) {
    if (fullText.isEmpty()) {
        value = ""
        return@produceState
    }
    while (true) {
        for (i in 0..fullText.length) {
            value = fullText.take(i)
            kotlinx.coroutines.delay(70L)
        }
        kotlinx.coroutines.delay(1800L)
        for (i in fullText.length downTo 0) {
            value = fullText.take(i)
            kotlinx.coroutines.delay(30L)
        }
        kotlinx.coroutines.delay(500L)
    }
}

internal fun weatherGlyph(condition: WeatherCondition): String = when (condition) {
    WeatherCondition.CLEAR -> "☀"
    WeatherCondition.CLOUDY -> "☁"
    WeatherCondition.FOG -> "〰"
    WeatherCondition.RAIN -> "☂"
    WeatherCondition.SNOW -> "❄"
    WeatherCondition.THUNDER -> "⚡"
}

@Composable
private fun rememberMinuteTicker() = produceState(initialValue = formatNow()) {
    while (true) {
        value = formatNow()
        val now = Calendar.getInstance()
        val msToNextMinute = 60_000L - (now.get(Calendar.SECOND) * 1000L + now.get(Calendar.MILLISECOND))
        kotlinx.coroutines.delay(msToNextMinute.coerceAtLeast(1000L))
    }
}

private fun formatNow(): String = SimpleDateFormat("H:mm", Locale.getDefault()).format(java.util.Date())

private fun todayLabel(): String = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(java.util.Date())

@Composable
private fun RightEdgeAffordance(color: androidx.compose.ui.graphics.Color) {
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }
    val transition = rememberInfiniteTransition(label = "edgeAffordance")
    val breathe by transition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.34f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse),
        label = "edgeAffordanceAlpha",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(if (revealed) breathe else 0f),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Box(
            modifier = Modifier
                .padding(end = 10.dp)
                .size(width = 4.dp, height = 56.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
    }
}
