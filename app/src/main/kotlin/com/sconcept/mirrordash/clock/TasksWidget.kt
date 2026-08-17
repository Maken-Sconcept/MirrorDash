package com.sconcept.mirrordash.clock

import kotlinx.serialization.Serializable

/** A single checklist item - user-authored, same spirit as [CustomTextWidget]'s freeform text
 * rather than synced from any external Tasks account (modern Android has no public Tasks
 * provider to sync from). */
@Serializable
data class TaskItem(
    val id: String,
    val text: String = "",
    val completed: Boolean = false,
)

/** A draggable checklist on the Clock page. Completion can be toggled with a plain tap directly
 * on the widget (not a long-press, so it never fights [DraggableAnchor]'s long-press-to-drag);
 * everything else (add/edit/reorder/delete items) happens in Settings. */
@Serializable
data class TasksWidget(
    override val id: String,
    val items: List<TaskItem> = emptyList(),
    val fontSizeSp: Int = 18,
    val colorArgb: Int = 0xFFF5F3EF.toInt(),
    override val anchorX: Float = 0.06f,
    override val anchorY: Float = 0.42f,
) : AnchoredWidget

fun defaultTasksWidget(
    id: String = java.util.UUID.randomUUID().toString(),
    anchorX: Float = 0.06f,
    anchorY: Float = 0.42f,
): TasksWidget = TasksWidget(id = id, anchorX = anchorX, anchorY = anchorY)
