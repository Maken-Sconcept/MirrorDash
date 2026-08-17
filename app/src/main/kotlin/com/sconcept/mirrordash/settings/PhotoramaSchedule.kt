package com.sconcept.mirrordash.settings

import java.util.Calendar
import java.util.Locale

fun isPhotoramaScheduleActive(
    settings: MirrorDashSettings,
    nowMinuteOfDay: Int = currentMinuteOfDay(),
): Boolean = isMinuteWithinPhotoramaWindow(
    enabled = settings.photoramaScheduleEnabled,
    startMinutes = settings.photoramaScheduleStartMinutes,
    endMinutes = settings.photoramaScheduleEndMinutes,
    minuteOfDay = nowMinuteOfDay,
)

/** Handles an overnight window (start > end, e.g. 22:00-07:00) by wrapping past midnight rather
 * than treating it as an empty range - `start == end` is always empty (a zero-width window means
 * nothing, not "all day"). */
fun isMinuteWithinPhotoramaWindow(
    enabled: Boolean,
    startMinutes: Int,
    endMinutes: Int,
    minuteOfDay: Int,
): Boolean {
    if (!enabled) return true

    val start = startMinutes.coerceIn(0, 1439)
    val end = endMinutes.coerceIn(0, 1439)
    val minute = minuteOfDay.coerceIn(0, 1439)

    if (start == end) return false
    return if (start < end) {
        minute in start until end
    } else {
        minute >= start || minute < end
    }
}

fun formatMinutesOfDay(minutes: Int): String {
    val clamped = minutes.coerceIn(0, 1439)
    val hour = clamped / 60
    val minute = clamped % 60
    return String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
}

private fun currentMinuteOfDay(calendar: Calendar = Calendar.getInstance()): Int =
    calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
