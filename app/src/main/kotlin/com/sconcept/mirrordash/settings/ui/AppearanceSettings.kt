package com.sconcept.mirrordash.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sconcept.mirrordash.settings.CLOCK_BACKGROUND_MODE_PHOTORAMA
import com.sconcept.mirrordash.settings.SettingsUiState
import com.sconcept.mirrordash.settings.SettingsViewModel
import com.sconcept.mirrordash.ui.theme.ClockBackgroundPresets
import com.sconcept.mirrordash.ui.theme.ClockColorPresets
import com.sconcept.mirrordash.ui.theme.MDTheme

@Composable
fun AppearanceSettingsContent(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val settings = uiState.settings

    SettingGroup(title = "Clock size") {
        Text(
            "${settings.clockFontSizeSp}sp",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        Slider(
            value = settings.clockFontSizeSp.toFloat(),
            onValueChange = { viewModel.setClockFontSize(it.toInt()) },
            valueRange = 64f..180f,
            steps = 0,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = MDTheme.colors.accent,
                activeTrackColor = MDTheme.colors.accent,
                inactiveTrackColor = MDTheme.colors.divider,
            ),
        )
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Clock color") {
        ColorSwatchRow(
            colors = ClockColorPresets,
            selected = Color(settings.clockTextColorArgb),
            onSelect = { viewModel.setClockTextColor(it) },
        )
    }

    Spacer(Modifier.height(28.dp))

    val photoramaConfigured = settings.smbShare.isConfigured && settings.photoramaFolderPath.isNotBlank()
    val usePhotoramaBackground = settings.clockBackgroundMode == CLOCK_BACKGROUND_MODE_PHOTORAMA

    SettingGroup(title = "Background") {
        SettingRow(
            title = "Use Photorama as background",
            subtitle = if (photoramaConfigured) {
                "Show your live Photorama slideshow behind the clock instead of a solid color"
            } else {
                "Set up a NAS connection and folder in Photorama settings first"
            },
        ) {
            Switch(
                checked = usePhotoramaBackground,
                onCheckedChange = { viewModel.setClockBackgroundMode(it) },
                enabled = photoramaConfigured || usePhotoramaBackground,
                colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "Background color",
            style = MDTheme.type.settingTitle,
            color = MDTheme.colors.textPrimary,
            modifier = Modifier.alpha(if (usePhotoramaBackground) 0.4f else 1f),
        )
        Spacer(Modifier.height(10.dp))
        ColorSwatchRow(
            colors = ClockBackgroundPresets,
            selected = Color(settings.clockBackgroundColorArgb),
            onSelect = { viewModel.setClockBackgroundColor(it) },
            enabled = !usePhotoramaBackground,
        )
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Position") {
        Text(
            "Long-press and drag the clock or weather on the Clock page to move them.",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(10.dp))
        androidx.compose.material3.TextButton(onClick = viewModel::resetClockLayout) {
            Text("Reset position", color = MDTheme.colors.accent)
        }
    }
}

@Composable
internal fun SettingGroup(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MDTheme.type.settingTitle, color = MDTheme.colors.textPrimary)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
internal fun ColorSwatchRow(colors: List<Color>, selected: Color, onSelect: (Color) -> Unit, enabled: Boolean = true) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f),
    ) {
        colors.forEach { color ->
            val isSelected = color.value == selected.value
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (isSelected) 3.dp else 0.dp,
                        color = if (isSelected) MDTheme.colors.accent else Color.Transparent,
                        shape = CircleShape,
                    )
                    .clickable(enabled = enabled) { onSelect(color) },
            )
        }
    }
}
