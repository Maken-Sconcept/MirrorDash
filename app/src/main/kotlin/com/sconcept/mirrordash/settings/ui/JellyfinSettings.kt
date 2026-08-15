package com.sconcept.mirrordash.settings.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sconcept.mirrordash.settings.SettingsUiState
import com.sconcept.mirrordash.settings.SettingsViewModel
import com.sconcept.mirrordash.ui.theme.MDTheme

/**
 * Jellyfin runs here as its own web app inside a full-screen WebView, not by embedding the
 * Android Jellyfin app (which Android does not support hosting inside another app's window).
 */
@Composable
fun JellyfinSettingsContent(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val settings = uiState.settings

    SettingRow(
        title = "Show Jellyfin tab",
        subtitle = if (settings.jellyfinServerUrl.isBlank()) {
            "Add your server address below first"
        } else {
            "Swipe from Clock to reach it"
        },
    ) {
        Switch(
            checked = settings.jellyfinEnabled,
            onCheckedChange = viewModel::setJellyfinEnabled,
            enabled = settings.jellyfinServerUrl.isNotBlank() || settings.jellyfinEnabled,
            colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
        )
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Server address") {
        Text(
            "Your Jellyfin server root, e.g. http://jellyfin.local:8096 or https://media.example.com",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(10.dp))
        BufferedTextField(
            persistedValue = settings.jellyfinServerUrl,
            onValueChange = viewModel::setJellyfinServerUrl,
            placeholder = { Text("https://") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Start path (optional)") {
        Text(
            "Appended onto the server URL on first load. Leave blank for Jellyfin's normal landing page, or use something like /web/#/home.html.",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(10.dp))
        BufferedTextField(
            persistedValue = settings.jellyfinStartPath,
            onValueChange = viewModel::setJellyfinStartPath,
            placeholder = { Text("/web/#/home.html") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Spacer(Modifier.height(28.dp))

    SettingRow(
        title = "Desktop layout",
        subtitle = "Ask Jellyfin for its desktop-style web UI instead of the mobile one when possible",
    ) {
        Switch(
            checked = settings.jellyfinDesktopMode,
            onCheckedChange = viewModel::setJellyfinDesktopMode,
            colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
        )
    }

    Spacer(Modifier.height(16.dp))

    SettingRow(
        title = "Reload when reopened",
        subtitle = "Refresh the current Jellyfin page each time you swipe back to the tab",
    ) {
        Switch(
            checked = settings.jellyfinReloadOnOpen,
            onCheckedChange = viewModel::setJellyfinReloadOnOpen,
            colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
        )
    }

    Spacer(Modifier.height(16.dp))

    SettingRow(
        title = "Open external links outside MirrorDash",
        subtitle = "Send links that leave your Jellyfin server, like trailers or logins on other sites, to other apps",
    ) {
        Switch(
            checked = settings.jellyfinOpenExternalLinks,
            onCheckedChange = viewModel::setJellyfinOpenExternalLinks,
            colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
        )
    }

    Spacer(Modifier.height(16.dp))

    Text(
        "Sign in once on the tab itself. Cookies and local storage stay with the embedded web app, so Jellyfin can keep its own session the same way Home Assistant already does here.",
        style = MDTheme.type.caption,
        color = MDTheme.colors.textTertiary,
    )
}
