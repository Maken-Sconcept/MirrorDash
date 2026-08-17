package com.sconcept.mirrordash.settings.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sconcept.mirrordash.nas.model.SmbFileItem
import com.sconcept.mirrordash.settings.NasTestResult
import com.sconcept.mirrordash.settings.SettingsUiState
import com.sconcept.mirrordash.settings.SettingsViewModel
import com.sconcept.mirrordash.ui.theme.MDTheme

@Composable
fun PhotoramaSettingsContent(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val settings = uiState.settings
    var host by remember(settings.smbHost) { mutableStateOf(settings.smbHost) }
    var share by remember(settings.smbShareName) { mutableStateOf(settings.smbShareName) }
    var username by remember(settings.smbUsername) { mutableStateOf(settings.smbUsername) }
    var domain by remember(settings.smbDomain) { mutableStateOf(settings.smbDomain) }
    var rememberConnection by remember(settings.smbRememberConnection) { mutableStateOf(settings.smbRememberConnection) }

    SettingGroup(title = "NAS connection") {
        LabeledField("Server", host, { host = it })
        Spacer(Modifier.height(10.dp))
        LabeledField("Share", share, { share = it })
        Spacer(Modifier.height(10.dp))
        LabeledField("Username", username, { username = it })
        Spacer(Modifier.height(10.dp))
        LabeledField("Domain (optional)", domain, { domain = it })
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = uiState.nasPasswordDraft,
            onValueChange = viewModel::setNasPasswordDraft,
            placeholder = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth().trackFieldFocusForIdleTimer(),
        )
        Spacer(Modifier.height(12.dp))

        SettingRow(title = "Remember connection", subtitle = "Store the password securely on-device") {
            Switch(
                checked = rememberConnection,
                onCheckedChange = { rememberConnection = it },
                colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { viewModel.testNasConnection(host, share, username, domain, rememberConnection) },
                enabled = uiState.nasTestResult != NasTestResult.TESTING && host.isNotBlank() && share.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MDTheme.colors.accent, contentColor = MDTheme.colors.onAccent),
            ) {
                Text(if (uiState.nasTestResult == NasTestResult.TESTING) "Testing…" else "Test connection")
            }
            Spacer(Modifier.width(16.dp))
            if (uiState.nasTestMessage != null) {
                Text(
                    uiState.nasTestMessage,
                    color = if (uiState.nasTestResult == NasTestResult.SUCCESS) MDTheme.colors.accent else MDTheme.colors.danger,
                    style = MDTheme.type.settingSubtitle,
                )
            }
        }

        if (settings.smbHost.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = viewModel::forgetNasConnection) {
                Text("Forget connection", color = MDTheme.colors.danger)
            }
        }
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Slideshow folder") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = settings.photoramaFolderPath.ifBlank { "No folder selected" },
                style = MDTheme.type.settingSubtitle,
                color = MDTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { viewModel.openFolderBrowser(settings.photoramaFolderPath) },
                enabled = uiState.nasTestResult == NasTestResult.SUCCESS || settings.smbHost.isNotBlank(),
            ) {
                Text("Browse", color = MDTheme.colors.accent)
            }
        }
    }

    Spacer(Modifier.height(28.dp))

    SettingRow(title = "Include subfolders", subtitle = "Scan folders inside the selected one too") {
        Switch(
            checked = settings.photoramaIncludeSubfolders,
            onCheckedChange = viewModel::setPhotoramaIncludeSubfolders,
            colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
        )
    }

    Spacer(Modifier.height(24.dp))

    SettingRow(title = "Shuffle", subtitle = "Show photos in random order") {
        Switch(
            checked = settings.photoramaShuffle,
            onCheckedChange = viewModel::setPhotoramaShuffle,
            colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
        )
    }

    Spacer(Modifier.height(24.dp))

    SettingGroup(title = "Interval") {
        Text("${settings.photoramaIntervalSeconds}s between photos", style = MDTheme.type.settingSubtitle, color = MDTheme.colors.textSecondary)
        androidx.compose.material3.Slider(
            value = settings.photoramaIntervalSeconds.toFloat(),
            onValueChange = { viewModel.setPhotoramaIntervalSeconds(it.toInt()) },
            valueRange = 5f..300f,
            colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = MDTheme.colors.accent, activeTrackColor = MDTheme.colors.accent),
        )
    }

    Spacer(Modifier.height(24.dp))

    SettingRow(title = "Enable Photorama", subtitle = "Turn the Photorama page into a live slideshow") {
        Switch(
            checked = settings.photoramaEnabled,
            onCheckedChange = viewModel::setPhotoramaEnabled,
            colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
        )
    }

    if (uiState.browser != null) {
        NasFolderBrowserSheet(
            path = uiState.browser.path,
            items = uiState.browser.items,
            isLoading = uiState.browser.isLoading,
            errorMessage = uiState.browser.errorMessage,
            onEnter = viewModel::browseInto,
            onUp = viewModel::browseUp,
            onSelect = viewModel::selectCurrentBrowserFolder,
            onDismiss = viewModel::closeFolderBrowser,
        )
    }
}

@Composable
private fun LabeledField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(label) },
        singleLine = true,
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth().trackFieldFocusForIdleTimer(),
    )
}

@Composable
private fun NasFolderBrowserSheet(
    path: String,
    items: List<SmbFileItem>,
    isLoading: Boolean,
    errorMessage: String?,
    onEnter: (SmbFileItem) -> Unit,
    onUp: () -> Unit,
    onSelect: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MDTheme.colors.scrim)
            .clickable(onClick = onDismiss),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.7f)
                .fillMaxSize()
                .padding(vertical = 60.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MDTheme.colors.surface)
                .clickable(enabled = false) {}
                .padding(24.dp),
        ) {
            Text("Select folder", style = MDTheme.type.sectionTitle, color = MDTheme.colors.textPrimary)
            Spacer(Modifier.height(6.dp))
            Text(path.ifBlank { "/ (root)" }, style = MDTheme.type.settingSubtitle, color = MDTheme.colors.textSecondary)
            Spacer(Modifier.height(16.dp))

            if (errorMessage != null) {
                Text(errorMessage, color = MDTheme.colors.danger, style = MDTheme.type.settingSubtitle)
                Spacer(Modifier.height(12.dp))
            }

            if (isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MDTheme.colors.accent)
                        Spacer(Modifier.height(12.dp))
                        Text("Loading…", style = MDTheme.type.settingSubtitle, color = MDTheme.colors.textSecondary)
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (path.isNotBlank()) {
                        item {
                            BrowserRow(name = "..", onClick = onUp)
                        }
                    }
                    if (items.isEmpty()) {
                        item {
                            Text(
                                "No subfolders here",
                                style = MDTheme.type.settingSubtitle,
                                color = MDTheme.colors.textSecondary,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                    }
                    items(items, key = { it.name }) { item ->
                        BrowserRow(name = item.name, onClick = { onEnter(item) })
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = MDTheme.colors.textSecondary) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onSelect,
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = MDTheme.colors.accent, contentColor = MDTheme.colors.onAccent),
                ) {
                    Text("Use this folder")
                }
            }
        }
    }
}

@Composable
private fun BrowserRow(name: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
    ) {
        Icon(Icons.Filled.Folder, contentDescription = null, tint = MDTheme.colors.accent)
        Spacer(Modifier.width(12.dp))
        Text(name, style = MDTheme.type.body, color = MDTheme.colors.textPrimary)
    }
}
