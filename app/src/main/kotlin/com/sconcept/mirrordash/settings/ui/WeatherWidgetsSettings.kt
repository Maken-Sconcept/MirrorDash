package com.sconcept.mirrordash.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sconcept.mirrordash.clock.WEATHER_WIDGET_FORECAST_DAILY
import com.sconcept.mirrordash.clock.WEATHER_WIDGET_FORECAST_HOURLY
import com.sconcept.mirrordash.clock.WEATHER_WIDGET_MODE_FORECAST_CARD
import com.sconcept.mirrordash.clock.WeatherWidget
import com.sconcept.mirrordash.clock.WeatherWidgetForecastModes
import com.sconcept.mirrordash.clock.WeatherWidgetModes
import com.sconcept.mirrordash.settings.SettingsUiState
import com.sconcept.mirrordash.settings.SettingsViewModel
import com.sconcept.mirrordash.ui.theme.MDTheme

@Composable
fun WeatherWidgetsSettingsContent(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val widgets = uiState.settings.weatherWidgets
    var editingId by remember { mutableStateOf<String?>(null) }
    val editing = widgets.firstOrNull { it.id == editingId }

    if (editing != null) {
        WeatherWidgetEditor(
            widget = editing,
            onBack = { editingId = null },
            onChange = { transform -> viewModel.updateWeatherWidget(editing.id, transform) },
            onDelete = {
                viewModel.removeWeatherWidget(editing.id)
                editingId = null
            },
        )
        return
    }

    if (widgets.isEmpty()) {
        Text(
            "No weather widgets yet.",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textTertiary,
        )
        Spacer(Modifier.height(16.dp))
    } else {
        widgets.forEach { widget ->
            WeatherWidgetRow(
                widget = widget,
                onClick = { editingId = widget.id },
                onDelete = { viewModel.removeWeatherWidget(widget.id) },
            )
            Spacer(Modifier.height(2.dp))
        }
        Spacer(Modifier.height(16.dp))
    }

    TextButton(onClick = viewModel::addWeatherWidget) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = MDTheme.colors.accent)
        Spacer(Modifier.width(6.dp))
        Text("Add weather widget", color = MDTheme.colors.accent)
    }
}

@Composable
private fun WeatherWidgetRow(widget: WeatherWidget, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 12.dp),
    ) {
        Text(
            if (widget.mode == WEATHER_WIDGET_MODE_FORECAST_CARD) "Card" else "Line",
            style = MDTheme.type.settingTitle,
            color = MDTheme.colors.accent,
        )
        Spacer(Modifier.width(14.dp))
        Text(
            weatherWidgetSummary(widget),
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Close, contentDescription = "Delete", tint = MDTheme.colors.textTertiary)
        }
    }
}

@Composable
private fun WeatherWidgetEditor(
    widget: WeatherWidget,
    onBack: () -> Unit,
    onChange: ((WeatherWidget) -> WeatherWidget) -> Unit,
    onDelete: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to list", tint = MDTheme.colors.textPrimary)
        }
        Spacer(Modifier.width(4.dp))
        Text("Edit weather widget", style = MDTheme.type.settingTitle, color = MDTheme.colors.textPrimary)
    }
    Spacer(Modifier.height(16.dp))

    SettingGroup(title = "Mode") {
        WeatherWidgetModes.ALL.forEach { mode ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
            ) {
                RadioButton(
                    selected = widget.mode == mode,
                    onClick = { onChange { it.copy(mode = mode) } },
                    colors = RadioButtonDefaults.colors(selectedColor = MDTheme.colors.accent),
                )
                Text(WeatherWidgetModes.label(mode), style = MDTheme.type.body, color = MDTheme.colors.textPrimary)
            }
        }
    }

    if (widget.mode == WEATHER_WIDGET_MODE_FORECAST_CARD) {
        Spacer(Modifier.height(28.dp))

        SettingGroup(title = "Forecast") {
            WeatherWidgetForecastModes.ALL.forEach { forecastMode ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                ) {
                    RadioButton(
                        selected = widget.forecastMode == forecastMode,
                        onClick = { onChange { it.copy(forecastMode = forecastMode) } },
                        colors = RadioButtonDefaults.colors(selectedColor = MDTheme.colors.accent),
                    )
                    Text(
                        WeatherWidgetForecastModes.label(forecastMode),
                        style = MDTheme.type.body,
                        color = MDTheme.colors.textPrimary,
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        val maxItems = if (widget.forecastMode == WEATHER_WIDGET_FORECAST_DAILY) 5 else 8
        val minItems = 1
        val clampedCount = widget.itemCount.coerceIn(minItems, maxItems)

        SettingGroup(title = "Visible items") {
            Text(
                if (widget.forecastMode == WEATHER_WIDGET_FORECAST_DAILY) {
                    "$clampedCount day${if (clampedCount == 1) "" else "s"}"
                } else {
                    "$clampedCount hour${if (clampedCount == 1) "" else "s"}"
                },
                style = MDTheme.type.settingSubtitle,
                color = MDTheme.colors.textSecondary,
            )
            Slider(
                value = clampedCount.toFloat(),
                onValueChange = { value -> onChange { it.copy(itemCount = value.toInt().coerceIn(minItems, maxItems)) } },
                valueRange = minItems.toFloat()..maxItems.toFloat(),
                steps = (maxItems - minItems - 1).coerceAtLeast(0),
                colors = SliderDefaults.colors(
                    thumbColor = MDTheme.colors.accent,
                    activeTrackColor = MDTheme.colors.accent,
                    inactiveTrackColor = MDTheme.colors.divider,
                ),
            )
        }
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Size") {
        Text(
            "${widget.scalePercent}%",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        Slider(
            value = widget.scalePercent.toFloat(),
            onValueChange = { value -> onChange { it.copy(scalePercent = value.toInt().coerceIn(70, 140)) } },
            valueRange = 70f..140f,
            steps = 13,
            colors = SliderDefaults.colors(
                thumbColor = MDTheme.colors.accent,
                activeTrackColor = MDTheme.colors.accent,
                inactiveTrackColor = MDTheme.colors.divider,
            ),
        )
    }

    Spacer(Modifier.height(28.dp))

    SettingRow(
        title = "Show location",
        subtitle = "Keep the city label in the widget header",
    ) {
        Switch(
            checked = widget.showLocation,
            onCheckedChange = { value -> onChange { it.copy(showLocation = value) } },
            colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
        )
    }

    Spacer(Modifier.height(28.dp))

    TextButton(onClick = onDelete) {
        Text("Delete this weather widget", color = MDTheme.colors.danger, fontWeight = FontWeight.Medium)
    }
}

private fun weatherWidgetSummary(widget: WeatherWidget): String {
    return when (widget.mode) {
        WEATHER_WIDGET_MODE_FORECAST_CARD -> {
            val unit = if (widget.forecastMode == WEATHER_WIDGET_FORECAST_DAILY) "day" else "hour"
            "Forecast card - ${widget.itemCount} $unit${if (widget.itemCount == 1) "" else "s"} - ${widget.scalePercent}%"
        }
        else -> "Simple line - ${widget.scalePercent}%"
    }
}
