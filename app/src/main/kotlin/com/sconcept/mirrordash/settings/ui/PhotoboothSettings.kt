package com.sconcept.mirrordash.settings.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sconcept.mirrordash.photobooth.HardwareTestStatus
import com.sconcept.mirrordash.photobooth.PhotoboothViewModel
import com.sconcept.mirrordash.settings.SettingsUiState
import com.sconcept.mirrordash.settings.SettingsViewModel
import com.sconcept.mirrordash.ui.theme.MDTheme

@Composable
fun PhotoboothSettingsContent(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val settings = uiState.settings
    val context = LocalContext.current
    // Resolves to the same instance MirrorDashActivity owns via `by viewModels {}` - both are
    // scoped to the Activity's ViewModelStore, so diagnostics run here and the Photobooth tab
    // itself always agree on camera state without threading a second parameter through
    // SettingsScreen/SectionScaffold.
    val photoboothViewModel: PhotoboothViewModel = viewModel(
        factory = PhotoboothViewModel.factory(context.applicationContext as Application),
    )
    val photoboothState by photoboothViewModel.uiState.collectAsStateWithLifecycle()

    // Enumeration runs with no permission needed (see CameraCapabilityDetector), so this reflects
    // real hardware presence even before the user has ever granted camera access - an empty list
    // here means Camera2 itself reports zero devices, not just "permission not asked yet".
    val cameraDetected = photoboothState.cameras.isNotEmpty()
    SettingRow(
        title = "Enable Photobooth",
        subtitle = if (cameraDetected) {
            "Adds a Photobooth tab for 3-photo countdown sessions"
        } else {
            "No camera was detected on this device"
        },
    ) {
        Switch(
            checked = settings.photoboothEnabled && cameraDetected,
            onCheckedChange = viewModel::setPhotoboothEnabled,
            enabled = cameraDetected,
            colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
        )
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Diagnostics") {
        Text(
            "This mirror runs on unverified Rockchip/Echelon hardware - run this before relying on Photobooth or MirrorDrop.",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(12.dp))

        DiagnosticsBlock("Device") {
            val info = photoboothState.deviceInfo
            DiagnosticsLine("Manufacturer / model", "${info.manufacturer} ${info.model}")
            DiagnosticsLine("Android", "${info.androidRelease} (API ${info.apiLevel})")
            DiagnosticsLine("CPU ABI", info.supportedAbis.joinToString())
            DiagnosticsLine("Device / board / hardware", "${info.device} / ${info.board} / ${info.hardware}")
        }

        Spacer(Modifier.height(16.dp))

        DiagnosticsBlock("Camera") {
            if (!photoboothState.hasCameraPermission) {
                Text("Camera permission not granted yet.", style = MDTheme.type.caption, color = MDTheme.colors.textTertiary)
            }
            if (photoboothState.cameras.isEmpty()) {
                Text("No cameras detected.", style = MDTheme.type.caption, color = MDTheme.colors.danger)
            } else {
                photoboothState.cameras.forEach { camera ->
                    DiagnosticsLine(
                        "Camera ${camera.cameraId} (${camera.lensFacing})",
                        "${camera.hardwareLevel} · AF ${if (camera.supportsAutoFocus) "yes" else "no"} · " +
                            "Flash ${if (camera.hasFlash) "yes" else "no"} · JPEG ${camera.jpegSizes.firstOrNull() ?: "n/a"}",
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        DiagnosticsBlock("Network") {
            DiagnosticsLine("Wi-Fi", if (com.sconcept.mirrordash.mirrordrop.MirrorDropNetworkUtils.isWifiConnected(context)) "Connected" else "Not connected")
            DiagnosticsLine(
                "LAN address",
                com.sconcept.mirrordash.mirrordrop.MirrorDropNetworkUtils.getLocalIpv4Address() ?: "Unavailable",
            )
            DiagnosticsLine("MirrorDrop server", uiState.mirrorDropState.serverState.let { state ->
                when (state) {
                    is com.sconcept.mirrordash.mirrordrop.MirrorDropServerState.Running -> "Running on port ${state.port}"
                    is com.sconcept.mirrordash.mirrordrop.MirrorDropServerState.Stopped -> "Stopped"
                    is com.sconcept.mirrordash.mirrordrop.MirrorDropServerState.Starting -> "Starting..."
                    is com.sconcept.mirrordash.mirrordrop.MirrorDropServerState.Stopping -> "Stopping..."
                    is com.sconcept.mirrordash.mirrordrop.MirrorDropServerState.Error -> "Error - ${state.message}"
                }
            })
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = photoboothViewModel::runHardwareTest,
            enabled = !photoboothState.isRunningHardwareTest,
        ) {
            if (photoboothState.isRunningHardwareTest) {
                CircularProgressIndicator(modifier = Modifier.height(16.dp), color = MDTheme.colors.onAccent, strokeWidth = 2.dp)
                Spacer(Modifier.height(0.dp))
                Text("  Running test...")
            } else {
                Text("Run Hardware Test")
            }
        }

        photoboothState.lastHardwareTestReport?.let { report ->
            Spacer(Modifier.height(12.dp))
            Text(
                "Overall: ${report.overall}",
                style = MDTheme.type.settingTitle,
                color = report.overall.toColor(),
            )
            Spacer(Modifier.height(6.dp))
            DiagnosticsLine("Camera", "${report.camera.status} - ${report.camera.message}")
            DiagnosticsLine("Storage", "${report.storage.status} - ${report.storage.message}")
            DiagnosticsLine("Network", "${report.network.status} - ${report.network.message}")
        }
    }
}

@Composable
private fun DiagnosticsBlock(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MDTheme.type.settingTitle, color = MDTheme.colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun DiagnosticsLine(label: String, value: String) {
    Text("$label: $value", style = MDTheme.type.caption, color = MDTheme.colors.textSecondary)
}

private fun HardwareTestStatus.toColor() = when (this) {
    HardwareTestStatus.PASS -> androidx.compose.ui.graphics.Color(0xFF7FBF7F)
    HardwareTestStatus.WARNING -> androidx.compose.ui.graphics.Color(0xFFE8A659)
    HardwareTestStatus.FAIL -> androidx.compose.ui.graphics.Color(0xFFE0665A)
}
