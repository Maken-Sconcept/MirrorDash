package com.sconcept.mirrordash.clock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sconcept.mirrordash.calendar.CalendarAgendaEntry
import com.sconcept.mirrordash.calendar.CalendarAgendaUiState
import com.sconcept.mirrordash.ui.theme.MDTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
internal fun CalendarAgendaWidgetSurface(widget: CalendarWidget, calendar: CalendarAgendaUiState) {
    val textColor = Color(widget.colorArgb)
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .widthIn(min = 240.dp, max = 440.dp)
            .shadow(elevation = 18.dp, shape = shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.4f))
            .border(1.dp, textColor.copy(alpha = 0.12f), shape)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CalendarGlyphIcon(size = 18.dp, color = textColor.copy(alpha = 0.7f))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Agenda",
                    style = MDTheme.type.settingSubtitle.copy(fontSize = 14.sp, letterSpacing = 0.2.sp),
                    color = textColor.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.height(14.dp))

            when {
                !calendar.permissionGranted -> Text(
                    "Grant calendar access in Clock settings to show upcoming events.",
                    style = MDTheme.type.caption.copy(fontSize = widget.fontSizeSp.sp * 0.78f),
                    color = textColor.copy(alpha = 0.62f),
                )

                else -> {
                    val lookaheadMillis = TimeUnit.DAYS.toMillis(widget.lookaheadDays.toLong().coerceAtLeast(1))
                    val cutoff = System.currentTimeMillis() + lookaheadMillis
                    val entries = calendar.events
                        .filter { it.startMillis <= cutoff }
                        .take(widget.itemCount.coerceAtLeast(1))

                    if (entries.isEmpty()) {
                        Text(
                            "No upcoming events",
                            style = MDTheme.type.caption.copy(fontSize = widget.fontSizeSp.sp * 0.78f),
                            color = textColor.copy(alpha = 0.55f),
                        )
                    } else {
                        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                            entries.forEach { entry -> AgendaRow(entry = entry, fontSizeSp = widget.fontSizeSp, textColor = textColor) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgendaRow(entry: CalendarAgendaEntry, fontSizeSp: Int, textColor: Color) {
    val eventColor = Color(entry.calendarColorArgb)
    Row(verticalAlignment = Alignment.CenterVertically) {
        DateTile(entry = entry, accent = eventColor)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MDTheme.type.settingSubtitle.copy(fontSize = fontSizeSp.sp),
                color = textColor,
                maxLines = 1,
            )
            Text(
                text = agendaTimeLabel(entry),
                style = MDTheme.type.caption.copy(fontSize = (fontSizeSp * 0.68f).sp),
                color = textColor.copy(alpha = 0.58f),
            )
        }
    }
}

/** The visual anchor of each row - a compact torn-page date tile (day number + month) tinted by
 * the source calendar's own color, replacing a plain colored dot with something that actually
 * reads as "a calendar entry" on its own, without needing the text next to it. */
@Composable
private fun DateTile(entry: CalendarAgendaEntry, accent: Color) {
    val cal = remember(entry.startMillis) { Calendar.getInstance().apply { timeInMillis = entry.startMillis } }
    val day = cal.get(Calendar.DAY_OF_MONTH)
    val month = remember(entry.startMillis) { SimpleDateFormat("MMM", Locale.getDefault()).format(entry.startMillis).uppercase() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .size(width = 40.dp, height = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.16f))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = month,
            style = MDTheme.type.caption.copy(fontSize = 9.sp, letterSpacing = 0.5.sp),
            color = accent,
            textAlign = TextAlign.Center,
        )
        Text(
            text = day.toString(),
            style = MDTheme.type.settingTitle.copy(fontSize = 17.sp, lineHeight = 18.sp),
            color = accent,
            textAlign = TextAlign.Center,
        )
    }
}

private fun agendaTimeLabel(entry: CalendarAgendaEntry): String {
    if (entry.allDay) return "All day"
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(entry.startMillis)
}
