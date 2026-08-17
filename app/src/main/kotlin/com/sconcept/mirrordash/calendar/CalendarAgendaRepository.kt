package com.sconcept.mirrordash.calendar

import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import java.util.concurrent.TimeUnit

data class CalendarAgendaEntry(
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val calendarColorArgb: Int,
)

/** Reads the next [lookaheadDays] of events across every visible device calendar via the
 * platform [CalendarContract.Instances] table - read-only, no write/edit support. Requires
 * `android.permission.READ_CALENDAR`; callers must check that before querying (see
 * [CalendarAgendaViewModel]). */
class CalendarAgendaRepository(private val context: Context) {

    fun upcomingEvents(lookaheadDays: Int, limit: Int): List<CalendarAgendaEntry> {
        val now = System.currentTimeMillis()
        val end = now + TimeUnit.DAYS.toMillis(lookaheadDays.toLong().coerceAtLeast(1))

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            android.content.ContentUris.appendId(this, now)
            android.content.ContentUris.appendId(this, end)
        }.build()

        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_COLOR,
            CalendarContract.Instances.VISIBLE,
        )

        val entries = mutableListOf<CalendarAgendaEntry>()
        val cursor: Cursor? = context.contentResolver.query(
            uri,
            projection,
            "${CalendarContract.Instances.VISIBLE} = 1",
            null,
            "${CalendarContract.Instances.BEGIN} ASC",
        )
        cursor?.use {
            while (it.moveToNext() && entries.size < limit) {
                entries.add(
                    CalendarAgendaEntry(
                        title = it.getString(0) ?: "(untitled)",
                        startMillis = it.getLong(1),
                        endMillis = it.getLong(2),
                        allDay = it.getInt(3) != 0,
                        calendarColorArgb = it.getInt(4),
                    ),
                )
            }
        }
        return entries
    }
}
