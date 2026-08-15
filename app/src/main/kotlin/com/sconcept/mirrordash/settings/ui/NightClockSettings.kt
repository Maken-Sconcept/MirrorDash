package com.sconcept.mirrordash.settings.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sconcept.mirrordash.brightness.BrightnessMath
import com.sconcept.mirrordash.settings.SettingsUiState
import com.sconcept.mirrordash.settings.SettingsViewModel
import com.sconcept.mirrordash.ui.theme.MDTheme

/**
 * The hidden Night Clock tab (swipe past the Clock page - see [LauncherGestureHost]'s
 * `nightClockContent`) has its own brightness pair, deliberately separate from the main
 * Brightness section: that tab exists specifically to sit dimmer than wherever the main
 * brightness controls are normally left, so sharing one value would defeat the point. Only two
 * controls, not three - Night Clock's background is always solid black by design, so there's no
 * "whole screen vs text only" choice to make the way there is on the photo/color Clock
 * background; only the digits themselves need a dim knob beyond what the backlight can reach.
 */
@Composable
fun NightClockSettingsContent(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val settings = uiState.settings
    val steps = BrightnessMath.QUICK_CONTROL_STEPS
    val stepIndex = BrightnessMath.stepIndexFor(settings.nightClockBrightnessLevel255)

    SettingGroup(title = "Night Clock") {
        Text(
            "A hidden, always-dark clock tab - swipe past the Clock page to reach it, swipe back to leave. " +
                "Meant to sit dimmer than the mirror ever would during the day, like a bedside clock at night.",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Backlight level") {
        Text(
            if (settings.nightClockBrightnessLevel255 == 0) "Off" else "${settings.nightClockBrightnessLevel255 * 100 / 255}%",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        Slider(
            value = stepIndex.toFloat(),
            onValueChange = { viewModel.setNightClockBrightnessLevel(steps[it.toInt().coerceIn(0, steps.lastIndex)]) },
            valueRange = 0f..steps.lastIndex.toFloat(),
            steps = steps.size - 2,
            colors = SliderDefaults.colors(thumbColor = MDTheme.colors.accent, activeTrackColor = MDTheme.colors.accent),
        )
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Digit dim") {
        Text(
            "${settings.nightClockTextDimPercent}% - fades the time itself, on top of the backlight level above.",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        Slider(
            value = settings.nightClockTextDimPercent.toFloat(),
            onValueChange = { viewModel.setNightClockTextDimPercent(it.toInt()) },
            valueRange = 0f..90f,
            colors = SliderDefaults.colors(thumbColor = MDTheme.colors.accent, activeTrackColor = MDTheme.colors.accent),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Capped below 100% so the time never fades away completely.",
            style = MDTheme.type.caption,
            color = MDTheme.colors.textTertiary,
        )
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Failsafe") {
        Text(
            "The same 8-second Volume Up hold or touch-and-hold-anywhere failsafe from the main Brightness section works here too, in case Night Clock is ever dimmer than you meant.",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
    }
}
