package com.sconcept.mirrordash.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sconcept.mirrordash.clock.CLOCK_FONT_SYSTEM_CURSIVE
import com.sconcept.mirrordash.clock.CLOCK_FONT_SYSTEM_DEFAULT
import com.sconcept.mirrordash.clock.CLOCK_FONT_SYSTEM_MONO
import com.sconcept.mirrordash.clock.CLOCK_FONT_SYSTEM_SANS
import com.sconcept.mirrordash.clock.CLOCK_FONT_SYSTEM_SERIF
import com.sconcept.mirrordash.clock.ClockFontLibrary
import com.sconcept.mirrordash.clock.DownloadedClockFont
import com.sconcept.mirrordash.ui.theme.MDTheme

private val systemFontIds = listOf(
    CLOCK_FONT_SYSTEM_DEFAULT,
    CLOCK_FONT_SYSTEM_SANS,
    CLOCK_FONT_SYSTEM_SERIF,
    CLOCK_FONT_SYSTEM_MONO,
    CLOCK_FONT_SYSTEM_CURSIVE,
)

/** Lets one widget instance pick its own font, independent of every other widget and of the
 * clock digits themselves - system fonts, the 15-font starter pack (bundled, no download), and
 * anything already downloaded from Clock settings' Google Fonts browser. Downloading a *new*
 * family stays a Clock-settings action (it needs network + local storage); this picker only
 * chooses among fonts already available on the device. */
@Composable
internal fun WidgetFontPicker(
    selectedFontId: String,
    downloadedFonts: List<DownloadedClockFont>,
    onSelect: (String) -> Unit,
) {
    val starterIds = remember { ClockFontLibrary.starterPack.map { it.id } }
    val extraIds = remember(downloadedFonts) {
        downloadedFonts.map { it.id }.filterNot { it in starterIds }
    }
    val allIds = remember(downloadedFonts) { systemFontIds + starterIds + extraIds }

    Column(
        modifier = Modifier
            .heightIn(max = 280.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        allIds.forEach { id ->
            FontPickerRow(
                label = ClockFontLibrary.label(id, downloadedFonts),
                selected = selectedFontId == id,
                onClick = { onSelect(id) },
            )
        }
    }
}

@Composable
private fun FontPickerRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MDTheme.colors.surface.copy(alpha = 0.6f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = MDTheme.colors.accent),
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = MDTheme.type.body, color = MDTheme.colors.textPrimary)
    }
}
