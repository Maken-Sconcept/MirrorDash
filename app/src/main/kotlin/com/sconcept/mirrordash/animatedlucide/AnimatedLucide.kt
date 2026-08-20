package com.sconcept.mirrordash.animatedlucide

import android.animation.ValueAnimator
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Native, declarative definition; resource geometry can later be replaced with imported SVG elements. */
data class AnimatedLucideDefinition(
    val name: String,
    @DrawableRes val staticResource: Int,
    val animations: Map<String, AnimatedLucideTimeline> = emptyMap(),
)

data class AnimatedLucideTimeline(
    val durationMillis: Int = 600,
    val tracks: List<AnimatedLucideTrack>,
    val replayPolicy: AnimatedLucideReplayPolicy = AnimatedLucideReplayPolicy.Restart,
)

data class AnimatedLucideTrack(
    /** Reserved for stable SVG element IDs; `root` is available with resource-backed geometry. */
    val target: String = "root",
    val property: AnimatedLucideProperty,
    val keyframes: List<AnimatedLucideKeyframe>,
)

data class AnimatedLucideKeyframe(val fraction: Float, val value: Float)
enum class AnimatedLucideProperty { TranslationX, TranslationY, Rotation, ScaleX, ScaleY, Alpha }
enum class AnimatedLucideReplayPolicy { Restart, IgnoreWhileRunning, Continue }
enum class AnimatedLucideTrigger { Manual, OnVisible, OnStateChange, Continuous }

@Stable
class AnimatedLucideController internal constructor(private val scope: CoroutineScope) {
    private val clock = Animatable(0f)
    private var job: Job? = null
    val progress: Float get() = clock.value
    val isRunning: Boolean get() = job?.isActive == true

    fun play(timeline: AnimatedLucideTimeline) {
        if (timeline.replayPolicy == AnimatedLucideReplayPolicy.IgnoreWhileRunning && isRunning) return
        if (timeline.replayPolicy == AnimatedLucideReplayPolicy.Continue && isRunning) return
        job?.cancel()
        job = scope.launch {
            clock.snapTo(0f)
            clock.animateTo(1f, tween(timeline.durationMillis, easing = FastOutSlowInEasing))
        }
    }

    fun stop() { job?.cancel() }
    fun reset() { job?.cancel(); scope.launch { clock.snapTo(0f) } }
    fun snapToRest() = reset()
}

@Composable
fun rememberAnimatedLucideController(): AnimatedLucideController = rememberAnimatedLucideController(rememberCoroutineScope())

@Composable
fun rememberAnimatedLucideController(scope: CoroutineScope): AnimatedLucideController = remember(scope) { AnimatedLucideController(scope) }

@Composable
fun AnimatedLucideIcon(
    icon: AnimatedLucideDefinition,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    controller: AnimatedLucideController = rememberAnimatedLucideController(),
    animation: String = "default",
    trigger: AnimatedLucideTrigger = AnimatedLucideTrigger.Manual,
    animated: Boolean = false,
    enabled: Boolean = true,
) {
    val timeline = icon.animations[animation]
    // Decorative motion is disabled with the platform animator setting; geometry still renders.
    LaunchedEffect(enabled, timeline, trigger, animated) {
        if (enabled && ValueAnimator.areAnimatorsEnabled() && timeline != null &&
            (trigger == AnimatedLucideTrigger.OnVisible || trigger == AnimatedLucideTrigger.OnStateChange && animated)
        ) controller.play(timeline)
    }
    val resolved = timeline?.tracks?.filter { it.target == "root" }.orEmpty()
    fun value(property: AnimatedLucideProperty, rest: Float): Float = resolved.firstOrNull { it.property == property }?.let { interpolate(it.keyframes, controller.progress) } ?: rest
    Image(
        painter = painterResource(icon.staticResource),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        colorFilter = if (tint == Color.Unspecified) null else androidx.compose.ui.graphics.ColorFilter.tint(tint),
        modifier = modifier
            .graphicsLayer(
                translationX = value(AnimatedLucideProperty.TranslationX, 0f),
                translationY = value(AnimatedLucideProperty.TranslationY, 0f),
                rotationZ = value(AnimatedLucideProperty.Rotation, 0f),
                scaleX = value(AnimatedLucideProperty.ScaleX, 1f),
                scaleY = value(AnimatedLucideProperty.ScaleY, 1f),
            )
            .alpha(value(AnimatedLucideProperty.Alpha, 1f)),
    )
}

internal fun interpolate(keyframes: List<AnimatedLucideKeyframe>, progress: Float): Float {
    val sorted = keyframes.sortedBy { it.fraction }
    val before = sorted.lastOrNull { it.fraction <= progress } ?: return sorted.firstOrNull()?.value ?: 0f
    val after = sorted.firstOrNull { it.fraction >= progress } ?: return before.value
    val local = ((progress - before.fraction) / (after.fraction - before.fraction)).coerceIn(0f, 1f)
    return before.value + (after.value - before.value) * LinearEasing.transform(local)
}
