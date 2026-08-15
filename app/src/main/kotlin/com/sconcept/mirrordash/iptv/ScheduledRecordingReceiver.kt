package com.sconcept.mirrordash.iptv

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sconcept.mirrordash.launcher.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Fires at a [ScheduledRecording]'s start/end time - armed by [IptvRecordingEngine] - and once at
 * boot to re-arm anything still pending, since `AlarmManager` alarms don't survive a reboot on
 * their own. [android.content.BroadcastReceiver.onReceive] can't suspend, but looking a recording
 * up means a DataStore read, so this hands off via [goAsync] rather than blocking the broadcast
 * dispatch thread or racing an unstructured launch that Android could kill mid-lookup.
 */
class ScheduledRecordingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = AppContainer.get(appContext)
                val engine = container.iptvRecordingEngine
                when (intent.action) {
                    ACTION_START -> {
                        val id = intent.getStringExtra(EXTRA_RECORDING_ID) ?: return@launch
                        val recording = container.settingsRepository.settings.first()
                            .iptvScheduledRecordings.firstOrNull { it.id == id } ?: return@launch
                        engine.startScheduled(recording)
                    }
                    ACTION_STOP -> engine.stop()
                    Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED -> engine.rearmPendingRecordings()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_START = "com.sconcept.mirrordash.iptv.action.START_RECORDING"
        const val ACTION_STOP = "com.sconcept.mirrordash.iptv.action.STOP_RECORDING"
        private const val EXTRA_RECORDING_ID = "recording_id"

        fun pendingIntent(context: Context, recordingId: String, action: String): PendingIntent {
            val intent = Intent(context, ScheduledRecordingReceiver::class.java).apply {
                this.action = action
                putExtra(EXTRA_RECORDING_ID, recordingId)
            }
            // Distinct request codes per (recording, action) pair - otherwise arming the start
            // and stop alarms for the same recording (or two different recordings) would collide
            // and silently overwrite each other's PendingIntent.
            val requestCode = (recordingId + action).hashCode()
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
