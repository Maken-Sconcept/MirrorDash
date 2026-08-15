package com.sconcept.mirrordash.iptv

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
import kotlinx.coroutines.launch
import java.util.Locale

private const val CHANNEL_ID = "iptv_recording"
private const val NOTIFICATION_ID = 4202

/**
 * A thin foreground-priority wrapper - the recording itself doesn't live here.
 * [IptvRecordingEngine] owns the actual byte-copy loop in its own long-lived scope and keeps
 * running regardless of this service's lifecycle; this exists only so Android doesn't
 * deprioritize/kill the process mid-recording, and to show the ongoing notification a real
 * recording indicator needs - same shape as
 * [com.sconcept.mirrordash.airplay.AirPlayReceiverService].
 */
class IptvRecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification(null))

        val engine = AppContainer.get(applicationContext).iptvRecordingEngine
        observeJob = scope.launch {
            engine.uiState.collect { state ->
                val active = state.activeRecording
                if (active == null) {
                    stopSelf()
                } else {
                    updateNotification(active)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        observeJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "IPTV recording", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows while a live TV recording is in progress"
                setShowBadge(false)
            },
        )
    }

    private fun buildNotification(active: ActiveRecording?): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MirrorDashActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = active?.let { "${it.channelName} — ${formatMegabytes(it.bytesWritten)} — ${it.destinationLabel}" }
            ?: "Starting…"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Recording")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(active: ActiveRecording) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification(active))
    }

    private fun formatMegabytes(bytes: Long): String = String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))

    companion object {
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, IptvRecordingService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, IptvRecordingService::class.java))
        }
    }
}
