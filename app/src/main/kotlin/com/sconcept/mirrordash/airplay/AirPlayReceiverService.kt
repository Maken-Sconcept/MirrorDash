package com.sconcept.mirrordash.airplay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sconcept.mirrordash.R
import com.sconcept.mirrordash.launcher.AppContainer
import com.sconcept.mirrordash.launcher.MirrorDashActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val CHANNEL_ID = "airplay_receiver"
private const val NOTIFICATION_ID = 4201
private const val WATCHDOG_INTERVAL_MS = 30_000L

/**
 * Keeps the AirPlay receiver alive independent of any Activity's lifecycle - the single biggest
 * gap identified when porting BerthierOptions' implementation (there, the native receiver was
 * only ever started from an Activity's `onResume()`, with no guarantee against the process being
 * killed while backgrounded). A low-priority foreground notification plus a periodic watchdog
 * (restarts the receiver if it's supposed to be running but isn't, e.g. after a transient native
 * start failure at boot before the network is ready) is what makes this "robust" the way a
 * dedicated AirPlay-receiver app like AirPin Pro is - a real background server, not something
 * tied to a settings screen being on top.
 */
class AirPlayReceiverService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watchdogJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Ready for AirPlay"))

        val container = AppContainer.get(applicationContext)

        scope.launch {
            container.airPlayEngine.uiState.collect { state ->
                if (!state.enabled) {
                    stopSelf()
                    return@collect
                }
                updateNotification(state.statusLabel)
            }
        }

        watchdogJob = scope.launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                container.airPlayEngine.watchdogCheck()
            }
        }

        scope.launch {
            container.settingsRepository.settings
                .map { it.airplayEnabled }
                .distinctUntilChanged()
                .collect { enabled -> if (!enabled) stopSelf() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        watchdogJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "AirPlay receiver", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Shows while MirrorDash is ready to accept AirPlay connections"
                setShowBadge(false)
            },
        )
    }

    private fun buildNotification(statusText: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MirrorDashActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("AirPlay")
            .setContentText(statusText)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    companion object {
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, AirPlayReceiverService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AirPlayReceiverService::class.java))
        }
    }
}
