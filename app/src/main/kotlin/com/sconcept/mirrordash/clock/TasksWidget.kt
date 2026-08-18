package com.sconcept.mirrordash.clock

import kotlinx.serialization.Serializable

const val TASK_STATUS_TODO = "todo"
const val TASK_STATUS_IN_PROGRESS = "in_progress"
const val TASK_STATUS_BLOCKED = "blocked"
const val TASK_STATUS_DONE = "done"

fun normalizedTaskStatus(raw: String): String = when (raw.trim().lowercase()) {
    "todo", "to do", "pending", "not_started", "not started", "open" -> TASK_STATUS_TODO
    "in_progress", "in progress", "doing", "started", "active" -> TASK_STATUS_IN_PROGRESS
    "blocked", "waiting" -> TASK_STATUS_BLOCKED
    "done", "complete", "completed", "closed" -> TASK_STATUS_DONE
    else -> TASK_STATUS_TODO
}

fun taskStatusLabel(raw: String): String = when (normalizedTaskStatus(raw)) {
    TASK_STATUS_IN_PROGRESS -> "In Progress"
    TASK_STATUS_BLOCKED -> "Blocked"
    TASK_STATUS_DONE -> "Done"
    else -> "To Do"
}

/** A single task item, either authored in Settings or read from an optional NAS CSV file. */
@Serializable
data class TaskItem(
    val id: String,
    val text: String = "",
    val startsAt: String = "",
    val dueBy: String = "",
    val assignees: String = "",
    val status: String = TASK_STATUS_TODO,
    val reminder: String = "",
    val completed: Boolean = false,
)

val TaskItem.isDone: Boolean
    get() = completed || normalizedTaskStatus(status) == TASK_STATUS_DONE

fun TaskItem.normalized(): TaskItem {
    val normalizedStatus = if (completed) TASK_STATUS_DONE else normalizedTaskStatus(status)
    return copy(
        status = normalizedStatus,
        completed = completed || normalizedStatus == TASK_STATUS_DONE,
    )
}

fun TaskItem.withCompleted(isCompleted: Boolean): TaskItem {
    val priorStatus = normalizedTaskStatus(status)
    return copy(
        completed = isCompleted,
        status = if (isCompleted) TASK_STATUS_DONE else if (priorStatus == TASK_STATUS_DONE) TASK_STATUS_TODO else priorStatus,
    )
}

fun TaskItem.withStatus(nextStatus: String): TaskItem {
    val normalizedStatus = normalizedTaskStatus(nextStatus)
    return copy(
        status = normalizedStatus,
        completed = normalizedStatus == TASK_STATUS_DONE,
    )
}

/** A draggable task widget on the Clock page. If [csvFilePath] is set, tasks are loaded from
 * that NAS-side CSV using the same SMB credentials Photorama already uses. */
@Serializable
data class TasksWidget(
    override val id: String,
    val items: List<TaskItem> = emptyList(),
    val csvFilePath: String = "",
    val fontSizeSp: Int = 18,
    val fontId: String = CLOCK_FONT_SYSTEM_DEFAULT,
    val colorArgb: Int = 0xFFF5F3EF.toInt(),
    override val anchorX: Float = 0.06f,
    override val anchorY: Float = 0.42f,
    override val rotationDegrees: Float = 0f,
) : AnchoredWidget

val TasksWidget.isFileBacked: Boolean
    get() = csvFilePath.isNotBlank()

fun defaultTasksWidget(
    id: String = java.util.UUID.randomUUID().toString(),
    anchorX: Float = 0.06f,
    anchorY: Float = 0.42f,
): TasksWidget = TasksWidget(id = id, anchorX = anchorX, anchorY = anchorY)
