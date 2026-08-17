package com.sconcept.mirrordash.wedding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sconcept.mirrordash.launcher.gestures.LauncherGestureHost
import com.sconcept.mirrordash.photobooth.PhotoboothScreen
import com.sconcept.mirrordash.photobooth.PhotoboothViewModel
import com.sconcept.mirrordash.settings.SettingsViewModel
import com.sconcept.mirrordash.ui.theme.MDTheme
import com.sconcept.mirrordash.wedding.seating.WeddingSeatingScreen
import kotlinx.coroutines.delay

private enum class WeddingPage(val label: String, val icon: ImageVector) {
    WELCOME("Welcome", Icons.Filled.FavoriteBorder),
    SEATING("Find Your Seat", Icons.Filled.EventSeat),
    MEMORIES("Memories", Icons.Filled.AutoStories),
    SLIDESHOW("Slideshow", Icons.Filled.PhotoLibrary),
    PHOTOBOOTH("Photobooth", Icons.Filled.PhotoCamera),
}

internal val WeddingBackground = Color(0xFF17110F)
internal val WeddingSurface = Color(0xFF2B201C)
internal val WeddingIvory = Color(0xFFF7EFE3)
internal val WeddingMuted = Color(0xFFCDBFB2)
internal val WeddingRose = Color(0xFFD8A59A)

/** Locked guest shell: the existing pager remains, while normal launcher sheets stay disabled. */
@Composable
fun WeddingModeExperience(
    settingsViewModel: SettingsViewModel,
    photoboothViewModel: PhotoboothViewModel,
    weddingViewModel: WeddingViewModel,
    onFailsafeHoldTriggered: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val pages = remember(settingsState.settings.photoboothEnabled) {
        WeddingPage.entries.filter { it != WeddingPage.PHOTOBOOTH || settingsState.settings.photoboothEnabled }
    }
    val profile = settingsState.settings.weddingProfile
    var showExitDialog by remember { mutableStateOf(false) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var interactionSequence by remember { mutableIntStateOf(0) }
    var requestedPage by remember { mutableStateOf<Int?>(null) }

    DisposableEffect(weddingViewModel) {
        weddingViewModel.clearGuestSession()
        onDispose { weddingViewModel.clearGuestSession() }
    }

    LaunchedEffect(currentPageIndex, interactionSequence, profile.idleTimeoutSeconds) {
        if (currentPageIndex == 0) return@LaunchedEffect
        delay(profile.idleTimeoutSeconds.coerceIn(30, 900) * 1_000L)
        weddingViewModel.clearGuestSession()
        requestedPage = 0
    }

    BackHandler { showExitDialog = true }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(WeddingBackground),
    ) {
        LauncherGestureHost(
            pageCount = pages.size,
            initialPage = 0,
            pageLabels = pages.map(WeddingPage::label),
            pageIcons = pages.map(WeddingPage::icon),
            onPageSettled = { index ->
                currentPageIndex = index
                if (pages.getOrNull(index) != WeddingPage.SEATING) {
                    weddingViewModel.clearGuestSession()
                }
                if (index == 0) requestedPage = null
            },
            pageContent = { index, _ ->
                when (pages.getOrElse(index) { WeddingPage.WELCOME }) {
                    WeddingPage.WELCOME -> WeddingWelcomePage(profile)
                    WeddingPage.SEATING -> WeddingSeatingScreen(
                        viewModel = weddingViewModel,
                        isActive = currentPageIndex == index,
                        onInteraction = { interactionSequence++ },
                    )
                    WeddingPage.MEMORIES -> WeddingFeaturePage(
                        icon = WeddingPage.MEMORIES.icon,
                        title = "Leave a Memory",
                        subtitle = "Share a note, photo, or message for the couple.",
                        status = "Memory capture is ready for the next Wedding Mode stage.",
                    )
                    WeddingPage.SLIDESHOW -> WeddingFeaturePage(
                        icon = WeddingPage.SLIDESHOW.icon,
                        title = "Our Story",
                        subtitle = "A quiet, full-screen wedding slideshow.",
                        status = "Wedding photos will use their own slideshow configuration.",
                    )
                    WeddingPage.PHOTOBOOTH -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 82.dp),
                    ) {
                        PhotoboothScreen(viewModel = photoboothViewModel)
                    }
                }
            },
            appDrawerContent = { _, _ -> },
            notificationsContent = { _, _ -> },
            nightClockContent = {},
            utilitySheetsEnabled = false,
            nightClockEnabled = false,
            travelModeEnabled = false,
            pageBarAlwaysVisible = true,
            pageBarBackgroundColor = WeddingSurface,
            pageBarSelectedColor = WeddingRose,
            pageBarUnselectedColor = WeddingMuted,
            onFailsafeHoldTriggered = onFailsafeHoldTriggered,
            onUserInteraction = { interactionSequence++ },
            requestedPage = requestedPage,
        )

        HostAccessButton(
            showLabel = maxWidth >= 720.dp,
            onClick = { showExitDialog = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 20.dp, end = 20.dp),
        )
    }

    if (showExitDialog) {
        ExitWeddingModeDialog(
            onDismiss = { showExitDialog = false },
            onSubmit = settingsViewModel::exitWeddingMode,
        )
    }
}

@Composable
private fun WeddingWelcomePage(profile: WeddingProfile) {
    val coupleName = profile.coupleDisplayName
    val welcomeMessage = profile.welcomeMessage.trim().ifEmpty { "Welcome to our wedding" }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF30221D), WeddingBackground, Color(0xFF0D0B0A)),
                ),
            )
            .padding(horizontal = 48.dp, vertical = 96.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Filled.FavoriteBorder,
                contentDescription = null,
                tint = WeddingRose,
                modifier = Modifier.size(42.dp),
            )
            Spacer(Modifier.height(28.dp))
            if (coupleName.isNotEmpty()) {
                Text(
                    welcomeMessage,
                    color = WeddingMuted,
                    fontSize = 19.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
            }
            Text(
                coupleName.ifEmpty { welcomeMessage },
                color = WeddingIvory,
                fontSize = 52.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
                lineHeight = 60.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(18.dp))
            listOf(profile.dateText.trim(), profile.location.trim())
                .filter(String::isNotEmpty)
                .joinToString("  |  ")
                .takeIf(String::isNotEmpty)
                ?.let { details ->
                    Text(
                        details,
                        color = WeddingMuted,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(24.dp))
                }
            Text(
                "Swipe left or right to explore",
                color = WeddingMuted,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun WeddingFeaturePage(
    icon: ImageVector,
    title: String,
    subtitle: String,
    status: String,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WeddingBackground)
            .padding(horizontal = 56.dp, vertical = 96.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(0.82f),
        ) {
            Icon(icon, contentDescription = null, tint = WeddingRose, modifier = Modifier.size(46.dp))
            Spacer(Modifier.height(24.dp))
            Text(
                title,
                color = WeddingIvory,
                fontSize = 42.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                subtitle,
                color = WeddingMuted,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp,
            )
            Spacer(Modifier.height(34.dp))
            Text(
                status,
                color = WeddingMuted.copy(alpha = 0.78f),
                style = MDTheme.type.body,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(WeddingSurface)
                    .padding(horizontal = 22.dp, vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun HostAccessButton(
    showLabel: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(WeddingSurface.copy(alpha = 0.92f))
            .clickable(onClick = onClick)
            .padding(horizontal = if (showLabel) 14.dp else 12.dp, vertical = 10.dp),
    ) {
        Icon(Icons.Filled.Lock, contentDescription = "Host access", tint = WeddingMuted, modifier = Modifier.size(20.dp))
        if (showLabel) {
            Text("Host", color = WeddingMuted, style = MDTheme.type.caption)
        }
    }
}

@Composable
private fun ExitWeddingModeDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> WeddingPinResult,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun submit() {
        error = when (onSubmit(pin)) {
            WeddingPinResult.SUCCESS -> {
                onDismiss()
                null
            }
            WeddingPinResult.INVALID_FORMAT -> "Use ${WeddingPinPolicy.MIN_LENGTH}-${WeddingPinPolicy.MAX_LENGTH} digits."
            WeddingPinResult.INCORRECT -> "That PIN is incorrect. Wedding Mode is still active."
            WeddingPinResult.STORAGE_ERROR -> "The PIN could not be checked on this device. Try again."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exit Wedding Mode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Enter the same host PIN used to enter Wedding Mode.")
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        pin = WeddingPinPolicy.sanitize(it)
                        error = null
                    },
                    label = { Text("Host PIN") },
                    singleLine = true,
                    isError = error != null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                error?.let { Text(it, color = MDTheme.colors.danger, style = MDTheme.type.caption) }
            }
        },
        confirmButton = { Button(onClick = ::submit) { Text("Exit Wedding Mode") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Stay in Wedding Mode") } },
    )
}
