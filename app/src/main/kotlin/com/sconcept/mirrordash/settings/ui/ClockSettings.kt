package com.sconcept.mirrordash.settings.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sconcept.mirrordash.settings.SettingsUiState
import com.sconcept.mirrordash.settings.SettingsViewModel
import com.sconcept.mirrordash.ui.theme.MDTheme

private enum class ClockSettingsTab(val title: String) {
    CLOCK("Clock"),
    WIDGETS("Widgets"),
}

@Composable
fun ClockSettingsContent(uiState: SettingsUiState, viewModel: SettingsViewModel, onRequestCalendarAccess: () -> Unit) {
    var selectedTab by remember { mutableStateOf(ClockSettingsTab.CLOCK) }

    Text(
        "Keep the time display separate from the overlays that float around it.",
        style = MDTheme.type.settingSubtitle,
        color = MDTheme.colors.textSecondary,
    )
    Spacer(Modifier.height(20.dp))

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MDTheme.colors.surface)
            .padding(6.dp),
    ) {
        ClockSettingsTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) MDTheme.colors.accent else MDTheme.colors.surface)
                    .clickable { selectedTab = tab }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    tab.title,
                    style = MDTheme.type.settingTitle,
                    color = if (selected) MDTheme.colors.onAccent else MDTheme.colors.textSecondary,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }

    Spacer(Modifier.height(28.dp))

    Crossfade(targetState = selectedTab, label = "clockSettingsTab") { tab ->
        Column(modifier = Modifier.fillMaxWidth()) {
            when (tab) {
                ClockSettingsTab.CLOCK -> AppearanceSettingsContent(uiState, viewModel)
                ClockSettingsTab.WIDGETS -> WidgetsSettingsContent(uiState, viewModel, onRequestCalendarAccess)
            }
        }
    }
}

@Composable
private fun WidgetsSettingsContent(uiState: SettingsUiState, viewModel: SettingsViewModel, onRequestCalendarAccess: () -> Unit) {
    Text(
        "Long-press and drag widgets directly on the Clock page to place them.",
        style = MDTheme.type.settingSubtitle,
        color = MDTheme.colors.textSecondary,
    )
    Spacer(Modifier.height(20.dp))

    GroupedSettingsContent(
        subsections = listOf(
            SettingsSubsection(title = "Weather", subtitle = "Location source and weather widgets on the Clock") {
                SettingGroup(title = "Weather source") {
                    WeatherSourceSettingsContent(uiState, viewModel)
                }
                Spacer(Modifier.height(28.dp))
                SettingGroup(title = "Weather widgets") {
                    WeatherWidgetsSettingsContent(uiState, viewModel)
                }
            },
            SettingsSubsection(title = "Text widgets", subtitle = "Custom captions you place anywhere on the Clock") {
                TextWidgetsSettingsContent(uiState = uiState, viewModel = viewModel, showDragHint = false)
            },
            SettingsSubsection(title = "Calendar widgets", subtitle = "Upcoming events pulled from this device's calendars") {
                CalendarWidgetsSettingsContent(uiState, viewModel, onRequestCalendarAccess)
            },
            SettingsSubsection(title = "Tasks widgets", subtitle = "On-device checklists you can tick off from the Clock") {
                TasksWidgetsSettingsContent(uiState, viewModel)
            },
            SettingsSubsection(title = "Stocks widgets", subtitle = "Ticker symbols with live price and change") {
                StocksWidgetsSettingsContent(uiState, viewModel)
            },
            SettingsSubsection(title = "News widgets", subtitle = "A rotating headline from an RSS or Atom feed") {
                NewsWidgetsSettingsContent(uiState, viewModel)
            },
        ),
    )
}
