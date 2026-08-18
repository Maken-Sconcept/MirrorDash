package com.sconcept.mirrordash.iptv

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sconcept.mirrordash.iptv.player.IptvPlayer
import com.sconcept.mirrordash.iptv.player.IptvPlayerFactory
import com.sconcept.mirrordash.iptv.player.IptvPlayerState
import com.sconcept.mirrordash.iptv.player.PlayerBackend
import com.sconcept.mirrordash.settings.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "IptvViewModel"

/** How long a typed channel-number digit sits on screen before it's treated as "finished" and
 * looked up - see [IptvViewModel.enterChannelDigit]. A remote has no dedicated "confirm channel"
 * key most apps can rely on, so inactivity is the confirm signal instead, the same way a classic
 * TV's own channel-number OSD works. */
private const val CHANNEL_NUMBER_COMMIT_DEBOUNCE_MS = 1_500L

/** Channel numbers in practice are 1-4 digits; a 5th digit almost certainly means a fat-fingered
 * remote press, not a real channel, so it's dropped rather than extending the draft forever. */
private const val MAX_CHANNEL_NUMBER_DRAFT_LENGTH = 4

/** [BLOCKED] is its own state, not folded into [ERROR] - a blocked/expired account is a portal
 * response, not a failure to connect (handshake and token both succeeded, see
 * [StalkerPortalClient.blockMessage]'s doc comment), and the tab has something concrete to show
 * for it ([IptvUiState.accountInfo]'s expiry) that a generic "couldn't connect" screen doesn't. */
enum class IptvPageState { OFF, CONNECTING, READY, SLEEPING, ERROR, BLOCKED }

/** Which of the portal's content types the tab is currently browsing - see [IptvUiState.contentTab].
 * Movies/Series categories are fetched lazily the first time their tab is opened (see
 * [IptvViewModel.selectContentTab]), not during the initial [IptvViewModel] connect - most
 * sessions only ever watch live TV, so paying for a VOD catalog fetch nobody asked for would be
 * pure waste. */
enum class IptvContentTab { LIVE, MOVIES, SERIES }

/** What triggered the parental-PIN dialog - so a correct PIN can carry out the action that was
 * blocked, instead of just unlocking with nothing happening. */
sealed class PinChallenge {
    data class Channel(val channelId: String) : PinChallenge()
    data class Genre(val genreId: String) : PinChallenge()
}

data class IptvUiState(
    val configured: Boolean = false,
    val pageState: IptvPageState = IptvPageState.OFF,
    val errorMessage: String? = null,
    /** Populated whenever [pageState] is [IptvPageState.BLOCKED] - see [IptvViewModel.beginConnect].
     * [StalkerAccountInfo.blockMessage] is the reason shown; [StalkerAccountInfo.expiryEpochSeconds]
     * the date, when the portal provides one. */
    val accountInfo: StalkerAccountInfo? = null,
    val channels: List<StalkerChannel> = emptyList(),
    val currentChannelIndex: Int = -1,
    val genres: List<StalkerGenre> = emptyList(),
    val selectedGenreId: String = ALL_GENRES_ID,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val showChannelList: Boolean = false,
    val showGuide: Boolean = false,
    /** True once a channel has been tapped from the Guide (and not since collapsed back via
     * [IptvViewModel.collapseGuidePreview]) - the Guide shrinks to the bottom 70% of the screen
     * and the live player becomes visible in the top 30% above it, rather than the Guide covering
     * the whole screen and hiding playback the way it does before any tap. Reset to false each
     * time the Guide is freshly opened, so every Guide session starts full-size. */
    val guideShowsPreview: Boolean = false,
    val volume: Float = 1f,
    /** Bumped every time the underlying [IptvPlayer] instance is created, torn down, or swapped
     * for a different [PlayerBackend], so the screen knows to re-fetch it from
     * [IptvViewModel.currentPlayer] and rebind its render view - the player itself can't live in
     * Compose state, it's a plain mutable Android object. */
    val playerEpoch: Int = 0,
    /** Whichever backend the current (or most recently created) player actually is - see
     * [IptvViewModel.setPlayerBackend] for the in-player quick switcher this drives, and
     * [MirrorDashSettings.iptvPlayerBackend] for the persisted default it starts from. */
    val playerBackend: PlayerBackend = PlayerBackend.EXOPLAYER,
    /** Non-null shows the PIN dialog - see [PinChallenge] and [IptvViewModel.submitParentalPin]. */
    val pendingPinChallenge: PinChallenge? = null,
    val pinError: Boolean = false,

    // --- Movies/Series (see IptvContentTab) -------------------------------------------------
    val contentTab: IptvContentTab = IptvContentTab.LIVE,
    val vodCategories: List<StalkerVodCategory> = emptyList(),
    val selectedVodCategoryId: String? = null,
    /** Accumulated across every chunk fetched so far for [selectedVodCategoryId] - see
     * [VodPage]/[IptvViewModel.loadMoreVodItems]. */
    val vodItems: List<StalkerVodItem> = emptyList(),
    val vodLoading: Boolean = false,
    /** True while a [IptvViewModel.loadMoreVodItems] chunk is in flight - separate from
     * [vodLoading] so the category's own first-load spinner and the list's trailing
     * "loading more" footer never fight over the same flag. */
    val vodLoadingMore: Boolean = false,
    /** Portal page [IptvViewModel.loadMoreVodItems] resumes from - see [VodPage.nextPage]. */
    val vodNextPage: Int = 1,
    /** False once a chunk's own request came back empty - see [VodPage.hasMore]. */
    val vodHasMore: Boolean = false,
    /** The category's own item count, straight off the portal - see [VodPage.totalItems]. 0
     * means the portal didn't report one, shown as just a plain count of what's loaded instead. */
    val vodTotalItems: Int = 0,
    val vodError: String? = null,
    /** Non-null while [vodItems] holds [IptvSearchMode.DEEP] search results (see
     * [IptvViewModel.searchVodDeep]) for this query, rather than [selectedVodCategoryId]'s
     * ordinary browsing chunk - [IptvViewModel.loadMoreVodItems] checks this to know which of the
     * two it's continuing to paginate. */
    val vodActiveSearchQuery: String? = null,
    val seriesCategories: List<StalkerVodCategory> = emptyList(),
    val selectedSeriesCategoryId: String? = null,
    val seriesItems: List<StalkerVodItem> = emptyList(),
    val seriesLoading: Boolean = false,
    val seriesLoadingMore: Boolean = false,
    val seriesNextPage: Int = 1,
    val seriesHasMore: Boolean = false,
    val seriesTotalItems: Int = 0,
    val seriesError: String? = null,
    val seriesActiveSearchQuery: String? = null,
    /** Keyed by [StalkerVodItem.id] - see [StreamHealthChecker]. Absent from the map means
     * [StreamHealth.UNKNOWN] (no check requested yet), not "checked and unknown". */
    val streamHealth: Map<String, StreamHealth> = emptyMap(),
    /** Non-null shows the VOD playback overlay over whichever of [vodItems]/[seriesItems] is
     * currently browsed - see [IptvViewModel.playVodItem]. */
    val playingVodItem: StalkerVodItem? = null,
    /** Grid vs. list, remembered independently per content type (mirrored to/from
     * [MirrorDashSettings.iptvMoviesViewMode]/`iptvSeriesViewMode` - see
     * [IptvViewModel.setViewMode]'s doc comment), not reset by switching tabs or by [tearDown]. */
    val moviesViewMode: VodViewMode = VodViewMode.GRID,
    val seriesViewMode: VodViewMode = VodViewMode.GRID,

    /** Non-null shows the on-screen "typing a channel number" OSD - see
     * [IptvViewModel.enterChannelDigit]. A TV-remote-only concept: there's no touch entry point
     * that ever sets this. */
    val channelNumberDraft: String? = null,
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
    private var player: IptvPlayer? = null
    private var playerStateJob: Job? = null
    /** Which backends have already been tried for whatever [lastPlayedUrl] currently is - reset
     * to just the current one every time a *new* selection is made ([playOn]), accumulated
     * (without resetting) across each automatic fallback retry of that same selection - see
     * [handlePlayerState]. Bounds the fallback loop to at most [PlayerBackend.entries]`.size`
     * attempts per selection. */
    private var attemptedBackends = mutableSetOf<PlayerBackend>()
    private var lastPlayedUrl: String? = null
    /** Kept in sync from Settings (same cheap-field-assignment pattern as [parentalMode]/[parentalPin]
     * below) so [ensurePlayer] always starts from the current default without an extra suspend read. */
    private var preferredBackend: PlayerBackend = PlayerBackend.EXOPLAYER
    private var isActive = false
    private var connectJob: Job? = null
    private var sleepTimeoutJob: Job? = null
    private var volumePersistJob: Job? = null
    private var channelNumberJob: Job? = null

    // Parental control - kept fresh from Settings without distinctUntilChanged (cheap field
    // assignment, not a WalkieTalkieEngine-style expensive side effect, so no debounce needed).
    // [adultUnlocked] itself is deliberately never persisted - it's a live-session flag, reset per
    // [ParentalControlMode]'s rules in [enterSleep]/[tearDown], not a setting.
    private var parentalMode: ParentalControlMode = ParentalControlMode.DISABLED
    private var parentalPin: String = DEFAULT_PARENTAL_CONTROL_PIN
    private var adultUnlocked = false

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

        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                parentalPin = settings.parentalControlPin
                parentalMode = ParentalControlMode.fromStorageKey(settings.parentalControlMode)
                preferredBackend = PlayerBackend.fromStorageKey(settings.iptvPlayerBackend)
            }
        }

        // Loads the remembered view mode on first connect and picks up any later external
        // change - see setViewMode()'s doc comment for why this is the one source of truth
        // rather than _uiState being updated directly from there.
        viewModelScope.launch {
            settingsRepository.settings
                .map { VodViewMode.fromStorageKey(it.iptvMoviesViewMode) to VodViewMode.fromStorageKey(it.iptvSeriesViewMode) }
                .distinctUntilChanged()
                .collect { (movies, series) ->
                    _uiState.update { it.copy(moviesViewMode = movies, seriesViewMode = series) }
                }
        }
    }

    fun currentPlayer(): IptvPlayer? = player

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
                // Nothing to reconnect just from re-opening the tab - the account is still
                // blocked until the user explicitly retries (see retry()), e.g. after topping up.
                IptvPageState.CONNECTING, IptvPageState.READY, IptvPageState.BLOCKED -> Unit
            }
        } else {
            when (_uiState.value.pageState) {
                IptvPageState.READY -> enterSleep()
                IptvPageState.CONNECTING -> {
                    connectJob?.cancel()
                    connectJob = null
                    _uiState.update { it.copy(pageState = IptvPageState.OFF) }
                }
                // Nothing playing to preserve for a fast resume like READY's enterSleep() does -
                // a full teardown is simpler and releases the session promptly instead of holding
                // a blocked account's share indefinitely.
                IptvPageState.BLOCKED -> tearDown()
                IptvPageState.OFF, IptvPageState.SLEEPING, IptvPageState.ERROR -> Unit
            }
        }
    }

    fun retry() {
        if (_uiState.value.pageState != IptvPageState.ERROR && _uiState.value.pageState != IptvPageState.BLOCKED) return
        beginConnect()
    }

    fun toggleChannelList() {
        _uiState.update { it.copy(showChannelList = !it.showChannelList, showGuide = false) }
    }

    fun toggleGuide() {
        _uiState.update {
            val opening = !it.showGuide
            it.copy(
                showGuide = opening,
                showChannelList = false,
                // Every fresh Guide session starts full-size, whatever it was left at last time.
                guideShowsPreview = if (opening) false else it.guideShowsPreview,
            )
        }
    }

    /** The Guide's own "regular size" button - collapses the preview back to a full-screen Guide
     * without closing it, the reverse of what tapping a channel does. */
    fun collapseGuidePreview() {
        _uiState.update { it.copy(guideShowsPreview = false) }
    }

    fun selectGenre(genreId: String) {
        val genre = _uiState.value.genres.firstOrNull { it.id == genreId }
        if (genre?.censored == true && isLocked()) {
            _uiState.update { it.copy(pendingPinChallenge = PinChallenge.Genre(genreId), pinError = false) }
            return
        }
        applyGenreSelection(genreId)
    }

    private fun applyGenreSelection(genreId: String) {
        _uiState.update { it.copy(selectedGenreId = genreId) }
        loadCensoredGenreChannelsIfNeeded(genreId)
    }

    /** [StalkerPortalClient.fetchChannels] silently excludes every censored genre's channels -
     * see that function's doc comment - so the first time one is actually unlocked/opened, its
     * channels have to be fetched separately and merged in. A no-op once already fetched (or for
     * a non-censored genre, where [StalkerPortalClient.fetchChannels] already covered it). */
    private fun loadCensoredGenreChannelsIfNeeded(genreId: String) {
        val genre = _uiState.value.genres.firstOrNull { it.id == genreId } ?: return
        if (!genre.censored) return
        if (_uiState.value.channels.any { it.genreId == genreId }) return
        val session = client ?: return
        viewModelScope.launch {
            session.fetchCensoredGenreChannels(genreId).onSuccess { fetched ->
                _uiState.update { state -> state.copy(channels = state.channels + fetched) }
            }
        }
    }

    private fun isLocked(): Boolean = parentalMode != ParentalControlMode.DISABLED && !adultUnlocked

    /** Checked against [parentalPin] - on a match, unlocks for the rest of this session (or until
     * [ParentalControlMode.EVERY_REJOIN] re-locks it, see [enterSleep]) and carries out whatever
     * selection the PIN prompt interrupted; on a mismatch, just flags [IptvUiState.pinError] so
     * the dialog can show it without dropping the challenge. */
    fun submitParentalPin(pin: String) {
        if (pin != parentalPin) {
            _uiState.update { it.copy(pinError = true) }
            return
        }
        adultUnlocked = true
        val challenge = _uiState.value.pendingPinChallenge
        _uiState.update { it.copy(pendingPinChallenge = null, pinError = false) }
        when (challenge) {
            is PinChallenge.Channel -> {
                val index = _uiState.value.channels.indexOfFirst { it.id == challenge.channelId }
                if (index >= 0) applyChannelSelection(index)
            }
            is PinChallenge.Genre -> applyGenreSelection(challenge.genreId)
            null -> Unit
        }
    }

    fun dismissPinChallenge() {
        _uiState.update { it.copy(pendingPinChallenge = null, pinError = false) }
    }

    fun togglePlayPause() {
        val active = player ?: return
        if (active.isPlaying) active.pause() else active.play()
    }

    /** Persists debounced, not on every emission - a fine-tune drag alone can fire this dozens
     * of times a second, and only where the user's finger *ends up* is worth remembering. Uses
     * the cancel-and-relaunch idiom (same as [MirrorDashActivity]'s volume-key failsafe timer)
     * rather than a `Flow.debounce` subscription, so nothing gets written before the user has
     * ever actually touched the slider - see [beginConnect] for why that distinction matters. */
    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _uiState.update { it.copy(volume = clamped) }
        player?.volume = clamped

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
        val channel = channels[index]
        if (channel.censored && isLocked()) {
            _uiState.update { it.copy(pendingPinChallenge = PinChallenge.Channel(channel.id), pinError = false) }
            return
        }
        applyChannelSelection(index)
    }

    private fun applyChannelSelection(index: Int) {
        val state = _uiState.value
        if (index == state.currentChannelIndex && state.showGuide) {
            // Tapping the channel that's already playing while the Guide is open means "I've
            // already picked this, just show me it" - closes the Guide outright rather than the
            // usual "preview inline" a *different* channel gets below, and skips
            // playCurrentChannel() since there's nothing to actually change about playback.
            _uiState.update { it.copy(showGuide = false, guideShowsPreview = false, showChannelList = false) }
            return
        }
        _uiState.update {
            it.copy(
                currentChannelIndex = index,
                showChannelList = false,
                // Picking a *different* channel from the Guide previews it inline instead of
                // closing the Guide out from under you - see [IptvUiState.guideShowsPreview].
                // Picking one from the simpler ChannelListPanel instead still closes as before,
                // since showGuide is untouched here and was already false in that case.
                guideShowsPreview = it.guideShowsPreview || it.showGuide,
            )
        }
        playCurrentChannel()
    }

    fun selectChannelById(id: String) {
        val index = _uiState.value.channels.indexOfFirst { it.id == id }
        if (index >= 0) selectChannel(index)
    }

    fun nextChannel() = stepChannel(1)
    fun previousChannel() = stepChannel(-1)

    /** TV-remote number-pad entry (brief: "typing numbers ... should attempt to change the
     * channel to the selected number"). Each digit extends [IptvUiState.channelNumberDraft] and
     * restarts a short inactivity timer (the cancel-and-relaunch idiom [setVolume] also uses to
     * debounce); the timer firing - not a dedicated confirm key - is what actually commits the
     * lookup, since number-pad remotes rarely expose one MirrorDash can rely on. Only meaningful
     * for live TV: Movies/Series have no channel numbers to jump to. */
    fun enterChannelDigit(digit: Char) {
        val current = _uiState.value
        if (current.contentTab != IptvContentTab.LIVE || current.pendingPinChallenge != null) return
        val next = ((_uiState.value.channelNumberDraft ?: "") + digit).take(MAX_CHANNEL_NUMBER_DRAFT_LENGTH)
        _uiState.update { it.copy(channelNumberDraft = next) }
        channelNumberJob?.cancel()
        channelNumberJob = viewModelScope.launch {
            kotlinx.coroutines.delay(CHANNEL_NUMBER_COMMIT_DEBOUNCE_MS)
            commitChannelNumberDraft()
        }
    }

    /** Lets the draft OSD be dismissed early (e.g. Back) without waiting out the debounce. */
    fun cancelChannelNumberEntry() {
        channelNumberJob?.cancel()
        channelNumberJob = null
        _uiState.update { it.copy(channelNumberDraft = null) }
    }

    private fun commitChannelNumberDraft() {
        channelNumberJob = null
        val draft = _uiState.value.channelNumberDraft
        _uiState.update { it.copy(channelNumberDraft = null) }
        if (!draft.isNullOrEmpty()) selectChannelByNumber(draft)
    }

    /** Matches [StalkerChannel.number] with leading zeros ignored on both sides, so a portal
     * that numbers a channel "007" still responds to a remote typing plain "7". Goes through
     * [selectChannel] rather than [applyChannelSelection] directly so a censored match still
     * raises the parental PIN challenge instead of silently tuning to it. */
    fun selectChannelByNumber(number: String) {
        val target = number.trimStart('0').ifEmpty { "0" }
        val index = _uiState.value.channels.indexOfFirst { it.number.trimStart('0').ifEmpty { "0" } == target }
        if (index >= 0) selectChannel(index)
    }

    /** Deliberately doesn't route through [applyChannelSelection] on the unlocked path - channel
     * up/down from a remote shouldn't close an open channel list/Guide the way explicitly picking
     * a channel from one does. */
    private fun stepChannel(delta: Int) {
        val channels = _uiState.value.channels
        if (channels.isEmpty()) return
        val current = _uiState.value.currentChannelIndex
        val next = ((if (current < 0) 0 else current + delta) % channels.size + channels.size) % channels.size
        val channel = channels[next]
        if (channel.censored && isLocked()) {
            _uiState.update { it.copy(pendingPinChallenge = PinChallenge.Channel(channel.id), pinError = false) }
            return
        }
        _uiState.update { it.copy(currentChannelIndex = next) }
        playCurrentChannel()
    }

    // --- Movies/Series ------------------------------------------------------------------------

    /** In-memory only, per session (cleared in [tearDown] along with everything else the portal
     * session owns) - a few minutes' staleness is fine for "is this link still broken", and
     * re-checking on every single tab revisit would defeat the point of caching at all. Value is
     * (result, checkedAtMs). */
    private val healthCache = mutableMapOf<String, Pair<StreamHealth, Long>>()

    /** Switches which of Live/Movies/Series the tab shows. Live playback is paused (not torn
     * down - [player] itself keeps existing so switching back is instant) while browsing
     * Movies/Series, since the same single player is reused for VOD playback (see [playVodItem])
     * rather than running a second decoder just for a tab that isn't even visible most of the
     * time. */
    fun selectContentTab(tab: IptvContentTab) {
        if (_uiState.value.contentTab == tab) return
        // Whatever was playing under the previous tab (a live channel, or a VOD item's overlay)
        // stops - only IptvContentTab.LIVE below explicitly resumes anything.
        player?.pause()
        _uiState.update { it.copy(contentTab = tab, playingVodItem = null) }
        when (tab) {
            IptvContentTab.LIVE -> playCurrentChannel()
            IptvContentTab.MOVIES -> loadVodCategoriesIfNeeded()
            IptvContentTab.SERIES -> loadSeriesCategoriesIfNeeded()
        }
    }

    private fun loadVodCategoriesIfNeeded() {
        if (_uiState.value.vodCategories.isNotEmpty()) return
        val session = client ?: return
        _uiState.update { it.copy(vodLoading = true, vodError = null) }
        viewModelScope.launch {
            session.fetchVodCategories()
                .onSuccess { categories ->
                    _uiState.update { it.copy(vodCategories = categories, vodLoading = false) }
                    categories.firstOrNull()?.let { selectVodCategory(it.id) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(vodLoading = false, vodError = error.message ?: "This provider doesn't seem to offer movies") }
                }
        }
    }

    private fun loadSeriesCategoriesIfNeeded() {
        if (_uiState.value.seriesCategories.isNotEmpty()) return
        val session = client ?: return
        _uiState.update { it.copy(seriesLoading = true, seriesError = null) }
        viewModelScope.launch {
            session.fetchSeriesCategories()
                .onSuccess { categories ->
                    _uiState.update { it.copy(seriesCategories = categories, seriesLoading = false) }
                    categories.firstOrNull()?.let { selectSeriesCategory(it.id) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(seriesLoading = false, seriesError = error.message ?: "This provider doesn't seem to offer series") }
                }
        }
    }

    fun selectVodCategory(categoryId: String) {
        val session = client ?: return
        _uiState.update {
            it.copy(
                selectedVodCategoryId = categoryId, vodItems = emptyList(), vodLoading = true, vodError = null,
                vodNextPage = 1, vodHasMore = false, vodTotalItems = 0, vodActiveSearchQuery = null,
            )
        }
        viewModelScope.launch {
            session.fetchVodItems(categoryId, startPage = 1, limit = VOD_PAGE_FETCH_LIMIT)
                .onSuccess { page ->
                    _uiState.update {
                        it.copy(vodItems = page.items, vodLoading = false, vodNextPage = page.nextPage, vodHasMore = page.hasMore, vodTotalItems = page.totalItems)
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(vodLoading = false, vodError = error.message ?: "Couldn't load this category") } }
        }
    }

    fun selectSeriesCategory(categoryId: String) {
        val session = client ?: return
        _uiState.update {
            it.copy(
                selectedSeriesCategoryId = categoryId, seriesItems = emptyList(), seriesLoading = true, seriesError = null,
                seriesNextPage = 1, seriesHasMore = false, seriesTotalItems = 0, seriesActiveSearchQuery = null,
            )
        }
        viewModelScope.launch {
            session.fetchSeriesItems(categoryId, startPage = 1, limit = VOD_PAGE_FETCH_LIMIT)
                .onSuccess { page ->
                    _uiState.update {
                        it.copy(
                            seriesItems = page.items, seriesLoading = false, seriesNextPage = page.nextPage,
                            seriesHasMore = page.hasMore, seriesTotalItems = page.totalItems,
                        )
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(seriesLoading = false, seriesError = error.message ?: "Couldn't load this category") } }
        }
    }

    /** [IptvSearchMode.DEEP] for Movies (brief: "directly query for all movies that have the
     * parameters") - replaces [IptvUiState.vodItems] with the portal's own catalog-wide search
     * results rather than filtering [selectedVodCategoryId]'s loaded chunk, and remembers [query]
     * in [IptvUiState.vodActiveSearchQuery] so [loadMoreVodItems] knows to keep paginating *this*
     * instead of the category. Callers (the search box) are expected to debounce - this fires a
     * portal request every call, unlike [IptvSearchMode.FILTER] which never leaves the client. */
    fun searchVodDeep(query: String) {
        val session = client ?: return
        if (query.isBlank()) return
        _uiState.update {
            it.copy(
                vodActiveSearchQuery = query, vodItems = emptyList(), vodLoading = true, vodError = null,
                vodNextPage = 1, vodHasMore = false, vodTotalItems = 0,
            )
        }
        viewModelScope.launch {
            session.searchVod(query, startPage = 1, limit = VOD_PAGE_FETCH_LIMIT)
                .onSuccess { page ->
                    _uiState.update {
                        it.copy(vodItems = page.items, vodLoading = false, vodNextPage = page.nextPage, vodHasMore = page.hasMore, vodTotalItems = page.totalItems)
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(vodLoading = false, vodError = error.message ?: "Deep search failed") } }
        }
    }

    /** Same as [searchVodDeep], for series. */
    fun searchSeriesDeep(query: String) {
        val session = client ?: return
        if (query.isBlank()) return
        _uiState.update {
            it.copy(
                seriesActiveSearchQuery = query, seriesItems = emptyList(), seriesLoading = true, seriesError = null,
                seriesNextPage = 1, seriesHasMore = false, seriesTotalItems = 0,
            )
        }
        viewModelScope.launch {
            session.searchSeries(query, startPage = 1, limit = VOD_PAGE_FETCH_LIMIT)
                .onSuccess { page ->
                    _uiState.update {
                        it.copy(
                            seriesItems = page.items, seriesLoading = false, seriesNextPage = page.nextPage,
                            seriesHasMore = page.hasMore, seriesTotalItems = page.totalItems,
                        )
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(seriesLoading = false, seriesError = error.message ?: "Deep search failed") } }
        }
    }

    /** Leaving [IptvSearchMode.DEEP] (query cleared, or switched back to Filter) restores ordinary
     * category browsing - a no-op if nothing was ever selected (falls back to loading categories
     * fresh would be redundant; [selectVodCategory] itself requires a real id). */
    fun clearVodDeepSearch() {
        val categoryId = _uiState.value.selectedVodCategoryId
        if (categoryId != null) {
            selectVodCategory(categoryId)
        } else {
            _uiState.update { it.copy(vodActiveSearchQuery = null, vodItems = emptyList()) }
        }
    }

    fun clearSeriesDeepSearch() {
        val categoryId = _uiState.value.selectedSeriesCategoryId
        if (categoryId != null) {
            selectSeriesCategory(categoryId)
        } else {
            _uiState.update { it.copy(seriesActiveSearchQuery = null, seriesItems = emptyList()) }
        }
    }

    /** Fetches the next chunk (see [VodPage]) and appends it - guarded so a second tap while one
     * is already in flight, or after the category's last chunk already came back empty, is a
     * no-op rather than a redundant/wasted request. Continues whichever of category browsing or
     * an [IptvSearchMode.DEEP] search (see [IptvUiState.vodActiveSearchQuery]) is currently active. */
    fun loadMoreVodItems() {
        val session = client ?: return
        val state = _uiState.value
        if (!state.vodHasMore || state.vodLoadingMore) return
        val searchQuery = state.vodActiveSearchQuery
        val categoryId = state.selectedVodCategoryId
        if (searchQuery == null && categoryId == null) return
        _uiState.update { it.copy(vodLoadingMore = true) }
        viewModelScope.launch {
            val result = if (searchQuery != null) {
                session.searchVod(searchQuery, startPage = state.vodNextPage, limit = VOD_PAGE_FETCH_LIMIT)
            } else {
                session.fetchVodItems(categoryId!!, startPage = state.vodNextPage, limit = VOD_PAGE_FETCH_LIMIT)
            }
            result
                .onSuccess { page ->
                    _uiState.update {
                        it.copy(
                            vodItems = it.vodItems + page.items, vodLoadingMore = false, vodNextPage = page.nextPage, vodHasMore = page.hasMore,
                            // A later chunk that doesn't itself report total_items shouldn't blank
                            // out a total an earlier chunk already established.
                            vodTotalItems = page.totalItems.takeIf { total -> total > 0 } ?: it.vodTotalItems,
                        )
                    }
                }
                .onFailure { _uiState.update { it.copy(vodLoadingMore = false) } }
        }
    }

    fun loadMoreSeriesItems() {
        val session = client ?: return
        val state = _uiState.value
        if (!state.seriesHasMore || state.seriesLoadingMore) return
        val searchQuery = state.seriesActiveSearchQuery
        val categoryId = state.selectedSeriesCategoryId
        if (searchQuery == null && categoryId == null) return
        _uiState.update { it.copy(seriesLoadingMore = true) }
        viewModelScope.launch {
            val result = if (searchQuery != null) {
                session.searchSeries(searchQuery, startPage = state.seriesNextPage, limit = VOD_PAGE_FETCH_LIMIT)
            } else {
                session.fetchSeriesItems(categoryId!!, startPage = state.seriesNextPage, limit = VOD_PAGE_FETCH_LIMIT)
            }
            result
                .onSuccess { page ->
                    _uiState.update {
                        it.copy(
                            seriesItems = it.seriesItems + page.items, seriesLoadingMore = false, seriesNextPage = page.nextPage,
                            seriesHasMore = page.hasMore,
                            seriesTotalItems = page.totalItems.takeIf { total -> total > 0 } ?: it.seriesTotalItems,
                        )
                    }
                }
                .onFailure { _uiState.update { it.copy(seriesLoadingMore = false) } }
        }
    }

    /** Written straight to Settings rather than only `_uiState` - the collector added in `init`
     * mirrors it back in, so this is the one place that ever needs to change for the mode to be
     * both reflected immediately in the UI and remembered (per content type - see
     * [MirrorDashSettings.iptvMoviesViewMode]) the next time this tab is opened. */
    fun setViewMode(contentType: VodContentType, mode: VodViewMode) {
        viewModelScope.launch {
            settingsRepository.update {
                when (contentType) {
                    VodContentType.MOVIES -> iptvMoviesViewMode = mode.storageKey
                    VodContentType.SERIES -> iptvSeriesViewMode = mode.storageKey
                }
            }
        }
    }

    /** Requested lazily per visible card (see `VodItemCard` in `IptvVodScreen.kt`), not for a
     * whole category up front - see [StreamHealthChecker]'s doc comment for why this stays cheap
     * regardless of catalog size. */
    fun checkHealth(item: StalkerVodItem) {
        val cached = healthCache[item.id]
        if (cached != null && System.currentTimeMillis() - cached.second < HEALTH_CACHE_TTL_MS) {
            _uiState.update { it.copy(streamHealth = it.streamHealth + (item.id to cached.first)) }
            return
        }
        if (_uiState.value.streamHealth[item.id] == StreamHealth.CHECKING) return
        val session = client ?: return
        _uiState.update { it.copy(streamHealth = it.streamHealth + (item.id to StreamHealth.CHECKING)) }
        viewModelScope.launch {
            val result = StreamHealthChecker.check(session, item)
            healthCache[item.id] = result to System.currentTimeMillis()
            _uiState.update { it.copy(streamHealth = it.streamHealth + (item.id to result)) }
        }
    }

    /** Reuses the tab's single [player] instance rather than a second one just for VOD - the
     * live channel's own media is simply replaced, and restored by [selectContentTab] when
     * the user switches back to Live. */
    fun playVodItem(item: StalkerVodItem) {
        val session = client ?: return
        val requestedPlayer = player ?: return
        _uiState.update { it.copy(playingVodItem = item) }
        viewModelScope.launch {
            session.resolveVodStreamUrl(item)
                .onSuccess { url ->
                    if (player !== requestedPlayer || _uiState.value.playingVodItem?.id != item.id) return@onSuccess
                    playOn(url)
                }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.message ?: "Couldn't play ${item.name}", playingVodItem = null) }
                }
        }
    }

    fun stopVodPlayback() {
        player?.pause()
        _uiState.update { it.copy(playingVodItem = null) }
    }

    private fun beginConnect() {
        connectJob?.cancel()
        // Normally a no-op (fail()/tearDown() already null this out before beginConnect() is
        // ever called again) - the one exception is retry() from BLOCKED, where blockMessage's
        // path deliberately keeps holding its session share (see that branch below) so a bare
        // acquire() here would otherwise double-count it without a matching release.
        if (client != null) sessionCoordinator.release()
        client = null
        _uiState.update {
            it.copy(
                pageState = IptvPageState.CONNECTING,
                errorMessage = null,
                accountInfo = null,
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
            if (newClient.blockMessage != null) {
                // Handshake/token succeeded - this account just can't stream right now (no
                // credit, expired, banned MAC). fetchAccountInfo is best-effort for the expiry
                // date only; the block itself is already known from blockMessage regardless of
                // whether that call succeeds.
                client = newClient
                val accountInfo = newClient.fetchAccountInfo().getOrNull()
                _uiState.update { it.copy(pageState = IptvPageState.BLOCKED, accountInfo = accountInfo) }
                return@launch
            }
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

            // Resume the remembered channel if it's still in the list, otherwise just the first one.
            val rememberedIndex = channels.indexOfFirst { it.id == config.iptvLastChannelId }.takeIf { it >= 0 }
            val initialIndex = rememberedIndex ?: if (channels.isEmpty()) -1 else 0
            val initialVolume = config.iptvVolume.coerceIn(0f, 1f)
            if (initialVolume > 0f) volumeBeforeMute = initialVolume

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
        // The stricter of the two enabled modes re-locks on every departure, not just a full
        // teardown - "ask every time the tab is left and rejoined" means what it says.
        if (parentalMode == ParentalControlMode.EVERY_REJOIN) adultUnlocked = false
        _uiState.update {
            it.copy(pageState = IptvPageState.SLEEPING, isPlaying = false, pendingPinChallenge = null, pinError = false)
        }
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
        channelNumberJob?.cancel()
        channelNumberJob = null
        releasePlayer()
        if (client != null) sessionCoordinator.release()
        client = null
        epgCache.clear()
        healthCache.clear()
        // A full teardown always re-locks, regardless of mode - "once per session" means for as
        // long as this session (this portal connection) is actually alive, and this is where it
        // ends.
        adultUnlocked = false
        _uiState.update {
            IptvUiState(
                configured = it.configured,
                pageState = IptvPageState.OFF,
                playerEpoch = it.playerEpoch,
                volume = it.volume,
                // Not session state like everything else this resets - a remembered UI
                // preference (see setViewMode's doc comment) that happens to live on IptvUiState
                // rather than survive a teardown by way of the settings collector re-emitting a
                // value that, from its own distinctUntilChanged's point of view, never changed.
                moviesViewMode = it.moviesViewMode,
                seriesViewMode = it.seriesViewMode,
            )
        }
    }

    private fun ensurePlayer() {
        if (player != null) return
        createPlayer(preferredBackend)
    }

    private fun releasePlayer() {
        playerStateJob?.cancel()
        playerStateJob = null
        player?.release()
        player = null
        _uiState.update { it.copy(playerEpoch = it.playerEpoch + 1) }
    }

    /** Builds a fresh [IptvPlayer] for [backend], replacing whatever [player] currently is (the
     * caller is responsible for releasing the old one first, if any - both [releasePlayer] and
     * [handlePlayerState]'s fallback path already do). Deliberately does **not** touch
     * [attemptedBackends] - [playOn] resets it for a genuinely new selection, [handlePlayerState]
     * adds to it for a same-selection fallback retry; this function is used by both and shouldn't
     * assume which one is calling. */
    private fun createPlayer(backend: PlayerBackend) {
        val newPlayer = IptvPlayerFactory.create(backend, getApplication())
        newPlayer.volume = _uiState.value.volume
        player = newPlayer
        playerStateJob?.cancel()
        playerStateJob = viewModelScope.launch {
            newPlayer.state.collect { state -> handlePlayerState(newPlayer, state) }
        }
        _uiState.update { it.copy(playerEpoch = it.playerEpoch + 1, playerBackend = backend) }
    }

    /** Bridges an [IptvPlayer]'s own [IptvPlayerState] into [IptvUiState], and is also where
     * automatic fallback lives: an error from a backend that hasn't exhausted every entry in
     * [PlayerBackend] for the current selection quietly retries on the next one instead of
     * surfacing - only once every backend has failed does the error actually reach
     * [IptvUiState.errorMessage]. [source] guards against a state emission from a player that's
     * since been superseded (e.g. two fallbacks racing, or a manual [setPlayerBackend] mid-retry). */
    private fun handlePlayerState(source: IptvPlayer, state: IptvPlayerState) {
        if (player !== source) return
        if (state.errorMessage != null) {
            val next = PlayerBackend.entries.firstOrNull { it !in attemptedBackends }
            if (next != null) {
                Log.w(TAG, "${source.backend.displayName} couldn't play this stream (${state.errorMessage}) - trying ${next.displayName}")
                attemptedBackends += next
                val url = lastPlayedUrl
                source.release()
                createPlayer(next)
                if (url != null) player?.setMediaAndPlay(url)
                return
            }
        }
        _uiState.update { it.copy(isPlaying = state.isPlaying, isBuffering = state.isBuffering, errorMessage = state.errorMessage) }
    }

    /** The one place that actually starts a new piece of content on [player] - live channel and
     * VOD selection both funnel through this, which is what lets [handlePlayerState]'s fallback
     * stay backend-agnostic (it just replays [lastPlayedUrl] on whichever backend is next, no
     * knowledge of channels/VOD items needed). Resets [attemptedBackends] to just the current
     * backend - a *new* selection always gets a fresh set of fallback attempts, even if an earlier
     * selection this session already exhausted every backend. */
    private fun playOn(url: String) {
        val active = player ?: return
        lastPlayedUrl = url
        attemptedBackends = mutableSetOf(active.backend)
        active.setMediaAndPlay(url)
    }

    /** The in-player quick switcher (brief: "add a player option next to the volume button") -
     * unlike changing the Settings default alone, this hot-swaps whatever's playing *right now*
     * onto the new backend immediately, replaying [lastPlayedUrl]. Also persists the choice as the
     * new Settings default (same "toggle in the UI, it sticks" pattern [setViewMode] already
     * uses), so it's remembered next time too, not just for this session. */
    fun setPlayerBackend(backend: PlayerBackend) {
        viewModelScope.launch { settingsRepository.update { iptvPlayerBackend = backend.storageKey } }
        if (player?.backend == backend) return
        val url = lastPlayedUrl
        player?.release()
        createPlayer(backend)
        attemptedBackends = mutableSetOf(backend)
        if (url != null) player?.setMediaAndPlay(url)
    }

    private fun playCurrentChannel() {
        val channel = _uiState.value.currentChannel ?: return
        val session = client ?: return
        val requestedPlayer = player ?: return
        viewModelScope.launch { settingsRepository.update { iptvLastChannelId = channel.id } }
        viewModelScope.launch {
            session.resolveStreamUrl(channel)
                .onSuccess { url ->
                    if (player !== requestedPlayer) return@onSuccess // superseded by a newer session
                    playOn(url)
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

        /** How long a cached [StreamHealth] result is trusted before [checkHealth] re-checks -
         * long enough that scrolling a grid back and forth doesn't re-hit the portal, short
         * enough that a link fixed provider-side shows up again within one browsing session. */
        private const val HEALTH_CACHE_TTL_MS = 5 * 60 * 1000L

        /** How many Movies/Series items [selectVodCategory]/[loadMoreVodItems] (and their series
         * equivalents) fetch per chunk - see [VodPage]'s doc comment for why this is chunked at
         * all rather than fetching a whole category up front. */
        private const val VOD_PAGE_FETCH_LIMIT = 100

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
