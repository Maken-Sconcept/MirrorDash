package com.sconcept.mirrordash.iptv.player

import android.content.Context
import android.net.Uri
import android.view.View
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/** VLC (LibVLC) backend - ffmpeg-based, tolerates malformed/unusual streams ExoPlayer sometimes
 * rejects outright. Renders through [VLCVideoLayout] (`MediaPlayer.attachViews`), LibVLC's own
 * ready-made video+subtitle surface, rather than a raw `SurfaceView`/`TextureView`. */
class VlcIptvPlayer(context: Context) : IptvPlayer {
    override val backend = PlayerBackend.VLC

    private val _state = MutableStateFlow(IptvPlayerState())
    override val state: StateFlow<IptvPlayerState> = _state

    private val libVlc = LibVLC(context.applicationContext, ArrayList<String>())
    private val vlcPlayer = MediaPlayer(libVlc).apply {
        setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> _state.update { it.copy(isPlaying = true, isBuffering = false, errorMessage = null) }
                MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped -> _state.update { it.copy(isPlaying = false) }
                MediaPlayer.Event.Buffering -> _state.update { it.copy(isBuffering = event.buffering < 100f) }
                MediaPlayer.Event.EncounteredError -> _state.update {
                    it.copy(errorMessage = "VLC couldn't play this stream", isPlaying = false, isBuffering = false)
                }
            }
        }
    }
    private var videoLayout: VLCVideoLayout? = null

    override fun view(context: Context): View {
        videoLayout?.let { return it }
        val layout = VLCVideoLayout(context)
        vlcPlayer.attachViews(layout, null, false, false)
        videoLayout = layout
        return layout
    }

    override fun setMediaAndPlay(url: String) {
        val media = Media(libVlc, Uri.parse(url))
        vlcPlayer.setMedia(media)
        media.release()
        vlcPlayer.play()
    }

    override fun play() {
        vlcPlayer.play()
    }

    override fun pause() {
        vlcPlayer.pause()
    }

    override val isPlaying: Boolean get() = vlcPlayer.isPlaying
    override var volume: Float
        get() = vlcPlayer.volume / 100f
        set(value) {
            vlcPlayer.setVolume((value * 100).toInt())
        }
    override val currentPositionMs: Long get() = vlcPlayer.time
    override val durationMs: Long get() = vlcPlayer.length

    override fun seekTo(ms: Long) {
        vlcPlayer.setTime(ms)
    }

    override fun release() {
        vlcPlayer.stop()
        videoLayout?.let { vlcPlayer.detachViews() }
        vlcPlayer.release()
        libVlc.release()
    }
}
