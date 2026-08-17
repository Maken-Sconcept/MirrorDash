package com.sconcept.mirrordash.iptv.player

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import android.view.View
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import tv.danmaku.ijk.media.player.IMediaPlayer
import tv.danmaku.ijk.media.player.IjkMediaPlayer

/** IjkPlayer backend - also ffmpeg-based, historically the most common "fallback player" in IPTV
 * apps. Renders through a plain [TextureView] (`setSurface`) - unlike ExoPlayer/VLC, IjkPlayer has
 * no ready-made Compose/View wrapper of its own to reuse. Upstream `bilibili/ijkplayer` is
 * archived/unmaintained; this binds against a community fork (Tencent's `iot-ijkplayer`) still
 * published to Maven Central - see `app/build.gradle.kts`'s comment on that dependency. */
class IjkIptvPlayer(context: Context) : IptvPlayer {
    override val backend = PlayerBackend.IJKPLAYER

    private val _state = MutableStateFlow(IptvPlayerState())
    override val state: StateFlow<IptvPlayerState> = _state

    init {
        ensureLibrariesLoaded()
    }

    private val ijkPlayer = IjkMediaPlayer().apply {
        setOnPreparedListener { it.start() }
        setOnCompletionListener { _state.update { s -> s.copy(isPlaying = false) } }
        setOnErrorListener { _, what, extra ->
            _state.update { it.copy(errorMessage = "IjkPlayer error ($what/$extra)", isPlaying = false, isBuffering = false) }
            true
        }
        setOnInfoListener(object : IMediaPlayer.OnInfoListener {
            override fun onInfo(mp: IMediaPlayer, what: Int, extra: Int): Boolean {
                when (what) {
                    IMediaPlayer.MEDIA_INFO_BUFFERING_START -> _state.update { it.copy(isBuffering = true) }
                    IMediaPlayer.MEDIA_INFO_BUFFERING_END -> _state.update { it.copy(isBuffering = false) }
                    IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> _state.update { it.copy(isPlaying = true, isBuffering = false, errorMessage = null) }
                }
                return false
            }
            // This fork's OnInfoListener extends the upstream one with SEI/PCM callbacks this
            // app has no use for - implemented as no-ops purely to satisfy the interface.
            override fun onInfoSEI(mp: IMediaPlayer, what: Int, extra: Int, data: String?): Boolean = false
            override fun onInfoAudioPcmData(mp: IMediaPlayer, data: ByteArray?, size: Int) = Unit
        })
    }
    private var textureView: TextureView? = null
    private var surface: Surface? = null

    /** Playback can't actually start until the [TextureView]'s [Surface] exists, which happens
     * asynchronously after [view] returns (the view has to be laid out/attached first) - a
     * [setMediaAndPlay] call before that just gets remembered and started once
     * [android.view.TextureView.SurfaceTextureListener.onSurfaceTextureAvailable] fires. */
    private var pendingUrl: String? = null

    override fun view(context: Context): View {
        textureView?.let { return it }
        val view = TextureView(context)
        view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                surface = Surface(st)
                pendingUrl?.let { url ->
                    pendingUrl = null
                    startPlayback(url)
                }
            }

            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) = Unit

            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                surface?.release()
                surface = null
                return true
            }

            override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
        }
        textureView = view
        return view
    }

    override fun setMediaAndPlay(url: String) {
        if (surface == null) {
            pendingUrl = url
            return
        }
        startPlayback(url)
    }

    private fun startPlayback(url: String) {
        ijkPlayer.reset()
        ijkPlayer.setSurface(surface)
        ijkPlayer.dataSource = url
        ijkPlayer.prepareAsync()
    }

    override fun play() {
        ijkPlayer.start()
    }

    override fun pause() {
        ijkPlayer.pause()
    }

    override val isPlaying: Boolean get() = ijkPlayer.isPlaying
    override var volume: Float = 1f
        set(value) {
            field = value
            ijkPlayer.setVolume(value, value)
        }
    override val currentPositionMs: Long get() = ijkPlayer.currentPosition
    override val durationMs: Long get() = ijkPlayer.duration

    override fun seekTo(ms: Long) {
        ijkPlayer.seekTo(ms)
    }

    override fun release() {
        ijkPlayer.setOnPreparedListener(null)
        ijkPlayer.setOnCompletionListener(null)
        ijkPlayer.setOnErrorListener(null)
        ijkPlayer.setOnInfoListener(null)
        ijkPlayer.release()
        surface?.release()
        surface = null
    }

    companion object {
        @Volatile
        private var librariesLoaded = false

        /** IjkMediaPlayer throws if a native library hasn't been loaded before the first instance
         * is constructed - a process-wide one-time thing, not per-player. */
        @Synchronized
        private fun ensureLibrariesLoaded() {
            if (librariesLoaded) return
            IjkMediaPlayer.loadLibrariesOnce(null)
            librariesLoaded = true
        }
    }
}
