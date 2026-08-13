package com.sconcept.mirrordash.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.sconcept.mirrordash.settings.SettingsUiState
import com.sconcept.mirrordash.settings.SettingsViewModel
import com.sconcept.mirrordash.settings.WALKIE_TALKIE_TARGET_ALL
import com.sconcept.mirrordash.ui.theme.MDTheme
import com.sconcept.mirrordash.walkietalkie.WalkieTalkieMicBoost

@Composable
fun WalkieTalkieSettingsContent(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val settings = uiState.settings
    var newPeerName by remember { mutableStateOf("") }
    var newPeerIp by remember { mutableStateOf("") }

    SettingRow(title = "Enable Walkie-Talkie", subtitle = "Push-to-talk audio with other MirrorDash units") {
        Switch(
            checked = settings.walkieTalkieEnabled,
            onCheckedChange = viewModel::setWalkieTalkieEnabled,
            colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
        )
    }

    Spacer(Modifier.height(12.dp))

    val context = androidx.compose.ui.platform.LocalContext.current
    val resolvedDeviceName = remember(settings.deviceName) {
        settings.deviceName.ifBlank { com.sconcept.mirrordash.settings.DeviceNameHelper.defaultDeviceName(context) }
    }
    Text(
        "Other units see this one as \"$resolvedDeviceName\" - change it under Launcher > Device name.",
        style = MDTheme.type.caption,
        color = MDTheme.colors.textTertiary,
    )

    Spacer(Modifier.height(24.dp))

    SettingGroup(title = "Nearby devices") {
        val alreadyAddedIps = remember(settings.walkieTalkiePeers) { settings.walkieTalkiePeers.map { it.ip }.toSet() }
        val nearby = uiState.nearbyWalkieTalkiePeers.filter { it.ip !in alreadyAddedIps }

        if (!settings.walkieTalkieEnabled) {
            Text(
                "Turn on Walkie-Talkie to scan for nearby MirrorDash units.",
                style = MDTheme.type.settingSubtitle,
                color = MDTheme.colors.textTertiary,
            )
        } else if (nearby.isEmpty()) {
            ScanningRow()
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                nearby.forEach { peer ->
                    NearbyDeviceRow(
                        name = peer.name,
                        ip = peer.ip,
                        onAdd = { viewModel.addWalkieTalkiePeer(peer.name, peer.ip) },
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Peers") {
        settings.walkieTalkiePeers.forEach { peer ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
            ) {
                RadioButton(
                    selected = settings.walkieTalkieTarget == peer.ip,
                    onClick = { viewModel.setWalkieTalkieTarget(peer.ip) },
                    colors = RadioButtonDefaults.colors(selectedColor = MDTheme.colors.accent),
                )
                Column(Modifier.weight(1f)) {
                    Text(peer.name, style = MDTheme.type.body, color = MDTheme.colors.textPrimary)
                    Text(peer.ip, style = MDTheme.type.caption, color = MDTheme.colors.textTertiary)
                }
                IconButton(onClick = { viewModel.removeWalkieTalkiePeer(peer) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove", tint = MDTheme.colors.textTertiary)
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        ) {
            RadioButton(
                selected = settings.walkieTalkieTarget == WALKIE_TALKIE_TARGET_ALL,
                onClick = { viewModel.setWalkieTalkieTarget(WALKIE_TALKIE_TARGET_ALL) },
                colors = RadioButtonDefaults.colors(selectedColor = MDTheme.colors.accent),
            )
            Text("All peers", style = MDTheme.type.body, color = MDTheme.colors.textPrimary)
        }

        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newPeerName,
                onValueChange = { newPeerName = it },
                placeholder = { Text("Name") },
                singleLine = true,
                colors = fieldColors(),
                modifier = Modifier.weight(1f).trackFieldFocusForIdleTimer(),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = newPeerIp,
                onValueChange = { newPeerIp = it },
                placeholder = { Text("IP address") },
                singleLine = true,
                colors = fieldColors(),
                modifier = Modifier.weight(1f).trackFieldFocusForIdleTimer(),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    if (newPeerName.isNotBlank() && newPeerIp.isNotBlank()) {
                        viewModel.addWalkieTalkiePeer(newPeerName, newPeerIp)
                        newPeerName = ""
                        newPeerIp = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MDTheme.colors.accent, contentColor = MDTheme.colors.onAccent),
            ) {
                Text("Add")
            }
        }
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Mic boost") {
        Text(WalkieTalkieMicBoost.label(settings.walkieTalkieMicBoostPercent), style = MDTheme.type.settingSubtitle, color = MDTheme.colors.textSecondary)
        Slider(
            value = settings.walkieTalkieMicBoostPercent.toFloat(),
            onValueChange = { viewModel.setWalkieTalkieMicBoost(it.toInt()) },
            valueRange = 100f..300f,
            colors = SliderDefaults.colors(thumbColor = MDTheme.colors.accent, activeTrackColor = MDTheme.colors.accent),
        )
    }

    Spacer(Modifier.height(24.dp))

    SettingRow(
        title = "Floating button",
        subtitle = "Keep a push-to-talk button available while using other apps",
    ) {
        Switch(
            checked = settings.walkieTalkieOverlayEnabled,
            onCheckedChange = viewModel::setWalkieTalkieOverlayEnabled,
            colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
        )
    }
}

/** A discovered-but-not-yet-added peer, styled like a Wi-Fi network picker entry: signal-style
 * icon, device name as the primary line, its address as the secondary line (playing the role
 * "security type" plays in a Wi-Fi list), tap anywhere on the row to add. */
@Composable
private fun NearbyDeviceRow(name: String, ip: String, onAdd: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onAdd)
            .padding(vertical = 12.dp, horizontal = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MDTheme.colors.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Wifi, contentDescription = null, tint = MDTheme.colors.accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MDTheme.type.body, color = MDTheme.colors.textPrimary)
            Text(ip, style = MDTheme.type.caption, color = MDTheme.colors.textTertiary)
        }
        Icon(Icons.Filled.Add, contentDescription = "Add", tint = MDTheme.colors.accent)
    }
}

/** Shown while mDNS discovery is active but hasn't found anything yet - a gentle pulsing radar
 * glyph rather than a static "no devices" dead-end, since new units can appear at any moment. */
@Composable
private fun ScanningRow() {
    val transition = rememberInfiniteTransition(label = "scanning")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "scanningAlpha",
    )

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(
            Icons.Filled.WifiFind,
            contentDescription = null,
            tint = MDTheme.colors.textTertiary,
            modifier = Modifier.size(20.dp).alpha(alpha),
        )
        Spacer(Modifier.width(12.dp))
        Text("Searching for nearby devices…", style = MDTheme.type.settingSubtitle, color = MDTheme.colors.textTertiary)
    }
}
