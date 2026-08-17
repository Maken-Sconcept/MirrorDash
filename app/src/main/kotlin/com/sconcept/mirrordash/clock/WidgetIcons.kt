package com.sconcept.mirrordash.clock

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Small hand-drawn glyph set for the Clock-page data widgets, in the same monoline-Canvas
 * vocabulary [AnimatedWeatherIcon] already established for this app (stroked geometry sized off
 * the canvas, not bitmap art) - so calendar/tasks/stocks/news read as the same family as weather
 * rather than four unrelated templates. */

@Composable
internal fun CalendarGlyphIcon(size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.then(Modifier)) {
        val w = size.toPx()
        val stroke = w * 0.09f
        val bodyTop = w * 0.22f
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.08f, bodyTop),
            size = androidx.compose.ui.geometry.Size(w * 0.84f, w * 0.74f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.1f, w * 0.1f),
            style = Stroke(width = stroke, join = StrokeJoin.Round),
        )
        drawLine(color, Offset(w * 0.08f, w * 0.42f), Offset(w * 0.92f, w * 0.42f), strokeWidth = stroke * 0.85f, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.28f, w * 0.06f), Offset(w * 0.28f, bodyTop + w * 0.06f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.72f, w * 0.06f), Offset(w * 0.72f, bodyTop + w * 0.06f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawCircle(color, radius = w * 0.065f, center = Offset(w * 0.5f, w * 0.68f))
    }
}

@Composable
internal fun ChecklistGlyphIcon(size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.toPx()
        val stroke = w * 0.1f
        val boxSize = w * 0.3f
        listOf(0.12f, 0.44f, 0.76f).forEachIndexed { index, top ->
            drawRoundRect(
                color = color.copy(alpha = if (index == 0) 1f else 0.55f),
                topLeft = Offset(w * 0.04f, w * top),
                size = androidx.compose.ui.geometry.Size(boxSize, boxSize),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.05f, w * 0.05f),
                style = Stroke(width = stroke * 0.7f),
            )
            val lineY = w * top + boxSize / 2f
            drawLine(
                color = color.copy(alpha = if (index == 0) 1f else 0.55f),
                start = Offset(w * 0.46f, lineY),
                end = Offset(w * 0.96f, lineY),
                strokeWidth = stroke * 0.7f,
                cap = StrokeCap.Round,
            )
        }
        // Checkmark inside the first (completed) box.
        val check = Path().apply {
            moveTo(w * 0.09f, w * 0.27f)
            lineTo(w * 0.16f, w * 0.34f)
            lineTo(w * 0.29f, w * 0.16f)
        }
        drawPath(check, color = color, style = Stroke(width = stroke * 0.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
internal fun TrendGlyphIcon(size: Dp, color: Color, positive: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.toPx()
        val stroke = w * 0.14f
        val path = Path()
        if (positive) {
            path.moveTo(w * 0.08f, w * 0.82f)
            path.lineTo(w * 0.38f, w * 0.5f)
            path.lineTo(w * 0.58f, w * 0.68f)
            path.lineTo(w * 0.92f, w * 0.2f)
        } else {
            path.moveTo(w * 0.08f, w * 0.2f)
            path.lineTo(w * 0.38f, w * 0.52f)
            path.lineTo(w * 0.58f, w * 0.34f)
            path.lineTo(w * 0.92f, w * 0.82f)
        }
        drawPath(path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        val arrowTip = if (positive) Offset(w * 0.92f, w * 0.2f) else Offset(w * 0.92f, w * 0.82f)
        val arrowDir = if (positive) 1f else -1f
        val head = Path().apply {
            moveTo(arrowTip.x, arrowTip.y)
            lineTo(arrowTip.x - w * 0.22f, arrowTip.y + w * 0.06f * arrowDir)
            moveTo(arrowTip.x, arrowTip.y)
            lineTo(arrowTip.x - w * 0.06f, arrowTip.y - w * 0.22f * arrowDir)
        }
        drawPath(head, color = color, style = Stroke(width = stroke * 0.85f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** A quiet broadcast-wave glyph for the News widget's header - two arcs radiating from a dot,
 * reused (not re-invented) as the "this is a live feed" motif instead of a plain text label. */
@Composable
internal fun TickerGlyphIcon(size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.toPx()
        val stroke = w * 0.11f
        drawCircle(color, radius = w * 0.1f, center = Offset(w * 0.22f, w * 0.5f))
        val arcStroke = Stroke(width = stroke, cap = StrokeCap.Round)
        drawArc(
            color = color.copy(alpha = 0.85f),
            startAngle = -55f,
            sweepAngle = 110f,
            useCenter = false,
            topLeft = Offset(w * 0.16f, w * 0.14f),
            size = androidx.compose.ui.geometry.Size(w * 0.56f, w * 0.72f),
            style = arcStroke,
        )
        drawArc(
            color = color.copy(alpha = 0.55f),
            startAngle = -55f,
            sweepAngle = 110f,
            useCenter = false,
            topLeft = Offset(w * 0.0f, w * -0.06f),
            size = androidx.compose.ui.geometry.Size(w * 0.9f, w * 1.12f),
            style = arcStroke,
        )
    }
}

/** Small breathing dot used as a corner "this data is live" indicator - the same
 * infinite-transition breathing technique [RightEdgeAffordance] already uses on the Clock page,
 * reused here rather than a new motion idea. */
@Composable
internal fun LivePulseDot(color: Color, size: Dp = 7.dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "livePulseDot")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    Canvas(modifier = modifier) {
        val w = size.toPx()
        drawCircle(color.copy(alpha = alpha), radius = w / 2f, center = Offset(w / 2f, w / 2f))
    }
}
