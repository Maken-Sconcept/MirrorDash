package com.sconcept.mirrordash.settings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sconcept.mirrordash.settings.SettingsViewModel
import com.sconcept.mirrordash.ui.theme.MDTheme

private enum class SettingsSection(val title: String, val subtitle: String) {
    APPEARANCE("Appearance", "Clock font size, color, and background"),
    BRIGHTNESS("Brightness", "Backlight level, extra dim layer, and failsafes"),
    TEXT_WIDGETS("Text Widgets", "Add and style custom text on the Clock page"),
    WEATHER("Weather", "Location, units, and refresh"),
    PHOTORAMA("Photorama", "NAS connection and slideshow folder"),
    WALKIE_TALKIE("Walkie-Talkie", "Peers, target, and floating button"),
    AIRPLAY("AirPlay", "Receiver name, pairing, and mirror quality"),
    HOME_ASSISTANT("Home Assistant", "Dashboard address and the tab it lives on"),
    LAUNCHER("Launcher", "Default Home app and notification access"),
}

/**
 * Settings is the final pager page (brief section 31/45), not a separate Activity. Sub-areas
 * use in-place navigation rather than a nav graph since the whole surface is one Compose page
 * already hosted inside the launcher pager.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onRequestHomeRole: () -> Unit,
    onRequestNotificationAccess: () -> Unit,
    onRequestWriteSettingsAccess: () -> Unit,
    onRequestOverlayAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedSection by remember { mutableStateOf<SettingsSection?>(null) }

    BackHandler(enabled = selectedSection != null) { selectedSection = null }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MDTheme.colors.backgroundElevated),
    ) {
        AnimatedContent(
            targetState = selectedSection,
            transitionSpec = {
                if (targetState != null) {
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 3 }
                } else {
                    slideInHorizontally { -it / 3 } togetherWith slideOutHorizontally { it }
                }
            },
            label = "settingsSection",
        ) { section ->
            when (section) {
                null -> SettingsList(onSelect = { selectedSection = it })
                SettingsSection.APPEARANCE -> SectionScaffold(section.title) { AppearanceSettingsContent(uiState, viewModel) }
                SettingsSection.BRIGHTNESS -> SectionScaffold(section.title) { BrightnessSettingsContent(uiState, viewModel, onRequestWriteSettingsAccess) }
                SettingsSection.TEXT_WIDGETS -> SectionScaffold(section.title) { TextWidgetsSettingsContent(uiState, viewModel) }
                SettingsSection.WEATHER -> SectionScaffold(section.title) { WeatherSettingsContent(uiState, viewModel) }
                SettingsSection.PHOTORAMA -> SectionScaffold(section.title) { PhotoramaSettingsContent(uiState, viewModel) }
                SettingsSection.WALKIE_TALKIE -> SectionScaffold(section.title) { WalkieTalkieSettingsContent(uiState, viewModel) }
                SettingsSection.AIRPLAY -> SectionScaffold(section.title) { AirPlaySettingsContent(uiState, viewModel) }
                SettingsSection.HOME_ASSISTANT -> SectionScaffold(section.title) { HomeAssistantSettingsContent(uiState, viewModel) }
                SettingsSection.LAUNCHER -> SectionScaffold(section.title) {
                    LauncherSettingsContent(
                        uiState = uiState,
                        viewModel = viewModel,
                        onRequestHomeRole = onRequestHomeRole,
                        onRequestNotificationAccess = onRequestNotificationAccess,
                        onRequestWriteSettingsAccess = onRequestWriteSettingsAccess,
                        onRequestOverlayAccess = onRequestOverlayAccess,
                    )
                }
            }
        }

        if (selectedSection != null) {
            IconButton(
                onClick = { selectedSection = null },
                modifier = Modifier.padding(top = 20.dp, start = 20.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MDTheme.colors.textPrimary)
            }
        }
    }
}

@Composable
private fun SettingsList(onSelect: (SettingsSection) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 40.dp, start = 56.dp, end = 56.dp, bottom = 32.dp),
    ) {
        Text("Settings", style = MDTheme.type.sectionTitle.copy(fontSize = MDTheme.type.sectionTitle.fontSize), color = MDTheme.colors.textPrimary)
        Spacer(Modifier.height(28.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(SettingsSection.entries) { section ->
                SettingsRow(section = section, onClick = { onSelect(section) })
            }
        }
    }
}

@Composable
private fun SettingsRow(section: SettingsSection, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp, horizontal = 12.dp),
    ) {
        Icon(sectionIcon(section), contentDescription = null, tint = MDTheme.colors.accent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(section.title, style = MDTheme.type.settingTitle, color = MDTheme.colors.textPrimary)
            Text(section.subtitle, style = MDTheme.type.settingSubtitle, color = MDTheme.colors.textSecondary)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MDTheme.colors.textTertiary)
    }
}

private fun sectionIcon(section: SettingsSection) = when (section) {
    SettingsSection.APPEARANCE -> Icons.Filled.Palette
    SettingsSection.BRIGHTNESS -> Icons.Filled.Brightness6
    SettingsSection.TEXT_WIDGETS -> Icons.Filled.TextFields
    SettingsSection.WEATHER -> Icons.Filled.Cloud
    SettingsSection.PHOTORAMA -> Icons.Filled.Photo
    SettingsSection.WALKIE_TALKIE -> Icons.Filled.Podcasts
    SettingsSection.AIRPLAY -> Icons.Filled.Cast
    SettingsSection.HOME_ASSISTANT -> Icons.Filled.Home
    SettingsSection.LAUNCHER -> Icons.Filled.Info
}

@Composable
private fun SectionScaffold(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 40.dp, start = 56.dp, end = 56.dp, bottom = 32.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        Text(title, style = MDTheme.type.sectionTitle, color = MDTheme.colors.textPrimary)
        Spacer(Modifier.height(24.dp))
        content()
    }
}
