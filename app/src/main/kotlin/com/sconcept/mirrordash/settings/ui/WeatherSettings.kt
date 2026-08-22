package com.sconcept.mirrordash.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sconcept.mirrordash.clock.AnimatedWeatherIcon
import com.sconcept.mirrordash.launcher.gestures.LauncherIdleTracker
import com.sconcept.mirrordash.settings.SettingsUiState
import com.sconcept.mirrordash.settings.SettingsViewModel
import com.sconcept.mirrordash.ui.theme.MDTheme
import com.sconcept.mirrordash.weather.WeatherIconStyles

@Composable
fun WeatherSettingsContent(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    SettingGroup(title = "Weather source") {
        WeatherSourceSettingsContent(uiState, viewModel)
    }
}

/** Picks which icon set the weather widgets, Night Clock line, and forecast cards draw from -
 * MirrorDash's own procedural Canvas animation, or the flat-color FBgo set lifted from Meta
 * Portal Go's ambient screen (see [com.sconcept.mirrordash.weather.FbgoWeatherIcons]). Reuses
 * [AnimatedWeatherIcon] itself for the preview tiles so they never drift from what actually
 * renders on the Clock page. */
@Composable
internal fun WeatherIconStyleSettingsContent(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val settings = uiState.settings
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        WeatherIconStyles.ALL.forEach { styleId ->
            WeatherIconStyleCard(
                styleId = styleId,
                selected = settings.weatherIconStyle == styleId,
                onClick = { viewModel.setWeatherIconStyle(styleId) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun WeatherIconStyleCard(styleId: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val borderColor = if (selected) MDTheme.colors.accent else MDTheme.colors.divider
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MDTheme.colors.surface)
            .border(width = if (selected) 2.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        AnimatedWeatherIcon(weatherCode = 0, isDay = true, size = 40.dp, iconStyle = styleId)
        Spacer(Modifier.height(10.dp))
        Text(WeatherIconStyles.label(styleId), style = MDTheme.type.settingTitle, color = MDTheme.colors.textPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            WeatherIconStyles.subtitle(styleId),
            style = MDTheme.type.caption,
            color = MDTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun WeatherSourceSettingsContent(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val settings = uiState.settings
    var queryDraft by remember(settings.weatherLocationQuery) { mutableStateOf(settings.weatherLocationQuery) }

    SettingRow(
        title = "Show weather",
        subtitle = "Allow weather in clock styles and optional weather widgets",
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
            modifier = Modifier
                .fillMaxWidth()
                .trackFieldFocusForIdleTimer(),
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
            Text(
                uiState.weatherError,
                style = MDTheme.type.settingSubtitle,
                color = MDTheme.colors.danger,
            )
            Spacer(Modifier.height(10.dp))
        }
        Button(
            onClick = { viewModel.resolveAndSaveWeatherLocation(queryDraft) },
            enabled = !uiState.weatherResolving && queryDraft.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MDTheme.colors.accent,
                contentColor = MDTheme.colors.onAccent,
            ),
        ) {
            Text(if (uiState.weatherResolving) "Looking up..." else "Save location")
        }
    }

    Spacer(Modifier.height(28.dp))

    SettingRow(
        title = "Units",
        subtitle = if (settings.weatherUseFahrenheit) "Fahrenheit" else "Celsius",
    ) {
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

@Composable
internal fun Modifier.trackFieldFocusForIdleTimer(): Modifier {
    var isFocused by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose {
            if (isFocused) LauncherIdleTracker.fieldUnfocused()
        }
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
