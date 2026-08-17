package com.sconcept.mirrordash.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sconcept.mirrordash.ui.theme.MDTheme

/** Shared "list of rows, tap a row to open an in-place editor, delete per row, Add at the
 * bottom" shell - the part every Clock-page widget-type settings screen (weather, text,
 * calendar, tasks, stocks, news) duplicated before this was pulled out. Owns which item is being
 * edited itself so each call site only supplies row/editor content and the add/delete actions. */
@Composable
internal fun <T> WidgetListEditor(
    items: List<T>,
    itemId: (T) -> String,
    emptyLabel: String,
    addLabel: String,
    onAdd: () -> Unit,
    onDelete: (id: String) -> Unit,
    row: @Composable (item: T, onClick: () -> Unit, onDelete: () -> Unit) -> Unit,
    editor: @Composable (item: T, onBack: () -> Unit, onDelete: () -> Unit) -> Unit,
) {
    var editingId by remember { mutableStateOf<String?>(null) }
    val editing = items.firstOrNull { itemId(it) == editingId }

    if (editing != null) {
        editor(
            editing,
            { editingId = null },
            {
                onDelete(itemId(editing))
                editingId = null
            },
        )
        return
    }

    if (items.isEmpty()) {
        Text(emptyLabel, style = MDTheme.type.settingSubtitle, color = MDTheme.colors.textTertiary)
        Spacer(Modifier.height(16.dp))
    } else {
        Column {
            items.forEach { item ->
                row(item, { editingId = itemId(item) }, { onDelete(itemId(item)) })
                Spacer(Modifier.height(2.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    TextButton(onClick = onAdd) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = MDTheme.colors.accent)
        Spacer(Modifier.width(6.dp))
        Text(addLabel, color = MDTheme.colors.accent)
    }
}
