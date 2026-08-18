package com.sconcept.mirrordash.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import com.sconcept.mirrordash.clock.TaskItem
import com.sconcept.mirrordash.clock.TasksWidget
import com.sconcept.mirrordash.clock.isDone
import com.sconcept.mirrordash.clock.isFileBacked
import com.sconcept.mirrordash.clock.taskStatusLabel
import com.sconcept.mirrordash.clock.withCompleted
import com.sconcept.mirrordash.clock.withStatus
import com.sconcept.mirrordash.settings.SettingsUiState
import com.sconcept.mirrordash.settings.SettingsViewModel
import com.sconcept.mirrordash.ui.theme.ClockColorPresets
import com.sconcept.mirrordash.ui.theme.MDTheme

@Composable
fun TasksWidgetsSettingsContent(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    WidgetListEditor(
        items = uiState.settings.tasksWidgets,
        itemId = { it.id },
        emptyLabel = "No tasks widgets yet.",
        addLabel = "Add tasks widget",
        onAdd = viewModel::addTasksWidget,
        onDelete = viewModel::removeTasksWidget,
        row = { widget, onClick, onDelete -> TasksWidgetRow(widget, onClick, onDelete) },
        editor = { widget, onBack, onDelete ->
            TasksWidgetEditor(
                widget = widget,
                onBack = onBack,
                onChange = { transform -> viewModel.updateTasksWidget(widget.id, transform) },
                onDelete = onDelete,
                downloadedFonts = uiState.settings.downloadedClockFonts,
            )
        },
    )
}

@Composable
private fun TasksWidgetRow(widget: TasksWidget, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 12.dp),
    ) {
        Text(
            "Tasks",
            style = MDTheme.type.settingTitle,
            color = MDTheme.colors.accent,
        )
        Spacer(Modifier.width(14.dp))
        val done = widget.items.count { it.isDone }
        Text(
            if (widget.isFileBacked) "NAS CSV: ${widget.csvFilePath}" else "${widget.items.size} items, $done done",
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
private fun TasksWidgetEditor(
    widget: TasksWidget,
    onBack: () -> Unit,
    onChange: ((TasksWidget) -> TasksWidget) -> Unit,
    onDelete: () -> Unit,
    downloadedFonts: List<com.sconcept.mirrordash.clock.DownloadedClockFont>,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to list", tint = MDTheme.colors.textPrimary)
        }
        Spacer(Modifier.width(4.dp))
        Text("Edit tasks widget", style = MDTheme.type.settingTitle, color = MDTheme.colors.textPrimary)
    }
    Spacer(Modifier.height(16.dp))

    SettingGroup(title = "Source") {
        BufferedTextField(
            persistedValue = widget.csvFilePath,
            onValueChange = { value -> onChange { it.copy(csvFilePath = value) } },
            placeholder = { Text("Optional NAS CSV path, e.g. Shared/tasks.csv") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Uses the same NAS credentials as Photorama. Leave blank for manual tasks. CSV headers supported: task, start, due, assignees, status, reminder, completed.",
            style = MDTheme.type.caption,
            color = MDTheme.colors.textSecondary,
        )
    }

    Spacer(Modifier.height(28.dp))

    if (!widget.isFileBacked) {
        SettingGroup(title = "Items") {
            widget.items.forEachIndexed { index, item ->
                TaskItemRow(
                    item = item,
                    onChange = { updated ->
                        onChange { widget2 -> widget2.copy(items = widget2.items.map { if (it.id == item.id) updated else it }) }
                    },
                    onDelete = {
                        onChange { widget2 -> widget2.copy(items = widget2.items.filterNot { it.id == item.id }) }
                    },
                )
                if (index != widget.items.lastIndex) Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(if (widget.items.isEmpty()) 0.dp else 10.dp))

            TextButton(
                onClick = {
                    onChange { widget2 -> widget2.copy(items = widget2.items + TaskItem(id = java.util.UUID.randomUUID().toString())) }
                },
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = MDTheme.colors.accent)
                Spacer(Modifier.width(6.dp))
                Text("Add item", color = MDTheme.colors.accent)
            }
        }
        Spacer(Modifier.height(28.dp))
    } else {
        SettingGroup(title = "Imported tasks") {
            Text(
                "The Clock page will read live task details from that NAS CSV. Clear the path above if you want to hand-edit tasks here again.",
                style = MDTheme.type.settingSubtitle,
                color = MDTheme.colors.textSecondary,
            )
        }
        Spacer(Modifier.height(28.dp))
    }

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
        Text("Delete this tasks widget", color = MDTheme.colors.danger, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun TaskItemRow(item: TaskItem, onChange: (TaskItem) -> Unit, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Checkbox(
            checked = item.isDone,
            onCheckedChange = { checked -> onChange(item.withCompleted(checked)) },
            colors = CheckboxDefaults.colors(checkedColor = MDTheme.colors.accent),
        )
        Column(modifier = Modifier.weight(1f)) {
            BufferedTextField(
                persistedValue = item.text,
                onValueChange = { value -> onChange(item.copy(text = value)) },
                placeholder = { Text("Task title") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            BufferedTextField(
                persistedValue = item.startsAt,
                onValueChange = { value -> onChange(item.copy(startsAt = value)) },
                placeholder = { Text("Start time") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            BufferedTextField(
                persistedValue = item.dueBy,
                onValueChange = { value -> onChange(item.copy(dueBy = value)) },
                placeholder = { Text("To be completed by") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            BufferedTextField(
                persistedValue = item.assignees,
                onValueChange = { value -> onChange(item.copy(assignees = value)) },
                placeholder = { Text("Person or people responsible") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            BufferedTextField(
                persistedValue = taskStatusLabel(item.status),
                onValueChange = { value -> onChange(item.withStatus(value)) },
                placeholder = { Text("Status (todo, in progress, blocked, done)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            BufferedTextField(
                persistedValue = item.reminder,
                onValueChange = { value -> onChange(item.copy(reminder = value)) },
                placeholder = { Text("Reminder") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Close, contentDescription = "Delete item", tint = MDTheme.colors.textTertiary)
        }
    }
}
