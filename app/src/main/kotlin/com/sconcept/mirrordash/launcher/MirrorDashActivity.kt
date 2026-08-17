package com.sconcept.mirrordash.launcher

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sconcept.mirrordash.airplay.AirPlayReceiverService
import com.sconcept.mirrordash.browser.BrowserScreen
import com.sconcept.mirrordash.brightness.BRIGHTNESS_FAILSAFE_HOLD_MS
import com.sconcept.mirrordash.brightness.BRIGHTNESS_FAILSAFE_WARNING_LEAD_MS
import com.sconcept.mirrordash.brightness.BacklightController
import com.sconcept.mirrordash.brightness.BrightnessDimOverlay
import com.sconcept.mirrordash.brightness.BrightnessFailsafe
import com.sconcept.mirrordash.brightness.BrightnessMath
import com.sconcept.mirrordash.clock.ClockBackground
import com.sconcept.mirrordash.clock.ClockScreen
import com.sconcept.mirrordash.clock.ClockViewModel
import com.sconcept.mirrordash.clock.NightClockScreen
import com.sconcept.mirrordash.clock.OverlayAnchor
import com.sconcept.mirrordash.homeassistant.HomeAssistantScreen
import com.sconcept.mirrordash.iptv.IptvScreen
import com.sconcept.mirrordash.iptv.IptvViewModel
import com.sconcept.mirrordash.jellyfin.JellyfinScreen
import com.sconcept.mirrordash.launcher.apps.AppDrawerScreen
import com.sconcept.mirrordash.launcher.display.DisplayOrientationController
import com.sconcept.mirrordash.launcher.display.DisplayOrientationMode
import com.sconcept.mirrordash.launcher.apps.AppDrawerViewModel
import com.sconcept.mirrordash.launcher.gestures.LauncherGestureHost
import com.sconcept.mirrordash.launcher.gestures.LauncherInteractionState
import com.sconcept.mirrordash.launcher.navigation.LauncherPage
import com.sconcept.mirrordash.launcher.navigation.LauncherPages
import com.sconcept.mirrordash.launcher.notifications.NotificationRepository
import com.sconcept.mirrordash.launcher.notifications.NotificationsScreen
import com.sconcept.mirrordash.launcher.onboarding.OnboardingOverlay
import com.sconcept.mirrordash.photorama.PhotoramaScreen
import com.sconcept.mirrordash.photorama.PhotoramaViewModel
import com.sconcept.mirrordash.settings.BRIGHTNESS_DIM_TARGET_TEXT_ONLY
import com.sconcept.mirrordash.settings.ui.SettingsScreen
import com.sconcept.mirrordash.settings.SettingsViewModel
import com.sconcept.mirrordash.ui.theme.MDTheme
import com.sconcept.mirrordash.ui.theme.MirrorDashTheme
import com.sconcept.mirrordash.walkietalkie.PttButton
import com.sconcept.mirrordash.weather.WeatherViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MirrorDashActivity : ComponentActivity() {

    private val container by lazy { AppContainer.get(applicationContext) }

    private val launcherViewModel: LauncherViewModel by viewModels { LauncherViewModel.factory(container.settingsRepository) }
    private val clockViewModel: ClockViewModel by viewModels { ClockViewModel.factory(container.settingsRepository) }
    private val weatherViewModel: WeatherViewModel by viewModels { WeatherViewModel.factory(container.settingsRepository) }
    private val photoramaViewModel: PhotoramaViewModel by viewModels {
        PhotoramaViewModel.factory(application, container.settingsRepository)
    }
    private val appDrawerViewModel: AppDrawerViewModel by viewModels { AppDrawerViewModel.factory(application) }
    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModel.factory(application, container.settingsRepository)
    }
    private val iptvViewModel: IptvViewModel by viewModels {
        IptvViewModel.factory(application, container.settingsRepository, container.iptvSessionCoordinator)
    }
    // Bridges the Compose-side "is the hidden Night Clock tab currently showing" signal (from
    // LauncherGestureHost's onNightClockActiveChanged) into the plain lifecycleScope brightness
    // collectors below, which run outside Compose and read settingsRepository.settings directly.
    private val isNightClockActive = MutableStateFlow(false)

    private val requestMicPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val requestNotificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val requestHomeRole = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        if (androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        photoramaViewModel.ensureStarted()

        lifecycleScope.launch {
            if (!container.settingsRepository.settings.first().provisioningAppliedOnce) {
                settingsViewModel.autoApplyProvisioningConfigIfPresent()
            }
        }

        // Safety net alongside ScheduledRecordingReceiver's own BOOT_COMPLETED handling - covers
        // the case where the app was installed/updated after the last boot (which never fires
        // BOOT_COMPLETED for a freshly-installed receiver) leaving armed alarms that were never
        // actually set. Idempotent: re-arming an already-armed alarm is a harmless no-op.
        lifecycleScope.launch {
            container.iptvRecordingEngine.rearmPendingRecordings()
        }

        lifecycleScope.launch {
            container.settingsRepository.settings
                .map { DisplayOrientationMode.fromStorageKey(it.displayOrientationMode) }
                .distinctUntilChanged()
                .collect { mode -> DisplayOrientationController.apply(this@MirrorDashActivity, mode) }
        }

        lifecycleScope.launch {
            container.settingsRepository.settings
                .map { it.airplayEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    if (enabled) {
                        AirPlayReceiverService.start(this@MirrorDashActivity)
                    } else {
                        AirPlayReceiverService.stop(this@MirrorDashActivity)
                    }
                }
        }

        // Split in two: the window-brightness layer is a cheap in-process attribute set, so it
        // tracks every emission live as the slider drags. The root layer shells out (several
        // su spawns per candidate path/syntax), which is slow enough that applying it on every
        // intermediate value during a drag falls badly behind - debounced so only the value the
        // user actually settles on gets written there. Both switch to the Night Clock tab's own,
        // separately-controlled level while that hidden tab is showing (see isNightClockActive).
        val effectiveBrightnessLevel255 = combine(container.settingsRepository.settings, isNightClockActive) { settings, nightActive ->
            if (nightActive) settings.nightClockBrightnessLevel255 else settings.brightnessLevel255
        }.distinctUntilChanged()

        lifecycleScope.launch {
            effectiveBrightnessLevel255
                .collect { level -> BacklightController.applyWindowBrightness(this@MirrorDashActivity, level) }
        }

        lifecycleScope.launch {
            effectiveBrightnessLevel255
                .debounce(250)
                .collect { level -> BacklightController.applyRootLayer(this@MirrorDashActivity, level) }
        }

        setContent {
            MirrorDashTheme {
                MirrorDashRoot(
                    launcherViewModel = launcherViewModel,
                    clockViewModel = clockViewModel,
                    weatherViewModel = weatherViewModel,
                    photoramaViewModel = photoramaViewModel,
                    appDrawerViewModel = appDrawerViewModel,
                    settingsViewModel = settingsViewModel,
                    iptvViewModel = iptvViewModel,
                    onRequestHomeRole = { requestHomeRole.launch(HomeRoleHelper.requestHomeRoleIntent(this)) },
                    onRequestNotificationAccess = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    onRequestWriteSettingsAccess = {
                        startActivity(
                            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")),
                        )
                    },
                    onRequestOverlayAccess = {
                        startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                        )
                    },
                    onRequestBrightnessFailsafe = {
                        BrightnessFailsafe.trigger(this@MirrorDashActivity, lifecycleScope, container.settingsRepository)
                    },
                    onNightClockActiveChanged = { active -> isNightClockActive.value = active },
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private var volumeFailsafeJob: Job? = null

    // Never consumes the event (falls through to super regardless) so volume still ramps
    // normally as a side effect of holding it - the failsafe is a bonus, not a hijack. Unlike
    // BerthierOptions, no AccessibilityService/root `getevent` watcher is needed for this: as the
    // Home app, MirrorDashActivity reliably sees every volume key press on its own already.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0 && volumeFailsafeJob == null) {
                        volumeFailsafeJob = lifecycleScope.launch {
                            delay(BRIGHTNESS_FAILSAFE_WARNING_LEAD_MS)
                            BrightnessFailsafe.warn(this@MirrorDashActivity)
                            delay(BRIGHTNESS_FAILSAFE_HOLD_MS - BRIGHTNESS_FAILSAFE_WARNING_LEAD_MS)
                            BrightnessFailsafe.trigger(this@MirrorDashActivity, lifecycleScope, container.settingsRepository)
                        }
                    }
                }
                KeyEvent.ACTION_UP -> {
                    volumeFailsafeJob?.cancel()
                    volumeFailsafeJob = null
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

}

@Composable
private fun MirrorDashRoot(
    launcherViewModel: LauncherViewModel,
    clockViewModel: ClockViewModel,
    weatherViewModel: WeatherViewModel,
    photoramaViewModel: PhotoramaViewModel,
    appDrawerViewModel: AppDrawerViewModel,
    settingsViewModel: SettingsViewModel,
    iptvViewModel: IptvViewModel,
    onRequestHomeRole: () -> Unit,
    onRequestNotificationAccess: () -> Unit,
    onRequestWriteSettingsAccess: () -> Unit,
    onRequestOverlayAccess: () -> Unit,
    onRequestBrightnessFailsafe: () -> Unit,
    onNightClockActiveChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val initialPageIndex by launcherViewModel.initialPageIndex.collectAsStateWithLifecycle()
    val clockAppearance by clockViewModel.appearance.collectAsStateWithLifecycle()
    val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    if (!settingsUiState.settingsLoaded) return

    var isDefaultLauncher by remember { mutableStateOf(HomeRoleHelper.isDefaultLauncher(context)) }
    var showOnboarding by remember { mutableStateOf(!isDefaultLauncher) }

    LaunchedEffect(Unit) {
        isDefaultLauncher = HomeRoleHelper.isDefaultLauncher(context)
    }

    val pageIndex = initialPageIndex ?: return
    // Photorama drops out while doubling as the Clock's own background (a standalone page
    // repeating the exact photos already showing behind the clock would just be a redundant
    // swipe); Browser/Jellyfin/Home Assistant/IPTV are opt-in entirely - "each tab can be
    // enabled or disabled in the settings, but the clock and settings must always remain" (see
    // LauncherPages' doc comment).
    val orderedPages = remember(
        clockAppearance.background,
        settingsUiState.settings.browserEnabled,
        settingsUiState.settings.jellyfinEnabled,
        settingsUiState.settings.homeAssistantEnabled,
        settingsUiState.settings.iptvEnabled,
    ) {
        LauncherPages.ordered(
            includePhotoramaPage = clockAppearance.background !is ClockBackground.Photorama,
            includeBrowserPage = settingsUiState.settings.browserEnabled,
            includeJellyfinPage = settingsUiState.settings.jellyfinEnabled,
            includeHomeAssistantPage = settingsUiState.settings.homeAssistantEnabled,
            includeIptvPage = settingsUiState.settings.iptvEnabled,
        )
    }
    var currentPageIndex by remember { mutableStateOf(pageIndex) }

    // An AirPlay connection should be visible regardless of which tab the user happens to be on
    // when it comes in - not just when they're already sitting on the Clock page, where the
    // mirror surface/status widget actually live (see ClockScreen). requestedPage flips back to
    // null once the session ends so the next session's rising edge is a fresh, distinct value the
    // pager will act on again (see LauncherGestureHost's requestedPage doc).
    val airPlayEngine = remember { AppContainer.get(context).airPlayEngine }
    val airPlayUiState by airPlayEngine.uiState.collectAsStateWithLifecycle()
    val clockPageIndex = remember(orderedPages) { orderedPages.indexOf(LauncherPage.Clock).takeIf { it >= 0 } }
    var requestedPage by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(airPlayUiState.isSessionActive) {
        requestedPage = clockPageIndex.takeIf { airPlayUiState.isSessionActive }
    }

    // The IPTV tab's whole sleep/off lifecycle (brief: "sleeping state when not active" / "shut
    // off completely ... when not in view") hangs off this one signal - see IptvViewModel's doc
    // comment. Driven from here rather than the page's own composable lifecycle because
    // HorizontalPager disposes off-screen pages almost immediately, which would lose track of
    // "still within the sleep timeout" the moment the user swipes away.
    val isIptvPageActive = orderedPages.getOrNull(currentPageIndex) == LauncherPage.Iptv
    LaunchedEffect(isIptvPageActive) {
        iptvViewModel.setActive(isIptvPageActive)
    }

    val isClockPageActive = orderedPages.getOrNull(currentPageIndex) == LauncherPage.Clock
    val textOnlyDim = settingsUiState.settings.brightnessDimTarget == BRIGHTNESS_DIM_TARGET_TEXT_ONLY && isClockPageActive
    val effectiveDimAlpha = remember(settingsUiState.settings.brightnessLevel255, settingsUiState.settings.brightnessExtraDimPercent) {
        maxOf(
            BrightnessMath.backlightValueToOverlayAlpha(settingsUiState.settings.brightnessLevel255),
            BrightnessMath.extraDimPercentToAlpha(settingsUiState.settings.brightnessExtraDimPercent),
        ) / 255f
    }

    // Local mirror of the same "is Night Clock showing" signal MirrorDashActivity threads into
    // the backlight/root-layer collectors (see onNightClockActiveChanged below) - needed here too
    // so BrightnessDimOverlay, the compensating scrim for layer 1's limited backlight floor, can
    // switch to Night Clock's own dimmer target instead of always reading the main Clock's.
    var isNightClockActive by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MDTheme.colors.background),
    ) {
        LauncherGestureHost(
            pageCount = orderedPages.size,
            initialPage = pageIndex.coerceIn(0, orderedPages.lastIndex),
            pageLabels = orderedPages.map { it.label },
            onPageSettled = { index ->
                launcherViewModel.onPageSettled(index)
                currentPageIndex = index
            },
            pageContent = { index, interactionState ->
                LauncherPageContent(
                    page = orderedPages.getOrElse(index) { LauncherPage.Clock },
                    isJellyfinPageActive = orderedPages.getOrNull(currentPageIndex) == LauncherPage.Jellyfin &&
                        orderedPages.getOrElse(index) { LauncherPage.Clock } == LauncherPage.Jellyfin,
                    isAwake = interactionState == LauncherInteractionState.Travel,
                    clockViewModel = clockViewModel,
                    weatherViewModel = weatherViewModel,
                    photoramaViewModel = photoramaViewModel,
                    settingsViewModel = settingsViewModel,
                    iptvViewModel = iptvViewModel,
                    onRequestHomeRole = onRequestHomeRole,
                    onRequestNotificationAccess = onRequestNotificationAccess,
                    onRequestWriteSettingsAccess = onRequestWriteSettingsAccess,
                    onRequestOverlayAccess = onRequestOverlayAccess,
                    clockContentDimAlpha = if (textOnlyDim) effectiveDimAlpha else 0f,
                )
            },
            onFailsafeHoldTriggered = onRequestBrightnessFailsafe,
            onNightClockActiveChanged = { active ->
                isNightClockActive = active
                onNightClockActiveChanged(active)
            },
            requestedPage = requestedPage,
            nightClockContent = {
                val nightWeather by weatherViewModel.uiState.collectAsStateWithLifecycle()
                NightClockScreen(
                    textDimPercent = settingsUiState.settings.nightClockTextDimPercent,
                    weather = nightWeather,
                    clockAnchor = OverlayAnchor(settingsUiState.settings.nightClockAnchorX, settingsUiState.settings.nightClockAnchorY),
                    weatherAnchor = OverlayAnchor(settingsUiState.settings.nightClockWeatherAnchorX, settingsUiState.settings.nightClockWeatherAnchorY),
                    onClockAnchorChange = clockViewModel::setNightClockAnchor,
                    onWeatherAnchorChange = clockViewModel::setNightClockWeatherAnchor,
                )
            },
            appDrawerContent = { _, close ->
                val drawerState by appDrawerViewModel.uiState.collectAsStateWithLifecycle()
                BackHandler(onBack = close)
                AppDrawerScreen(
                    state = drawerState,
                    onQueryChange = appDrawerViewModel::onQueryChange,
                    onLaunch = { app -> appDrawerViewModel.launch(app); close() },
                    onLongPress = appDrawerViewModel::openAppInfo,
                    onClose = close,
                )
            },
            notificationsContent = { _, close ->
                val notifications by NotificationRepository.notifications.collectAsStateWithLifecycle()
                val accessState by NotificationRepository.accessState.collectAsStateWithLifecycle()
                BackHandler(onBack = close)
                NotificationsScreen(
                    accessState = accessState,
                    notifications = notifications,
                    onOpen = { notification ->
                        runCatching { notification.contentIntent?.send() }
                        close()
                    },
                    onDismiss = { NotificationRepository.dismiss(it.key) },
                    onClearAll = NotificationRepository::clearAll,
                    onGrantAccess = onRequestNotificationAccess,
                    onOpenSettings = {
                        requestedPage = orderedPages.indexOf(LauncherPage.Settings).takeIf { it >= 0 }
                        close()
                    },
                    brightnessLevel255 = settingsUiState.settings.brightnessLevel255,
                    onBrightnessChange = settingsViewModel::setBrightnessLevel,
                )
            },
        )

        // Hidden on Settings specifically - its whole value is "reachable from any other page,"
        // which Settings itself doesn't need, and its fixed bottom-right position can otherwise
        // sit directly over a settings row that happens to render in that same corner.
        if (orderedPages.getOrNull(currentPageIndex) != LauncherPage.Settings) {
            InAppPttOverlay(context)
        }

        AnimatedVisibility(visible = showOnboarding && !isDefaultLauncher) {
            OnboardingOverlay(
                onSetAsHome = onRequestHomeRole,
                onSkip = { showOnboarding = false },
            )
        }

        // Skipped entirely in text-only mode on the Clock page - there, ClockScreen dims its own
        // content directly instead, leaving the background/Photorama fully lit underneath. While
        // Night Clock is showing, this reads its own separate (and usually much dimmer) backlight
        // target instead of the main Clock's - otherwise this compensating scrim, which is what
        // actually gets the panel down near true black past layer 1's limited floor (see
        // BacklightController's doc comment), stays computed from whatever the main brightness
        // happens to be during the day and never kicks in for Night Clock at all. The main "extra
        // dim" knob has no Night Clock equivalent - Night Clock's background is already solid
        // black by design (see NightClockSettingsContent's doc comment), so it's excluded here.
        if (!textOnlyDim) {
            BrightnessDimOverlay(
                brightnessLevel255 = if (isNightClockActive) {
                    settingsUiState.settings.nightClockBrightnessLevel255
                } else {
                    settingsUiState.settings.brightnessLevel255
                },
                extraDimPercent = if (isNightClockActive) 0 else settingsUiState.settings.brightnessExtraDimPercent,
            )
        }
    }
}

@Composable
private fun LauncherPageContent(
    page: LauncherPage,
    isJellyfinPageActive: Boolean,
    isAwake: Boolean,
    clockViewModel: ClockViewModel,
    weatherViewModel: WeatherViewModel,
    photoramaViewModel: PhotoramaViewModel,
    settingsViewModel: SettingsViewModel,
    iptvViewModel: IptvViewModel,
    onRequestHomeRole: () -> Unit,
    onRequestNotificationAccess: () -> Unit,
    onRequestWriteSettingsAccess: () -> Unit,
    onRequestOverlayAccess: () -> Unit,
    clockContentDimAlpha: Float = 0f,
) {
    when (page) {
        LauncherPage.Clock -> {
            val context = LocalContext.current
            val appearance by clockViewModel.appearance.collectAsStateWithLifecycle()
            val weather by weatherViewModel.uiState.collectAsStateWithLifecycle()
            val photorama by photoramaViewModel.uiState.collectAsStateWithLifecycle()
            val airPlayEngine = remember { AppContainer.get(context).airPlayEngine }
            val airPlay by airPlayEngine.uiState.collectAsStateWithLifecycle()
            ClockScreen(
                appearance = appearance,
                weather = weather,
                showEdgeAffordance = true,
                onClockAnchorChange = clockViewModel::setClockAnchor,
                onWeatherWidgetAnchorChange = clockViewModel::setWeatherWidgetAnchor,
                photoramaBackground = photorama.currentPhoto,
                // Active mirroring must always be allowed to take over the Clock page; the
                // separate "show clock widget" setting only controls the idle/status chip, not
                // whether live video is visible at all.
                airPlayStatus = airPlay,
                onTextWidgetAnchorChange = clockViewModel::setTextWidgetAnchor,
                contentDimAlpha = clockContentDimAlpha,
            )
        }
        LauncherPage.Photorama -> {
            val state by photoramaViewModel.uiState.collectAsStateWithLifecycle()
            PhotoramaScreen(state = state)
        }
        LauncherPage.Browser -> {
            val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
            BrowserScreen(
                homeUrl = settingsState.settings.browserHomeUrl,
                persistedUrl = settingsState.settings.browserLastVisitedUrl,
                onPersistCurrentUrl = settingsViewModel::setBrowserLastVisitedUrl,
                isAwake = isAwake,
            )
        }
        LauncherPage.Jellyfin -> {
            val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
            // Starts null (not "") so autoAuthEnabled below stays false - and no auto-auth script
            // gets generated at all - until the Keystore read actually resolves, rather than
            // briefly generating one with an empty password that could reach the page before the
            // real value loads.
            var autoAuthPassword by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(settingsState.settings.jellyfinAutoAuth) {
                autoAuthPassword = if (settingsState.settings.jellyfinAutoAuth) settingsViewModel.jellyfinPassword() else null
            }
            JellyfinScreen(
                serverUrl = settingsState.settings.jellyfinServerUrl,
                startPath = settingsState.settings.jellyfinStartPath,
                desktopMode = settingsState.settings.jellyfinDesktopMode,
                reloadOnOpen = settingsState.settings.jellyfinReloadOnOpen,
                openExternalLinks = settingsState.settings.jellyfinOpenExternalLinks,
                isActive = isJellyfinPageActive,
                isAwake = isAwake,
                autoAuthUsername = settingsState.settings.jellyfinUsername,
                autoAuthPassword = autoAuthPassword.orEmpty(),
                autoAuthEnabled = settingsState.settings.jellyfinAutoAuth && autoAuthPassword != null,
            )
        }
        LauncherPage.HomeAssistant -> {
            val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
            var autoAuthPassword by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(settingsState.settings.homeAssistantAutoAuth) {
                autoAuthPassword = if (settingsState.settings.homeAssistantAutoAuth) settingsViewModel.homeAssistantPassword() else null
            }
            HomeAssistantScreen(
                url = settingsState.settings.homeAssistantUrl,
                autoAuthUsername = settingsState.settings.homeAssistantUsername,
                autoAuthPassword = autoAuthPassword.orEmpty(),
                autoAuthEnabled = settingsState.settings.homeAssistantAutoAuth && autoAuthPassword != null,
            )
        }
        LauncherPage.Iptv -> {
            IptvScreen(viewModel = iptvViewModel)
        }
        LauncherPage.Settings -> {
            SettingsScreen(
                viewModel = settingsViewModel,
                onRequestHomeRole = onRequestHomeRole,
                onRequestNotificationAccess = onRequestNotificationAccess,
                onRequestWriteSettingsAccess = onRequestWriteSettingsAccess,
                onRequestOverlayAccess = onRequestOverlayAccess,
            )
        }
    }
}

@Composable
private fun InAppPttOverlay(context: android.content.Context) {
    val engine = remember { AppContainer.get(context).walkieTalkieEngine }
    val state by engine.uiState.collectAsStateWithLifecycle()
    if (!state.enabled) return

    Box(modifier = Modifier.fillMaxSize()) {
        PttButton(
            isTransmitting = state.isTransmitting,
            isSpeaking = state.activeIncomingPeerName != null,
            enabled = state.hasMicPermission,
            onPressStart = engine::pressToTalk,
            onPressEnd = engine::releaseToTalk,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 32.dp, end = 32.dp),
        )
    }
}
