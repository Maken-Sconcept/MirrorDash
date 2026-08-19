package com.sconcept.mirrordash.settings.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sconcept.mirrordash.rtsp.RTSP_CAMERA_PORT
import com.sconcept.mirrordash.rtsp.RTSP_QUALITY_HIGH
import com.sconcept.mirrordash.rtsp.RTSP_QUALITY_LOW
import com.sconcept.mirrordash.rtsp.RTSP_QUALITY_MEDIUM
import com.sconcept.mirrordash.rtsp.RtspStreamQuality
import com.sconcept.mirrordash.rtsp.localRtspIpv4Address
import com.sconcept.mirrordash.settings.SettingsUiState
import com.sconcept.mirrordash.settings.SettingsViewModel
import com.sconcept.mirrordash.ui.theme.MDTheme

@Composable
fun RtspCameraSettingsContent(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    onRequestCameraAccess: () -> Unit,
) {
    val context = LocalContext.current
    val enabled = uiState.settings.rtspCameraEnabled
    val hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val lanIp = remember { localRtspIpv4Address() }
    val profile = RtspStreamQuality.profile(uiState.settings.rtspQuality)

    SettingRow(
        title = "Enable RTSP stream",
        subtitle = when {
            !hasCameraPermission -> "Camera permission is required"
            enabled -> "H.264 camera feed is active on your LAN"
            else -> "Off"
        },
    ) {
        Switch(
            checked = enabled,
            onCheckedChange = { shouldEnable ->
                if (shouldEnable && !hasCameraPermission) onRequestCameraAccess()
                else viewModel.setRtspCameraEnabled(shouldEnable)
            },
            colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
        )
    }

    Spacer(Modifier.height(24.dp))
    SettingGroup(title = "Stream quality") {
        RtspQualityOption(RTSP_QUALITY_LOW, "Low quality", "640 × 480 · 15 fps · 0.8 Mbps · lowest delay", uiState.settings.rtspQuality == RTSP_QUALITY_LOW, viewModel::setRtspQuality)
        RtspQualityOption(RTSP_QUALITY_MEDIUM, "Medium quality", "848 × 480 · 30 fps · 1.2 Mbps · balanced (default)", uiState.settings.rtspQuality == RTSP_QUALITY_MEDIUM, viewModel::setRtspQuality)
        RtspQualityOption(RTSP_QUALITY_HIGH, "High quality", "1280 × 720 · 30 fps · 2 Mbps · more detail, more delay", uiState.settings.rtspQuality == RTSP_QUALITY_HIGH, viewModel::setRtspQuality)
    }

    Spacer(Modifier.height(28.dp))
    SettingGroup(title = "RTSP connection") {
        Text(
            lanIp?.let { "rtsp://$it:$RTSP_CAMERA_PORT/mirror" } ?: "Connect the mirror to Wi-Fi to show its address",
            style = MDTheme.type.body,
            color = MDTheme.colors.textPrimary,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "H.264 video + AAC microphone audio · ${profile.width} × ${profile.height} · ${profile.fps} fps\nUse RTSP over TCP in your viewer.",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
    }

    Spacer(Modifier.height(28.dp))
    SettingGroup(title = "Allowed local clients") {
        BufferedTextField(
            persistedValue = uiState.settings.rtspAllowedClientIps,
            onValueChange = viewModel::setRtspAllowedClientIps,
            placeholder = { Text("IP addresses, separated by commas") },
            singleLine = false,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Only the listed IPv4 addresses can connect. For Home Assistant, enter 172.17.0.6.",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
    }

    Spacer(Modifier.height(28.dp))
    SettingGroup(title = "LAN-only protection") {
        Text(
            "The device firewall permits only the IP addresses above and drops every other RTSP client. This keeps the stream blocked from the public internet even if port forwarding is added accidentally.",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun RtspQualityOption(
    quality: String,
    label: String,
    detail: String,
    selected: Boolean,
    onSelect: (String) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = { onSelect(quality) },
            colors = RadioButtonDefaults.colors(selectedColor = MDTheme.colors.accent),
        )
        Column(Modifier.padding(start = 4.dp)) {
            Text(label, style = MDTheme.type.body, color = MDTheme.colors.textPrimary)
            Text(detail, style = MDTheme.type.settingSubtitle, color = MDTheme.colors.textSecondary)
        }
    }
}
