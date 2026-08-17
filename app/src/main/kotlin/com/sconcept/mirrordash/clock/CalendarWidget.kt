package com.sconcept.mirrordash.clock

import kotlinx.serialization.Serializable

/** A draggable agenda card on the Clock page - the next [itemCount] upcoming events across every
 * visible device calendar, looking [lookaheadDays] ahead. Read-only (no add/edit-event support);
 * data comes from [com.sconcept.mirrordash.calendar.CalendarAgendaRepository]. */
@Serializable
data class CalendarWidget(
    override val id: String,
    val itemCount: Int = 5,
    val lookaheadDays: Int = 7,
    val fontSizeSp: Int = 18,
    val colorArgb: Int = 0xFFF5F3EF.toInt(),
    override val anchorX: Float = 0.5f,
    override val anchorY: Float = 0.1f,
) : AnchoredWidget

fun defaultCalendarWidget(
    id: String = java.util.UUID.randomUUID().toString(),
    anchorX: Float = 0.5f,
    anchorY: Float = 0.1f,
): CalendarWidget = CalendarWidget(id = id, anchorX = anchorX, anchorY = anchorY)
