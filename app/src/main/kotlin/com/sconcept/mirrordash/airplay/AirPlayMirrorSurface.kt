package com.sconcept.mirrordash.airplay

import android.util.Log
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView

private const val TAG = "AirPlayMirrorSurface"

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
                override fun onVideoFrame(data: ByteArray, ptsUs: Long) {
                    decoder.onVideoFrame(data, ptsUs)
                }
                override fun onVideoSessionEnded() {
                    decoder.resetSession()
                }
                override fun onPinRequested(pin: String) = Unit
            }
            bridge.addListener(listener)
            onDispose {
                bridge.removeListener(listener)
                decoder.release()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        val aspect = videoSize.width.toFloat() / videoSize.height.toFloat()
        AndroidView(
            factory = { ctx -> SurfaceView(ctx).also { surfaceView = it } },
            modifier = if (videoSize.width > 0 && videoSize.height > 0) {
                Modifier.fillMaxSize().aspectRatio(aspect)
            } else {
                Modifier.fillMaxSize()
            },
        )
    }
}
