package com.sconcept.mirrordash.launcher.notifications

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
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
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sconcept.mirrordash.ui.theme.MDTheme
import java.text.DateFormat
import java.util.Date

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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MDTheme.colors.backgroundElevated)
            .padding(top = 28.dp, start = 40.dp, end = 40.dp, bottom = 24.dp),
    ) {
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
