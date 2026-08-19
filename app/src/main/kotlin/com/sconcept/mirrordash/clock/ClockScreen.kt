package com.sconcept.mirrordash.clock

import android.net.Uri
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import com.sconcept.mirrordash.airplay.AirPlayUiState
import com.sconcept.mirrordash.airplay.AirPlayMirrorSurface
import com.sconcept.mirrordash.calendar.CalendarAgendaUiState
import com.sconcept.mirrordash.nas.model.SmbConnectionState
import com.sconcept.mirrordash.news.NewsUiState
import com.sconcept.mirrordash.photorama.PhotoramaUiState
import com.sconcept.mirrordash.photorama.PhotoramaMedia
import com.sconcept.mirrordash.nas.model.PhotoramaMediaType
import java.io.File
import com.sconcept.mirrordash.stocks.StocksUiState
import com.sconcept.mirrordash.ui.theme.MDTheme
import com.sconcept.mirrordash.ui.theme.MirrorDashTypography
import com.sconcept.mirrordash.ui.theme.mirrorDashTypography
import com.sconcept.mirrordash.weather.WeatherCondition
import com.sconcept.mirrordash.weather.WeatherUiState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

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
    photoramaState: PhotoramaUiState = PhotoramaUiState(),
    airPlayStatus: AirPlayUiState? = null,
    onTextWidgetAnchorChange: (id: String, anchor: OverlayAnchor) -> Unit = { _, _ -> },
    calendar: CalendarAgendaUiState = CalendarAgendaUiState(),
    onCalendarWidgetAnchorChange: (id: String, anchor: OverlayAnchor) -> Unit = { _, _ -> },
    onTasksWidgetAnchorChange: (id: String, anchor: OverlayAnchor) -> Unit = { _, _ -> },
    onTaskItemToggle: (widgetId: String, itemId: String, completed: Boolean) -> Unit = { _, _, _ -> },
    stocks: StocksUiState = StocksUiState(),
    onStocksWidgetAnchorChange: (id: String, anchor: OverlayAnchor) -> Unit = { _, _ -> },
    news: NewsUiState = NewsUiState(),
    onNewsWidgetAnchorChange: (id: String, anchor: OverlayAnchor) -> Unit = { _, _ -> },
    contentDimAlpha: Float = 0f,
    onClockRemove: () -> Unit = {},
    onWeatherWidgetRemove: (id: String) -> Unit = {},
    onTextWidgetRemove: (id: String) -> Unit = {},
    onCalendarWidgetRemove: (id: String) -> Unit = {},
    onTasksWidgetRemove: (id: String) -> Unit = {},
    onStocksWidgetRemove: (id: String) -> Unit = {},
    onNewsWidgetRemove: (id: String) -> Unit = {},
    onClockRotationChange: (degrees: Float) -> Unit = {},
    onWeatherWidgetRotationChange: (id: String, degrees: Float) -> Unit = { _, _ -> },
    onTextWidgetRotationChange: (id: String, degrees: Float) -> Unit = { _, _ -> },
    onCalendarWidgetRotationChange: (id: String, degrees: Float) -> Unit = { _, _ -> },
    onTasksWidgetRotationChange: (id: String, degrees: Float) -> Unit = { _, _ -> },
    onStocksWidgetRotationChange: (id: String, degrees: Float) -> Unit = { _, _ -> },
    onNewsWidgetRotationChange: (id: String, degrees: Float) -> Unit = { _, _ -> },
) {
    val typography = remember(appearance.fontSizeSp) { mirrorDashTypography(appearance.fontSizeSp) }
    val clockData by rememberClockRenderData(weather = weather, showWeather = appearance.showWidgets)
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
                photoramaState = photoramaState,
                airPlayStatus = airPlayStatus,
                onTextWidgetAnchorChange = onTextWidgetAnchorChange,
                calendar = calendar,
                onCalendarWidgetAnchorChange = onCalendarWidgetAnchorChange,
                onTasksWidgetAnchorChange = onTasksWidgetAnchorChange,
                onTaskItemToggle = onTaskItemToggle,
                stocks = stocks,
                onStocksWidgetAnchorChange = onStocksWidgetAnchorChange,
                news = news,
                onNewsWidgetAnchorChange = onNewsWidgetAnchorChange,
                contentDimAlpha = contentDimAlpha,
                typography = typography,
                clockData = clockData,
                insetPx = insetPx,
                onClockRemove = onClockRemove,
                onWeatherWidgetRemove = onWeatherWidgetRemove,
                onTextWidgetRemove = onTextWidgetRemove,
                onCalendarWidgetRemove = onCalendarWidgetRemove,
                onTasksWidgetRemove = onTasksWidgetRemove,
                onStocksWidgetRemove = onStocksWidgetRemove,
                onNewsWidgetRemove = onNewsWidgetRemove,
                onClockRotationChange = onClockRotationChange,
                onWeatherWidgetRotationChange = onWeatherWidgetRotationChange,
                onTextWidgetRotationChange = onTextWidgetRotationChange,
                onCalendarWidgetRotationChange = onCalendarWidgetRotationChange,
                onTasksWidgetRotationChange = onTasksWidgetRotationChange,
                onStocksWidgetRotationChange = onStocksWidgetRotationChange,
                onNewsWidgetRotationChange = onNewsWidgetRotationChange,
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
    photoramaState: PhotoramaUiState,
    airPlayStatus: AirPlayUiState?,
    onTextWidgetAnchorChange: (id: String, anchor: OverlayAnchor) -> Unit,
    calendar: CalendarAgendaUiState,
    onCalendarWidgetAnchorChange: (id: String, anchor: OverlayAnchor) -> Unit,
    onTasksWidgetAnchorChange: (id: String, anchor: OverlayAnchor) -> Unit,
    onTaskItemToggle: (widgetId: String, itemId: String, completed: Boolean) -> Unit,
    stocks: StocksUiState,
    onStocksWidgetAnchorChange: (id: String, anchor: OverlayAnchor) -> Unit,
    news: NewsUiState,
    onNewsWidgetAnchorChange: (id: String, anchor: OverlayAnchor) -> Unit,
    contentDimAlpha: Float,
    typography: MirrorDashTypography,
    clockData: ClockRenderData,
    insetPx: Float,
    onClockRemove: () -> Unit,
    onWeatherWidgetRemove: (id: String) -> Unit,
    onTextWidgetRemove: (id: String) -> Unit,
    onCalendarWidgetRemove: (id: String) -> Unit,
    onTasksWidgetRemove: (id: String) -> Unit,
    onStocksWidgetRemove: (id: String) -> Unit,
    onNewsWidgetRemove: (id: String) -> Unit,
    onClockRotationChange: (degrees: Float) -> Unit,
    onWeatherWidgetRotationChange: (id: String, degrees: Float) -> Unit,
    onTextWidgetRotationChange: (id: String, degrees: Float) -> Unit,
    onCalendarWidgetRotationChange: (id: String, degrees: Float) -> Unit,
    onTasksWidgetRotationChange: (id: String, degrees: Float) -> Unit,
    onStocksWidgetRotationChange: (id: String, degrees: Float) -> Unit,
    onNewsWidgetRotationChange: (id: String, degrees: Float) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        when (appearance.background) {
            is ClockBackground.Photorama -> PhotoramaBackdrop(photoramaState)
            is ClockBackground.SolidColor -> Box(
                Modifier.fillMaxSize().background(appearance.background.color),
            )
            is ClockBackground.Photo -> Box(Modifier.fillMaxSize().background(Color.Black))
        }

        val parentWidthPx = constraints.maxWidth
        val parentHeightPx = constraints.maxHeight

        if (appearance.showTime) {
            DraggableAnchor(
                anchor = appearance.clockAnchor,
                parentWidthPx = parentWidthPx,
                parentHeightPx = parentHeightPx,
                insetPx = insetPx,
                onAnchorChange = onClockAnchorChange,
                onRemove = onClockRemove,
                rotationDegrees = appearance.clockRotationDegrees,
                onRotationChange = onClockRotationChange,
            ) {
                Column(Modifier.alpha(1f - contentDimAlpha)) {
                    ClockTextBlock(
                        appearance = appearance,
                        typography = typography,
                        clockData = clockData,
                    )
                }
            }
        }

        if (appearance.showWidgets) {
            WidgetOverlayLayer(
                widgets = appearance.weatherWidgets,
                parentWidthPx = parentWidthPx,
                parentHeightPx = parentHeightPx,
                insetPx = insetPx,
                contentDimAlpha = contentDimAlpha,
                onAnchorChange = onWeatherWidgetAnchorChange,
                onRemove = onWeatherWidgetRemove,
                onRotationChange = onWeatherWidgetRotationChange,
            ) { widget -> WeatherWidgetSurface(widget = widget, weather = weather, textColor = appearance.textColor) }

            WidgetOverlayLayer(
                widgets = appearance.textWidgets,
                parentWidthPx = parentWidthPx,
                parentHeightPx = parentHeightPx,
                insetPx = insetPx,
                contentDimAlpha = contentDimAlpha,
                onAnchorChange = onTextWidgetAnchorChange,
                onRemove = onTextWidgetRemove,
                onRotationChange = onTextWidgetRotationChange,
            ) { widget -> AnimatedWidgetText(widget) }

            WidgetOverlayLayer(
                widgets = appearance.calendarWidgets,
                parentWidthPx = parentWidthPx,
                parentHeightPx = parentHeightPx,
                insetPx = insetPx,
                contentDimAlpha = contentDimAlpha,
                onAnchorChange = onCalendarWidgetAnchorChange,
                onRemove = onCalendarWidgetRemove,
                onRotationChange = onCalendarWidgetRotationChange,
            ) { widget ->
                CalendarAgendaWidgetSurface(
                    widget = widget,
                    calendar = calendar,
                    fontFamily = rememberClockFontFamily(widget.fontId, appearance.downloadedClockFonts),
                )
            }

            WidgetOverlayLayer(
                widgets = appearance.tasksWidgets,
                parentWidthPx = parentWidthPx,
                parentHeightPx = parentHeightPx,
                insetPx = insetPx,
                contentDimAlpha = contentDimAlpha,
                onAnchorChange = onTasksWidgetAnchorChange,
                onRemove = onTasksWidgetRemove,
                onRotationChange = onTasksWidgetRotationChange,
            ) { widget ->
                TasksWidgetSurface(
                    widget = widget,
                    onToggleItem = { itemId, completed -> onTaskItemToggle(widget.id, itemId, completed) },
                    fontFamily = rememberClockFontFamily(widget.fontId, appearance.downloadedClockFonts),
                )
            }

            WidgetOverlayLayer(
                widgets = appearance.stocksWidgets,
                parentWidthPx = parentWidthPx,
                parentHeightPx = parentHeightPx,
                insetPx = insetPx,
                contentDimAlpha = contentDimAlpha,
                onAnchorChange = onStocksWidgetAnchorChange,
                onRemove = onStocksWidgetRemove,
                onRotationChange = onStocksWidgetRotationChange,
            ) { widget ->
                StocksTickerWidgetSurface(
                    widget = widget,
                    stocks = stocks,
                    fontFamily = rememberClockFontFamily(widget.fontId, appearance.downloadedClockFonts),
                )
            }

            WidgetOverlayLayer(
                widgets = appearance.newsWidgets,
                parentWidthPx = parentWidthPx,
                parentHeightPx = parentHeightPx,
                insetPx = insetPx,
                contentDimAlpha = contentDimAlpha,
                onAnchorChange = onNewsWidgetAnchorChange,
                onRemove = onNewsWidgetRemove,
                onRotationChange = onNewsWidgetRotationChange,
            ) { widget ->
                NewsTickerWidgetSurface(
                    widget = widget,
                    news = news,
                    fontFamily = rememberClockFontFamily(widget.fontId, appearance.downloadedClockFonts),
                )
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

@Composable
private fun ClockTextBlock(
    appearance: ClockAppearance,
    typography: MirrorDashTypography,
    clockData: ClockRenderData,
) {
    ClockStyleScene(
        style = ClockStyleCatalog.preset(appearance.styleId),
        data = clockData,
        baseSizeSp = appearance.fontSizeSp.toFloat(),
        fontFamily = rememberClockFontFamily(appearance.fontId, appearance.downloadedClockFonts),
        primaryColor = appearance.textColor,
        accentColor = appearance.accentColor,
        lineSizeSp = typography.date.fontSize.value,
    )
}

@Composable
internal fun ClockStyleScene(
    style: ClockStyleDefinition,
    data: ClockRenderData,
    baseSizeSp: Float,
    fontFamily: FontFamily,
    primaryColor: Color,
    accentColor: Color,
    lineSizeSp: Float,
    modifier: Modifier = Modifier,
) {
    val unit = (baseSizeSp / 12f).dp
    when (style.id) {
        CLOCK_STYLE_CLASSIC -> {
            Column(modifier = modifier) {
                Text(
                    text = data.timeText,
                    color = primaryColor,
                    fontFamily = fontFamily,
                    fontSize = baseSizeSp.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-0.03).em,
                )
                Spacer(Modifier.height(unit * 0.4f))
                ClockInfoLine(data = data, fontFamily = fontFamily, textColor = accentColor, sizeSp = lineSizeSp)
            }
        }

        CLOCK_STYLE_BIG_SMALL -> {
            Column(modifier = modifier) {
                Box {
                    Text(
                        text = data.hour,
                        color = primaryColor,
                        fontFamily = fontFamily,
                        fontSize = (baseSizeSp * 1.06f).sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = (-0.035).em,
                    )
                    Text(
                        text = data.minute,
                        color = accentColor,
                        fontFamily = fontFamily,
                        fontSize = (baseSizeSp * 0.48f).sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.02).em,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = unit * 0.2f, bottom = unit * 0.9f),
                    )
                }
                Spacer(Modifier.height(unit * 0.25f))
                ClockInfoLine(data = data, fontFamily = fontFamily, textColor = primaryColor.copy(alpha = 0.88f), sizeSp = lineSizeSp)
            }
        }

        CLOCK_STYLE_OVERLAP -> {
            Column(modifier = modifier) {
                Box {
                    Text(
                        text = data.hourPadded,
                        color = primaryColor,
                        fontFamily = fontFamily,
                        fontSize = (baseSizeSp * 0.84f).sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.035).em,
                    )
                    Text(
                        text = data.minutePadded,
                        color = accentColor.copy(alpha = 0.95f),
                        fontFamily = fontFamily,
                        fontSize = (baseSizeSp * 0.84f).sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.035).em,
                        modifier = Modifier.padding(start = unit * 1.8f, top = unit * 0.7f),
                    )
                }
                Spacer(Modifier.height(unit * 0.25f))
                ClockInfoLine(data = data, fontFamily = fontFamily, textColor = accentColor, sizeSp = lineSizeSp)
            }
        }

        CLOCK_STYLE_VERTICAL -> {
            Column(modifier = modifier) {
                Row(horizontalArrangement = Arrangement.spacedBy(unit * 0.2f)) {
                    ClockDigit(data.hourPadded[0].toString(), fontFamily, primaryColor, baseSizeSp * 0.58f)
                    ClockDigit(data.hourPadded[1].toString(), fontFamily, accentColor, baseSizeSp * 0.58f)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(unit * 0.2f)) {
                    ClockDigit(data.minutePadded[0].toString(), fontFamily, accentColor, baseSizeSp * 0.58f)
                    ClockDigit(data.minutePadded[1].toString(), fontFamily, primaryColor, baseSizeSp * 0.58f)
                }
                Spacer(Modifier.height(unit * 0.35f))
                ClockInfoLine(data = data, fontFamily = fontFamily, textColor = primaryColor.copy(alpha = 0.9f), sizeSp = lineSizeSp)
            }
        }

        CLOCK_STYLE_MINIMALIST -> {
            Box(modifier = modifier) {
                Text(
                    text = data.timeText,
                    color = accentColor.copy(alpha = 0.14f),
                    fontFamily = fontFamily,
                    fontSize = (baseSizeSp * 0.96f).sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.035).em,
                )
                Column(modifier = Modifier.padding(start = unit * 1.2f, top = unit * 1.1f)) {
                    Text(
                        text = data.timeText,
                        color = primaryColor,
                        fontFamily = fontFamily,
                        fontSize = (baseSizeSp * 0.4f).sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-0.02).em,
                    )
                    Spacer(Modifier.height(unit * 0.2f))
                    ClockInfoLine(data = data, fontFamily = fontFamily, textColor = accentColor, sizeSp = lineSizeSp)
                }
            }
        }

        CLOCK_STYLE_FLIP -> {
            Column(modifier = modifier) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FlipTile(text = data.hourPadded, fontFamily = fontFamily, textColor = primaryColor, accentColor = accentColor, baseSizeSp = baseSizeSp)
                    Spacer(Modifier.width(unit * 0.55f))
                    Text(
                        text = ":",
                        color = accentColor,
                        fontFamily = fontFamily,
                        fontSize = (baseSizeSp * 0.38f).sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = unit * 0.2f),
                    )
                    Spacer(Modifier.width(unit * 0.55f))
                    FlipTile(text = data.minutePadded, fontFamily = fontFamily, textColor = primaryColor, accentColor = accentColor, baseSizeSp = baseSizeSp)
                }
                Spacer(Modifier.height(unit * 0.35f))
                ClockInfoLine(data = data, fontFamily = fontFamily, textColor = accentColor, sizeSp = lineSizeSp)
            }
        }
    }
}

@Composable
private fun ClockDigit(text: String, fontFamily: FontFamily, color: Color, sizeSp: Float) {
    Text(
        text = text,
        color = color,
        fontFamily = fontFamily,
        fontSize = sizeSp.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.03).em,
    )
}

@Composable
private fun FlipTile(
    text: String,
    fontFamily: FontFamily,
    textColor: Color,
    accentColor: Color,
    baseSizeSp: Float,
) {
    val tilePadding = (baseSizeSp / 18f).dp
    val shape = RoundedCornerShape((baseSizeSp / 10f).dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.34f))
            .border(1.dp, accentColor.copy(alpha = 0.2f), shape)
            .padding(horizontal = tilePadding * 1.2f, vertical = tilePadding),
    ) {
        Text(
            text = text,
            color = textColor,
            fontFamily = fontFamily,
            fontSize = (baseSizeSp * 0.38f).sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.02).em,
        )
    }
}

@Composable
private fun ClockInfoLine(
    data: ClockRenderData,
    fontFamily: FontFamily,
    textColor: Color,
    sizeSp: Float,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (data.weatherGlyph != null) {
            Text(
                text = data.weatherGlyph,
                color = textColor,
                fontSize = (sizeSp * 1.08f).sp,
                modifier = Modifier.padding(end = 6.dp),
            )
        }
        Text(
            text = data.infoLine,
            color = textColor,
            fontFamily = fontFamily,
            fontSize = sizeSp.sp,
            fontWeight = FontWeight.Medium,
        )
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
 * where the anchor happens to be or what the current photo looks like. Folds in the state
 * messaging the old standalone Photorama tab used to show (not configured / empty folder /
 * connecting-with-reason) - now that Photorama only ever exists as this backdrop, a broken or
 * still-connecting source needs to say so here rather than just sitting on plain black. */
@Composable
private fun PhotoramaBackdrop(state: PhotoramaUiState) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val media = state.currentMedia
        if (media != null) {
            PhotoramaMediaBackdrop(media)
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f)))

        if (media == null) {
            val (title, subtitle) = photoramaStatusMessage(state)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(title, style = MDTheme.type.settingTitle, color = Color.White)
                    if (subtitle != null) {
                        Spacer(Modifier.padding(top = 6.dp))
                        Text(
                            subtitle,
                            style = MDTheme.type.settingSubtitle,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 64.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoramaMediaBackdrop(media: PhotoramaMedia) {
    when (media.type) {
        PhotoramaMediaType.IMAGE -> Crossfade(
            targetState = media.source,
            animationSpec = tween(900),
            label = "clockPhotoramaImageBackground",
        ) { source ->
            Image(
                painter = rememberAsyncImagePainter(model = source),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        PhotoramaMediaType.VIDEO -> PhotoramaVideoBackdrop(media.source)
    }
}

@Composable
private fun PhotoramaVideoBackdrop(source: Any) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uri = remember(source) { if (source is File) source.toUri() else source as Uri }
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { PlayerView(it).apply { useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM; this.player = player } },
        update = { it.player = player },
        modifier = Modifier.fillMaxSize(),
    )
}

private fun photoramaStatusMessage(state: PhotoramaUiState): Pair<String, String?> = when {
    !state.isConfigured -> "Photorama isn't set up yet" to "Turn it on in Clock settings and pick where your photos live."
    state.hasNoPhotos -> "No photos found" to "The selected folder doesn't contain any supported images yet."
    else -> when (state.connectionState) {
        SmbConnectionState.CONNECTING -> "Connecting…" to null
        SmbConnectionState.RECONNECTING -> "Reconnecting…" to null
        SmbConnectionState.AUTHENTICATION_FAILED -> "Incorrect username or password" to "Check the NAS connection in Clock settings."
        SmbConnectionState.SERVER_UNREACHABLE -> "Can't reach the NAS right now" to null
        SmbConnectionState.SHARE_UNAVAILABLE -> "Couldn't access the selected folder" to "Check the source in Clock settings."
        SmbConnectionState.OFFLINE -> "The device is offline" to null
        SmbConnectionState.DISCONNECTED -> "Waiting to connect…" to null
        SmbConnectionState.CONNECTED -> "Loading photos…" to null
    }
}

// How long the trash icon (and its outline) lingers after a drag ends, so there's a real window
// to tap it as a separate, ordinary tap - a drag's own pointer is already consumed start-to-finish
// by detectDragGesturesAfterLongPress, so the icon can never be "dropped onto" in one continuous
// touch. Restarted from either onDragStart or onDragEnd, whichever happens most recently.
private const val REMOVE_AFFORDANCE_TIMEOUT_MS = 3000L

/** A long-press-then-drag positioned element, backed by a single [OverlayAnchor]. Shows a
 * quiet outline only while actively being dragged (or, if [onRemove] is given, for a few seconds
 * after too - see [REMOVE_AFFORDANCE_TIMEOUT_MS]) - otherwise indistinguishable from a normal,
 * fixed piece of UI, so dragging never feels like a discoverability puzzle nor a constant visual
 * distraction. Internal (not private) so [NightClockScreen] can reuse the exact same drag
 * mechanics instead of re-deriving them - [onRemove] defaults to null there, so its clock/weather
 * elements are unaffected and keep their existing drag-only behavior. */
@Composable
internal fun DraggableAnchor(
    anchor: OverlayAnchor,
    parentWidthPx: Int,
    parentHeightPx: Int,
    insetPx: Float,
    onAnchorChange: (OverlayAnchor) -> Unit,
    onRemove: (() -> Unit)? = null,
    rotationDegrees: Float = 0f,
    onRotationChange: ((Float) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var showRemoveAffordance by remember { mutableStateOf(false) }
    var affordanceToken by remember { mutableStateOf(0) }
    var isRotating by remember { mutableStateOf(false) }

    if (showRemoveAffordance) {
        LaunchedEffect(affordanceToken) {
            delay(REMOVE_AFFORDANCE_TIMEOUT_MS)
            showRemoveAffordance = false
        }
    }

    val basePosition = ClockLayoutHelper.placementPx(anchor, sizePx.width, sizePx.height, parentWidthPx, parentHeightPx, insetPx)
    val left = basePosition.first + (if (isDragging) dragOffset.x else 0f)
    val top = basePosition.second + (if (isDragging) dragOffset.y else 0f)
    // Rotating counts as "still interacting" on its own, independent of the trash icon's timeout -
    // a slow freeform rotate shouldn't have the handle vanish out from under the user's finger.
    val showOutline = isDragging || showRemoveAffordance || isRotating

    Box(
        modifier = Modifier
            .offset { IntOffset(left.toInt(), top.toInt()) }
            .onSizeChanged { sizePx = it }
            .then(
                if (showOutline) {
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
                        if (onRemove != null) {
                            showRemoveAffordance = true
                            affordanceToken++
                        }
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
                        if (onRemove != null) affordanceToken++
                    },
                    onDragCancel = {
                        isDragging = false
                        dragOffset = Offset.Zero
                    },
                )
            },
    ) {
        Box(Modifier.graphicsLayer(rotationZ = rotationDegrees)) {
            content()
        }

        if (onRemove != null && showRemoveAffordance) {
            RemoveWidgetButton(
                onClick = {
                    showRemoveAffordance = false
                    onRemove()
                },
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }

        if (onRotationChange != null && showOutline) {
            RotateWidgetHandle(
                rotationDegrees = rotationDegrees,
                onRotationChange = onRotationChange,
                onRotationGestureActive = { active ->
                    isRotating = active
                    // Finishing a rotate re-arms the same few-second window the trash icon uses,
                    // so it's still reachable right after rotating without a second long-press.
                    if (!active && onRemove != null) {
                        showRemoveAffordance = true
                        affordanceToken++
                    }
                },
                // Above the card's top edge, not over its middle - centered over the content
                // would sit right where a finger needs to see/grab the widget itself, and every
                // drag would happen with the hand covering exactly what's being rotated.
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = -(ROTATE_HANDLE_TOUCH_SIZE + ROTATE_HANDLE_GAP)),
            )
        }
    }
}

private val ROTATE_HANDLE_TOUCH_SIZE = 44.dp
private val ROTATE_HANDLE_VISIBLE_SIZE = 30.dp
private val ROTATE_HANDLE_GAP = 8.dp

@Composable
private fun RemoveWidgetButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .offset(x = 8.dp, y = (-8).dp)
            .size(28.dp)
            .clip(CircleShape)
            .background(MDTheme.colors.danger)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = "Remove widget",
            tint = Color.White,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** Floats above the widget it rotates (see the caller's offset in [DraggableAnchor]) rather than
 * sitting over its middle - centered on the content would put a dragging finger right over the
 * thing being rotated, hiding exactly what the user needs to watch while adjusting it. The current
 * angle sits in its own badge immediately beside the icon at all times (not just mid-drag), so
 * there's always a reference to steer by. A plain tap snaps 90° at a time; dragging tracks the
 * pointer's angle around the handle's own center for freeform rotation. Built directly on
 * [awaitFirstDown]/[awaitTouchSlopOrCancellation]/[drag] - the primitives [detectDragGestures]
 * itself wraps - rather than that helper, because [detectDragGestures] never invokes onDragStart
 * *or* onDragEnd for a press released before crossing touch slop, which a plain tap always is; the
 * "was this just a tap" branch could never be reached from inside those callbacks. The touch
 * target ([ROTATE_HANDLE_TOUCH_SIZE]) is kept larger than the visible circle
 * ([ROTATE_HANDLE_VISIBLE_SIZE]) so the handle stays easy to land a finger on. Rotation itself is
 * applied to the widget's *content* by the caller, not here - this only ever reports an angle. */
@Composable
private fun RotateWidgetHandle(
    rotationDegrees: Float,
    onRotationChange: (Float) -> Unit,
    onRotationGestureActive: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDragging by remember { mutableStateOf(false) }
    var liveRotation by remember { mutableStateOf(rotationDegrees) }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(ROTATE_HANDLE_TOUCH_SIZE)
                .pointerInput(Unit) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val slopChange = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                        if (slopChange == null) {
                            // Lifted before crossing touch slop - treat as a tap.
                            onRotationChange(normalizeRotationDegrees(rotationDegrees + 90f))
                        } else {
                            isDragging = true
                            onRotationGestureActive(true)
                            liveRotation = rotationDegrees
                            var lastAngleDeg = angleDegrees(slopChange.position, center)
                            drag(slopChange.id) { change ->
                                change.consume()
                                val currentAngleDeg = angleDegrees(change.position, center)
                                var step = currentAngleDeg - lastAngleDeg
                                if (step > 180f) step -= 360f
                                if (step < -180f) step += 360f
                                liveRotation = normalizeRotationDegrees(liveRotation + step)
                                lastAngleDeg = currentAngleDeg
                                onRotationChange(liveRotation)
                            }
                            isDragging = false
                            onRotationGestureActive(false)
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(ROTATE_HANDLE_VISIBLE_SIZE)
                    .clip(CircleShape)
                    .background(MDTheme.colors.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.RotateRight,
                    contentDescription = "Rotate widget",
                    tint = MDTheme.colors.onAccent,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Spacer(Modifier.width(6.dp))

        Text(
            text = "${(if (isDragging) liveRotation else rotationDegrees).roundToInt()}°",
            style = MDTheme.type.caption,
            color = Color.White,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}

private fun angleDegrees(point: Offset, center: Offset): Float {
    val radians = kotlin.math.atan2((point.y - center.y).toDouble(), (point.x - center.x).toDouble())
    return Math.toDegrees(radians).toFloat()
}

/** One [DraggableAnchor] per widget in [widgets], sharing the same dim-on-content-scrim and
 * anchor-change plumbing - the generic form of what used to be a hand-written `forEach` block per
 * widget type (weather, text, and now calendar/tasks/stocks/news all go through this). */
@Composable
internal fun <T : AnchoredWidget> WidgetOverlayLayer(
    widgets: List<T>,
    parentWidthPx: Int,
    parentHeightPx: Int,
    insetPx: Float,
    contentDimAlpha: Float,
    onAnchorChange: (id: String, anchor: OverlayAnchor) -> Unit,
    onRemove: (id: String) -> Unit,
    onRotationChange: (id: String, degrees: Float) -> Unit = { _, _ -> },
    content: @Composable (T) -> Unit,
) {
    widgets.forEach { widget ->
        DraggableAnchor(
            anchor = OverlayAnchor(widget.anchorX, widget.anchorY),
            parentWidthPx = parentWidthPx,
            parentHeightPx = parentHeightPx,
            insetPx = insetPx,
            onAnchorChange = { onAnchorChange(widget.id, it) },
            onRemove = { onRemove(widget.id) },
            rotationDegrees = widget.rotationDegrees,
            onRotationChange = { onRotationChange(widget.id, it) },
        ) {
            Box(Modifier.alpha(1f - contentDimAlpha)) {
                content(widget)
            }
        }
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
private fun rememberClockRenderData(weather: WeatherUiState, showWeather: Boolean) =
    produceState(initialValue = buildClockRenderData(weather, showWeather), weather.temperature, weather.condition, weather.isConfigured, showWeather) {
        while (true) {
            value = buildClockRenderData(weather, showWeather)
            val now = Calendar.getInstance()
            val msToNextMinute = 60_000L - (now.get(Calendar.SECOND) * 1000L + now.get(Calendar.MILLISECOND))
            kotlinx.coroutines.delay(msToNextMinute.coerceAtLeast(1000L))
        }
    }

private fun buildClockRenderData(weather: WeatherUiState, showWeather: Boolean): ClockRenderData {
    val now = Calendar.getInstance()
    val hour = SimpleDateFormat("H", Locale.getDefault()).format(now.time)
    val minute = SimpleDateFormat("mm", Locale.getDefault()).format(now.time)
    val hourPadded = SimpleDateFormat("HH", Locale.getDefault()).format(now.time)
    val day = SimpleDateFormat("EEE", Locale.getDefault()).format(now.time).uppercase(Locale.getDefault())
    val date = SimpleDateFormat("MMM d", Locale.getDefault()).format(now.time)
    val weatherGlyph = weather.condition?.let(::weatherGlyph)?.takeIf { showWeather && weather.isConfigured }
    val temperature = weather.temperature?.let { "$it°" }?.takeIf { showWeather && weather.isConfigured }
    val infoLine = listOfNotNull(day, date, temperature).joinToString("  ·  ")
    return ClockRenderData(
        hour = hour,
        minute = minute,
        hourPadded = hourPadded,
        minutePadded = minute,
        timeText = "$hour:$minute",
        infoLine = infoLine,
        weatherGlyph = weatherGlyph,
    )
}

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
