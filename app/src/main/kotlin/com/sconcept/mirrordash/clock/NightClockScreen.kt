package com.sconcept.mirrordash.clock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sconcept.mirrordash.ui.theme.MDTheme
import com.sconcept.mirrordash.weather.WeatherUiState
import com.sconcept.mirrordash.weather.weatherConditionLabel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// Matches ClockScreen's own EDGE_INSET - just enough margin to keep a dragged element from
// clipping off-screen, not a "keep away from the corners" restriction (see that file's note).
private val NIGHT_CLOCK_EDGE_INSET = 18.dp

/**
 * The hidden Night Clock tab (brief follow-up: "just like the Nest Home Hub" ambient mode) -
 * reached by swiping past the Clock page, left by swiping back (see [LauncherGestureHost]'s
 * `nightClockContent`). Deliberately not [ClockScreen]: no text widgets, no photo/color
 * background choice, no AirPlay takeover - always solid black, the one thing this tab is for.
 * The clock and weather one-liner ARE freely draggable though, each backed by their own anchor
 * kept entirely separate from the daytime Clock page's (see `nightClockAnchorX/Y` and
 * `nightClockWeatherAnchorX/Y` in [com.sconcept.mirrordash.settings.SettingsRepository]) so
 * repositioning either one here never touches the daytime layout. `textDimPercent` fades both
 * the digits and the weather line on top of whatever the real backlight is already doing (see
 * [com.sconcept.mirrordash.launcher.MirrorDashActivity]'s brightness collectors switching to the
 * Night Clock settings pair while this tab is active).
 */
@Composable
fun NightClockScreen(
    textDimPercent: Int,
    weather: WeatherUiState,
    clockAnchor: OverlayAnchor,
    weatherAnchor: OverlayAnchor,
    onClockAnchorChange: (OverlayAnchor) -> Unit,
    onWeatherAnchorChange: (OverlayAnchor) -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeText by rememberNightClockTicker()
    val textAlpha = 1f - (textDimPercent.coerceIn(0, 100) / 100f)
    val insetPx = with(LocalDensity.current) { NIGHT_CLOCK_EDGE_INSET.toPx() }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(Color.Black)) {
        val parentWidthPx = constraints.maxWidth
        val parentHeightPx = constraints.maxHeight

        DraggableAnchor(
            anchor = clockAnchor,
            parentWidthPx = parentWidthPx,
            parentHeightPx = parentHeightPx,
            insetPx = insetPx,
            onAnchorChange = onClockAnchorChange,
        ) {
            Text(
                text = timeText,
                style = MDTheme.type.clock,
                color = Color.White,
                fontWeight = FontWeight.Light,
                modifier = Modifier.alpha(textAlpha),
            )
        }

        if (weather.isConfigured) {
            DraggableAnchor(
                anchor = weatherAnchor,
                parentWidthPx = parentWidthPx,
                parentHeightPx = parentHeightPx,
                insetPx = insetPx,
                onAnchorChange = onWeatherAnchorChange,
            ) {
                Box(Modifier.alpha(textAlpha)) {
                    NightWeatherLine(weather)
                }
            }
        }
    }
}

/** Icon, temperature, and a short condition label all on one line - "16° Cloudy tonight" -
 * matching a Nest Hub's ambient-mode weather line rather than [WeatherWidgetSurface]'s bigger,
 * card-style options which would be too bright/busy for this tab's whole reason for existing. */
@Composable
private fun NightWeatherLine(weather: WeatherUiState) {
    val snapshot = weather.snapshot
    Row(verticalAlignment = Alignment.CenterVertically) {
        AnimatedWeatherIcon(
            weatherCode = snapshot?.weatherCode ?: 0,
            isDay = snapshot?.isDay ?: true,
            size = 26.dp,
        )
        Spacer(Modifier.width(8.dp))
        val temp = weather.temperature
        val condition = snapshot?.let { weatherConditionLabel(it.weatherCode, it.isDay) }
        Text(
            text = buildString {
                append(if (temp != null) "$temp°" else "--°")
                if (!condition.isNullOrBlank()) {
                    append(' ')
                    append(condition)
                }
            },
            style = MDTheme.type.settingSubtitle,
            color = Color.White.copy(alpha = if (weather.isStale) 0.6f else 0.85f),
        )
    }
}

@Composable
private fun rememberNightClockTicker() = produceState(initialValue = formatNightClockTime()) {
    while (true) {
        value = formatNightClockTime()
        val now = Calendar.getInstance()
        val msToNextMinute = 60_000L - (now.get(Calendar.SECOND) * 1000L + now.get(Calendar.MILLISECOND))
        kotlinx.coroutines.delay(msToNextMinute.coerceAtLeast(1000L))
    }
}

private fun formatNightClockTime(): String = SimpleDateFormat("H:mm", Locale.getDefault()).format(java.util.Date())
