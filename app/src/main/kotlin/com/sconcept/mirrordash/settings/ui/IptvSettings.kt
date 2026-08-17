package com.sconcept.mirrordash.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sconcept.mirrordash.iptv.DEFAULT_PARENTAL_CONTROL_PIN
import com.sconcept.mirrordash.iptv.IptvMac
import com.sconcept.mirrordash.iptv.MAX_PARENTAL_CONTROL_PIN_LENGTH
import com.sconcept.mirrordash.iptv.ParentalControlMode
import com.sconcept.mirrordash.iptv.RecordingDestinationMode
import com.sconcept.mirrordash.iptv.StalkerAccountInfo
import com.sconcept.mirrordash.iptv.formatAccountExpiry
import com.sconcept.mirrordash.iptv.player.PlayerBackend
import com.sconcept.mirrordash.launcher.AppContainer
import com.sconcept.mirrordash.settings.SettingsUiState
import com.sconcept.mirrordash.settings.SettingsViewModel
import com.sconcept.mirrordash.ui.theme.MDTheme

/**
 * Settings for the IPTV tab (brief: "a tab ... that reproduces [STBEMU] but with controls" -
 * see [com.sconcept.mirrordash.iptv.StalkerPortalClient]'s doc comment for the protocol it
 * speaks). Only what a Stalker/Ministra portal actually needs to authenticate a device: portal
 * address and MAC address - no username/password, that's the whole point of MAC-based auth.
 */
@Composable
fun IptvSettingsContent(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val settings = uiState.settings

    SettingRow(
        title = "Show IPTV tab",
        subtitle = if (!settings.iptvEnabled && (settings.iptvPortalUrl.isBlank() || settings.iptvMacAddress.isBlank())) {
            "Add a portal address and MAC address below first"
        } else {
            "Swipe from Clock to reach it"
        },
    ) {
        Switch(
            checked = settings.iptvEnabled,
            onCheckedChange = viewModel::setIptvEnabled,
            enabled = (settings.iptvPortalUrl.isNotBlank() && settings.iptvMacAddress.isNotBlank()) || settings.iptvEnabled,
            colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
        )
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Portal address") {
        Text(
            "The Stalker/Ministra portal URL your provider gave you, e.g. http://provider.example:8080/c/ " +
                "or a full .../portal.php address.",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(10.dp))
        BufferedTextField(
            persistedValue = settings.iptvPortalUrl,
            onValueChange = viewModel::setIptvPortalUrl,
            placeholder = { Text("http://") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "MAC address") {
        Text(
            "Identifies this device to the portal in place of a username/password - most providers " +
                "need this exact value registered on their side before the portal will authorize it.",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            BufferedTextField(
                persistedValue = settings.iptvMacAddress,
                onValueChange = { viewModel.setIptvMacAddress(IptvMac.normalize(it)) },
                placeholder = { Text("00:1A:79:XX:XX:XX") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            TextButton(onClick = viewModel::regenerateIptvMac) {
                Text("Generate", color = MDTheme.colors.accent)
            }
        }
        if (settings.iptvMacAddress.isNotBlank() && !IptvMac.isValid(settings.iptvMacAddress)) {
            Spacer(Modifier.height(6.dp))
            Text("Expected format: 00:1A:79:XX:XX:XX", style = MDTheme.type.caption, color = MDTheme.colors.danger)
        }
    }

    if (settings.iptvPortalUrl.isNotBlank() && IptvMac.isValid(settings.iptvMacAddress)) {
        Spacer(Modifier.height(28.dp))
        IptvAccountCard(portalUrl = settings.iptvPortalUrl, macAddress = settings.iptvMacAddress)
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Player") {
        Text(
            "Which engine plays live TV and Movies/Series. If the selected one can't play a " +
                "given stream, MirrorDash automatically tries the other two before showing an " +
                "error - this only picks which one to start with (also changeable per-stream from " +
                "the player controls themselves). VLC and IjkPlayer both tolerate unusual/malformed " +
                "streams ExoPlayer sometimes rejects outright. IjkPlayer is a community-maintained " +
                "fork of a project with no official support left - the most capable fallback of " +
                "the three, but also the least maintained.",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(10.dp))
        val currentBackend = PlayerBackend.fromStorageKey(settings.iptvPlayerBackend)
        PlayerBackend.entries.forEach { backend ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            ) {
                RadioButton(
                    selected = currentBackend == backend,
                    onClick = { viewModel.setIptvPlayerBackend(backend) },
                    colors = RadioButtonDefaults.colors(selectedColor = MDTheme.colors.accent),
                )
                Text(backend.displayName, style = MDTheme.type.body, color = MDTheme.colors.textPrimary)
            }
        }
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Sleep timeout") {
        Text(
            "How long the tab keeps its connection paused-but-warm after you swipe away before " +
                "dropping it completely and freeing its memory. Coming back after that reconnects " +
                "from scratch.",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(6.dp))
        Text("${settings.iptvSleepTimeoutSeconds}s", style = MDTheme.type.settingSubtitle, color = MDTheme.colors.textSecondary)
        Slider(
            value = settings.iptvSleepTimeoutSeconds.toFloat(),
            onValueChange = { viewModel.setIptvSleepTimeoutSeconds(it.toInt()) },
            valueRange = 10f..600f,
            colors = SliderDefaults.colors(thumbColor = MDTheme.colors.accent, activeTrackColor = MDTheme.colors.accent),
        )
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Parental control") {
        Text(
            "Gates whatever this portal itself flags as adult content - the portal never checks " +
                "any PIN on its own, so this is enforced entirely here, in the app.",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(10.dp))
        val currentMode = ParentalControlMode.fromStorageKey(settings.parentalControlMode)
        listOf(
            ParentalControlMode.DISABLED to "Disabled (open)",
            ParentalControlMode.ONCE_PER_SESSION to "Ask once per session",
            ParentalControlMode.EVERY_REJOIN to "Ask every time the tab is left and rejoined",
        ).forEach { (mode, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            ) {
                RadioButton(
                    selected = currentMode == mode,
                    onClick = { viewModel.setParentalControlMode(mode) },
                    colors = RadioButtonDefaults.colors(selectedColor = MDTheme.colors.accent),
                )
                Text(label, style = MDTheme.type.body, color = MDTheme.colors.textPrimary)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("PIN", style = MDTheme.type.settingSubtitle, color = MDTheme.colors.textSecondary)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            BufferedTextField(
                persistedValue = settings.parentalControlPin,
                onValueChange = viewModel::setParentalControlPin,
                placeholder = { Text(DEFAULT_PARENTAL_CONTROL_PIN) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            TextButton(onClick = viewModel::resetParentalControlPin) {
                Text("Reset to default", color = MDTheme.colors.accent)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Up to $MAX_PARENTAL_CONTROL_PIN_LENGTH digits, default $DEFAULT_PARENTAL_CONTROL_PIN.",
            style = MDTheme.type.caption,
            color = MDTheme.colors.textTertiary,
        )
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Recording connection") {
        Text(
            "This portal allows one active connection at a time - so recording a different " +
                "channel than what's playing needs a connection of its own, or it briefly takes " +
                "over and interrupts live viewing. If your subscription includes a second MAC " +
                "(most providers issue extra ones on the same portal for exactly this), add it " +
                "here. Leave blank to share the one connection instead.",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            BufferedTextField(
                persistedValue = settings.iptvRecordingMacAddress,
                onValueChange = { viewModel.setIptvRecordingMacAddress(IptvMac.normalize(it)) },
                placeholder = { Text("Same connection as live viewing") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            TextButton(onClick = viewModel::regenerateIptvRecordingMac) {
                Text("Generate", color = MDTheme.colors.accent)
            }
        }
        if (settings.iptvRecordingMacAddress.isNotBlank() && !IptvMac.isValid(settings.iptvRecordingMacAddress)) {
            Spacer(Modifier.height(6.dp))
            Text("Expected format: 00:1A:79:XX:XX:XX", style = MDTheme.type.caption, color = MDTheme.colors.danger)
        }
        if (settings.iptvRecordingMacAddress.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(
                "Recording portal (optional)",
                style = MDTheme.type.settingSubtitle,
                color = MDTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(6.dp))
            BufferedTextField(
                persistedValue = settings.iptvRecordingPortalUrl,
                onValueChange = viewModel::setIptvRecordingPortalUrl,
                placeholder = { Text("Same portal as live viewing") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Recording destination") {
        Text(
            "Both are always available as a fallback for each other - this only picks which is " +
                "tried first. Recording is a raw copy of the stream, not a screen capture, so it " +
                "keeps going even backgrounded or muted.",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(10.dp))
        val currentMode = RecordingDestinationMode.fromStorageKey(settings.iptvRecordingDestination)
        listOf(
            RecordingDestinationMode.SMB_PRIMARY to "NAS first (recommended)",
            RecordingDestinationMode.LOCAL_PRIMARY to "Local storage first",
        ).forEach { (mode, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            ) {
                RadioButton(
                    selected = currentMode == mode,
                    onClick = { viewModel.setIptvRecordingDestination(mode) },
                    colors = RadioButtonDefaults.colors(selectedColor = MDTheme.colors.accent),
                )
                Text(label, style = MDTheme.type.body, color = MDTheme.colors.textPrimary)
            }
        }
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "NAS recordings folder") {
        Text(
            "Subfolder on the same NAS connection configured under Photorama - recordings don't " +
                "need a second, separate connection.",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(10.dp))
        BufferedTextField(
            persistedValue = settings.iptvRecordingSmbFolder,
            onValueChange = viewModel::setIptvRecordingSmbFolder,
            placeholder = { Text("MirrorDash Recordings") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Download folder") {
        Text(
            "Where movies/series downloaded from the Movies and Series tabs are saved. Leave " +
                "blank to use the same folder as recordings above.",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(10.dp))
        BufferedTextField(
            persistedValue = settings.iptvDownloadFolderName,
            onValueChange = viewModel::setIptvDownloadFolderName,
            placeholder = { Text("Same as recordings") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Local storage cap") {
        Text(
            "Oldest local recordings are deleted first once this is exceeded - local storage on " +
                "this hardware is limited, so this is meant as a fallback buffer, not the main " +
                "destination.",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(6.dp))
        Text("${settings.iptvRecordingLocalCapMb} MB", style = MDTheme.type.settingSubtitle, color = MDTheme.colors.textSecondary)
        Slider(
            value = settings.iptvRecordingLocalCapMb.toFloat(),
            onValueChange = { viewModel.setIptvRecordingLocalCapMb(it.toInt()) },
            valueRange = 100f..2000f,
            colors = SliderDefaults.colors(thumbColor = MDTheme.colors.accent, activeTrackColor = MDTheme.colors.accent),
        )
    }

    Spacer(Modifier.height(16.dp))

    Text(
        "Best-effort Stalker/Ministra client - most portals work out of the box, but some providers " +
            "customize the protocol enough that a channel list or stream may not load.",
        style = MDTheme.type.caption,
        color = MDTheme.colors.textTertiary,
    )
}

private sealed class AccountCardState {
    object Loading : AccountCardState()
    data class Error(val message: String) : AccountCardState()
    data class Loaded(val info: StalkerAccountInfo) : AccountCardState()
}

/** Auto-fetches and shows whatever the portal returns for this account once a valid portal
 * URL/MAC are both set - see [com.sconcept.mirrordash.iptv.StalkerPortalClient.fetchAccountInfo].
 * Goes through [AppContainer.iptvSessionCoordinator] rather than opening its own
 * [com.sconcept.mirrordash.iptv.StalkerPortalClient] - this portal allows only one active session
 * per MAC (see [com.sconcept.mirrordash.iptv.IptvSessionCoordinator]'s doc comment), and the IPTV
 * tab may still be holding one (SLEEPING, not yet timed out) while Settings is open, so a second,
 * independent handshake here would silently kill it. */
@Composable
private fun IptvAccountCard(portalUrl: String, macAddress: String) {
    val context = LocalContext.current
    val sessionCoordinator = remember { AppContainer.get(context).iptvSessionCoordinator }
    var state by remember { mutableStateOf<AccountCardState>(AccountCardState.Loading) }

    LaunchedEffect(portalUrl, macAddress) {
        state = AccountCardState.Loading
        sessionCoordinator.acquire(portalUrl, macAddress)
            .onSuccess { client ->
                val result = client.fetchAccountInfo()
                sessionCoordinator.release()
                state = result.fold(
                    onSuccess = { info -> AccountCardState.Loaded(info) },
                    onFailure = { error -> AccountCardState.Error(error.message ?: "Couldn't load account details") },
                )
            }
            .onFailure { error -> state = AccountCardState.Error(error.message ?: "Couldn't connect to the portal") }
    }

    SettingGroup(title = "Account") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MDTheme.colors.surfaceElevated)
                .padding(16.dp),
        ) {
            when (val s = state) {
                is AccountCardState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MDTheme.colors.accent)
                    Spacer(Modifier.width(10.dp))
                    Text("Checking account…", style = MDTheme.type.settingSubtitle, color = MDTheme.colors.textSecondary)
                }
                is AccountCardState.Error -> Text(s.message, style = MDTheme.type.settingSubtitle, color = MDTheme.colors.textSecondary)
                is AccountCardState.Loaded -> AccountCardContent(s.info)
            }
        }
    }
}

@Composable
private fun AccountCardContent(info: StalkerAccountInfo) {
    if (info.blocked) {
        Text(
            "BLOCKED",
            style = MDTheme.type.caption,
            color = MDTheme.colors.danger,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(MDTheme.colors.danger.copy(alpha = 0.16f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            info.blockMessage?.ifBlank { null } ?: "This account can't stream right now.",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(10.dp))
    }

    val rows = listOfNotNull(
        info.expiryEpochSeconds?.let { (if (info.blocked) "Expired" else "Expires") to formatAccountExpiry(it) },
        info.fullName?.let { "Name" to it },
        info.login?.let { "Login" to it },
        info.tariffPlan?.let { "Plan" to it },
        info.phone?.let { "Phone" to it },
        info.email?.let { "Email" to it },
        "MAC" to info.mac,
    )
    if (rows.size == 1 && !info.blocked) {
        // Only MAC came back - the provider didn't return anything else worth a card for.
        Text(
            "This provider didn't return any additional account details.",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        return
    }
    rows.forEachIndexed { index, (label, value) ->
        if (index > 0) Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MDTheme.type.settingSubtitle, color = MDTheme.colors.textSecondary, modifier = Modifier.width(90.dp))
            Text(value, style = MDTheme.type.body, color = MDTheme.colors.textPrimary)
        }
    }
}
