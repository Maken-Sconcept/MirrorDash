package com.sconcept.mirrordash.settings.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sconcept.mirrordash.settings.SettingsUiState
import com.sconcept.mirrordash.settings.SettingsViewModel
import com.sconcept.mirrordash.ui.theme.MDTheme

@Composable
fun BrowserSettingsContent(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val settings = uiState.settings

    SettingRow(
        title = "Show browser tab",
        subtitle = "Adds a compact web page to the launcher for quick browsing sessions",
    ) {
        Switch(
            checked = settings.browserEnabled,
            onCheckedChange = viewModel::setBrowserEnabled,
            colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
        )
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Home page (optional)") {
        Text(
            "Leave this blank if you want the tab to open to an empty page and use the address bar as needed.",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(10.dp))
        BufferedTextField(
            persistedValue = settings.browserHomeUrl,
            onValueChange = viewModel::setBrowserHomeUrl,
            placeholder = { Text("https://") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Spacer(Modifier.height(20.dp))

    SettingGroup(title = "Current session") {
        Text(
            if (settings.browserLastVisitedUrl.isBlank()) {
                "No page is currently remembered for this tab."
            } else {
                "MirrorDash will reopen the last page you left in the browser tab."
            },
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        if (settings.browserLastVisitedUrl.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                settings.browserLastVisitedUrl,
                style = MDTheme.type.caption,
                color = MDTheme.colors.textTertiary,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = viewModel::clearBrowserSession) {
                Text("Reset remembered page", color = MDTheme.colors.accent)
            }
        }
    }
}
