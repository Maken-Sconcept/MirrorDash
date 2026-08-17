package com.sconcept.mirrordash.iptv

import kotlinx.serialization.Serializable

/** A recording queued to start/stop itself at specific times, driven by `AlarmManager` (see
 * `ScheduledRecordingReceiver`) rather than anything that needs the app open or the IPTV tab
 * visible. Created from a Guide program cell - [startEpochSeconds]/[endEpochSeconds] come
 * straight from that program's [EpgProgram], not a hand-picked time range. */
@Serializable
data class ScheduledRecording(
    val id: String,
    val channelId: String,
    val channelName: String,
    val channelNumber: String,
    /** [StalkerChannel.cmd] captured at schedule time - a channel's play command is a stable
     * part of its definition (unlike the ephemeral URL `create_link` resolves it to), so this
     * lets the alarm fire and resolve a fresh stream URL directly, without first having to
     * reconnect and re-fetch the entire channel list just to look one channel back up. */
    val channelCmd: String,
    val programTitle: String,
    val startEpochSeconds: Long,
    val endEpochSeconds: Long,
)

enum class RecordingTrigger { MANUAL, FIXED_DURATION, SCHEDULED, DOWNLOAD }

/** Which destination is tried first - the other is always attempted as a fallback if opening the
 * primary one fails, per the brief: both are implemented as independent, always-available write
 * paths, and the choice between them is one Settings-level default rather than a per-recording
 * decision. */
enum class RecordingDestinationMode(val storageKey: String) {
    SMB_PRIMARY("smb"),
    LOCAL_PRIMARY("local"),
    ;

    companion object {
        fun fromStorageKey(key: String): RecordingDestinationMode = entries.firstOrNull { it.storageKey == key } ?: SMB_PRIMARY
    }
}

/** What's actually running right now, for the record button/Download Manager UI to reflect -
 * `null` in [IptvRecordingEngine.uiState] means nothing is currently recording/downloading. */
data class ActiveRecording(
    val channelId: String,
    val channelName: String,
    val trigger: RecordingTrigger,
    val destinationLabel: String,
    val startedAtEpochSeconds: Long,
    val bytesWritten: Long = 0L,
    val stopAtEpochSeconds: Long? = null,
    /** The source's `Content-Length`, when the server sent one - a VOD download almost always
     * has one (it's a finite file), a live channel recording never does (an open-ended stream).
     * Null means "unknown size", which is what tells the Download Manager to show an
     * indeterminate spinner instead of a percentage/circular progress. */
    val totalBytes: Long? = null,
    /** Bytes written since the last ~1s tick, divided by however long that tick actually took -
     * see [IptvRecordingEngine.copyStream]. 0 until the first tick has a prior sample to diff
     * against. */
    val bytesPerSecond: Long = 0L,
)

/** One finished recording, kept in [com.sconcept.mirrordash.settings.SettingsRepository]'s
 * persisted history (see [com.sconcept.mirrordash.settings.MirrorDashSettings.iptvRecordingHistory])
 * so what got saved is still browsable after the fact, not just the momentary confirmation right
 * as a recording stops. [path] is share/app-storage-relative (e.g. "MirrorDash Recordings/WWE
 * HD_2026-08-15_10-30-00.ts"), not an absolute filesystem path - the latter is either meaningless
 * (app-private local storage) or not what the user typed in as the NAS folder. */
@Serializable
data class CompletedRecording(
    val channelName: String,
    val destinationLabel: String,
    val path: String,
    val bytesWritten: Long,
    val startedAtEpochSeconds: Long,
    val finishedAtEpochSeconds: Long,
    /** Stopped itself, rather than the user or a schedule/timer ending it - the destination
     * volume dropped under the free-space failsafe mid-recording. */
    val stoppedForLowStorage: Boolean = false,
    /** Defaults to [RecordingTrigger.MANUAL] so history entries persisted before this field
     * existed still decode - added so the Download Manager's History tab can tell a Movies/Series
     * download apart from a live-TV recording in the shared history list. */
    val trigger: RecordingTrigger = RecordingTrigger.MANUAL,
)
