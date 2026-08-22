package com.sconcept.mirrordash.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sconcept.mirrordash.clock.IconWidget
import com.sconcept.mirrordash.clock.WeatherIconCatalog
import com.sconcept.mirrordash.clock.WeatherIconGlyph
import com.sconcept.mirrordash.settings.SettingsUiState
import com.sconcept.mirrordash.settings.SettingsViewModel
import com.sconcept.mirrordash.ui.theme.ClockColorPresets
import com.sconcept.mirrordash.ui.theme.MDTheme

@Composable
fun IconWidgetsSettingsContent(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    WidgetListEditor(
        items = uiState.settings.iconWidgets,
        itemId = { it.id },
        emptyLabel = "No icon widgets yet.",
        addLabel = "Add icon widget",
        onAdd = viewModel::addIconWidget,
        onDelete = viewModel::removeIconWidget,
        row = { widget, onClick, onDelete -> IconWidgetRow(widget, onClick, onDelete) },
        editor = { widget, onBack, onDelete ->
            IconWidgetEditor(widget, onBack, { transform -> viewModel.updateIconWidget(widget.id, transform) }, onDelete)
        },
    )
}

@Composable
private fun IconWidgetRow(widget: IconWidget, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(12.dp),
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(Color(widget.colorArgb).copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            WeatherIconGlyph(widget.iconName, 30, Color(widget.colorArgb))
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(WeatherIconCatalog.label(widget.iconName), style = MDTheme.type.settingTitle, color = MDTheme.colors.textPrimary)
            Text("${widget.sizeSp}sp · Weather Icons", style = MDTheme.type.settingSubtitle, color = MDTheme.colors.textSecondary)
        }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Close, "Delete", tint = MDTheme.colors.textTertiary) }
    }
}

@Composable
private fun IconWidgetEditor(widget: IconWidget, onBack: () -> Unit, onChange: ((IconWidget) -> IconWidget) -> Unit, onDelete: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val matching = remember(query) {
        WeatherIconCatalog.all.filter { it.contains(query.trim(), ignoreCase = true) }.take(30)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to list", tint = MDTheme.colors.textPrimary) }
        Spacer(Modifier.width(4.dp))
        Text("Edit icon widget", style = MDTheme.type.settingTitle, color = MDTheme.colors.textPrimary)
    }
    Spacer(Modifier.height(16.dp))

    SettingGroup(title = "Selected icon") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(76.dp).clip(RoundedCornerShape(14.dp)).background(Color(widget.colorArgb).copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                WeatherIconGlyph(widget.iconName, 54, Color(widget.colorArgb))
            }
            Column(Modifier.padding(start = 16.dp)) {
                Text(WeatherIconCatalog.label(widget.iconName), style = MDTheme.type.settingTitle, color = MDTheme.colors.textPrimary)
                Text("${WeatherIconCatalog.all.size} bundled Weather Icons", style = MDTheme.type.settingSubtitle, color = MDTheme.colors.textSecondary)
            }
        }
    }
    Spacer(Modifier.height(24.dp))

    SettingGroup(title = "Find an icon") {
        BufferedTextField(
            persistedValue = query,
            onValueChange = { query = it },
            placeholder = { Text("Search: rain, moon, wind, warning…") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        if (matching.isEmpty()) {
            Text("No matching Weather Icons.", style = MDTheme.type.settingSubtitle, color = MDTheme.colors.textSecondary)
        } else {
            matching.forEach { name ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onChange { it.copy(iconName = name) } }.padding(vertical = 8.dp, horizontal = 10.dp),
                ) {
                    WeatherIconGlyph(name, 28, if (name == widget.iconName) MDTheme.colors.accent else MDTheme.colors.textPrimary)
                    Text(WeatherIconCatalog.label(name), Modifier.padding(start = 14.dp), style = MDTheme.type.body, color = MDTheme.colors.textPrimary)
                }
            }
            if (matching.size == 30) Text("Refine the search to narrow the full catalog.", style = MDTheme.type.settingSubtitle, color = MDTheme.colors.textSecondary)
        }
    }
    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Size") {
        Text("${widget.sizeSp}sp", style = MDTheme.type.settingSubtitle, color = MDTheme.colors.textSecondary)
        Slider(widget.sizeSp.toFloat(), { onChange { item -> item.copy(sizeSp = it.toInt()) } }, valueRange = 16f..800f, colors = SliderDefaults.colors(thumbColor = MDTheme.colors.accent, activeTrackColor = MDTheme.colors.accent))
    }
    Spacer(Modifier.height(28.dp))
    SettingGroup(title = "Color") {
        ColorSwatchRow(ClockColorPresets, Color(widget.colorArgb), { color -> onChange { it.copy(colorArgb = color.toArgb()) } })
    }
    Spacer(Modifier.height(28.dp))
    TextButton(onClick = onDelete) { Text("Delete this icon widget", color = MDTheme.colors.danger, fontWeight = FontWeight.Medium) }
}
