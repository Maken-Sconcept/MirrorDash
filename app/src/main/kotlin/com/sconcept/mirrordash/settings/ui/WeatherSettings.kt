package com.sconcept.mirrordash.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.sconcept.mirrordash.launcher.gestures.LauncherIdleTracker
import com.sconcept.mirrordash.settings.SettingsUiState
import com.sconcept.mirrordash.settings.SettingsViewModel
import com.sconcept.mirrordash.ui.theme.MDTheme

@Composable
fun WeatherSettingsContent(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val settings = uiState.settings
    var queryDraft by remember(settings.weatherLocationQuery) { mutableStateOf(settings.weatherLocationQuery) }

    SettingRow(
        title = "Show weather",
        subtitle = "Display current conditions on the Clock page",
    ) {
        Switch(
            checked = settings.weatherEnabled,
            onCheckedChange = viewModel::setWeatherEnabled,
            colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
        )
    }

    Spacer(Modifier.height(24.dp))

    SettingGroup(title = "Location") {
        OutlinedTextField(
            value = queryDraft,
            onValueChange = { queryDraft = it },
            placeholder = { Text("City name or \"lat, lon\"") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.resolveAndSaveWeatherLocation(queryDraft) }),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth().trackFieldFocusForIdleTimer(),
        )
        Spacer(Modifier.height(10.dp))
        if (settings.weatherLocationLabel.isNotBlank()) {
            Text(
                "Currently: ${settings.weatherLocationLabel}",
                style = MDTheme.type.settingSubtitle,
                color = MDTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(10.dp))
        }
        if (uiState.weatherError != null) {
            Text(uiState.weatherError, style = MDTheme.type.settingSubtitle, color = MDTheme.colors.danger)
            Spacer(Modifier.height(10.dp))
        }
        Button(
            onClick = { viewModel.resolveAndSaveWeatherLocation(queryDraft) },
            enabled = !uiState.weatherResolving && queryDraft.isNotBlank(),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MDTheme.colors.accent, contentColor = MDTheme.colors.onAccent),
        ) {
            Text(if (uiState.weatherResolving) "Looking up…" else "Save location")
        }
    }

    Spacer(Modifier.height(28.dp))

    SettingRow(title = "Units", subtitle = if (settings.weatherUseFahrenheit) "Fahrenheit" else "Celsius") {
        Switch(
            checked = settings.weatherUseFahrenheit,
            onCheckedChange = viewModel::setWeatherUnit,
            colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
        )
    }
}

@Composable
internal fun SettingRow(title: String, subtitle: String, content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MDTheme.type.settingTitle, color = MDTheme.colors.textPrimary)
            Text(subtitle, style = MDTheme.type.settingSubtitle, color = MDTheme.colors.textSecondary)
        }
        content()
    }
}

/**
 * A single-line field whose displayed value is a local buffer, not the persisted setting itself.
 * Fields bound straight to a DataStore-backed setting (`value = settings.x`) visibly revert a
 * character while typing on this hardware: each keystroke writes through
 * [com.sconcept.mirrordash.settings.SettingsRepository.update] (a suspend DataStore write), and if
 * the next keystroke lands before that write's re-emission reaches this composable, `value` is
 * still one character behind and the field snaps back to it. Buffering locally and pushing writes
 * upstream as a side effect (same pattern already used for the NAS host/share/username fields in
 * [PhotoramaSettingsContent]) makes the displayed text authoritative regardless of how long the
 * round trip takes. No `remember` key on purpose - re-syncing every time [persistedValue] changes
 * would reintroduce the same race, so this only initializes from the persisted value once, when
 * the field first enters composition (screen open / list-to-detail navigation), not on every
 * settings emission.
 */
@Composable
internal fun BufferedTextField(
    persistedValue: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
) {
    var draft by remember { mutableStateOf(persistedValue) }
    OutlinedTextField(
        value = draft,
        onValueChange = {
            draft = it
            onValueChange(it)
        },
        placeholder = placeholder,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        colors = fieldColors(),
        modifier = modifier.trackFieldFocusForIdleTimer(),
    )
}

/**
 * Marks [LauncherIdleTracker] while this field has focus - a *paused* Settings text field (user
 * reading, thinking, or mid-sentence with no keystrokes for a moment) generates no touches on the
 * launcher's root gesture surface at all, so without this the Travel-mode idle timer would still
 * expire out from under a focused-but-momentarily-still field. Always paired with an unfocus (or
 * the composable leaving composition) so the count can never leak.
 */
@Composable
internal fun Modifier.trackFieldFocusForIdleTimer(): Modifier {
    var isFocused by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose { if (isFocused) LauncherIdleTracker.fieldUnfocused() }
    }
    return this.onFocusChanged { state ->
        if (state.isFocused != isFocused) {
            isFocused = state.isFocused
            if (state.isFocused) LauncherIdleTracker.fieldFocused() else LauncherIdleTracker.fieldUnfocused()
        }
    }
}

@Composable
internal fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MDTheme.colors.textPrimary,
    unfocusedTextColor = MDTheme.colors.textPrimary,
    focusedBorderColor = MDTheme.colors.accent,
    unfocusedBorderColor = MDTheme.colors.divider,
    cursorColor = MDTheme.colors.accent,
    focusedPlaceholderColor = MDTheme.colors.textTertiary,
    unfocusedPlaceholderColor = MDTheme.colors.textTertiary,
)
