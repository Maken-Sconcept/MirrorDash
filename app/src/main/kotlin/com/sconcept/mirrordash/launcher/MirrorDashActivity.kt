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
import com.sconcept.mirrordash.calendar.CalendarAgendaViewModel
import com.sconcept.mirrordash.brightness.BRIGHTNESS_FAILSAFE_HOLD_MS
import com.sconcept.mirrordash.brightness.BRIGHTNESS_FAILSAFE_WARNING_LEAD_MS
import com.sconcept.mirrordash.brightness.BacklightController
import com.sconcept.mirrordash.brightness.BrightnessDimOverlay
import com.sconcept.mirrordash.brightness.BrightnessFailsafe
import com.sconcept.mirrordash.brightness.BrightnessMath
import com.sconcept.mirrordash.clock.ClockScreen
import com.sconcept.mirrordash.clock.ClockViewModel
import com.sconcept.mirrordash.clock.NightClockScreen
import com.sconcept.mirrordash.clock.OverlayAnchor
import com.sconcept.mirrordash.gym.GymScreen
import com.sconcept.mirrordash.gym.GymViewModel
import com.sconcept.mirrordash.homeassistant.HomeAssistantScreen
import com.sconcept.mirrordash.iptv.EXTRA_OPEN_DOWNLOAD_MANAGER
import com.sconcept.mirrordash.iptv.IptvDownloadStatusPill
import com.sconcept.mirrordash.iptv.IptvPageState
import com.sconcept.mirrordash.iptv.IptvScreen
import com.sconcept.mirrordash.iptv.IptvViewModel
import com.sconcept.mirrordash.jellyfin.JellyfinScreen
import com.sconcept.mirrordash.kodi.KodiScreen
import com.sconcept.mirrordash.launcher.apps.AppDrawerScreen
import com.sconcept.mirrordash.launcher.display.DisplayOrientationController
import com.sconcept.mirrordash.launcher.display.DisplayOrientationMode
import com.sconcept.mirrordash.launcher.apps.AppDrawerViewModel
import com.sconcept.mirrordash.launcher.gestures.LauncherGestureHost
import com.sconcept.mirrordash.launcher.gestures.LauncherInteractionState
import com.sconcept.mirrordash.launcher.gestures.pageSwipePriority
import com.sconcept.mirrordash.launcher.navigation.LauncherPage
import com.sconcept.mirrordash.launcher.navigation.LauncherPages
import com.sconcept.mirrordash.launcher.notifications.NotificationRepository
import com.sconcept.mirrordash.launcher.notifications.NotificationsScreen
import com.sconcept.mirrordash.launcher.onboarding.OnboardingOverlay
import com.sconcept.mirrordash.news.NewsFeedViewModel
import com.sconcept.mirrordash.photorama.PhotoramaViewModel
import com.sconcept.mirrordash.settings.BRIGHTNESS_DIM_TARGET_TEXT_ONLY
import com.sconcept.mirrordash.settings.ui.SettingsScreen
import com.sconcept.mirrordash.settings.SettingsViewModel
import com.sconcept.mirrordash.stocks.StocksViewModel
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

/** Step size for a single remote volume-key press against [IptvViewModel]'s 0f..1f player volume
 * - 20 presses end to end, a comparable feel to the system stream volume's own step count. */
private const val IPTV_REMOTE_VOLUME_STEP = 0.05f

class MirrorDashActivity : ComponentActivity() {

    private val container by lazy { AppContainer.get(applicationContext) }
    private val memoryTrimCoordinator by lazy { MemoryTrimCoordinator(applicationContext) }

    private val launcherViewModel: LauncherViewModel by viewModels { LauncherViewModel.factory(container.settingsRepository) }
    private val clockViewModel: ClockViewModel by viewModels { ClockViewModel.factory(application, container.settingsRepository) }
    private val weatherViewModel: WeatherViewModel by viewModels { WeatherViewModel.factory(container.settingsRepository) }
    private val calendarAgendaViewModel: CalendarAgendaViewModel by viewModels { CalendarAgendaViewModel.factory(application) }
    private val stocksViewModel: StocksViewModel by viewModels { StocksViewModel.factory(container.settingsRepository) }
    private val newsFeedViewModel: NewsFeedViewModel by viewModels { NewsFeedViewModel.factory(container.settingsRepository) }
    private val photoramaViewModel: PhotoramaViewModel by viewModels {
        PhotoramaViewModel.factory(application, container.settingsRepository)
    }
    private val appDrawerViewModel: AppDrawerViewModel by viewModels { AppDrawerViewModel.factory(application) }
    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModel.factory(application, container.settingsRepository)
    }
    private val gymViewModel: GymViewModel by viewModels {
        GymViewModel.factory(application, container.gymRepository, container.gymContentRepository, container.gymSessionEngine, container.gymHealthConnectGateway)
    }
    private val iptvViewModel: IptvViewModel by viewModels {
        IptvViewModel.factory(application, container.settingsRepository, container.iptvSessionCoordinator)
    }
    // Bridges the Compose-side "is the hidden Night Clock tab currently showing" signal (from
    // LauncherGestureHost's onNightClockActiveChanged) into the plain lifecycleScope brightness
    // collectors below, which run outside Compose and read settingsRepository.settings directly.
    private val isNightClockActive = MutableStateFlow(false)

    // Same bridge shape as isNightClockActive above, but for "is the IPTV tab the one currently
    // visible" - dispatchKeyEvent (below) isn't composable, so it can't just read the pager's own
    // currentPageIndex directly, and TV-remote keys (volume/channel/number-pad) should only ever
    // be claimed by IPTV while its tab is actually on screen.
    private val isIptvPageActive = MutableStateFlow(false)

    private val requestMicPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val requestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) settingsViewModel.setRtspCameraEnabled(true)
    }
    private val requestNotificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val requestHomeRole = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    private val requestCalendarPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        calendarAgendaViewModel.refreshNow()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        memoryTrimCoordinator.start()
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

        // The service is intentionally driven by the persisted switch as well as the Settings
        // action, so a power cycle or process restart restores an explicitly enabled LAN camera.
        lifecycleScope.launch {
            container.settingsRepository.settings
                .map { Triple(it.rtspCameraEnabled, it.rtspAllowedClientIps, it.rtspQuality) }
                .distinctUntilChanged()
                .collect { (enabled, allowedClientIps, quality) ->
                    val serviceIntent = com.sconcept.mirrordash.rtsp.RtspCameraService.intent(
                        this@MirrorDashActivity,
                        allowedClientIps,
                        quality,
                    )
                    if (enabled && androidx.core.content.ContextCompat.checkSelfPermission(this@MirrorDashActivity, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        androidx.core.content.ContextCompat.startForegroundService(this@MirrorDashActivity, serviceIntent)
                    } else if (!enabled) {
                        stopService(serviceIntent)
                    }
                }
        }

        handleOpenDownloadManagerIntent(intent)

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
                    calendarAgendaViewModel = calendarAgendaViewModel,
                    stocksViewModel = stocksViewModel,
                    newsFeedViewModel = newsFeedViewModel,
                    photoramaViewModel = photoramaViewModel,
                    appDrawerViewModel = appDrawerViewModel,
                    settingsViewModel = settingsViewModel,
                    gymViewModel = gymViewModel,
                    iptvViewModel = iptvViewModel,
                    onRequestHomeRole = { requestHomeRole.launch(HomeRoleHelper.requestHomeRoleIntent(this)) },
                    onRequestNotificationAccess = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    onRequestCalendarAccess = { requestCalendarPermission.launch(Manifest.permission.READ_CALENDAR) },
                    onRequestCameraAccess = { requestCameraPermission.launch(Manifest.permission.CAMERA) },
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
                    onIptvPageActiveChanged = { active -> isIptvPageActive.value = active },
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    // singleTask (see the manifest) means a tap on the "download complete" notification while
    // this Activity is already running/backgrounded delivers here rather than a fresh onCreate -
    // both paths funnel through the same handler so the deep link works regardless of which one
    // actually fires.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenDownloadManagerIntent(intent)
    }

    private fun handleOpenDownloadManagerIntent(intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_OPEN_DOWNLOAD_MANAGER, false)) {
            container.iptvRecordingEngine.requestOpenDownloadManager()
        }
    }

    private var volumeFailsafeJob: Job? = null

    // This failsafe-timer block itself never consumes the event (falls through regardless) so
    // system volume still ramps normally as a side effect of holding it on every other page - the
    // failsafe is a bonus, not a hijack. No AccessibilityService/root `getevent` watcher is needed
    // for this: as the Home app, MirrorDashActivity reliably sees every volume key press on its
    // own already. handleIptvRemoteKey below *does* consume volume keys, but only while the IPTV
    // tab is visible, redirecting them to the tab's own player volume instead of the system
    // stream - the timer above still starts/stops off the raw key regardless of who ends up
    // consuming it.
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
        if (handleIptvRemoteKey(event)) return true
        return super.dispatchKeyEvent(event)
    }

    /** TV-remote support for the IPTV tab (brief: "volume up down, change channel etc" - "this
     * clock app might be installed on tv therefore make it work with the tv remote"). Only claims
     * a key while IPTV is the visible page ([isIptvPageActive]) and only for keys IPTV actually
     * has a use for - every other page's own input (including the volume failsafe above, which
     * runs unconditionally regardless of this method's return value) is unaffected. Reads
     * [iptvViewModel]'s state directly rather than threading it through Compose: it's a plain
     * field on this Activity already, and dispatchKeyEvent isn't composable.
     *
     * Deliberately doesn't intercept D-pad up/down for channel-stepping the way an old cable box
     * remote would - the Guide/channel list/VOD grids rely on D-pad for their own on-screen focus
     * navigation, and stealing it here would break moving around inside them. [KeyEvent
     * .KEYCODE_CHANNEL_UP]/[KeyEvent.KEYCODE_CHANNEL_DOWN] (a real key on most TV/IR remotes,
     * distinct from D-pad) and the number pad remain unambiguous either way. */
    private fun handleIptvRemoteKey(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || !isIptvPageActive.value) return false
        val state = iptvViewModel.uiState.value
        if (state.pageState != IptvPageState.READY) return false

        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> iptvViewModel.setVolume(state.volume + IPTV_REMOTE_VOLUME_STEP)
            KeyEvent.KEYCODE_VOLUME_DOWN -> iptvViewModel.setVolume(state.volume - IPTV_REMOTE_VOLUME_STEP)
            KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_MUTE -> iptvViewModel.toggleMute()
            KeyEvent.KEYCODE_CHANNEL_UP -> iptvViewModel.nextChannel()
            KeyEvent.KEYCODE_CHANNEL_DOWN -> iptvViewModel.previousChannel()
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE ->
                iptvViewModel.togglePlayPause()
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 ->
                iptvViewModel.enterChannelDigit('0' + (event.keyCode - KeyEvent.KEYCODE_0))
            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 ->
                iptvViewModel.enterChannelDigit('0' + (event.keyCode - KeyEvent.KEYCODE_NUMPAD_0))
            else -> return false
        }
        return true
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
    calendarAgendaViewModel: CalendarAgendaViewModel,
    stocksViewModel: StocksViewModel,
    newsFeedViewModel: NewsFeedViewModel,
    photoramaViewModel: PhotoramaViewModel,
    appDrawerViewModel: AppDrawerViewModel,
    settingsViewModel: SettingsViewModel,
    gymViewModel: GymViewModel,
    iptvViewModel: IptvViewModel,
    onRequestHomeRole: () -> Unit,
    onRequestNotificationAccess: () -> Unit,
    onRequestCalendarAccess: () -> Unit,
    onRequestCameraAccess: () -> Unit,
    onRequestWriteSettingsAccess: () -> Unit,
    onRequestOverlayAccess: () -> Unit,
    onRequestBrightnessFailsafe: () -> Unit,
    onNightClockActiveChanged: (Boolean) -> Unit,
    onIptvPageActiveChanged: (Boolean) -> Unit,
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
    // Photorama only ever exists as the Clock's own background now (see ClockViewModel/
    // ClockScreen) - there is no standalone page for it anymore. Browser/Jellyfin/Home
    // Assistant/IPTV are still opt-in - "each tab can be enabled or disabled in the settings, but
    // the clock and settings must always remain" (see LauncherPages' doc comment).
    val orderedPages = remember(
        settingsUiState.settings.browserEnabled,
        settingsUiState.settings.gymEnabled,
        settingsUiState.settings.jellyfinEnabled,
        settingsUiState.settings.homeAssistantEnabled,
        settingsUiState.settings.iptvEnabled,
        settingsUiState.settings.kodiEnabled,
    ) {
        LauncherPages.ordered(
            includeBrowserPage = settingsUiState.settings.browserEnabled,
            includeGymPage = settingsUiState.settings.gymEnabled,
            includeJellyfinPage = settingsUiState.settings.jellyfinEnabled,
            includeHomeAssistantPage = settingsUiState.settings.homeAssistantEnabled,
            includeIptvPage = settingsUiState.settings.iptvEnabled,
            includeKodiPage = settingsUiState.settings.kodiEnabled,
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

    // Tapping a "download complete" notification (see IptvRecordingEngine.requestOpenDownloadManager)
    // should jump to the IPTV tab regardless of which one is currently showing - same shape as the
    // AirPlay jump above, but driven by an ever-incrementing epoch (there's no natural "session
    // ended" edge here the way AirPlay's isSessionActive going false gives it) rather than a
    // boolean, so a second tap still counts as fresh even if the user never left the IPTV tab in
    // between. IptvScreen reacts to the same epoch to actually open the Download Manager panel
    // once there.
    val recordingEngine = remember { AppContainer.get(context).iptvRecordingEngine }
    val recordingEngineUiState by recordingEngine.uiState.collectAsStateWithLifecycle()
    val iptvPageIndex = remember(orderedPages) { orderedPages.indexOf(LauncherPage.Iptv).takeIf { it >= 0 } }
    LaunchedEffect(recordingEngineUiState.openRequestEpoch) {
        if (recordingEngineUiState.openRequestEpoch == 0) return@LaunchedEffect
        val target = iptvPageIndex ?: return@LaunchedEffect
        requestedPage = target
        // Reset back to null rather than leaving it pinned at target - LauncherGestureHost's own
        // requestedPage effect only re-navigates on a genuinely distinct value (see its doc
        // comment), so a *second* tap that resolves to this same target needs requestedPage to
        // have moved away from it in between to still register as a fresh request.
        delay(300)
        requestedPage = null
    }

    // The IPTV tab's whole sleep/off lifecycle (brief: "sleeping state when not active" / "shut
    // off completely ... when not in view") hangs off this one signal - see IptvViewModel's doc
    // comment. Driven from here rather than the page's own composable lifecycle because
    // HorizontalPager disposes off-screen pages almost immediately, which would lose track of
    // "still within the sleep timeout" the moment the user swipes away.
    val isIptvPageActive = orderedPages.getOrNull(currentPageIndex) == LauncherPage.Iptv
    LaunchedEffect(isIptvPageActive) {
        iptvViewModel.setActive(isIptvPageActive)
        onIptvPageActiveChanged(isIptvPageActive)
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
            requireTwoFingerPageSwipe = settingsUiState.settings.pageSwipeRequireTwoFingers,
            onPageSettled = { index ->
                launcherViewModel.onPageSettled(index)
                currentPageIndex = index
            },
            pageContent = { index, interactionState ->
                LauncherPageContent(
                    page = orderedPages.getOrElse(index) { LauncherPage.Clock },
                    isJellyfinPageActive = orderedPages.getOrNull(currentPageIndex) == LauncherPage.Jellyfin &&
                        orderedPages.getOrElse(index) { LauncherPage.Clock } == LauncherPage.Jellyfin,
                    isKodiPageActive = orderedPages.getOrNull(currentPageIndex) == LauncherPage.Kodi &&
                        orderedPages.getOrElse(index) { LauncherPage.Clock } == LauncherPage.Kodi,
                    isAwake = interactionState == LauncherInteractionState.Travel,
                    onSwipePage = { forward ->
                        val target = index + if (forward) 1 else -1
                        if (target in orderedPages.indices) requestedPage = target
                    },
                    clockViewModel = clockViewModel,
                    weatherViewModel = weatherViewModel,
                    calendarAgendaViewModel = calendarAgendaViewModel,
                    stocksViewModel = stocksViewModel,
                    newsFeedViewModel = newsFeedViewModel,
                    photoramaViewModel = photoramaViewModel,
                    settingsViewModel = settingsViewModel,
                    gymViewModel = gymViewModel,
                    iptvViewModel = iptvViewModel,
                    onRequestHomeRole = onRequestHomeRole,
                    onRequestNotificationAccess = onRequestNotificationAccess,
                    onRequestCalendarAccess = onRequestCalendarAccess,
                    onRequestCameraAccess = onRequestCameraAccess,
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
                val context = LocalContext.current
                val notifications by NotificationRepository.notifications.collectAsStateWithLifecycle()
                val accessState by NotificationRepository.accessState.collectAsStateWithLifecycle()
                // The listener can reconnect after the panel is composed (or be temporarily
                // disconnected by Android) even while the user has granted Notification Access.
                // The platform setting is the permission source of truth; the listener state is
                // only the live-feed connection state. Do not show a grant prompt for the latter.
                val effectiveAccessState = if (
                    accessState == com.sconcept.mirrordash.launcher.notifications.NotificationAccessState.GRANTED ||
                    NotificationRepository.isAccessGranted(context)
                ) {
                    com.sconcept.mirrordash.launcher.notifications.NotificationAccessState.GRANTED
                } else {
                    accessState
                }
                BackHandler(onBack = close)
                NotificationsScreen(
                    accessState = effectiveAccessState,
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
                    onClose = close,
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

        if (settingsUiState.settings.iptvShowRecordingStatusPill) {
            IptvDownloadStatusPill()
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
    isKodiPageActive: Boolean,
    isAwake: Boolean,
    onSwipePage: (forward: Boolean) -> Unit,
    clockViewModel: ClockViewModel,
    weatherViewModel: WeatherViewModel,
    calendarAgendaViewModel: CalendarAgendaViewModel,
    stocksViewModel: StocksViewModel,
    newsFeedViewModel: NewsFeedViewModel,
    photoramaViewModel: PhotoramaViewModel,
    settingsViewModel: SettingsViewModel,
    gymViewModel: GymViewModel,
    iptvViewModel: IptvViewModel,
    onRequestHomeRole: () -> Unit,
    onRequestNotificationAccess: () -> Unit,
    onRequestCalendarAccess: () -> Unit,
    onRequestCameraAccess: () -> Unit,
    onRequestWriteSettingsAccess: () -> Unit,
    onRequestOverlayAccess: () -> Unit,
    clockContentDimAlpha: Float = 0f,
) {
    when (page) {
        LauncherPage.Clock -> {
            val context = LocalContext.current
            val appearance by clockViewModel.appearance.collectAsStateWithLifecycle()
            val weather by weatherViewModel.uiState.collectAsStateWithLifecycle()
            val calendar by calendarAgendaViewModel.uiState.collectAsStateWithLifecycle()
            val stocks by stocksViewModel.uiState.collectAsStateWithLifecycle()
            val news by newsFeedViewModel.uiState.collectAsStateWithLifecycle()
            val photorama by photoramaViewModel.uiState.collectAsStateWithLifecycle()
            val airPlayEngine = remember { AppContainer.get(context).airPlayEngine }
            val airPlay by airPlayEngine.uiState.collectAsStateWithLifecycle()
            ClockScreen(
                appearance = appearance,
                weather = weather,
                showEdgeAffordance = true,
                onClockAnchorChange = clockViewModel::setClockAnchor,
                onWeatherWidgetAnchorChange = clockViewModel::setWeatherWidgetAnchor,
                photoramaState = photorama,
                onPhotoramaVideoMuted = { settingsViewModel.setPhotoramaVideosMuted(true) },
                // Active mirroring must always be allowed to take over the Clock page; the
                // separate "show clock widget" setting only controls the idle/status chip, not
                // whether live video is visible at all.
                airPlayStatus = airPlay,
                onTextWidgetAnchorChange = clockViewModel::setTextWidgetAnchor,
                calendar = calendar,
                onCalendarWidgetAnchorChange = clockViewModel::setCalendarWidgetAnchor,
                onTasksWidgetAnchorChange = clockViewModel::setTasksWidgetAnchor,
                onTaskItemToggle = clockViewModel::setTaskItemCompleted,
                stocks = stocks,
                onStocksWidgetAnchorChange = clockViewModel::setStocksWidgetAnchor,
                news = news,
                onNewsWidgetAnchorChange = clockViewModel::setNewsWidgetAnchor,
                contentDimAlpha = clockContentDimAlpha,
                onClockRemove = clockViewModel::hideClockTime,
                onWeatherWidgetRemove = clockViewModel::removeWeatherWidget,
                onTextWidgetRemove = clockViewModel::removeTextWidget,
                onCalendarWidgetRemove = clockViewModel::removeCalendarWidget,
                onTasksWidgetRemove = clockViewModel::removeTasksWidget,
                onStocksWidgetRemove = clockViewModel::removeStocksWidget,
                onNewsWidgetRemove = clockViewModel::removeNewsWidget,
                onClockRotationChange = clockViewModel::setClockRotation,
                onWeatherWidgetRotationChange = clockViewModel::setWeatherWidgetRotation,
                onTextWidgetRotationChange = clockViewModel::setTextWidgetRotation,
                onCalendarWidgetRotationChange = clockViewModel::setCalendarWidgetRotation,
                onTasksWidgetRotationChange = clockViewModel::setTasksWidgetRotation,
                onStocksWidgetRotationChange = clockViewModel::setStocksWidgetRotation,
                onNewsWidgetRotationChange = clockViewModel::setNewsWidgetRotation,
                onClockFontSizeChange = clockViewModel::setFontSize,
                onWeatherWidgetSizeChange = clockViewModel::setWeatherWidgetScale,
                onTextWidgetSizeChange = clockViewModel::setTextWidgetFontSize,
                onCalendarWidgetSizeChange = clockViewModel::setCalendarWidgetFontSize,
                onTasksWidgetSizeChange = clockViewModel::setTasksWidgetFontSize,
                onStocksWidgetSizeChange = clockViewModel::setStocksWidgetFontSize,
                onNewsWidgetSizeChange = clockViewModel::setNewsWidgetFontSize,
            )
        }
        LauncherPage.Browser -> {
            val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
            // WebView-backed pages lock every touch to themselves (see WebViewSwipePriority.kt's
            // doc comment) - this passive sibling watches the same raw drag and drives the page
            // change itself, rather than trying to win the WebView's touch away from it. Always
            // on, not gated to Travel mode like the rest of the launcher chrome - requiring a
            // separate long-press-to-wake before a swipe even registers made leaving these pages
            // feel like a two-step gesture instead of a plain swipe.
            Box(
                Modifier.fillMaxSize().pageSwipePriority(
                    enabled = true,
                    requireTwoFingers = settingsState.settings.pageSwipeRequireTwoFingers,
                    onSwipe = onSwipePage,
                ),
            ) {
                BrowserScreen(
                    homeUrl = settingsState.settings.browserHomeUrl,
                    persistedUrl = settingsState.settings.browserLastVisitedUrl,
                    onPersistCurrentUrl = settingsViewModel::setBrowserLastVisitedUrl,
                )
            }
        }
        LauncherPage.Gym -> {
            GymScreen(viewModel = gymViewModel)
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
            Box(
                Modifier.fillMaxSize().pageSwipePriority(
                    enabled = true,
                    requireTwoFingers = settingsState.settings.pageSwipeRequireTwoFingers,
                    onSwipe = onSwipePage,
                ),
            ) {
                JellyfinScreen(
                    serverUrl = settingsState.settings.jellyfinServerUrl,
                    startPath = settingsState.settings.jellyfinStartPath,
                    desktopMode = settingsState.settings.jellyfinDesktopMode,
                    reloadOnOpen = settingsState.settings.jellyfinReloadOnOpen,
                    openExternalLinks = settingsState.settings.jellyfinOpenExternalLinks,
                    isActive = isJellyfinPageActive,
                    autoAuthUsername = settingsState.settings.jellyfinUsername,
                    autoAuthPassword = autoAuthPassword.orEmpty(),
                    autoAuthEnabled = settingsState.settings.jellyfinAutoAuth && autoAuthPassword != null,
                )
            }
        }
        LauncherPage.HomeAssistant -> {
            val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
            var autoAuthPassword by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(settingsState.settings.homeAssistantAutoAuth) {
                autoAuthPassword = if (settingsState.settings.homeAssistantAutoAuth) settingsViewModel.homeAssistantPassword() else null
            }
            Box(
                Modifier.fillMaxSize().pageSwipePriority(
                    enabled = true,
                    requireTwoFingers = settingsState.settings.pageSwipeRequireTwoFingers,
                    onSwipe = onSwipePage,
                ),
            ) {
                HomeAssistantScreen(
                    url = settingsState.settings.homeAssistantUrl,
                    autoAuthUsername = settingsState.settings.homeAssistantUsername,
                    autoAuthPassword = autoAuthPassword.orEmpty(),
                    autoAuthEnabled = settingsState.settings.homeAssistantAutoAuth && autoAuthPassword != null,
                )
            }
        }
        LauncherPage.Iptv -> {
            IptvScreen(viewModel = iptvViewModel)
        }
        LauncherPage.Kodi -> {
            // Not wrapped in pageSwipePriority like the WebView-backed pages - this is plain
            // Compose content (a landing card, not an embedded WebView), so nothing locks the
            // touch away from HorizontalPager's own native drag handling here in the first place.
            // Adding the extra passive detector on top would double-fire on the same swipe: the
            // pager's own native drag already moves pages here, and pageSwipePriority would then
            // ALSO call requestedPage on release, competing with a still-in-flight native fling.
            val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
            KodiScreen(
                packageName = settingsState.settings.kodiPackageName,
                isActive = isKodiPageActive,
                autoLaunchOnOpen = settingsState.settings.kodiAutoLaunchOnOpen,
            )
        }
        LauncherPage.Settings -> {
            SettingsScreen(
                viewModel = settingsViewModel,
                onRequestHomeRole = onRequestHomeRole,
                onRequestNotificationAccess = onRequestNotificationAccess,
                onRequestCalendarAccess = onRequestCalendarAccess,
                onRequestCameraAccess = onRequestCameraAccess,
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
