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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.sconcept.mirrordash.launcher.AppContainer
import com.sconcept.mirrordash.ui.theme.MDTheme
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
    val scope = rememberCoroutineScope()

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
            uiState.pageState == IptvPageState.SLEEPING -> LoadingState()
            uiState.channels.isEmpty() -> CenteredMessage(
                title = "No channels",
                subtitle = "The portal connected but didn't return any live channels.",
            )
            else -> {
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
                    PlayerSurface(playerEpoch = uiState.playerEpoch, getPlayer = viewModel::currentPlayer)
                    if (uiState.isBuffering) {
                        CircularProgressIndicator(
                            color = MDTheme.colors.accent,
                            modifier = Modifier.align(Alignment.Center).size(40.dp),
                        )
                    }
                    PlaybackControls(
                        uiState = uiState,
                        recordingState = recordingState,
                        onPlayPause = viewModel::togglePlayPause,
                        onPrevious = viewModel::previousChannel,
                        onNext = viewModel::nextChannel,
                        onToggleChannelList = viewModel::toggleChannelList,
                        onToggleGuide = viewModel::toggleGuide,
                        onVolumeChange = viewModel::setVolume,
                        onToggleMute = viewModel::toggleMute,
                        onRecordNow = { channel -> recordingEngine.startManual(channel) },
                        onRecordTimed = { channel, minutes -> recordingEngine.startFixedDuration(channel, minutes) },
                        onOpenGuideToSchedule = viewModel::toggleGuide,
                        onStopRecording = { recordingEngine.stop() },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                    if (uiState.showChannelList) {
                        ChannelListPanel(
                            channels = uiState.displayedChannels,
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

        uiState.pendingPinChallenge?.let { _ ->
            ParentalPinDialog(
                pinError = uiState.pinError,
                onSubmit = viewModel::submitParentalPin,
                onDismiss = viewModel::dismissPinChallenge,
            )
        }
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

/** [playerEpoch] isn't read directly - passing it in is what makes this composable (and so its
 * `update` block below, which re-reads [getPlayer] and rebinds the view) recompose whenever the
 * ViewModel tears down/recreates the underlying player, e.g. across a sleep/wake cycle. */
@Composable
private fun PlayerSurface(playerEpoch: Int, getPlayer: () -> androidx.media3.exoplayer.ExoPlayer?) {
    androidx.compose.runtime.key(playerEpoch) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = getPlayer()
                }
            },
            update = { view -> view.player = getPlayer() },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun PlaybackControls(
    uiState: IptvUiState,
    recordingState: RecordingEngineUiState,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleChannelList: () -> Unit,
    onToggleGuide: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onRecordNow: (StalkerChannel) -> Unit,
    onRecordTimed: (StalkerChannel, Int) -> Unit,
    onOpenGuideToSchedule: () -> Unit,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text(
            uiState.currentChannel?.let { "${it.number}  ${it.name}" } ?: "",
            style = MDTheme.type.settingTitle,
            color = Color.White,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            RoundIconButton(icon = Icons.Filled.SkipPrevious, contentDescription = "Previous channel", onClick = onPrevious)
            RoundIconButton(
                icon = if (uiState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                onClick = onPlayPause,
            )
            RoundIconButton(icon = Icons.Filled.SkipNext, contentDescription = "Next channel", onClick = onNext)
            Spacer(Modifier.width(8.dp))
            RoundIconButton(icon = Icons.AutoMirrored.Filled.List, contentDescription = "Channel list", onClick = onToggleChannelList)
            RoundIconButton(icon = Icons.Filled.GridView, contentDescription = "Guide", onClick = onToggleGuide)
            RecordButton(
                currentChannel = uiState.currentChannel,
                recordingState = recordingState,
                onRecordNow = onRecordNow,
                onRecordTimed = onRecordTimed,
                onOpenGuideToSchedule = onOpenGuideToSchedule,
                onStopRecording = onStopRecording,
            )
            VolumeButton(volume = uiState.volume, onVolumeChange = onVolumeChange, onToggleMute = onToggleMute)
        }
    }
}

/**
 * Same expand-in-place shape as [VolumeButton] - the icon itself grows into the control, not a
 * popup elsewhere - but with three destinations instead of one continuous control, since "record"
 * isn't a single value to drag, it's a choice of *how* (brief: "when clicking record, the 3
 * options are offered"). Collapsed: a round icon, red when idle so it reads as "record" at a
 * glance (the one place here that breaks from the neutral white icon language on purpose - red
 * already means "record" everywhere else, overriding that for visual consistency would cost more
 * than it gains). Expanded while idle: three chips - Now / Timed / Schedule. While actively
 * recording: collapses that choice to the one that still applies, Stop, with a live elapsed/size
 * readout standing in for the percentage the volume control shows in the same slot.
 */
@Composable
private fun RecordButton(
    currentChannel: StalkerChannel?,
    recordingState: RecordingEngineUiState,
    onRecordNow: (StalkerChannel) -> Unit,
    onRecordTimed: (StalkerChannel, Int) -> Unit,
    onOpenGuideToSchedule: () -> Unit,
    onStopRecording: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var showDurationPicker by remember { mutableStateOf(false) }
    var showSaved by remember { mutableStateOf(false) }
    val active = recordingState.activeRecording

    LaunchedEffect(active) { if (active == null) { expanded = false; showDurationPicker = false } }

    // Confirms where a recording landed once it stops, in the same slot the live "recording..."
    // readout just occupied - reverting straight to a bare icon after Stop would leave the save
    // itself unconfirmed. Auto-collapses like the volume bar rather than needing a dismiss tap.
    LaunchedEffect(recordingState.lastCompleted) {
        if (recordingState.lastCompleted != null) {
            showSaved = true
            delay(SAVED_BADGE_DURATION_MS)
            showSaved = false
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
                if (active != null) {
                    onStopRecording()
                } else {
                    expanded = !expanded
                    showDurationPicker = false
                    showSaved = false
                }
            },
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                if (active != null) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                contentDescription = if (active != null) "Stop recording" else "Record",
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
        } else if (showSaved && recordingState.lastCompleted != null) {
            val completed = recordingState.lastCompleted
            val label = if (completed.stoppedForLowStorage) {
                "Storage almost full, stopped · ${completed.path}"
            } else {
                "Saved · ${completed.path}"
            }
            Text(
                label,
                style = MDTheme.type.caption,
                color = if (completed.stoppedForLowStorage) MDTheme.colors.danger else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp, end = 16.dp).widthIn(max = 240.dp),
            )
        } else if (expanded && currentChannel != null) {
            if (showDurationPicker) {
                listOf(30, 60, 90).forEach { minutes ->
                    RecordChip("${minutes}m") { onRecordTimed(currentChannel, minutes); expanded = false }
                }
            } else {
                RecordChip("Now") { onRecordNow(currentChannel); expanded = false }
                RecordChip("Timed") { showDurationPicker = true }
                RecordChip("Schedule") { expanded = false; onOpenGuideToSchedule() }
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
private const val SAVED_BADGE_DURATION_MS = 5000L

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
private fun RoundIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f)),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White)
    }
}

@Composable
private fun ChannelListPanel(
    channels: List<StalkerChannel>,
    currentChannelId: String?,
    onSelect: (StalkerChannel) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            LazyColumn {
                items(channels, key = { it.id }) { channel ->
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
