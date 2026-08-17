package com.sconcept.mirrordash.iptv

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.sconcept.mirrordash.ui.theme.MDTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val CHANNEL_COLUMN_WIDTH = 152.dp
private val ROW_HEIGHT = 72.dp
private val PX_PER_MINUTE = 4.dp
private const val WINDOW_HOURS = 6
private const val WINDOW_MINUTES = WINDOW_HOURS * 60

/**
 * Plex-style Live TV guide: channels down the left (logo + number badge), a shared time axis
 * across the top, program blocks positioned by absolute start/end time. EPG per channel is
 * fetched lazily as its row scrolls into view - see [IptvViewModel.epgFor] - rather than for
 * every channel up front, since a portal's full channel list can run into the thousands.
 *
 * Vertical sync (a channel's logo vs. its own program row) is free - both live in the same
 * [LazyColumn] item, not two separately-scrolled lists. Horizontal sync (the time ruler vs. every
 * row) uses one shared [androidx.compose.foundation.ScrollState] applied to the ruler and to each
 * visible row's `horizontalScroll` - sharing a single `ScrollState` across several scrollable
 * modifiers is fine (unlike sharing one `LazyListState` across multiple `LazyColumn`s, which
 * isn't supported and breaks in practice).
 *
 * Every program cell also carries a record shortcut (brief: "the guide should ... have ui
 * shortcuts to record specific stuff") - tapping it schedules that program if it's upcoming, or
 * starts recording immediately (bounded to that program's own end time) if it's airing right now.
 * A cell already scheduled/recording shows a filled dot instead of an outline one; tapping again
 * cancels/stops it. [scheduledRecordings] and [activeRecording] are what tell a cell which state
 * it's in - both come from [IptvRecordingEngine], not from this screen's own state.
 */
@Composable
fun IptvGuideOverlay(
    channels: List<StalkerChannel>,
    currentChannelId: String?,
    onSelectChannel: (StalkerChannel) -> Unit,
    onDismiss: () -> Unit,
    loadEpg: suspend (StalkerChannel) -> List<EpgProgram>,
    genres: List<StalkerGenre>,
    selectedGenreId: String,
    onSelectGenre: (String) -> Unit,
    scheduledRecordings: List<ScheduledRecording>,
    activeRecording: ActiveRecording?,
    onScheduleProgram: (StalkerChannel, EpgProgram) -> Unit,
    onCancelScheduled: (String) -> Unit,
    onRecordLiveProgram: (StalkerChannel, EpgProgram) -> Unit,
    onStopRecording: () -> Unit,
    /** Non-null (and shown as a "regular size" button) only while the split preview is active -
     * see [IptvUiState.guideShowsPreview]. Null in the ordinary full-screen Guide, where there's
     * no preview to collapse back out of. */
    onCollapsePreview: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val hScroll = rememberScrollState()
    // Shared by the ruler and every row (not one per horizontalScroll call) so a fling started on
    // any of them decelerates with the exact same curve - see [smoothFlingBehavior]'s doc comment
    // for why the platform default isn't used as-is.
    val flingBehavior = smoothFlingBehavior()
    val windowStartSeconds = remember { flooredToHalfHourEpochSeconds() }
    val totalWidth = PX_PER_MINUTE * WINDOW_MINUTES

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f))
            // Swallows taps so the player/controls underneath never react while the guide is up.
            .clickable(onClick = {}, indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Text("Guide", style = MDTheme.type.sectionTitle, color = Color.White, modifier = Modifier.weight(1f))
                if (onCollapsePreview != null) {
                    IconButton(onClick = onCollapsePreview) {
                        Icon(Icons.Filled.FullscreenExit, contentDescription = "Regular size", tint = Color.White)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close guide", tint = Color.White)
                }
            }

            if (genres.size > 1) {
                GenreTabsRow(genres = genres, selectedGenreId = selectedGenreId, onSelect = onSelectGenre)
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(CHANNEL_COLUMN_WIDTH))
                // 44dp, not just tall enough for the text - a thin strip is a poor drag target
                // for a finger even though the visible ruler itself only needs a fraction of it.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(hScroll, flingBehavior = flingBehavior)
                        .height(44.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    TimeRuler(windowStartSeconds = windowStartSeconds, totalWidth = totalWidth)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(channels, key = { it.id }) { channel ->
                    var programs by remember(channel.id) { mutableStateOf<List<EpgProgram>>(emptyList()) }
                    LaunchedEffect(channel.id) { programs = loadEpg(channel) }

                    Column {
                        Row(modifier = Modifier.height(ROW_HEIGHT)) {
                            ChannelLogoCell(
                                channel = channel,
                                isSelected = channel.id == currentChannelId,
                                onClick = { onSelectChannel(channel) },
                                modifier = Modifier.width(CHANNEL_COLUMN_WIDTH),
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .horizontalScroll(hScroll, flingBehavior = flingBehavior)
                                    .clipToBounds(),
                            ) {
                                ChannelProgramRow(
                                    channel = channel,
                                    programs = programs,
                                    windowStartSeconds = windowStartSeconds,
                                    totalWidth = totalWidth,
                                    isCurrentChannel = channel.id == currentChannelId,
                                    scheduledRecordings = scheduledRecordings,
                                    activeRecording = activeRecording,
                                    onScheduleProgram = onScheduleProgram,
                                    onCancelScheduled = onCancelScheduled,
                                    onRecordLiveProgram = onRecordLiveProgram,
                                    onStopRecording = onStopRecording,
                                )
                            }
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
                    }
                }
            }
        }
    }
}

/** The row of category tabs (Plex's "Featured / Bingeworthy / Movies / ..."), sourced from the
 * portal's own `get_genres` list - every channel is tagged with one of these via `tv_genre_id`,
 * so picking a tab here is what [IptvUiState.displayedChannels] filters on. Its own independent
 * horizontal scroll, not tied to [hScroll] - these are filter chips, not part of the time axis. */
@Composable
private fun GenreTabsRow(genres: List<StalkerGenre>, selectedGenreId: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        genres.forEach { genre ->
            val selected = genre.id == selectedGenreId
            Text(
                genre.title,
                style = MDTheme.type.settingSubtitle,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MDTheme.colors.accent else Color.White.copy(alpha = 0.65f),
                maxLines = 1,
                modifier = Modifier.clickable { onSelect(genre.id) },
            )
        }
    }
}

@Composable
private fun TimeRuler(windowStartSeconds: Long, totalWidth: Dp) {
    val formatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    Box(modifier = Modifier.width(totalWidth).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
        for (halfHour in 0 until WINDOW_MINUTES / 30) {
            val minute = halfHour * 30
            Text(
                formatter.format(Date((windowStartSeconds + minute * 60L) * 1000L)),
                style = MDTheme.type.caption,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.offset(x = PX_PER_MINUTE * minute).padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun ChannelLogoCell(channel: StalkerChannel, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(if (isSelected) MDTheme.colors.accent.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        if (channel.logoUrl != null) {
            Image(
                painter = rememberAsyncImagePainter(model = channel.logoUrl),
                contentDescription = channel.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                channel.name,
                style = MDTheme.type.caption,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Text(
            channel.number,
            style = MDTheme.type.caption,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun ChannelProgramRow(
    channel: StalkerChannel,
    programs: List<EpgProgram>,
    windowStartSeconds: Long,
    totalWidth: Dp,
    isCurrentChannel: Boolean,
    scheduledRecordings: List<ScheduledRecording>,
    activeRecording: ActiveRecording?,
    onScheduleProgram: (StalkerChannel, EpgProgram) -> Unit,
    onCancelScheduled: (String) -> Unit,
    onRecordLiveProgram: (StalkerChannel, EpgProgram) -> Unit,
    onStopRecording: () -> Unit,
) {
    val windowEndSeconds = windowStartSeconds + WINDOW_MINUTES * 60L
    val nowSeconds = System.currentTimeMillis() / 1000L
    Box(modifier = Modifier.width(totalWidth).fillMaxHeight()) {
        programs.forEach { program ->
            if (program.endEpochSeconds <= windowStartSeconds || program.startEpochSeconds >= windowEndSeconds) return@forEach
            val startMinute = ((program.startEpochSeconds - windowStartSeconds) / 60f).coerceAtLeast(0f)
            val endMinute = ((program.endEpochSeconds - windowStartSeconds) / 60f).coerceAtMost(WINDOW_MINUTES.toFloat())
            val widthMinutes = (endMinute - startMinute).coerceAtLeast(2f)
            val isLive = program.startEpochSeconds <= nowSeconds && nowSeconds < program.endEpochSeconds
            val isPast = program.endEpochSeconds <= nowSeconds
            val isThisChannelRecording = activeRecording?.channelId == channel.id
            val scheduled = scheduledRecordings.firstOrNull {
                it.channelId == channel.id && it.startEpochSeconds == program.startEpochSeconds
            }

            Box(
                modifier = Modifier
                    .offset(x = PX_PER_MINUTE * startMinute)
                    .width(PX_PER_MINUTE * widthMinutes)
                    .fillMaxHeight()
                    .padding(2.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isCurrentChannel) MDTheme.colors.accent.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.09f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(
                    program.title,
                    style = MDTheme.type.settingSubtitle,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 20.dp),
                )
                if (!isPast) {
                    ProgramRecordShortcut(
                        isLive = isLive,
                        isRecording = isLive && isThisChannelRecording,
                        isScheduled = scheduled != null,
                        onClick = {
                            when {
                                isLive && isThisChannelRecording -> onStopRecording()
                                isLive -> onRecordLiveProgram(channel, program)
                                scheduled != null -> onCancelScheduled(scheduled.id)
                                else -> onScheduleProgram(channel, program)
                            }
                        },
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )
                }
            }
        }
    }
}

/** Outline dot = not recording/scheduled yet; filled red = it is (recording now, or queued for
 * later) - the same on/filled-vs-off/outline language used everywhere else a toggle needs to
 * read at a glance from across a room, just with record's own established color instead of the
 * app's neutral accent, since "red = recording" is a stronger, pre-existing convention worth
 * keeping rather than overriding for consistency's own sake. */
@Composable
private fun ProgramRecordShortcut(
    isLive: Boolean,
    isRecording: Boolean,
    isScheduled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = isRecording || isScheduled
    val tint = if (active) RecordRed else Color.White.copy(alpha = 0.55f)
    Box(
        modifier = modifier
            .padding(3.dp)
            .size(18.dp)
            .clip(CircleShape)
            .background(if (active) RecordRed.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.35f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (isRecording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
            contentDescription = when {
                isRecording -> "Stop recording"
                isScheduled -> "Cancel scheduled recording"
                isLive -> "Record now"
                else -> "Schedule recording"
            },
            tint = tint,
            modifier = Modifier.size(12.dp),
        )
    }
}

private val RecordRed = Color(0xFFE0433D)

private fun flooredToHalfHourEpochSeconds(): Long {
    val nowSeconds = System.currentTimeMillis() / 1000L
    return nowSeconds - (nowSeconds % 1800L)
}

/** A swipe/fling here needs to travel *hours* of a 4dp/minute axis, not a screen-width of content
 * the way most scrollables do - the platform default fling (tuned for that shorter, denser case)
 * runs out of momentum well before a real flick across the grid stops feeling like it under-shot.
 * Same decay-animation shape as the default (an exponential curve, not linear), just with lower
 * friction so a fast swipe carries further and settles more gradually - "accelerated" in the
 * everyday sense of "keeps going once you flick it," not literally accelerating. */
@Composable
private fun smoothFlingBehavior(): FlingBehavior {
    val decay = remember { exponentialDecay<Float>(frictionMultiplier = FLING_FRICTION_MULTIPLIER) }
    return remember(decay) { DecayFlingBehavior(decay) }
}

private const val FLING_FRICTION_MULTIPLIER = 0.35f

private class DecayFlingBehavior(private val decay: DecayAnimationSpec<Float>) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        var lastValue = 0f
        var finalVelocity = initialVelocity
        AnimationState(initialValue = 0f, initialVelocity = initialVelocity).animateDecay(decay) {
            val delta = value - lastValue
            val consumed = scrollBy(delta)
            lastValue = value
            finalVelocity = velocity
            if (abs(delta - consumed) > 0.5f) cancelAnimation()
        }
        return finalVelocity
    }
}
