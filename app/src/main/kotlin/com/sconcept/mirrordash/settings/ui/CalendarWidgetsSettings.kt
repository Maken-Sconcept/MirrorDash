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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sconcept.mirrordash.clock.CalendarWidget
import com.sconcept.mirrordash.settings.SettingsUiState
import com.sconcept.mirrordash.settings.SettingsViewModel
import com.sconcept.mirrordash.ui.theme.ClockColorPresets
import com.sconcept.mirrordash.ui.theme.MDTheme

@Composable
fun CalendarWidgetsSettingsContent(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    onRequestCalendarAccess: () -> Unit,
) {
    WidgetListEditor(
        items = uiState.settings.calendarWidgets,
        itemId = { it.id },
        emptyLabel = "No calendar widgets yet.",
        addLabel = "Add calendar widget",
        onAdd = viewModel::addCalendarWidget,
        onDelete = viewModel::removeCalendarWidget,
        row = { widget, onClick, onDelete -> CalendarWidgetRow(widget, onClick, onDelete) },
        editor = { widget, onBack, onDelete ->
            CalendarWidgetEditor(
                widget = widget,
                onBack = onBack,
                onChange = { transform -> viewModel.updateCalendarWidget(widget.id, transform) },
                onDelete = onDelete,
                onRequestCalendarAccess = onRequestCalendarAccess,
            )
        },
    )
}

@Composable
private fun CalendarWidgetRow(widget: CalendarWidget, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 12.dp),
    ) {
        Text(
            "Agenda",
            style = MDTheme.type.settingTitle,
            color = MDTheme.colors.accent,
        )
        Spacer(Modifier.width(14.dp))
        Text(
            "Next ${widget.itemCount}, ${widget.lookaheadDays}d ahead",
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
private fun CalendarWidgetEditor(
    widget: CalendarWidget,
    onBack: () -> Unit,
    onChange: ((CalendarWidget) -> CalendarWidget) -> Unit,
    onDelete: () -> Unit,
    onRequestCalendarAccess: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to list", tint = MDTheme.colors.textPrimary)
        }
        Spacer(Modifier.width(4.dp))
        Text("Edit calendar widget", style = MDTheme.type.settingTitle, color = MDTheme.colors.textPrimary)
    }
    Spacer(Modifier.height(16.dp))

    SettingGroup(title = "Calendar access") {
        Text(
            "Reads events from every visible calendar on this device.",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onRequestCalendarAccess) {
            Text("Grant calendar access", color = MDTheme.colors.accent)
        }
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Events shown") {
        Text("${widget.itemCount}", style = MDTheme.type.settingSubtitle, color = MDTheme.colors.textSecondary)
        Slider(
            value = widget.itemCount.toFloat(),
            onValueChange = { value -> onChange { it.copy(itemCount = value.toInt().coerceIn(1, 10)) } },
            valueRange = 1f..10f,
            steps = 8,
            colors = SliderDefaults.colors(thumbColor = MDTheme.colors.accent, activeTrackColor = MDTheme.colors.accent),
        )
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Look ahead") {
        Text("${widget.lookaheadDays} days", style = MDTheme.type.settingSubtitle, color = MDTheme.colors.textSecondary)
        Slider(
            value = widget.lookaheadDays.toFloat(),
            onValueChange = { value -> onChange { it.copy(lookaheadDays = value.toInt().coerceIn(1, 14)) } },
            valueRange = 1f..14f,
            steps = 12,
            colors = SliderDefaults.colors(thumbColor = MDTheme.colors.accent, activeTrackColor = MDTheme.colors.accent),
        )
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Size") {
        Text("${widget.fontSizeSp}sp", style = MDTheme.type.settingSubtitle, color = MDTheme.colors.textSecondary)
        Slider(
            value = widget.fontSizeSp.toFloat(),
            onValueChange = { value -> onChange { it.copy(fontSizeSp = value.toInt().coerceIn(12, 40)) } },
            valueRange = 12f..40f,
            colors = SliderDefaults.colors(thumbColor = MDTheme.colors.accent, activeTrackColor = MDTheme.colors.accent),
        )
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Color") {
        ColorSwatchRow(
            colors = ClockColorPresets,
            selected = Color(widget.colorArgb),
            onSelect = { color -> onChange { it.copy(colorArgb = color.toArgb()) } },
        )
    }

    Spacer(Modifier.height(28.dp))

    TextButton(onClick = onDelete) {
        Text("Delete this calendar widget", color = MDTheme.colors.danger, fontWeight = FontWeight.Medium)
    }
}
