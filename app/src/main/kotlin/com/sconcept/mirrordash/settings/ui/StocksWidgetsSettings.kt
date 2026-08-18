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
import com.sconcept.mirrordash.clock.StocksWidget
import com.sconcept.mirrordash.settings.SettingsUiState
import com.sconcept.mirrordash.settings.SettingsViewModel
import com.sconcept.mirrordash.ui.theme.ClockColorPresets
import com.sconcept.mirrordash.ui.theme.MDTheme

@Composable
fun StocksWidgetsSettingsContent(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    WidgetListEditor(
        items = uiState.settings.stocksWidgets,
        itemId = { it.id },
        emptyLabel = "No stocks widgets yet.",
        addLabel = "Add stocks widget",
        onAdd = viewModel::addStocksWidget,
        onDelete = viewModel::removeStocksWidget,
        row = { widget, onClick, onDelete -> StocksWidgetRow(widget, onClick, onDelete) },
        editor = { widget, onBack, onDelete ->
            StocksWidgetEditor(
                widget = widget,
                onBack = onBack,
                onChange = { transform -> viewModel.updateStocksWidget(widget.id, transform) },
                onDelete = onDelete,
                downloadedFonts = uiState.settings.downloadedClockFonts,
            )
        },
    )
}

@Composable
private fun StocksWidgetRow(widget: StocksWidget, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 12.dp),
    ) {
        Text(
            "Ticker",
            style = MDTheme.type.settingTitle,
            color = MDTheme.colors.accent,
        )
        Spacer(Modifier.width(14.dp))
        Text(
            widget.symbols.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "No symbols",
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
private fun StocksWidgetEditor(
    widget: StocksWidget,
    onBack: () -> Unit,
    onChange: ((StocksWidget) -> StocksWidget) -> Unit,
    onDelete: () -> Unit,
    downloadedFonts: List<com.sconcept.mirrordash.clock.DownloadedClockFont>,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to list", tint = MDTheme.colors.textPrimary)
        }
        Spacer(Modifier.width(4.dp))
        Text("Edit stocks widget", style = MDTheme.type.settingTitle, color = MDTheme.colors.textPrimary)
    }
    Spacer(Modifier.height(16.dp))

    SettingGroup(title = "Symbols") {
        Text(
            "Comma-separated, e.g. AAPL, MSFT, TSLA",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(8.dp))
        BufferedTextField(
            persistedValue = widget.symbols.joinToString(", "),
            onValueChange = { value ->
                onChange { it.copy(symbols = value.split(",").map { s -> s.trim() }.filter { s -> s.isNotBlank() }) }
            },
            placeholder = { Text("AAPL, MSFT") },
            modifier = Modifier.fillMaxWidth(),
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
        Text("Delete this stocks widget", color = MDTheme.colors.danger, fontWeight = FontWeight.Medium)
    }
}
