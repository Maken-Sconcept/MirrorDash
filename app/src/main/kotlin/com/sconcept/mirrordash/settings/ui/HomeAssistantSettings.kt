package com.sconcept.mirrordash.settings.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sconcept.mirrordash.settings.SettingsUiState
import com.sconcept.mirrordash.settings.SettingsViewModel
import com.sconcept.mirrordash.ui.theme.MDTheme

/**
 * A WebView-backed kiosk tab rather than launching the Home Assistant app - see
 * [com.sconcept.mirrordash.homeassistant.HomeAssistantScreen]'s doc comment for why. "Each tab
 * can be enabled or disabled" is handled generically by [com.sconcept.mirrordash.launcher.navigation.LauncherPages];
 * this is the one concrete instance of that so far, alongside Photorama.
 */
@Composable
fun HomeAssistantSettingsContent(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val settings = uiState.settings

    SettingRow(
        title = "Show Home Assistant tab",
        subtitle = if (settings.homeAssistantUrl.isBlank()) "Add a dashboard address below first" else "Swipe from Clock to reach it",
    ) {
        Switch(
            checked = settings.homeAssistantEnabled,
            onCheckedChange = viewModel::setHomeAssistantEnabled,
            enabled = settings.homeAssistantUrl.isNotBlank() || settings.homeAssistantEnabled,
            colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
        )
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Dashboard address") {
        Text(
            "The full URL of the dashboard to show, e.g. http://homeassistant.local:8123/lovelace/default_view",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(10.dp))
        BufferedTextField(
            persistedValue = settings.homeAssistantUrl,
            onValueChange = viewModel::setHomeAssistantUrl,
            placeholder = { Text("https://") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Auto sign-in") {
        Text(
            "Fills in the username/password below on Home Assistant's login screen automatically." +
                " Best-effort - Home Assistant's login form is more likely to change shape than" +
                " Jellyfin's, so if it stops working it just falls back to signing in manually" +
                " that session.",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        ) {
            Switch(
                checked = settings.homeAssistantAutoAuth,
                onCheckedChange = viewModel::setHomeAssistantAutoAuth,
                colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
            )
            Spacer(Modifier.width(10.dp))
            Text("Sign in automatically", style = MDTheme.type.body, color = MDTheme.colors.textPrimary)
        }
        Spacer(Modifier.height(16.dp))
        BufferedTextField(
            persistedValue = settings.homeAssistantUsername,
            onValueChange = viewModel::setHomeAssistantUsername,
            placeholder = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = uiState.homeAssistantPasswordDraft,
            onValueChange = viewModel::setHomeAssistantPasswordDraft,
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = viewModel::saveHomeAssistantPassword,
            enabled = uiState.homeAssistantPasswordDraft.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = MDTheme.colors.accent, contentColor = MDTheme.colors.onAccent),
        ) {
            Text("Save password")
        }
    }

    Spacer(Modifier.height(16.dp))

    Text(
        "Sign in once on the tab itself - the session is remembered from then on.",
        style = MDTheme.type.caption,
        color = MDTheme.colors.textTertiary,
    )
}
