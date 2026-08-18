package com.sconcept.mirrordash.clock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TasksCsvParserTest {

    @Test
    fun parse_supportsRichHeaderedRows() {
        val tasks = TasksCsvParser.parse(
            raw = "task,start,due,assignees,status,reminder\n" +
                "\"Replace filter\",2026-08-18 09:00,2026-08-18 11:00,Alex + Sam,in progress,Buy the right size first",
            sourceKey = "tasks.csv",
        )

        val task = tasks.single()
        assertEquals("Replace filter", task.text)
        assertEquals("2026-08-18 09:00", task.startsAt)
        assertEquals("2026-08-18 11:00", task.dueBy)
        assertEquals("Alex + Sam", task.assignees)
        assertEquals(TASK_STATUS_IN_PROGRESS, task.status)
        assertEquals("Buy the right size first", task.reminder)
    }

    @Test
    fun parse_treatsCompletedColumnAsDone() {
        val tasks = TasksCsvParser.parse(
            raw = "task,completed\nTake out bins,yes",
            sourceKey = "tasks.csv",
        )

        val task = tasks.single()
        assertTrue(task.isDone)
        assertEquals(TASK_STATUS_DONE, task.status)
    }

    @Test
    fun parse_supportsHeaderlessSimpleRows() {
        val tasks = TasksCsvParser.parse(
            raw = "Water plants\nCall HVAC",
            sourceKey = "tasks.csv",
        )

        assertEquals(listOf("Water plants", "Call HVAC"), tasks.map { it.text })
    }
}
