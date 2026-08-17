package com.sconcept.mirrordash.settings.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sconcept.mirrordash.settings.DEFAULT_KODI_PACKAGE_NAME
import com.sconcept.mirrordash.settings.SettingsUiState
import com.sconcept.mirrordash.settings.SettingsViewModel
import com.sconcept.mirrordash.ui.theme.MDTheme

/**
 * Kodi is a native Android app, not a web surface, so this tab launches the installed app rather
 * than embedding Kodi inside MirrorDash's own window.
 */
@Composable
fun KodiSettingsContent(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val settings = uiState.settings
    val packageName = settings.kodiPackageName.ifBlank { DEFAULT_KODI_PACKAGE_NAME }

    SettingRow(
        title = "Show Kodi tab",
        subtitle = "Adds a Kodi page to the launcher; opening that page launches the real Kodi app",
    ) {
        Switch(
            checked = settings.kodiEnabled,
            onCheckedChange = viewModel::setKodiEnabled,
            colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
        )
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Kodi package") {
        Text(
            "Usually this stays at the standard Android package name. Only change it if your Kodi build uses a different package.",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(10.dp))
        BufferedTextField(
            persistedValue = packageName,
            onValueChange = viewModel::setKodiPackageName,
            placeholder = { Text(DEFAULT_KODI_PACKAGE_NAME) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Spacer(Modifier.height(28.dp))

    SettingRow(
        title = "Launch automatically on open",
        subtitle = "When you swipe onto the Kodi tab, open Kodi right away instead of waiting for a tap",
    ) {
        Switch(
            checked = settings.kodiAutoLaunchOnOpen,
            onCheckedChange = viewModel::setKodiAutoLaunchOnOpen,
            colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
        )
    }

    Spacer(Modifier.height(16.dp))

    Text(
        "Kodi cannot be embedded inside MirrorDash the way Browser, Jellyfin, or Home Assistant can. This page is a launcher surface for the installed Kodi app instead.",
        style = MDTheme.type.caption,
        color = MDTheme.colors.textTertiary,
    )
}
