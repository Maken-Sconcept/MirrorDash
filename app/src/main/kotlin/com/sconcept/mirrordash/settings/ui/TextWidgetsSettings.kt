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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import com.sconcept.mirrordash.clock.CustomTextWidget
import com.sconcept.mirrordash.clock.TextWidgetAnimations
import com.sconcept.mirrordash.clock.TextWidgetFonts
import com.sconcept.mirrordash.settings.SettingsUiState
import com.sconcept.mirrordash.settings.SettingsViewModel
import com.sconcept.mirrordash.ui.theme.ClockColorPresets
import com.sconcept.mirrordash.ui.theme.MDTheme

/**
 * List of user-added text overlays with an in-place editor (tap a row to edit, tap back to
 * return to the list) - a third navigation depth within the one Settings sub-screen this lives
 * in, same pattern [com.sconcept.mirrordash.settings.ui.SettingsScreen] itself already uses for
 * list-to-detail. Position isn't set here - that's a drag directly on the Clock page, same as the
 * clock/weather clusters, so "where is this text" and "what does it look like" don't fight over
 * the same control surface.
 */
@Composable
fun TextWidgetsSettingsContent(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val widgets = uiState.settings.customTextWidgets
    var editingId by remember { mutableStateOf<String?>(null) }
    val editing = widgets.firstOrNull { it.id == editingId }

    if (editing != null) {
        TextWidgetEditor(
            widget = editing,
            onBack = { editingId = null },
            onChange = { transform -> viewModel.updateTextWidget(editing.id, transform) },
            onDelete = {
                viewModel.removeTextWidget(editing.id)
                editingId = null
            },
        )
        return
    }

    Text(
        "Long-press and drag any text on the Clock page to move it, same as the clock and weather.",
        style = MDTheme.type.settingSubtitle,
        color = MDTheme.colors.textSecondary,
    )
    Spacer(Modifier.height(20.dp))

    if (widgets.isEmpty()) {
        Text(
            "No text widgets yet.",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textTertiary,
        )
        Spacer(Modifier.height(16.dp))
    } else {
        Column {
            widgets.forEach { widget ->
                TextWidgetRow(
                    widget = widget,
                    onClick = { editingId = widget.id },
                    onDelete = { viewModel.removeTextWidget(widget.id) },
                )
                Spacer(Modifier.height(2.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    TextButton(onClick = viewModel::addTextWidget) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = MDTheme.colors.accent)
        Spacer(Modifier.width(6.dp))
        Text("Add text widget", color = MDTheme.colors.accent)
    }
}

@Composable
private fun TextWidgetRow(widget: CustomTextWidget, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(widget.colorArgb)),
        )
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                widget.text.ifBlank { "(empty text)" },
                style = MDTheme.type.settingTitle,
                color = MDTheme.colors.textPrimary,
                maxLines = 1,
            )
            Text(
                "${widget.fontSizeSp}sp · ${TextWidgetFonts.label(widget.fontFamily)} · ${TextWidgetAnimations.label(widget.animation)}",
                style = MDTheme.type.settingSubtitle,
                color = MDTheme.colors.textSecondary,
                maxLines = 1,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Close, contentDescription = "Delete", tint = MDTheme.colors.textTertiary)
        }
    }
}

@Composable
private fun TextWidgetEditor(
    widget: CustomTextWidget,
    onBack: () -> Unit,
    onChange: ((CustomTextWidget) -> CustomTextWidget) -> Unit,
    onDelete: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to list", tint = MDTheme.colors.textPrimary)
        }
        Spacer(Modifier.width(4.dp))
        Text("Edit text widget", style = MDTheme.type.settingTitle, color = MDTheme.colors.textPrimary)
    }
    Spacer(Modifier.height(16.dp))

    SettingGroup(title = "Text") {
        BufferedTextField(
            persistedValue = widget.text,
            onValueChange = { value -> onChange { it.copy(text = value) } },
            placeholder = { Text("Type something…") },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Size") {
        Text("${widget.fontSizeSp}sp", style = MDTheme.type.settingSubtitle, color = MDTheme.colors.textSecondary)
        Slider(
            value = widget.fontSizeSp.toFloat(),
            onValueChange = { value -> onChange { it.copy(fontSizeSp = value.toInt()) } },
            valueRange = 12f..160f,
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

    SettingGroup(title = "Font") {
        TextWidgetFonts.ALL.forEach { fontId ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            ) {
                RadioButton(
                    selected = widget.fontFamily == fontId,
                    onClick = { onChange { it.copy(fontFamily = fontId) } },
                    colors = RadioButtonDefaults.colors(selectedColor = MDTheme.colors.accent),
                )
                Text(
                    TextWidgetFonts.label(fontId),
                    style = MDTheme.type.body.copy(fontFamily = TextWidgetFonts.family(fontId)),
                    color = MDTheme.colors.textPrimary,
                )
            }
        }
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Animation") {
        TextWidgetAnimations.ALL.forEach { animId ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            ) {
                RadioButton(
                    selected = widget.animation == animId,
                    onClick = { onChange { it.copy(animation = animId) } },
                    colors = RadioButtonDefaults.colors(selectedColor = MDTheme.colors.accent),
                )
                Text(TextWidgetAnimations.label(animId), style = MDTheme.type.body, color = MDTheme.colors.textPrimary)
            }
        }
    }

    Spacer(Modifier.height(28.dp))

    TextButton(onClick = onDelete) {
        Text("Delete this text widget", color = MDTheme.colors.danger, fontWeight = FontWeight.Medium)
    }
}
