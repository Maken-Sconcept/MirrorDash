package com.sconcept.mirrordash.walkietalkie

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sconcept.mirrordash.ui.theme.MDTheme

/**
 * Shared push-to-talk control used by the settings page, the in-app overlay, and the floating
 * overlay service. The old BerthierOptions talk button used expanding radio-wave circles while
 * talking; this Compose version recreates that same "live comms" cue both while transmitting and
 * while an incoming speaker is active.
 */
@Composable
fun PttButton(
    isTransmitting: Boolean,
    isSpeaking: Boolean = false,
    enabled: Boolean,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 64.dp,
) {
    val isActive = isTransmitting || isSpeaking
    val scale by animateFloatAsState(
        targetValue = when {
            isTransmitting -> 1.12f
            isSpeaking -> 1.05f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "pttScale",
    )
    val haloAlpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "pttHaloAlpha",
    )
    val accent = MDTheme.colors.accent
    val surfaceElevated = MDTheme.colors.surfaceElevated
    val onAccent = MDTheme.colors.onAccent
    val textPrimary = MDTheme.colors.textPrimary
    val waveTransition = rememberInfiniteTransition(label = "pttVoice")
    val wavePhase by waveTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 940, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pttWavePhase",
    )

    Box(
        modifier = modifier
            .size(buttonSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(
                if (isTransmitting) accent else surfaceElevated,
                CircleShape,
            )
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        onPressStart()
                        tryAwaitRelease()
                        onPressEnd()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (haloAlpha > 0f) {
                val minDimension = size.minDimension
                val baseRadius = minDimension * 0.35f

                drawCircle(
                    color = accent.copy(alpha = 0.12f * haloAlpha),
                    radius = baseRadius + minDimension * (0.06f + 0.08f * wavePhase),
                )

                repeat(3) { index ->
                    val waveOffset = (wavePhase + index * 0.2f) % 1f
                    val radius = baseRadius + minDimension * (0.12f + index * 0.08f + waveOffset * 0.12f)
                    val alpha = ((1f - waveOffset) * (0.42f - index * 0.09f) * haloAlpha).coerceAtLeast(0f)
                    drawCircle(
                        color = accent.copy(alpha = alpha),
                        radius = radius,
                        style = Stroke(
                            width = (minDimension * (0.052f - index * 0.008f)).coerceAtLeast(2f),
                            cap = StrokeCap.Round,
                        ),
                    )
                }
            }
        }

        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = "Push to talk",
            tint = if (isTransmitting) onAccent else textPrimary,
        )
    }
}
