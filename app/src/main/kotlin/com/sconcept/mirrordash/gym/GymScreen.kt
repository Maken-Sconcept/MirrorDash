package com.sconcept.mirrordash.gym

import android.net.Uri
import android.util.Log
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.key
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.composables.icons.lucide.R as LucideR
import com.sconcept.mirrordash.R
import com.sconcept.mirrordash.launcher.AppContainer
import com.sconcept.mirrordash.ui.theme.MDTheme
import com.sconcept.mirrordash.launcher.display.DisplayOrientationMode
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun GymScreen(
    viewModel: GymViewModel,
    modifier: Modifier = Modifier,
) {
    // Gym stays mounted inside the dashboard. Collect directly so catalogue updates are not
    // stranded on the initial empty state while the launcher manages its own lifecycle.
    val uiState by viewModel.uiState.collectAsState()
    val loadedExerciseCatalog by viewModel.exerciseCatalog.collectAsState()
    LaunchedEffect(loadedExerciseCatalog.size) {
        Log.i("GymScreen", "Exercise screen observes ${loadedExerciseCatalog.size} loaded exercises")
    }
    // Keep browsing responsive even if the dashboard aggregate state is still on its first frame.
    val exerciseUiState = if (uiState.exerciseCatalog.isEmpty() && loadedExerciseCatalog.isNotEmpty()) {
        uiState.copy(
            exerciseCatalog = loadedExerciseCatalog,
            exerciseCatalogCount = loadedExerciseCatalog.size,
            exerciseCatalogHighlights = loadedExerciseCatalog.take(6).map { it.name },
        )
    } else {
        uiState
    }
    var achievementsVisible by rememberSaveable { mutableStateOf(false) }
    var gymSettingsVisible by rememberSaveable { mutableStateOf(false) }
    var freeRideVideoUri by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingHealthConnectDeviceId by rememberSaveable { mutableStateOf<String?>(null) }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> freeRideVideoUri = uri?.toString() }
    val healthConnectPermissions = remember {
        setOf(
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(OxygenSaturationRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(BodyFatRecord::class),
        )
    }
    val healthConnectPermissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        pendingHealthConnectDeviceId?.takeIf { healthConnectPermissions.all { permission -> permission in granted } }?.let(viewModel::connectDevice)
        pendingHealthConnectDeviceId = null
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        GymBackdrop()
        if (uiState.activeSession != null) {
            ActiveSessionHud(
                uiState = exerciseUiState,
                onPauseResume = viewModel::pauseOrResumeSession,
                onEnd = viewModel::endAndSaveSession,
                onDiscard = viewModel::discardSession,
                onDismissStatus = viewModel::clearStatusMessage,
                freeRideVideoUri = freeRideVideoUri,
                onSelectFreeRideVideo = { videoPicker.launch(arrayOf("video/*")) },
            )
        } else {
            GymDashboard(
                uiState = exerciseUiState,
                onSelectDashboardTab = viewModel::selectDashboardTab,
                onToggleConnectionCenter = viewModel::toggleConnectionCenter,
                onOpenSetup = viewModel::openSetup,
                onDismissSetup = viewModel::dismissSetup,
                onSelectWorkoutType = viewModel::setWorkoutType,
                onSelectGoal = viewModel::selectGeneratorGoal,
                onSelectLevel = viewModel::selectGeneratorLevel,
                onToggleEquipment = viewModel::toggleGeneratorEquipment,
                onToggleMuscle = viewModel::toggleGeneratorMuscle,
                onSelectExerciseCount = viewModel::setGeneratorExerciseCount,
                onSelectDuration = viewModel::setGeneratorDuration,
                onGoToStep = viewModel::goToGeneratorStep,
                onNextStep = viewModel::nextGeneratorStep,
                onPreviousStep = viewModel::previousGeneratorStep,
                onStartSelectedSession = viewModel::startSelectedSession,
                onSetWorkoutLibraryFilter = viewModel::setWorkoutLibraryFilter,
                onOpenExercise = viewModel::openExercise,
                onCloseExercise = viewModel::closeExercise,
                onToggleFavoriteExercise = viewModel::toggleFavoriteExercise,
                onToggleWorkoutQueue = viewModel::toggleWorkoutQueue,
                onTogglePlayer = viewModel::togglePlayer,
                onOpenProfile = viewModel::openProfile,
                onAddProfile = viewModel::addProfile,
                onConnectDevice = { deviceId ->
                    val device = uiState.devices.firstOrNull { it.deviceId == deviceId }
                    if (device?.adapterId == GymBuiltInAdapterIds.SAMSUNG_HEALTH_CONNECT) {
                        pendingHealthConnectDeviceId = deviceId
                        healthConnectPermissionLauncher.launch(healthConnectPermissions)
                    } else {
                        viewModel.connectDevice(deviceId)
                    }
                },
                onDisconnectDevice = viewModel::disconnectDevice,
                onCycleAssignment = viewModel::cycleDeviceAssignment,
                onSelectChallenge = viewModel::selectChallenge,
                onOpenAchievements = { achievementsVisible = true },
                onOpenGymSettings = { gymSettingsVisible = true; viewModel.refreshWorkoutLibraryStatus() },
            )
        }

        AnimatedVisibility(
            visible = uiState.profileSheetProfile != null && uiState.activeSession == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            uiState.profileSheetProfile?.let { profile ->
                ProfileDetailSheet(
                    profile = profile,
                    wearableHealth = uiState.wearableHealth,
                    onRefreshWearableHealth = viewModel::refreshWearableHealth,
                    onDismiss = viewModel::dismissProfileSheet,
                    onSave = { name, ageYears, weightKg, heightCm, bodyFatPercent, healthSource, healthConnectionStatus ->
                        viewModel.saveProfile(
                            profileId = profile.id,
                            name = name,
                            ageYears = ageYears,
                            weightKg = weightKg,
                            heightCm = heightCm,
                            bodyFatPercent = bodyFatPercent,
                            healthSource = healthSource,
                            healthConnectionStatus = healthConnectionStatus,
                        )
                        viewModel.dismissProfileSheet()
                    },
                )
            }
        }
        AnimatedVisibility(visible = gymSettingsVisible && uiState.activeSession == null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            GymSettingsSheet(
                challengesEnabled = uiState.featureSettings.challengesEnabled,
                workoutLibrarySource = uiState.featureSettings.workoutLibrarySource,
                workoutLibraryStatus = uiState.workoutLibraryStatus,
                workoutSyncStatus = uiState.workoutSyncStatus,
                orientationMode = uiState.displayOrientationMode,
                onSetChallengesEnabled = viewModel::setChallengesEnabled,
                onSetWorkoutLibrarySource = viewModel::setWorkoutLibrarySource,
                onRefreshWorkoutLibrary = viewModel::refreshWorkoutLibraryStatus,
                onSyncWorkoutLibrary = viewModel::syncWorkoutLibraryNow,
                onDismiss = { gymSettingsVisible = false },
            )
        }
        AnimatedVisibility(visible = achievementsVisible && uiState.activeSession == null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            AchievementCollectionSheet(
                profile = uiState.selectedProfileDashboards.firstOrNull()?.profile ?: uiState.profiles.firstOrNull(),
                profiles = uiState.profiles,
                sessionHistory = uiState.sessionHistory,
                weeklyProgress = uiState.weeklyProgress,
                onDismiss = { achievementsVisible = false },
            )
        }

        AnimatedVisibility(
            visible = uiState.latestSummary != null && uiState.activeSession == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            uiState.latestSummary?.let { summary ->
                SummarySheet(summary = summary, onDismiss = viewModel::dismissSummary)
            }
        }
        WorkoutSyncOverlay(
            status = uiState.workoutSyncStatus,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 18.dp, end = 22.dp),
        )
    }
}

@Composable
private fun GymBackdrop() {
    val breathing = rememberInfiniteTransition(label = "gymAmbientBreath")
    val breath by breathing.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 6_800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "gymBackdropBreath",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF020305),
                        Color(0xFF04070B),
                        Color(0xFF000000),
                    ),
                ),
            ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x4037D9FF).copy(alpha = 0.12f + breath * 0.13f), Color.Transparent),
                        center = Offset(160f, 80f),
                        radius = 900f,
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(520.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x26FF8B59).copy(alpha = 0.07f + breath * 0.08f), Color.Transparent),
                        center = Offset(900f, 260f),
                        radius = 880f,
                    ),
                ),
        )
    }
}

@Composable
private fun GymDashboard(
    uiState: GymUiState,
    onSelectDashboardTab: (GymDashboardTab) -> Unit,
    onToggleConnectionCenter: () -> Unit,
    onOpenSetup: () -> Unit,
    onDismissSetup: () -> Unit,
    onSelectWorkoutType: (GymWorkoutType) -> Unit,
    onSelectGoal: (GymTrainingGoal) -> Unit,
    onSelectLevel: (GymTrainingLevel) -> Unit,
    onToggleEquipment: (GymEquipmentOption) -> Unit,
    onToggleMuscle: (GymMuscleGroup) -> Unit,
    onSelectExerciseCount: (Int) -> Unit,
    onSelectDuration: (Int) -> Unit,
    onGoToStep: (GymGeneratorStep) -> Unit,
    onNextStep: () -> Unit,
    onPreviousStep: () -> Unit,
    onStartSelectedSession: () -> Unit,
    onSetWorkoutLibraryFilter: (String) -> Unit,
    onOpenExercise: (String) -> Unit,
    onCloseExercise: () -> Unit,
    onToggleFavoriteExercise: (String) -> Unit,
    onToggleWorkoutQueue: (String) -> Unit,
    onTogglePlayer: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    onAddProfile: () -> Unit,
    onConnectDevice: (String) -> Unit,
    onDisconnectDevice: (String) -> Unit,
    onCycleAssignment: (String) -> Unit,
    onSelectChallenge: (String) -> Unit,
    onOpenAchievements: () -> Unit,
    onOpenGymSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        HeaderBlock(devices = uiState.devices, onOpenAchievements = onOpenAchievements, onOpenSettings = onOpenGymSettings)
        Spacer(Modifier.height(18.dp))
        DashboardTabBar(
            selectedTab = uiState.dashboardTab,
            onSelectTab = onSelectDashboardTab,
        )
        Spacer(Modifier.height(18.dp))
        when (uiState.dashboardTab) {
            GymDashboardTab.HOME -> {
                val scroll = rememberScrollState()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scroll),
                ) {
                    StartWorkoutHero(
                        profiles = uiState.profiles,
                        selectedPlayerIds = uiState.selectedPlayerIds,
                        onTogglePlayer = onTogglePlayer,
                        onAddProfile = onAddProfile,
                        onStart = onOpenSetup,
                    )
                    Spacer(Modifier.height(18.dp))
                    AnimatedContent(
                        targetState = uiState.setupVisible,
                        transitionSpec = {
                            if (targetState) {
                                (slideInHorizontally { it } + fadeIn()).togetherWith(
                                    slideOutHorizontally { -it / 4 } + fadeOut(),
                                )
                            } else {
                                (slideInHorizontally { -it / 4 } + fadeIn()).togetherWith(
                                    slideOutHorizontally { it } + fadeOut(),
                                )
                            }
                        },
                        label = "homeSetupStage",
                    ) { setupVisible ->
                        if (setupVisible) {
                            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                                LaunchFocusMeta(
                                    activeProfileCount = uiState.activeProfileCount,
                                    workoutType = uiState.selectedWorkoutType,
                                    connectedDeviceCount = uiState.devices.count { it.state != FitnessConnectionState.DISCONNECTED },
                                )
                                SessionSetupSheet(
                                    uiState = uiState,
                                    onTogglePlayer = onTogglePlayer,
                                    onSelectWorkoutType = onSelectWorkoutType,
                                    onSelectGoal = onSelectGoal,
                                    onSelectLevel = onSelectLevel,
                                    onToggleEquipment = onToggleEquipment,
                                    onToggleMuscle = onToggleMuscle,
                                    onSelectExerciseCount = onSelectExerciseCount,
                                    onSelectDuration = onSelectDuration,
                                    onGoToStep = onGoToStep,
                                    onNextStep = onNextStep,
                                    onPreviousStep = onPreviousStep,
                                    onStart = onStartSelectedSession,
                                    onDismiss = onDismissSetup,
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                DeviceStatusStrip(
                                    devices = uiState.devices.filter { it.kind != FitnessDeviceKind.HEART_RATE },
                                    expanded = uiState.connectionCenterExpanded,
                                    onToggle = onToggleConnectionCenter,
                                    onConnectDevice = onConnectDevice,
                                    onDisconnectDevice = onDisconnectDevice,
                                    onCycleAssignment = onCycleAssignment,
                                    profiles = uiState.profiles,
                                )
                                ProfilesDashboardCards(
                                    profiles = uiState.selectedProfileDashboards,
                                    onOpenProfile = onOpenProfile,
                                )
                                if (uiState.featureSettings.challengesEnabled) WorkoutChallengeSection(
                                    challenges = uiState.availableChallenges.take(3),
                                    selectedChallengeId = uiState.selectedChallengeId,
                                    onSelectChallenge = onSelectChallenge,
                                    onSelectWorkoutType = onSelectWorkoutType,
                                    onStart = onOpenSetup,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Spacer(Modifier.height(120.dp))
                }
            }
            GymDashboardTab.WORKOUTS -> WorkoutsTabContent(
                uiState = uiState,
                onSetWorkoutLibraryFilter = onSetWorkoutLibraryFilter,
                onOpenExercise = onOpenExercise,
                onCloseExercise = onCloseExercise,
                onToggleFavoriteExercise = onToggleFavoriteExercise,
                onToggleWorkoutQueue = onToggleWorkoutQueue,
                onStart = { onSelectWorkoutType(GymWorkoutType.STRENGTH); onOpenSetup() },
                modifier = Modifier.weight(1f),
            )
            GymDashboardTab.CHALLENGES -> ChallengesTabContent(
                uiState = uiState,
                onSelectChallenge = onSelectChallenge,
                onSelectWorkoutType = onSelectWorkoutType,
                onStart = onOpenSetup,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DashboardTabBar(
    selectedTab: GymDashboardTab,
    onSelectTab: (GymDashboardTab) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        GymDashboardTab.entries.forEach { tab ->
            FilterChip(
                label = tab.displayLabel,
                selected = selectedTab == tab,
                onClick = { onSelectTab(tab) },
            )
        }
    }
}

@Composable
private fun WorkoutsTabContent(
    uiState: GymUiState,
    onSetWorkoutLibraryFilter: (String) -> Unit,
    onOpenExercise: (String) -> Unit,
    onCloseExercise: () -> Unit,
    onToggleFavoriteExercise: (String) -> Unit,
    onToggleWorkoutQueue: (String) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedNames = uiState.selectedProfileDashboards.map { it.profile.name }
    val filter = uiState.workoutLibraryFilter
    val filteredCatalog = uiState.exerciseCatalog.filter { it.matchesLibraryFilter(filter) }
    val muscleGroups = listOf("All") + uiState.exerciseCatalog
        .map { it.libraryGroup ?: it.muscleGroups.firstOrNull()?.replace('_', ' ') ?: "Full Body" }
        .distinct()
        .sortedBy { libraryGroupSortOrder(it) }
    val context = LocalContext.current
    val contentRepository = remember(context) { AppContainer.get(context).gymContentRepository }

    val selectedExercise = uiState.selectedExerciseId?.let { selectedId ->
        uiState.exerciseCatalog.firstOrNull { it.id == selectedId }
    }
    if (selectedExercise != null) {
        ExerciseCatalogDetailPage(
            entry = selectedExercise,
            isFavorite = selectedExercise.id in uiState.favoriteExerciseIds,
            isQueued = selectedExercise.id in uiState.workoutQueueIds,
            onResolveVideo = contentRepository::resolveVideoUri,
            onBack = onCloseExercise,
            onToggleFavorite = { onToggleFavoriteExercise(selectedExercise.id) },
            onToggleQueue = { onToggleWorkoutQueue(selectedExercise.id) },
            modifier = modifier,
        )
        return
    }

    Box(modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
        item {
            WorkoutLibraryHero(
                activeProfileCount = uiState.activeProfileCount,
                selectedNames = selectedNames,
                selectedFilter = filter,
                muscleGroups = muscleGroups,
                onSelectFilter = onSetWorkoutLibraryFilter,
                onGenerate = onStart,
            )
        }
        item { ExerciseCatalogHeader(count = filteredCatalog.size, queueCount = uiState.workoutQueueIds.size, favoriteCount = uiState.favoriteExerciseIds.size) }
        if (filteredCatalog.isEmpty()) {
            item { EmptyWorkoutLibraryCard("No exercises match this equipment", "Choose another equipment icon to broaden the library.") }
        } else {
            items(filteredCatalog, key = { it.id }) { entry -> ExerciseCatalogRow(entry = entry, onClick = { onOpenExercise(entry.id) }) }
        }
        item { Spacer(Modifier.height(120.dp)) }
        }
    }
}

@Composable
private fun ChallengesTabContent(
    uiState: GymUiState,
    onSelectChallenge: (String) -> Unit,
    onSelectWorkoutType: (GymWorkoutType) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!uiState.featureSettings.challengesEnabled) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Challenges are off. Turn them back on from the Gym menu.", color = MDTheme.colors.textSecondary, style = MDTheme.type.body)
        }
        return
    }
    LazyColumn(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("CHALLENGE WORKOUTS", color = Color.White, style = MDTheme.type.sectionTitle.copy(fontWeight = FontWeight.Bold))
                Text("Purpose-built workouts that test a specific skill. Time-based goals live as achievements on each player card.", color = MDTheme.colors.textSecondary, style = MDTheme.type.settingSubtitle)
            }
        }
        item {
            WorkoutChallengeSection(
                challenges = uiState.availableChallenges,
                selectedChallengeId = uiState.selectedChallengeId,
                onSelectChallenge = onSelectChallenge,
                onSelectWorkoutType = onSelectWorkoutType,
                onStart = onStart,
            )
        }
        item { Spacer(Modifier.height(120.dp)) }
    }
}

@Composable
private fun ActiveAchievementSection(
    achievements: List<GymActiveAchievement>,
) {
    if (achievements.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        achievements.filter { it.cadence == GymAchievementCadence.WEEKLY }.takeIf { it.isNotEmpty() }?.let { weekly ->
            Text("WEEKLY ACHIEVEMENT", color = Color.White, style = MDTheme.type.settingTitle)
            weekly.forEach { ActiveAchievementCard(it) }
        }
        achievements.filter { it.cadence == GymAchievementCadence.DAILY }.takeIf { it.isNotEmpty() }?.let { daily ->
            Text("DAILY ACHIEVEMENT", color = Color.White, style = MDTheme.type.settingTitle)
            daily.forEach { ActiveAchievementCard(it) }
        }
    }
}

@Composable
private fun ActiveAchievementCard(achievement: GymActiveAchievement) {
    val percent = (achievement.progressSeconds.toFloat() / achievement.targetSeconds).coerceIn(0f, 1f)
    val accent = if (achievement.cadence == GymAchievementCadence.DAILY) Color(0xFF7CF7B8) else Color(0xFFF8C56F)
    Surface(color = Color(0x0FFFFFFF), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(achievement.title, color = Color.White, style = MDTheme.type.settingTitle, modifier = Modifier.weight(1f))
                Text("${achievement.rewardMultiplier}× XP", color = accent, style = MDTheme.type.caption)
            }
            Text(achievement.subtitle, color = MDTheme.colors.textSecondary, style = MDTheme.type.settingSubtitle)
            LinearProgressIndicator(progress = { percent }, color = accent, trackColor = Color(0x1FFFFFFF), modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape))
            Text(
                if (achievement.isComplete) "Complete — reward secured" else "${achievement.progressSeconds / 60} / ${achievement.targetSeconds / 60} min",
                color = MDTheme.colors.textTertiary,
                style = MDTheme.type.caption,
            )
        }
    }
}

@Composable
private fun WorkoutLibraryHero(
    activeProfileCount: Int,
    selectedNames: List<String>,
    selectedFilter: String,
    muscleGroups: List<String>,
    onSelectFilter: (String) -> Unit,
    onGenerate: () -> Unit,
) {
    Surface(
        color = Color(0x110FFFFFF),
        shape = RoundedCornerShape(26.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("EXERCISES", color = Color.White, style = MDTheme.type.settingTitle)
                Spacer(Modifier.weight(1f))
                Button(onClick = onGenerate, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8F7FF), contentColor = Color(0xFF08131A))) { Text("Generate workout") }
            }
            Text(
                if (selectedNames.isEmpty()) {
                    "No active profile selected yet."
                } else {
                    "$activeProfileCount active ${if (activeProfileCount == 1) "profile" else "profiles"}: ${selectedNames.joinToString(" / ")}"
                },
                color = MDTheme.colors.textSecondary,
                style = MDTheme.type.settingSubtitle,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                muscleGroups.forEach { filter ->
                    EquipmentFilterIcon(
                        label = filter,
                        selected = selectedFilter == filter,
                        onClick = { onSelectFilter(filter) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkoutSyncOverlay(status: GymWorkoutSyncStatus, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = status != GymWorkoutSyncStatus.Idle, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Surface(color = Color(0xEC101820), shape = RoundedCornerShape(14.dp)) {
            Row(
                modifier = Modifier.widthIn(max = 300.dp).padding(horizontal = 13.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (status) {
                    GymWorkoutSyncStatus.Indexing -> CircularProgressIndicator(color = Color(0xFF7DE6FF), strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    is GymWorkoutSyncStatus.Syncing -> CircularProgressIndicator(color = Color(0xFF7DE6FF), strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    is GymWorkoutSyncStatus.Complete -> Text("✓", color = Color(0xFF80E7B5), style = MDTheme.type.settingTitle)
                    is GymWorkoutSyncStatus.Failed -> Text("!", color = Color(0xFFFF9B7B), style = MDTheme.type.settingTitle)
                    GymWorkoutSyncStatus.Idle -> Unit
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    when (status) {
                        GymWorkoutSyncStatus.Indexing -> {
                            Text("SYNCING WORKOUTS", color = Color.White, style = MDTheme.type.caption)
                            Text("Checking the NAS library…", color = MDTheme.colors.textSecondary, style = MDTheme.type.caption)
                        }
                        is GymWorkoutSyncStatus.Syncing -> {
                            Text("SYNCING ${status.currentFile} OF ${status.totalFiles}", color = Color.White, style = MDTheme.type.caption)
                            Text(status.currentFileName, color = MDTheme.colors.textSecondary, style = MDTheme.type.caption, maxLines = 1)
                        }
                        is GymWorkoutSyncStatus.Complete -> {
                            Text("SYNC COMPLETE", color = Color(0xFF80E7B5), style = MDTheme.type.caption)
                            Text("${status.copiedFiles} of ${status.totalFiles} files copied", color = MDTheme.colors.textSecondary, style = MDTheme.type.caption)
                        }
                        is GymWorkoutSyncStatus.Failed -> {
                            Text("SYNC PAUSED", color = Color(0xFFFF9B7B), style = MDTheme.type.caption)
                            Text(status.message, color = MDTheme.colors.textSecondary, style = MDTheme.type.caption, maxLines = 2)
                        }
                        GymWorkoutSyncStatus.Idle -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun EquipmentFilterIcon(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .size(width = 76.dp, height = 68.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color(0x2038CBFF) else Color(0x0FFFFFFF))
            .border(if (selected) BorderStroke(1.dp, Color(0xFF38CBFF)) else BorderStroke(1.dp, Color(0x263D5665)), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ExerciseLucideIcon(label, contentDescription = label, tint = if (selected) Color(0xFF7DE6FF) else MDTheme.colors.textSecondary, animate = selected, ambient = selected, modifier = Modifier.size(22.dp))
        Text(if (label == "All") "All" else label, color = Color.White, style = MDTheme.type.caption, textAlign = TextAlign.Center)
    }
}

/** Lucide artwork makes workout lanes and achievement families recognizable at a glance. */
@Composable
private fun ExerciseLucideIcon(
    label: String,
    contentDescription: String?,
    tint: Color,
    animate: Boolean = false,
    ambient: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val icon = when {
        label.contains("Neck", true) || label.contains("Shoulder", true) || label.contains("Trap", true) -> LucideIconSpec(LucideR.drawable.lucide_ic_activity, setOf(LucideMotion.LIFT, LucideMotion.TURN))
        label.contains("Arm", true) || label.contains("Bicep", true) || label.contains("Tricep", true) || label.contains("Wrist", true) || label.contains("Forearm", true) -> LucideIconSpec(LucideR.drawable.lucide_ic_dumbbell, setOf(LucideMotion.LIFT, LucideMotion.PULSE))
        label.contains("Chest", true) || label.contains("Press", true) || label.contains("Fly", true) -> LucideIconSpec(LucideR.drawable.lucide_ic_dumbbell, setOf(LucideMotion.LIFT, LucideMotion.POP))
        label.contains("Back", true) || label.contains("Pull", true) || label.contains("Deadlift", true) -> LucideIconSpec(LucideR.drawable.lucide_ic_activity, setOf(LucideMotion.PULSE, LucideMotion.TURN))
        label.contains("Leg", true) || label.contains("Glute", true) || label.contains("Hamstring", true) || label.contains("Calf", true) -> LucideIconSpec(LucideR.drawable.lucide_ic_circle_gauge, setOf(LucideMotion.PULSE, LucideMotion.TURN))
        label.contains("Bike", true) || label.contains("Cycl", true) -> LucideIconSpec(LucideR.drawable.lucide_ic_bike, setOf(LucideMotion.WHEEL, LucideMotion.PULSE))
        label.contains("Row", true) || label.contains("Cable", true) || label.contains("Rope", true) -> LucideIconSpec(LucideR.drawable.lucide_ic_activity, setOf(LucideMotion.PULSE, LucideMotion.TURN))
        label.contains("Mobility", true) || label.contains("Yoga", true) || label.contains("Pilates", true) || label.contains("Barre", true) -> LucideIconSpec(LucideR.drawable.lucide_ic_timer_reset, setOf(LucideMotion.TURN, LucideMotion.PULSE))
        label.contains("Cardio", true) || label.contains("Run", true) || label.contains("Lunge", true) || label.contains("Squat", true) -> LucideIconSpec(LucideR.drawable.lucide_ic_circle_gauge, setOf(LucideMotion.PULSE, LucideMotion.TURN))
        label.contains("Core", true) || label.contains("Plank", true) || label.contains("Crunch", true) -> LucideIconSpec(LucideR.drawable.lucide_ic_timer, setOf(LucideMotion.POP, LucideMotion.PULSE))
        label.contains("Stretch", true) || label.contains("Recovery", true) -> LucideIconSpec(LucideR.drawable.lucide_ic_timer_reset, setOf(LucideMotion.TURN, LucideMotion.PULSE))
        label.contains("Bench", true) || label.contains("Bar", true) || label.contains("Handle", true) || label.contains("Dumbbell", true) || label.contains("Kettle", true) -> LucideIconSpec(LucideR.drawable.lucide_ic_dumbbell, setOf(LucideMotion.LIFT, LucideMotion.PULSE))
        else -> LucideIconSpec(LucideR.drawable.lucide_ic_activity, setOf(LucideMotion.PULSE, LucideMotion.POP))
    }
    AnimatedLucideIcon(icon.resource, icon.motions, contentDescription, tint, animate, ambient, modifier)
}

@Composable
private fun AchievementLucideIcon(
    category: String,
    contentDescription: String?,
    tint: Color,
    animate: Boolean = false,
    ambient: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val icon = when (category) {
        "Consistency" -> LucideIconSpec(LucideR.drawable.lucide_ic_calendar_check, setOf(LucideMotion.POP, LucideMotion.PULSE))
        "Endurance" -> LucideIconSpec(LucideR.drawable.lucide_ic_timer, setOf(LucideMotion.TURN, LucideMotion.PULSE))
        "Cycling" -> LucideIconSpec(LucideR.drawable.lucide_ic_bike, setOf(LucideMotion.WHEEL, LucideMotion.PULSE))
        "Strength" -> LucideIconSpec(LucideR.drawable.lucide_ic_dumbbell, setOf(LucideMotion.LIFT, LucideMotion.PULSE))
        "Together" -> LucideIconSpec(LucideR.drawable.lucide_ic_heart_handshake, setOf(LucideMotion.PULSE, LucideMotion.POP))
        "Story" -> LucideIconSpec(LucideR.drawable.lucide_ic_flame, setOf(LucideMotion.PULSE, LucideMotion.POP))
        else -> LucideIconSpec(LucideR.drawable.lucide_ic_trophy, setOf(LucideMotion.POP, LucideMotion.TURN))
    }
    AnimatedLucideIcon(icon.resource, icon.motions, contentDescription, tint, animate, ambient, modifier)
}

private enum class LucideMotion { POP, PULSE, TURN, WHEEL, LIFT }
/** Add an icon by registering its drawable and any number of independent motion layers. */
private data class LucideIconSpec(val resource: Int, val motions: Set<LucideMotion>)

@Composable
private fun AnimatedLucideIcon(
    resource: Int,
    motions: Set<LucideMotion>,
    contentDescription: String?,
    tint: Color,
    animate: Boolean,
    ambient: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var hasEntered by remember(resource, animate, ambient) { mutableStateOf(false) }
    LaunchedEffect(resource, animate, ambient) {
        hasEntered = false
        if (animate) {
            // Yield one frame so a newly opened modal visibly starts compact before arriving.
            kotlinx.coroutines.delay(90)
            hasEntered = true
        }
        if (ambient) {
            while (true) {
                kotlinx.coroutines.delay(kotlin.random.Random.nextLong(1_500, 4_000))
                hasEntered = false
                kotlinx.coroutines.delay(50)
                hasEntered = true
            }
        }
    }
    val progress by animateFloatAsState(
        targetValue = if (hasEntered) 1f else 0f,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "lucideIconMotion",
    )
    val scale = when {
        LucideMotion.LIFT in motions -> 0.82f + progress * 0.18f
        LucideMotion.POP in motions -> 0.78f + progress * 0.22f
        LucideMotion.PULSE in motions -> 0.84f + progress * 0.16f
        else -> 1f
    }
    val rotation = when {
        LucideMotion.WHEEL in motions -> progress * 28f
        LucideMotion.TURN in motions -> progress * 18f
        else -> 0f
    }
    Icon(
        painter = painterResource(resource),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.graphicsLayer(scaleX = scale, scaleY = scale, rotationZ = rotation),
    )
}

@Composable
private fun EmptyWorkoutLibraryCard(
    title: String,
    body: String,
) {
    Surface(
        color = Color(0x0FFFFFFF),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, color = Color.White, style = MDTheme.type.settingTitle)
            Text(body, color = MDTheme.colors.textSecondary, style = MDTheme.type.settingSubtitle)
        }
    }
}

@Composable
private fun WorkoutBrowserSection(
    categories: List<OnDemandCategoryTile>,
    workouts: List<OnDemandWorkoutCard>,
    activeProfileCount: Int,
    onSelectWorkoutType: (GymWorkoutType) -> Unit,
    onStart: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("WORKOUTS", color = Color.White, style = MDTheme.type.settingTitle)
            Spacer(Modifier.weight(1f))
            Text("$activeProfileCount active", color = MDTheme.colors.textSecondary, style = MDTheme.type.caption)
        }
        if (categories.isEmpty() && workouts.isEmpty()) {
            EmptyWorkoutLibraryCard(
                title = "No workouts in this lane",
                body = "Switch the badges above to browse another lane or jump into the exercise library below.",
            )
            return@Column
        }
        categories.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { category ->
                    Surface(
                        color = Color.Transparent,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(0.93f)
                            .clickable {
                                onSelectWorkoutType(
                                    when (category.title) {
                                        "Strength" -> GymWorkoutType.STRENGTH
                                        "Mobility", "Cool Down", "Yoga", "Pilates", "Barre" -> GymWorkoutType.HYBRID
                                        else -> GymWorkoutType.FREE_WORKOUT
                                    },
                                )
                            },
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(category.accent),
                                    RoundedCornerShape(22.dp),
                                )
                                .padding(18.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x1AFFFFFF)),
                                contentAlignment = Alignment.Center,
                            ) {
                                ExerciseLucideIcon(category.title, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            Text(
                                category.title,
                                color = Color.White,
                                style = MDTheme.type.body.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.align(Alignment.BottomStart),
                            )
                        }
                    }
                }
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        if (workouts.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("FEATURED CLASSES", color = Color.White, style = MDTheme.type.settingTitle)
        }
        workouts.forEachIndexed { index, workout ->
            val featured = index == 0
            Surface(
                color = Color(0x0FFFFFFF),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onSelectWorkoutType(workout.workoutType)
                        onStart()
                    },
            ) {
                if (featured) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.76f)
                                .background(
                                    Brush.linearGradient(workout.accent),
                                    RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                                )
                                .padding(18.dp),
                        ) {
                            Surface(
                                color = Color(0xE6E8FFFA),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(
                                    workout.badgeLabel,
                                    color = Color(0xFF0C1720),
                                    style = MDTheme.type.caption,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                            Text(
                                workout.title,
                                color = Color.White,
                                style = MDTheme.type.sectionTitle.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.align(Alignment.BottomStart),
                            )
                        }
                        Column(Modifier.padding(18.dp)) {
                            Text(
                                "${workout.dateLabel}  /  ${workout.durationLabel}  /  ${workout.difficultyLabel}",
                                color = MDTheme.colors.textSecondary,
                                style = MDTheme.type.caption,
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "with ${workout.coach}",
                                color = Color.White,
                                style = MDTheme.type.body.copy(fontWeight = FontWeight.SemiBold),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Ready for $activeProfileCount active ${if (activeProfileCount == 1) "profile" else "profiles"} with full-screen coaching, mirror telemetry, score events, and player-aware HUD overlays.",
                                color = MDTheme.colors.textSecondary,
                                style = MDTheme.type.settingSubtitle,
                            )
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                FilterChip(label = workout.focusLabel, selected = true, onClick = {})
                                FilterChip(label = "Bluetooth Audio", selected = false, onClick = {})
                                FilterChip(label = "Profiles Ready", selected = false, onClick = {})
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(0.95f)
                                .aspectRatio(1.5f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Brush.linearGradient(workout.accent)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.PlayCircleFilled,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(34.dp),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1.35f)) {
                            Text(workout.title, color = Color.White, style = MDTheme.type.settingTitle)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "${workout.dateLabel}  /  ${workout.difficultyLabel}",
                                color = MDTheme.colors.textSecondary,
                                style = MDTheme.type.caption,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("with ${workout.coach}", color = Color.White, style = MDTheme.type.settingSubtitle)
                            Spacer(Modifier.height(10.dp))
                            FilterChip(label = workout.focusLabel, selected = true, onClick = {})
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutChallengeSection(
    challenges: List<GymChallengeDefinition>,
    selectedChallengeId: String?,
    onSelectChallenge: (String) -> Unit,
    onSelectWorkoutType: (GymWorkoutType) -> Unit,
    onStart: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("CHALLENGES", color = Color.White, style = MDTheme.type.settingTitle)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.Leaderboard, contentDescription = null, tint = Color(0xFF7CF7B8))
        }
        challenges.forEach { challenge ->
            val selected = challenge.id == selectedChallengeId
            Surface(
                color = if (selected) Color(0x1438CBFF) else Color(0x0FFFFFFF),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectChallenge(challenge.id) },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(challenge.title.uppercase(), color = Color.White, style = MDTheme.type.settingTitle)
                        Spacer(Modifier.height(6.dp))
                        Text(challenge.subtitle, color = MDTheme.colors.textSecondary, style = MDTheme.type.settingSubtitle)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "${challenge.durationSeconds}s / ${challenge.difficultyLabel} / ${challenge.equipmentLabel}" +
                                (challenge.bestLabel?.let { " / PB $it" } ?: ""),
                            color = MDTheme.colors.textTertiary,
                            style = MDTheme.type.caption,
                        )
                    }
                    Button(
                        onClick = {
                            onSelectChallenge(challenge.id)
                            onSelectWorkoutType(challenge.workoutType)
                            onStart()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selected) Color(0xFFE8F7FF) else Color(0x1FFFFFFF),
                            contentColor = if (selected) Color(0xFF06131A) else Color.White,
                        ),
                    ) {
                        Text("START")
                    }
                }
            }
        }
    }
}

private fun OnDemandCategoryTile.matchesLibraryFilter(filter: String): Boolean = when (filter) {
    "All" -> true
    "Strength" -> title == "Strength"
    "Recovery" -> title == "Cool Down" || title == "Mobility"
    "Mind & Body" -> title == "Yoga" || title == "Pilates" || title == "Barre"
    "Library", "Challenges" -> false
    else -> true
}

private fun OnDemandWorkoutCard.matchesLibraryFilter(filter: String): Boolean = when (filter) {
    "All", "Library" -> true
    "Challenges" -> false
    "Strength" -> workoutType == GymWorkoutType.STRENGTH
    "Recovery" -> focusLabel.contains("Mobility", true) || title.contains("Cool", true)
    "Mind & Body" -> focusLabel.contains("Flow", true) || workoutType == GymWorkoutType.HYBRID
    else -> true
}

private fun GymExerciseCatalogEntry.matchesLibraryFilter(filter: String): Boolean {
    val actualGroup = libraryGroup ?: muscleGroups.firstOrNull()?.replace('_', ' ') ?: "Full Body"
    val haystack = buildString {
        append(name)
        append(' ')
        append(equipment.joinToString(" "))
        append(' ')
        append(muscleGroups.joinToString(" "))
        append(' ')
        append(muscles.joinToString(" "))
    }.lowercase(Locale.US)
    return when (filter) {
        "All" -> true
        actualGroup -> true
        "Bodyweight" -> equipment.isEmpty() || haystack.contains("bodyweight")
        "Dumbbells" -> equipment.any { it in setOf("HANDLES", "DUMBBELLS") }
        "Barbell" -> equipment.any { it in setOf("BAR", "SHORT_BAR", "BARBELL") }
        "Kettlebells" -> equipment.any { it in setOf("HANDLES", "KETTLEBELLS") } && (haystack.contains("kettle") || !haystack.contains("bar"))
        "Bands" -> equipment.any { it in setOf("STRAPS", "BANDS", "BELT") }
        "Cables" -> equipment.any { it in setOf("BLACK_CABLES", "GREY_CABLES", "CABLES", "ROPE") }
        "Bench" -> equipment.any { it == "BENCH" } || haystack.contains("bench")
        "Bike" -> haystack.contains("bike") || haystack.contains("cycling") || haystack.contains("pedal")
        "Rower" -> haystack.contains("row")
        "Challenges" -> false
        "Strength" -> !haystack.contains("mobility") && !haystack.contains("stretch") && !haystack.contains("recovery")
        "Recovery" -> haystack.contains("mobility") || haystack.contains("stretch") || haystack.contains("recovery")
        "Mind & Body" -> haystack.contains("yoga") || haystack.contains("pilates") || haystack.contains("barre")
        else -> false
    }
}

private fun libraryGroupSortOrder(group: String): Int = listOf(
    "All", "Abs", "Arms", "Back", "Chest", "Legs", "Shoulders", "Neck", "Full Body",
).indexOf(group).let { if (it == -1) Int.MAX_VALUE else it }

@Composable
private fun HeaderBlock(devices: List<FitnessDeviceSnapshot>, onOpenAchievements: () -> Unit, onOpenSettings: () -> Unit) {
    val date = remember { SimpleDateFormat("EEEE, MMMM d  h:mm a", Locale.US).format(Date()) }
    val connectedEquipment = devices.filter {
        it.kind != FitnessDeviceKind.HEART_RATE && it.state != FitnessConnectionState.DISCONNECTED
    }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val stacked = maxWidth < 820.dp
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column {
                    Text(
                        "GYM & WORKOUTS",
                        color = Color.White,
                        style = MDTheme.type.pageLabel.copy(letterSpacing = MDTheme.type.pageLabel.letterSpacing * 1.2f),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(date.uppercase(), color = MDTheme.colors.textSecondary, style = MDTheme.type.body)
                }
                if (connectedEquipment.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        connectedEquipment.forEach { device ->
                            FilterChip(
                                label = device.displayName,
                                selected = true,
                                onClick = {},
                            )
                        }
                    }
                }
                GymOverflowMenu(onOpenAchievements, onOpenSettings)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "GYM & WORKOUTS",
                        color = Color.White,
                        style = MDTheme.type.pageLabel.copy(letterSpacing = MDTheme.type.pageLabel.letterSpacing * 1.2f),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(date.uppercase(), color = MDTheme.colors.textSecondary, style = MDTheme.type.body)
                }
                if (connectedEquipment.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        connectedEquipment.forEach { device ->
                            FilterChip(
                                label = device.displayName,
                                selected = true,
                                onClick = {},
                            )
                        }
                    }
                }
                GymOverflowMenu(onOpenAchievements, onOpenSettings)
            }
        }
    }
}

@Composable
private fun GymOverflowMenu(onOpenAchievements: () -> Unit, onOpenSettings: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        color = Color(0x14FFFFFF), shape = CircleShape,
        modifier = Modifier.size(44.dp).clickable { expanded = true },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Menu, contentDescription = "Gym menu", tint = Color.White, modifier = Modifier.size(23.dp))
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("Achievements") }, onClick = { expanded = false; onOpenAchievements() })
                DropdownMenuItem(text = { Text("Settings") }, onClick = { expanded = false; onOpenSettings() })
            }
        }
    }
}

@Composable
private fun GymSettingsSheet(
    challengesEnabled: Boolean,
    workoutLibrarySource: GymWorkoutLibrarySource,
    workoutLibraryStatus: GymWorkoutLibraryStatus,
    workoutSyncStatus: GymWorkoutSyncStatus,
    orientationMode: String,
    onSetChallengesEnabled: (Boolean) -> Unit,
    onSetWorkoutLibrarySource: (GymWorkoutLibrarySource) -> Unit,
    onRefreshWorkoutLibrary: () -> Unit,
    onSyncWorkoutLibrary: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(color = Color(0xF50A1016), modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Menu, null, tint = Color(0xFF7DE6FF), modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("GYM SETTINGS", color = Color.White, style = MDTheme.type.sectionTitle.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                Text("Controls for how your workout space behaves. More options will live here as the Gym grows.", color = MDTheme.colors.textSecondary, style = MDTheme.type.settingSubtitle)
                Text("EXPERIENCE", color = MDTheme.colors.textTertiary, style = MDTheme.type.caption)
                Surface(color = Color(0x0DFFFFFF), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Challenges", color = Color.White, style = MDTheme.type.settingTitle)
                        Text("Show adaptive missions and harder challenge programs.", color = MDTheme.colors.textSecondary, style = MDTheme.type.settingSubtitle)
                    }
                    Switch(checked = challengesEnabled, onCheckedChange = onSetChallengesEnabled)
                }
                Text("Display follows MirrorDash: ${DisplayOrientationMode.fromStorageKey(orientationMode).name.replace('_', ' ')}. Change orientation in Launcher settings.", color = MDTheme.colors.textTertiary, style = MDTheme.type.caption)
            }
        }
                Text("WORKOUT LIBRARY", color = MDTheme.colors.textTertiary, style = MDTheme.type.caption)
                Surface(color = Color(0x0DFFFFFF), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Workout source", color = Color.White, style = MDTheme.type.settingTitle)
                        Text("Memory card is offline-first. The Gym syncs Entertainment/Workouts in the background and streams any missing video from the NAS.", color = MDTheme.colors.textSecondary, style = MDTheme.type.settingSubtitle)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GymWorkoutLibrarySource.entries.forEach { source ->
                                val selected = source == workoutLibrarySource
                                TextButton(onClick = { onSetWorkoutLibrarySource(source) }) {
                                    Text(
                                        if (source == GymWorkoutLibrarySource.MEMORY_CARD) "Memory card" else "NAS only",
                                        color = if (selected) Color(0xFF7DE6FF) else MDTheme.colors.textSecondary,
                                    )
                                }
                            }
                        }
                        val syncInProgress = workoutSyncStatus is GymWorkoutSyncStatus.Indexing || workoutSyncStatus is GymWorkoutSyncStatus.Syncing
                        when {
                            workoutLibraryStatus.isLoading -> Text("Checking card and NAS library...", color = MDTheme.colors.textSecondary, style = MDTheme.type.caption)
                            workoutLibraryStatus.message != null -> Text(workoutLibraryStatus.message, color = Color(0xFFFF9B7B), style = MDTheme.type.caption)
                            else -> Text(
                                "Memory card: ${workoutLibraryStatus.cardVideoCount} videos  •  NAS: ${workoutLibraryStatus.nasVideoCount}  •  Remaining: ${workoutLibraryStatus.remainingVideoCount}",
                                color = MDTheme.colors.textSecondary,
                                style = MDTheme.type.caption,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TextButton(onClick = onRefreshWorkoutLibrary, enabled = !workoutLibraryStatus.isLoading && !syncInProgress) { Text("Refresh") }
                            Button(
                                onClick = onSyncWorkoutLibrary,
                                enabled = workoutLibrarySource == GymWorkoutLibrarySource.MEMORY_CARD && workoutLibraryStatus.isAvailable && !workoutLibraryStatus.isLoading && !syncInProgress,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7DE6FF), contentColor = Color(0xFF071218)),
                            ) {
                                Text(if (syncInProgress) "Syncing..." else if (workoutLibraryStatus.remainingVideoCount > 0) "Sync remaining" else "Verify sync")
                            }
                        }
                    }
                }
    }
    }
}

@Composable
private fun StreakFlame(streak: Int) {
    val transition = rememberInfiniteTransition(label = "streakFlame")
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(850)),
        label = "streakFlamePulse",
    )
    val base = (1f + streak.coerceAtMost(20) * 0.035f) * pulse
    Surface(color = Color(0x18FF7A45), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🔥", style = MDTheme.type.sectionTitle, modifier = Modifier.graphicsLayer(scaleX = base, scaleY = base))
            Spacer(Modifier.width(12.dp))
            Column { Text("${streak.coerceAtLeast(0)} WEEK STREAK", color = Color.White, style = MDTheme.type.settingTitle); Text(if (streak > 0) "Keep your weekly target alive." else "Complete your weekly target to ignite your streak.", color = MDTheme.colors.textSecondary, style = MDTheme.type.caption) }
        }
    }
}

@Composable
private fun AchievementCollectionSheet(
    profile: GymProfile?,
    profiles: List<GymProfile>,
    sessionHistory: List<GymSessionRecord>,
    weeklyProgress: GymWeeklyProgress,
    onDismiss: () -> Unit,
) {
    if (profile == null) return
    var view by rememberSaveable { mutableStateOf(AchievementView.ME) }
    var collectionLayout by rememberSaveable { mutableStateOf(AchievementCollectionLayout.CARDS) }
    var selectedAchievementId by rememberSaveable { mutableStateOf<String?>(null) }
    val partner = profiles.firstOrNull { it.id != profile.id }
    val activePartner = when (view) {
        AchievementView.ME -> null
        AchievementView.PARTNER -> partner
        AchievementView.TOGETHER -> partner
    }
    val subject = if (view == AchievementView.PARTNER) partner ?: profile else profile
    val achievements = evaluateAchievements(subject, sessionHistory, if (view == AchievementView.TOGETHER) activePartner else null)
    val almostThere = achievements.filter { it.nextTarget != null }.sortedByDescending { it.percentToNext }.take(4)
    val selectedAchievement = achievements.firstOrNull { it.definition.id == selectedAchievementId }
    Surface(color = Color(0xF50A1016), modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                AchievementLucideIcon("Achievement", contentDescription = null, tint = Color(0xFFF8C56F), modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("ACHIEVEMENTS", color = Color.White, style = MDTheme.type.sectionTitle.copy(fontWeight = FontWeight.Bold)); Text(if (view == AchievementView.TOGETHER) "${profile.name} + ${partner?.name ?: "Partner"}" else subject.name, color = MDTheme.colors.textSecondary, style = MDTheme.type.settingSubtitle) }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
            if (partner != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    AchievementView.entries.forEach { option ->
                        TextButton(onClick = { view = option }) {
                            Text(option.label, color = if (view == option) Color(0xFFF8C56F) else MDTheme.colors.textSecondary)
                        }
                    }
                }
            }
            AchievementScoreStrip(profile = profile, partner = partner, view = view, achievements = achievements, weeklyProgress = weeklyProgress)
            if (almostThere.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ALMOST THERE", color = Color(0xFFF8C56F), style = MDTheme.type.caption)
                    Text("Your closest next unlocks", color = Color.White, style = MDTheme.type.settingTitle)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    almostThere.forEach { achievement -> AlmostThereCard(achievement, onClick = { selectedAchievementId = achievement.definition.id }) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("YOUR COLLECTION", color = MDTheme.colors.textTertiary, style = MDTheme.type.caption, modifier = Modifier.weight(1f))
                AchievementCollectionLayout.entries.forEach { layout ->
                    TextButton(onClick = { collectionLayout = layout }) { Text(layout.label, color = if (collectionLayout == layout) Color(0xFFF8C56F) else MDTheme.colors.textSecondary) }
                }
            }
            if (collectionLayout == AchievementCollectionLayout.CARDS) {
                achievements.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { achievement -> DuolingoAchievementCard(achievement, onClick = { selectedAchievementId = achievement.definition.id }, modifier = Modifier.weight(1f)) }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            } else {
                achievements.forEach { AchievementProgressCard(it, onClick = { selectedAchievementId = it.definition.id }) }
            }
        }
        selectedAchievement?.let { selected ->
            AchievementDetailDialog(
                achievement = selected,
                categoryAchievements = achievements.filter { it.definition.category == selected.definition.category && it.definition.id != selected.definition.id },
                profile = subject,
                onDismiss = { selectedAchievementId = null },
            )
        }
    }
}

@Composable
private fun AchievementDetailDialog(
    achievement: GymAchievementStatus,
    categoryAchievements: List<GymAchievementStatus>,
    profile: GymProfile,
    onDismiss: () -> Unit,
) {
    val next = achievement.nextTarget
    val tint = when (achievement.definition.category) {
        "Together" -> Color(0xFFF8C56F)
        "Cycling" -> Color(0xFF38CBFF)
        "Story" -> Color(0xFFFF8A5B)
        else -> Color(0xFF7CF7B8)
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = Color(0xFF111A22), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth(0.86f).heightIn(max = 720.dp)) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = onDismiss) { Text("Close") } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Reuse the collection icon and its motion profile; the detail card must never
            // substitute a generic trophy for a category-specific achievement.
            AchievementLucideIcon(
                category = achievement.definition.category,
                contentDescription = null,
                tint = tint,
                animate = true,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(achievement.definition.name, color = Color.White, style = MDTheme.type.sectionTitle.copy(fontWeight = FontWeight.Bold))
                Text("${achievement.definition.category} · ${achievement.definition.rarity}", color = tint, style = MDTheme.type.caption)
            }
        }
        Text(achievement.definition.description, color = MDTheme.colors.textSecondary, style = MDTheme.type.body)
        Text("HOW TO UNLOCK", color = MDTheme.colors.textTertiary, style = MDTheme.type.caption)
        Surface(
            color = Color(0x0AFFFFFF),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = achievement.definition.unlockCriteria,
                color = MDTheme.colors.textTertiary,
                style = MDTheme.type.settingSubtitle,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            )
        }
        Surface(color = tint.copy(alpha = 0.11f), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (next == null) "ALL TIERS COMPLETE" else "NEXT TIER", color = tint, style = MDTheme.type.caption)
                Text(if (next == null) "Mastered" else "${achievementValueLabel(achievement)} / ${achievementValueLabel(GymAchievementStatus(achievement.definition, next))}", color = Color.White, style = MDTheme.type.settingTitle)
                Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color(0x243FFFFFF))) {
                    Box(Modifier.fillMaxWidth(achievement.percentToNext).height(8.dp).background(tint))
                }
                Text(if (next == null) "Every tier is yours." else "${achievementValueLabel(GymAchievementStatus(achievement.definition, next - achievement.current))} remaining · +${achievement.nextReward ?: 0} XP", color = MDTheme.colors.textSecondary, style = MDTheme.type.caption)
            }
        }
        Text("PLAYER STATS", color = MDTheme.colors.textTertiary, style = MDTheme.type.caption)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            ProfileEditMetric("Level", GymProgression.levelFromXp(profile.totalXp).toString(), Modifier.weight(1f))
            ProfileEditMetric("XP", DecimalFormat("#,##0").format(profile.totalXp), Modifier.weight(1f))
            ProfileEditMetric("Workouts", profile.totalWorkouts.toString(), Modifier.weight(1f))
            ProfileEditMetric("Active time", "${profile.progression.lifetimeMinutes} min", Modifier.weight(1f))
        }
        if (categoryAchievements.isNotEmpty()) {
            Text("MORE ${achievement.definition.category.uppercase()}", color = MDTheme.colors.textTertiary, style = MDTheme.type.caption)
            categoryAchievements.forEach { related ->
                Surface(color = Color(0x0DFFFFFF), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        AchievementLucideIcon(related.definition.category, null, tint = MDTheme.colors.textSecondary, ambient = true, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(related.definition.name, color = Color.White, style = MDTheme.type.settingTitle)
                            Text(related.definition.description, color = MDTheme.colors.textSecondary, style = MDTheme.type.caption)
                        }
                        Text(if (related.nextTarget == null) "DONE" else "${related.currentTier}/${related.definition.tiers.size}", color = tint, style = MDTheme.type.caption)
                    }
                }
            }
        }
            }
        }
    }
}

private enum class AchievementView(val label: String) { ME("Me"), PARTNER("Partner"), TOGETHER("Together") }
private enum class AchievementCollectionLayout(val label: String) { CARDS("Cards"), LIST("List") }

@Composable
private fun DuolingoAchievementCard(achievement: GymAchievementStatus, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val unlocked = achievement.currentTier > 0
    val tint = when (achievement.definition.category) {
        "Cycling" -> Color(0xFF38CBFF)
        "Story" -> Color(0xFFFF8A5B)
        "Together" -> Color(0xFFF8C56F)
        else -> Color(0xFF7CF7B8)
    }
    Surface(color = if (unlocked) tint.copy(alpha = .14f) else Color(0x0DFFFFFF), shape = RoundedCornerShape(18.dp), modifier = modifier.aspectRatio(.86f).clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(color = if (unlocked) tint.copy(alpha = .2f) else Color(0x14FFFFFF), shape = CircleShape, modifier = Modifier.size(46.dp)) {
                Box(contentAlignment = Alignment.Center) { AchievementLucideIcon(achievement.definition.category, null, if (unlocked) tint else Color(0xFF8995A3), ambient = unlocked, modifier = Modifier.size(25.dp)) }
            }
            Text(achievement.definition.name, color = Color.White, style = MDTheme.type.caption, textAlign = TextAlign.Center)
            Text(if (unlocked) "TIER ${achievement.currentTier}" else "LOCKED", color = if (unlocked) tint else MDTheme.colors.textTertiary, style = MDTheme.type.caption)
            Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Color(0x243FFFFFF))) { Box(Modifier.fillMaxWidth(achievement.percentToNext).height(5.dp).background(tint)) }
        }
    }
}

@Composable
private fun AchievementScoreStrip(
    profile: GymProfile,
    partner: GymProfile?,
    view: AchievementView,
    weeklyProgress: GymWeeklyProgress,
    achievements: List<GymAchievementStatus>,
) {
    val discovered = achievements.count { it.currentTier > 0 }
    Surface(color = Color(0x0DFFFFFF), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${profile.totalXp} XP  ·  LEVEL ${GymProgression.levelFromXp(profile.totalXp)}", color = Color.White, style = MDTheme.type.settingTitle)
            Text("$discovered / ${achievements.size} achievements progressed", color = MDTheme.colors.textSecondary, style = MDTheme.type.caption)
            if (view == AchievementView.ME) Text("${weeklyProgress.completedWorkouts} / ${profile.progression.weeklyWorkoutTarget} workouts this week", color = Color(0xFFF8C56F), style = MDTheme.type.caption)
            if (view == AchievementView.TOGETHER && partner != null) Text("${profile.name}: ${profile.totalXp} XP  ·  ${partner.name}: ${partner.totalXp} XP", color = Color(0xFFF8C56F), style = MDTheme.type.caption)
        }
    }
}

@Composable
private fun AlmostThereCard(achievement: GymAchievementStatus, onClick: () -> Unit) {
    val target = achievement.nextTarget ?: return
    val remaining = target - achievement.current
    val tint = when (achievement.definition.category) {
        "Together" -> Color(0xFFF8C56F)
        "Cycling" -> Color(0xFF38CBFF)
        "Story" -> Color(0xFFFF8A5B)
        else -> Color(0xFF7CF7B8)
    }
    Surface(
        color = tint.copy(alpha = 0.11f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.width(300.dp).clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AchievementLucideIcon(achievement.definition.category, contentDescription = null, tint = tint, animate = true, ambient = true, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(achievement.definition.name, color = Color.White, style = MDTheme.type.settingTitle)
            }
            Text(achievement.definition.description, color = MDTheme.colors.textSecondary, style = MDTheme.type.caption)
            Text("${achievementValueLabel(GymAchievementStatus(achievement.definition, remaining))} remaining", color = tint, style = MDTheme.type.settingSubtitle.copy(fontWeight = FontWeight.Bold))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color(0x243FFFFFF))) {
                Box(
                    Modifier.fillMaxWidth(achievement.percentToNext)
                        .height(6.dp)
                        .background(tint),
                )
            }
            Text("${achievementValueLabel(achievement)} / ${achievementValueLabel(GymAchievementStatus(achievement.definition, target))}", color = Color.White, style = MDTheme.type.caption)
        }
    }
}

@Composable
private fun AchievementProgressCard(achievement: GymAchievementStatus, onClick: () -> Unit) {
    val next = achievement.nextTarget
    val unlocked = achievement.currentTier > 0
    Surface(color = if (unlocked) Color(0x147CF7B8) else Color(0x0DFFFFFF), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AchievementLucideIcon(achievement.definition.category, contentDescription = null, tint = if (unlocked) Color(0xFFF8C56F) else Color(0xFF8995A3), ambient = true, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp)); Text(achievement.definition.name, color = Color.White, style = MDTheme.type.settingTitle); Spacer(Modifier.weight(1f)); Text(if (unlocked) "TIER ${achievement.currentTier}" else achievement.definition.rarity.uppercase(), color = MDTheme.colors.textSecondary, style = MDTheme.type.caption)
            }
            Text(if (next == null) "All tiers complete" else "${achievementValueLabel(achievement)} / ${achievementValueLabel(GymAchievementStatus(achievement.definition, next))}", color = MDTheme.colors.textSecondary, style = MDTheme.type.settingSubtitle)
            Text(achievement.definition.description, color = Color.White, style = MDTheme.type.caption)
            next?.let { Text("Next reward +${achievement.nextReward ?: 0} XP", color = Color(0xFFF8C56F), style = MDTheme.type.caption) }
            Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(Color(0x243FFFFFF))) { Box(Modifier.fillMaxWidth(achievement.percentToNext).height(7.dp).background(if (unlocked) Color(0xFF7CF7B8) else Color(0xFF38CBFF))) }
        }
    }
}

@Composable
private fun AchievementProgressCard(achievement: GymAchievementProgress, unlocked: Boolean) {
    val next = achievement.nextTarget
    Surface(color = if (unlocked) Color(0x147CF7B8) else Color(0x0DFFFFFF), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.EmojiEvents, null, tint = if (unlocked) Color(0xFFF8C56F) else Color(0xFF8995A3), modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp)); Text(achievement.name, color = Color.White, style = MDTheme.type.settingTitle); Spacer(Modifier.weight(1f)); Text(if (unlocked) "TIER ${achievement.currentTier}" else "IN PROGRESS", color = MDTheme.colors.textSecondary, style = MDTheme.type.caption)
            }
            Text(if (next == null) "All tiers complete" else "${achievement.currentValue} / $next", color = MDTheme.colors.textSecondary, style = MDTheme.type.settingSubtitle)
            Text(if (unlocked) "Earned by: ${achievement.description}" else "To unlock: ${achievement.description}", color = Color.White, style = MDTheme.type.caption)
            Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(Color(0x243FFFFFF))) { Box(Modifier.fillMaxWidth(achievement.percentToNext).height(7.dp).background(if (unlocked) Color(0xFF7CF7B8) else Color(0xFF38CBFF))) }
        }
    }
}

@Composable
private fun StartWorkoutHero(
    profiles: List<GymProfile>,
    selectedPlayerIds: List<String>,
    onTogglePlayer: (String) -> Unit,
    onAddProfile: () -> Unit,
    onStart: () -> Unit,
) {
    Surface(
        color = Color(0x140FFFFFF),
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val stacked = maxWidth < 700.dp
                if (stacked) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SessionControlCopy(selectedPlayerCount = selectedPlayerIds.size)
                        Button(
                            onClick = onStart,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE8F7FF),
                                contentColor = Color(0xFF08131A),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Launch")
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        SessionControlCopy(
                            selectedPlayerCount = selectedPlayerIds.size,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(18.dp))
                        Button(
                            onClick = onStart,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE8F7FF),
                                contentColor = Color(0xFF08131A),
                            ),
                        ) {
                            Text("Launch")
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("ACTIVE PROFILES", color = MDTheme.colors.textTertiary, style = MDTheme.type.caption)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                profiles.forEach { profile ->
                    val selected = profile.id in selectedPlayerIds
                    Surface(
                        color = if (selected) Color(profile.accentColorArgb).copy(alpha = 0.2f) else Color(0x121FFFFFF),
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) Color(profile.accentColorArgb) else Color.White.copy(alpha = 0.08f),
                        ),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.clickable { onTogglePlayer(profile.id) },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(profile.accentColorArgb).copy(alpha = if (selected) 1f else 0.5f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(profile.avatarLabel, color = Color.Black, style = MDTheme.type.caption)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(profile.name, color = Color.White, style = MDTheme.type.settingTitle)
                                Text(
                                    if (selected) {
                                        "Active now  /  Lvl ${profile.totalXp / 500 + 1}  /  ${profile.totalWorkouts} sessions"
                                    } else {
                                        "Tap to activate  /  Lvl ${profile.totalXp / 500 + 1}"
                                    },
                                    color = if (selected) Color(profile.accentColorArgb) else MDTheme.colors.textSecondary,
                                    style = MDTheme.type.caption,
                                )
                            }
                        }
                    }
                }
                Surface(
                    color = Color(0x101FFFFFF),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.clickable { onAddProfile() },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("+", color = Color(0xFF7CF7B8), style = MDTheme.type.settingTitle)
                        Spacer(Modifier.width(8.dp))
                        Text("Add Profile", color = Color.White, style = MDTheme.type.settingTitle)
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionControlCopy(
    selectedPlayerCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text("SESSION CONTROL", color = MDTheme.colors.textTertiary, style = MDTheme.type.caption)
        Spacer(Modifier.height(8.dp))
        Text(
            "START WORKOUT",
            color = Color.White,
            style = MDTheme.type.clock.copy(fontSize = MDTheme.type.clock.fontSize * 0.25f, fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (selectedPlayerCount == 1) {
                "Launch a workout for the selected profile and keep every weekly goal and stat tied to that training history."
            } else {
                "Launch a shared workout for the active profiles and keep both dashboards synced to the sessions you finish together."
            },
            color = MDTheme.colors.textSecondary,
            style = MDTheme.type.settingSubtitle,
        )
    }
}

@Composable
private fun LaunchFocusMeta(
    activeProfileCount: Int,
    workoutType: GymWorkoutType,
    connectedDeviceCount: Int,
) {
    Surface(
        color = Color(0x110FFFFFF),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        BoxWithConstraints(Modifier.padding(18.dp)) {
            val compact = maxWidth < 700.dp
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("SESSION STAGING", color = Color.White, style = MDTheme.type.settingTitle)
                    Text(
                        "$activeProfileCount active ${if (activeProfileCount == 1) "profile" else "profiles"} / $connectedDeviceCount connected devices / ${workoutType.displayLabel}",
                        color = MDTheme.colors.textSecondary,
                        style = MDTheme.type.settingSubtitle,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("SESSION STAGING", color = Color.White, style = MDTheme.type.settingTitle, modifier = Modifier.weight(1f))
                    FilterChip(
                        label = "$activeProfileCount ${if (activeProfileCount == 1) "Profile" else "Profiles"}",
                        selected = true,
                        onClick = {},
                    )
                    FilterChip(
                        label = "$connectedDeviceCount Devices",
                        selected = false,
                        onClick = {},
                    )
                    FilterChip(
                        label = workoutType.displayLabel,
                        selected = false,
                        onClick = {},
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileDetailSheet(
    profile: GymProfile,
    wearableHealth: GymWearableHealthSnapshot,
    onRefreshWearableHealth: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        ageYears: Int?,
        weightKg: Double?,
        heightCm: Int?,
        bodyFatPercent: Double?,
        healthSource: String?,
        healthConnectionStatus: String?,
    ) -> Unit,
) {
    var name by rememberSaveable(profile.id) { mutableStateOf(profile.name) }
    var ageText by rememberSaveable(profile.id) { mutableStateOf(profile.ageYears?.toString().orEmpty()) }
    var weightText by rememberSaveable(profile.id) { mutableStateOf(profile.weightKg?.toString().orEmpty()) }
    var heightText by rememberSaveable(profile.id) { mutableStateOf(profile.heightCm?.toString().orEmpty()) }
    var bodyFatText by rememberSaveable(profile.id) { mutableStateOf(profile.bodyFatPercent?.toString().orEmpty()) }
    var healthSource by rememberSaveable(profile.id) { mutableStateOf(profile.healthSource ?: "Manual") }
    var connectionStatus by rememberSaveable(profile.id) { mutableStateOf(profile.healthConnectionStatus ?: "Disconnected") }
    LaunchedEffect(profile.id, healthSource) {
        if (healthSource == "Samsung Health") onRefreshWearableHealth()
    }

    Surface(
        color = Color(0xF50A1016),
        modifier = Modifier.fillMaxSize(),
    ) {
        val scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(profile.accentColorArgb)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(profile.avatarLabel, color = Color.Black, style = MDTheme.type.caption)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("PROFILE", color = MDTheme.colors.textTertiary, style = MDTheme.type.caption)
                    Text(profile.name, color = Color.White, style = MDTheme.type.sectionTitle.copy(fontWeight = FontWeight.Bold))
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }

            Surface(color = Color(0x110FFFFFF), shape = RoundedCornerShape(24.dp)) {
                BoxWithConstraints(Modifier.padding(18.dp)) {
                    val wide = maxWidth >= 760.dp
                    if (wide) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            ProfileEditMetric("Total XP", DecimalFormat("#,##0").format(profile.totalXp), Modifier.weight(1f))
                            ProfileEditMetric("Workouts", profile.totalWorkouts.toString(), Modifier.weight(1f))
                            ProfileEditMetric("Streak", "${profile.streakDays} days", Modifier.weight(1f))
                            ProfileEditMetric("Health", connectionStatus, Modifier.weight(1f))
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                ProfileEditMetric("Total XP", DecimalFormat("#,##0").format(profile.totalXp), Modifier.weight(1f))
                                ProfileEditMetric("Workouts", profile.totalWorkouts.toString(), Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                ProfileEditMetric("Streak", "${profile.streakDays} days", Modifier.weight(1f))
                                ProfileEditMetric("Health", connectionStatus, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            StreakFlame(profile.progression.currentWeeklyStreak)

            val profileAchievements = GymProgression.achievementProgress(profile.totalWorkouts, profile.progression.lifetimeMinutes)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("ACHIEVEMENTS", color = Color.White, style = MDTheme.type.settingTitle)
                Text("Earned badges and your closest next unlocks.", color = MDTheme.colors.textSecondary, style = MDTheme.type.settingSubtitle)
                profileAchievements.forEach { achievement -> AchievementProgressCard(achievement, achievement.currentTier > 0) }
            }

            Surface(color = Color(0x0FFFFFFF), shape = RoundedCornerShape(24.dp)) {
                BoxWithConstraints(Modifier.padding(18.dp)) {
                    val wide = maxWidth >= 760.dp
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("PERSONAL DETAILS", color = Color.White, style = MDTheme.type.settingTitle)
                        if (wide) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    label = { Text("Name") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1.4f),
                                )
                                OutlinedTextField(
                                    value = ageText,
                                    onValueChange = { ageText = it.filter(Char::isDigit) },
                                    label = { Text("Age") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(0.6f),
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = weightText,
                                    onValueChange = { weightText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                    label = { Text("Weight (kg)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = heightText,
                                    onValueChange = { heightText = it.filter(Char::isDigit) },
                                    label = { Text("Height (cm)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = bodyFatText,
                                    onValueChange = { bodyFatText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                    label = { Text("Body Fat %") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        } else {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = ageText,
                                onValueChange = { ageText = it.filter(Char::isDigit) },
                                label = { Text("Age") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = weightText,
                                onValueChange = { weightText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                label = { Text("Weight (kg)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = heightText,
                                onValueChange = { heightText = it.filter(Char::isDigit) },
                                label = { Text("Height (cm)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = bodyFatText,
                                onValueChange = { bodyFatText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                label = { Text("Body Fat %") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            Surface(color = Color(0x0FFFFFFF), shape = RoundedCornerShape(24.dp)) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("HEALTH DATA", color = Color.White, style = MDTheme.type.settingTitle)
                    Text(
                        "Choose the source you want this profile to sync from. Android-native real-time feeds typically come through Health Connect or a device vendor.",
                        color = MDTheme.colors.textSecondary,
                        style = MDTheme.type.settingSubtitle,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        listOf("Samsung Health", "Health Connect", "Fitbit", "Garmin", "Polar", "Apple Health Relay", "Manual").forEach { source ->
                            FilterChip(
                                label = source,
                                selected = healthSource == source,
                                onClick = { healthSource = source },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        listOf("Disconnected", "Requested", "Connected").forEach { state ->
                            FilterChip(
                                label = state,
                                selected = connectionStatus == state,
                                onClick = { connectionStatus = state },
                            )
                        }
                    }
                    if (healthSource == "Samsung Health") {
                        SamsungWearableSummary(
                            snapshot = wearableHealth,
                            onRefresh = onRefreshWearableHealth,
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x161FFFFFF), contentColor = Color.White),
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        onSave(
                            name.trim(),
                            ageText.toIntOrNull(),
                            weightText.toDoubleOrNull(),
                            heightText.toIntOrNull(),
                            bodyFatText.toDoubleOrNull(),
                            healthSource,
                            connectionStatus,
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8F7FF), contentColor = Color(0xFF08131A)),
                ) {
                    Text("Save Profile")
                }
            }
        }
    }
}

@Composable
private fun SamsungWearableSummary(
    snapshot: GymWearableHealthSnapshot,
    onRefresh: () -> Unit,
) {
    val updated = snapshot.updatedAtEpochMs?.let { SimpleDateFormat("h:mm a", Locale.US).format(Date(it)) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("GALAXY HEALTH", color = Color.White, style = MDTheme.type.settingTitle)
                Text(snapshot.status, color = MDTheme.colors.textSecondary, style = MDTheme.type.caption)
            }
            TextButton(onClick = onRefresh) { Text("Refresh") }
        }
        val activity = listOf(
            "LIVE HR" to snapshot.latestHeartRate?.let { "$it BPM" },
            "RESTING HR" to snapshot.restingHeartRate?.let { "$it BPM" },
            "STEPS TODAY" to snapshot.stepsToday?.let { DecimalFormat("#,##0").format(it) },
            "ACTIVE CAL" to snapshot.activeCaloriesToday?.let { "$it kcal" },
            "DISTANCE" to snapshot.distanceTodayKm?.let(::formatDistance),
            "WORKOUTS (7D)" to snapshot.workoutsThisWeek?.toString(),
        )
        WearableMetricGrid(activity)
        Text("SLEEP", color = Color.White, style = MDTheme.type.settingTitle)
        val sleepValue = snapshot.sleepMinutes?.let { "${it / 60}h ${it % 60}m" }
        WearableMetricGrid(
            listOf(
                "LAST SLEEP" to sleepValue,
                "DEEP" to snapshot.sleepDeepMinutes?.let { "${it}m" },
                "REM" to snapshot.sleepRemMinutes?.let { "${it}m" },
                "LIGHT" to snapshot.sleepLightMinutes?.let { "${it}m" },
            ),
        )
        Text("BODY & VITALS", color = Color.White, style = MDTheme.type.settingTitle)
        WearableMetricGrid(
            listOf(
                "OXYGEN" to snapshot.bloodOxygenPercent?.let { "${DecimalFormat("0.#").format(it)}%" },
                "WEIGHT" to snapshot.bodyWeightKg?.let { "${DecimalFormat("0.#").format(it)} kg" },
                "BODY FAT" to snapshot.bodyFatPercent?.let { "${DecimalFormat("0.#").format(it)}%" },
                "LAST WORKOUT" to snapshot.lastWorkoutLabel,
            ),
        )
        updated?.let { Text("Samsung Health data updated $it", color = MDTheme.colors.textTertiary, style = MDTheme.type.caption) }
        Text("Metrics appear only when Samsung Health shares them through Health Connect.", color = MDTheme.colors.textTertiary, style = MDTheme.type.caption)
    }
}

@Composable
private fun WearableMetricGrid(items: List<Pair<String, String?>>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 760.dp) 4 else 2
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { (label, value) -> ProfileEditMetric(label, value ?: "Not shared", Modifier.weight(1f)) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ProfileEditMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(color = Color(0x0FFFFFFF), shape = RoundedCornerShape(18.dp), modifier = modifier) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 16.dp)) {
            Text(label, color = MDTheme.colors.textTertiary, style = MDTheme.type.caption)
            Spacer(Modifier.height(8.dp))
            Text(value, color = Color.White, style = MDTheme.type.body.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

private data class OnDemandCategoryTile(
    val title: String,
    val accent: List<Color>,
)

private data class OnDemandWorkoutCard(
    val title: String,
    val coach: String,
    val dateLabel: String,
    val durationLabel: String,
    val difficultyLabel: String,
    val focusLabel: String,
    val badgeLabel: String,
    val accent: List<Color>,
    val workoutType: GymWorkoutType,
)

private val onDemandCategories = listOf(
    OnDemandCategoryTile("Strength", listOf(Color(0xFF203640), Color(0xFF101A1E))),
    OnDemandCategoryTile("Cool Down", listOf(Color(0xFF2A3A2D), Color(0xFF101411))),
    OnDemandCategoryTile("Yoga", listOf(Color(0xFF293541), Color(0xFF10161A))),
    OnDemandCategoryTile("Pilates", listOf(Color(0xFF43333B), Color(0xFF171215))),
    OnDemandCategoryTile("Barre", listOf(Color(0xFF3A2D26), Color(0xFF17120F))),
    OnDemandCategoryTile("Mobility", listOf(Color(0xFF2A393A), Color(0xFF101616))),
)

private val onDemandWorkouts = listOf(
    OnDemandWorkoutCard(
        title = "Ladder Lift 20 - Upper Body",
        coach = "Sam Jackson",
        dateLabel = "03/19/2025",
        durationLabel = "20 mins",
        difficultyLabel = "Intermediate",
        focusLabel = "Upper Body",
        badgeLabel = "New",
        accent = listOf(Color(0xFF4FA5FF), Color(0xFFBA7BFF), Color(0xFFF9A66A)),
        workoutType = GymWorkoutType.STRENGTH,
    ),
    OnDemandWorkoutCard(
        title = "Nightly Burn 20 - Lower Body",
        coach = "Brandon Reed",
        dateLabel = "03/18/2025",
        durationLabel = "20 mins",
        difficultyLabel = "Intermediate",
        focusLabel = "Lower Body",
        badgeLabel = "Encore",
        accent = listOf(Color(0xFFED74B2), Color(0xFF7C88FF), Color(0xFFF49761)),
        workoutType = GymWorkoutType.STRENGTH,
    ),
    OnDemandWorkoutCard(
        title = "Yoga 20 - Vinyasa",
        coach = "Ryn Tucker",
        dateLabel = "03/19/2025",
        durationLabel = "20 mins",
        difficultyLabel = "Flow",
        focusLabel = "Mobility",
        badgeLabel = "Workout",
        accent = listOf(Color(0xFF7BE2C0), Color(0xFF4EA3A5), Color(0xFF223E53)),
        workoutType = GymWorkoutType.HYBRID,
    ),
)

@Composable
private fun OnDemandBrowserSection(
    categories: List<OnDemandCategoryTile>,
    workouts: List<OnDemandWorkoutCard>,
    activeProfileCount: Int,
    onSelectWorkoutType: (GymWorkoutType) -> Unit,
    onStart: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("WORKOUTS", color = Color.White, style = MDTheme.type.settingTitle)
            Spacer(Modifier.weight(1f))
            Text("$activeProfileCount active", color = MDTheme.colors.textSecondary, style = MDTheme.type.caption)
        }
        if (categories.isEmpty() && workouts.isEmpty()) {
            EmptyWorkoutLibraryCard(
                title = "No workouts in this lane",
                body = "Switch the badges above to browse another lane or jump into the exercise library below.",
            )
            return@Column
        }
        categories.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { category ->
                    Surface(
                        color = Color.Transparent,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(0.93f)
                            .clickable {
                                onSelectWorkoutType(
                                    when (category.title) {
                                        "Strength" -> GymWorkoutType.STRENGTH
                                        "Mobility", "Cool Down", "Yoga", "Pilates", "Barre" -> GymWorkoutType.HYBRID
                                        else -> GymWorkoutType.FREE_WORKOUT
                                    },
                                )
                            },
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(category.accent),
                                    RoundedCornerShape(22.dp),
                                )
                                .padding(18.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x1AFFFFFF)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = when (category.title) {
                                        "Strength" -> Icons.Filled.FitnessCenter
                                        "Cool Down", "Yoga", "Pilates", "Barre", "Mobility" -> Icons.Filled.Sensors
                                        else -> Icons.Filled.Timer
                                    },
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Text(
                                category.title,
                                color = Color.White,
                                style = MDTheme.type.body.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.align(Alignment.BottomStart),
                            )
                        }
                    }
                }
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        if (workouts.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("FEATURED CLASSES", color = Color.White, style = MDTheme.type.settingTitle)
        }
        workouts.forEachIndexed { index, workout ->
            val featured = index == 0
            Surface(
                color = Color(0x0FFFFFFF),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onSelectWorkoutType(workout.workoutType)
                        onStart()
                    },
            ) {
                if (featured) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.76f)
                                .background(
                                    Brush.linearGradient(workout.accent),
                                    RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                                )
                                .padding(18.dp),
                        ) {
                            Surface(
                                color = Color(0xE6E8FFFA),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(
                                    workout.badgeLabel,
                                    color = Color(0xFF0C1720),
                                    style = MDTheme.type.caption,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                            Text(
                                workout.title,
                                color = Color.White,
                                style = MDTheme.type.sectionTitle.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.align(Alignment.BottomStart),
                            )
                        }
                        Column(Modifier.padding(18.dp)) {
                            Text(
                                "${workout.dateLabel}  •  ${workout.durationLabel}  •  ${workout.difficultyLabel}",
                                color = MDTheme.colors.textSecondary,
                                style = MDTheme.type.caption,
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "with ${workout.coach}",
                                color = Color.White,
                                style = MDTheme.type.body.copy(fontWeight = FontWeight.SemiBold),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Start this workout with full-screen coaching, mirror telemetry, score events, and player-ready HUD overlays.",
                                color = MDTheme.colors.textSecondary,
                                style = MDTheme.type.settingSubtitle,
                            )
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                FilterChip(label = workout.focusLabel, selected = true, onClick = {})
                                FilterChip(label = "Bluetooth Audio", selected = false, onClick = {})
                                FilterChip(label = "Heart Rate", selected = false, onClick = {})
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(0.95f)
                                .aspectRatio(1.5f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Brush.linearGradient(workout.accent)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.PlayCircleFilled,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(34.dp),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1.35f)) {
                            Text(workout.title, color = Color.White, style = MDTheme.type.settingTitle)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "${workout.dateLabel}  •  ${workout.difficultyLabel}",
                                color = MDTheme.colors.textSecondary,
                                style = MDTheme.type.caption,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("with ${workout.coach}", color = Color.White, style = MDTheme.type.settingSubtitle)
                            Spacer(Modifier.height(10.dp))
                            FilterChip(label = workout.focusLabel, selected = true, onClick = {})
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceStatusStrip(
    devices: List<FitnessDeviceSnapshot>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onConnectDevice: (String) -> Unit,
    onDisconnectDevice: (String) -> Unit,
    onCycleAssignment: (String) -> Unit,
    profiles: List<GymProfile>,
) {
    if (devices.isEmpty()) {
        return
    }
    Surface(
        color = Color(0x110FFFFFF),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("CONNECTED", color = MDTheme.colors.textTertiary, style = MDTheme.type.caption)
                Spacer(Modifier.weight(1f))
                Text(
                    "${devices.count { it.state != FitnessConnectionState.DISCONNECTED }} devices",
                    color = Color.White,
                    style = MDTheme.type.settingSubtitle,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                devices.take(3).forEach { device ->
                    DeviceDot(device = device, modifier = Modifier.weight(1f))
                }
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    devices.forEach { device ->
                        val assignedName = profiles.firstOrNull { it.id == device.assignedPlayerId }?.name ?: "Unassigned"
                        Surface(color = Color(0x100FFFFFF), shape = RoundedCornerShape(18.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(device.displayName, color = Color.White, style = MDTheme.type.settingTitle)
                                    Text(
                                        "${device.state.name.lowercase().replaceFirstChar { it.uppercase() }} • $assignedName",
                                        color = MDTheme.colors.textSecondary,
                                        style = MDTheme.type.settingSubtitle,
                                    )
                                    device.errorMessage?.let {
                                        Text(it, color = Color(0xFFFF9D74), style = MDTheme.type.caption)
                                    }
                                }
                                FilterChip(
                                    label = "Assign",
                                    selected = device.assignedPlayerId != null,
                                    onClick = { onCycleAssignment(device.deviceId) },
                                )
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (device.state == FitnessConnectionState.DISCONNECTED) {
                                            onConnectDevice(device.deviceId)
                                        } else {
                                            onDisconnectDevice(device.deviceId)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (device.state == FitnessConnectionState.DISCONNECTED) Color(0xFF183746) else Color(0xFF311816),
                                        contentColor = Color.White,
                                    ),
                                ) {
                                    Text(if (device.state == FitnessConnectionState.DISCONNECTED) "Connect" else "Reconnect")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceDot(device: FitnessDeviceSnapshot, modifier: Modifier = Modifier) {
    val color = when (device.state) {
        FitnessConnectionState.DISCONNECTED -> MDTheme.colors.textTertiary
        FitnessConnectionState.ERROR -> Color(0xFFFF7C68)
        else -> Color(0xFF7CF7B8)
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(device.displayName, color = Color.White, style = MDTheme.type.settingTitle)
            val stat = when (device.kind) {
                FitnessDeviceKind.HEART_RATE -> "${device.lastTelemetry?.heartRate ?: "--"} BPM"
                else -> device.subtitle
            }
            Text(stat, color = MDTheme.colors.textSecondary, style = MDTheme.type.caption)
        }
    }
}

@Composable
private fun WeeklyGoalSection(progress: GymWeeklyProgress) {
    Surface(color = Color(0x0FFFFFFF), shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp)) {
            Text("WEEKLY GOAL", color = Color.White, style = MDTheme.type.settingTitle)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { index, label ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(label, color = MDTheme.colors.textTertiary, style = MDTheme.type.caption)
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (progress.days[index]) Color(0xFF7CF7B8) else Color(0x1FFFFFFF)),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("${progress.completedWorkouts} / ${progress.targetWorkouts} workouts", color = Color.White, style = MDTheme.type.body)
            Spacer(Modifier.height(10.dp))
            val ratio = (progress.completedWorkouts / progress.targetWorkouts.toFloat()).coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color(0x14FFFFFF)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(ratio)
                        .height(10.dp)
                        .background(
                            Brush.horizontalGradient(listOf(Color(0xFF7CF7B8), Color(0xFF38CBFF))),
                        ),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "${progress.weeklyMinutes} minutes • ${progress.weeklyCalories} cal • ${DecimalFormat("#,##0").format(progress.weeklyVolumeKg)} kg • ${formatDistance(progress.weeklyDistanceKm)}",
                color = MDTheme.colors.textSecondary,
                style = MDTheme.type.settingSubtitle,
            )
        }
    }
}

@Composable
private fun DashboardStatsSection(stats: GymDashboardStats) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("PERSONAL STATS", color = Color.White, style = MDTheme.type.settingTitle)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile("WORKOUTS", stats.workouts.toString(), modifier = Modifier.weight(1f))
            StatTile("TIME", "${stats.timeMinutes / 60}h ${stats.timeMinutes % 60}m", modifier = Modifier.weight(1f))
            StatTile("CALORIES", DecimalFormat("#,##0").format(stats.calories), modifier = Modifier.weight(1f))
            StatTile("STREAK", "${stats.streakDays} days", modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile("DISTANCE", formatDistance(stats.distanceKm), modifier = Modifier.weight(1f))
            StatTile("BEST POWER", "${stats.bestPowerWatts}W", modifier = Modifier.weight(1f))
            StatTile("VOLUME", DecimalFormat("#,##0").format(stats.strengthVolumeKg) + " kg", modifier = Modifier.weight(1f))
            StatTile("REPS", DecimalFormat("#,##0").format(stats.totalRepetitions), modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(color = Color(0x0FFFFFFF), shape = RoundedCornerShape(20.dp), modifier = modifier) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 16.dp)) {
            Text(label, color = MDTheme.colors.textTertiary, style = MDTheme.type.caption)
            Spacer(Modifier.height(8.dp))
            Text(value, color = Color.White, style = MDTheme.type.body.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
private fun ProfilesDashboardCards(
    profiles: List<GymProfileDashboardSnapshot>,
    onOpenProfile: (String) -> Unit,
) {
    if (profiles.isEmpty()) {
        Surface(color = Color(0x0FFFFFFF), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                "Select a profile above to load weekly goals and personal stats.",
                color = MDTheme.colors.textSecondary,
                style = MDTheme.type.settingSubtitle,
                modifier = Modifier.padding(18.dp),
            )
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        profiles.forEach { snapshot ->
            Surface(color = Color(0x0BFFFFFF), shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenProfile(snapshot.profile.id) },
                    ) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(snapshot.profile.accentColorArgb).copy(alpha = 0.9f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(snapshot.profile.avatarLabel, color = Color.Black, style = MDTheme.type.caption)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(snapshot.profile.name.uppercase(), color = Color.White, style = MDTheme.type.settingTitle)
                            Text(
                                profileSummaryLine(snapshot.profile),
                                color = MDTheme.colors.textSecondary,
                                style = MDTheme.type.caption,
                            )
                        }
                        Text("Edit", color = Color(snapshot.profile.accentColorArgb), style = MDTheme.type.caption)
                    }
                    ActiveAchievementSection(achievements = snapshot.activeAchievements)
                    ProfileWeeklyGoalCard(progress = snapshot.weeklyProgress)
                    AdaptiveProfileStatsSection(snapshot = snapshot)
                }
            }
        }
    }
}

private fun profileSummaryLine(profile: GymProfile): String {
    val facts = listOfNotNull(
        profile.ageYears?.let { "$it yr" },
        profile.weightKg?.let { "${DecimalFormat("0.#").format(it)} kg" },
        profile.heightCm?.let { "$it cm" },
        profile.bodyFatPercent?.let { "${DecimalFormat("0.#").format(it)}% fat" },
        profile.healthSource,
    )
    return if (facts.isNotEmpty()) {
        facts.joinToString(" / ")
    } else {
        "${profile.totalWorkouts} sessions / ${profile.totalXp} xp"
    }
}

@Composable
private fun AdaptiveProfileStatsSection(snapshot: GymProfileDashboardSnapshot) {
    val stats = snapshot.dashboardStats
    val items = listOf(
        "WORKOUTS" to stats.workouts.toString(),
        "TIME" to "${stats.timeMinutes / 60}h ${stats.timeMinutes % 60}m",
        "CALORIES" to DecimalFormat("#,##0").format(stats.calories),
        "STREAK" to "${stats.streakDays} days",
        "HEART RATE" to snapshot.heartRateSummary,
        "DISTANCE" to formatDistance(stats.distanceKm),
        "BEST POWER" to "${stats.bestPowerWatts}W",
        "VOLUME" to DecimalFormat("#,##0").format(stats.strengthVolumeKg) + " kg",
        "REPS" to DecimalFormat("#,##0").format(stats.totalRepetitions),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("PERSONAL STATS", color = Color.White, style = MDTheme.type.settingTitle)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val columns = when {
                maxWidth >= 1_080.dp -> 5
                maxWidth >= 760.dp -> 3
                else -> 2
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items.chunked(columns).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        rowItems.forEach { (label, value) ->
                            StatTile(label, value, modifier = Modifier.weight(1f))
                        }
                        repeat(columns - rowItems.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedProfilesDashboardSection(
    profiles: List<GymProfileDashboardSnapshot>,
) {
    if (profiles.isEmpty()) {
        Surface(color = Color(0x0FFFFFFF), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                "Select a profile above to load weekly goals and personal stats.",
                color = MDTheme.colors.textSecondary,
                style = MDTheme.type.settingSubtitle,
                modifier = Modifier.padding(18.dp),
            )
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        profiles.forEach { snapshot ->
            Surface(color = Color(0x0BFFFFFF), shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(snapshot.profile.accentColorArgb).copy(alpha = 0.9f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(snapshot.profile.avatarLabel, color = Color.Black, style = MDTheme.type.caption)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(snapshot.profile.name.uppercase(), color = Color.White, style = MDTheme.type.settingTitle)
                            Text(
                                "${snapshot.profile.totalWorkouts} sessions / ${snapshot.profile.totalXp} xp",
                                color = MDTheme.colors.textSecondary,
                                style = MDTheme.type.caption,
                            )
                        }
                    }
                    ActiveAchievementSection(achievements = snapshot.activeAchievements)
                    ProfileWeeklyGoalCard(progress = snapshot.weeklyProgress)
                    ProfileStatsSection(stats = snapshot.dashboardStats)
                }
            }
        }
    }
}

@Composable
private fun ProfileWeeklyGoalCard(progress: GymWeeklyProgress) {
    Surface(color = Color(0x0FFFFFFF), shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp)) {
            Text("WEEKLY GOAL", color = Color.White, style = MDTheme.type.settingTitle)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { index, label ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(label, color = MDTheme.colors.textTertiary, style = MDTheme.type.caption)
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (progress.days[index]) Color(0xFF7CF7B8) else Color(0x1FFFFFFF)),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("${progress.completedWorkouts} / ${progress.targetWorkouts} workouts", color = Color.White, style = MDTheme.type.body)
            Spacer(Modifier.height(10.dp))
            val ratio = (progress.completedWorkouts / progress.targetWorkouts.toFloat()).coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color(0x14FFFFFF)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(ratio)
                        .height(10.dp)
                        .background(Brush.horizontalGradient(listOf(Color(0xFF7CF7B8), Color(0xFF38CBFF)))),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "${progress.weeklyMinutes} minutes / ${progress.weeklyCalories} cal / ${DecimalFormat("#,##0").format(progress.weeklyVolumeKg)} kg / ${formatDistance(progress.weeklyDistanceKm)}",
                color = MDTheme.colors.textSecondary,
                style = MDTheme.type.settingSubtitle,
            )
        }
    }
}

@Composable
private fun ProfileStatsSection(stats: GymDashboardStats) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("PERSONAL STATS", color = Color.White, style = MDTheme.type.settingTitle)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile("WORKOUTS", stats.workouts.toString(), modifier = Modifier.weight(1f))
            StatTile("TIME", "${stats.timeMinutes / 60}h ${stats.timeMinutes % 60}m", modifier = Modifier.weight(1f))
            StatTile("CALORIES", DecimalFormat("#,##0").format(stats.calories), modifier = Modifier.weight(1f))
            StatTile("STREAK", "${stats.streakDays} days", modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile("DISTANCE", formatDistance(stats.distanceKm), modifier = Modifier.weight(1f))
            StatTile("BEST POWER", "${stats.bestPowerWatts}W", modifier = Modifier.weight(1f))
            StatTile("VOLUME", DecimalFormat("#,##0").format(stats.strengthVolumeKg) + " kg", modifier = Modifier.weight(1f))
            StatTile("REPS", DecimalFormat("#,##0").format(stats.totalRepetitions), modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ExerciseCatalogHeader(count: Int, queueCount: Int, favoriteCount: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("EXERCISE LIBRARY", color = Color.White, style = MDTheme.type.settingTitle)
        Text(
            "$count exercises / $queueCount in your workout / $favoriteCount favourites.",
            color = MDTheme.colors.textSecondary,
            style = MDTheme.type.settingSubtitle,
        )
    }
}

@Composable
private fun ExerciseCatalogRow(entry: GymExerciseCatalogEntry, onClick: () -> Unit) {
    val videoCount = entry.videos.size
    val libraryGroup = entry.libraryGroup ?: entry.muscleGroups.firstOrNull()?.replace('_', ' ')
    val armSubgroup = entry.videos.firstOrNull()?.relativePath
        ?.split('/')
        ?.takeIf { it.size >= 3 && it[0] == "Arms" }
        ?.getOrNull(1)
    val subtitleParts = listOf(
        listOfNotNull(libraryGroup, armSubgroup).joinToString(" / ").ifBlank { null },
        entry.equipment.firstOrNull()?.replace('_', ' '),
        entry.sidedness,
    ).filterNotNull().filter { it.isNotBlank() }
    Surface(color = Color(0x0FFFFFFF), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Color(0x1438CBFF), shape = RoundedCornerShape(14.dp), modifier = Modifier.size(46.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    ExerciseLucideIcon(entryIconLabel(entry), null, Color(0xFF7DE6FF), animate = true, ambient = true, modifier = Modifier.size(24.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(entry.name, color = Color.White, style = MDTheme.type.settingTitle)
                if (subtitleParts.isNotEmpty()) {
                    Text(subtitleParts.joinToString(" / ") { it.replaceFirstChar { ch -> ch.uppercase() } }, color = MDTheme.colors.textSecondary, style = MDTheme.type.caption)
                }
                Text(
                    if (videoCount > 0) "$videoCount video${if (videoCount == 1) "" else "s"} available" else "No video assigned",
                    color = if (videoCount > 0) Color(0xFF7DE6FF) else MDTheme.colors.textTertiary,
                    style = MDTheme.type.caption,
                )
                if (entry.muscles.isNotEmpty()) Text(entry.muscles.take(4).joinToString(" / ") { it.replaceFirstChar { ch -> ch.uppercase() } }, color = MDTheme.colors.textTertiary, style = MDTheme.type.caption)
            }
        }
    }
}

@Composable
private fun ExerciseCatalogDetailPage(
    entry: GymExerciseCatalogEntry,
    isFavorite: Boolean,
    isQueued: Boolean,
    onResolveVideo: suspend (GymExerciseVideo) -> Result<String>,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val equipment = entry.equipment.firstOrNull()?.replace('_', ' ') ?: "bodyweight"
    val video = entry.videos.firstOrNull()
    var resolvedVideoUri by remember(entry.id) { mutableStateOf<String?>(null) }
    var videoError by remember(entry.id) { mutableStateOf<String?>(null) }
    var loadingVideo by remember(entry.id) { mutableStateOf(video != null) }
    LaunchedEffect(entry.id, video?.relativePath, video?.localUri) {
        if (video == null) {
            loadingVideo = false
            return@LaunchedEffect
        }
        loadingVideo = true
        onResolveVideo(video)
            .onSuccess { resolvedVideoUri = it }
            .onFailure { videoError = it.message ?: "Unable to load this exercise video." }
        loadingVideo = false
    }
    val targets = entry.muscles.ifEmpty { entry.muscleGroups }.take(4).joinToString(" · ") { it.replace('_', ' ').replaceFirstChar(Char::uppercase) }
    LazyColumn(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null); Spacer(Modifier.width(6.dp)); Text("Exercises") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onToggleFavorite) { Icon(Icons.Filled.Favorite, null, tint = if (isFavorite) Color(0xFFFF7A9E) else MDTheme.colors.textSecondary); Spacer(Modifier.width(6.dp)); Text(if (isFavorite) "Saved" else "Save") }
            }
        }
        item {
            Surface(color = Color(0x1738CBFF), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().height(360.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    when {
                        resolvedVideoUri != null -> AndroidView(
                            factory = { viewContext -> VideoView(viewContext).apply { setVideoURI(Uri.parse(resolvedVideoUri)); setOnPreparedListener { player -> player.isLooping = true; start() } } },
                            modifier = Modifier.fillMaxSize(),
                        )
                        loadingVideo -> CircularProgressIndicator(color = Color(0xFF7DE6FF))
                        video == null -> ExerciseLucideIcon(entryIconLabel(entry), null, Color(0xFF7DE6FF), animate = true, ambient = true, modifier = Modifier.size(86.dp))
                        else -> Text("Video could not load", color = Color(0xFFFFB4AB), style = MDTheme.type.caption)
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(entry.name, color = Color.White, style = MDTheme.type.sectionTitle.copy(fontWeight = FontWeight.Bold))
                Text(if (resolvedVideoUri != null) "EXERCISE DEMO" else "FORM GUIDE", color = Color(0xFF7DE6FF), style = MDTheme.type.caption)
                Text("Perform ${entry.name.lowercase()} with controlled form. Use ${equipment.replaceFirstChar(Char::uppercase)} and keep the effort focused on ${targets.ifBlank { "the intended muscle group" }}.", color = MDTheme.colors.textSecondary, style = MDTheme.type.body)
                if (targets.isNotBlank()) Text("TARGETS  $targets", color = MDTheme.colors.textTertiary, style = MDTheme.type.caption)
                if (videoError != null) Text("Video unavailable: $videoError", color = Color(0xFFFFB4AB), style = MDTheme.type.caption)
                if (video == null) Text("No video has been assigned to this exercise yet. The exercise icon is shown as a placeholder.", color = MDTheme.colors.textTertiary, style = MDTheme.type.caption)
            }
        }
        item {
            Button(onClick = onToggleQueue, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = if (isQueued) Color(0xFF213741) else Color(0xFFE8F7FF), contentColor = if (isQueued) Color.White else Color(0xFF06131A))) {
                Icon(if (isQueued) Icons.Filled.Favorite else Icons.Filled.Add, null)
                Spacer(Modifier.width(8.dp))
                Text(if (isQueued) "REMOVE FROM WORKOUT" else "ADD TO WORKOUT")
            }
        }
        item { Spacer(Modifier.height(120.dp)) }
    }
}

private fun entryIconLabel(entry: GymExerciseCatalogEntry): String = buildString {
    append(entry.name)
    append(' ')
    append(entry.equipment.joinToString(" "))
    append(' ')
    append(entry.muscleGroups.joinToString(" "))
}

@Composable
private fun ChallengeSection(
    challenges: List<GymChallengeDefinition>,
    selectedChallengeId: String?,
    onSelectChallenge: (String) -> Unit,
    onStart: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("CHALLENGES", color = Color.White, style = MDTheme.type.settingTitle)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.Leaderboard, contentDescription = null, tint = Color(0xFF7CF7B8))
        }
        challenges.forEach { challenge ->
            val selected = challenge.id == selectedChallengeId
            Surface(
                color = if (selected) Color(0x1438CBFF) else Color(0x0FFFFFFF),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectChallenge(challenge.id) },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(challenge.title.uppercase(), color = Color.White, style = MDTheme.type.settingTitle)
                        Spacer(Modifier.height(6.dp))
                        Text(challenge.subtitle, color = MDTheme.colors.textSecondary, style = MDTheme.type.settingSubtitle)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "${challenge.durationSeconds}s • ${challenge.difficultyLabel} • ${challenge.equipmentLabel}" +
                                (challenge.bestLabel?.let { " • PB $it" } ?: ""),
                            color = MDTheme.colors.textTertiary,
                            style = MDTheme.type.caption,
                        )
                    }
                    Button(
                        onClick = onStart,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selected) Color(0xFFE8F7FF) else Color(0x1FFFFFFF),
                            contentColor = if (selected) Color(0xFF06131A) else Color.White,
                        ),
                    ) {
                        Text("START")
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentSessionsSection(history: List<GymSessionRecord>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("RECENT", color = Color.White, style = MDTheme.type.settingTitle)
        if (history.isEmpty()) {
            Surface(color = Color(0x0FFFFFFF), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    "No completed gym sessions yet. Start with mock equipment and build your first weekly streak.",
                    color = MDTheme.colors.textSecondary,
                    style = MDTheme.type.settingSubtitle,
                    modifier = Modifier.padding(18.dp),
                )
            }
        } else {
            history.take(4).forEach { session ->
                Surface(color = Color(0x0FFFFFFF), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val icon = when (session.workoutType) {
                            GymWorkoutType.STRENGTH -> Icons.Filled.FitnessCenter
                            GymWorkoutType.CYCLING -> Icons.Filled.DirectionsBike
                            else -> Icons.Filled.Timer
                        }
                        Icon(icon, contentDescription = null, tint = Color(0xFF38CBFF))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                session.challengeTitle ?: session.workoutType.name.replace('_', ' '),
                                color = Color.White,
                                style = MDTheme.type.settingTitle,
                            )
                            Text(
                                "${formatDuration(session.durationSeconds)} • ${session.players.sumOf { it.score }} pts",
                                color = MDTheme.colors.textSecondary,
                                style = MDTheme.type.settingSubtitle,
                            )
                        }
                        Text(
                            DecimalFormat("#,##0").format(session.players.sumOf { it.metrics.calories }) + " cal",
                            color = MDTheme.colors.textTertiary,
                            style = MDTheme.type.caption,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionSetupSheet(
    uiState: GymUiState,
    onTogglePlayer: (String) -> Unit,
    onSelectWorkoutType: (GymWorkoutType) -> Unit,
    onSelectGoal: (GymTrainingGoal) -> Unit,
    onSelectLevel: (GymTrainingLevel) -> Unit,
    onToggleEquipment: (GymEquipmentOption) -> Unit,
    onToggleMuscle: (GymMuscleGroup) -> Unit,
    onSelectExerciseCount: (Int) -> Unit,
    onSelectDuration: (Int) -> Unit,
    onGoToStep: (GymGeneratorStep) -> Unit,
    onNextStep: () -> Unit,
    onPreviousStep: () -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    val generatorEnabled = uiState.selectedWorkoutType != GymWorkoutType.CHALLENGE &&
        uiState.selectedWorkoutType != GymWorkoutType.MULTIPLAYER &&
        uiState.selectedWorkoutType != GymWorkoutType.CYCLING
    Surface(
        color = Color(0xFF091017),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp)) {
            Text(
                if (generatorEnabled) "Workout selection" else "Session selection",
                color = Color.White,
                style = MDTheme.type.sectionTitle.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (generatorEnabled) {
                    "Build the session for ${uiState.activeProfileCount} active ${if (uiState.activeProfileCount == 1) "profile" else "profiles"} with your workout type, goal, level, equipment, and workout size."
                } else {
                    "Choose the workout mode for ${uiState.activeProfileCount} active ${if (uiState.activeProfileCount == 1) "profile" else "profiles"}."
                },
                color = MDTheme.colors.textSecondary,
                style = MDTheme.type.settingSubtitle,
            )
            if (generatorEnabled && uiState.exerciseCatalogCount > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "${uiState.exerciseCatalogCount} exercises available from your workout library.",
                    color = Color(0xFF7CF7B8),
                    style = MDTheme.type.caption,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                uiState.profiles.forEach { profile ->
                    val selected = profile.id in uiState.selectedPlayerIds
                    Surface(
                        color = if (selected) Color(profile.accentColorArgb).copy(alpha = 0.18f) else Color(0x120FFFFFF),
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onTogglePlayer(profile.id) },
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(profile.accentColorArgb).copy(alpha = if (selected) 1f else 0.45f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(profile.avatarLabel, color = Color.Black, style = MDTheme.type.caption)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(profile.name, color = Color.White, style = MDTheme.type.settingTitle)
                                Text("Level ${profile.totalXp / 500 + 1}", color = MDTheme.colors.textSecondary, style = MDTheme.type.caption)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            if (generatorEnabled) {
                GeneratorEquipmentStep(
                    selectedEquipment = uiState.generatorPreferences.equipment,
                    onToggleEquipment = onToggleEquipment,
                )
                Spacer(Modifier.height(16.dp))
                when (uiState.generatorStep) {
                    GymGeneratorStep.MUSCLES -> GeneratorMuscleStep(
                        selectedMuscles = uiState.generatorPreferences.muscleGroups,
                        onToggleMuscle = onToggleMuscle,
                        durationMinutes = uiState.generatorPreferences.durationMinutes,
                        onSelectDuration = onSelectDuration,
                    )
                    GymGeneratorStep.EXERCISE_COUNT -> GeneratorExerciseCountStep(
                        selectedCount = uiState.generatorPreferences.exerciseCount,
                        onSelectExerciseCount = onSelectExerciseCount,
                    )
                    GymGeneratorStep.PREVIEW -> GeneratorPreviewStep(
                        workout = uiState.generatedWorkout,
                    )
                }
                Spacer(Modifier.height(20.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x161FFFFFF), contentColor = Color.White),
                ) {
                    Text("Close")
                }
                if (generatorEnabled && uiState.generatorStep != GymGeneratorStep.PREVIEW) {
                    Button(
                        onClick = { onGoToStep(GymGeneratorStep.PREVIEW) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7A45), contentColor = Color(0xFF160803)),
                    ) { Text("Generate") }
                }
                if (generatorEnabled && uiState.generatorStep != GymGeneratorStep.MUSCLES) {
                    Button(
                        onClick = onPreviousStep,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x121FFFFFF), contentColor = Color.White),
                    ) {
                        Text("Back")
                    }
                }
                Button(
                    onClick = if (generatorEnabled && uiState.generatorStep != GymGeneratorStep.PREVIEW) onNextStep else onStart,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8F7FF), contentColor = Color(0xFF08131A)),
                ) {
                    Text(
                        when {
                            !generatorEnabled -> "Start session"
                            uiState.generatorStep == GymGeneratorStep.PREVIEW -> "Start workout"
                            else -> "Next"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun GeneratorStepRail(
    currentStep: GymGeneratorStep,
    onGoToStep: (GymGeneratorStep) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        GymGeneratorStep.entries.forEach { step ->
            FilterChip(
                label = step.displayLabel,
                selected = currentStep == step,
                onClick = { onGoToStep(step) },
            )
        }
    }
}

@Composable
private fun GeneratorGoalStep(
    selectedGoal: GymTrainingGoal,
    onSelectGoal: (GymTrainingGoal) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("What's your goal?", color = Color.White, style = MDTheme.type.settingTitle)
        GymTrainingGoal.entries.forEach { goal ->
            SelectionCard(
                title = goal.displayLabel,
                subtitle = goal.description,
                selected = selectedGoal == goal,
                onClick = { onSelectGoal(goal) },
            )
        }
    }
}

@Composable
private fun GeneratorLevelStep(
    selectedLevel: GymTrainingLevel,
    onSelectLevel: (GymTrainingLevel) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Choose your level", color = Color.White, style = MDTheme.type.settingTitle)
        GymTrainingLevel.entries.forEach { level ->
            SelectionCard(
                title = level.displayLabel,
                subtitle = level.description,
                selected = selectedLevel == level,
                onClick = { onSelectLevel(level) },
            )
        }
    }
}

@Composable
private fun GeneratorEquipmentStep(
    selectedEquipment: List<GymEquipmentOption>,
    onToggleEquipment: (GymEquipmentOption) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Equipment access", color = Color.White, style = MDTheme.type.settingTitle)
        Text(
            "Pick the gear your mirror zone actually has so the generated workout stays realistic.",
            color = MDTheme.colors.textSecondary,
            style = MDTheme.type.settingSubtitle,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            GymEquipmentOption.entries.forEach { option ->
                EquipmentFilterIcon(
                    label = option.displayLabel,
                    selected = option in selectedEquipment,
                    onClick = { onToggleEquipment(option) },
                )
            }
        }
    }
}

@Composable
private fun GeneratorMuscleStep(
    selectedMuscles: List<GymMuscleGroup>,
    onToggleMuscle: (GymMuscleGroup) -> Unit,
    durationMinutes: Int,
    onSelectDuration: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StaggeredSetupItem(0) { GeneratorDurationPicker(durationMinutes, onSelectDuration) }
        StaggeredSetupItem(1) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Choose your focus", color = Color.White, style = MDTheme.type.settingTitle)
                Text(
                    "Select the areas you want to train today. Leave all unselected for a balanced full-body workout.",
                    color = MDTheme.colors.textSecondary,
                    style = MDTheme.type.settingSubtitle,
                )
                GymMuscleGroup.entries.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { group ->
                            Box(Modifier.weight(1f)) {
                                SelectionCard(
                                    title = group.displayLabel,
                                    subtitle = if (group in selectedMuscles) "Selected" else "Tap to target",
                                    selected = group in selectedMuscles,
                                    onClick = { onToggleMuscle(group) },
                                )
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GeneratorDurationPicker(durationMinutes: Int, onSelectDuration: (Int) -> Unit) {
    var customPickerVisible by rememberSaveable { mutableStateOf(false) }
    val presets = listOf(30 to "30 min", 45 to "45 min", 60 to "1 hour", 90 to "1h 30")
    val customDuration = durationMinutes !in presets.map { it.first }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Choose your workout duration", color = Color.White, style = MDTheme.type.settingTitle)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            presets.forEach { (minutes, label) ->
                FilterChip(label = label, selected = durationMinutes == minutes, onClick = { onSelectDuration(minutes) })
            }
            FilterChip(
                label = if (customDuration) "Custom · ${formatDurationBadge(durationMinutes)}" else "Custom",
                selected = customDuration,
                onClick = { customPickerVisible = true },
            )
        }
    }
    if (customPickerVisible) {
        CustomDurationDialog(
            initialMinutes = durationMinutes,
            onDismiss = { customPickerVisible = false },
            onApply = { minutes -> onSelectDuration(minutes); customPickerVisible = false },
        )
    }
}

@Composable
private fun CustomDurationDialog(initialMinutes: Int, onDismiss: () -> Unit, onApply: (Int) -> Unit) {
    var hours by rememberSaveable { mutableStateOf((initialMinutes / 60).toString()) }
    var minutes by rememberSaveable { mutableStateOf((initialMinutes % 60).toString()) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = Color(0xFF111A22), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Custom workout time", color = Color.White, style = MDTheme.type.sectionTitle)
                Text("Set the session length; the value will appear on your Custom badge.", color = MDTheme.colors.textSecondary, style = MDTheme.type.settingSubtitle)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = hours, onValueChange = { hours = it.filter(Char::isDigit).take(2) }, label = { Text("Hours") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = minutes, onValueChange = { minutes = it.filter(Char::isDigit).take(2) }, label = { Text("Minutes") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(onClick = { onApply((hours.toIntOrNull() ?: 0).coerceIn(0, 12) * 60 + (minutes.toIntOrNull() ?: 0).coerceIn(0, 59)) }, modifier = Modifier.weight(1f)) { Text("Apply") }
                }
            }
        }
    }
}

private fun formatDurationBadge(totalMinutes: Int): String = when {
    totalMinutes < 60 -> "$totalMinutes min"
    totalMinutes % 60 == 0 -> "${totalMinutes / 60}h"
    else -> "${totalMinutes / 60}h ${totalMinutes % 60}m"
}

@Composable
private fun StaggeredSetupItem(index: Int, content: @Composable () -> Unit) {
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }
    val progress by animateFloatAsState(if (revealed) 1f else 0f, tween(durationMillis = 340, delayMillis = index * 80, easing = FastOutSlowInEasing), label = "setupStagger$index")
    Box(Modifier.graphicsLayer(alpha = progress, translationY = (1f - progress) * 18f)) { content() }
}

@Composable
private fun GeneratorExerciseCountStep(
    selectedCount: Int,
    onSelectExerciseCount: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Select number of exercises", color = Color.White, style = MDTheme.type.settingTitle)
        Text(
            "Shorter stacks feel more like quick on-demand sessions. Bigger stacks feel like a complete floor workout.",
            color = MDTheme.colors.textSecondary,
            style = MDTheme.type.settingSubtitle,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            listOf(4, 6, 8, 10).forEach { count ->
                FilterChip(
                    label = "$count exercises",
                    selected = selectedCount == count,
                    onClick = { onSelectExerciseCount(count) },
                )
            }
        }
    }
}

@Composable
private fun GeneratorPreviewStep(
    workout: GymGeneratedWorkoutPlan,
) {
    var selectedExercises by rememberSaveable(workout.title) { mutableStateOf(workout.exercises.map { it.title }.toSet()) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(color = Color(0x100FFFFFF), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Text("Preview", color = MDTheme.colors.textTertiary, style = MDTheme.type.caption)
                Spacer(Modifier.height(8.dp))
                Text(workout.title, color = Color.White, style = MDTheme.type.sectionTitle.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(6.dp))
                Text(workout.subtitle, color = MDTheme.colors.textSecondary, style = MDTheme.type.settingSubtitle)
                Spacer(Modifier.height(10.dp))
                Text(
                    "${workout.estimatedMinutes} mins • ${workout.targetedMuscles.joinToString(" • ")}",
                    color = Color(0xFF7CF7B8),
                    style = MDTheme.type.caption,
                )
                Spacer(Modifier.height(10.dp))
                Text(workout.focusSummary, color = Color.White, style = MDTheme.type.settingSubtitle)
            }
        }
        Text("Choose the exercises to include", color = MDTheme.colors.textTertiary, style = MDTheme.type.caption)
        workout.exercises.forEach { exercise ->
            val included = exercise.title in selectedExercises
            Surface(color = if (included) Color(0x1438CBFF) else Color(0x0DFFFFFF), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().clickable {
                selectedExercises = if (included) selectedExercises - exercise.title else selectedExercises + exercise.title
            }) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = included, onCheckedChange = { checked -> selectedExercises = if (checked) selectedExercises + exercise.title else selectedExercises - exercise.title })
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                    Text("${exercise.order}. ${exercise.title}", color = Color.White, style = MDTheme.type.settingTitle)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${exercise.focusLabel} • ${exercise.repScheme} • ${exercise.durationMinutes} min",
                        color = MDTheme.colors.textSecondary,
                        style = MDTheme.type.caption,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(exercise.coachingCue, color = Color.White, style = MDTheme.type.settingSubtitle)
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = if (selected) Color(0x1838CBFF) else Color(0x0DFFFFFF),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Color.White, style = MDTheme.type.settingTitle)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = MDTheme.colors.textSecondary, style = MDTheme.type.settingSubtitle)
        }
    }
}

@Composable
private fun ActiveSessionHud(
    uiState: GymUiState,
    onPauseResume: () -> Unit,
    onEnd: () -> Unit,
    onDiscard: () -> Unit,
    onDismissStatus: () -> Unit,
    freeRideVideoUri: String?,
    onSelectFreeRideVideo: () -> Unit,
) {
    val session = uiState.activeSession ?: return
    val configuration = LocalConfiguration.current
    val layoutTier = remember(configuration.screenWidthDp, configuration.screenHeightDp) {
        gymLayoutTier(configuration.screenWidthDp, configuration.screenHeightDp)
    }
    val configuredOrientation = DisplayOrientationMode.fromStorageKey(uiState.displayOrientationMode)
    val shouldUseLandscapeHud = when (configuredOrientation) {
        DisplayOrientationMode.LANDSCAPE, DisplayOrientationMode.REVERSE_LANDSCAPE -> true
        DisplayOrientationMode.PORTRAIT, DisplayOrientationMode.REVERSE_PORTRAIT -> false
        DisplayOrientationMode.AUTO -> layoutTier.isLandscape
    }
    if (session.workoutType == GymWorkoutType.CYCLING && session.challenge == null && session.players.size == 1) {
        FreeRideHud(session, freeRideVideoUri, onSelectFreeRideVideo, onPauseResume, onEnd, onDiscard)
        return
    }
    if (session.players.size == 2) {
        SplitSessionHud(session, onPauseResume, onEnd, onDiscard)
        return
    }
    if (shouldUseLandscapeHud) {
        LandscapeActiveSessionHud(
            session = session,
            layoutTier = layoutTier,
            onPauseResume = onPauseResume,
            onEnd = onEnd,
            onDiscard = onDiscard,
            onDismissStatus = onDismissStatus,
        )
        return
    }
    val lead = session.players.maxByOrNull { it.score } ?: session.players.first()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            SessionCueStrip(session = session, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(16.dp))
            EffortDial(player = lead)
        }
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            PrimaryMetricStage(session = session, modifier = Modifier.matchParentSize())
            AnimatedContent(
                targetState = formatDuration(session.elapsedSeconds),
                label = "sessionClock",
                modifier = Modifier.align(Alignment.TopCenter),
            ) { value ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("SESSION", color = MDTheme.colors.textTertiary, style = MDTheme.type.caption)
                    Text(
                        value,
                        color = Color.White,
                        style = MDTheme.type.clock.copy(fontSize = MDTheme.type.clock.fontSize * 0.28f, fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        WorkoutProgressRail(session = session, player = lead)
        Spacer(Modifier.height(12.dp))
        TelemetryDock(session = session, lead = lead)
        Spacer(Modifier.height(12.dp))
        ScoreRail(players = session.players)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            ControlButton(
                label = if (session.isPaused) "RESUME" else "PAUSE",
                icon = if (session.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                onClick = onPauseResume,
                modifier = Modifier.weight(1f),
            )
            ControlButton(
                label = "END & SAVE",
                icon = Icons.Filled.Stop,
                onClick = onEnd,
                modifier = Modifier.weight(1f),
                accent = Color(0xFFFF8A5B),
            )
            ControlButton("QUIT", Icons.Filled.Stop, onDiscard, Modifier.weight(.72f), Color(0xFF6B7785))
        }
        AnimatedVisibility(visible = session.statusMessage != null, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            session.statusMessage?.let { status ->
                Surface(
                    color = Color(0x181FFFFFF),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDismissStatus() },
                ) {
                    Text(
                        status,
                        color = Color.White,
                        style = MDTheme.type.settingSubtitle,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LandscapeActiveSessionHud(
    session: GymActiveSessionState,
    layoutTier: GymLayoutTier,
    onPauseResume: () -> Unit,
    onEnd: () -> Unit,
    onDiscard: () -> Unit,
    onDismissStatus: () -> Unit,
) {
    val lead = session.players.maxByOrNull { it.score } ?: session.players.first()
    val compact = layoutTier == GymLayoutTier.COMPACT_LANDSCAPE
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = if (compact) 12.dp else 22.dp, vertical = if (compact) 10.dp else 18.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 18.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                SessionCueStrip(session = session, modifier = Modifier.weight(1f))
                if (!compact) {
                    Spacer(Modifier.width(12.dp))
                    EffortDial(player = lead)
                }
            }
            Spacer(Modifier.height(if (compact) 8.dp else 14.dp))
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                PrimaryMetricStage(session = session, modifier = Modifier.matchParentSize())
                AnimatedContent(
                    targetState = formatDuration(session.elapsedSeconds),
                    label = "landscapeSessionClock",
                    modifier = Modifier.align(Alignment.TopCenter),
                ) { value ->
                    Text(value, color = Color.White, style = MDTheme.type.clock.copy(fontSize = MDTheme.type.clock.fontSize * if (compact) .20f else .26f, fontWeight = FontWeight.Bold))
                }
            }
        }
        Column(
            modifier = Modifier.width(layoutTier.sessionControlPaneWidthDp.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
        ) {
            WorkoutProgressRail(session = session, player = lead)
            if (!compact) TelemetryDock(session = session, lead = lead)
            ScoreRail(players = session.players)
            Spacer(Modifier.weight(1f))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ControlButton(if (session.isPaused) "RESUME" else "PAUSE", if (session.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause, onPauseResume, Modifier.fillMaxWidth())
                ControlButton("END & SAVE", Icons.Filled.Stop, onEnd, Modifier.fillMaxWidth(), Color(0xFFFF8A5B))
                ControlButton("QUIT", Icons.Filled.Stop, onDiscard, Modifier.fillMaxWidth(), Color(0xFF6B7785))
            }
            session.statusMessage?.let { status ->
                Surface(color = Color(0x181FFFFFF), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onDismissStatus)) {
                    Text(status, color = Color.White, style = MDTheme.type.caption, modifier = Modifier.padding(10.dp))
                }
            }
        }
    }
}

@Composable
private fun WorkoutProgressRail(session: GymActiveSessionState, player: GymPlayerLiveStats) {
    val pacing = session.workoutPacing()
    val targetSeconds = pacing.totalSeconds.coerceAtLeast(1)
    var elapsed = session.activeSeconds
    val activeBlockIndex = pacing.blocks.indexOfFirst { block ->
        val span = block.workSeconds + block.restSeconds
        if (elapsed < span) true else { elapsed -= span; false }
    }.let { if (it == -1) pacing.blocks.lastIndex else it }
    val activeBlock = pacing.blocks[activeBlockIndex]
    val inPlannedBreak = elapsed >= activeBlock.workSeconds
    val phaseRemaining = if (inPlannedBreak) activeBlock.workSeconds + activeBlock.restSeconds - elapsed else activeBlock.workSeconds - elapsed
    val progress by animateFloatAsState(
        targetValue = (session.activeSeconds.toFloat() / targetSeconds).coerceIn(0f, 1f),
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "workoutProgress",
    )
    val plannedBreak = pacing.blocks.take(activeBlockIndex).sumOf { it.restSeconds } + if (inPlannedBreak) elapsed - activeBlock.workSeconds else 0
    val trackedBreak = session.pausedSeconds + session.trackerObservedBreakSeconds
    val extraBreak = (trackedBreak - plannedBreak).coerceAtLeast(0)
    val tint = if (inPlannedBreak || session.isPaused) Color(0xFF7DE6FF) else Color(0xFFFF8A5B)
    var selectedEvent by remember(session.sessionId) { mutableStateOf<GymScoreEvent?>(null) }
    Surface(color = Color(0xD9101820), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(if (inPlannedBreak || session.isPaused) "RECOVER" else "WORK", color = tint, style = MDTheme.type.caption)
                Spacer(Modifier.width(8.dp))
                Text(formatDuration(phaseRemaining.coerceAtLeast(0)), color = Color.White, style = MDTheme.type.settingTitle)
                Spacer(Modifier.weight(1f))
                Text("${(progress * 100).toInt()}% complete", color = MDTheme.colors.textSecondary, style = MDTheme.type.caption)
            }
            BoxWithConstraints(Modifier.fillMaxWidth().height(18.dp).clip(CircleShape)) {
                // The base is deliberately quiet: schedule is visible, but color only arrives as time is earned.
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    pacing.blocks.forEach { block ->
                        Box(Modifier.weight(block.workSeconds.toFloat()).fillMaxHeight().background(Color(0x1FFFFFFF)))
                        if (block.restSeconds > 0) TimelineRestSegment(Modifier.weight(block.restSeconds.toFloat()).fillMaxHeight(), elapsed = false)
                    }
                }
                Box(Modifier.width(maxWidth * progress).fillMaxHeight().clip(CircleShape)) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        pacing.blocks.forEachIndexed { index, block ->
                            val workGradient = when {
                                index == 0 -> Brush.horizontalGradient(listOf(Color(0xFF2C8EAE), Color(0xFF55BCD2))) // warm-up builds gently
                                index == activeBlockIndex && !inPlannedBreak && player.heartRate?.let { it >= player.targetHeartRate } == true -> Brush.horizontalGradient(listOf(Color(0xFFFFA057), Color(0xFFFF4E42)))
                                index == activeBlockIndex && !inPlannedBreak -> Brush.horizontalGradient(listOf(Color(0xFFE5854A), Color(0xFFFFB05E)))
                                else -> Brush.horizontalGradient(listOf(Color(0xFF685047), Color(0xFF8A6251)))
                            }
                            Box(Modifier.weight(block.workSeconds.toFloat()).fillMaxHeight().background(workGradient))
                            if (block.restSeconds > 0) TimelineRestSegment(Modifier.weight(block.restSeconds.toFloat()).fillMaxHeight(), elapsed = true)
                        }
                    }
                }
                val markerEvents = session.recentEvents.distinctBy { it.id }.filter { it.elapsedSeconds in 0..targetSeconds }
                markerEvents.forEach { event ->
                    val position = (maxWidth.value * (event.elapsedSeconds.toFloat() / targetSeconds)).dp
                    Box(
                        Modifier.offset(x = (position - 6.dp).coerceIn(0.dp, maxWidth - 12.dp), y = 3.dp).size(12.dp).clip(CircleShape)
                            .background(Color.White).clickable { selectedEvent = event },
                    )
                }
                val playhead = (maxWidth.value * progress).dp
                Box(Modifier.offset(x = (playhead - 1.dp).coerceIn(0.dp, maxWidth - 2.dp)).width(2.dp).fillMaxHeight().background(Color.White))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${activeBlock.label} · ${activeBlock.workSeconds / 60}:${"%02d".format(activeBlock.workSeconds % 60)} work" + if (activeBlock.restSeconds > 0) " · ${activeBlock.restSeconds}s rest" else "", color = MDTheme.colors.textTertiary, style = MDTheme.type.caption)
                Text("Break ${formatDuration(trackedBreak)}" + if (extraBreak > 0) " · +${formatDuration(extraBreak)}" else "", color = if (extraBreak > 0) Color(0xFFF8C56F) else MDTheme.colors.textTertiary, style = MDTheme.type.caption)
            }
            if (inPlannedBreak || session.isPaused) {
                Text(
                    "Recovery check: ${player.heartRate?.let { "$it BPM" } ?: "waiting for heart-rate tracker"}. A lower reading is shown as recovery, not a health assessment.",
                    color = MDTheme.colors.textSecondary,
                    style = MDTheme.type.caption,
                )
            }
            selectedEvent?.let { event ->
                Text("${event.title} · ${event.detail} at ${formatDuration(event.elapsedSeconds)}", color = Color.White, style = MDTheme.type.caption, modifier = Modifier.clickable { selectedEvent = null })
            }
        }
    }
}

@Composable
private fun TimelineRestSegment(modifier: Modifier = Modifier, elapsed: Boolean) {
    Box(modifier.background(if (elapsed) Color(0xFF2B6D7F) else Color(0x3A405461))) {
        Canvas(Modifier.fillMaxSize()) {
            val step = size.height / 2f
            var x = -size.height
            while (x < size.width) {
                drawLine(if (elapsed) Color(0xBB9BE8FF) else Color(0xB08FC6D8), Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 1.5f)
                x += step
            }
        }
    }
}

@Composable
private fun FreeRideHud(
    session: GymActiveSessionState,
    videoUri: String?,
    onSelectVideo: () -> Unit,
    onPauseResume: () -> Unit,
    onEnd: () -> Unit,
    onDiscard: () -> Unit,
) {
    val rider = session.players.first()
    Row(Modifier.fillMaxSize().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AchievementLucideIcon("Cycling", null, Color(0xFF38CBFF), ambient = true, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp)); Text("FREE RIDE", color = Color.White, style = MDTheme.type.settingTitle)
                Spacer(Modifier.weight(1f)); TextButton(onClick = onSelectVideo) { Text(if (videoUri == null) "SELECT VIDEO" else "CHANGE VIDEO") }
            }
            Spacer(Modifier.height(12.dp))
            Surface(color = Color(0xFF101A22), shape = RoundedCornerShape(24.dp), modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (videoUri != null) {
                    // Keyed on the URI itself - AndroidView's own factory only ever runs once per
                    // call site, so without this, picking a second video via "CHANGE VIDEO"
                    // silently kept playing the first one (no update callback ever pointed the
                    // VideoView at the new URI). key() forces a clean teardown/recreate instead,
                    // same as IPTV's own player surface does on a deliberate content swap (see
                    // PlayerSurface's key(playerEpoch) in IptvScreen.kt) - safe here since a video
                    // pick is a rare, explicit tap, not the kind of automatic per-second churn that
                    // caused Photorama's ExoPlayer rebuild issue.
                    key(videoUri) {
                        AndroidView(
                            factory = { context -> VideoView(context).apply { setVideoURI(Uri.parse(videoUri)); setOnPreparedListener { it.isLooping = true; start() } } },
                            update = { view -> if (!view.isPlaying) view.start() },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ExerciseLucideIcon("Bike", null, Color(0xFF38CBFF), animate = true, ambient = true, modifier = Modifier.size(72.dp))
                            Text("Choose a ride video to begin", color = MDTheme.colors.textSecondary, style = MDTheme.type.body)
                            Button(onClick = onSelectVideo) { Text("SELECT VIDEO") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                ControlButton(if (session.isPaused) "RESUME" else "PAUSE", if (session.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause, onPauseResume, Modifier.weight(1f))
                ControlButton("END & SAVE", Icons.Filled.Stop, onEnd, Modifier.weight(1f), Color(0xFFFF8A5B))
                ControlButton("QUIT", Icons.Filled.Stop, onDiscard, Modifier.weight(.72f), Color(0xFF6B7785))
            }
        }
        Column(Modifier.width(230.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FreeRideMetric("TIME", formatDuration(session.elapsedSeconds), Color(0xFFE8F7FF))
            FreeRideMetric("HEALTH · BPM", "${rider.heartRate ?: "--"}", Color(0xFF7CF7B8))
            FreeRideMetric("SPEED · KM/H", "%.1f".format(rider.speedKph ?: 0.0), Color(0xFF38CBFF))
            FreeRideMetric("CADENCE · RPM", "${rider.cadenceRpm?.toInt() ?: "--"}", Color(0xFFF8C56F))
            FreeRideMetric("OUTPUT · W", "${rider.powerWatts?.toInt() ?: "--"}", Color(0xFFFF8A5B))
            FreeRideMetric("DISTANCE · KM", "%.2f".format(rider.distanceKm), Color(0xFFBCA7FF))
        }
    }
}

@Composable
private fun FreeRideMetric(label: String, value: String, tint: Color) {
    Surface(color = Color(0x141FFFFFF), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, color = MDTheme.colors.textTertiary, style = MDTheme.type.caption)
            Text(value, color = tint, style = MDTheme.type.clock.copy(fontSize = MDTheme.type.clock.fontSize * .32f, fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun SplitSessionHud(
    session: GymActiveSessionState,
    onPauseResume: () -> Unit,
    onEnd: () -> Unit,
    onDiscard: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(22.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.weight(1f).fillMaxWidth()) {
            session.players.forEach { player ->
                PlayerWorkoutPanel(player, session.elapsedSeconds, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            ControlButton(if (session.isPaused) "RESUME" else "PAUSE", if (session.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause, onPauseResume, Modifier.weight(1f))
            ControlButton("END & SAVE", Icons.Filled.Stop, onEnd, Modifier.weight(1f), Color(0xFFFF8A5B))
            ControlButton("QUIT", Icons.Filled.Stop, onDiscard, Modifier.weight(.72f), Color(0xFF6B7785))
        }
    }
}

@Composable
private fun PlayerWorkoutPanel(player: GymPlayerLiveStats, elapsedSeconds: Int, modifier: Modifier = Modifier) {
    val accent = Color(player.accentColorArgb)
    Surface(color = accent.copy(alpha = 0.10f), shape = RoundedCornerShape(24.dp), modifier = modifier.fillMaxHeight()) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) { Text(player.avatarLabel(), color = Color.Black, style = MDTheme.type.caption) }
                Spacer(Modifier.width(10.dp))
                Column { Text(player.displayName, color = Color.White, style = MDTheme.type.settingTitle); Text(formatDuration(elapsedSeconds), color = MDTheme.colors.textSecondary, style = MDTheme.type.caption) }
            }
            Text("HEART RATE", color = MDTheme.colors.textTertiary, style = MDTheme.type.caption)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${player.heartRate ?: "--"}", color = Color.White, style = MDTheme.type.clock.copy(fontSize = MDTheme.type.clock.fontSize * .38f, fontWeight = FontWeight.Bold))
                Spacer(Modifier.width(8.dp)); Text("BPM", color = MDTheme.colors.textSecondary, style = MDTheme.type.body)
            }
            TargetHeartRateStrip(player)
            MetricSparkline(player)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                PlayerMetric("SCORE", "${player.score}", Modifier.weight(1f))
                PlayerMetric("CALORIES", "${player.calories}", Modifier.weight(1f))
                PlayerMetric("COMBO", "×${player.combo.coerceAtLeast(1)}", Modifier.weight(1f))
            }
        }
    }
}

@Composable private fun PlayerMetric(label: String, value: String, modifier: Modifier) = Column(modifier) { Text(label, color = MDTheme.colors.textTertiary, style = MDTheme.type.caption); Text(value, color = Color.White, style = MDTheme.type.settingTitle) }

@Composable
private fun TargetHeartRateStrip(player: GymPlayerLiveStats) {
    val hr = player.heartRate ?: 0
    val inZone = hr >= 130 && hr <= 165
    Surface(color = if (inZone) Color(0x1C7CF7B8) else Color(0x12FFFFFF), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Favorite, null, tint = if (inZone) Color(0xFF7CF7B8) else Color(0xFFF3C56A), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp)); Text("Target ${player.targetHeartRate} BPM", color = Color.White, style = MDTheme.type.caption)
            Spacer(Modifier.weight(1f)); Text("×${"%.1f".format(player.effortMultiplier)}", color = if (player.effortMultiplier > 1f) Color(0xFFF8C56F) else MDTheme.colors.textSecondary, style = MDTheme.type.settingTitle)
        }
    }
}

@Composable
private fun SessionCueStrip(
    session: GymActiveSessionState,
    modifier: Modifier = Modifier,
) {
    val lead = session.players.maxByOrNull { it.score } ?: session.players.first()
    Surface(color = Color(0x0DFFFFFF), shape = RoundedCornerShape(18.dp), modifier = modifier) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                when (session.primaryDeviceKind) {
                    FitnessDeviceKind.STRENGTH -> "Upper Body Ladder"
                    FitnessDeviceKind.CARDIO -> "Bike Sprint Builder"
                    FitnessDeviceKind.HEART_RATE -> "Zone Control"
                },
                color = Color.White,
                style = MDTheme.type.settingTitle,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${lead.displayName}  •  ${lead.combo.coerceAtLeast(1)} streak  •  ${session.challenge?.title ?: "On Demand"}",
                color = MDTheme.colors.textSecondary,
                style = MDTheme.type.caption,
            )
        }
    }
}

@Composable
private fun PrimaryMetricStage(
    session: GymActiveSessionState,
    modifier: Modifier = Modifier,
) {
    val lead = session.players.maxByOrNull { it.score } ?: session.players.first()
    Surface(color = Color.Transparent, modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(34.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0x220F1720),
                                Color(0x0AFFFFFF),
                                Color(0x1407090C),
                            ),
                        ),
                    ),
            )
            CoachStage(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .padding(top = 36.dp, bottom = 48.dp),
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp),
            ) {
                if (session.challenge != null && session.elapsedSeconds < 4) {
                    Text(
                        "${(4 - session.elapsedSeconds).coerceAtLeast(1)}",
                        color = Color.White,
                        style = MDTheme.type.clock.copy(
                            fontSize = MDTheme.type.clock.fontSize * 0.4f,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .width(112.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(Color(0x30FFFFFF)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.62f)
                                .height(4.dp)
                                .background(Brush.horizontalGradient(listOf(Color(0xFF6F8BFF), Color(0xFFF17CB5)))),
                        )
                    }
                    Spacer(Modifier.height(22.dp))
                }
                Text(
                    when (session.primaryDeviceKind) {
                        FitnessDeviceKind.STRENGTH -> "JUMP SQUAT"
                        FitnessDeviceKind.CARDIO -> "SPRINT DRIVE"
                        FitnessDeviceKind.HEART_RATE -> "HEART RATE"
                    },
                    color = Color.White,
                    style = MDTheme.type.settingTitle,
                )
                Spacer(Modifier.height(12.dp))
                when (session.primaryDeviceKind) {
                    FitnessDeviceKind.STRENGTH -> {
                        LargeMetric("${lead.repetitions}", "REPS")
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                            MetricPair("LEFT", lead.loadLeftKg?.let { "${DecimalFormat("0.0").format(it)} KG" } ?: "--")
                            MetricPair("RIGHT", lead.loadRightKg?.let { "${DecimalFormat("0.0").format(it)} KG" } ?: "--")
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            DecimalFormat("#,##0").format(lead.volumeKg) + " KG TOTAL VOLUME",
                            color = MDTheme.colors.textSecondary,
                            style = MDTheme.type.settingSubtitle,
                        )
                    }
                    else -> {
                        LargeMetric("${lead.powerWatts ?: 0}", "WATTS")
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                            MetricPair("CADENCE", "${lead.cadenceRpm ?: 0} RPM")
                            MetricPair("RESISTANCE", "${lead.resistance ?: 0}")
                        }
                    }
                }
            }
            session.recentEvents.firstOrNull()?.let { event ->
                Surface(
                    color = Color(0x16000000),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text("+${event.scoreDelta}", color = Color(0xFFF8C56F), style = MDTheme.type.settingTitle)
                        Text(event.title.uppercase(), color = Color.White, style = MDTheme.type.caption)
                    }
                }
            }
        }
    }
}

@Composable
private fun CoachStage(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.aspectRatio(0.84f)) {
        val centerX = size.width / 2f
        val floorY = size.height * 0.82f
        drawCircle(
            color = Color.White.copy(alpha = 0.85f),
            radius = size.width * 0.28f,
            center = Offset(centerX, floorY),
            style = Stroke(width = 7f),
        )
        val bodyTop = size.height * 0.26f
        val headRadius = size.width * 0.038f
        drawCircle(Color.White.copy(alpha = 0.9f), headRadius, Offset(centerX, bodyTop))
        val shoulderY = bodyTop + headRadius * 2.1f
        val hipY = size.height * 0.54f
        val handY = size.height * 0.46f
        val footY = floorY - size.height * 0.03f
        val shoulderOffset = size.width * 0.09f
        val handOffset = size.width * 0.14f
        val footOffset = size.width * 0.1f
        val stroke = Stroke(width = 8f, cap = StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.92f), Offset(centerX, shoulderY), Offset(centerX, hipY), strokeWidth = 8f, cap = StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.92f), Offset(centerX - shoulderOffset, shoulderY), Offset(centerX - handOffset, handY), strokeWidth = 8f, cap = StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.92f), Offset(centerX + shoulderOffset, shoulderY), Offset(centerX + handOffset, handY), strokeWidth = 8f, cap = StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.92f), Offset(centerX, hipY), Offset(centerX - footOffset, footY), strokeWidth = 8f, cap = StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.92f), Offset(centerX, hipY), Offset(centerX + footOffset, footY), strokeWidth = 8f, cap = StrokeCap.Round)
        drawCircle(Color(0xFF6D4B38).copy(alpha = 0.9f), size.width * 0.022f, Offset(centerX - handOffset, handY))
        drawCircle(Color(0xFFB4623C).copy(alpha = 0.9f), size.width * 0.022f, Offset(centerX + handOffset, handY))
        repeat(3) { index ->
            val angle = 0.65f + (index * 0.18f)
            val orbX = centerX + cos(angle).toFloat() * size.width * 0.33f
            val orbY = floorY + sin(angle).toFloat() * size.height * 0.02f
            drawCircle(Color.White.copy(alpha = 0.06f), size.width * (0.018f + index * 0.006f), Offset(orbX, orbY))
        }
    }
}

@Composable
private fun EffortDial(player: GymPlayerLiveStats) {
    val ratio = ((player.heartRate ?: 0) / 180f).coerceIn(0f, 1f)
    Surface(color = Color(0x0DFFFFFF), shape = RoundedCornerShape(22.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text("${player.heartRate ?: 0}", color = Color.White, style = MDTheme.type.settingTitle)
            Text("BPM", color = MDTheme.colors.textTertiary, style = MDTheme.type.caption)
            Spacer(Modifier.height(10.dp))
            Canvas(modifier = Modifier.size(78.dp)) {
                drawArc(
                    color = Color.White.copy(alpha = 0.12f),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 11f, cap = StrokeCap.Round),
                )
                drawArc(
                    brush = Brush.sweepGradient(listOf(Color(0xFF7CF7B8), Color(0xFFF3C56A), Color(0xFFFF7C68), Color(0xFF7CF7B8))),
                    startAngle = 135f,
                    sweepAngle = 270f * ratio,
                    useCenter = false,
                    style = Stroke(width = 11f, cap = StrokeCap.Round),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(player.heartRateZone ?: "Zone --", color = MDTheme.colors.textSecondary, style = MDTheme.type.caption)
        }
    }
}

@Composable
private fun TelemetryDock(
    session: GymActiveSessionState,
    lead: GymPlayerLiveStats,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Surface(color = Color(0x16121719), shape = RoundedCornerShape(18.dp), modifier = Modifier.weight(1.2f)) {
            Column(Modifier.padding(14.dp)) {
                Text("Heart Rate", color = MDTheme.colors.textTertiary, style = MDTheme.type.caption)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color(0xFFF0C55F), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("${lead.heartRate ?: 0}", color = Color.White, style = MDTheme.type.body.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.width(4.dp))
                    Text("bpm", color = MDTheme.colors.textSecondary, style = MDTheme.type.caption)
                }
                Spacer(Modifier.height(10.dp))
                MetricSparkline(player = lead)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(0.88f)) {
            Surface(color = Color(0x16121719), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Calories", color = MDTheme.colors.textTertiary, style = MDTheme.type.caption)
                    Spacer(Modifier.height(10.dp))
                    Text("${lead.calories}", color = Color.White, style = MDTheme.type.body.copy(fontWeight = FontWeight.Bold))
                }
            }
            Surface(color = Color(0x16121719), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Time Elapsed", color = MDTheme.colors.textTertiary, style = MDTheme.type.caption)
                    Spacer(Modifier.height(10.dp))
                    Text(formatDuration(session.elapsedSeconds), color = Color.White, style = MDTheme.type.body.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

@Composable
private fun ScoreRail(players: List<GymPlayerLiveStats>) {
    Surface(color = Color(0x100FFFFFF), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            players.sortedByDescending { it.score }.forEach { player ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(player.accentColorArgb).copy(alpha = 0.28f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(player.avatarLabel(), color = Color.White, style = MDTheme.type.caption)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(player.displayName, color = Color.White, style = MDTheme.type.caption)
                        Text("${player.score} pts", color = Color(player.accentColorArgb), style = MDTheme.type.caption)
                    }
                }
            }
        }
    }
}

private fun GymPlayerLiveStats.avatarLabel(): String =
    displayName.split(' ').mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("").take(2)

@Composable
private fun LargeMetric(value: String, unit: String) {
    Text(
        value,
        color = Color.White,
        style = MDTheme.type.clock.copy(fontSize = MDTheme.type.clock.fontSize * 0.52f, fontWeight = FontWeight.Bold),
    )
    Text(unit, color = MDTheme.colors.textTertiary, style = MDTheme.type.body)
}

@Composable
private fun MetricPair(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, style = MDTheme.type.settingTitle)
        Text(label, color = MDTheme.colors.textTertiary, style = MDTheme.type.caption)
    }
}

@Composable
private fun MetricSparkline(player: GymPlayerLiveStats) {
    val alpha by animateFloatAsState(targetValue = if (player.combo > 0) 1f else 0.66f, label = "sparkAlpha")
    val transition = rememberInfiniteTransition(label = "chartBreath")
    val breath by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900)),
        label = "chartDotBreath",
    )
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3.2f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x0AFFFFFF))
            .padding(10.dp),
    ) {
        val stroke = Stroke(width = 4f, cap = StrokeCap.Round, pathEffect = PathEffect.cornerPathEffect(12f))
        val base = size.height * 0.72f
        val step = size.width / 11f
        val points = List(12) { index ->
            val wave = sin((index + player.combo + (player.powerWatts ?: player.repetitions)) / 2.0)
            Offset(index * step, base - (wave.toFloat() * size.height * 0.28f))
        }
        for (i in 0 until points.lastIndex) {
            drawLine(
                brush = Brush.horizontalGradient(listOf(Color(0xFF38CBFF).copy(alpha), Color(0xFF7CF7B8).copy(alpha))),
                start = points[i],
                end = points[i + 1],
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
        }
        points.forEachIndexed { index, point ->
            val emphasis = if (index == points.lastIndex) 1f else 0.55f
            drawCircle(Color(0xFF7CF7B8).copy(alpha = breath * emphasis * 0.22f), radius = 10f * breath, center = point)
            drawCircle(Color(0xFF7CF7B8).copy(alpha = breath * emphasis), radius = 3.5f, center = point)
        }
    }
}

@Composable
private fun LeaderboardStrip(players: List<GymPlayerLiveStats>) {
    Surface(color = Color(0x100FFFFFF), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Leaderboard, contentDescription = null, tint = Color(0xFF7CF7B8))
                Spacer(Modifier.width(10.dp))
                Text("LIVE LEADERBOARD", color = Color.White, style = MDTheme.type.settingTitle)
            }
            Spacer(Modifier.height(12.dp))
            players.sortedByDescending { it.score }.forEachIndexed { index, player ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}", color = MDTheme.colors.textTertiary, style = MDTheme.type.caption, modifier = Modifier.width(18.dp))
                    Text(player.displayName, color = Color.White, style = MDTheme.type.body, modifier = Modifier.weight(1f))
                    Text("${player.score}", color = Color(player.accentColorArgb), style = MDTheme.type.settingTitle)
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun ControlButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Color(0xFF38CBFF),
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = accent.copy(alpha = 0.16f), contentColor = Color.White),
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun SummarySheet(
    summary: GymSessionSummaryState,
    onDismiss: () -> Unit,
) {
    if (summary.session.workoutType == GymWorkoutType.CYCLING && summary.session.challengeId == null) {
        FreeRideSummarySheet(summary, onDismiss)
        return
    }
    Surface(
        color = Color(0xEE081018),
        shape = RoundedCornerShape(30.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp),
    ) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(summary.title.uppercase(), color = Color.White, style = MDTheme.type.settingTitle)
            Spacer(Modifier.height(8.dp))
            Text(summary.subtitle, color = MDTheme.colors.textSecondary, style = MDTheme.type.settingSubtitle)
            Spacer(Modifier.height(18.dp))
            summary.session.players.forEach { player ->
                Surface(color = Color(0x100FFFFFF), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(player.displayName, color = Color.White, style = MDTheme.type.settingTitle)
                            Text(
                                "${player.score} pts • ${player.xpEarned} xp • ${player.metrics.calories} cal",
                                color = MDTheme.colors.textSecondary,
                                style = MDTheme.type.settingSubtitle,
                            )
                        }
                        Text(formatDistance(player.metrics.distanceKm), color = Color(0xFF7CF7B8), style = MDTheme.type.body)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8F7FF), contentColor = Color(0xFF08131A)),
            ) {
                Text("Back to Gym")
            }
        }
    }
}

@Composable
private fun FreeRideSummarySheet(summary: GymSessionSummaryState, onDismiss: () -> Unit) {
    val rider = summary.session.players.firstOrNull() ?: return
    var rating by remember { mutableStateOf(0) }
    Surface(color = Color(0xF0081018), shape = RoundedCornerShape(30.dp), modifier = Modifier.fillMaxWidth(0.72f)) {
        Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("GREAT RIDE!", color = Color.White, style = MDTheme.type.sectionTitle.copy(fontWeight = FontWeight.Bold))
            Text("Free Ride complete · ${formatDuration(summary.session.activeSeconds)} active", color = MDTheme.colors.textSecondary, style = MDTheme.type.settingSubtitle)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                Surface(color = Color(0x111FFFFF), shape = RoundedCornerShape(20.dp), modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("YOUR RIDE", color = Color(0xFF38CBFF), style = MDTheme.type.caption)
                        Text(rider.displayName, color = Color.White, style = MDTheme.type.settingTitle)
                        Text("${rider.score} points · +${rider.xpEarned} XP", color = MDTheme.colors.textSecondary, style = MDTheme.type.settingSubtitle)
                        Text("RATE THIS RIDE", color = MDTheme.colors.textTertiary, style = MDTheme.type.caption)
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            (1..5).forEach { star -> Text(if (star <= rating) "★" else "☆", color = if (star <= rating) Color(0xFFF8C56F) else Color.White, style = MDTheme.type.clock.copy(fontSize = MDTheme.type.clock.fontSize * .32f), modifier = Modifier.clickable { rating = star }) }
                        }
                    }
                }
                Surface(color = Color(0x111FFFFF), shape = RoundedCornerShape(20.dp), modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("YOUR STATS", color = Color.White, style = MDTheme.type.settingTitle)
                        FreeRideSummaryMetric("OUTPUT", rider.metrics.averagePowerWatts?.let { "$it W" } ?: "--")
                        FreeRideSummaryMetric("CALORIES", "${rider.metrics.calories}")
                        FreeRideSummaryMetric("DISTANCE", formatDistance(rider.metrics.distanceKm))
                        FreeRideSummaryMetric("AVG HEART RATE", rider.metrics.averageHeartRate?.let { "$it BPM" } ?: "--")
                        FreeRideSummaryMetric("BEST POWER", rider.metrics.maxPowerWatts?.let { "$it W" } ?: "--")
                    }
                }
            }
            rider.achievements.takeIf { it.isNotEmpty() }?.let { unlocked -> Text("UNLOCKED  ${unlocked.joinToString(" · ")}", color = Color(0xFFF8C56F), style = MDTheme.type.caption) }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(0.55f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38CBFF), contentColor = Color(0xFF06131A))) { Text("DONE") }
        }
    }
}

@Composable
private fun FreeRideSummaryMetric(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MDTheme.colors.textSecondary, style = MDTheme.type.caption)
        Text(value, color = Color.White, style = MDTheme.type.settingSubtitle)
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) Color(0xFF38CBFF) else Color.White.copy(alpha = 0.12f)
    Surface(
        color = if (selected) Color(0x1438CBFF) else Color.Transparent,
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier
            .border(1.dp, borderColor, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            val icon = when {
                label.contains("Challenge", true) -> Icons.Filled.Bolt
                label.contains("Multi", true) -> Icons.Filled.Leaderboard
                label.contains("Bike", true) || label.contains("Cycl", true) -> Icons.Filled.DirectionsBike
                label.contains("Strength", true) -> Icons.Filled.FitnessCenter
                else -> Icons.Filled.Sensors
            }
            Icon(icon, contentDescription = null, tint = if (selected) Color.White else MDTheme.colors.textSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, color = if (selected) Color.White else MDTheme.colors.textSecondary, style = MDTheme.type.caption)
        }
    }
}

private fun formatDuration(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}

private fun formatDistance(distanceKm: Double): String =
    DecimalFormat("0.0").format(distanceKm) + " km"
