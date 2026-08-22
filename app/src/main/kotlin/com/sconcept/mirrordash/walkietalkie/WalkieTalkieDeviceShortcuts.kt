package com.sconcept.mirrordash.walkietalkie

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sconcept.mirrordash.ui.theme.MDTheme
import com.sconcept.mirrordash.walkietalkie.model.WalkieTalkiePeer

/** A single projection of the configured peer registry for the notification shade and home. */
@Composable
fun WalkieTalkieDeviceBar(
    peers: List<WalkieTalkiePeer>,
    discoveredIps: Set<String>,
    shortcutIps: Set<String>,
    activeIncomingIp: String?,
    activeTransmitIp: String?,
    talkEnabled: Boolean,
    onShortcutChange: (WalkieTalkiePeer, Boolean) -> Unit,
    onTalkStart: (WalkieTalkiePeer) -> Unit,
    onTalkEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (peers.isEmpty()) return
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        items(peers, key = { it.ip }) { peer ->
            val online = peer.ip in discoveredIps
            val receiving = activeIncomingIp == peer.ip
            val transmitting = activeTransmitIp == peer.ip
            DeviceTile(
                peer = peer,
                online = online,
                receiving = receiving,
                transmitting = transmitting,
                selected = peer.ip in shortcutIps,
                talkEnabled = talkEnabled && online,
                onSelectedChange = { onShortcutChange(peer, it) },
                onTalkStart = { onTalkStart(peer) },
                onTalkEnd = onTalkEnd,
            )
        }
    }
}

@Composable
private fun DeviceTile(
    peer: WalkieTalkiePeer,
    online: Boolean,
    receiving: Boolean,
    transmitting: Boolean,
    selected: Boolean,
    talkEnabled: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onTalkStart: () -> Unit,
    onTalkEnd: () -> Unit,
) {
    val activationLabel = if (selected) "Active" else "Inactive"
    val status = when {
        transmitting -> "transmitting"
        receiving -> "receiving audio"
        online -> "online"
        else -> "offline"
    }
    val statusColor = when {
        transmitting || receiving -> MDTheme.colors.accent
        online -> Color(0xFF4CAF78)
        else -> MDTheme.colors.textTertiary
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MDTheme.colors.surfaceElevated else MDTheme.colors.surface)
            .border(
                width = 1.dp,
                color = if (selected) MDTheme.colors.accent.copy(alpha = .72f) else MDTheme.colors.textTertiary.copy(alpha = .28f),
                shape = RoundedCornerShape(14.dp),
            )
            .clickable { onSelectedChange(!selected) }
            .padding(horizontal = 10.dp, vertical = 12.dp)
            .semantics {
                contentDescription = "${peer.name}, $activationLabel, $status, IP address ${peer.ip}. Tap card to ${if (selected) "deactivate" else "activate"}."
            },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(statusColor.copy(alpha = if (online || receiving || transmitting) .92f else .45f))
                .alpha(if (online || receiving || transmitting) 1f else .65f),
        ) {
            Text(peer.initials(), style = MDTheme.type.settingTitle, color = MDTheme.colors.onAccent)
        }
        Spacer(Modifier.height(8.dp))
        Text(peer.name, style = MDTheme.type.caption, color = MDTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(peer.ip, style = MDTheme.type.caption, color = MDTheme.colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "$activationLabel · $status",
            style = MDTheme.type.caption,
            color = if (selected) MDTheme.colors.accent else statusColor,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        PttButton(
            isTransmitting = transmitting,
            isSpeaking = receiving,
            enabled = talkEnabled,
            onPressStart = onTalkStart,
            onPressEnd = onTalkEnd,
            buttonSize = 42.dp,
            modifier = Modifier.semantics { contentDescription = if (talkEnabled) "Hold to talk to ${peer.name}" else "${peer.name} is offline" },
        )
    }
}

/**
 * The persistent, right-edge replacement for the old broadcast-to-everyone PTT button. It shows
 * only user-selected rooms; holding an initial starts the existing peer-specific PTT session.
 */
@Composable
fun WalkieTalkieRoomShortcutStack(
    peers: List<WalkieTalkiePeer>,
    discoveredIps: Set<String>,
    activeIncomingIp: String?,
    activeTransmitIp: String?,
    talkEnabled: Boolean,
    showTalkToAll: Boolean,
    iconSizePx: Int,
    isBroadcasting: Boolean,
    onTalkStart: (WalkieTalkiePeer) -> Unit,
    onTalkEnd: () -> Unit,
    onTalkToAllStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (peers.isEmpty()) return
    val iconSize = with(LocalDensity.current) { iconSizePx.coerceIn(8, 200).toDp() }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier) {
        items(peers, key = { it.ip }) { peer ->
            val online = peer.ip in discoveredIps
            val receiving = activeIncomingIp == peer.ip
            val transmitting = activeTransmitIp == peer.ip
            val status = if (receiving) "receiving" else if (transmitting) "transmitting" else if (online) "online" else "offline"
            val avatarColor = if (receiving || transmitting) MDTheme.colors.accent else if (online) Color(0xFF4CAF78) else MDTheme.colors.textTertiary
            TalkingInitialCircle(
                label = peer.initials(),
                labelDescription = "${peer.name}, $status. Press and hold to talk.",
                backgroundColor = avatarColor,
                active = receiving || transmitting,
                enabled = talkEnabled && online,
                onPressStart = { onTalkStart(peer) },
                onPressEnd = onTalkEnd,
                iconSize = iconSize,
                modifier = Modifier.alpha(if (online || receiving || transmitting) 1f else .58f),
            )
        }
        if (showTalkToAll) {
            item(key = "talk-to-all") {
                TalkingInitialCircle(
                    label = "ALL",
                    labelDescription = "Talk to all rooms. Press and hold to broadcast.",
                    backgroundColor = MDTheme.colors.accent,
                    active = isBroadcasting,
                    enabled = talkEnabled,
                    onPressStart = onTalkToAllStart,
                    onPressEnd = onTalkEnd,
                    iconSize = iconSize,
                )
            }
        }
    }
}

/**
 * The one active-state motion on the home surface: expanding sound waves explain precisely
 * which room is transmitting or receiving. The small white edge and translucent fill preserve
 * legibility over the mirror's dynamic backgrounds without introducing a tile behind the button.
 */
@Composable
private fun TalkingInitialCircle(
    label: String,
    labelDescription: String,
    backgroundColor: Color,
    active: Boolean,
    enabled: Boolean,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    iconSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val pulse by animateFloatAsState(
        targetValue = if (active) 1.06f else 1f,
        animationSpec = tween(160, easing = FastOutSlowInEasing),
        label = "roomTalkScale",
    )
    val transition = rememberInfiniteTransition(label = "roomTalkRipple")
    val wavePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), repeatMode = androidx.compose.animation.core.RepeatMode.Restart),
        label = "roomTalkWave",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(iconSize)
            .semantics { contentDescription = labelDescription },
    ) {
        Canvas(Modifier.matchParentSize()) {
            if (active) {
                val radius = size.minDimension * (0.34f + wavePhase * 0.16f)
                drawCircle(
                    color = backgroundColor.copy(alpha = (1f - wavePhase) * 0.46f),
                    radius = radius,
                    style = Stroke(width = size.minDimension * 0.027f, cap = StrokeCap.Round),
                )
                drawCircle(
                    color = Color.White.copy(alpha = (1f - wavePhase) * 0.22f),
                    radius = radius + size.minDimension * 0.09f,
                    style = Stroke(width = size.minDimension * 0.018f),
                )
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(iconSize * (64f / 88f))
                .graphicsLayer { scaleX = pulse; scaleY = pulse }
                .clip(CircleShape)
                .background(backgroundColor.copy(alpha = if (active) .88f else .68f))
                .border(1.dp, Color.White.copy(alpha = .82f), CircleShape)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            onPressStart()
                            tryAwaitRelease()
                            onPressEnd()
                        },
                    )
                },
        ) {
            Text(label, style = MDTheme.type.settingTitle, color = MDTheme.colors.onAccent, textAlign = TextAlign.Center)
        }
    }
}
