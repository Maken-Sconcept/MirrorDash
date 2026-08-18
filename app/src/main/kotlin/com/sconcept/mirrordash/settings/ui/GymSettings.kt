package com.sconcept.mirrordash.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sconcept.mirrordash.gym.FitnessConnectionState
import com.sconcept.mirrordash.gym.FitnessDeviceKind
import com.sconcept.mirrordash.gym.GymHudMode
import com.sconcept.mirrordash.settings.SettingsUiState
import com.sconcept.mirrordash.settings.SettingsViewModel
import com.sconcept.mirrordash.ui.theme.MDTheme

@Composable
fun GymSettingsContent(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val settings = uiState.settings
    val gym = settings.gymFeatureSettings

    SettingRow(
        title = "Show Gym & Workouts tab",
        subtitle = "Adds the premium mirror-first gym surface as an optional launcher page",
    ) {
        Switch(
            checked = settings.gymEnabled,
            onCheckedChange = viewModel::setGymEnabled,
            colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
        )
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Workout") {
        SettingRow(
            title = "Countdown",
            subtitle = "Show a 3-2-1 start before sessions and challenges",
        ) {
            Switch(
                checked = gym.countdownEnabled,
                onCheckedChange = viewModel::setGymCountdownEnabled,
                colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
            )
        }
        Spacer(Modifier.height(16.dp))
        SettingRow(
            title = "Workout sounds",
            subtitle = "Enable optional countdown, challenge, and achievement audio hooks",
        ) {
            Switch(
                checked = gym.soundsEnabled,
                onCheckedChange = viewModel::setGymSoundsEnabled,
                colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
            )
        }
    }

    Spacer(Modifier.height(24.dp))

    SettingGroup(title = "Display") {
        SettingRow(
            title = "Show heart rate",
            subtitle = "Keep HR visible in the live mirror HUD when available",
        ) {
            Switch(
                checked = gym.showHeartRate,
                onCheckedChange = viewModel::setGymShowHeartRate,
                colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
            )
        }
        Spacer(Modifier.height(16.dp))
        SettingRow(
            title = "Show calories",
            subtitle = "Display calorie totals when we have a direct or estimated source",
        ) {
            Switch(
                checked = gym.showCalories,
                onCheckedChange = viewModel::setGymShowCalories,
                colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
            )
        }
        Spacer(Modifier.height(16.dp))
        SettingRow(
            title = "Show score",
            subtitle = "Display points, combos, and challenge progression during a workout",
        ) {
            Switch(
                checked = gym.showScore,
                onCheckedChange = viewModel::setGymShowScore,
                colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text("HUD style", style = MDTheme.type.settingTitle, color = MDTheme.colors.textPrimary)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GymModeChip(
                label = "Minimal",
                selected = gym.hudMode == GymHudMode.MINIMAL,
                onClick = { viewModel.setGymHudMode(GymHudMode.MINIMAL) },
            )
            GymModeChip(
                label = "Expanded",
                selected = gym.hudMode == GymHudMode.EXPANDED,
                onClick = { viewModel.setGymHudMode(GymHudMode.EXPANDED) },
            )
        }
    }

    Spacer(Modifier.height(24.dp))

    SettingGroup(title = "Developer") {
        SettingRow(
            title = "Mock equipment",
            subtitle = "Drive the gym UI and session logic without standing on the bike",
        ) {
            Switch(
                checked = gym.mockDevicesEnabled,
                onCheckedChange = viewModel::setGymMockDevicesEnabled,
                colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
            )
        }

        Spacer(Modifier.height(18.dp))
        Text("Raw device debug", style = MDTheme.type.settingTitle, color = MDTheme.colors.textPrimary)
        Spacer(Modifier.height(8.dp))
        uiState.gymDebugDevices.forEach { device ->
            Surface(
                color = MDTheme.colors.surface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    when (device.state) {
                                        FitnessConnectionState.DISCONNECTED -> MDTheme.colors.textTertiary
                                        FitnessConnectionState.ERROR -> MDTheme.colors.danger
                                        else -> Color(0xFF7CF7B8)
                                    },
                                ),
                        )
                        Spacer(Modifier.size(10.dp))
                        Text(device.displayName, color = MDTheme.colors.textPrimary, style = MDTheme.type.settingTitle)
                        Spacer(Modifier.weight(1f))
                        Text(device.kind.name, color = MDTheme.colors.textTertiary, style = MDTheme.type.caption)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "State: ${device.state.name.lowercase().replaceFirstChar { it.uppercase() }} • Packet age: ${device.lastPacketAgeSeconds ?: "--"}s • Reconnects: ${device.reconnectCount}",
                        color = MDTheme.colors.textSecondary,
                        style = MDTheme.type.settingSubtitle,
                    )
                    Text(
                        when (device.kind) {
                            FitnessDeviceKind.CARDIO -> "Power ${device.lastTelemetry?.powerWatts?.toInt() ?: 0}W • Cadence ${device.lastTelemetry?.cadenceRpm?.toInt() ?: 0} RPM • Resistance ${device.lastTelemetry?.resistance?.toInt() ?: 0}"
                            FitnessDeviceKind.STRENGTH -> "Reps ${device.lastTelemetry?.repetitions ?: 0} • Load L/R ${device.lastTelemetry?.loadLeftKg ?: 0.0}/${device.lastTelemetry?.loadRightKg ?: 0.0} kg • Mode mock"
                            FitnessDeviceKind.HEART_RATE -> "Heart rate ${device.lastTelemetry?.heartRate ?: "--"} BPM"
                        },
                        color = MDTheme.colors.textTertiary,
                        style = MDTheme.type.caption,
                    )
                    device.errorMessage?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, color = MDTheme.colors.danger, style = MDTheme.type.caption)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { viewModel.connectGymDevice(device.deviceId) },
                            colors = ButtonDefaults.buttonColors(containerColor = MDTheme.colors.accent, contentColor = MDTheme.colors.onAccent),
                        ) {
                            Text(if (device.state == FitnessConnectionState.DISCONNECTED) "Connect" else "Refresh")
                        }
                        Button(
                            onClick = { viewModel.disconnectGymDevice(device.deviceId) },
                            colors = ButtonDefaults.buttonColors(containerColor = MDTheme.colors.surfaceElevated, contentColor = MDTheme.colors.textPrimary),
                        ) {
                            Text("Disconnect")
                        }
                    }
                }
            }
        }
        uiState.gymActiveSession?.let { session ->
            Text(
                "Active session: ${session.workoutType.name.replace('_', ' ')} • ${session.elapsedSeconds}s elapsed • ${session.players.sumOf { it.score }} total pts",
                color = MDTheme.colors.textSecondary,
                style = MDTheme.type.settingSubtitle,
            )
        }
    }
}

@Composable
private fun GymModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = if (selected) MDTheme.colors.accent.copy(alpha = 0.16f) else MDTheme.colors.surface,
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            label,
            color = if (selected) MDTheme.colors.accent else MDTheme.colors.textSecondary,
            style = MDTheme.type.caption,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}
