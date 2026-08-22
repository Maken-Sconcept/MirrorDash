package com.sconcept.mirrordash.launcher.notifications

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.media.AudioManager
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sconcept.mirrordash.brightness.BrightnessMath
import com.sconcept.mirrordash.ui.theme.MDTheme
import com.sconcept.mirrordash.walkietalkie.WalkieTalkieDeviceBar
import com.sconcept.mirrordash.walkietalkie.WalkieTalkieUiState
import com.sconcept.mirrordash.walkietalkie.model.WalkieTalkiePeer
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Swipe-down-from-top notification panel (brief section 27-28). Built on
 * [NotificationRepository], which is fed by [MirrorDashNotificationListenerService] once the
 * user grants Notification Access - there's no privileged/root path here per the scoping
 * decision (assume an unprivileged install).
 */
@Composable
fun NotificationsScreen(
    accessState: NotificationAccessState,
    notifications: List<MirrorDashNotification>,
    onOpen: (MirrorDashNotification) -> Unit,
    onDismiss: (MirrorDashNotification) -> Unit,
    onClearAll: () -> Unit,
    onGrantAccess: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    brightnessLevel255: Int,
    onBrightnessChange: (Int) -> Unit,
    walkieState: WalkieTalkieUiState = WalkieTalkieUiState(),
    homeShortcutIps: Set<String> = emptySet(),
    onHomeShortcutChange: (WalkieTalkiePeer, Boolean) -> Unit = { _, _ -> },
    onTalkStart: (WalkieTalkiePeer) -> Unit = {},
    onTalkEnd: () -> Unit = {},
    onRefreshWalkieTalkieDevices: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val timeText by rememberClockTicker()

    Column(
        modifier = modifier
            .fillMaxSize()
            // Translucent rather than the fully opaque backgroundElevated every other panel
            // uses - the point of a quick-settings drop-down is seeing the page underneath
            // still peeking through, same as stock Android's shade. No blur (RenderEffect
            // needs API 31+, this runs on API 25 hardware) - flat alpha is the honest option.
            .background(MDTheme.colors.backgroundElevated.copy(alpha = 0.86f))
            .padding(top = 28.dp, start = 40.dp, end = 40.dp, bottom = 24.dp),
    ) {
        // Landscape-width layout throughout, matching every other panel here (App Drawer,
        // Settings): time/date and the gear sit side by side rather than stacked, and the four
        // toggles below span the row evenly instead of the narrow single-column stack a phone's
        // status bar dropdown uses.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Text(timeText, style = MDTheme.type.sectionTitle, color = MDTheme.colors.textPrimary)
                Text(todayLabel(), style = MDTheme.type.settingSubtitle, color = MDTheme.colors.textSecondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) { Text("Close", color = MDTheme.colors.textSecondary) }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MDTheme.colors.surface)
                        .clickable(onClick = onOpenSettings)
                        .padding(12.dp),
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = MDTheme.colors.textSecondary, modifier = Modifier.size(22.dp))
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        QuickSettingsRow()

        Spacer(Modifier.height(20.dp))

        BrightnessSliderRow(level255 = brightnessLevel255, onLevelChange = onBrightnessChange)

        Spacer(Modifier.height(12.dp))

        VolumeSliderRow()

        Spacer(Modifier.height(24.dp))

        if (walkieState.enabled || walkieState.peers.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Rooms & devices", style = MDTheme.type.sectionTitle, color = MDTheme.colors.textPrimary)
                IconButton(
                    onClick = onRefreshWalkieTalkieDevices,
                    enabled = walkieState.enabled,
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Refresh available devices",
                        tint = if (walkieState.enabled) MDTheme.colors.accent else MDTheme.colors.textTertiary,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            WalkieTalkieDeviceBar(
                peers = walkieState.peers,
                discoveredIps = walkieState.discoveredPeers.map { it.ip }.toSet(),
                shortcutIps = homeShortcutIps,
                activeIncomingIp = walkieState.activeIncomingPeerIp,
                activeTransmitIp = walkieState.activeTransmitTarget,
                talkEnabled = walkieState.enabled && walkieState.hasMicPermission,
                onShortcutChange = onHomeShortcutChange,
                onTalkStart = onTalkStart,
                onTalkEnd = onTalkEnd,
            )
            if (walkieState.peers.isEmpty()) {
                Text(
                    "No saved rooms yet. Refresh scans for nearby devices.",
                    style = MDTheme.type.settingSubtitle,
                    color = MDTheme.colors.textTertiary,
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Notifications", style = MDTheme.type.sectionTitle, color = MDTheme.colors.textPrimary)
            if (notifications.isNotEmpty()) {
                TextButton(onClick = onClearAll) {
                    Text("Clear all", color = MDTheme.colors.accent)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        when {
            accessState == NotificationAccessState.NOT_GRANTED -> EmptyState(
                title = "Notification access is off",
                subtitle = "Grant access so notifications from your apps can show up here.",
                actionLabel = "Grant access",
                onAction = onGrantAccess,
            )
            notifications.isEmpty() -> EmptyState(
                title = "You're all caught up",
                subtitle = "New notifications will appear here.",
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(notifications, key = { it.key }) { notification ->
                    NotificationRow(
                        notification = notification,
                        onOpen = { onOpen(notification) },
                        onDismiss = { onDismiss(notification) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        androidx.compose.material3.Icon(
            Icons.Filled.NotificationsNone,
            contentDescription = null,
            tint = MDTheme.colors.textTertiary,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(title, style = MDTheme.type.settingTitle, color = MDTheme.colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = MDTheme.type.settingSubtitle, color = MDTheme.colors.textSecondary)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            TextButton(onClick = onAction) {
                Text(actionLabel, color = MDTheme.colors.accent)
            }
        }
    }
}

@Composable
private fun NotificationRow(notification: MirrorDashNotification, onOpen: () -> Unit, onDismiss: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MDTheme.colors.surface)
            .clickable(onClick = onOpen)
            .padding(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MDTheme.colors.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            notification.appIcon?.let { DrawableThumbnail(it, sizeDp = 26) }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(notification.appLabel, style = MDTheme.type.caption, color = MDTheme.colors.textTertiary)
            Text(
                notification.title.ifBlank { notification.appLabel },
                style = MDTheme.type.settingTitle,
                color = MDTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (notification.text.isNotBlank()) {
                Text(
                    notification.text,
                    style = MDTheme.type.settingSubtitle,
                    color = MDTheme.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(relativeTime(notification.postedAtMs), style = MDTheme.type.caption, color = MDTheme.colors.textTertiary)
        if (notification.isClearable) {
            Spacer(Modifier.width(12.dp))
            Text(
                "Dismiss",
                style = MDTheme.type.caption,
                color = MDTheme.colors.textTertiary,
                modifier = Modifier.clickable(onClick = onDismiss),
            )
        }
    }
}

/** Wi-Fi / Bluetooth / Do Not Disturb / Airplane mode, evenly spaced across the panel's full
 * width - see [QuickSettingsController] for what each tap actually does. Re-reads state after
 * every tap and once on first show, since a prior session could have left Wi-Fi/Bluetooth/DND/
 * Airplane in a different state than whatever this composable last knew about - plus a slow
 * background poll while the panel is open, since e.g. Wi-Fi actually powering down after
 * `setWifiEnabled(false)` returns isn't instant, and re-reading state immediately after the call
 * can still observe the old value for a moment. */
@Composable
private fun QuickSettingsRow() {
    val context = LocalContext.current
    var states by remember { mutableStateOf(QuickSettingsController.currentStates(context)) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                states = QuickSettingsController.currentStates(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1500)
            states = QuickSettingsController.currentStates(context)
        }
    }

    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        states.forEach { state ->
            QuickSettingTile(
                state = state,
                onClick = {
                    val toggled = QuickSettingsController.toggle(context, state.kind, !state.enabled)
                    if (!toggled) {
                        runCatching { context.startActivity(QuickSettingsController.settingsIntentFor(state.kind)) }
                    }
                    states = QuickSettingsController.currentStates(context)
                },
            )
        }
    }
}

@Composable
private fun QuickSettingTile(state: QuickSettingState, onClick: () -> Unit) {
    val (icon, label) = when (state.kind) {
        QuickSettingKind.WIFI -> Icons.Filled.Wifi to "Wi-Fi"
        QuickSettingKind.BLUETOOTH -> Icons.Filled.Bluetooth to "Bluetooth"
        QuickSettingKind.DND -> Icons.Filled.DoNotDisturbOn to "Do Not Disturb"
        QuickSettingKind.AIRPLANE -> Icons.Filled.AirplanemodeActive to "Airplane mode"
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (state.enabled) MDTheme.colors.accent else MDTheme.colors.surface)
                .clickable(enabled = state.available, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (state.enabled) MDTheme.colors.onAccent else MDTheme.colors.textSecondary,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MDTheme.type.caption, color = MDTheme.colors.textSecondary)
    }
}

/** Mirrors [com.sconcept.mirrordash.settings.ui.BrightnessSettingsContent]'s own "Backlight
 * level" slider exactly - same [BrightnessMath.QUICK_CONTROL_STEPS] stepped scale, same setter
 * call - so this and the Settings screen are just two views onto the one
 * `settingsRepository.brightnessLevel255` value; dragging either updates the other live with no
 * extra wiring, the same way [MirrorDashActivity]'s brightness collectors already react to any
 * write regardless of where it came from. */
@Composable
private fun BrightnessSliderRow(level255: Int, onLevelChange: (Int) -> Unit) {
    val steps = BrightnessMath.QUICK_CONTROL_STEPS
    val stepIndex = BrightnessMath.stepIndexFor(level255)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(
            Icons.Filled.Brightness6,
            contentDescription = "Brightness",
            tint = MDTheme.colors.textSecondary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Slider(
            value = stepIndex.toFloat(),
            onValueChange = { onLevelChange(steps[it.toInt().coerceIn(0, steps.lastIndex)]) },
            valueRange = 0f..steps.lastIndex.toFloat(),
            steps = steps.size - 2,
            colors = SliderDefaults.colors(thumbColor = MDTheme.colors.accent, activeTrackColor = MDTheme.colors.accent),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            if (level255 == 0) "Off" else "${level255 * 100 / 255}%",
            style = MDTheme.type.caption,
            color = MDTheme.colors.textSecondary,
            modifier = Modifier.width(36.dp),
        )
    }
}

/** System media volume ([AudioManager.STREAM_MUSIC]) - same direct-AudioManager approach as
 * [com.sconcept.mirrordash.airplay.AirPlayMirrorSurface]'s volume overlay, the only other place
 * in the app that touches the system stream. Tapping the icon mutes/restores (remembering the
 * level from just before muting), same idea as a physical mute button. Polls on resume plus a
 * slow background tick - same staleness handling [QuickSettingsRow] already uses for Wi-Fi/
 * Bluetooth/DND - since a physical volume-key press changes the stream with no callback this
 * composable would otherwise see. */
@Composable
private fun VolumeSliderRow() {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    var volumeBeforeMute by remember { mutableIntStateOf(volume.takeIf { it > 0 } ?: (maxVolume / 2)) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1500)
            volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = when {
                volume <= 0 -> Icons.AutoMirrored.Filled.VolumeOff
                volume < maxVolume / 2 -> Icons.AutoMirrored.Filled.VolumeDown
                else -> Icons.AutoMirrored.Filled.VolumeUp
            },
            contentDescription = if (volume <= 0) "Unmute" else "Mute",
            tint = MDTheme.colors.textSecondary,
            modifier = Modifier
                .size(22.dp)
                .clickable {
                    if (volume > 0) {
                        volumeBeforeMute = volume
                        volume = 0
                    } else {
                        volume = volumeBeforeMute
                    }
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                },
        )
        Spacer(Modifier.width(14.dp))
        Slider(
            value = volume.toFloat(),
            valueRange = 0f..maxVolume.toFloat(),
            onValueChange = { newValue ->
                volume = newValue.toInt()
                if (volume > 0) volumeBeforeMute = volume
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
            },
            colors = SliderDefaults.colors(thumbColor = MDTheme.colors.accent, activeTrackColor = MDTheme.colors.accent),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun rememberClockTicker() = produceState(initialValue = formatTime()) {
    while (true) {
        value = formatTime()
        val now = java.util.Calendar.getInstance()
        val msToNextMinute = 60_000L - (now.get(java.util.Calendar.SECOND) * 1000L + now.get(java.util.Calendar.MILLISECOND))
        kotlinx.coroutines.delay(msToNextMinute.coerceAtLeast(1000L))
    }
}

private fun formatTime(): String = SimpleDateFormat("H:mm", Locale.getDefault()).format(Date())

private fun todayLabel(): String = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())

@Composable
private fun DrawableThumbnail(drawable: Drawable, sizeDp: Int) {
    val bitmap = remember(drawable) {
        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bmp.asImageBitmap()
    }
    Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(sizeDp.dp))
}

private fun relativeTime(atMs: Long): String {
    val diffMs = System.currentTimeMillis() - atMs
    val minutes = diffMs / 60_000
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 24 * 60 -> "${minutes / 60}h"
        else -> DateFormat.getDateInstance(DateFormat.SHORT).format(Date(atMs))
    }
}
