package com.sconcept.mirrordash.clock

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sconcept.mirrordash.ui.theme.MDTheme
import com.sconcept.mirrordash.weather.FbgoWeatherIcons
import com.sconcept.mirrordash.weather.WEATHER_ICON_STYLE_ANIMATED
import com.sconcept.mirrordash.weather.WEATHER_ICON_STYLE_FBGO
import com.sconcept.mirrordash.weather.WeatherDayForecast
import com.sconcept.mirrordash.weather.WeatherHourForecast
import com.sconcept.mirrordash.weather.WeatherUiState
import com.sconcept.mirrordash.weather.weatherConditionLabel
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun WeatherWidgetSurface(widget: WeatherWidget, weather: WeatherUiState, textColor: Color, iconStyle: String) {
    if (widget.mode == WEATHER_WIDGET_MODE_FORECAST_CARD) {
        ForecastWeatherCard(widget = widget, weather = weather, textColor = textColor, iconStyle = iconStyle)
    } else {
        WeatherLineWidget(widget = widget, weather = weather, textColor = textColor, iconStyle = iconStyle)
    }
}

@Composable
private fun WeatherLineWidget(widget: WeatherWidget, weather: WeatherUiState, textColor: Color, iconStyle: String) {
    val scale = widget.scalePercent / 100f
    Row(verticalAlignment = Alignment.CenterVertically) {
        AnimatedWeatherIcon(
            weatherCode = weather.snapshot?.weatherCode ?: 0,
            isDay = weather.snapshot?.isDay ?: true,
            size = (32f * scale).dp,
            iconStyle = iconStyle,
        )
        Spacer(Modifier.width((10f * scale).dp))
        Text(
            text = weather.temperature?.let { "$it°" } ?: "--°",
            style = MDTheme.type.weather.copy(fontSize = (28f * scale).sp),
            color = textColor.copy(alpha = if (weather.isStale) 0.62f else 1f),
        )
        if (widget.showLocation && !weather.locationLabel.isNullOrBlank()) {
            Spacer(Modifier.width((10f * scale).dp))
            Text(
                text = weather.locationLabel,
                style = MDTheme.type.settingSubtitle.copy(fontSize = (14f * scale).sp),
                color = textColor.copy(alpha = 0.72f),
            )
        }
    }
}

@Composable
private fun ForecastWeatherCard(widget: WeatherWidget, weather: WeatherUiState, textColor: Color, iconStyle: String) {
    val snapshot = weather.snapshot
    val scale = widget.scalePercent / 100f
    val headerTitle = remember(snapshot?.weatherCode, snapshot?.isDay, weather.condition) {
        snapshot?.let { weatherConditionLabel(it.weatherCode, it.isDay) }
            ?: weather.condition?.name?.lowercase()?.replaceFirstChar { it.titlecase() }
            ?: "Weather"
    }
    val currentTemperature = snapshot?.currentTemperature?.roundToInt() ?: weather.temperature
    val horizontalPadding = (20f * scale).dp
    val verticalPadding = (18f * scale).dp
    val itemSpacing = (12f * scale).dp
    val cornerRadius = (22f * scale).dp

    Box(
        modifier = Modifier
            .widthIn(min = (220f * scale).dp, max = (560f * scale).dp)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color.Black.copy(alpha = 0.34f))
            .border(1.dp, textColor.copy(alpha = 0.1f), RoundedCornerShape(cornerRadius))
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = headerTitle,
                        style = MDTheme.type.settingTitle.copy(
                            fontSize = (30f * scale).sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = textColor,
                    )
                    if (widget.showLocation) {
                        val locationLabel = snapshot?.location?.label ?: weather.locationLabel
                        if (!locationLabel.isNullOrBlank()) {
                            Spacer(Modifier.height((4f * scale).dp))
                            Text(
                                text = locationLabel,
                                style = MDTheme.type.settingSubtitle.copy(fontSize = (15f * scale).sp),
                                color = textColor.copy(alpha = 0.76f),
                            )
                        }
                    }
                    Spacer(Modifier.height((6f * scale).dp))
                    Text(
                        text = when {
                            snapshot == null && weather.isLoading -> "Loading forecast"
                            snapshot == null && weather.isConfigured -> "Forecast unavailable"
                            !weather.isConfigured -> "Set a weather location"
                            weather.isStale -> "Last update unavailable"
                            else -> "Live forecast"
                        },
                        style = MDTheme.type.caption.copy(fontSize = (12f * scale).sp),
                        color = textColor.copy(alpha = 0.6f),
                    )
                }

                Text(
                    text = currentTemperature?.let { "$it°" } ?: "--°",
                    style = MDTheme.type.clock.copy(
                        fontSize = (54f * scale).sp,
                        fontWeight = FontWeight.Light,
                    ),
                    color = textColor,
                    textAlign = TextAlign.End,
                )
            }

            Spacer(Modifier.height((18f * scale).dp))

            when {
                snapshot == null -> PlaceholderForecastSummary(
                    widget = widget,
                    scale = scale,
                    textColor = textColor,
                    iconStyle = iconStyle,
                )
                widget.forecastMode == WEATHER_WIDGET_FORECAST_DAILY -> DailyForecastGrid(
                    items = snapshot.forecast.take(widget.itemCount.coerceAtLeast(1)),
                    scale = scale,
                    textColor = textColor,
                    iconStyle = iconStyle,
                )
                else -> HourlyForecastGrid(
                    items = snapshot.hourlyForecast.take(widget.itemCount.coerceAtLeast(1)),
                    scale = scale,
                    textColor = textColor,
                    iconStyle = iconStyle,
                )
            }

            Spacer(Modifier.height(itemSpacing))

            Text(
                text = if (widget.forecastMode == WEATHER_WIDGET_FORECAST_DAILY) "Next days" else "Next hours",
                style = MDTheme.type.caption.copy(fontSize = (12f * scale).sp),
                color = textColor.copy(alpha = 0.56f),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaceholderForecastSummary(widget: WeatherWidget, scale: Float, textColor: Color, iconStyle: String) {
    val items = widget.itemCount.coerceAtLeast(1).coerceAtMost(if (widget.forecastMode == WEATHER_WIDGET_FORECAST_DAILY) 5 else 6)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy((10f * scale).dp),
        verticalArrangement = Arrangement.spacedBy((10f * scale).dp),
        maxItemsInEachRow = min(3, items),
    ) {
        repeat(items) { index ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy((6f * scale).dp),
                modifier = Modifier
                    .defaultMinSize(minWidth = (72f * scale).dp)
                    .clip(RoundedCornerShape((16f * scale).dp))
                    .background(Color.White.copy(alpha = 0.03f))
                    .padding(horizontal = (10f * scale).dp, vertical = (10f * scale).dp),
            ) {
                Text(
                    text = if (widget.forecastMode == WEATHER_WIDGET_FORECAST_DAILY) "Day ${index + 1}" else "Hour ${index + 1}",
                    style = MDTheme.type.settingSubtitle.copy(fontSize = (13f * scale).sp),
                    color = textColor.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
                AnimatedWeatherIcon(weatherCode = 3, isDay = true, size = (26f * scale).dp, iconStyle = iconStyle)
                Text(
                    text = "--",
                    style = MDTheme.type.caption.copy(fontSize = (12f * scale).sp),
                    color = textColor.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HourlyForecastGrid(items: List<WeatherHourForecast>, scale: Float, textColor: Color, iconStyle: String) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy((10f * scale).dp),
        verticalArrangement = Arrangement.spacedBy((10f * scale).dp),
        maxItemsInEachRow = min(4, items.size.coerceAtLeast(1)),
    ) {
        items.forEach { item ->
            ForecastChip(
                topLabel = "${item.temperature.roundToInt()}°",
                bottomLabel = item.timeLabel,
                weatherCode = item.weatherCode,
                isDay = item.isDay,
                scale = scale,
                textColor = textColor,
                iconStyle = iconStyle,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DailyForecastGrid(items: List<WeatherDayForecast>, scale: Float, textColor: Color, iconStyle: String) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy((10f * scale).dp),
        verticalArrangement = Arrangement.spacedBy((10f * scale).dp),
        maxItemsInEachRow = min(4, items.size.coerceAtLeast(1)),
    ) {
        items.forEach { item ->
            ForecastChip(
                topLabel = item.dayLabel,
                bottomLabel = "${item.maxTemperature.roundToInt()}° / ${item.minTemperature.roundToInt()}°",
                weatherCode = item.weatherCode,
                isDay = true,
                scale = scale,
                textColor = textColor,
                iconStyle = iconStyle,
            )
        }
    }
}

@Composable
private fun ForecastChip(
    topLabel: String,
    bottomLabel: String,
    weatherCode: Int,
    isDay: Boolean,
    scale: Float,
    textColor: Color,
    iconStyle: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy((6f * scale).dp),
        modifier = Modifier
            .defaultMinSize(minWidth = (72f * scale).dp)
            .clip(RoundedCornerShape((16f * scale).dp))
            .background(Color.White.copy(alpha = 0.035f))
            .padding(horizontal = (10f * scale).dp, vertical = (10f * scale).dp),
    ) {
        Text(
            text = topLabel,
            style = MDTheme.type.settingSubtitle.copy(fontSize = (14f * scale).sp),
            color = textColor,
            textAlign = TextAlign.Center,
        )
        AnimatedWeatherIcon(
            weatherCode = weatherCode,
            isDay = isDay,
            size = (28f * scale).dp,
            iconStyle = iconStyle,
        )
        Text(
            text = bottomLabel,
            style = MDTheme.type.caption.copy(fontSize = (12f * scale).sp),
            color = textColor.copy(alpha = 0.74f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun AnimatedWeatherIcon(weatherCode: Int, isDay: Boolean, size: Dp, iconStyle: String = WEATHER_ICON_STYLE_ANIMATED) {
    if (iconStyle == WEATHER_ICON_STYLE_FBGO) {
        val drawableRes = remember(weatherCode, isDay) { FbgoWeatherIcons.drawableRes(weatherCode, isDay) }
        Image(
            painter = painterResource(drawableRes),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size),
        )
        return
    }

    val transition = rememberInfiniteTransition(label = "weatherIcon")
    val drift by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 4200, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 3600, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val rainOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1500, easing = LinearEasing), RepeatMode.Restart),
        label = "rainOffset",
    )
    val snowOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 3000, easing = LinearEasing), RepeatMode.Restart),
        label = "snowOffset",
    )
    val flash by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing), RepeatMode.Reverse),
        label = "flash",
    )

    val spec = remember(weatherCode, isDay) { weatherVisualSpec(weatherCode, isDay) }

    Canvas(modifier = Modifier.size(size)) {
        val canvasWidth = this.size.width
        val canvasHeight = this.size.height
        val sunColor = Color(0xFFF4C86A)
        val moonColor = Color(0xFFD9E7FF)
        val cloudColor = Color(0xFFEAF0F7)
        val rainColor = Color(0xFF7FB6D9)
        val snowColor = Color(0xFFF6FAFF)
        val fogColor = Color(0xCCDDE6F4)
        val stormColor = Color(0xFFF3C86B)

        if (spec.kind == WeatherVisualKind.SUN || spec.kind == WeatherVisualKind.PARTLY_CLOUDY) {
            val sunCenter = Offset(canvasWidth * 0.34f + drift * canvasWidth * 0.015f, canvasHeight * 0.34f)
            val sunRadius = canvasWidth * 0.15f * pulse
            drawCircle(sunColor, radius = sunRadius, center = sunCenter)
            repeat(8) { index ->
                val angle = Math.toRadians((index * 45.0) + drift * 4.0)
                val start = Offset(
                    sunCenter.x + kotlin.math.cos(angle).toFloat() * sunRadius * 1.45f,
                    sunCenter.y + kotlin.math.sin(angle).toFloat() * sunRadius * 1.45f,
                )
                val end = Offset(
                    sunCenter.x + kotlin.math.cos(angle).toFloat() * sunRadius * 2.05f,
                    sunCenter.y + kotlin.math.sin(angle).toFloat() * sunRadius * 2.05f,
                )
                drawLine(
                    color = sunColor.copy(alpha = 0.55f),
                    start = start,
                    end = end,
                    strokeWidth = canvasWidth * 0.03f,
                    cap = StrokeCap.Round,
                )
            }
        }

        if (spec.kind == WeatherVisualKind.MOON || spec.kind == WeatherVisualKind.PARTLY_CLOUDY_NIGHT) {
            val moonRadius = canvasWidth * 0.19f
            val moonCenter = Offset(canvasWidth * 0.38f + drift * canvasWidth * 0.012f, canvasHeight * 0.36f)
            val outer = Path().apply {
                addOval(Rect(moonCenter.x - moonRadius, moonCenter.y - moonRadius, moonCenter.x + moonRadius, moonCenter.y + moonRadius))
            }
            val inner = Path().apply {
                addOval(
                    Rect(
                        moonCenter.x - moonRadius * 0.52f,
                        moonCenter.y - moonRadius * 1.02f,
                        moonCenter.x + moonRadius * 0.94f,
                        moonCenter.y + moonRadius * 0.86f,
                    ),
                )
            }
            drawPath(Path.combine(PathOperation.Difference, outer, inner), color = moonColor.copy(alpha = 0.95f))
        }

        if (spec.kind == WeatherVisualKind.FOG) {
            repeat(3) { index ->
                val y = canvasHeight * (0.36f + index * 0.18f)
                drawLine(
                    color = fogColor.copy(alpha = 0.45f + index * 0.08f),
                    start = Offset(canvasWidth * 0.16f + drift * canvasWidth * 0.02f, y),
                    end = Offset(canvasWidth * 0.86f + drift * canvasWidth * 0.02f, y),
                    strokeWidth = canvasWidth * 0.06f,
                    cap = StrokeCap.Round,
                )
            }
            return@Canvas
        }

        if (spec.kind.needsCloud) {
            val cloudShift = drift * canvasWidth * 0.03f
            drawCircle(cloudColor.copy(alpha = 0.95f), radius = canvasWidth * 0.14f, center = Offset(canvasWidth * 0.38f + cloudShift, canvasHeight * 0.56f))
            drawCircle(cloudColor.copy(alpha = 0.95f), radius = canvasWidth * 0.17f, center = Offset(canvasWidth * 0.56f + cloudShift, canvasHeight * 0.5f))
            drawCircle(cloudColor.copy(alpha = 0.95f), radius = canvasWidth * 0.13f, center = Offset(canvasWidth * 0.72f + cloudShift, canvasHeight * 0.58f))
            drawRoundRect(
                color = cloudColor.copy(alpha = 0.95f),
                topLeft = Offset(canvasWidth * 0.26f + cloudShift, canvasHeight * 0.56f),
                size = androidx.compose.ui.geometry.Size(canvasWidth * 0.52f, canvasHeight * 0.16f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(canvasWidth * 0.08f, canvasWidth * 0.08f),
            )
        }

        if (spec.kind == WeatherVisualKind.RAIN || spec.kind == WeatherVisualKind.HEAVY_RAIN) {
            val dropCount = if (spec.kind == WeatherVisualKind.HEAVY_RAIN) 5 else 3
            repeat(dropCount) { index ->
                val x = canvasWidth * (0.34f + index * 0.12f)
                val phase = (rainOffset + index * 0.18f) % 1f
                val startY = canvasHeight * (0.68f + phase * 0.14f)
                drawLine(
                    color = rainColor.copy(alpha = 0.7f),
                    start = Offset(x, startY),
                    end = Offset(x - canvasWidth * 0.035f, startY + canvasHeight * 0.12f),
                    strokeWidth = canvasWidth * 0.032f,
                    cap = StrokeCap.Round,
                )
            }
        }

        if (spec.kind == WeatherVisualKind.SNOW || spec.kind == WeatherVisualKind.HEAVY_SNOW) {
            val flakeCount = if (spec.kind == WeatherVisualKind.HEAVY_SNOW) 5 else 3
            repeat(flakeCount) { index ->
                val x = canvasWidth * (0.32f + index * 0.13f) + drift * canvasWidth * 0.015f
                val phase = (snowOffset + index * 0.19f) % 1f
                val y = canvasHeight * (0.7f + phase * 0.14f)
                val radius = canvasWidth * 0.022f
                drawCircle(snowColor.copy(alpha = 0.85f), radius = radius, center = Offset(x, y))
                drawLine(
                    color = snowColor.copy(alpha = 0.75f),
                    start = Offset(x - radius * 1.5f, y),
                    end = Offset(x + radius * 1.5f, y),
                    strokeWidth = canvasWidth * 0.012f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = snowColor.copy(alpha = 0.75f),
                    start = Offset(x, y - radius * 1.5f),
                    end = Offset(x, y + radius * 1.5f),
                    strokeWidth = canvasWidth * 0.012f,
                    cap = StrokeCap.Round,
                )
            }
        }

        if (spec.kind == WeatherVisualKind.THUNDER) {
            val bolt = Path().apply {
                moveTo(canvasWidth * 0.54f, canvasHeight * 0.62f)
                lineTo(canvasWidth * 0.46f, canvasHeight * 0.86f)
                lineTo(canvasWidth * 0.58f, canvasHeight * 0.86f)
                lineTo(canvasWidth * 0.5f, canvasHeight * 1.02f)
            }
            drawPath(
                path = bolt,
                color = stormColor.copy(alpha = flash),
                style = Stroke(width = canvasWidth * 0.06f, cap = StrokeCap.Round),
                blendMode = BlendMode.SrcOver,
            )
        }
    }
}

private data class WeatherVisualSpec(val kind: WeatherVisualKind)

private enum class WeatherVisualKind(val needsCloud: Boolean) {
    SUN(false),
    MOON(false),
    PARTLY_CLOUDY(true),
    PARTLY_CLOUDY_NIGHT(true),
    CLOUD(true),
    FOG(false),
    RAIN(true),
    HEAVY_RAIN(true),
    SNOW(true),
    HEAVY_SNOW(true),
    THUNDER(true),
}

private fun weatherVisualSpec(weatherCode: Int, isDay: Boolean): WeatherVisualSpec {
    val kind = when (weatherCode) {
        0 -> if (isDay) WeatherVisualKind.SUN else WeatherVisualKind.MOON
        1, 2 -> if (isDay) WeatherVisualKind.PARTLY_CLOUDY else WeatherVisualKind.PARTLY_CLOUDY_NIGHT
        3 -> WeatherVisualKind.CLOUD
        45, 48 -> WeatherVisualKind.FOG
        65, 66, 67, 82 -> WeatherVisualKind.HEAVY_RAIN
        61, 63, 80, 81, 51, 53, 55, 56, 57 -> WeatherVisualKind.RAIN
        75, 77, 86 -> WeatherVisualKind.HEAVY_SNOW
        71, 73, 85 -> WeatherVisualKind.SNOW
        95, 96, 99 -> WeatherVisualKind.THUNDER
        else -> WeatherVisualKind.CLOUD
    }
    return WeatherVisualSpec(kind)
}
