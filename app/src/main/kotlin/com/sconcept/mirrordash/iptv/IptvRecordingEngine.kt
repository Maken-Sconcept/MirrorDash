package com.sconcept.mirrordash.iptv

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.StatFs
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sconcept.mirrordash.R
import com.sconcept.mirrordash.launcher.MirrorDashActivity
import com.sconcept.mirrordash.nas.SmbPaths
import com.sconcept.mirrordash.nas.SmbRepository
import com.sconcept.mirrordash.settings.MirrorDashSettings
import com.sconcept.mirrordash.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "IptvRecordingEngine"

/** Read by [MirrorDashActivity] (`onCreate`/`onNewIntent`) - set on the "download complete"
 * notification's [PendingIntent] so tapping it opens straight to the Download Manager, on the
 * IPTV tab, rather than just whatever page the launcher happened to be showing. */
const val EXTRA_OPEN_DOWNLOAD_MANAGER = "com.sconcept.mirrordash.OPEN_DOWNLOAD_MANAGER"

private const val DOWNLOAD_COMPLETE_CHANNEL_ID = "iptv_download_complete"

/** Fixed rather than per-download - a new download's own completion is a fresh event worth its
 * own notification, but there's no need for more than the *latest* completed download to still be
 * sitting in the shade; [NotificationManager.notify] with the same ID just replaces it. */
private const val DOWNLOAD_COMPLETE_NOTIFICATION_ID = 4203

/** Below this, a destination refuses to even open for a new recording, and an already-running
 * one stops itself rather than write a device (or NAS share) down to 0 bytes free. */
private const val MIN_FREE_SPACE_BYTES = 300L * 1024 * 1024

/** How often an already-running recording re-checks free space on its destination - cheap for
 * local (`StatFs`), a real network round-trip for NAS, so this is throttled rather than checked
 * every chunk like the byte-count UI update is. */
private const val FREE_SPACE_CHECK_INTERVAL_MS = 15_000L

/** How many finished recordings [MirrorDashSettings.iptvRecordingHistory] keeps, oldest dropped
 * first - a DataStore preference is a poor fit for an unbounded, ever-growing list. */
private const val RECORDING_HISTORY_LIMIT = 50

data class RecordingEngineUiState(
    val activeRecording: ActiveRecording? = null,
    val lastCompleted: CompletedRecording? = null,
    val lastError: String? = null,
    /** Bumped by [IptvRecordingEngine.requestOpenDownloadManager] - a "download complete"
     * notification's tap target has no view of Compose navigation state, so it goes through this
     * app-wide singleton instead (same shape as `AirPlayEngine`'s own session-active signal
     * driving [com.sconcept.mirrordash.launcher.MirrorDashActivity]'s page jump). Every collector
     * only cares that this changed, not its actual value - a plain counter rather than a Boolean
     * so two requests in a row (rare, but e.g. two notification taps) both register as a distinct
     * edge instead of the second being silently swallowed by `distinctUntilChanged`. */
    val openRequestEpoch: Int = 0,
)

/**
 * Records a live channel straight from the portal to a file - a second, independent HTTP
 * connection to the resolved stream URL, piped byte-for-byte to disk/SMB. Deliberately not a
 * screen recording and not a decode/re-encode: this portal serves plain MPEG-TS over HTTP (no
 * `.m3u8`, confirmed live against edge.bz), so the bytes already are the file - there's nothing
 * to decode just to write straight back out again. That also means recording has no dependency
 * on anything being on-screen or audible: it never touches [androidx.media3.exoplayer.ExoPlayer]
 * or the app's volume, so it works whether the IPTV tab is even open, and regardless of mute.
 *
 * Session: if [MirrorDashSettings.iptvRecordingMacAddress] is set, this opens its own independent
 * [StalkerPortalClient] under that MAC - a genuinely separate session slot on the portal, so it
 * can run alongside live viewing with zero risk (this portal allows exactly one active session
 * *per MAC*, confirmed live against edge.bz - a different MAC never collides). Left blank, it
 * shares [IptvViewModel]'s session through [IptvSessionCoordinator] instead - see that class for
 * why a coordinator, not each side opening its own, is what keeps that sharing from silently
 * killing whichever side asked first.
 *
 * An app-scoped singleton, held in [com.sconcept.mirrordash.launcher.AppContainer] alongside
 * AirPlay/Walkie-Talkie's engines rather than owned by any one screen - a recording must survive
 * [IptvViewModel] releasing its own session when the tab isn't visible (see its doc comment).
 *
 * Destinations (SMB, local) are both always implemented, not a per-recording choice - one is the
 * configured primary, the other is the automatic fallback if the primary fails to *open*. A
 * failure partway through an already-open destination does not fail over - resuming a partially
 * written stream mid-file on a different destination would need more buffering than this device
 * can spare. Only one recording runs at a time in this version.
 */
class IptvRecordingEngine private constructor(
    private val appContext: Context,
    private val settingsRepository: SettingsRepository,
    private val sessionCoordinator: IptvSessionCoordinator,
) {
    private val smbRepository = SmbRepository(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _uiState = MutableStateFlow(RecordingEngineUiState())
    val uiState: StateFlow<RecordingEngineUiState> = _uiState

    private var recordingJob: Job? = null

    fun startManual(channel: StalkerChannel) {
        start(channel, RecordingTrigger.MANUAL, stopAtEpochSeconds = null)
    }

    fun startFixedDuration(channel: StalkerChannel, minutes: Int) {
        start(channel, RecordingTrigger.FIXED_DURATION, stopAtEpochSeconds = nowSeconds() + minutes * 60L)
    }

    /** Called by [ScheduledRecordingReceiver] when a scheduled recording's start alarm fires -
     * reconstructs just enough of a [StalkerChannel] to record from the saved [ScheduledRecording]
     * (its [ScheduledRecording.channelCmd] is what `create_link` actually needs; logo/genre were
     * never relevant to recording, so they're left blank rather than requiring a channel-list
     * refetch just to repopulate fields nothing here reads). */
    fun startScheduled(scheduled: ScheduledRecording) {
        val channel = StalkerChannel(
            id = scheduled.channelId,
            number = scheduled.channelNumber,
            name = scheduled.channelName,
            logoUrl = null,
            cmd = scheduled.channelCmd,
            genreId = "",
        )
        start(channel, RecordingTrigger.SCHEDULED, stopAtEpochSeconds = scheduled.endEpochSeconds)
    }

    /** Starts recording immediately, stopping automatically when [stopAtEpochSeconds] is
     * reached - the Guide's "record the program airing right now" shortcut, bounded to that
     * program's own end time rather than open-ended. */
    fun startUntil(channel: StalkerChannel, stopAtEpochSeconds: Long) {
        start(channel, RecordingTrigger.MANUAL, stopAtEpochSeconds)
    }

    fun stop() {
        recordingJob?.cancel()
    }

    /** Called by [MirrorDashActivity] when it's launched/resumed via the "download complete"
     * notification's [PendingIntent] - see [RecordingEngineUiState.openRequestEpoch]'s doc
     * comment for why a counter bump, observed from both the page-jump logic in
     * `MirrorDashRoot` and the Download Manager panel's own open state, is how that's threaded
     * through rather than a direct navigation call (nothing here has a reference to Compose
     * navigation state to call it *with*). */
    fun requestOpenDownloadManager() {
        _uiState.update { it.copy(openRequestEpoch = it.openRequestEpoch + 1) }
    }

    /** One-shot, finite download of a Movies/Series item to the same destination machinery live
     * recording uses - not open-ended like [start] ([stopAtEpochSeconds] is always null, so
     * [copyStream] just runs to the source's natural EOF), and to a separate, independently
     * configurable folder ([MirrorDashSettings.iptvDownloadFolderName], falling back to the same
     * folder recordings use when left blank) rather than always [RECORDINGS_FOLDER_NAME]. Shares
     * [runCapture] with [start] rather than duplicating the connect/destination/copy/history
     * plumbing - see that function for what's common between the two. */
    fun downloadVodItem(item: StalkerVodItem) {
        if (_uiState.value.activeRecording != null) return
        recordingJob?.cancel()
        IptvRecordingService.start(appContext)
        recordingJob = scope.launch {
            runCapture(
                id = item.id,
                name = item.name,
                trigger = RecordingTrigger.DOWNLOAD,
                stopAtEpochSeconds = null,
                resolveUrl = { client -> client.resolveVodStreamUrl(item) },
                folderNameFor = { settings -> settings.iptvDownloadFolderName.ifBlank { RECORDINGS_FOLDER_NAME } },
                smbFolderNameFor = { settings -> settings.iptvDownloadFolderName.ifBlank { settings.iptvRecordingSmbFolder } },
                fileName = { streamUrl -> buildFileName(item, streamUrl) },
            )
        }
    }

    private fun start(channel: StalkerChannel, trigger: RecordingTrigger, stopAtEpochSeconds: Long?) {
        if (_uiState.value.activeRecording != null) return
        recordingJob?.cancel()
        IptvRecordingService.start(appContext)

        recordingJob = scope.launch {
            runCapture(
                id = channel.id,
                name = channel.name,
                trigger = trigger,
                stopAtEpochSeconds = stopAtEpochSeconds,
                resolveUrl = { client -> client.resolveStreamUrl(channel) },
                folderNameFor = { RECORDINGS_FOLDER_NAME },
                smbFolderNameFor = { settings -> settings.iptvRecordingSmbFolder },
                fileName = { buildFileName(channel) },
            )
        }
    }

    /** What [start] (live channel, open-ended or bounded by [stopAtEpochSeconds]) and
     * [downloadVodItem] (Movies/Series item, always runs to EOF) share: acquire a portal session,
     * resolve a stream URL, open a destination, copy bytes, record history - everything that
     * differs between the two is passed in rather than branched on internally. */
    private suspend fun runCapture(
        id: String,
        name: String,
        trigger: RecordingTrigger,
        stopAtEpochSeconds: Long?,
        resolveUrl: suspend (StalkerPortalClient) -> Result<String>,
        folderNameFor: (MirrorDashSettings) -> String,
        smbFolderNameFor: (MirrorDashSettings) -> String,
        fileName: (streamUrl: String) -> String,
    ) {
        var destination: OutputStream? = null
        var usedSharedSession = false
        var openedHandle: DestinationHandle? = null
        var stoppedForLowStorage = false
        var wasCancelled = false
        try {
            val settings = settingsRepository.settings.first()
            require(settings.iptvPortalUrl.isNotBlank() && settings.iptvMacAddress.isNotBlank()) {
                "IPTV isn't configured"
            }
            val dedicatedMac = settings.iptvRecordingMacAddress
            val client = if (dedicatedMac.isNotBlank()) {
                val portal = settings.iptvRecordingPortalUrl.ifBlank { settings.iptvPortalUrl }
                StalkerPortalClient(portal, dedicatedMac).also { it.connect().getOrThrow() }
            } else {
                usedSharedSession = true
                sessionCoordinator.acquire(settings.iptvPortalUrl, settings.iptvMacAddress).getOrThrow()
            }
            // connect() itself no longer fails a blocked account (see StalkerPortalClient.blockMessage's
            // doc comment) - a recording/download needs actual playback, so this is where that
            // now needs to be checked explicitly instead.
            client.blockMessage?.let { throw IOException("Account blocked: $it") }
            val streamUrl = resolveUrl(client).getOrThrow()

            val opened = openDestination(fileName(streamUrl), folderNameFor(settings), smbFolderNameFor(settings), settings)
            openedHandle = opened
            destination = opened.stream

            _uiState.update {
                it.copy(
                    activeRecording = ActiveRecording(
                        channelId = id,
                        channelName = name,
                        trigger = trigger,
                        destinationLabel = opened.label,
                        startedAtEpochSeconds = nowSeconds(),
                        stopAtEpochSeconds = stopAtEpochSeconds,
                    ),
                    lastError = null,
                )
            }

            stoppedForLowStorage = copyStream(streamUrl, opened.stream, stopAtEpochSeconds, opened.freeSpaceBytes)
        } catch (e: kotlinx.coroutines.CancellationException) {
            // stop() cancelling this job - not a failure, just the ordinary way a recording
            // ends. Left to propagate so finally still runs and structured cancellation isn't
            // broken, but not logged/surfaced as lastError like a genuine failure would be.
            // Recorded so the finally block below doesn't post a "download complete" notification
            // over something the user themselves just stopped.
            wasCancelled = true
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Recording failed: ${e.message}", e)
            _uiState.update { it.copy(lastError = e.message ?: "Recording failed") }
        } finally {
            val active = _uiState.value.activeRecording
            val bytesWritten = active?.bytesWritten ?: 0L
            runCatching { destination?.close() }
            if (usedSharedSession) sessionCoordinator.release()
            // Only when a destination actually got opened - an error before that point (e.g.
            // couldn't reach the portal) never wrote anything worth pointing at or keeping.
            val completed = openedHandle?.let { handle ->
                CompletedRecording(
                    channelName = name,
                    destinationLabel = handle.label,
                    path = handle.path,
                    bytesWritten = bytesWritten,
                    startedAtEpochSeconds = active?.startedAtEpochSeconds ?: nowSeconds(),
                    finishedAtEpochSeconds = nowSeconds(),
                    stoppedForLowStorage = stoppedForLowStorage,
                    trigger = trigger,
                )
            }
            _uiState.update { it.copy(activeRecording = null, lastCompleted = completed ?: it.lastCompleted) }
            if (completed != null) {
                settingsRepository.update {
                    iptvRecordingHistory = (listOf(completed) + iptvRecordingHistory).take(RECORDING_HISTORY_LIMIT)
                }
                // Only for downloads (not live recordings, which have no comparable "you're done,
                // go look at it" moment - they either run until manually stopped or a schedule
                // ends them) and only when this actually ran to completion rather than being
                // stopped by the user, who was already right there when they tapped Stop.
                if (trigger == RecordingTrigger.DOWNLOAD && !wasCancelled) {
                    notifyDownloadComplete(completed)
                }
            }
            IptvRecordingService.stop(appContext)
        }
    }

    /** Returns true if this stopped itself because [freeSpaceBytes] dropped under
     * [MIN_FREE_SPACE_BYTES] mid-recording, false for every other way a recording ends (reached
     * [stopAtEpochSeconds], the source stream closed, or `stop()`/cancellation from outside). */
    private suspend fun copyStream(
        streamUrl: String,
        destination: OutputStream,
        stopAtEpochSeconds: Long?,
        freeSpaceBytes: () -> Long,
    ): Boolean = withContext(Dispatchers.IO) {
        val connection = URL(streamUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 4 rev: 250 Safari/533.3",
        )
        var stoppedForLowStorage = false
        try {
            // A VOD download almost always has one (it's a finite file); a live channel's
            // open-ended stream never does - see ActiveRecording.totalBytes's doc comment for
            // what a missing one means to the Download Manager UI.
            val contentLength = connection.contentLengthLong.takeIf { it > 0 }
            _uiState.update { state -> state.copy(activeRecording = state.activeRecording?.copy(totalBytes = contentLength)) }
            connection.inputStream.use { input ->
                val buffer = ByteArray(64 * 1024)
                var writtenBytes = 0L
                var lastUiUpdateAtMs = 0L
                var bytesAtLastUiUpdate = 0L
                var lastSpaceCheckAtMs = 0L
                while (isActive) {
                    if (stopAtEpochSeconds != null && nowSeconds() >= stopAtEpochSeconds) break
                    val now = System.currentTimeMillis()
                    if (now - lastSpaceCheckAtMs > FREE_SPACE_CHECK_INTERVAL_MS) {
                        lastSpaceCheckAtMs = now
                        // A failed query (e.g. NAS briefly unreachable) fails open here - an
                        // already-healthy recording shouldn't abort over a transient space check,
                        // only over a genuinely confirmed low-space reading.
                        val free = runCatching { freeSpaceBytes() }.getOrDefault(Long.MAX_VALUE)
                        if (free < MIN_FREE_SPACE_BYTES) {
                            Log.w(TAG, "Stopping recording - under ${MIN_FREE_SPACE_BYTES / (1024 * 1024)}MB free")
                            stoppedForLowStorage = true
                            break
                        }
                    }
                    val read = input.read(buffer)
                    if (read == -1) break
                    destination.write(buffer, 0, read)
                    writtenBytes += read
                    // Updating on every chunk would spam the StateFlow at network speed - once
                    // a second is plenty for a "recording... 42MB" indicator to feel live. Speed
                    // is the delta since the *previous* tick over however long that tick actually
                    // took (rarely exactly 1000ms), not an assumed-1s rate.
                    if (now - lastUiUpdateAtMs > 1000) {
                        val elapsedMs = (now - lastUiUpdateAtMs).takeIf { lastUiUpdateAtMs > 0 } ?: 1000L
                        val bytesPerSecond = ((writtenBytes - bytesAtLastUiUpdate) * 1000L) / elapsedMs
                        lastUiUpdateAtMs = now
                        bytesAtLastUiUpdate = writtenBytes
                        _uiState.update { state ->
                            state.copy(activeRecording = state.activeRecording?.copy(bytesWritten = writtenBytes, bytesPerSecond = bytesPerSecond))
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        stoppedForLowStorage
    }

    private data class DestinationHandle(
        val stream: OutputStream,
        val label: String,
        val path: String,
        /** Re-queryable, not a one-time snapshot - [copyStream] polls this while writing so the
         * failsafe also catches a destination that was fine when the recording started but ran
         * low over the course of a long one. */
        val freeSpaceBytes: () -> Long,
    )

    /** [localFolderName]/[smbFolderName] let [downloadVodItem] land in a different folder than
     * [start]'s live recordings (see [MirrorDashSettings.iptvDownloadFolderName]) while sharing
     * every other destination-selection/failsafe rule - both always default to the same folder
     * live recordings use ([RECORDINGS_FOLDER_NAME]/[MirrorDashSettings.iptvRecordingSmbFolder]). */
    private suspend fun openDestination(
        fileName: String,
        localFolderName: String,
        smbFolderName: String,
        settings: MirrorDashSettings,
    ): DestinationHandle {
        val primary = RecordingDestinationMode.fromStorageKey(settings.iptvRecordingDestination)
        val order = if (primary == RecordingDestinationMode.SMB_PRIMARY) {
            listOf(RecordingDestinationMode.SMB_PRIMARY, RecordingDestinationMode.LOCAL_PRIMARY)
        } else {
            listOf(RecordingDestinationMode.LOCAL_PRIMARY, RecordingDestinationMode.SMB_PRIMARY)
        }
        var lastError: Exception? = null
        for (mode in order) {
            try {
                return when (mode) {
                    RecordingDestinationMode.SMB_PRIMARY -> openSmbDestination(fileName, smbFolderName, settings)
                    RecordingDestinationMode.LOCAL_PRIMARY -> openLocalDestination(fileName, localFolderName, settings)
                }
            } catch (e: Exception) {
                Log.w(TAG, "${mode.storageKey} recording destination unavailable: ${e.message}")
                lastError = e
            }
        }
        throw lastError ?: IOException("No recording destination available")
    }

    private suspend fun openSmbDestination(fileName: String, folderName: String, settings: MirrorDashSettings): DestinationHandle {
        val share = settingsRepository.smbShareWithPassword()
        require(share.isConfigured) { "No NAS connection configured" }
        val free = smbRepository.freeSpaceBytes(share)
        if (free < MIN_FREE_SPACE_BYTES) {
            throw IOException("NAS share has less than ${MIN_FREE_SPACE_BYTES / (1024 * 1024)}MB free")
        }
        val path = SmbPaths.childPath(folderName, fileName)
        return DestinationHandle(
            stream = smbRepository.openOutputStream(share, path),
            label = "NAS",
            path = path,
            freeSpaceBytes = { runCatching { smbRepository.freeSpaceBytes(share) }.getOrDefault(Long.MAX_VALUE) },
        )
    }

    private fun openLocalDestination(fileName: String, folderName: String, settings: MirrorDashSettings): DestinationHandle {
        // App-private external storage - no storage permission needed on any API level, unlike
        // shared/public storage.
        val dir = File(appContext.getExternalFilesDir(null), folderName)
        dir.mkdirs()
        enforceLocalCap(dir, settings.iptvRecordingLocalCapMb)
        val free = StatFs(dir.path).availableBytes
        if (free < MIN_FREE_SPACE_BYTES) {
            throw IOException("Local storage has less than ${MIN_FREE_SPACE_BYTES / (1024 * 1024)}MB free")
        }
        return DestinationHandle(
            stream = FileOutputStream(File(dir, fileName)),
            label = "Local storage",
            path = "$folderName/$fileName",
            freeSpaceBytes = { StatFs(dir.path).availableBytes },
        )
    }

    private fun enforceLocalCap(dir: File, capMb: Int) {
        val capBytes = capMb * 1024L * 1024L
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= capBytes) break
            total -= file.length()
            file.delete()
        }
    }

    private fun buildFileName(channel: StalkerChannel): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val safeName = channel.name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifBlank { "channel" }
        return "${safeName}_$timestamp.ts"
    }

    /** Unlike [buildFileName]'s live-channel `.ts` (an open-ended capture of whatever plain
     * MPEG-TS the portal is streaming right now), a VOD/series item is a finite file the portal
     * serves from a real, resolved [streamUrl] - its own extension is used when recognizable,
     * `.mp4` otherwise (the safe assumption, since that's what these portals overwhelmingly
     * actually serve VOD as). */
    private fun buildFileName(item: StalkerVodItem, streamUrl: String): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val safeName = item.name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifBlank { "video" }
        val extension = Regex("\\.(mp4|mkv|avi|mov|m4v|ts)(?:\\?|$)", RegexOption.IGNORE_CASE)
            .find(streamUrl)?.groupValues?.get(1)?.lowercase() ?: "mp4"
        return "${safeName}_$timestamp.$extension"
    }

    // --- Scheduling ------------------------------------------------------------------------

    suspend fun scheduleRecording(recording: ScheduledRecording) {
        settingsRepository.update { iptvScheduledRecordings = iptvScheduledRecordings + recording }
        armAlarms(recording)
    }

    suspend fun cancelScheduledRecording(id: String) {
        val recording = settingsRepository.settings.first().iptvScheduledRecordings.firstOrNull { it.id == id }
        settingsRepository.update { iptvScheduledRecordings = iptvScheduledRecordings.filterNot { it.id == id } }
        if (recording != null) disarmAlarms(recording)
    }

    /** Re-arms every still-relevant scheduled recording's alarms - called at boot (alarms don't
     * survive a reboot on their own) and once at process start as a safety net. Anything whose
     * window has already fully passed is pruned; anything mid-window when this runs starts
     * immediately, truncated to whatever time is left. */
    suspend fun rearmPendingRecordings() {
        val now = nowSeconds()
        val settings = settingsRepository.settings.first()
        val stillRelevant = settings.iptvScheduledRecordings.filter { it.endEpochSeconds > now }
        if (stillRelevant.size != settings.iptvScheduledRecordings.size) {
            settingsRepository.update { iptvScheduledRecordings = stillRelevant }
        }
        stillRelevant.forEach { recording ->
            if (recording.startEpochSeconds <= now) startScheduled(recording) else armAlarms(recording)
        }
    }

    private fun armAlarms(recording: ScheduledRecording) {
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        setExactAlarm(alarmManager, recording.startEpochSeconds, startPendingIntent(recording))
        setExactAlarm(alarmManager, recording.endEpochSeconds, stopPendingIntent(recording))
    }

    private fun disarmAlarms(recording: ScheduledRecording) {
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(startPendingIntent(recording))
        alarmManager.cancel(stopPendingIntent(recording))
    }

    private fun setExactAlarm(alarmManager: AlarmManager, epochSeconds: Long, pendingIntent: PendingIntent) {
        val triggerAtMillis = epochSeconds * 1000L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // No exact-alarm grant on this device/API level - an inexact alarm still fires close
            // enough for a recording window rather than not scheduling anything at all.
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun startPendingIntent(recording: ScheduledRecording): PendingIntent =
        ScheduledRecordingReceiver.pendingIntent(appContext, recording.id, ScheduledRecordingReceiver.ACTION_START)

    private fun stopPendingIntent(recording: ScheduledRecording): PendingIntent =
        ScheduledRecordingReceiver.pendingIntent(appContext, recording.id, ScheduledRecordingReceiver.ACTION_STOP)

    // --- Download-complete notification --------------------------------------------------

    /** Separate, higher-priority channel from [IptvRecordingService]'s own ongoing-recording one -
     * that one is silent/low-priority by design (it exists only to keep the process alive while
     * something's in progress), this is a one-shot "you're done, go look at it" alert and should
     * behave like a normal notification (sound/visible), not blend into the ambient one. */
    private fun ensureDownloadCompleteChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(DOWNLOAD_COMPLETE_CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(DOWNLOAD_COMPLETE_CHANNEL_ID, "Download complete", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Shown once a Movies/Series download finishes"
            },
        )
    }

    /** Best-effort: [NotificationManagerCompat.areNotificationsEnabled] can be false (permission
     * never granted/revoked) since this runs from a plain singleton, not an Activity that could
     * itself request it - a finished download not being reported this way is a minor miss, not
     * worth surfacing as a [lastError] over. */
    private fun notifyDownloadComplete(completed: CompletedRecording) {
        ensureDownloadCompleteChannel()
        val manager = NotificationManagerCompat.from(appContext)
        if (!manager.areNotificationsEnabled()) return
        val openIntent = PendingIntent.getActivity(
            appContext,
            0,
            Intent(appContext, MirrorDashActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_DOWNLOAD_MANAGER, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(appContext, DOWNLOAD_COMPLETE_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Download complete")
            .setContentText("${completed.channelName} — ${formatMegabytes(completed.bytesWritten)}")
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { manager.notify(DOWNLOAD_COMPLETE_NOTIFICATION_ID, notification) }
            .onFailure { e -> Log.w(TAG, "Couldn't post download-complete notification: ${e.message}") }
    }

    private fun formatMegabytes(bytes: Long): String = String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))

    private fun nowSeconds(): Long = System.currentTimeMillis() / 1000L

    companion object {
        /** The local-storage folder name (and default SMB folder fallback point) live recordings
         * have always used - kept as a named constant now that [downloadVodItem] needs to refer
         * to it too as its own default, rather than the folder name only ever appearing as a
         * string literal inside [openLocalDestination]. */
        private const val RECORDINGS_FOLDER_NAME = "iptv_recordings"

        @Volatile
        private var instance: IptvRecordingEngine? = null

        fun get(
            context: Context,
            settingsRepository: SettingsRepository,
            sessionCoordinator: IptvSessionCoordinator,
        ): IptvRecordingEngine =
            instance ?: synchronized(this) {
                instance ?: IptvRecordingEngine(context.applicationContext, settingsRepository, sessionCoordinator)
                    .also { instance = it }
            }
    }
}
