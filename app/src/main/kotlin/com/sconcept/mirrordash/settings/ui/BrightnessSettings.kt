package com.sconcept.mirrordash.settings.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sconcept.mirrordash.brightness.BacklightController
import com.sconcept.mirrordash.brightness.BrightnessMath
import com.sconcept.mirrordash.settings.BRIGHTNESS_DIM_TARGET_TEXT_ONLY
import com.sconcept.mirrordash.settings.BRIGHTNESS_DIM_TARGET_WHOLE_SCREEN
import com.sconcept.mirrordash.settings.SettingsUiState
import com.sconcept.mirrordash.settings.SettingsViewModel
import com.sconcept.mirrordash.ui.theme.MDTheme

/**
 * Real backlight level (0-255, snapped to [BrightnessMath.QUICK_CONTROL_STEPS]'s 25 perceptually
 * spaced levels), an independent manual "extra dim" overlay layer on top of it, and whether that
 * overlay dims the whole screen or just the Clock page's text - see [MirrorDashActivity]'s doc
 * comments on `BacklightController`/`BrightnessDimOverlay` for how the three layers combine.
 * Ported from BerthierOptions' proven curves/timings, adapted to not need a system overlay
 * permission since MirrorDash is the whole screen almost always (see the brightness plan).
 */
@Composable
fun BrightnessSettingsContent(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    onRequestWriteSettingsAccess: () -> Unit,
) {
    val settings = uiState.settings
    val context = LocalContext.current
    val steps = BrightnessMath.QUICK_CONTROL_STEPS
    val stepIndex = BrightnessMath.stepIndexFor(settings.brightnessLevel255)

    SettingGroup(title = "Backlight level") {
        Text(
            if (settings.brightnessLevel255 == 0) "Off" else "${settings.brightnessLevel255 * 100 / 255}%",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        Slider(
            value = stepIndex.toFloat(),
            onValueChange = { viewModel.setBrightnessLevel(steps[it.toInt().coerceIn(0, steps.lastIndex)]) },
            valueRange = 0f..steps.lastIndex.toFloat(),
            steps = steps.size - 2,
            colors = SliderDefaults.colors(thumbColor = MDTheme.colors.accent, activeTrackColor = MDTheme.colors.accent),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "At 0 this turns the backlight fully off, not just dim - the mirror can look almost transparent at night.",
            style = MDTheme.type.caption,
            color = MDTheme.colors.textTertiary,
        )
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Extra dim layer") {
        Text(
            "${settings.brightnessExtraDimPercent}% - darkens further than the backlight alone can go.",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        Slider(
            value = settings.brightnessExtraDimPercent.toFloat(),
            onValueChange = { viewModel.setBrightnessExtraDim(it.toInt()) },
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(thumbColor = MDTheme.colors.accent, activeTrackColor = MDTheme.colors.accent),
        )
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Dim target") {
        listOf(
            BRIGHTNESS_DIM_TARGET_WHOLE_SCREEN to "Whole screen",
            BRIGHTNESS_DIM_TARGET_TEXT_ONLY to "Text only (Clock page)",
        ).forEach { (value, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            ) {
                RadioButton(
                    selected = settings.brightnessDimTarget == value,
                    onClick = { viewModel.setBrightnessDimTarget(value) },
                    colors = RadioButtonDefaults.colors(selectedColor = MDTheme.colors.accent),
                )
                Text(label, style = MDTheme.type.body, color = MDTheme.colors.textPrimary)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "\"Text only\" fades the clock, weather, and any text widgets while keeping the background lit - it only applies on the Clock page; every other page always dims the whole screen.",
            style = MDTheme.type.caption,
            color = MDTheme.colors.textTertiary,
        )
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Failsafe") {
        Text(
            "If the screen ever goes darker than you meant, hold the Volume Up button, or touch and hold anywhere on the screen, for 8 seconds to instantly reset brightness to full.",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
    }

    if (!BacklightController.hasWriteSettingsPermission(context)) {
        Spacer(Modifier.height(28.dp))
        SettingGroup(title = "System-wide brightness (optional)") {
            Text(
                "Backlight dimming already works without this - grant \"Modify system settings\" only if you also want the change to stick system-wide, not just while MirrorDash is on screen.",
                style = MDTheme.type.settingSubtitle,
                color = MDTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onRequestWriteSettingsAccess,
                colors = ButtonDefaults.buttonColors(containerColor = MDTheme.colors.accent, contentColor = MDTheme.colors.onAccent),
            ) {
                Text("Grant permission")
            }
        }
    }
}
