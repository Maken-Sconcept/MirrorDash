package com.sconcept.mirrordash.walkietalkie

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.sconcept.mirrordash.ui.theme.MDTheme

/**
 * The single push-to-talk control, shared verbatim by the in-app surface and
 * [PttOverlayController]'s floating window - see WalkieTalkieViewModel for why both call the
 * same [onPressStart]/[onPressEnd] rather than duplicating hold-to-talk logic.
 */
@Composable
fun PttButton(
    isTransmitting: Boolean,
    enabled: Boolean,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (isTransmitting) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pttScale",
    )

    Box(
        modifier = modifier
            .size(64.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(
                if (isTransmitting) MDTheme.colors.accent else MDTheme.colors.surfaceElevated,
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
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = "Push to talk",
            tint = if (isTransmitting) MDTheme.colors.onAccent else MDTheme.colors.textPrimary,
        )
    }
}
