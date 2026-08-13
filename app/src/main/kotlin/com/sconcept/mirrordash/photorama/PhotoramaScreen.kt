package com.sconcept.mirrordash.photorama

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.sconcept.mirrordash.nas.model.SmbConnectionState
import com.sconcept.mirrordash.ui.theme.MDTheme
import java.io.File

/**
 * The Photorama page - a digital-photo-frame view of the reused NAS Photorama layer (brief
 * section 18-22). Fullscreen photo, minimal chrome, crossfade transitions, and honest states
 * for every network condition rather than a blank screen when the NAS hiccups.
 */
@Composable
fun PhotoramaScreen(state: PhotoramaUiState, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(MDTheme.colors.background)) {
        when {
            !state.isConfigured -> NotConfiguredState()
            state.hasNoPhotos -> EmptyFolderState()
            state.currentPhoto != null -> Crossfade(
                targetState = state.currentPhoto,
                animationSpec = tween(900),
                label = "photorama",
            ) { file ->
                PhotoFrame(file)
            }
            else -> ConnectingState(state.connectionState)
        }
    }
}

@Composable
private fun PhotoFrame(file: File) {
    Image(
        painter = rememberAsyncImagePainter(model = file),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun NotConfiguredState() {
    CenteredMessage(
        title = "Photorama isn't set up yet",
        subtitle = "Add a NAS connection and pick a photo folder in Settings to turn this page into a slideshow.",
    )
}

@Composable
private fun EmptyFolderState() {
    CenteredMessage(
        title = "No photos found",
        subtitle = "The selected folder doesn't contain any supported images yet.",
    )
}

@Composable
private fun ConnectingState(connectionState: SmbConnectionState) {
    val message = when (connectionState) {
        SmbConnectionState.CONNECTING -> "Connecting to your NAS…"
        SmbConnectionState.RECONNECTING -> "Reconnecting…"
        SmbConnectionState.AUTHENTICATION_FAILED -> "Incorrect username or password. Check Settings > Photorama."
        SmbConnectionState.SERVER_UNREACHABLE -> "Can't reach the NAS right now."
        SmbConnectionState.SHARE_UNAVAILABLE -> "The configured share couldn't be found."
        SmbConnectionState.OFFLINE -> "The device is offline."
        SmbConnectionState.DISCONNECTED -> "Waiting to connect…"
        SmbConnectionState.CONNECTED -> "Loading photos…"
    }
    CenteredMessage(title = message, subtitle = null)
}

@Composable
private fun CenteredMessage(title: String, subtitle: String?) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MDTheme.type.settingTitle, color = MDTheme.colors.textPrimary)
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                style = MDTheme.type.settingSubtitle,
                color = MDTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 64.dp),
            )
        }
    }
}
