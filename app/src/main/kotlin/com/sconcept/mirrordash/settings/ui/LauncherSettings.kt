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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.StayCurrentLandscape
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sconcept.mirrordash.launcher.HomeRoleHelper
import com.sconcept.mirrordash.launcher.display.DisplayOrientationController
import com.sconcept.mirrordash.launcher.display.DisplayOrientationMode
import com.sconcept.mirrordash.launcher.notifications.NotificationRepository
import com.sconcept.mirrordash.settings.SettingsUiState
import com.sconcept.mirrordash.settings.SettingsViewModel
import com.sconcept.mirrordash.ui.theme.MDTheme

@Composable
fun LauncherSettingsContent(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    onRequestHomeRole: () -> Unit,
    onRequestNotificationAccess: () -> Unit,
    onRequestWriteSettingsAccess: () -> Unit,
    onRequestOverlayAccess: () -> Unit,
) {
    val context = LocalContext.current
    var isDefaultLauncher by remember { mutableStateOf(HomeRoleHelper.isDefaultLauncher(context)) }
    var notificationAccessGranted by remember { mutableStateOf(NotificationRepository.isAccessGranted(context)) }
    var writeSettingsGranted by remember { mutableStateOf(DisplayOrientationController.hasWriteSettingsPermission(context)) }
    var overlayGranted by remember { mutableStateOf(DisplayOrientationController.hasOverlayPermission(context)) }

    // Re-check on every resume, not just first composition - the user grants these permissions
    // on a separate system screen (Notification Access, Modify System Settings) and comes right
    // back here, so a one-shot check would keep showing stale "not granted" prompts.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isDefaultLauncher = HomeRoleHelper.isDefaultLauncher(context)
                notificationAccessGranted = NotificationRepository.isAccessGranted(context)
                writeSettingsGranted = DisplayOrientationController.hasWriteSettingsPermission(context)
                overlayGranted = DisplayOrientationController.hasOverlayPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingGroup(title = "Device name") {
        Text(
            "Used everywhere this unit identifies itself on the network - the AirPlay receiver " +
                "name and the name other units see it as in Walkie-Talkie.",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(10.dp))
        BufferedTextField(
            persistedValue = uiState.settings.deviceName,
            onValueChange = viewModel::setDeviceName,
            placeholder = { Text(com.sconcept.mirrordash.settings.DeviceNameHelper.defaultDeviceName(context)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Spacer(Modifier.height(28.dp))

    SettingRow(
        title = "Default launcher",
        subtitle = if (isDefaultLauncher) "MirrorDash is your Home app" else "Not currently your Home app",
    ) {
        if (!isDefaultLauncher) {
            Button(
                onClick = onRequestHomeRole,
                colors = ButtonDefaults.buttonColors(containerColor = MDTheme.colors.accent, contentColor = MDTheme.colors.onAccent),
            ) {
                Text("Set as Home")
            }
        }
    }

    Spacer(Modifier.height(24.dp))

    SettingRow(
        title = "Notification access",
        subtitle = if (notificationAccessGranted) "Granted" else "Needed for the notification panel",
    ) {
        if (!notificationAccessGranted) {
            Button(
                onClick = onRequestNotificationAccess,
                colors = ButtonDefaults.buttonColors(containerColor = MDTheme.colors.accent, contentColor = MDTheme.colors.onAccent),
            ) {
                Text("Grant access")
            }
        }
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Screen orientation") {
        Text(
            "This unit has no gyroscope, only an accelerometer - picking the orientation that " +
                "matches how the mirror is mounted is the reliable option.",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(12.dp))

        val current = DisplayOrientationMode.fromStorageKey(uiState.settings.displayOrientationMode)
        // AUTO was a fifth radio option pre-simplification - this hardware can't actually sense
        // rotation (see the doc comment on DisplayOrientationMode), so it was never a reliable
        // choice here anyway. A unit that happens to still be on it just shows neither icon
        // highlighted until Landscape or Portrait is picked, same as a fresh install would.
        val isFlipped = current == DisplayOrientationMode.REVERSE_LANDSCAPE || current == DisplayOrientationMode.REVERSE_PORTRAIT
        val isLandscape = current == DisplayOrientationMode.LANDSCAPE || current == DisplayOrientationMode.REVERSE_LANDSCAPE
        val isPortrait = current == DisplayOrientationMode.PORTRAIT || current == DisplayOrientationMode.REVERSE_PORTRAIT

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OrientationOptionButton(
                icon = Icons.Filled.StayCurrentLandscape,
                label = "Landscape",
                selected = isLandscape,
                onClick = {
                    viewModel.setDisplayOrientationMode(
                        if (isFlipped) DisplayOrientationMode.REVERSE_LANDSCAPE else DisplayOrientationMode.LANDSCAPE,
                    )
                },
                modifier = Modifier.weight(1f),
            )
            OrientationOptionButton(
                icon = Icons.Filled.StayCurrentPortrait,
                label = "Portrait",
                selected = isPortrait,
                onClick = {
                    viewModel.setDisplayOrientationMode(
                        if (isFlipped) DisplayOrientationMode.REVERSE_PORTRAIT else DisplayOrientationMode.PORTRAIT,
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }

        // The two icons above pick the axis; this reaches the two "upside down" mounts that used
        // to be their own radio rows, so every physical mounting position is still one tap away.
        if (isLandscape || isPortrait) {
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            ) {
                Switch(
                    checked = isFlipped,
                    onCheckedChange = { flipped ->
                        val mode = when {
                            isLandscape && flipped -> DisplayOrientationMode.REVERSE_LANDSCAPE
                            isLandscape -> DisplayOrientationMode.LANDSCAPE
                            flipped -> DisplayOrientationMode.REVERSE_PORTRAIT
                            else -> DisplayOrientationMode.PORTRAIT
                        }
                        viewModel.setDisplayOrientationMode(mode)
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = MDTheme.colors.accent),
                )
                Spacer(Modifier.width(10.dp))
                Text("Mounted upside down", style = MDTheme.type.body, color = MDTheme.colors.textPrimary)
            }
        }

        if (!writeSettingsGranted) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Grant \"Modify system settings\" so the chosen orientation sticks system-wide, not just inside MirrorDash.",
                style = MDTheme.type.caption,
                color = MDTheme.colors.textTertiary,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onRequestWriteSettingsAccess,
                colors = ButtonDefaults.buttonColors(containerColor = MDTheme.colors.accent, contentColor = MDTheme.colors.onAccent),
            ) {
                Text("Grant permission")
            }
        }

        if (!overlayGranted) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Grant \"Display over other apps\" - on this hardware it's what actually makes Portrait stick, not just the two Landscape modes.",
                style = MDTheme.type.caption,
                color = MDTheme.colors.textTertiary,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onRequestOverlayAccess,
                colors = ButtonDefaults.buttonColors(containerColor = MDTheme.colors.accent, contentColor = MDTheme.colors.onAccent),
            ) {
                Text("Grant permission")
            }
        }
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "Provisioning") {
        Text(
            "Seeds Jellyfin, Home Assistant, Walkie-Talkie, IPTV, and NAS from a config file at " +
                com.sconcept.mirrordash.provisioning.ProvisioningConfigLoader.configFile(context).absolutePath +
                " - push it there with adb, then use this button any time the file changes.",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = viewModel::reapplyProvisioningConfig,
            colors = ButtonDefaults.buttonColors(containerColor = MDTheme.colors.accent, contentColor = MDTheme.colors.onAccent),
        ) {
            Text("Re-apply config file")
        }
        uiState.provisioningStatus?.let { status ->
            Spacer(Modifier.height(8.dp))
            Text(
                status.summary,
                style = MDTheme.type.caption,
                color = if (status.isError) MDTheme.colors.danger else MDTheme.colors.textTertiary,
            )
        }
    }

    Spacer(Modifier.height(28.dp))

    SettingGroup(title = "About") {
        Text("MirrorDash 1.0.0", style = MDTheme.type.settingSubtitle, color = MDTheme.colors.textSecondary)
    }
}

@Composable
private fun OrientationOptionButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) MDTheme.colors.accent.copy(alpha = 0.16f) else MDTheme.colors.surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MDTheme.colors.accent else MDTheme.colors.textTertiary.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MDTheme.colors.accent else MDTheme.colors.textSecondary,
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            style = MDTheme.type.body,
            color = if (selected) MDTheme.colors.accent else MDTheme.colors.textPrimary,
        )
    }
}
