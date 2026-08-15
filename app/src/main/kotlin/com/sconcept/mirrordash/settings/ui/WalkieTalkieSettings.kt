package com.sconcept.mirrordash.settings.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.sconcept.mirrordash.settings.SettingsUiState
import com.sconcept.mirrordash.settings.SettingsViewModel
import com.sconcept.mirrordash.settings.WALKIE_TALKIE_TARGET_ALL
import com.sconcept.mirrordash.ui.theme.MDTheme
import com.sconcept.mirrordash.walkietalkie.PttButton
import com.sconcept.mirrordash.walkietalkie.WalkieTalkieChimes
import com.sconcept.mirrordash.walkietalkie.WalkieTalkieMicBoost
import com.sconcept.mirrordash.walkietalkie.model.WalkieTalkiePeer

@Composable
fun WalkieTalkieSettingsContent(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val settings = uiState.settings
    val walkieState = uiState.walkieTalkieState
    val nearbyIps = remember(uiState.nearbyWalkieTalkiePeers) { uiState.nearbyWalkieTalkiePeers.map { it.ip }.toSet() }
    val unknownIncomingSpeaker = remember(walkieState.activeIncomingPeerIp, walkieState.activeIncomingPeerName, settings.walkieTalkiePeers) {
        val activeName = walkieState.activeIncomingPeerName ?: return@remember null
        if (walkieState.activeIncomingPeerIp != null && settings.walkieTalkiePeers.any { it.ip == walkieState.activeIncomingPeerIp }) {
            null
        } else {
            activeName
        }
    }
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

    Spacer(Modifier.height(12.dp))

    Text(
        "Hold a device button to talk. Radio waves mean live voice, whether you are pressing the button or that unit is actively speaking.",
        style = MDTheme.type.caption,
        color = MDTheme.colors.textSecondary,
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
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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

    SettingGroup(title = "Saved devices") {
        if (settings.walkieTalkiePeers.isEmpty()) {
            Text(
                "Save a nearby device or add one manually, then hold its talk button here to call it directly.",
                style = MDTheme.type.settingSubtitle,
                color = MDTheme.colors.textTertiary,
            )
        } else {
            BroadcastTalkRow(
                peerCount = settings.walkieTalkiePeers.size,
                isTransmitting = walkieState.activeTransmitTarget == WALKIE_TALKIE_TARGET_ALL,
                isSpeaking = unknownIncomingSpeaker != null,
                speakingLabel = unknownIncomingSpeaker,
                enabled = settings.walkieTalkieEnabled && walkieState.hasMicPermission,
                onPressStart = { viewModel.pressToTalk(WALKIE_TALKIE_TARGET_ALL) },
                onPressEnd = viewModel::releaseToTalk,
            )

            Spacer(Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                settings.walkieTalkiePeers.forEach { peer ->
                    TalkDeviceRow(
                        peer = peer,
                        isNearby = peer.ip in nearbyIps,
                        isDefaultTarget = settings.walkieTalkieTarget == peer.ip,
                        isSpeaking = walkieState.activeIncomingPeerIp == peer.ip,
                        isTransmitting = walkieState.activeTransmitTarget == peer.ip,
                        talkEnabled = settings.walkieTalkieEnabled && walkieState.hasMicPermission,
                        onPressStart = { viewModel.pressToTalk(peer.ip) },
                        onPressEnd = viewModel::releaseToTalk,
                        onRemove = { viewModel.removeWalkieTalkiePeer(peer) },
                    )
                }
            }
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

    SettingGroup(title = "Floating button route") {
        Text(
            "The floating push-to-talk button outside Settings still follows one default route.",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )

        Spacer(Modifier.height(10.dp))

        RouteRow(
            label = "All devices",
            subtitle = if (settings.walkieTalkiePeers.isEmpty()) {
                "Broadcast to every saved device once you add them."
            } else {
                "Broadcast to every saved device."
            },
            selected = settings.walkieTalkieTarget == WALKIE_TALKIE_TARGET_ALL,
            onSelect = { viewModel.setWalkieTalkieTarget(WALKIE_TALKIE_TARGET_ALL) },
        )

        settings.walkieTalkiePeers.forEach { peer ->
            RouteRow(
                label = peer.name,
                subtitle = peer.ip,
                selected = settings.walkieTalkieTarget == peer.ip,
                onSelect = { viewModel.setWalkieTalkieTarget(peer.ip) },
            )
        }
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Incoming chime") {
        SettingRow(
            title = "Play incoming alert",
            subtitle = "Optional short sound before an incoming discussion starts",
        ) {
            Switch(
                checked = settings.walkieTalkieIncomingChimeEnabled,
                onCheckedChange = viewModel::setWalkieTalkieIncomingChimeEnabled,
                colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
            )
        }

        Spacer(Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WalkieTalkieChimes.options.forEach { option ->
                ChimeOptionRow(
                    label = option.label,
                    description = option.description,
                    selected = settings.walkieTalkieIncomingChime == option.key,
                    muted = !settings.walkieTalkieIncomingChimeEnabled,
                    onSelect = { viewModel.setWalkieTalkieIncomingChime(option.key) },
                    onTest = { viewModel.previewWalkieTalkieIncomingChime(option.key) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            if (settings.walkieTalkieIncomingChimeEnabled) {
                "Test any option here. The selected chime will play once when a new incoming burst begins."
            } else {
                "Incoming chimes are muted. You can still use Test to audition sounds before turning them back on."
            },
            style = MDTheme.type.caption,
            color = MDTheme.colors.textTertiary,
        )
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Mic boost") {
        Text(
            WalkieTalkieMicBoost.label(settings.walkieTalkieMicBoostPercent),
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
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

@Composable
private fun BroadcastTalkRow(
    peerCount: Int,
    isTransmitting: Boolean,
    isSpeaking: Boolean,
    speakingLabel: String?,
    enabled: Boolean,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MDTheme.colors.backgroundElevated)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MDTheme.colors.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Wifi, contentDescription = null, tint = MDTheme.colors.accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("All devices", style = MDTheme.type.body, color = MDTheme.colors.textPrimary)
            Text(
                when {
                    isTransmitting -> "Broadcasting now"
                    isSpeaking && speakingLabel != null -> "Incoming from $speakingLabel"
                    peerCount > 0 -> "$peerCount saved device" + if (peerCount == 1) "" else "s"
                    else -> "Add a device to broadcast to everyone"
                },
                style = MDTheme.type.caption,
                color = if (isTransmitting || isSpeaking) MDTheme.colors.accent else MDTheme.colors.textTertiary,
            )
        }
        PttButton(
            isTransmitting = isTransmitting,
            isSpeaking = isSpeaking,
            enabled = enabled && peerCount > 0,
            onPressStart = onPressStart,
            onPressEnd = onPressEnd,
            buttonSize = 58.dp,
        )
    }
}

@Composable
private fun TalkDeviceRow(
    peer: WalkieTalkiePeer,
    isNearby: Boolean,
    isDefaultTarget: Boolean,
    isSpeaking: Boolean,
    isTransmitting: Boolean,
    talkEnabled: Boolean,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MDTheme.colors.backgroundElevated)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MDTheme.colors.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            Text(peer.initials(), style = MDTheme.type.caption, color = MDTheme.colors.textPrimary)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(peer.name, style = MDTheme.type.body, color = MDTheme.colors.textPrimary)
            Text(peer.ip, style = MDTheme.type.caption, color = MDTheme.colors.textTertiary)
            Text(
                buildPeerStatus(isNearby = isNearby, isDefaultTarget = isDefaultTarget, isSpeaking = isSpeaking, isTransmitting = isTransmitting),
                style = MDTheme.type.caption,
                color = if (isSpeaking || isTransmitting) MDTheme.colors.accent else MDTheme.colors.textSecondary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PttButton(
                isTransmitting = isTransmitting,
                isSpeaking = isSpeaking,
                enabled = talkEnabled,
                onPressStart = onPressStart,
                onPressEnd = onPressEnd,
                buttonSize = 56.dp,
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Remove", tint = MDTheme.colors.textTertiary)
            }
        }
    }
}

private fun buildPeerStatus(
    isNearby: Boolean,
    isDefaultTarget: Boolean,
    isSpeaking: Boolean,
    isTransmitting: Boolean,
): String {
    val parts = mutableListOf<String>()
    when {
        isTransmitting -> parts += "Talking now"
        isSpeaking -> parts += "Speaking now"
        else -> parts += "Hold to talk directly"
    }
    if (isNearby) parts += "Nearby now"
    if (isDefaultTarget) parts += "Default floating button route"
    return parts.joinToString(" - ")
}

@Composable
private fun RouteRow(label: String, subtitle: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onSelect)
            .padding(vertical = 8.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(selectedColor = MDTheme.colors.accent),
        )
        Column(Modifier.weight(1f)) {
            Text(label, style = MDTheme.type.body, color = MDTheme.colors.textPrimary)
            Text(subtitle, style = MDTheme.type.caption, color = MDTheme.colors.textTertiary)
        }
    }
}

@Composable
private fun ChimeOptionRow(
    label: String,
    description: String,
    selected: Boolean,
    muted: Boolean,
    onSelect: () -> Unit,
    onTest: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MDTheme.colors.backgroundElevated)
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(selectedColor = MDTheme.colors.accent),
        )
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MDTheme.type.body, color = MDTheme.colors.textPrimary)
            Text(
                if (muted && selected) "$description - selected but muted" else description,
                style = MDTheme.type.caption,
                color = MDTheme.colors.textTertiary,
            )
        }
        Button(
            onClick = onTest,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selected) MDTheme.colors.accent else MDTheme.colors.surfaceElevated,
                contentColor = if (selected) MDTheme.colors.onAccent else MDTheme.colors.textPrimary,
            ),
        ) {
            Icon(Icons.Filled.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Test")
        }
    }
}

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
        Text("Searching for nearby devices...", style = MDTheme.type.settingSubtitle, color = MDTheme.colors.textTertiary)
    }
}
