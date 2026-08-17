package com.sconcept.mirrordash.iptv.player

import android.content.Context
import android.view.View
import kotlinx.coroutines.flow.StateFlow

/** Selectable player engines - all three play the exact same resolved stream URL
 * ([com.sconcept.mirrordash.iptv.IptvViewModel] doesn't re-resolve per backend, just hands the
 * same URL to whichever one is active). ExoPlayer is Media3's own decoder; VLC and IjkPlayer are
 * both ffmpeg-based and tend to tolerate malformed/unusual streams ExoPlayer rejects outright -
 * the reason to have alternatives at all for an IPTV portal whose stream quality isn't under this
 * app's control. */
enum class PlayerBackend(val storageKey: String, val displayName: String) {
    EXOPLAYER("exoplayer", "ExoPlayer"),
    VLC("vlc", "VLC"),
    IJKPLAYER("ijkplayer", "IjkPlayer"),
    ;

    companion object {
        fun fromStorageKey(key: String): PlayerBackend = entries.firstOrNull { it.storageKey == key } ?: EXOPLAYER
    }
}

data class IptvPlayerState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Common surface every backend ([PlayerBackend]) is adapted to, so
 * [com.sconcept.mirrordash.iptv.IptvViewModel] and the shared playback UI (`PlaybackControls`/
 * `PlayerSurface`/`SeekRow` in `IptvScreen.kt`) never need to know which one is actually running
 * underneath - swapping backends (a Settings default, an in-player quick switch, or an automatic
 * fallback after a playback error) is just releasing one implementation and creating another.
 *
 * Each implementation owns exactly one instance of its native player plus the one Android [View]
 * it renders into - both created lazily on first [view] call and torn down together in [release].
 */
interface IptvPlayer {
    val backend: PlayerBackend
    val state: StateFlow<IptvPlayerState>

    /** Creates (once) and returns this backend's own render surface - a different concrete [View]
     * subclass per backend (Media3's `PlayerView`, LibVLC's `VLCVideoLayout`, a raw `TextureView`
     * for IjkPlayer), which is exactly why this returns a plain [View] rather than something more
     * specific. Safe to call more than once - later calls return the same instance. */
    fun view(context: Context): View

    fun setMediaAndPlay(url: String)
    fun play()
    fun pause()
    val isPlaying: Boolean
    var volume: Float

    val currentPositionMs: Long

    /** <= 0 means unknown/live - the same convention the seek bar already treats specially for a
     * live channel (no scrubbable timeline), now shared by every backend rather than something
     * only true of ExoPlayer specifically. */
    val durationMs: Long
    fun seekTo(ms: Long)
    fun release()
}
