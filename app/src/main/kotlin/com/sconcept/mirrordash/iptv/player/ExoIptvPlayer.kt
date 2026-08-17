package com.sconcept.mirrordash.iptv.player

import android.content.Context
import android.view.View
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** The default backend - Media3's own `ExoPlayer`, wrapped to satisfy [IptvPlayer] rather than
 * being used directly. This is a near-verbatim move of what [com.sconcept.mirrordash.iptv.IptvViewModel]
 * used to do inline before alternate backends existed. */
class ExoIptvPlayer(context: Context) : IptvPlayer {
    override val backend = PlayerBackend.EXOPLAYER

    private val _state = MutableStateFlow(IptvPlayerState())
    override val state: StateFlow<IptvPlayerState> = _state

    private val exoPlayer = ExoPlayer.Builder(context.applicationContext).build().apply {
        addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                _state.update { it.copy(isBuffering = playbackState == Player.STATE_BUFFERING) }
            }
            override fun onPlayerError(error: PlaybackException) {
                _state.update { it.copy(errorMessage = error.message ?: "ExoPlayer error") }
            }
        })
        playWhenReady = true
    }
    private var playerView: PlayerView? = null

    override fun view(context: Context): View {
        playerView?.let { return it }
        return PlayerView(context).apply {
            useController = false
            player = exoPlayer
        }.also { playerView = it }
    }

    override fun setMediaAndPlay(url: String) {
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
        exoPlayer.play()
    }

    override fun play() = exoPlayer.play()
    override fun pause() = exoPlayer.pause()
    override val isPlaying: Boolean get() = exoPlayer.isPlaying
    override var volume: Float
        get() = exoPlayer.volume
        set(value) {
            exoPlayer.volume = value
        }
    override val currentPositionMs: Long get() = exoPlayer.currentPosition
    override val durationMs: Long
        get() = exoPlayer.duration.takeIf { it != C.TIME_UNSET } ?: -1L

    override fun seekTo(ms: Long) {
        exoPlayer.seekTo(ms)
    }

    override fun release() {
        playerView?.player = null
        exoPlayer.release()
    }
}
