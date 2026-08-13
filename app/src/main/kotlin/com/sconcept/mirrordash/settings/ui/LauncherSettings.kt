package com.sconcept.mirrordash.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                "matches how the mirror is mounted is the reliable option. Auto is best-effort.",
            style = MDTheme.type.settingSubtitle,
            color = MDTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(12.dp))

        val current = DisplayOrientationMode.fromStorageKey(uiState.settings.displayOrientationMode)
        listOf(
            DisplayOrientationMode.AUTO to "Auto (follow accelerometer)",
            DisplayOrientationMode.LANDSCAPE to "Landscape",
            DisplayOrientationMode.REVERSE_LANDSCAPE to "Landscape (upside down)",
            DisplayOrientationMode.PORTRAIT to "Vertical",
            DisplayOrientationMode.REVERSE_PORTRAIT to "Vertical (upside down)",
        ).forEach { (mode, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            ) {
                RadioButton(
                    selected = current == mode,
                    onClick = { viewModel.setDisplayOrientationMode(mode) },
                    colors = RadioButtonDefaults.colors(selectedColor = MDTheme.colors.accent),
                )
                Text(label, style = MDTheme.type.body, color = MDTheme.colors.textPrimary)
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
                "Grant \"Display over other apps\" - on this hardware it's what actually makes Vertical stick, not just the two Landscape modes.",
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

    SettingGroup(title = "About") {
        Text("MirrorDash 1.0.0", style = MDTheme.type.settingSubtitle, color = MDTheme.colors.textSecondary)
    }
}
