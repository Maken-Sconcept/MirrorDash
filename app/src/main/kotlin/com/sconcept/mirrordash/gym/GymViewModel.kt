package com.sconcept.mirrordash.gym

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class GymUiExtraState(
    val dashboardTab: GymDashboardTab = GymDashboardTab.HOME,
    val setupVisible: Boolean = false,
    val connectionCenterExpanded: Boolean = false,
    val selectedWorkoutType: GymWorkoutType = GymWorkoutType.HYBRID,
    val selectedPlayerIds: List<String> = defaultGymProfiles().take(1).map { it.id },
    val profileSheetProfileId: String? = null,
    val workoutLibraryFilter: String = "All",
    val selectedChallengeId: String? = defaultGymChallenges().firstOrNull()?.id,
    val generatorStep: GymGeneratorStep = GymGeneratorStep.MUSCLES,
    val generatorPreferences: GymGeneratorPreferences = defaultGymGeneratorPreferences(),
    val selectedExerciseId: String? = null,
    val favoriteExerciseIds: Set<String> = emptySet(),
    val workoutQueueIds: List<String> = emptyList(),
    val workoutSyncStatus: GymWorkoutSyncStatus = GymWorkoutSyncStatus.Idle,
    val workoutLibraryStatus: GymWorkoutLibraryStatus = GymWorkoutLibraryStatus(),
)

class GymViewModel(
    application: Application,
    private val repository: GymRepository,
    private val contentRepository: GymContentRepository,
    private val sessionEngine: GymSessionEngine,
    private val healthConnectGateway: GymHealthConnectGateway,
) : AndroidViewModel(application) {
    private val extra = MutableStateFlow(GymUiExtraState())
    private val loadedExerciseCatalog = MutableStateFlow<List<GymExerciseCatalogEntry>>(emptyList())
    /** Direct catalogue stream for exercise browsing; it remains available while the dashboard state settles. */
    val exerciseCatalog: StateFlow<List<GymExerciseCatalogEntry>> = loadedExerciseCatalog

    init {
        viewModelScope.launch {
            loadedExerciseCatalog.value = runCatching { contentRepository.loadWorkoutCatalog() }
                .onSuccess { catalog -> Log.i("GymViewModel", "Loaded ${catalog.size} exercises from the workout catalog") }
                .getOrElse { error ->
                    Log.e("GymViewModel", "Workout library could not be loaded", error)
                    emptyList()
                }
        }
        // Sync is intentionally independent from catalog loading: the Gym opens immediately and
        // every video can still stream from the NAS until its card copy has arrived.
        viewModelScope.launch {
            when (val result = contentRepository.syncWorkoutLibraryToMemoryCard { status ->
                extra.update { it.copy(workoutSyncStatus = status) }
            }) {
                is WorkoutLibrarySyncResult.Success -> {
                    extra.update { it.copy(workoutSyncStatus = GymWorkoutSyncStatus.Complete(result.copiedFiles, result.totalFiles)) }
                    Log.i("GymViewModel", "Workout card sync complete: ${result.copiedFiles}/${result.totalFiles} files copied")
                    kotlinx.coroutines.delay(SYNC_COMPLETE_MESSAGE_MS)
                    extra.update { state ->
                        if (state.workoutSyncStatus is GymWorkoutSyncStatus.Complete) state.copy(workoutSyncStatus = GymWorkoutSyncStatus.Idle) else state
                    }
                }
                is WorkoutLibrarySyncResult.Failed -> {
                    extra.update { it.copy(workoutSyncStatus = GymWorkoutSyncStatus.Failed(result.message)) }
                    Log.w("GymViewModel", "Workout card sync skipped: ${result.message}")
                }
                WorkoutLibrarySyncResult.NoMemoryCard -> Log.i("GymViewModel", "No removable card mounted; NAS playback remains available")
                WorkoutLibrarySyncResult.Skipped -> Unit
            }
        }
    }

    val uiState: StateFlow<GymUiState> = combine(
        repository.storedState,
        sessionEngine.runtimeState,
        extra,
        loadedExerciseCatalog,
        healthConnectGateway.snapshot,
    ) { stored, runtime, extraState, catalog, wearableHealth ->
        val selectedProfiles = resolveSelectedProfiles(stored.profiles, extraState.selectedPlayerIds)
        val selectedDashboards = selectedProfiles.map { profile ->
            val heartRateDevice = runtime.devices.firstOrNull {
                it.assignedPlayerId == profile.id && it.kind == FitnessDeviceKind.HEART_RATE
            }
            GymProfileDashboardSnapshot(
                profile = profile,
                weeklyProgress = buildWeeklyProgress(profile, stored.sessionHistory),
                dashboardStats = buildDashboardStats(profile, stored.sessionHistory),
                heartRateSummary = deriveHeartRateSummary(profile, heartRateDevice),
                activeAchievements = buildActiveAchievements(profile, stored.sessionHistory),
            )
        }
        val primaryDashboard = selectedDashboards.firstOrNull()
        val generatedWorkout = buildGymGeneratedWorkoutPlan(extraState.generatorPreferences, catalog)
        Log.i("GymViewModel", "Publishing ${catalog.size} exercises to the Gym UI")
        GymUiState(
            featureSettings = stored.featureSettings,
            displayOrientationMode = stored.displayOrientationMode,
            profiles = stored.profiles,
            devicePreferences = stored.devicePreferences,
            devices = runtime.devices,
            sessionHistory = stored.sessionHistory.sortedByDescending { it.startedAtEpochMs },
            weeklyProgress = primaryDashboard?.weeklyProgress ?: GymWeeklyProgress(),
            dashboardStats = primaryDashboard?.dashboardStats ?: GymDashboardStats(),
            activeSession = runtime.activeSession,
            latestSummary = runtime.latestSummary,
            availableChallenges = if (stored.featureSettings.challengesEnabled) defaultGymChallenges() else emptyList(),
            activeAchievements = selectedProfiles.firstOrNull()?.takeIf { stored.featureSettings.challengesEnabled }?.let { profile ->
                buildActiveAchievements(profile, stored.sessionHistory)
            }.orEmpty(),
            dashboardTab = extraState.dashboardTab,
            setupVisible = extraState.setupVisible,
            connectionCenterExpanded = extraState.connectionCenterExpanded,
            selectedWorkoutType = extraState.selectedWorkoutType,
            selectedPlayerIds = selectedProfiles.map { it.id },
            selectedProfileDashboards = selectedDashboards,
            activeProfileCount = selectedProfiles.size,
            profileSheetProfile = stored.profiles.firstOrNull { it.id == extraState.profileSheetProfileId },
            selectedChallengeId = extraState.selectedChallengeId,
            workoutLibraryFilter = extraState.workoutLibraryFilter,
            generatorStep = extraState.generatorStep,
            generatorPreferences = extraState.generatorPreferences,
            generatedWorkout = generatedWorkout,
            exerciseCatalogCount = catalog.size,
            exerciseCatalogHighlights = catalog.take(6).map { it.name },
            exerciseCatalog = catalog,
            selectedExerciseId = extraState.selectedExerciseId,
            favoriteExerciseIds = extraState.favoriteExerciseIds,
            workoutQueueIds = extraState.workoutQueueIds,
            wearableHealth = wearableHealth,
            workoutSyncStatus = extraState.workoutSyncStatus,
            workoutLibraryStatus = extraState.workoutLibraryStatus,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GymUiState())

    fun selectDashboardTab(tab: GymDashboardTab) {
        extra.update { it.copy(dashboardTab = tab) }
    }

    fun setWorkoutLibraryFilter(filter: String) {
        extra.update { it.copy(workoutLibraryFilter = filter) }
    }

    fun openExercise(exerciseId: String) = extra.update { it.copy(selectedExerciseId = exerciseId) }

    fun closeExercise() = extra.update { it.copy(selectedExerciseId = null) }

    fun toggleFavoriteExercise(exerciseId: String) = extra.update { state ->
        state.copy(favoriteExerciseIds = state.favoriteExerciseIds.let { favorites ->
            if (exerciseId in favorites) favorites - exerciseId else favorites + exerciseId
        })
    }

    fun toggleWorkoutQueue(exerciseId: String) = extra.update { state ->
        state.copy(workoutQueueIds = if (exerciseId in state.workoutQueueIds) {
            state.workoutQueueIds - exerciseId
        } else {
            state.workoutQueueIds + exerciseId
        })
    }

    fun setChallengesEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.updateFeatureSettings { it.copy(challengesEnabled = enabled) } }
    }

    fun setWorkoutLibrarySource(source: GymWorkoutLibrarySource) {
        viewModelScope.launch { repository.updateFeatureSettings { it.copy(workoutLibrarySource = source) } }
    }

    fun refreshWorkoutLibraryStatus() {
        viewModelScope.launch {
            extra.update { it.copy(workoutLibraryStatus = it.workoutLibraryStatus.copy(isLoading = true, message = null)) }
            extra.update { it.copy(workoutLibraryStatus = contentRepository.workoutLibraryStatus()) }
        }
    }

    fun syncWorkoutLibraryNow() {
        viewModelScope.launch {
            when (val result = contentRepository.syncWorkoutLibraryToMemoryCard { status ->
                extra.update { it.copy(workoutSyncStatus = status) }
            }) {
                is WorkoutLibrarySyncResult.Success -> extra.update {
                    it.copy(
                        workoutSyncStatus = GymWorkoutSyncStatus.Complete(result.copiedFiles, result.totalFiles),
                        workoutLibraryStatus = contentRepository.workoutLibraryStatus(),
                    )
                }
                is WorkoutLibrarySyncResult.Failed -> extra.update { it.copy(workoutSyncStatus = GymWorkoutSyncStatus.Failed(result.message)) }
                WorkoutLibrarySyncResult.NoMemoryCard -> refreshWorkoutLibraryStatus()
                WorkoutLibrarySyncResult.Skipped -> Unit
            }
        }
    }

    fun refreshWearableHealth() = healthConnectGateway.refreshHealthSummary(force = true)

    fun toggleSetup() {
        extra.update { it.copy(setupVisible = !it.setupVisible) }
    }

    fun openSetup() {
        val activeProfiles = resolveSelectedProfiles(uiState.value.profiles, uiState.value.selectedPlayerIds)
        // A shared workout respects both people: use the most accessible level and the
        // common goal when possible (otherwise keep the primary player's intent).
        val sharedGoal = activeProfiles.groupingBy { it.workoutGoal }.eachCount()
            .maxByOrNull { it.value }?.key ?: GymTrainingGoal.BUILD_MUSCLE
        val sharedLevel = activeProfiles.minByOrNull { it.workoutLevel.ordinal }?.workoutLevel
            ?: GymTrainingLevel.BEGINNER
        extra.update {
            it.copy(
                setupVisible = true,
                generatorStep = GymGeneratorStep.MUSCLES,
                generatorPreferences = it.generatorPreferences.copy(goal = sharedGoal, level = sharedLevel),
            )
        }
    }

    fun dismissSetup() {
        extra.update { it.copy(setupVisible = false, generatorStep = GymGeneratorStep.MUSCLES) }
    }

    fun toggleConnectionCenter() {
        extra.update { it.copy(connectionCenterExpanded = !it.connectionCenterExpanded) }
    }

    fun setWorkoutType(type: GymWorkoutType) {
        extra.update { it.copy(selectedWorkoutType = type) }
    }

    fun selectGeneratorGoal(goal: GymTrainingGoal) {
        extra.update { it.copy(generatorPreferences = it.generatorPreferences.copy(goal = goal)) }
    }

    fun selectGeneratorLevel(level: GymTrainingLevel) {
        extra.update { it.copy(generatorPreferences = it.generatorPreferences.copy(level = level)) }
    }

    fun toggleGeneratorEquipment(option: GymEquipmentOption) {
        extra.update { state ->
            val equipment = state.generatorPreferences.equipment.toMutableList()
            if (option in equipment) {
                if (equipment.size > 1) equipment.remove(option)
            } else {
                equipment += option
            }
            state.copy(generatorPreferences = state.generatorPreferences.copy(equipment = equipment.distinct()))
        }
    }

    fun toggleGeneratorMuscle(group: GymMuscleGroup) {
        extra.update { state ->
            val groups = state.generatorPreferences.muscleGroups.toMutableList()
            if (group in groups) groups.remove(group) else groups += group
            state.copy(generatorPreferences = state.generatorPreferences.copy(muscleGroups = groups))
        }
    }

    fun setGeneratorExerciseCount(count: Int) {
        extra.update { it.copy(generatorPreferences = it.generatorPreferences.copy(exerciseCount = count)) }
    }

    fun setGeneratorDuration(minutes: Int) {
        extra.update { it.copy(generatorPreferences = it.generatorPreferences.copy(durationMinutes = minutes.coerceIn(10, 180))) }
    }

    fun goToGeneratorStep(step: GymGeneratorStep) {
        extra.update { it.copy(generatorStep = step) }
    }

    fun nextGeneratorStep() {
        extra.update { state ->
            val next = when (state.generatorStep) {
                GymGeneratorStep.MUSCLES -> GymGeneratorStep.EXERCISE_COUNT
                GymGeneratorStep.EXERCISE_COUNT -> GymGeneratorStep.PREVIEW
                GymGeneratorStep.PREVIEW -> GymGeneratorStep.PREVIEW
            }
            state.copy(generatorStep = next)
        }
    }

    fun previousGeneratorStep() {
        extra.update { state ->
            val previous = when (state.generatorStep) {
                GymGeneratorStep.MUSCLES -> GymGeneratorStep.MUSCLES
                GymGeneratorStep.EXERCISE_COUNT -> GymGeneratorStep.MUSCLES
                GymGeneratorStep.PREVIEW -> GymGeneratorStep.EXERCISE_COUNT
            }
            state.copy(generatorStep = previous)
        }
    }

    fun togglePlayer(profileId: String) {
        extra.update { state ->
            val selected = state.selectedPlayerIds.toMutableList()
            if (profileId in selected) {
                if (selected.size > 1) selected.remove(profileId)
            } else if (selected.size < 2) {
                selected += profileId
            } else {
                selected[1] = profileId
            }
            state.copy(selectedPlayerIds = selected.distinct())
        }
    }

    fun openProfile(profileId: String) {
        extra.update { it.copy(profileSheetProfileId = profileId) }
    }

    fun dismissProfileSheet() {
        extra.update { it.copy(profileSheetProfileId = null) }
    }

    fun addProfile() {
        viewModelScope.launch {
            val currentProfiles = uiState.value.profiles
            val nextNumber = currentProfiles
                .mapNotNull { profile -> profile.id.substringAfterLast('_', "").toIntOrNull() }
                .maxOrNull()
                ?.plus(1)
                ?: (currentProfiles.size + 1)
            val newProfile = GymProfile(
                id = "player_$nextNumber",
                name = "Player $nextNumber",
                avatarLabel = "P$nextNumber",
                accentColorArgb = dashboardProfileAccents[(nextNumber - 1) % dashboardProfileAccents.size],
            )
            repository.updateProfiles { it + newProfile }
            extra.update { state ->
                val updatedSelection = when {
                    state.selectedPlayerIds.isEmpty() -> listOf(newProfile.id)
                    state.selectedPlayerIds.size == 1 -> state.selectedPlayerIds + newProfile.id
                    else -> listOf(state.selectedPlayerIds.first(), newProfile.id)
                }
                state.copy(selectedPlayerIds = updatedSelection.distinct())
            }
        }
    }

    fun saveProfile(
        profileId: String,
        name: String,
        ageYears: Int?,
        weightKg: Double?,
        heightCm: Int?,
        bodyFatPercent: Double?,
        healthSource: String?,
        healthConnectionStatus: String?,
    ) {
        viewModelScope.launch {
            repository.updateProfiles { current ->
                current.map { profile ->
                    if (profile.id != profileId) {
                        profile
                    } else {
                        profile.copy(
                            name = name.ifBlank { profile.name },
                            avatarLabel = deriveAvatarLabel(name.ifBlank { profile.name }),
                            ageYears = ageYears,
                            weightKg = weightKg,
                            heightCm = heightCm,
                            bodyFatPercent = bodyFatPercent,
                            healthSource = healthSource?.takeIf { it.isNotBlank() },
                            healthConnectionStatus = healthConnectionStatus?.takeIf { it.isNotBlank() },
                        )
                    }
                }
            }
            extra.update { it.copy(profileSheetProfileId = profileId) }
        }
    }

    fun selectChallenge(challengeId: String) {
        extra.update { it.copy(selectedChallengeId = challengeId) }
    }

    fun startSelectedSession() {
        val state = uiState.value
        val selectedChallenge = state.availableChallenges.firstOrNull { it.id == state.selectedChallengeId }
        sessionEngine.startSession(
            workoutType = state.selectedWorkoutType,
            playerIds = state.selectedPlayerIds,
            challenge = selectedChallenge?.takeIf {
                state.selectedWorkoutType == GymWorkoutType.CHALLENGE || state.selectedWorkoutType == GymWorkoutType.HYBRID
            },
            generatedWorkout = state.generatedWorkout.takeIf {
                state.selectedWorkoutType != GymWorkoutType.CHALLENGE && state.selectedWorkoutType != GymWorkoutType.MULTIPLAYER
            },
        )
        extra.update { it.copy(setupVisible = false, generatorStep = GymGeneratorStep.MUSCLES) }
    }

    fun pauseOrResumeSession() = sessionEngine.pauseOrResumeSession()

    fun endAndSaveSession() = sessionEngine.endSession(save = true)

    fun discardSession() {
        sessionEngine.endSession(save = false)
        extra.update { it.copy(dashboardTab = GymDashboardTab.WORKOUTS, setupVisible = false) }
    }

    fun dismissSummary() = sessionEngine.dismissSummary()

    fun clearStatusMessage() = sessionEngine.clearStatusMessage()

    fun connectDevice(deviceId: String) = sessionEngine.scanAndConnect(deviceId)

    fun disconnectDevice(deviceId: String) = sessionEngine.disconnectDevice(deviceId)

    fun cycleDeviceAssignment(deviceId: String) {
        viewModelScope.launch {
            repository.updateDevicePreferences { current ->
                val orderedPlayers = uiState.value.profiles.map { it.id }
                current.map { pref ->
                    if (pref.deviceId != deviceId) return@map pref
                    val currentIndex = orderedPlayers.indexOf(pref.assignedPlayerId)
                    val nextAssignment = when {
                        orderedPlayers.isEmpty() -> null
                        currentIndex == -1 -> orderedPlayers.first()
                        currentIndex == orderedPlayers.lastIndex -> null
                        else -> orderedPlayers[currentIndex + 1]
                    }
                    pref.copy(assignedPlayerId = nextAssignment)
                }
            }
        }
    }

    companion object {
        private const val SYNC_COMPLETE_MESSAGE_MS = 3_500L
        fun factory(
            application: Application,
            repository: GymRepository,
            contentRepository: GymContentRepository,
            sessionEngine: GymSessionEngine,
            healthConnectGateway: GymHealthConnectGateway,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return GymViewModel(application, repository, contentRepository, sessionEngine, healthConnectGateway) as T
            }
        }
    }
}

private fun resolveSelectedProfiles(
    profiles: List<GymProfile>,
    selectedPlayerIds: List<String>,
): List<GymProfile> {
    val ordered = selectedPlayerIds.mapNotNull { id -> profiles.firstOrNull { it.id == id } }
    return if (ordered.isNotEmpty()) ordered else profiles.take(1)
}

private fun buildWeeklyProgress(
    profile: GymProfile,
    history: List<GymSessionRecord>,
): GymWeeklyProgress {
    val startOfWeek = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    }.timeInMillis
    val sessionsThisWeek = history.filter { record ->
        record.startedAtEpochMs >= startOfWeek && record.players.any { it.profileId == profile.id }
    }
    val dayHits = MutableList(7) { false }
    sessionsThisWeek.forEach { record ->
        val calendar = Calendar.getInstance().apply { timeInMillis = record.startedAtEpochMs }
        val dayIndex = when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6
            else -> 0
        }
        dayHits[dayIndex] = true
    }
    return GymWeeklyProgress(
        days = dayHits,
        completedWorkouts = sessionsThisWeek.size,
        weeklyMinutes = sessionsThisWeek.sumOf { it.activeSeconds } / 60,
        weeklyCalories = sessionsThisWeek.sumOf { record -> record.players.firstOrNull { it.profileId == profile.id }?.metrics?.calories ?: 0 },
        weeklyVolumeKg = sessionsThisWeek.sumOf { record -> record.players.firstOrNull { it.profileId == profile.id }?.metrics?.strengthVolumeKg ?: 0.0 },
        weeklyDistanceKm = sessionsThisWeek.sumOf { record -> record.players.firstOrNull { it.profileId == profile.id }?.metrics?.distanceKm ?: 0.0 },
        streakDays = profile.streakDays,
    )
}

private fun buildDashboardStats(
    profile: GymProfile,
    history: List<GymSessionRecord>,
): GymDashboardStats {
    val profileSessions = history.filter { record -> record.players.any { it.profileId == profile.id } }
    val bestPower = profileSessions.maxOfOrNull { record ->
        record.players.firstOrNull { it.profileId == profile.id }?.metrics?.maxPowerWatts ?: 0
    } ?: 0
    return GymDashboardStats(
        workouts = profileSessions.size,
        timeMinutes = profileSessions.sumOf { it.activeSeconds } / 60,
        calories = profileSessions.sumOf { record -> record.players.firstOrNull { it.profileId == profile.id }?.metrics?.calories ?: 0 },
        streakDays = profile.streakDays,
        distanceKm = profileSessions.sumOf { record -> record.players.firstOrNull { it.profileId == profile.id }?.metrics?.distanceKm ?: 0.0 },
        bestPowerWatts = bestPower,
        strengthVolumeKg = profileSessions.sumOf { record -> record.players.firstOrNull { it.profileId == profile.id }?.metrics?.strengthVolumeKg ?: 0.0 },
        totalRepetitions = profileSessions.sumOf { record -> record.players.firstOrNull { it.profileId == profile.id }?.metrics?.repetitions ?: 0 },
    )
}

private val dashboardProfileAccents = listOf(
    0xFF57C7FF.toInt(),
    0xFFFF8A5B.toInt(),
    0xFF7CF7B8.toInt(),
    0xFFF7C96B.toInt(),
    0xFFBA7BFF.toInt(),
    0xFFFF6FA9.toInt(),
)

private fun deriveAvatarLabel(name: String): String =
    name.split(' ')
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .take(2)
        .ifBlank { "P" }

private fun deriveHeartRateSummary(
    profile: GymProfile,
    device: FitnessDeviceSnapshot?,
): String {
    val bpm = device?.lastTelemetry?.heartRate
    if (bpm != null) {
        return "$bpm bpm"
    }
    val deviceStatus = device?.state?.let { state ->
        when (state) {
            FitnessConnectionState.READY,
            FitnessConnectionState.CONNECTED,
            FitnessConnectionState.ACTIVE,
            FitnessConnectionState.PAUSED,
            FitnessConnectionState.RECONNECTING,
            FitnessConnectionState.CONNECTING,
            FitnessConnectionState.SCANNING,
            FitnessConnectionState.ERROR,
            FitnessConnectionState.DISCONNECTED,
                -> state.name.lowercase().replaceFirstChar { it.uppercase() }
        }
    }
    return deviceStatus
        ?: profile.healthConnectionStatus
        ?: profile.healthSource
        ?: "--"
}
