package com.sconcept.mirrordash.clock

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sconcept.mirrordash.ui.theme.MDTheme

@Composable
internal fun TasksWidgetSurface(widget: TasksWidget, onToggleItem: (itemId: String, completed: Boolean) -> Unit) {
    val textColor = Color(widget.colorArgb)
    val shape = RoundedCornerShape(20.dp)
    val total = widget.items.size
    val done = widget.items.count { it.completed }
    val progress by animateFloatAsState(
        targetValue = if (total == 0) 0f else done / total.toFloat(),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
        label = "tasksProgress",
    )

    Box(
        modifier = Modifier
            .widthIn(min = 220.dp, max = 400.dp)
            .shadow(elevation = 18.dp, shape = shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.4f))
            .border(1.dp, textColor.copy(alpha = 0.12f), shape)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChecklistGlyphIcon(size = 18.dp, color = textColor.copy(alpha = 0.7f))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Tasks",
                    style = MDTheme.type.settingSubtitle.copy(fontSize = 14.sp, letterSpacing = 0.2.sp),
                    color = textColor.copy(alpha = 0.7f),
                )
                if (total > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "$done/$total",
                        style = MDTheme.type.caption.copy(fontSize = 12.sp),
                        color = textColor.copy(alpha = 0.5f),
                    )
                }
            }

            if (total > 0) {
                Spacer(Modifier.height(10.dp))
                ProgressTrack(progress = progress, color = textColor)
            }

            Spacer(Modifier.height(14.dp))

            if (widget.items.isEmpty()) {
                Text(
                    "No tasks yet",
                    style = MDTheme.type.caption.copy(fontSize = widget.fontSizeSp.sp * 0.78f),
                    color = textColor.copy(alpha = 0.55f),
                )
            } else {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                    widget.items.forEach { item ->
                        TaskRow(
                            item = item,
                            fontSizeSp = widget.fontSizeSp,
                            textColor = textColor,
                            onToggle = { onToggleItem(item.id, !item.completed) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressTrack(progress: Float, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color.copy(alpha = 0.14f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color.copy(alpha = 0.85f)),
        )
    }
}

@Composable
private fun TaskRow(item: TaskItem, fontSizeSp: Int, textColor: Color, onToggle: () -> Unit) {
    val checkScale by animateFloatAsState(
        targetValue = if (item.completed) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "taskCheckScale",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(onClick = onToggle),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (item.completed) textColor.copy(alpha = 0.16f) else Color.Transparent)
                .border(1.5.dp, textColor.copy(alpha = if (item.completed) 0.85f else 0.5f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier
                    .size(13.dp)
                    .scale(checkScale),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = item.text.ifBlank { "(empty task)" },
            style = MDTheme.type.settingSubtitle.copy(
                fontSize = fontSizeSp.sp,
                textDecoration = if (item.completed) TextDecoration.LineThrough else null,
            ),
            color = if (item.completed) textColor.copy(alpha = 0.48f) else textColor,
        )
    }
}
