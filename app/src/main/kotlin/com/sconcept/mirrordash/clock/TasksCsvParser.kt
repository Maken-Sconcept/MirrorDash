package com.sconcept.mirrordash.clock

import java.nio.charset.StandardCharsets
import java.util.UUID

/** Small CSV parser for task imports. Headered files can map richer task fields; headerless files
 * fall back to one task title per row. */
object TasksCsvParser {

    fun parse(raw: String, sourceKey: String): List<TaskItem> {
        val rows = parseRows(raw.removePrefix("\uFEFF"))
        if (rows.isEmpty()) return emptyList()

        val header = resolveHeader(rows.first())
        val body = if (header != null) rows.drop(1) else rows

        return body.mapIndexedNotNull { index, columns ->
            val item = if (header != null) {
                taskFromHeaderRow(header, columns, index, sourceKey)
            } else {
                taskFromSimpleRow(columns, index, sourceKey)
            }
            item?.normalized()
        }
    }

    private fun taskFromHeaderRow(header: HeaderMap, columns: List<String>, index: Int, sourceKey: String): TaskItem? {
        val text = valueAt(columns, header.textIndex).ifBlank { return null }
        val explicitId = valueAt(columns, header.idIndex)
        val status = valueAt(columns, header.statusIndex)
        val completedColumn = valueAt(columns, header.completedIndex)
        val completed = parseBooleanish(completedColumn) ?: (normalizedTaskStatus(status) == TASK_STATUS_DONE)
        return TaskItem(
            id = explicitId.ifBlank { stableId(sourceKey, index, columns) },
            text = text,
            startsAt = valueAt(columns, header.startsAtIndex),
            dueBy = valueAt(columns, header.dueByIndex),
            assignees = valueAt(columns, header.assigneesIndex),
            status = status.ifBlank {
                if (completed) TASK_STATUS_DONE else TASK_STATUS_TODO
            },
            reminder = valueAt(columns, header.reminderIndex),
            completed = completed,
        )
    }

    private fun taskFromSimpleRow(columns: List<String>, index: Int, sourceKey: String): TaskItem? {
        val text = columns.firstOrNull().orEmpty().trim()
        if (text.isBlank()) return null
        val second = columns.getOrNull(1).orEmpty().trim()
        val completed = parseBooleanish(second) ?: false
        val status = if (completed) TASK_STATUS_DONE else second.takeIf { looksLikeStatus(it) }.orEmpty()
        return TaskItem(
            id = stableId(sourceKey, index, columns),
            text = text,
            status = status.ifBlank { if (completed) TASK_STATUS_DONE else TASK_STATUS_TODO },
            completed = completed,
        )
    }

    private fun resolveHeader(firstRow: List<String>): HeaderMap? {
        val normalized = firstRow.map(::normalizeHeader)
        val mapped = normalized.mapIndexedNotNull { index, header ->
            HEADER_ALIASES[header]?.let { canonical -> canonical to index }
        }.toMap()
        if (mapped.isEmpty()) return null

        return HeaderMap(
            idIndex = mapped["id"],
            textIndex = mapped["text"] ?: 0,
            startsAtIndex = mapped["starts_at"],
            dueByIndex = mapped["due_by"],
            assigneesIndex = mapped["assignees"],
            statusIndex = mapped["status"],
            reminderIndex = mapped["reminder"],
            completedIndex = mapped["completed"],
        )
    }

    private fun parseRows(raw: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var index = 0

        fun flushField() {
            row += field.toString().trim()
            field.setLength(0)
        }

        fun flushRow() {
            flushField()
            if (row.any { it.isNotBlank() }) rows += row.toList()
            row = mutableListOf()
        }

        while (index < raw.length) {
            when (val ch = raw[index]) {
                '"' -> {
                    if (inQuotes && index + 1 < raw.length && raw[index + 1] == '"') {
                        field.append('"')
                        index++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ',' -> if (inQuotes) field.append(ch) else flushField()
                '\n' -> if (inQuotes) field.append(ch) else flushRow()
                '\r' -> if (inQuotes) {
                    field.append(ch)
                } else if (index + 1 < raw.length && raw[index + 1] == '\n') {
                    flushRow()
                    index++
                } else {
                    flushRow()
                }
                else -> field.append(ch)
            }
            index++
        }

        if (inQuotes) throw IllegalArgumentException("CSV has an unclosed quoted value.")
        if (field.isNotEmpty() || row.isNotEmpty()) flushRow()
        return rows
    }

    private fun valueAt(columns: List<String>, index: Int?): String = columns.getOrNull(index ?: -1).orEmpty().trim()

    private fun stableId(sourceKey: String, rowIndex: Int, columns: List<String>): String {
        val seed = buildString {
            append(sourceKey)
            append('|')
            append(rowIndex)
            append('|')
            append(columns.joinToString("\u001F"))
        }
        return UUID.nameUUIDFromBytes(seed.toByteArray(StandardCharsets.UTF_8)).toString()
    }

    private fun normalizeHeader(raw: String): String = raw.trim().lowercase().replace(" ", "_").replace("-", "_")

    private fun parseBooleanish(raw: String): Boolean? = when (raw.trim().lowercase()) {
        "1", "true", "yes", "y", "done", "complete", "completed", "x" -> true
        "0", "false", "no", "n", "todo", "pending", "open" -> false
        else -> null
    }

    private fun looksLikeStatus(raw: String): Boolean {
        val normalized = normalizedTaskStatus(raw)
        return raw.isNotBlank() && (normalized != TASK_STATUS_TODO || raw.trim().lowercase() in TODO_SYNONYMS)
    }

    private data class HeaderMap(
        val idIndex: Int?,
        val textIndex: Int,
        val startsAtIndex: Int?,
        val dueByIndex: Int?,
        val assigneesIndex: Int?,
        val statusIndex: Int?,
        val reminderIndex: Int?,
        val completedIndex: Int?,
    )

    private val HEADER_ALIASES = mapOf(
        "id" to "id",
        "task" to "text",
        "task_name" to "text",
        "title" to "text",
        "text" to "text",
        "name" to "text",
        "start" to "starts_at",
        "start_at" to "starts_at",
        "start_time" to "starts_at",
        "starts_at" to "starts_at",
        "due" to "due_by",
        "due_by" to "due_by",
        "due_date" to "due_by",
        "deadline" to "due_by",
        "complete_by" to "due_by",
        "assignee" to "assignees",
        "assignees" to "assignees",
        "owner" to "assignees",
        "person" to "assignees",
        "people" to "assignees",
        "who" to "assignees",
        "status" to "status",
        "state" to "status",
        "reminder" to "reminder",
        "reminders" to "reminder",
        "note" to "reminder",
        "notes" to "reminder",
        "completed" to "completed",
        "done" to "completed",
    )

    private val TODO_SYNONYMS = setOf("todo", "to do", "pending", "not_started", "not started", "open")
}
