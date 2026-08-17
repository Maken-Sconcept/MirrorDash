package com.sconcept.mirrordash.settings.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sconcept.mirrordash.mirrordrop.MirrorDropQrCode
import com.sconcept.mirrordash.mirrordrop.MirrorDropServerState
import com.sconcept.mirrordash.mirrordrop.ShareMode
import com.sconcept.mirrordash.mirrordrop.buildMirrorDropShareUrl
import com.sconcept.mirrordash.settings.SettingsUiState
import com.sconcept.mirrordash.settings.SettingsViewModel
import com.sconcept.mirrordash.ui.theme.MDTheme

/** Selectable from the Settings share picker today - Selected Images/Session need a gallery UI
 * that doesn't exist until Phase 10, so [ShareMode.SelectedImages]/[ShareMode.SelectedSession]
 * aren't reachable from here yet even though the resolver already supports them. */
private val PICKABLE_SHARE_MODES = listOf(ShareMode.LatestMontage, ShareMode.LatestPhoto, ShareMode.CurrentSession, ShareMode.EntireLibrary)

/**
 * "MirrorDrop" internally, "Share Photos" to the user (brief §3) - local, self-hosted, Snapdrop-
 * style sharing. Kept as its own settings section, separate from Photobooth's, since the server
 * is meant to be reusable by other features later (brief §40), not exclusive to the photobooth.
 */
@Composable
fun MirrorDropSettingsContent(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val settings = uiState.settings
    val mirrorDrop = uiState.mirrorDropState

    SettingRow(
        title = "Enable local photo sharing",
        subtitle = "Lets nearby devices on this Wi-Fi network open a page to download shared photos",
    ) {
        Switch(
            checked = settings.mirrorDropEnabled,
            onCheckedChange = viewModel::setMirrorDropEnabled,
            colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
        )
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Server port") {
        Text(
            "Advanced - only change this if the default conflicts with something else on your network.",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(10.dp))
        BufferedTextField(
            persistedValue = settings.mirrorDropPort.toString(),
            onValueChange = { raw -> raw.toIntOrNull()?.let { viewModel.setMirrorDropPort(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Status") {
        StatusLine("Server", mirrorDrop.serverState.label())
        StatusLine("Wi-Fi", if (mirrorDrop.wifiConnected) "Connected" else "Not connected")
        StatusLine("Address", mirrorDrop.localIp?.let { "$it:${mirrorDrop.port}" } ?: "Unavailable")
        StatusLine("Devices connected", mirrorDrop.connectedPeers.size.toString())
    }

    if (mirrorDrop.serverState is MirrorDropServerState.Running) {
        Spacer(Modifier.height(28.dp))
        SettingGroup(title = "Share") {
            ShareSection(uiState, viewModel)
        }
    }
}

@Composable
private fun ShareSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val mirrorDrop = uiState.mirrorDropState
    val session = mirrorDrop.activeShareSession
    val lastPin by viewModel.lastSharePin.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var requirePin by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableStateOf<ShareMode>(ShareMode.LatestMontage) }

    if (session == null) {
        Text(
            "Nothing is being shared right now. Starting a share generates a one-time link only devices on this Wi-Fi can use.",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(12.dp))
        Text("What to share", style = MDTheme.type.settingSubtitle, color = MDTheme.colors.textSecondary)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PICKABLE_SHARE_MODES.forEach { mode ->
                FilterChip(
                    selected = selectedMode == mode,
                    onClick = { selectedMode = mode },
                    label = { Text(mode.label) },
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        SettingRow(title = "Require a PIN", subtitle = "Adds a 6-digit code shown alongside the QR") {
            Switch(
                checked = requirePin,
                onCheckedChange = { requirePin = it },
                colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = { viewModel.startMirrorDropSharing(requirePin, selectedMode) }) {
            Text("Start Sharing")
        }
        return
    }

    val localIp = mirrorDrop.localIp
    if (localIp == null) {
        Text(
            "Waiting for a Wi-Fi address before this link can work.",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        return
    }

    val shareUrl = remember(session.token, localIp, mirrorDrop.port) {
        buildMirrorDropShareUrl(localIp, mirrorDrop.port, session.token)
    }
    val qrBitmap = remember(shareUrl) { MirrorDropQrCode.generate(shareUrl) }

    Image(
        bitmap = qrBitmap.asImageBitmap(),
        contentDescription = "QR code for $shareUrl",
        modifier = Modifier
            .size(200.dp)
            .background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(12.dp))
            .padding(12.dp),
    )
    Spacer(Modifier.height(12.dp))
    Text("Sharing: ${selectedMode.label}", style = MDTheme.type.settingSubtitle, color = MDTheme.colors.textSecondary)
    Spacer(Modifier.height(4.dp))
    Text(shareUrl, style = MDTheme.type.caption, color = MDTheme.colors.textSecondary)

    if (session.pinHash != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            lastPin?.let { "PIN: $it" } ?: "PIN required (already shown when sharing started)",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textPrimary,
        )
    }

    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(shareUrl)) }) {
            Text("Copy address")
        }
        Button(onClick = { viewModel.stopMirrorDropSharing() }) {
            Text("Stop Sharing")
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Text("$label: $value", style = MDTheme.type.caption, color = MDTheme.colors.textSecondary)
    Spacer(Modifier.height(4.dp))
}

private fun MirrorDropServerState.label(): String = when (this) {
    is MirrorDropServerState.Stopped -> "Stopped"
    is MirrorDropServerState.Starting -> "Starting..."
    is MirrorDropServerState.Running -> "Running on port $port"
    is MirrorDropServerState.Stopping -> "Stopping..."
    is MirrorDropServerState.Error -> "Error - $message"
}
