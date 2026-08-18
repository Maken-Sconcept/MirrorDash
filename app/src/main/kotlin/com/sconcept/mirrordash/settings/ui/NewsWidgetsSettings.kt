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
import com.sconcept.mirrordash.clock.NewsWidget
import com.sconcept.mirrordash.settings.SettingsUiState
import com.sconcept.mirrordash.settings.SettingsViewModel
import com.sconcept.mirrordash.ui.theme.ClockColorPresets
import com.sconcept.mirrordash.ui.theme.MDTheme

@Composable
fun NewsWidgetsSettingsContent(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    WidgetListEditor(
        items = uiState.settings.newsWidgets,
        itemId = { it.id },
        emptyLabel = "No news widgets yet.",
        addLabel = "Add news widget",
        onAdd = viewModel::addNewsWidget,
        onDelete = viewModel::removeNewsWidget,
        row = { widget, onClick, onDelete -> NewsWidgetRow(widget, onClick, onDelete) },
        editor = { widget, onBack, onDelete ->
            NewsWidgetEditor(
                widget = widget,
                onBack = onBack,
                onChange = { transform -> viewModel.updateNewsWidget(widget.id, transform) },
                onDelete = onDelete,
                downloadedFonts = uiState.settings.downloadedClockFonts,
            )
        },
    )
}

@Composable
private fun NewsWidgetRow(widget: NewsWidget, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 12.dp),
    ) {
        Text(
            "News",
            style = MDTheme.type.settingTitle,
            color = MDTheme.colors.accent,
        )
        Spacer(Modifier.width(14.dp))
        Text(
            widget.feedUrl.ifBlank { "No feed configured" },
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Close, contentDescription = "Delete", tint = MDTheme.colors.textTertiary)
        }
    }
}

@Composable
private fun NewsWidgetEditor(
    widget: NewsWidget,
    onBack: () -> Unit,
    onChange: ((NewsWidget) -> NewsWidget) -> Unit,
    onDelete: () -> Unit,
    downloadedFonts: List<com.sconcept.mirrordash.clock.DownloadedClockFont>,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to list", tint = MDTheme.colors.textPrimary)
        }
        Spacer(Modifier.width(4.dp))
        Text("Edit news widget", style = MDTheme.type.settingTitle, color = MDTheme.colors.textPrimary)
    }
    Spacer(Modifier.height(16.dp))

    SettingGroup(title = "Feed URL") {
        BufferedTextField(
            persistedValue = widget.feedUrl,
            onValueChange = { value -> onChange { it.copy(feedUrl = value) } },
            placeholder = { Text("https://example.com/feed.xml") },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Headlines shown") {
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

    SettingGroup(title = "Font") {
        WidgetFontPicker(
            selectedFontId = widget.fontId,
            downloadedFonts = downloadedFonts,
            onSelect = { fontId -> onChange { it.copy(fontId = fontId) } },
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
        Text("Delete this news widget", color = MDTheme.colors.danger, fontWeight = FontWeight.Medium)
    }
}
