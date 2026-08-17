package com.sconcept.mirrordash.photobooth

import android.Manifest
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sconcept.mirrordash.ui.theme.MDTheme

/**
 * The Photobooth tab (brief §42): camera-permission handling and "no working camera" fallback
 * (Phase 1), then the real 3-photo countdown → montage flow (Phase 8) on top once a camera's
 * available. Deliberately simple v1 - no filters/frames, one capture flow, one montage layout.
 */
@Composable
fun PhotoboothScreen(viewModel: PhotoboothViewModel, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val requestCameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refreshCameraState() }

    LaunchedEffect(Unit) { viewModel.refreshCameraState() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MDTheme.colors.background),
        contentAlignment = Alignment.Center,
    ) {
        when {
            !uiState.hasCameraPermission -> PhotoboothMessage(
                title = "Photobooth",
                body = "MirrorDash needs camera access to run the photobooth.",
            ) {
                Button(
                    onClick = { requestCameraPermission.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = MDTheme.colors.accent, contentColor = MDTheme.colors.onAccent),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Text("Grant camera access")
                }
            }
            uiState.cameras.isEmpty() -> PhotoboothMessage(
                title = "Photobooth",
                body = "No camera was detected on this device.",
                caption = "Check Settings → Photobooth → Diagnostics for details.",
            )
            uiState.capture.phase == PhotoboothCapturePhase.ERROR -> PhotoboothMessage(
                title = "Photobooth",
                body = uiState.capture.errorMessage ?: "Something went wrong with the camera.",
            ) {
                Button(onClick = { viewModel.retake() }) { Text("Try again") }
            }
            uiState.capture.phase == PhotoboothCapturePhase.PREVIEW -> MontagePreview(
                image = uiState.capture.montagePreview,
                onRetake = viewModel::retake,
                onDone = viewModel::dismissPreview,
            )
            else -> CaptureSurface(viewModel = viewModel, capture = uiState.capture)
        }
    }
}

@Composable
private fun PhotoboothMessage(title: String, body: String, caption: String? = null, actions: (@Composable () -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(32.dp),
    ) {
        Text(title, style = MDTheme.type.sectionTitle, color = MDTheme.colors.textPrimary, fontWeight = FontWeight.Medium)
        Text(body, style = MDTheme.type.body, color = MDTheme.colors.textSecondary)
        if (caption != null) {
            Text(caption, style = MDTheme.type.caption, color = MDTheme.colors.textTertiary)
        }
        actions?.invoke()
    }
}

@Composable
private fun CaptureSurface(viewModel: PhotoboothViewModel, capture: PhotoboothCaptureState) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }

    DisposableEffect(Unit) {
        viewModel.bindCamera(previewView, lifecycleOwner)
        onDispose { viewModel.unbindCamera() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        when (capture.phase) {
            PhotoboothCapturePhase.IDLE -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Button(
                    onClick = { viewModel.startCaptureSession() },
                    colors = ButtonDefaults.buttonColors(containerColor = MDTheme.colors.accent, contentColor = MDTheme.colors.onAccent),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.padding(bottom = 48.dp),
                ) {
                    Text("Touch to start", style = MDTheme.type.body)
                }
            }
            PhotoboothCapturePhase.COUNTDOWN -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (capture.countdownValue > 0) capture.countdownValue.toString() else "",
                    color = MDTheme.colors.textPrimary,
                    fontSize = 120.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            PhotoboothCapturePhase.CAPTURING -> Box(
                modifier = Modifier.fillMaxSize().background(MDTheme.colors.textPrimary.copy(alpha = 0.85f)),
            )
            PhotoboothCapturePhase.PROCESSING -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Putting your photos together…", style = MDTheme.type.body, color = MDTheme.colors.textPrimary)
            }
            else -> Unit
        }

        if (capture.phase in setOf(PhotoboothCapturePhase.COUNTDOWN, PhotoboothCapturePhase.CAPTURING)) {
            Text(
                "Photo ${capture.photoIndex + 1} of 3",
                style = MDTheme.type.caption,
                color = MDTheme.colors.textPrimary,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun MontagePreview(image: PhotoboothImage?, onRetake: () -> Unit, onDone: () -> Unit) {
    val bitmap = remember(image?.id) {
        image?.bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize().padding(24.dp),
    ) {
        Text("Your photos", style = MDTheme.type.sectionTitle, color = MDTheme.colors.textPrimary, fontWeight = FontWeight.Medium)
        if (bitmap != null) {
            // The montage is a tall 3-photo strip (see MontageGenerator) - constrained to the
            // remaining space and scaled to fit within it (rather than sized off its own aspect
            // ratio) so it can never push Retake/Done off the bottom of the screen.
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Photo booth montage",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(0.6f)
                    .background(MDTheme.colors.backgroundElevated, RoundedCornerShape(16.dp))
                    .padding(8.dp),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onClick = onRetake) { Text("Retake") }
            Button(
                onClick = onDone,
                colors = ButtonDefaults.buttonColors(containerColor = MDTheme.colors.accent, contentColor = MDTheme.colors.onAccent),
            ) { Text("Done") }
        }
    }
}
