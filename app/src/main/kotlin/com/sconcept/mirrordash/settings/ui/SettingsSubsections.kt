package com.sconcept.mirrordash.settings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.sconcept.mirrordash.ui.theme.MDTheme

/**
 * One tappable page inside a [GroupedSettingsContent] menu - a slice of a settings tab that used
 * to be an always-visible SettingGroup in one long scroll, now folded away until tapped.
 */
class SettingsSubsection(
    val title: String,
    val subtitle: String,
    val content: @Composable () -> Unit,
)

/**
 * Splits a long settings tab into a tappable list of subsections, each opening into its own
 * scrollable detail page with a back row - mirrors SettingsScreen's own top-level list-to-detail
 * shell so nested navigation feels identical no matter how deep it goes.
 */
@Composable
fun GroupedSettingsContent(subsections: List<SettingsSubsection>, modifier: Modifier = Modifier) {
    var selected by remember { mutableStateOf<SettingsSubsection?>(null) }
    BackHandler(enabled = selected != null) { selected = null }

    AnimatedContent(
        targetState = selected,
        transitionSpec = {
            if (targetState != null) {
                slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 3 }
            } else {
                slideInHorizontally { -it / 3 } togetherWith slideOutHorizontally { it }
            }
        },
        label = "groupedSettings",
        modifier = modifier.fillMaxWidth(),
    ) { section ->
        if (section == null) {
            Column(Modifier.fillMaxWidth()) {
                subsections.forEachIndexed { index, subsection ->
                    SettingsSubsectionRow(subsection, onClick = { selected = subsection })
                    if (index != subsections.lastIndex) {
                        Spacer(Modifier.height(2.dp))
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { selected = null }
                        .padding(vertical = 8.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MDTheme.colors.accent,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(section.title, style = MDTheme.type.settingTitle, color = MDTheme.colors.accent)
                }
                Spacer(Modifier.height(20.dp))
                section.content()
            }
        }
    }
}

@Composable
private fun SettingsSubsectionRow(subsection: SettingsSubsection, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MDTheme.colors.surface)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(subsection.title, style = MDTheme.type.settingTitle, color = MDTheme.colors.textPrimary)
            Text(subsection.subtitle, style = MDTheme.type.settingSubtitle, color = MDTheme.colors.textSecondary)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MDTheme.colors.textTertiary)
    }
}
