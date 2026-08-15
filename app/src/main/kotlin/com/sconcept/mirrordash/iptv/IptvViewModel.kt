package com.sconcept.mirrordash.iptv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.sconcept.mirrordash.settings.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class IptvPageState { OFF, CONNECTING, READY, SLEEPING, ERROR }

data class IptvUiState(
    val configured: Boolean = false,
    val pageState: IptvPageState = IptvPageState.OFF,
    val errorMessage: String? = null,
    val channels: List<StalkerChannel> = emptyList(),
    val currentChannelIndex: Int = -1,
    val genres: List<StalkerGenre> = emptyList(),
    val selectedGenreId: String = ALL_GENRES_ID,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val showChannelList: Boolean = false,
    val showGuide: Boolean = false,
    val volume: Float = 1f,
    /** Bumped every time the underlying [ExoPlayer] instance is created or torn down, so the
     * screen knows to re-fetch it from [IptvViewModel.currentPlayer] and rebind its PlayerView -
     * the player itself can't live in Compose state, it's a plain mutable Android object. */
    val playerEpoch: Int = 0,
) {
    val currentChannel: StalkerChannel? get() = channels.getOrNull(currentChannelIndex)

    /** What the channel list/Guide should actually show - [channels] itself (and
     * [currentChannelIndex], and channel-up/down) stay unfiltered, so picking a category tab
     * narrows what's browsable without touching the real channel-up/down order. */
    val displayedChannels: List<StalkerChannel>
        get() = if (selectedGenreId == ALL_GENRES_ID) channels else channels.filter { it.genreId == selectedGenreId }
}

/**
 * Owns the tab's whole session lifecycle - portal connection, channel list, and the ExoPlayer
 * instance - as three states layered on top of connection progress (brief: "sleeping state when
 * not active" + "shut off completely, no memory, when not in view for more than 2 min"):
 *
 * - READY: page visible. Player exists and is playing.
 * - SLEEPING: page swiped away. Player released (no decoder/surface held), but the portal
 *   session (token) and channel list stay in memory so coming back is instant - just a fresh
 *   `create_link` + player recreate, no re-handshake.
 * - OFF: SLEEPING for longer than the configurable timeout, or never connected. Everything is
 *   dropped - channel list, portal client, token - so there is nothing left for the GC to hold
 *   onto. Reactivating from here looks identical to a cold start.
 *
 * The portal session itself comes from [IptvSessionCoordinator], not a private [StalkerPortalClient]
 * - this portal allows exactly one active session per MAC, so it's shared with [IptvRecordingEngine]
 * rather than each opening its own (which would silently kill whichever asked first).
 *
 * A [ViewModel] rather than a [com.sconcept.mirrordash.launcher.AppContainer]-held engine like
 * AirPlay/Walkie-Talkie: those run services that must keep working while the app is backgrounded,
 * this only ever needs to exist while its own tab is being looked at, so tying it to
 * [MirrorDashActivity]'s ViewModelStore (survives page swipes, dies with the Activity) is enough.
 */
class IptvViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository,
    private val sessionCoordinator: IptvSessionCoordinator,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(IptvUiState())
    val uiState: StateFlow<IptvUiState> = _uiState

    private var client: StalkerPortalClient? = null
    private var exoPlayer: ExoPlayer? = null
    private var isActive = false
    private var connectJob: Job? = null
    private var sleepTimeoutJob: Job? = null
    private var volumePersistJob: Job? = null

    /** Per-channel EPG, fetched lazily as the Guide's rows scroll into view (see
     * [com.sconcept.mirrordash.iptv.IptvGuideScreen]) rather than for the whole channel list up
     * front - with channel lists running into the thousands, prefetching everyone's schedule
     * just to show a few hours of guide would be enormously wasteful. Dropped in [tearDown] along
     * with everything else the portal session owns. */
    private val epgCache = mutableMapOf<String, List<EpgProgram>>()

    private data class PortalConfig(val portalUrl: String, val macAddress: String)

    init {
        // A portal URL/MAC edited in Settings invalidates whatever session is currently held
        // (different portal = different token/channel list entirely) - torn all the way down
        // rather than left to paper over the change, then reconnected immediately if the tab
        // happens to be the one visible right now.
        viewModelScope.launch {
            settingsRepository.settings
                .map { PortalConfig(it.iptvPortalUrl, it.iptvMacAddress) }
                .distinctUntilChanged()
                .collect { config ->
                    _uiState.update { it.copy(configured = config.portalUrl.isNotBlank() && config.macAddress.isNotBlank()) }
                    if (client != null || _uiState.value.pageState != IptvPageState.OFF) {
                        // The old config's cached session (if [sessionCoordinator] still has one)
                        // is for the wrong portal/MAC now - a plain release() would only drop this
                        // ViewModel's own share of it, so a hard invalidate() is needed instead.
                        sessionCoordinator.invalidate()
                        tearDown()
                        if (isActive) beginConnect()
                    }
                }
        }
    }

    fun currentPlayer(): ExoPlayer? = exoPlayer

    /** Called from [com.sconcept.mirrordash.launcher.MirrorDashActivity] whenever the pager
     * settles on/off this tab - the one signal the whole state machine above hangs off of. */
    fun setActive(active: Boolean) {
        if (isActive == active) return
        isActive = active

        if (active) {
            sleepTimeoutJob?.cancel()
            sleepTimeoutJob = null
            when (_uiState.value.pageState) {
                IptvPageState.OFF, IptvPageState.ERROR -> beginConnect()
                IptvPageState.SLEEPING -> resumeFromSleep()
                IptvPageState.CONNECTING, IptvPageState.READY -> Unit
            }
        } else {
            when (_uiState.value.pageState) {
                IptvPageState.READY -> enterSleep()
                IptvPageState.CONNECTING -> {
                    connectJob?.cancel()
                    connectJob = null
                    _uiState.update { it.copy(pageState = IptvPageState.OFF) }
                }
                IptvPageState.OFF, IptvPageState.SLEEPING, IptvPageState.ERROR -> Unit
            }
        }
    }

    fun retry() {
        if (_uiState.value.pageState != IptvPageState.ERROR) return
        beginConnect()
    }

    fun toggleChannelList() {
        _uiState.update { it.copy(showChannelList = !it.showChannelList, showGuide = false) }
    }

    fun toggleGuide() {
        _uiState.update { it.copy(showGuide = !it.showGuide, showChannelList = false) }
    }

    fun selectGenre(genreId: String) {
        _uiState.update { it.copy(selectedGenreId = genreId) }
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    /** Persists debounced, not on every emission - a fine-tune drag alone can fire this dozens
     * of times a second, and only where the user's finger *ends up* is worth remembering. Uses
     * the cancel-and-relaunch idiom (same as [MirrorDashActivity]'s volume-key failsafe timer)
     * rather than a `Flow.debounce` subscription, so nothing gets written before the user has
     * ever actually touched the slider - see [beginConnect] for why that distinction matters. */
    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _uiState.update { it.copy(volume = clamped) }
        exoPlayer?.volume = clamped

        volumePersistJob?.cancel()
        volumePersistJob = viewModelScope.launch {
            kotlinx.coroutines.delay(VOLUME_PERSIST_DEBOUNCE_MS)
            settingsRepository.update { iptvVolume = clamped }
        }
    }

    private var volumeBeforeMute = 1f

    /** Restores the exact level muting came from, not a fixed "unmute to 100%" - matches every
     * other volume mute button (TV remotes included). */
    fun toggleMute() {
        val current = _uiState.value.volume
        if (current > 0f) {
            volumeBeforeMute = current
            setVolume(0f)
        } else {
            setVolume(volumeBeforeMute.takeIf { it > 0f } ?: 1f)
        }
    }

    /** [StalkerPortalClient.fetchShortEpg] is per-channel and only worth calling for a channel
     * the Guide is actually showing right now - see [epgCache]'s doc comment. Returns an empty
     * list (rather than suspending forever or throwing) whenever there's no live session to ask,
     * e.g. the tab went SLEEPING/OFF while the Guide happened to still be open. */
    suspend fun epgFor(channel: StalkerChannel): List<EpgProgram> {
        epgCache[channel.id]?.let { return it }
        val session = client ?: return emptyList()
        val programs = session.fetchShortEpg(channel).getOrDefault(emptyList())
        epgCache[channel.id] = programs
        return programs
    }

    fun selectChannel(index: Int) {
        val channels = _uiState.value.channels
        if (index !in channels.indices) return
        _uiState.update { it.copy(currentChannelIndex = index, showChannelList = false, showGuide = false) }
        playCurrentChannel()
    }

    fun selectChannelById(id: String) {
        val index = _uiState.value.channels.indexOfFirst { it.id == id }
        if (index >= 0) selectChannel(index)
    }

    fun nextChannel() = stepChannel(1)
    fun previousChannel() = stepChannel(-1)

    private fun stepChannel(delta: Int) {
        val channels = _uiState.value.channels
        if (channels.isEmpty()) return
        val current = _uiState.value.currentChannelIndex
        val next = ((if (current < 0) 0 else current + delta) % channels.size + channels.size) % channels.size
        _uiState.update { it.copy(currentChannelIndex = next) }
        playCurrentChannel()
    }

    private fun beginConnect() {
        connectJob?.cancel()
        _uiState.update {
            it.copy(
                pageState = IptvPageState.CONNECTING,
                errorMessage = null,
                channels = emptyList(),
                currentChannelIndex = -1,
                genres = emptyList(),
                selectedGenreId = ALL_GENRES_ID,
            )
        }
        connectJob = viewModelScope.launch {
            val config = settingsRepository.settings.first()
            if (config.iptvPortalUrl.isBlank() || config.iptvMacAddress.isBlank()) {
                _uiState.update { it.copy(pageState = IptvPageState.OFF) }
                return@launch
            }
            val newClient = sessionCoordinator.acquire(config.iptvPortalUrl, config.iptvMacAddress)
                .getOrElse { error -> fail(error); return@launch }
            val channels = newClient.fetchChannels()
                .getOrElse { error ->
                    // A share of the session was already acquired above (fail() only clears this
                    // ViewModel's own [client] reference, not the coordinator's ref-count) - has to
                    // be released here or the coordinator would think this ViewModel still holds it.
                    sessionCoordinator.release()
                    fail(error)
                    return@launch
                }
            // Best-effort: a portal that doesn't support genres, or a transient failure here,
            // shouldn't fail the whole connection over a filter row - it just means one "All" tab.
            val genres = newClient.fetchGenres().getOrDefault(emptyList())
                .ifEmpty { listOf(StalkerGenre(id = ALL_GENRES_ID, title = "All")) }

            // Resume the remembered channel if it's still in the list, otherwise just the first
            // one - and start at the remembered volume unless "Always open muted" is on, which
            // overrides only this connection's starting point, not the remembered value itself.
            val rememberedIndex = channels.indexOfFirst { it.id == config.iptvLastChannelId }.takeIf { it >= 0 }
            val initialIndex = rememberedIndex ?: if (channels.isEmpty()) -1 else 0
            val rememberedVolume = config.iptvVolume.coerceIn(0f, 1f)
            val initialVolume = if (config.iptvOpenMuted) 0f else rememberedVolume
            // So that tapping the mute icon after an "open muted" start unmutes back to the
            // actual remembered level, not a hardcoded full blast.
            if (rememberedVolume > 0f) volumeBeforeMute = rememberedVolume

            client = newClient
            _uiState.update {
                it.copy(channels = channels, currentChannelIndex = initialIndex, genres = genres, volume = initialVolume)
            }
            ensurePlayer()
            if (channels.isNotEmpty()) playCurrentChannel()
            _uiState.update { it.copy(pageState = IptvPageState.READY) }
        }
    }

    private fun fail(error: Throwable) {
        client = null
        _uiState.update {
            it.copy(pageState = IptvPageState.ERROR, errorMessage = error.message ?: "Couldn't connect to the portal")
        }
    }

    private fun resumeFromSleep() {
        ensurePlayer()
        _uiState.update { it.copy(pageState = IptvPageState.READY) }
        if (_uiState.value.currentChannelIndex >= 0) playCurrentChannel()
    }

    private fun enterSleep() {
        releasePlayer()
        _uiState.update { it.copy(pageState = IptvPageState.SLEEPING, isPlaying = false) }
        sleepTimeoutJob = viewModelScope.launch {
            val timeoutSeconds = settingsRepository.settings.first().iptvSleepTimeoutSeconds
            kotlinx.coroutines.delay(timeoutSeconds * 1000L)
            tearDown()
        }
    }

    private fun tearDown() {
        connectJob?.cancel()
        connectJob = null
        sleepTimeoutJob?.cancel()
        sleepTimeoutJob = null
        releasePlayer()
        if (client != null) sessionCoordinator.release()
        client = null
        epgCache.clear()
        _uiState.update {
            IptvUiState(configured = it.configured, pageState = IptvPageState.OFF, playerEpoch = it.playerEpoch, volume = it.volume)
        }
    }

    private fun ensurePlayer() {
        if (exoPlayer != null) return
        exoPlayer = ExoPlayer.Builder(getApplication()).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _uiState.update { it.copy(isPlaying = isPlaying) }
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    _uiState.update { it.copy(isBuffering = playbackState == Player.STATE_BUFFERING) }
                }
                override fun onPlayerError(error: PlaybackException) {
                    _uiState.update { it.copy(errorMessage = error.message ?: "Playback error") }
                }
            })
            volume = _uiState.value.volume
            playWhenReady = true
        }
        _uiState.update { it.copy(playerEpoch = it.playerEpoch + 1) }
    }

    private fun releasePlayer() {
        exoPlayer?.release()
        exoPlayer = null
        _uiState.update { it.copy(playerEpoch = it.playerEpoch + 1) }
    }

    private fun playCurrentChannel() {
        val channel = _uiState.value.currentChannel ?: return
        val session = client ?: return
        val player = exoPlayer ?: return
        viewModelScope.launch { settingsRepository.update { iptvLastChannelId = channel.id } }
        viewModelScope.launch {
            session.resolveStreamUrl(channel)
                .onSuccess { url ->
                    if (exoPlayer !== player) return@onSuccess // superseded by a newer session
                    player.setMediaItem(MediaItem.fromUri(url))
                    player.prepare()
                    player.play()
                }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message ?: "Couldn't play ${channel.name}") }
                }
        }
    }

    override fun onCleared() {
        tearDown()
        super.onCleared()
    }

    companion object {
        private const val VOLUME_PERSIST_DEBOUNCE_MS = 400L

        fun factory(
            application: Application,
            settingsRepository: SettingsRepository,
            sessionCoordinator: IptvSessionCoordinator,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    IptvViewModel(application, settingsRepository, sessionCoordinator) as T
            }
    }
}
