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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sconcept.mirrordash.settings.SettingsViewModel
import com.sconcept.mirrordash.ui.theme.MDTheme
import com.sconcept.mirrordash.wedding.WeddingPinPolicy
import com.sconcept.mirrordash.wedding.WeddingPinResult
import com.sconcept.mirrordash.wedding.WeddingViewModel

private enum class SettingsSection(val title: String, val subtitle: String) {
    CLOCK("Clock", "Time, background, and draggable widgets"),
    BRIGHTNESS("Brightness", "Backlight level, extra dim layer, and failsafes"),
    NIGHT_CLOCK("Night Clock", "The hidden dark clock tab's own dimmer brightness"),
    PHOTORAMA("Photorama", "NAS connection and slideshow folder"),
    WALKIE_TALKIE("Walkie-Talkie", "Peers, target, and floating button"),
    AIRPLAY("AirPlay", "Receiver name, pairing, and mirror quality"),
    BROWSER("Browser", "Optional quick web tab inside MirrorDash"),
    JELLYFIN("Jellyfin", "Media server address and playback-focused web tab"),
    HOME_ASSISTANT("Home Assistant", "Dashboard address and the tab it lives on"),
    IPTV("IPTV", "Portal address, MAC address, and the tab it lives on"),
    PHOTOBOOTH("Photobooth", "3-photo capture, sharing, and hardware diagnostics"),
    MIRRORDROP("Share Photos", "Local Wi-Fi sharing server for Photobooth photos"),
    WEDDING("Wedding", "Couple details, guest reset, and seating setup"),
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
    weddingViewModel: WeddingViewModel,
    onRequestHomeRole: () -> Unit,
    onRequestNotificationAccess: () -> Unit,
    onRequestWriteSettingsAccess: () -> Unit,
    onRequestOverlayAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedSection by remember { mutableStateOf<SettingsSection?>(null) }
    var createWeddingPin by remember { mutableStateOf<Boolean?>(null) }

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
                null -> SettingsList(
                    onSelect = { selectedSection = it },
                    onEnterWeddingMode = { createWeddingPin = !viewModel.hasWeddingPin() },
                )
                SettingsSection.CLOCK -> SectionScaffold(section.title) { ClockSettingsContent(uiState, viewModel) }
                SettingsSection.BRIGHTNESS -> SectionScaffold(section.title) { BrightnessSettingsContent(uiState, viewModel, onRequestWriteSettingsAccess) }
                SettingsSection.NIGHT_CLOCK -> SectionScaffold(section.title) { NightClockSettingsContent(uiState, viewModel) }
                SettingsSection.PHOTORAMA -> SectionScaffold(section.title) { PhotoramaSettingsContent(uiState, viewModel) }
                SettingsSection.WALKIE_TALKIE -> SectionScaffold(section.title) { WalkieTalkieSettingsContent(uiState, viewModel) }
                SettingsSection.AIRPLAY -> SectionScaffold(section.title) { AirPlaySettingsContent(uiState, viewModel) }
                SettingsSection.BROWSER -> SectionScaffold(section.title) { BrowserSettingsContent(uiState, viewModel) }
                SettingsSection.JELLYFIN -> SectionScaffold(section.title) { JellyfinSettingsContent(uiState, viewModel) }
                SettingsSection.HOME_ASSISTANT -> SectionScaffold(section.title) { HomeAssistantSettingsContent(uiState, viewModel) }
                SettingsSection.IPTV -> SectionScaffold(section.title) { IptvSettingsContent(uiState, viewModel) }
                SettingsSection.PHOTOBOOTH -> SectionScaffold(section.title) { PhotoboothSettingsContent(uiState, viewModel) }
                SettingsSection.MIRRORDROP -> SectionScaffold(section.title) { MirrorDropSettingsContent(uiState, viewModel) }
                SettingsSection.WEDDING -> SectionScaffold(section.title) {
                    WeddingSettingsContent(uiState, viewModel, weddingViewModel)
                }
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

        createWeddingPin?.let { isCreating ->
            WeddingPinDialog(
                createPin = isCreating,
                onDismiss = { createWeddingPin = null },
                onSubmit = { pin -> viewModel.enterWeddingMode(pin) },
            )
        }
    }
}

@Composable
private fun SettingsList(
    onSelect: (SettingsSection) -> Unit,
    onEnterWeddingMode: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 40.dp, start = 56.dp, end = 56.dp, bottom = 32.dp),
    ) {
        Text("Settings", style = MDTheme.type.sectionTitle.copy(fontSize = MDTheme.type.sectionTitle.fontSize), color = MDTheme.colors.textPrimary)
        Spacer(Modifier.height(28.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(SettingsSection.entries) { section ->
                SettingsRow(section = section, onClick = { onSelect(section) })
            }
            item {
                Spacer(Modifier.height(28.dp))
                WeddingModeEntryCard(onClick = onEnterWeddingMode)
            }
        }
    }
}

@Composable
private fun WeddingModeEntryCard(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MDTheme.colors.surface)
            .padding(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.FavoriteBorder,
                contentDescription = null,
                tint = MDTheme.colors.accent,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Wedding Mode", style = MDTheme.type.settingTitle, color = MDTheme.colors.textPrimary)
                Text(
                    "Switch to the guest experience. The PIN you enter is required to leave it.",
                    style = MDTheme.type.settingSubtitle,
                    color = MDTheme.colors.textSecondary,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text("Enter Wedding Mode")
        }
    }
}

@Composable
private fun WeddingPinDialog(
    createPin: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> WeddingPinResult,
) {
    var pin by remember(createPin) { mutableStateOf("") }
    var confirmation by remember(createPin) { mutableStateOf("") }
    var error by remember(createPin) { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun submit() {
        if (createPin && pin != confirmation) {
            error = "The PINs do not match. Enter the same PIN twice."
            return
        }
        error = when (onSubmit(pin)) {
            WeddingPinResult.SUCCESS -> {
                onDismiss()
                null
            }
            WeddingPinResult.INVALID_FORMAT -> "Use ${WeddingPinPolicy.MIN_LENGTH}-${WeddingPinPolicy.MAX_LENGTH} digits."
            WeddingPinResult.INCORRECT -> "That PIN is incorrect. Try again."
            WeddingPinResult.STORAGE_ERROR -> "The PIN could not be secured on this device. Try again."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (createPin) "Create Wedding PIN" else "Enter Wedding Mode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    if (createPin) {
                        "Choose a PIN for the hosts. This same PIN will be required to exit Wedding Mode."
                    } else {
                        "Enter the host PIN to switch to Wedding Mode."
                    },
                    color = MDTheme.colors.textSecondary,
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        pin = WeddingPinPolicy.sanitize(it)
                        error = null
                    },
                    label = { Text(if (createPin) "New PIN" else "Host PIN") },
                    singleLine = true,
                    isError = error != null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                if (createPin) {
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = {
                            confirmation = WeddingPinPolicy.sanitize(it)
                            error = null
                        },
                        label = { Text("Confirm PIN") },
                        singleLine = true,
                        isError = error != null,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                error?.let { Text(it, color = MDTheme.colors.danger, style = MDTheme.type.caption) }
            }
        },
        confirmButton = {
            Button(onClick = ::submit) {
                Text(if (createPin) "Set PIN and Enter" else "Enter")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
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
    SettingsSection.CLOCK -> Icons.Filled.AccessTime
    SettingsSection.BRIGHTNESS -> Icons.Filled.Brightness6
    SettingsSection.NIGHT_CLOCK -> Icons.Filled.NightlightRound
    SettingsSection.PHOTORAMA -> Icons.Filled.Photo
    SettingsSection.WALKIE_TALKIE -> Icons.Filled.Podcasts
    SettingsSection.AIRPLAY -> Icons.Filled.Cast
    SettingsSection.BROWSER -> Icons.Filled.Language
    SettingsSection.JELLYFIN -> Icons.Filled.Movie
    SettingsSection.HOME_ASSISTANT -> Icons.Filled.Home
    SettingsSection.IPTV -> Icons.Filled.LiveTv
    SettingsSection.PHOTOBOOTH -> Icons.Filled.PhotoCamera
    SettingsSection.MIRRORDROP -> Icons.Filled.Share
    SettingsSection.WEDDING -> Icons.Filled.FavoriteBorder
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
