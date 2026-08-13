package com.sconcept.mirrordash.settings.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sconcept.mirrordash.airplay.AirPlayAuthMode
import com.sconcept.mirrordash.airplay.AirPlayMirrorPreset
import com.sconcept.mirrordash.settings.AIRPLAY_AUTH_MODE_PASSWORD
import com.sconcept.mirrordash.settings.SettingsUiState
import com.sconcept.mirrordash.settings.SettingsViewModel
import com.sconcept.mirrordash.ui.theme.MDTheme

/**
 * AirPlay receiver settings - ported behavior from BerthierOptions' `AirPlaySettingsActivity`
 * (device name / auth mode / password / mirror quality / HEVC), restructured as a single Settings
 * sub-screen matching every other section here rather than its own Activity, and backed by the
 * reactive [com.sconcept.mirrordash.airplay.AirPlayEngine] instead of restart-on-every-field-edit
 * imperative calls - flipping any field below just changes a setting, and the engine's own
 * settings-observing pipeline handles stopping/restarting the receiver.
 */
@Composable
fun AirPlaySettingsContent(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val settings = uiState.settings
    val airPlay = uiState.airPlayState

    SettingRow(title = "Enable AirPlay", subtitle = airPlay.statusLabel) {
        Switch(
            checked = settings.airplayEnabled,
            onCheckedChange = viewModel::setAirPlayEnabled,
            colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
        )
    }

    Spacer(Modifier.height(24.dp))

    if (airPlay.enabled && airPlay.receiverIp.isNotBlank()) {
        SettingGroup(title = "Address") {
            Text("${airPlay.receiverIp} · ${airPlay.deviceName}", style = MDTheme.type.settingSubtitle, color = MDTheme.colors.textSecondary)
        }
        Spacer(Modifier.height(24.dp))
    }

    SettingGroup(title = "Receiver name") {
        Text(
            "\"${airPlay.deviceName}\" is what your phone or laptop sees when picking an AirPlay " +
                "destination - change it under Launcher > Device name, since Walkie-Talkie uses the " +
                "same name.",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Pairing") {
        AirPlayAuthMode.ALL.forEach { mode ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            ) {
                RadioButton(
                    selected = settings.airplayAuthMode == mode,
                    onClick = { viewModel.setAirPlayAuthMode(mode) },
                    colors = RadioButtonDefaults.colors(selectedColor = MDTheme.colors.accent),
                )
                Text(AirPlayAuthMode.label(mode), style = MDTheme.type.body, color = MDTheme.colors.textPrimary)
            }
        }

        if (settings.airplayAuthMode == AIRPLAY_AUTH_MODE_PASSWORD) {
            Spacer(Modifier.height(10.dp))
            BufferedTextField(
                persistedValue = settings.airplayPassword,
                onValueChange = viewModel::setAirPlayPassword,
                placeholder = { Text("Password (min. 6 characters)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Mirror quality") {
        AirPlayMirrorPreset.ALL.forEach { preset ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            ) {
                RadioButton(
                    selected = settings.airplayMirrorPreset == preset,
                    onClick = { viewModel.setAirPlayMirrorPreset(preset) },
                    colors = RadioButtonDefaults.colors(selectedColor = MDTheme.colors.accent),
                )
                Text(AirPlayMirrorPreset.label(preset), style = MDTheme.type.body, color = MDTheme.colors.textPrimary)
            }
        }
    }

    Spacer(Modifier.height(24.dp))

    SettingRow(title = "Allow HEVC", subtitle = "Accept H.265 streams, not just H.264") {
        Switch(
            checked = settings.airplayAllowHevc,
            onCheckedChange = viewModel::setAirPlayAllowHevc,
            colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
        )
    }

    Spacer(Modifier.height(24.dp))

    SettingRow(title = "Show status on Clock", subtitle = "Small widget with connection, IP, and device name") {
        Switch(
            checked = settings.airplayShowClockWidget,
            onCheckedChange = viewModel::setAirPlayShowClockWidget,
            colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
        )
    }

    Spacer(Modifier.height(16.dp))

    Text(
        "Audio isn't played back yet - video mirrors, sound stays on the source device.",
        style = MDTheme.type.caption,
        color = MDTheme.colors.textTertiary,
    )
}
