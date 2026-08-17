package com.sconcept.mirrordash.airplay

import android.content.Context
import android.media.AudioManager
import android.util.Log
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

private const val TAG = "AirPlayMirrorSurface"
private const val VOLUME_OVERLAY_AUTO_HIDE_MS = 3000L

/**
 * Full-bleed live view of whatever is currently being AirPlay-mirrored to this unit. Hosts a
 * plain [SurfaceView] that [AirPlayVideoDecoder] decodes into directly (`MediaCodec` decode-to-
 * surface) - the decoder and its [AirPlayNsdBridge.Listener] registration are created fresh each
 * time this composable enters composition and torn down on [DisposableEffect]'s `onDispose`, so
 * simply mounting/unmounting this (driven by [AirPlayUiState.hasActiveVideo] in [ClockScreen])
 * is enough to start/stop receiving frames - no separate session management needed here.
 */
@Composable
fun AirPlayMirrorSurface(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var surfaceView by remember { mutableStateOf<SurfaceView?>(null) }
    var videoSize by remember { mutableStateOf(IntSize.Zero) }

    DisposableEffect(surfaceView) {
        val view = surfaceView
        if (view == null) {
            onDispose {}
        } else {
            val bridge = AirPlaySessionRegistry.getBridge(context)
            val decoder = AirPlayVideoDecoder(
                surfaceView = view,
                onError = { message -> Log.e(TAG, message) },
                onVideoSizeChanged = { width, height -> videoSize = IntSize(width, height) },
            )
            val listener = object : AirPlayNsdBridge.Listener {
                override fun onReceiverRunningChanged(running: Boolean, port: Int) = Unit
                override fun onClientChanged(deviceId: String, model: String, name: String) = Unit
                override fun onVideoFormatChanged(codec: Int, width: Int, height: Int) {
                    decoder.onVideoFormatChanged(codec, width, height)
                }
                override fun onVideoFrame(data: ByteArray, ptsUs: Long) = decoder.onVideoFrame(data, ptsUs)
                override fun onVideoSessionEnded() {
                    decoder.resetSession()
                }
                override fun onPinRequested(pin: String) = Unit
            }
            bridge.addListener(listener)
            // The one-time onVideoFormatChanged event (native only announces once per session,
            // gated by its own format_announced flag) fires as soon as the sender's SETUP
            // negotiates codec+size - typically *before* hasActiveVideo flips true and this
            // surface ever mounts, since that requires a frame to have already arrived. A
            // listener registered this late would otherwise never learn the codec and could
            // never configure a decoder, no matter how long the session runs - so prime it here
            // from the bridge's already-known snapshot instead of only waiting for a live event.
            // Metadata alone isn't enough - MediaCodec also needs a sample containing the actual
            // SPS/PPS NAL units, which the sender only ever transmits once per session (see
            // AirPlayNsdBridge.cachedFirstFrame's doc). Replay it here too, or a late-mounting
            // decoder has metadata but nothing it can actually configure with.
            val snapshot = bridge.snapshot()
            if (snapshot.codec != 0) {
                decoder.onVideoFormatChanged(snapshot.codec, snapshot.videoWidth, snapshot.videoHeight)
                bridge.cachedFirstFrameOrNull()?.let { (data, ptsUs) -> decoder.onVideoFrame(data, ptsUs) }
            }
            onDispose {
                bridge.removeListener(listener)
                decoder.release()
            }
        }
    }

    var showVolumeOverlay by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { showVolumeOverlay = !showVolumeOverlay },
        contentAlignment = Alignment.Center,
    ) {
        val aspect = videoSize.width.toFloat() / videoSize.height.toFloat()
        AndroidView(
            factory = { ctx -> SurfaceView(ctx).also { surfaceView = it } },
            modifier = if (videoSize.width > 0 && videoSize.height > 0) {
                Modifier.fillMaxSize().aspectRatio(aspect)
            } else {
                Modifier.fillMaxSize()
            },
        )

        AnimatedVisibility(
            visible = showVolumeOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
        ) {
            VolumeOverlay(onInteracted = { showVolumeOverlay = true })
        }

        if (showVolumeOverlay) {
            LaunchedEffect(Unit) {
                delay(VOLUME_OVERLAY_AUTO_HIDE_MS)
                showVolumeOverlay = false
            }
        }
    }
}

/** Tap-to-reveal media volume slider for while a session is actively mirroring - AirPlay hands us
 * decoded PCM to play (see [AirPlayAudioDecoder]) same as any other local media, so this adjusts
 * the ordinary [AudioManager.STREAM_MUSIC] volume rather than anything AirPlay-protocol-specific. */
@Composable
private fun VolumeOverlay(onInteracted: () -> Unit) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = when {
                volume <= 0 -> Icons.AutoMirrored.Filled.VolumeOff
                volume < maxVolume / 2 -> Icons.AutoMirrored.Filled.VolumeDown
                else -> Icons.AutoMirrored.Filled.VolumeUp
            },
            contentDescription = null,
            tint = Color.White,
        )
        Slider(
            value = volume.toFloat(),
            valueRange = 0f..maxVolume.toFloat(),
            onValueChange = { newValue ->
                volume = newValue.toInt()
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                onInteracted()
            },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
            ),
            modifier = Modifier.width(220.dp).padding(start = 12.dp),
        )
    }
}
