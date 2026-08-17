package com.sconcept.mirrordash.iptv

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoSettings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.viewinterop.AndroidView
import com.sconcept.mirrordash.iptv.player.IptvPlayer
import com.sconcept.mirrordash.iptv.player.PlayerBackend
import com.sconcept.mirrordash.launcher.AppContainer
import com.sconcept.mirrordash.ui.theme.MDTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Full-bleed live TV tab reproducing what STBEMU Pro does with a Stalker/Ministra portal - see
 * [StalkerPortalClient]'s doc comment - but with an on-screen channel list/transport instead of a
 * remote-control-driven STB UI, since MirrorDash has no physical remote to design around.
 */
@Composable
fun IptvScreen(viewModel: IptvViewModel, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val container = remember { AppContainer.get(context) }
    val recordingEngine = remember { container.iptvRecordingEngine }
    val recordingState by recordingEngine.uiState.collectAsState()
    val settings by container.settingsRepository.settings.collectAsState(initial = null)
    val scheduledRecordings = settings?.iptvScheduledRecordings.orEmpty()
    val recordingHistory = settings?.iptvRecordingHistory.orEmpty()
    var showDownloadManager by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // A "download complete" notification tap bumps this (see IptvRecordingEngine.requestOpenDownloadManager's
    // doc comment) - MirrorDashRoot reacts to the same epoch to jump to this tab in the first
    // place, this is the other half: once here, actually open the panel. Skips the initial 0 so a
    // fresh composition doesn't pop the panel open unprompted.
    LaunchedEffect(recordingState.openRequestEpoch) {
        if (recordingState.openRequestEpoch > 0) showDownloadManager = true
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        when {
            !uiState.configured -> CenteredMessage(
                title = "IPTV isn't set up yet",
                subtitle = "Add a portal address and MAC address in Settings to turn this tab on.",
            )
            uiState.pageState == IptvPageState.CONNECTING -> LoadingState()
            uiState.pageState == IptvPageState.ERROR -> ErrorState(
                message = uiState.errorMessage ?: "Couldn't connect to the portal",
                onRetry = viewModel::retry,
            )
            uiState.pageState == IptvPageState.BLOCKED -> AccountBlockedState(
                accountInfo = uiState.accountInfo,
                onRetry = viewModel::retry,
            )
            uiState.pageState == IptvPageState.SLEEPING -> LoadingState()
            else -> Column(modifier = Modifier.fillMaxSize()) {
                // One row above everything else, regardless of which content type is currently
                // shown below it - see [IptvContentTab]. Live TV having no channels (next) or a
                // Movies/Series category coming back empty doesn't affect whether the *other*
                // tabs are reachable, so this sits outside all three branches rather than only
                // appearing once live TV specifically is ready.
                IptvContentTabsRow(selected = uiState.contentTab, onSelect = viewModel::selectContentTab)

                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    when (uiState.contentTab) {
                        IptvContentTab.LIVE -> if (uiState.channels.isEmpty()) {
                            CenteredMessage(
                                title = "No channels",
                                subtitle = "The portal connected but didn't return any live channels.",
                            )
                        } else {
                            LiveTvContent(
                                uiState = uiState,
                                viewModel = viewModel,
                                recordingEngine = recordingEngine,
                                recordingState = recordingState,
                                scheduledRecordings = scheduledRecordings,
                                onToggleHistory = { showDownloadManager = !showDownloadManager },
                                scope = scope,
                            )
                        }
                        IptvContentTab.MOVIES, IptvContentTab.SERIES -> {
                            val contentType = if (uiState.contentTab == IptvContentTab.MOVIES) VodContentType.MOVIES else VodContentType.SERIES
                            IptvVodBrowser(
                                uiState = uiState,
                                contentType = contentType,
                                onSelectCategory = if (contentType == VodContentType.MOVIES) viewModel::selectVodCategory else viewModel::selectSeriesCategory,
                                onLoadMore = if (contentType == VodContentType.MOVIES) viewModel::loadMoreVodItems else viewModel::loadMoreSeriesItems,
                                onSetViewMode = { mode -> viewModel.setViewMode(contentType, mode) },
                                onDeepSearch = if (contentType == VodContentType.MOVIES) viewModel::searchVodDeep else viewModel::searchSeriesDeep,
                                onClearDeepSearch = if (contentType == VodContentType.MOVIES) viewModel::clearVodDeepSearch else viewModel::clearSeriesDeepSearch,
                                onCheckHealth = viewModel::checkHealth,
                                onPlay = viewModel::playVodItem,
                                onDownload = { item -> recordingEngine.downloadVodItem(item) },
                            )
                            uiState.playingVodItem?.let { item ->
                                VodPlayerScreen(
                                    uiState = uiState,
                                    viewModel = viewModel,
                                    recordingEngine = recordingEngine,
                                    recordingState = recordingState,
                                    item = item,
                                    onToggleHistory = { showDownloadManager = !showDownloadManager },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showDownloadManager) {
            DownloadManagerPanel(
                activeRecording = recordingState.activeRecording,
                pending = scheduledRecordings,
                recordings = recordingHistory,
                onStop = { recordingEngine.stop() },
                onCancelPending = { id -> scope.launch { recordingEngine.cancelScheduledRecording(id) } },
                onDismiss = { showDownloadManager = false },
            )
        }

        uiState.pendingPinChallenge?.let { _ ->
            ParentalPinDialog(
                pinError = uiState.pinError,
                onSubmit = viewModel::submitParentalPin,
                onDismiss = viewModel::dismissPinChallenge,
            )
        }
    }
}

/** The live-TV player/controls/Guide/channel-list, exactly as it behaved before Movies/Series
 * existed - factored out of [IptvScreen] purely so [IptvContentTab.LIVE] is one branch alongside
 * the other two rather than the only thing that Composable could ever render. */
@Composable
private fun LiveTvContent(
    uiState: IptvUiState,
    viewModel: IptvViewModel,
    recordingEngine: IptvRecordingEngine,
    recordingState: RecordingEngineUiState,
    scheduledRecordings: List<ScheduledRecording>,
    onToggleHistory: () -> Unit,
    scope: CoroutineScope,
) {
    // Factored out rather than duplicated across the split-preview and full-size
    // branches below - every param but the modifier (which controls whether this
    // fills the screen or just the bottom 70%) and showPreview/onCollapsePreview is
    // identical either way.
    val guide: @Composable (modifier: Modifier, showPreview: Boolean) -> Unit = { guideModifier, showPreview ->
        IptvGuideOverlay(
            channels = uiState.displayedChannels,
            currentChannelId = uiState.currentChannel?.id,
            onSelectChannel = { viewModel.selectChannelById(it.id) },
            onDismiss = viewModel::toggleGuide,
            loadEpg = viewModel::epgFor,
            genres = uiState.genres,
            selectedGenreId = uiState.selectedGenreId,
            onSelectGenre = viewModel::selectGenre,
            scheduledRecordings = scheduledRecordings,
            activeRecording = recordingState.activeRecording,
            onScheduleProgram = { channel, program ->
                scope.launch {
                    recordingEngine.scheduleRecording(
                        ScheduledRecording(
                            id = UUID.randomUUID().toString(),
                            channelId = channel.id,
                            channelName = channel.name,
                            channelNumber = channel.number,
                            channelCmd = channel.cmd,
                            programTitle = program.title,
                            startEpochSeconds = program.startEpochSeconds,
                            endEpochSeconds = program.endEpochSeconds,
                        ),
                    )
                }
            },
            onCancelScheduled = { id -> scope.launch { recordingEngine.cancelScheduledRecording(id) } },
            onRecordLiveProgram = { channel, program -> recordingEngine.startUntil(channel, program.endEpochSeconds) },
            onStopRecording = { recordingEngine.stop() },
            onCollapsePreview = if (showPreview) viewModel::collapseGuidePreview else null,
            modifier = guideModifier,
        )
    }

    if (uiState.showGuide && uiState.guideShowsPreview) {
        // Video on top, Guide below, both visible at once - the preview a channel tap
        // from the Guide starts (see [IptvUiState.guideShowsPreview]'s doc comment).
        // 30/70 via Column weights, not two overlapping fillMaxSize Boxes - simpler to
        // reason about than z-order + manual height math, and Compose's own layout
        // pass keeps the two regions from ever fighting over space.
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().weight(GUIDE_PREVIEW_HEIGHT_FRACTION).background(Color.Black)) {
                PlayerSurface(playerEpoch = uiState.playerEpoch, getPlayer = viewModel::currentPlayer)
                if (uiState.isBuffering) {
                    CircularProgressIndicator(
                        color = MDTheme.colors.accent,
                        modifier = Modifier.align(Alignment.Center).size(28.dp),
                    )
                }
            }
            guide(Modifier.fillMaxWidth().weight(1f - GUIDE_PREVIEW_HEIGHT_FRACTION), true)
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            PlayerSurface(playerEpoch = uiState.playerEpoch, getPlayer = viewModel::currentPlayer)
            if (uiState.isBuffering) {
                CircularProgressIndicator(
                    color = MDTheme.colors.accent,
                    modifier = Modifier.align(Alignment.Center).size(40.dp),
                )
            }
            PlaybackControls(
                title = uiState.currentChannel?.let { "${it.number}  ${it.name}" } ?: "",
                isPlaying = uiState.isPlaying,
                volume = uiState.volume,
                playerBackend = uiState.playerBackend,
                onPlayPause = viewModel::togglePlayPause,
                onPrevious = viewModel::previousChannel,
                onNext = viewModel::nextChannel,
                onToggleChannelList = viewModel::toggleChannelList,
                onToggleGuide = viewModel::toggleGuide,
                onVolumeChange = viewModel::setVolume,
                onToggleMute = viewModel::toggleMute,
                onToggleHistory = onToggleHistory,
                onSelectPlayerBackend = viewModel::setPlayerBackend,
                recordSlot = {
                    RecordButton(
                        hasTarget = uiState.currentChannel != null,
                        recordingState = recordingState,
                        onStart = { uiState.currentChannel?.let { recordingEngine.startManual(it) } },
                        onStartTimed = { minutes -> uiState.currentChannel?.let { recordingEngine.startFixedDuration(it, minutes) } },
                        onOpenSchedule = viewModel::toggleGuide,
                        onStop = { recordingEngine.stop() },
                    )
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            if (uiState.showChannelList) {
                ChannelListPanel(
                    displayedChannels = uiState.displayedChannels,
                    allChannels = uiState.channels,
                    currentChannelId = uiState.currentChannel?.id,
                    onSelect = { viewModel.selectChannelById(it.id) },
                    onDismiss = viewModel::toggleChannelList,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
            if (uiState.showGuide) {
                guide(Modifier.fillMaxSize(), false)
            }
        }
    }
}

/** Movies/Series playback - reuses [PlayerSurface]/[PlaybackControls]/[RecordButton] verbatim
 * rather than a second, Media3-native-controller implementation (mid-session feedback: the bottom
 * controls looked and behaved differently for Movies/Series than for live TV, and that was
 * unwanted - the same bar, not two codebases for it). A movie/series item is still just "the one
 * thing currently on the shared [IptvPlayer]" (see [IptvViewModel.playVodItem]), the same shape
 * live TV's player already handles - the one real difference is a seek bar, since a VOD file
 * (unlike a live stream) can be scrubbed; [PlaybackControls] renders that itself whenever
 * [PlaybackControls]'s `seekPlayer` param is non-null. */
@Composable
private fun VodPlayerScreen(
    uiState: IptvUiState,
    viewModel: IptvViewModel,
    recordingEngine: IptvRecordingEngine,
    recordingState: RecordingEngineUiState,
    item: StalkerVodItem,
    onToggleHistory: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        PlayerSurface(playerEpoch = uiState.playerEpoch, getPlayer = viewModel::currentPlayer)
        if (uiState.isBuffering) {
            CircularProgressIndicator(color = MDTheme.colors.accent, modifier = Modifier.align(Alignment.Center).size(40.dp))
        }
        RoundIconButton(
            icon = Icons.Filled.Close,
            contentDescription = "Close",
            onClick = viewModel::stopVodPlayback,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
        )
        PlaybackControls(
            title = item.name,
            isPlaying = uiState.isPlaying,
            volume = uiState.volume,
            playerBackend = uiState.playerBackend,
            onPlayPause = viewModel::togglePlayPause,
            onVolumeChange = viewModel::setVolume,
            onToggleMute = viewModel::toggleMute,
            onToggleHistory = onToggleHistory,
            onSelectPlayerBackend = viewModel::setPlayerBackend,
            recordSlot = {
                RecordButton(
                    hasTarget = true,
                    recordingState = recordingState,
                    onStart = { recordingEngine.downloadVodItem(item) },
                    onStop = { recordingEngine.stop() },
                )
            },
            seekPlayer = viewModel::currentPlayer,
            seekEpoch = uiState.playerEpoch,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** Numeric keypad, not a raw text field - this device is touch-driven (see this file's doc
 * comment), but a PIN is still much faster to tap out on dedicated number keys than to hunt for
 * digits on a full software keyboard. PIN length is 0-6 digits (see [MAX_PARENTAL_CONTROL_PIN_LENGTH])
 * with no fixed target, so submission is an explicit checkmark tap rather than auto-submitting
 * once some assumed length is reached.
 */
@Composable
private fun ParentalPinDialog(pinError: Boolean, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var digits by remember { mutableStateOf("") }
    // A wrong PIN clears the field so the next attempt starts clean, rather than piling more
    // digits onto an already-wrong entry.
    LaunchedEffect(pinError) { if (pinError) digits = "" }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onDismiss),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(MDTheme.colors.surfaceElevated)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                .padding(28.dp)
                .width(300.dp),
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = MDTheme.colors.accent, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(10.dp))
            Text("Parental PIN", style = MDTheme.type.settingTitle, color = MDTheme.colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                if (pinError) "Incorrect PIN" else "This channel is age-restricted",
                style = MDTheme.type.caption,
                color = if (pinError) MDTheme.colors.danger else MDTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                if (digits.isEmpty()) " " else "•".repeat(digits.length),
                style = MDTheme.type.settingTitle,
                color = MDTheme.colors.textPrimary,
                letterSpacing = 6.sp,
            )
            Spacer(Modifier.height(18.dp))
            val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { digit ->
                        PinKey(label = digit) {
                            if (digits.length < MAX_PARENTAL_CONTROL_PIN_LENGTH) digits += digit
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PinKey(icon = Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete digit") {
                    digits = digits.dropLast(1)
                }
                PinKey(label = "0") {
                    if (digits.length < MAX_PARENTAL_CONTROL_PIN_LENGTH) digits += "0"
                }
                PinKey(icon = Icons.Filled.Check, contentDescription = "Submit PIN", tint = MDTheme.colors.accent) {
                    onSubmit(digits)
                }
            }
        }
    }
}

@Composable
private fun PinKey(
    label: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    contentDescription: String? = null,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.1f))
            .clickable(onClick = onClick),
    ) {
        if (label != null) {
            Text(label, style = MDTheme.type.settingTitle, color = Color.White)
        } else if (icon != null) {
            Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

/** [playerEpoch] isn't read directly - passing it in is what makes this composable recompose (and
 * so re-fetch a fresh render view via [getPlayer]) whenever the ViewModel tears down/recreates/
 * swaps the underlying player, e.g. across a sleep/wake cycle or a [PlayerBackend] switch. Each
 * backend owns a different concrete View subclass ([IptvPlayer.view]'s doc comment), so unlike the
 * single-backend version of this composable, `update` can't just rebind a `PlayerView.player`
 * property - a backend switch needs the whole View recreated, which `key(playerEpoch)` already
 * forces by disposing and re-running `factory` from scratch. */
@Composable
private fun PlayerSurface(playerEpoch: Int, getPlayer: () -> IptvPlayer?) {
    androidx.compose.runtime.key(playerEpoch) {
        AndroidView(
            factory = { ctx -> getPlayer()?.view(ctx) ?: android.view.View(ctx) },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Shared, single implementation for both the live-TV tab and Movies/Series playback - a mid-
 * session request specifically asked that these not be two different controls/two codebases (VOD
 * previously used Media3's own native [PlayerView] controller, which looked and behaved
 * differently from this bar). [onPrevious]/[onNext]/[onToggleChannelList]/[onToggleGuide] are
 * null for VOD (there's no channel-up/down or guide concept for a single on-demand item) and
 * simply hide that button rather than disabling it - [recordSlot] is always supplied since both
 * contexts have *something* to start capturing, just via a different [RecordButton] call
 * ([IptvViewModel.playVodItem]/[IptvRecordingEngine.downloadVodItem] vs.
 * [IptvRecordingEngine.startManual]/`startFixedDuration`). [seekPlayer] non-null renders a seek
 * bar above the button row - only VOD passes one, a live stream has no scrubbable timeline. */
@Composable
private fun PlaybackControls(
    title: String,
    isPlaying: Boolean,
    volume: Float,
    playerBackend: PlayerBackend,
    onPlayPause: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onToggleHistory: () -> Unit,
    onSelectPlayerBackend: (PlayerBackend) -> Unit,
    recordSlot: @Composable () -> Unit,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    onToggleChannelList: (() -> Unit)? = null,
    onToggleGuide: (() -> Unit)? = null,
    seekPlayer: (() -> IptvPlayer?)? = null,
    seekEpoch: Int = 0,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text(title, style = MDTheme.type.settingTitle, color = Color.White)
        if (seekPlayer != null) {
            Spacer(Modifier.height(6.dp))
            SeekRow(getPlayer = seekPlayer, playerEpoch = seekEpoch)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (onPrevious != null) RoundIconButton(icon = Icons.Filled.SkipPrevious, contentDescription = "Previous", onClick = onPrevious)
            RoundIconButton(
                icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                onClick = onPlayPause,
            )
            if (onNext != null) RoundIconButton(icon = Icons.Filled.SkipNext, contentDescription = "Next", onClick = onNext)
            Spacer(Modifier.width(8.dp))
            if (onToggleChannelList != null) {
                RoundIconButton(icon = Icons.AutoMirrored.Filled.List, contentDescription = "Channel list", onClick = onToggleChannelList)
            }
            if (onToggleGuide != null) RoundIconButton(icon = Icons.Filled.GridView, contentDescription = "Guide", onClick = onToggleGuide)
            recordSlot()
            RoundIconButton(icon = Icons.Filled.History, contentDescription = "Download Manager", onClick = onToggleHistory)
            PlayerBackendButton(current = playerBackend, onSelect = onSelectPlayerBackend)
            VolumeButton(volume = volume, onVolumeChange = onVolumeChange, onToggleMute = onToggleMute)
        }
    }
}

/** Quick in-player switch between [PlayerBackend]s (brief: "add a player option next to the
 * volume button") - same expand-in-place shape as [VolumeButton]/[RecordButton]: collapsed, a
 * round icon; tapped, it expands into one chip per backend (reusing [RecordChip] rather than a
 * parallel chip composable), the current one highlighted. Picking one calls
 * [IptvViewModel.setPlayerBackend], which both hot-swaps playback immediately and remembers the
 * choice as the new Settings default - this button doesn't track any state of its own beyond
 * whether it's expanded. */
@Composable
private fun PlayerBackendButton(current: PlayerBackend, onSelect: (PlayerBackend) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .animateContentSize()
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.14f)),
    ) {
        IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.VideoSettings, contentDescription = "Player: ${current.displayName}", tint = Color.White)
        }
        if (expanded) {
            PlayerBackend.entries.forEach { backend ->
                RecordChip(if (backend == current) "${backend.displayName} ✓" else backend.displayName) {
                    onSelect(backend)
                    expanded = false
                }
            }
            Spacer(Modifier.width(6.dp))
        }
    }
}

/** A thin position/duration row with a drag-to-seek [Slider] - the one piece live TV's controls
 * never needed (a live stream has nothing to scrub to) that Movies/Series playback does, now that
 * VOD no longer gets that for free from Media3's own controller (see [PlaybackControls]'s doc
 * comment). Polls [getPlayer]'s position on a timer rather than subscribing to a position Flow -
 * none of the three [PlayerBackend]s expose one, only discontinuity/state-change callbacks, so
 * periodic polling is the one approach that works the same regardless of which is active. */
@Composable
private fun SeekRow(getPlayer: () -> IptvPlayer?, playerEpoch: Int) {
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }

    LaunchedEffect(playerEpoch) {
        while (true) {
            if (!isDragging) {
                getPlayer()?.let { player ->
                    positionMs = player.currentPositionMs.coerceAtLeast(0L)
                    durationMs = player.durationMs.coerceAtLeast(0L)
                }
            }
            delay(500)
        }
    }

    val fraction = if (durationMs > 0) {
        if (isDragging) dragFraction else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            formatPlaybackTime(if (isDragging) (dragFraction * durationMs).toLong() else positionMs),
            style = MDTheme.type.caption,
            color = Color.White.copy(alpha = 0.8f),
        )
        Slider(
            value = fraction,
            onValueChange = { isDragging = true; dragFraction = it },
            onValueChangeFinished = {
                if (durationMs > 0) getPlayer()?.seekTo((dragFraction * durationMs).toLong())
                isDragging = false
            },
            colors = SliderDefaults.colors(
                thumbColor = MDTheme.colors.accent,
                activeTrackColor = MDTheme.colors.accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.25f),
            ),
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )
        Text(formatPlaybackTime(durationMs), style = MDTheme.type.caption, color = Color.White.copy(alpha = 0.8f))
    }
}

private fun formatPlaybackTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
    }
}

/**
 * Same expand-in-place shape as [VolumeButton] - the icon itself grows into the control, not a
 * popup elsewhere - but with up to three destinations instead of one continuous control, since
 * "record" isn't a single value to drag, it's a choice of *how* (brief: "when clicking record,
 * the 3 options are offered"). Collapsed: a round icon, red when idle so it reads as "record" at
 * a glance (the one place here that breaks from the neutral white icon language on purpose - red
 * already means "record" everywhere else, overriding that for visual consistency would cost more
 * than it gains). Expanded while idle: chips for whichever of [onStart] (always available, live's
 * "Now"/VOD's only option)/[onStartTimed] (live only)/[onOpenSchedule] (live only) are non-null.
 * While actively recording/downloading: collapses that choice to the one that still applies,
 * Stop, with a live elapsed/size readout standing in for the percentage the volume control shows
 * in the same slot. Shared between live TV and VOD (see [PlaybackControls]'s doc comment) - which
 * context is calling only changes which of the nullable params are supplied, not this function's
 * own logic.
 */
@Composable
private fun RecordButton(
    hasTarget: Boolean,
    recordingState: RecordingEngineUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onStartTimed: ((Int) -> Unit)? = null,
    onOpenSchedule: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var showDurationPicker by remember { mutableStateOf(false) }
    val active = recordingState.activeRecording

    LaunchedEffect(active) { if (active == null) { expanded = false; showDurationPicker = false } }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .animateContentSize()
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.14f)),
    ) {
        IconButton(
            onClick = {
                if (active != null) {
                    onStop()
                } else {
                    expanded = !expanded
                    showDurationPicker = false
                }
            },
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                if (active != null) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                contentDescription = if (active != null) "Stop" else "Record",
                tint = RecordRed,
            )
        }

        if (active != null) {
            Text(
                "${active.channelName} · ${formatMegabytes(active.bytesWritten)}",
                style = MDTheme.type.caption,
                color = Color.White,
                modifier = Modifier.padding(start = 4.dp, end = 16.dp),
            )
        } else if (expanded && hasTarget) {
            if (showDurationPicker && onStartTimed != null) {
                listOf(30, 60, 90).forEach { minutes ->
                    RecordChip("${minutes}m") { onStartTimed(minutes); expanded = false }
                }
            } else {
                RecordChip("Now") { onStart(); expanded = false }
                if (onStartTimed != null) RecordChip("Timed") { showDurationPicker = true }
                if (onOpenSchedule != null) RecordChip("Schedule") { expanded = false; onOpenSchedule() }
            }
            Spacer(Modifier.width(6.dp))
        }
    }
}

@Composable
private fun RecordChip(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MDTheme.type.caption,
        color = Color.White,
        modifier = Modifier
            .padding(start = 4.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

private fun formatMegabytes(bytes: Long): String =
    String.format(java.util.Locale.US, "%.1f MB", bytes / (1024f * 1024f))

private val RecordRed = Color(0xFFE0433D)

/**
 * The volume control *is* the icon - not a button that opens something elsewhere. Collapsed,
 * it's a plain round icon matching every other control here. First tap expands it in place into
 * a horizontal bar (the icon becomes its leading edge); a second tap, now that it's expanded,
 * toggles mute instead of collapsing - collapsing back to just the icon happens on its own after
 * a few seconds of no interaction, the same "appears, then gets out of the way" behavior Android
 * TV's own system volume overlay uses, since there's no natural "tap outside to dismiss" for
 * something that lives inline in the control row rather than as a popup.
 *
 * Dragging the bar while muted (volume at 0) unmutes as a side effect for free - "muted" isn't a
 * separate flag here, just volume == 0, so setting any other value already leaves that state.
 */
@Composable
private fun VolumeButton(volume: Float, onVolumeChange: (Float) -> Unit, onToggleMute: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var interactionTick by remember { mutableStateOf(0) }
    val icon = when {
        volume <= 0f -> Icons.AutoMirrored.Filled.VolumeOff
        volume < 0.5f -> Icons.AutoMirrored.Filled.VolumeDown
        else -> Icons.AutoMirrored.Filled.VolumeUp
    }

    LaunchedEffect(expanded, interactionTick) {
        if (expanded) {
            delay(VOLUME_BAR_AUTO_COLLAPSE_MS)
            expanded = false
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .animateContentSize()
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.14f)),
    ) {
        IconButton(
            onClick = {
                interactionTick++
                if (expanded) onToggleMute() else expanded = true
            },
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                icon,
                contentDescription = if (!expanded) "Volume" else if (volume <= 0f) "Unmute" else "Mute",
                tint = Color.White,
            )
        }
        if (expanded) {
            VolumeSlider(
                volume = volume,
                onVolumeChange = { onVolumeChange(it); interactionTick++ },
                modifier = Modifier.width(140.dp),
            )
            Text(
                "${(volume * 100).toInt()}%",
                style = MDTheme.type.caption,
                color = Color.White,
                modifier = Modifier.padding(start = 10.dp, end = 16.dp),
            )
        }
    }
}

private const val VOLUME_BAR_AUTO_COLLAPSE_MS = 4000L

/** The Guide's split-preview layout (see [IptvUiState.guideShowsPreview]) - preview on top,
 * Guide below. */
private const val GUIDE_PREVIEW_HEIGHT_FRACTION = 0.3f

/** How far (in dp) the finger has to drag down, while holding, before fine-tuning bottoms out at
 * [FINE_TUNE_MIN_SENSITIVITY] - past that point extra downward movement doesn't get any finer.
 * Both are the one place to retune the gesture's feel. */
private val FINE_TUNE_MAX_DRAG = 160.dp
private const val FINE_TUNE_MIN_SENSITIVITY = 0.08f

/**
 * A regular horizontal volume bar - drag left/right to set volume directly, same as any slider.
 * The twist (brief: "sliding down a bit to have better controls"): keep holding and drag your
 * finger *down* away from the bar, and the same horizontal movement maps to a smaller and smaller
 * volume change, down to [FINE_TUNE_MIN_SENSITIVITY] of normal at [FINE_TUNE_MAX_DRAG] - letting
 * you land exactly on a quiet value a plain slider can't hit with one thumb-width of travel.
 * Sliding back up restores full sensitivity continuously, not as a snap back - there's no
 * separate "fine mode" to enter or leave, just how far down the finger currently is.
 */
@Composable
private fun VolumeSlider(volume: Float, onVolumeChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val latestVolume = rememberUpdatedState(volume)
    val trackHeight = 6.dp
    val thumbSize = 18.dp

    BoxWithConstraints(
        modifier = modifier
            .height(32.dp)
            .pointerInput(Unit) {
                val maxDragPx = FINE_TUNE_MAX_DRAG.toPx()
                var downDragPx = 0f
                detectDragGestures(
                    onDragStart = { downDragPx = 0f },
                    onDragEnd = { downDragPx = 0f },
                    onDragCancel = { downDragPx = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        downDragPx = (downDragPx + dragAmount.y).coerceAtLeast(0f)
                        val sensitivity = lerp(1f, FINE_TUNE_MIN_SENSITIVITY, (downDragPx / maxDragPx).coerceIn(0f, 1f))
                        val delta = (dragAmount.x / size.width.toFloat()) * sensitivity
                        onVolumeChange((latestVolume.value + delta).coerceIn(0f, 1f))
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val trackWidth = maxWidth
        Box(
            Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.22f)),
        )
        Box(
            Modifier
                .fillMaxWidth(volume.coerceIn(0f, 1f))
                .height(trackHeight)
                .clip(RoundedCornerShape(50))
                .background(MDTheme.colors.accent),
        )
        Box(
            Modifier
                .offset(x = (trackWidth - thumbSize) * volume.coerceIn(0f, 1f))
                .size(thumbSize)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f)),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White)
    }
}

/** [displayedChannels] is [IptvUiState.displayedChannels] (already narrowed to the selected
 * genre) - what [IptvSearchMode.FILTER] searches within. [allChannels] is the full, unfiltered
 * [IptvUiState.channels] - what [IptvSearchMode.DEEP] searches instead, ignoring the genre
 * selection entirely (brief: "deep search... directly query for all"). Unlike Movies/Series, this
 * needs no portal round-trip either way - [StalkerPortalClient.fetchChannels] already loads every
 * channel up front, so "deep" here just means "search the full list instead of the narrowed one",
 * not a network request. */
@Composable
private fun ChannelListPanel(
    displayedChannels: List<StalkerChannel>,
    allChannels: List<StalkerChannel>,
    currentChannelId: String?,
    onSelect: (StalkerChannel) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var searchMode by remember { mutableStateOf(IptvSearchMode.FILTER) }
    val baseList = if (searchMode == IptvSearchMode.DEEP) allChannels else displayedChannels
    val filtered = remember(baseList, query) { baseList.filter { matchesSearch(it.name, query) } }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(320.dp)
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(enabled = false) {},
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(top = 24.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Text("Channels", style = MDTheme.type.sectionTitle.copy(fontSize = MDTheme.type.settingTitle.fontSize), color = Color.White, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Close", tint = Color.White)
                }
            }
            Spacer(Modifier.height(8.dp))
            SearchBox(query = query, onQueryChange = { query = it }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
            SearchModeToggle(mode = searchMode, onSelect = { searchMode = it }, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
            if (filtered.isEmpty() && query.isNotBlank()) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (searchMode == IptvSearchMode.DEEP) "No channels match \"$query\"." else "No matches in this category - try Deep search.",
                        style = MDTheme.type.settingSubtitle,
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn {
                    items(filtered, key = { it.id }) { channel ->
                        val selected = channel.id == currentChannelId
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (selected) MDTheme.colors.accent.copy(alpha = 0.22f) else Color.Transparent)
                                .clickable { onSelect(channel) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Text(
                                channel.number,
                                style = MDTheme.type.settingSubtitle,
                                color = if (selected) MDTheme.colors.accent else Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.width(40.dp),
                            )
                            Text(
                                channel.name,
                                style = MDTheme.type.body,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class DownloadManagerTab { PENDING, CURRENT, HISTORY }

/** Full-screen, not a side panel like [ChannelListPanel] - each row carries enough metadata
 * (when, how long, how big, where it landed) that it needs the width. Three tabs:
 * [DownloadManagerTab.PENDING] is every not-yet-started [ScheduledRecording] (brief: scheduling
 * one from the Guide now shows it here too, not just as a marker in the Guide itself), soonest
 * first - these fire on their own via `AlarmManager`/`ScheduledRecordingReceiver`
 * (`IptvRecordingEngine.rearmPendingRecordings`/`startScheduled`), independent of whether the IPTV
 * tab or even this panel is open. [DownloadManagerTab.CURRENT] is the live progress of whatever
 * [activeRecording] is right now (a channel recording or a Movies/Series download - same engine,
 * see [IptvRecordingEngine]), [DownloadManagerTab.HISTORY] is the archived, completed ones, newest
 * first, matching how [MirrorDashSettings.iptvRecordingHistory] is already stored (see
 * [IptvRecordingEngine]'s `finally` block) - no separate sort needed here. Which tab opens first
 * is decided once, from whatever's most immediately actionable at the moment this panel is opened
 * (active, else pending, else history) - not re-decided afterward, so it doesn't yank the user
 * back out from under them just because something starts/finishes while they're looking elsewhere. */
@Composable
private fun DownloadManagerPanel(
    activeRecording: ActiveRecording?,
    pending: List<ScheduledRecording>,
    recordings: List<CompletedRecording>,
    onStop: () -> Unit,
    onCancelPending: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by remember {
        mutableStateOf(
            when {
                activeRecording != null -> DownloadManagerTab.CURRENT
                pending.isNotEmpty() -> DownloadManagerTab.PENDING
                else -> DownloadManagerTab.HISTORY
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(top = 24.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            ) {
                Text(
                    "Download Manager",
                    style = MDTheme.type.sectionTitle.copy(fontSize = MDTheme.type.settingTitle.fontSize),
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            ) {
                listOf(
                    DownloadManagerTab.PENDING to "Pending",
                    DownloadManagerTab.CURRENT to "Current",
                    DownloadManagerTab.HISTORY to "History",
                ).forEach { (value, label) ->
                    val selected = tab == value
                    Text(
                        label,
                        style = MDTheme.type.settingSubtitle,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MDTheme.colors.accent else Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.clickable { tab = value },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            when (tab) {
                DownloadManagerTab.PENDING -> if (pending.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Nothing scheduled", style = MDTheme.type.settingSubtitle, color = Color.White.copy(alpha = 0.6f))
                    }
                } else {
                    LazyColumn(modifier = Modifier.padding(horizontal = 24.dp)) {
                        items(pending.sortedBy { it.startEpochSeconds }, key = { it.id }) { scheduled ->
                            PendingRecordingRow(scheduled = scheduled, onCancel = { onCancelPending(scheduled.id) })
                        }
                    }
                }
                DownloadManagerTab.CURRENT -> CurrentDownloadContent(active = activeRecording, onStop = onStop)
                DownloadManagerTab.HISTORY -> if (recordings.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Nothing recorded yet", style = MDTheme.type.settingSubtitle, color = Color.White.copy(alpha = 0.6f))
                    }
                } else {
                    LazyColumn(modifier = Modifier.padding(horizontal = 24.dp)) {
                        items(recordings, key = { it.finishedAtEpochSeconds.toString() + it.path }) { recording ->
                            DownloadHistoryRow(recording)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingRecordingRow(scheduled: ScheduledRecording, onCancel: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(scheduled.programTitle, style = MDTheme.type.body, color = Color.White)
            Spacer(Modifier.height(2.dp))
            Text(
                "${scheduled.channelNumber}  ${scheduled.channelName}",
                style = MDTheme.type.caption,
                color = Color.White.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                formatScheduleWindow(scheduled.startEpochSeconds, scheduled.endEpochSeconds),
                style = MDTheme.type.caption,
                color = MDTheme.colors.accent,
            )
        }
        IconButton(onClick = onCancel) {
            Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = Color.White.copy(alpha = 0.7f))
        }
    }
}

/** e.g. "Aug 17, 8:00 PM – 9:00 PM" - the end time drops the date since a scheduled recording
 * crossing midnight is rare enough not to be worth the extra clutter on every other row. */
private fun formatScheduleWindow(startEpochSeconds: Long, endEpochSeconds: Long): String {
    val start = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault()).format(java.util.Date(startEpochSeconds * 1000))
    val end = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(endEpochSeconds * 1000))
    return "$start – $end"
}

/** Circular progress + percentage when [ActiveRecording.totalBytes] is known (a VOD download,
 * essentially always), an indeterminate spinner when it isn't (a live-channel recording, an
 * open-ended stream with no "percentage of what" to show) - both alongside the same
 * speed/downloaded-so-far/ETA readout, which degrades gracefully field by field as each piece
 * becomes computable rather than waiting for every one of them at once. */
@Composable
private fun CurrentDownloadContent(active: ActiveRecording?, onStop: () -> Unit) {
    if (active == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nothing downloading right now", style = MDTheme.type.settingSubtitle, color = Color.White.copy(alpha = 0.6f))
        }
        return
    }
    val total = active.totalBytes
    val fraction = total?.let { (active.bytesWritten.toFloat() / it).coerceIn(0f, 1f) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(12.dp))
        Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
            if (fraction != null) {
                CircularProgressIndicator(
                    progress = { fraction },
                    color = MDTheme.colors.accent,
                    trackColor = Color.White.copy(alpha = 0.15f),
                    strokeWidth = 6.dp,
                    modifier = Modifier.fillMaxSize(),
                )
                Text("${(fraction * 100).toInt()}%", style = MDTheme.type.settingTitle, color = Color.White)
            } else {
                CircularProgressIndicator(color = MDTheme.colors.accent, strokeWidth = 6.dp, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(active.channelName, style = MDTheme.type.settingTitle, color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            if (total != null) "${formatMegabytes(active.bytesWritten)} of ${formatMegabytes(total)}" else formatMegabytes(active.bytesWritten),
            style = MDTheme.type.settingSubtitle,
            color = Color.White.copy(alpha = 0.75f),
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(formatSpeed(active.bytesPerSecond), style = MDTheme.type.caption, color = Color.White.copy(alpha = 0.6f))
            if (total != null && active.bytesPerSecond > 0) {
                val remainingBytes = (total - active.bytesWritten).coerceAtLeast(0)
                Text("${formatEta(remainingBytes / active.bytesPerSecond)} remaining", style = MDTheme.type.caption, color = Color.White.copy(alpha = 0.6f))
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onStop,
            colors = ButtonDefaults.buttonColors(containerColor = RecordRed, contentColor = Color.White),
        ) {
            Text("Stop")
        }
    }
}

private fun formatSpeed(bytesPerSecond: Long): String = when {
    bytesPerSecond <= 0 -> "—"
    bytesPerSecond >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB/s", bytesPerSecond / (1024f * 1024f))
    else -> String.format(java.util.Locale.US, "%.0f KB/s", bytesPerSecond / 1024f)
}

private fun formatEta(remainingSeconds: Long): String {
    val hours = remainingSeconds / 3600
    val minutes = (remainingSeconds % 3600) / 60
    val seconds = remainingSeconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

@Composable
private fun DownloadHistoryRow(recording: CompletedRecording) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(recording.channelName, style = MDTheme.type.body, color = Color.White)
            if (recording.trigger == RecordingTrigger.DOWNLOAD) {
                Text(
                    "DOWNLOAD",
                    style = MDTheme.type.caption,
                    color = MDTheme.colors.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MDTheme.colors.accent.copy(alpha = 0.16f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                formatRecordingDate(recording.finishedAtEpochSeconds),
                style = MDTheme.type.caption,
                color = Color.White.copy(alpha = 0.6f),
            )
            Text(
                formatRecordingDuration(recording.startedAtEpochSeconds, recording.finishedAtEpochSeconds),
                style = MDTheme.type.caption,
                color = Color.White.copy(alpha = 0.6f),
            )
            Text(formatMegabytes(recording.bytesWritten), style = MDTheme.type.caption, color = Color.White.copy(alpha = 0.6f))
            Text(recording.destinationLabel, style = MDTheme.type.caption, color = Color.White.copy(alpha = 0.6f))
        }
        if (recording.stoppedForLowStorage) {
            Text(
                "Storage almost full, stopped early",
                style = MDTheme.type.caption,
                color = MDTheme.colors.danger,
            )
        }
        Text(
            recording.path,
            style = MDTheme.type.caption,
            color = Color.White.copy(alpha = 0.4f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatRecordingDate(epochSeconds: Long): String =
    java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(epochSeconds * 1000))

private fun formatRecordingDuration(startEpochSeconds: Long, endEpochSeconds: Long): String {
    val totalSeconds = (endEpochSeconds - startEpochSeconds).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MDTheme.colors.accent)
            Spacer(Modifier.height(16.dp))
            Text("Connecting to portal…", style = MDTheme.type.settingSubtitle, color = Color.White.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Tv, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text("Can't reach the portal", style = MDTheme.type.settingTitle, color = Color.White)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MDTheme.type.settingSubtitle,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 64.dp),
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = MDTheme.colors.accent, contentColor = MDTheme.colors.onAccent),
        ) {
            Text("Retry")
        }
    }
}

/** Shown instead of [ErrorState] when [StalkerPortalClient.blockMessage] is set - the handshake
 * itself succeeded (this isn't "can't reach the portal"), the account just can't stream right
 * now. [accountInfo] is best-effort (see [StalkerPortalClient.fetchAccountInfo]'s doc comment) -
 * this still renders with just the block reason if the portal didn't return anything else. */
@Composable
private fun AccountBlockedState(accountInfo: StalkerAccountInfo?, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = MDTheme.colors.danger, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text("Account blocked", style = MDTheme.type.settingTitle, color = Color.White)
        Spacer(Modifier.height(6.dp))
        Text(
            accountInfo?.blockMessage?.ifBlank { null } ?: "This account can't stream right now - check your subscription/credit with your provider.",
            style = MDTheme.type.settingSubtitle,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 64.dp),
        )
        accountInfo?.expiryEpochSeconds?.let { expiry ->
            Spacer(Modifier.height(10.dp))
            Text(
                "Expires ${formatAccountExpiry(expiry)}",
                style = MDTheme.type.settingSubtitle,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = MDTheme.colors.accent, contentColor = MDTheme.colors.onAccent),
        ) {
            Text("Retry")
        }
    }
}

@Composable
private fun CenteredMessage(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MDTheme.type.settingTitle, color = Color.White)
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            style = MDTheme.type.settingSubtitle,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 64.dp),
        )
    }
}
