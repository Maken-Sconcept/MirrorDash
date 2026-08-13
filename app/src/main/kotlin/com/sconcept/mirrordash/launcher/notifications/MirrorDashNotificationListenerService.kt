package com.sconcept.mirrordash.launcher.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Bridges active system notifications into [NotificationRepository] so the launcher's own
 * notification panel (brief section 28) can render them without needing privileged/system
 * access to expand the real status-bar shade. The user must grant Notification Access once
 * (Settings > Launcher > Notification Access); until then [NotificationRepository] simply
 * reports [NotificationAccessState.NotGranted].
 */
class MirrorDashNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationRepository.attach(this)
        NotificationRepository.replaceAll(activeNotifications?.toList().orEmpty())
    }

    override fun onListenerDisconnected() {
        NotificationRepository.detach(this)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        NotificationRepository.onPosted(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        NotificationRepository.onRemoved(sbn)
    }

    fun dismiss(key: String) {
        cancelNotification(key)
    }

    fun dismissAll() {
        cancelAllNotifications()
    }
}
