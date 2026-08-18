package com.sconcept.mirrordash.settings.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.sconcept.mirrordash.clock.ClockFontLibrary
import com.sconcept.mirrordash.clock.ClockRenderData
import com.sconcept.mirrordash.clock.ClockStyleCatalog
import com.sconcept.mirrordash.clock.ClockStyleDefinition
import com.sconcept.mirrordash.clock.ClockStyleScene
import com.sconcept.mirrordash.clock.DownloadedClockFont
import com.sconcept.mirrordash.clock.GoogleFontCatalogEntry
import com.sconcept.mirrordash.clock.rememberClockFontFamily
import com.sconcept.mirrordash.nas.model.SmbFileItem
import com.sconcept.mirrordash.settings.CLOCK_BACKGROUND_MODE_PHOTORAMA
import com.sconcept.mirrordash.settings.NasTestResult
import com.sconcept.mirrordash.settings.MirrorDashSettings
import com.sconcept.mirrordash.settings.PHOTORAMA_SOURCE_LOCAL
import com.sconcept.mirrordash.settings.PHOTORAMA_SOURCE_NAS
import com.sconcept.mirrordash.settings.SettingsUiState
import com.sconcept.mirrordash.settings.SettingsViewModel
import com.sconcept.mirrordash.settings.formatMinutesOfDay
import com.sconcept.mirrordash.ui.theme.ClockBackgroundPresets
import com.sconcept.mirrordash.ui.theme.ClockColorPresets
import com.sconcept.mirrordash.ui.theme.MDTheme

@Composable
fun AppearanceSettingsContent(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val settings = uiState.settings
    var showClockFontBrowser by remember { mutableStateOf(false) }

    SettingGroup(title = "Clock size") {
        Text(
            "${settings.clockFontSizeSp}sp",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        Slider(
            value = settings.clockFontSizeSp.toFloat(),
            onValueChange = { viewModel.setClockFontSize(it.toInt()) },
            valueRange = 64f..180f,
            steps = 0,
            colors = SliderDefaults.colors(
                thumbColor = MDTheme.colors.accent,
                activeTrackColor = MDTheme.colors.accent,
                inactiveTrackColor = MDTheme.colors.divider,
            ),
        )
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Clock style") {
        Text(
            "Choose the layout language first, then tune the colors and base font.",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(12.dp))

        ClockStyleCatalog.presets.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { style ->
                    ClockStyleCard(
                        style = style,
                        selected = settings.clockStyleId == style.id,
                        fontId = settings.clockFontId,
                        downloadedFonts = settings.downloadedClockFonts,
                        primaryColor = Color(settings.clockTextColorArgb),
                        accentColor = Color(settings.clockAccentColorArgb),
                        onClick = { viewModel.setClockStyle(style.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Clock font") {
        Text(
            ClockFontLibrary.label(settings.clockFontId, settings.downloadedClockFonts),
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(12.dp))

        Text(
            "Base pack: 15 fonts bundled with the app and copied into the app's private clock-fonts folder.",
            style = MDTheme.type.caption,
            color = MDTheme.colors.textTertiary,
        )
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { showClockFontBrowser = true },
            colors = ButtonDefaults.buttonColors(containerColor = MDTheme.colors.accent, contentColor = MDTheme.colors.onAccent),
        ) {
            Text("Browse more Google Fonts")
        }

        if (uiState.clockFontStatusMessage != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                uiState.clockFontStatusMessage,
                style = MDTheme.type.caption,
                color = if (uiState.downloadingClockFontId == null && uiState.clockFontStatusMessage.contains("failed", ignoreCase = true)) {
                    MDTheme.colors.danger
                } else {
                    MDTheme.colors.textSecondary
                },
            )
        }
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Primary color") {
        ColorSwatchRow(
            colors = ClockColorPresets,
            selected = Color(settings.clockTextColorArgb),
            onSelect = { viewModel.setClockTextColor(it) },
        )
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Accent color") {
        ColorSwatchRow(
            colors = ClockColorPresets,
            selected = Color(settings.clockAccentColorArgb),
            onSelect = { viewModel.setClockAccentColor(it) },
        )
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Show on Clock") {
        SettingRow(title = "Show clock", subtitle = "Time and date") {
            Switch(
                checked = settings.clockShowTime,
                onCheckedChange = viewModel::setClockShowTime,
                colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
            )
        }
        Spacer(Modifier.height(12.dp))
        SettingRow(title = "Show widgets", subtitle = "Weather and any custom text widgets") {
            Switch(
                checked = settings.clockShowWidgets,
                onCheckedChange = viewModel::setClockShowWidgets,
                colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Turn both off for a fullscreen Photorama slideshow with nothing overlaid.",
            style = MDTheme.type.caption,
            color = MDTheme.colors.textTertiary,
        )
    }

    Spacer(Modifier.height(28.dp))

    // clockBackgroundMode, not photoramaEnabled, is what ClockViewModel actually renders off of -
    // reading photoramaEnabled here instead would let this switch's checked state disagree with
    // what's really on screen wherever the two fields aren't in sync (e.g. a pre-existing device
    // where photoramaEnabled was set true long before clockBackgroundMode ever existed).
    val usePhotoramaBackground = settings.clockBackgroundMode == CLOCK_BACKGROUND_MODE_PHOTORAMA

    SettingGroup(title = "Background") {
        SettingRow(
            title = "Use Photorama as background",
            subtitle = "Show a live photo slideshow behind the clock instead of a solid color",
        ) {
            Switch(
                checked = usePhotoramaBackground,
                onCheckedChange = viewModel::setPhotoramaEnabled,
                colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
            )
        }

        if (usePhotoramaBackground) {
            Spacer(Modifier.height(20.dp))
            PhotoramaSourceSection(uiState, viewModel)
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "Background color",
            style = MDTheme.type.settingTitle,
            color = MDTheme.colors.textPrimary,
            modifier = Modifier.alpha(if (usePhotoramaBackground) 0.4f else 1f),
        )
        Spacer(Modifier.height(10.dp))
        ColorSwatchRow(
            colors = ClockBackgroundPresets,
            selected = Color(settings.clockBackgroundColorArgb),
            onSelect = { viewModel.setClockBackgroundColor(it) },
            enabled = !usePhotoramaBackground,
        )
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Position") {
        Text(
            "Long-press and drag the clock or any widget on the Clock page to move it.",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = viewModel::resetClockLayout) {
            Text("Reset clock position", color = MDTheme.colors.accent)
        }
    }

    if (uiState.browser != null) {
        // A plain composable call here would size itself against AppearanceSettingsContent's own
        // scrollable column - unbounded height, so fillMaxSize() on the sheet resolves against
        // that instead of the real screen, squashing it down to whatever little space was left
        // and leaving the folder list unreachable. A Dialog renders in its own window, sized
        // against the actual screen regardless of where this call site sits in the tree.
        androidx.compose.ui.window.Dialog(
            onDismissRequest = viewModel::closeFolderBrowser,
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false,
            ),
        ) {
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

    if (showClockFontBrowser) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showClockFontBrowser = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false,
            ),
        ) {
            ClockFontBrowserSheet(
                uiState = uiState,
                selectedFontId = settings.clockFontId,
                downloadedFonts = settings.downloadedClockFonts,
                onUseDownloaded = viewModel::setClockFont,
                onDownload = viewModel::downloadClockFont,
                onDeleteDownloaded = viewModel::removeDownloadedClockFont,
                onDismiss = { showClockFontBrowser = false },
            )
        }
    }
}

/** Everything that happens once "Use Photorama as background" is on: the Local storage / NAS
 * chooser if nothing's picked yet, the matching inline setup form once it is, and (once actually
 * configured) the slideshow behavior and schedule settings shared by both sources. */
@Composable
private fun PhotoramaSourceSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val settings = uiState.settings

    when (settings.photoramaSource) {
        "" -> PhotoramaSourceChooser(
            onChooseLocal = { viewModel.setPhotoramaSource(PHOTORAMA_SOURCE_LOCAL) },
            onChooseNas = { viewModel.setPhotoramaSource(PHOTORAMA_SOURCE_NAS) },
        )
        PHOTORAMA_SOURCE_LOCAL -> LocalSourceSection(settings.photoramaLocalFolderUri, viewModel)
        PHOTORAMA_SOURCE_NAS -> NasSourceSection(uiState, viewModel)
    }

    val isConfigured = when (settings.photoramaSource) {
        PHOTORAMA_SOURCE_LOCAL -> settings.photoramaLocalFolderUri.isNotBlank()
        PHOTORAMA_SOURCE_NAS -> settings.smbShare.isConfigured && settings.photoramaFolderPath.isNotBlank()
        else -> false
    }

    if (isConfigured) {
        Spacer(Modifier.height(20.dp))
        PhotoramaSlideshowSettings(settings, viewModel)
        Spacer(Modifier.height(20.dp))
        PhotoramaScheduleSettings(settings, viewModel)
    }
}

@Composable
private fun PhotoramaSourceChooser(onChooseLocal: () -> Unit, onChooseNas: () -> Unit) {
    Column {
        Text(
            "Where do your photos live?",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onChooseLocal,
                colors = ButtonDefaults.buttonColors(containerColor = MDTheme.colors.accent, contentColor = MDTheme.colors.onAccent),
            ) {
                Text("Local storage")
            }
            Button(
                onClick = onChooseNas,
                colors = ButtonDefaults.buttonColors(containerColor = MDTheme.colors.accent, contentColor = MDTheme.colors.onAccent),
            ) {
                Text("NAS")
            }
        }
    }
}

@Composable
private fun ChangeSourceButton(viewModel: SettingsViewModel) {
    TextButton(onClick = viewModel::resetPhotoramaSource) {
        Text("Change source", color = MDTheme.colors.textSecondary)
    }
}

@Composable
private fun LocalSourceSection(folderUri: String, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val folderName = remember(folderUri) {
        folderUri.takeIf { it.isNotBlank() }
            ?.let { runCatching { DocumentFile.fromTreeUri(context, Uri.parse(it)) }.getOrNull()?.name }
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.setPhotoramaLocalFolderUri(uri)
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = folderName ?: "No folder selected",
                style = MDTheme.type.settingSubtitle,
                color = MDTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { pickFolder.launch(null) }) {
                Text("Choose folder", color = MDTheme.colors.accent)
            }
        }
        Spacer(Modifier.height(8.dp))
        ChangeSourceButton(viewModel)
    }
}

@Composable
private fun NasSourceSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val settings = uiState.settings
    var host by remember(settings.smbHost) { mutableStateOf(settings.smbHost) }
    var share by remember(settings.smbShareName) { mutableStateOf(settings.smbShareName) }
    var username by remember(settings.smbUsername) { mutableStateOf(settings.smbUsername) }
    var domain by remember(settings.smbDomain) { mutableStateOf(settings.smbDomain) }
    var rememberConnection by remember(settings.smbRememberConnection) { mutableStateOf(settings.smbRememberConnection) }

    Column {
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

        Spacer(Modifier.height(16.dp))

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

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChangeSourceButton(viewModel)
            if (settings.smbHost.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = viewModel::forgetNasConnection) {
                    Text("Forget connection", color = MDTheme.colors.danger)
                }
            }
        }
    }
}

@Composable
private fun PhotoramaSlideshowSettings(settings: MirrorDashSettings, viewModel: SettingsViewModel) {
    Column {
        Text("Slideshow", style = MDTheme.type.settingTitle, color = MDTheme.colors.textPrimary)
        Spacer(Modifier.height(10.dp))

        SettingRow(title = "Include subfolders", subtitle = "Scan folders inside the selected one too") {
            Switch(
                checked = settings.photoramaIncludeSubfolders,
                onCheckedChange = viewModel::setPhotoramaIncludeSubfolders,
                colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
            )
        }

        Spacer(Modifier.height(12.dp))

        SettingRow(title = "Shuffle", subtitle = "Show photos in random order") {
            Switch(
                checked = settings.photoramaShuffle,
                onCheckedChange = viewModel::setPhotoramaShuffle,
                colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
            )
        }

        Spacer(Modifier.height(16.dp))

        Text("${settings.photoramaIntervalSeconds}s between photos", style = MDTheme.type.settingSubtitle, color = MDTheme.colors.textSecondary)
        Slider(
            value = settings.photoramaIntervalSeconds.toFloat(),
            onValueChange = { viewModel.setPhotoramaIntervalSeconds(it.toInt()) },
            valueRange = 5f..300f,
            colors = SliderDefaults.colors(thumbColor = MDTheme.colors.accent, activeTrackColor = MDTheme.colors.accent),
        )
    }
}

/** Optional refinement on top of "Use Photorama as background" - off (the default) means always
 * on, matching the switch above by itself; on gates it to a daily start/end window, checked live
 * every minute by [com.sconcept.mirrordash.clock.ClockViewModel]. */
@Composable
private fun PhotoramaScheduleSettings(settings: MirrorDashSettings, viewModel: SettingsViewModel) {
    Column {
        SettingRow(title = "Only during a schedule", subtitle = "Otherwise Photorama is always the background while enabled") {
            Switch(
                checked = settings.photoramaScheduleEnabled,
                onCheckedChange = viewModel::setPhotoramaScheduleEnabled,
                colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
            )
        }

        if (settings.photoramaScheduleEnabled) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Start · ${formatMinutesOfDay(settings.photoramaScheduleStartMinutes)}",
                style = MDTheme.type.settingSubtitle,
                color = MDTheme.colors.textSecondary,
            )
            Slider(
                value = settings.photoramaScheduleStartMinutes.toFloat(),
                onValueChange = { viewModel.setPhotoramaScheduleStartMinutes(it.toInt()) },
                valueRange = 0f..1439f,
                steps = 95,
                colors = SliderDefaults.colors(thumbColor = MDTheme.colors.accent, activeTrackColor = MDTheme.colors.accent),
            )

            Spacer(Modifier.height(12.dp))
            Text(
                "End · ${formatMinutesOfDay(settings.photoramaScheduleEndMinutes)}",
                style = MDTheme.type.settingSubtitle,
                color = MDTheme.colors.textSecondary,
            )
            Slider(
                value = settings.photoramaScheduleEndMinutes.toFloat(),
                onValueChange = { viewModel.setPhotoramaScheduleEndMinutes(it.toInt()) },
                valueRange = 0f..1439f,
                steps = 95,
                colors = SliderDefaults.colors(thumbColor = MDTheme.colors.accent, activeTrackColor = MDTheme.colors.accent),
            )
        }
    }
}

private val PreviewClockData = ClockRenderData(
    hour = "9",
    minute = "41",
    hourPadded = "09",
    minutePadded = "41",
    timeText = "9:41",
    infoLine = "MON  ·  AUG 17  ·  72°",
    weatherGlyph = "☀",
)

@Composable
private fun ClockStyleCard(
    style: ClockStyleDefinition,
    selected: Boolean,
    fontId: String,
    downloadedFonts: List<DownloadedClockFont>,
    primaryColor: Color,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fontFamily = rememberClockFontFamily(fontId, downloadedFonts)
    val borderColor = if (selected) MDTheme.colors.accent else MDTheme.colors.divider
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MDTheme.colors.surface)
            .border(width = if (selected) 2.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Text(style.title, style = MDTheme.type.settingTitle, color = MDTheme.colors.textPrimary)
        Text(
            style.subtitle,
            style = MDTheme.type.caption,
            color = MDTheme.colors.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(124.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MDTheme.colors.backgroundElevated)
                .padding(12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            ClockStyleScene(
                style = style,
                data = PreviewClockData,
                baseSizeSp = 28f,
                fontFamily = fontFamily,
                primaryColor = primaryColor,
                accentColor = accentColor,
                lineSizeSp = 8f,
            )
        }
    }
}

@Composable
private fun DownloadedClockFontRow(
    font: DownloadedClockFont,
    selected: Boolean,
    onUse: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MDTheme.colors.surface.copy(alpha = 0.9f) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(font.family, style = MDTheme.type.settingTitle, color = MDTheme.colors.textPrimary)
            Text(
                "${font.category.replace('_', ' ').lowercase()} · stored on this device",
                style = MDTheme.type.caption,
                color = MDTheme.colors.textSecondary,
            )
        }
        TextButton(onClick = onUse) {
            Text(if (selected) "Selected" else "Use", color = MDTheme.colors.accent)
        }
        if (onDelete != null) {
            TextButton(onClick = onDelete) {
                Text("Delete", color = MDTheme.colors.danger)
            }
        }
    }
}

@Composable
private fun ClockFontBrowserSheet(
    uiState: SettingsUiState,
    selectedFontId: String,
    downloadedFonts: List<DownloadedClockFont>,
    onUseDownloaded: (String) -> Unit,
    onDownload: (GoogleFontCatalogEntry) -> Unit,
    onDeleteDownloaded: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val downloadedIds = remember(downloadedFonts) { downloadedFonts.map { it.id }.toSet() }
    val starterPackIds = remember { ClockFontLibrary.starterPack.map { it.id }.toSet() }
    val filteredCatalog = remember(uiState.googleFontCatalog, query) {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) {
            ClockFontLibrary.starterPack.mapNotNull { bundled ->
                uiState.googleFontCatalog.firstOrNull { it.id == bundled.id }
            }
        } else {
            uiState.googleFontCatalog.filter { entry ->
                entry.family.lowercase().contains(needle) ||
                    entry.category.lowercase().contains(needle) ||
                    entry.subsets.any { it.lowercase().contains(needle) }
            }.take(15)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MDTheme.colors.scrim)
            .clickable(onClick = onDismiss),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.9f)
                .fillMaxSize()
                .padding(vertical = 24.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MDTheme.colors.surface)
                .clickable(enabled = false) {}
                .padding(24.dp),
        ) {
            Text("Google Fonts", style = MDTheme.type.sectionTitle, color = MDTheme.colors.textPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                "Download any family once and MirrorDash keeps it in its private clock-font folder on this device.",
                style = MDTheme.type.settingSubtitle,
                color = MDTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search fonts, categories, or subsets") },
                singleLine = true,
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth().trackFieldFocusForIdleTimer(),
            )
            Spacer(Modifier.height(16.dp))

            when {
                uiState.googleFontCatalogLoading -> {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MDTheme.colors.accent)
                            Spacer(Modifier.height(12.dp))
                            Text("Loading Google Fonts…", style = MDTheme.type.settingSubtitle, color = MDTheme.colors.textSecondary)
                        }
                    }
                }

                uiState.googleFontCatalogError != null -> {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(uiState.googleFontCatalogError, style = MDTheme.type.settingSubtitle, color = MDTheme.colors.danger)
                    }
                }

                else -> {
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(filteredCatalog, key = { it.id }) { entry ->
                            val isDownloaded = entry.id in downloadedIds
                            val isBundled = entry.id in starterPackIds
                            val isSelected = selectedFontId == entry.id
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) MDTheme.colors.background else Color.Transparent)
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.family, style = MDTheme.type.settingTitle, color = MDTheme.colors.textPrimary)
                                    Text(
                                        "${entry.category.replace('_', ' ').lowercase()} · ${entry.styleCount} style${if (entry.styleCount == 1) "" else "s"} · ${entry.subsets.take(3).joinToString(", ")}",
                                        style = MDTheme.type.caption,
                                        color = MDTheme.colors.textSecondary,
                                    )
                                }

                                when {
                                    uiState.downloadingClockFontId == entry.id -> {
                                        CircularProgressIndicator(
                                            color = MDTheme.colors.accent,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }

                                    isBundled -> {
                                        TextButton(onClick = { onUseDownloaded(entry.id) }) {
                                            Text(if (isSelected) "Selected" else "Use", color = MDTheme.colors.accent)
                                        }
                                    }

                                    isDownloaded -> {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            TextButton(onClick = { onUseDownloaded(entry.id) }) {
                                                Text(if (isSelected) "Selected" else "Use", color = MDTheme.colors.accent)
                                            }
                                            TextButton(onClick = { onDeleteDownloaded(entry.id) }) {
                                                Text("Delete", color = MDTheme.colors.danger)
                                            }
                                        }
                                    }

                                    else -> {
                                        Button(
                                            onClick = { onDownload(entry) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MDTheme.colors.accent,
                                                contentColor = MDTheme.colors.onAccent,
                                            ),
                                        ) {
                                            Text("Download")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) {
                    Text("Close", color = MDTheme.colors.textSecondary)
                }
            }
        }
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
                .fillMaxWidth(0.85f)
                .fillMaxSize()
                .padding(vertical = 24.dp)
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

@Composable
internal fun SettingGroup(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MDTheme.type.settingTitle, color = MDTheme.colors.textPrimary)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
internal fun ColorSwatchRow(colors: List<Color>, selected: Color, onSelect: (Color) -> Unit, enabled: Boolean = true) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f),
    ) {
        colors.forEach { color ->
            val isSelected = color.value == selected.value
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (isSelected) 3.dp else 0.dp,
                        color = if (isSelected) MDTheme.colors.accent else Color.Transparent,
                        shape = CircleShape,
                    )
                    .clickable(enabled = enabled) { onSelect(color) },
            )
        }
    }
}
