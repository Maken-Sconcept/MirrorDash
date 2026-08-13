package com.sconcept.mirrordash.launcher.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.lang.ref.WeakReference

enum class NotificationAccessState {
    NOT_GRANTED,
    GRANTED,
}

data class MirrorDashNotification(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val appIcon: Drawable?,
    val title: String,
    val text: String,
    val postedAtMs: Long,
    val isClearable: Boolean,
    val contentIntent: PendingIntent?,
)

/**
 * Process-wide bridge between [MirrorDashNotificationListenerService] (which only exists while
 * bound by the system) and the Compose notification panel. A singleton object rather than a
 * class because there is exactly one listener connection per process and multiple UI
 * surfaces (panel, settings access-state row) all want the same live feed.
 */
object NotificationRepository {

    private val _notifications = MutableStateFlow<List<MirrorDashNotification>>(emptyList())
    val notifications: StateFlow<List<MirrorDashNotification>> = _notifications

    private val _accessState = MutableStateFlow(NotificationAccessState.NOT_GRANTED)
    val accessState: StateFlow<NotificationAccessState> = _accessState

    private var serviceRef: WeakReference<MirrorDashNotificationListenerService>? = null

    fun attach(service: MirrorDashNotificationListenerService) {
        serviceRef = WeakReference(service)
        _accessState.value = NotificationAccessState.GRANTED
    }

    fun detach(service: MirrorDashNotificationListenerService) {
        if (serviceRef?.get() === service) {
            serviceRef = null
        }
        _accessState.value = NotificationAccessState.NOT_GRANTED
        _notifications.value = emptyList()
    }

    fun replaceAll(sbns: List<StatusBarNotification>) {
        val service = serviceRef?.get() ?: return
        _notifications.value = sbns
            .mapNotNull { it.toMirrorDashNotification(service) }
            .sortedByDescending { it.postedAtMs }
    }

    fun onPosted(sbn: StatusBarNotification) {
        val service = serviceRef?.get() ?: return
        val mapped = sbn.toMirrorDashNotification(service) ?: return
        _notifications.update { current ->
            (listOf(mapped) + current.filterNot { it.key == mapped.key }).sortedByDescending { it.postedAtMs }
        }
    }

    fun onRemoved(sbn: StatusBarNotification) {
        _notifications.update { current -> current.filterNot { it.key == sbn.key } }
    }

    fun dismiss(key: String) {
        serviceRef?.get()?.dismiss(key)
        _notifications.update { current -> current.filterNot { it.key == key } }
    }

    fun clearAll() {
        serviceRef?.get()?.dismissAll()
        _notifications.value = emptyList()
    }

    /** Static check (works even before the listener has connected) for whether the user has
     * granted Notification Access, used to drive the Settings row without needing a live
     * service binding. */
    fun isAccessGranted(context: Context): Boolean {
        val enabled = NotificationManagerCompat.getEnabledListenerPackages(context)
        return context.packageName in enabled
    }

    private fun StatusBarNotification.toMirrorDashNotification(
        service: MirrorDashNotificationListenerService,
    ): MirrorDashNotification? {
        val extras = notification.extras
        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null

        val pm = service.packageManager
        val appLabel = runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
        val appIcon = runCatching { pm.getApplicationIcon(packageName) }.getOrNull()

        return MirrorDashNotification(
            key = key,
            packageName = packageName,
            appLabel = appLabel,
            appIcon = appIcon,
            title = title,
            text = text,
            postedAtMs = postTime,
            isClearable = isClearable,
            contentIntent = notification.contentIntent,
        )
    }
}
