package com.sconcept.mirrordash.gym

import kotlinx.serialization.Serializable

@Serializable
enum class GymWorkoutType {
    FREE_WORKOUT,
    STRENGTH,
    CYCLING,
    HYBRID,
    CHALLENGE,
    MULTIPLAYER,
}

enum class GymDashboardTab {
    HOME,
    WORKOUTS,
}

enum class GymGeneratorStep {
    MUSCLES,
    EXERCISE_COUNT,
    PREVIEW,
}

enum class GymMuscleGroup {
    CHEST, BACK, SHOULDERS, ARMS, CORE, GLUTES, QUADS, HAMSTRINGS, CALVES,
}

enum class GymTrainingGoal {
    BUILD_MUSCLE,
    GAIN_STRENGTH,
    LOSE_WEIGHT,
    MOBILITY,
    RECOVERY,
}

enum class GymTrainingLevel {
    NOVICE,
    BEGINNER,
    INTERMEDIATE,
    ADVANCED,
}

enum class GymEquipmentOption {
    BODYWEIGHT,
    DUMBBELLS,
    BARBELL,
    KETTLEBELLS,
    BANDS,
    CABLES,
    BENCH,
    BIKE,
    ROWING,
}

data class GymGeneratorPreferences(
    val goal: GymTrainingGoal = GymTrainingGoal.BUILD_MUSCLE,
    val level: GymTrainingLevel = GymTrainingLevel.BEGINNER,
    val equipment: List<GymEquipmentOption> = defaultGymGeneratorPreferences().equipment,
    val muscleGroups: List<GymMuscleGroup> = emptyList(),
    val durationMinutes: Int = 45,
    val exerciseCount: Int = 6,
)

data class GymGeneratedExercise(
    val order: Int,
    val title: String,
    val focusLabel: String,
    val targetedMuscles: List<String>,
    val durationMinutes: Int,
    val repScheme: String,
    val coachingCue: String,
)

data class GymGeneratedWorkoutPlan(
    val title: String,
    val subtitle: String,
    val estimatedMinutes: Int,
    val targetedMuscles: List<String>,
    val focusSummary: String,
    val exercises: List<GymGeneratedExercise>,
)

@Serializable
enum class FitnessDeviceKind {
    STRENGTH,
    CARDIO,
    HEART_RATE,
}

@Serializable
enum class FitnessConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    READY,
    ACTIVE,
    PAUSED,
    RECONNECTING,
    ERROR,
}

@Serializable
enum class GymHudMode {
    MINIMAL,
    EXPANDED,
}

@Serializable
data class GymProfile(
    val id: String,
    val name: String,
    val avatarLabel: String,
    val accentColorArgb: Int,
    val ageYears: Int? = null,
    val weightKg: Double? = null,
    val heightCm: Int? = null,
    val bodyFatPercent: Double? = null,
    val healthSource: String? = null,
    val healthConnectionStatus: String? = null,
    val preferredUnits: String = "metric",
    val totalXp: Int = 0,
    val totalWorkouts: Int = 0,
    val streakDays: Int = 0,
    val preferredHeartRateDeviceId: String? = null,
    /** Personal workout intent; generator combines the active players' preferences. */
    val workoutGoal: GymTrainingGoal = GymTrainingGoal.BUILD_MUSCLE,
    val workoutLevel: GymTrainingLevel = GymTrainingLevel.BEGINNER,
    val progression: GymProgressionProfile = GymProgressionProfile(),
)

@Serializable
data class GymFeatureSettings(
    val soundsEnabled: Boolean = true,
    val countdownEnabled: Boolean = true,
    val showHeartRate: Boolean = true,
    val showCalories: Boolean = true,
    val showScore: Boolean = true,
    val mockDevicesEnabled: Boolean = true,
    val hudMode: GymHudMode = GymHudMode.EXPANDED,
)

@Serializable
data class FitnessDevicePreference(
    val deviceId: String,
    val displayName: String,
    val subtitle: String,
    val kind: FitnessDeviceKind,
    val adapterId: String = defaultAdapterId(kind),
    val preferred: Boolean = false,
    val autoConnect: Boolean = true,
    val remembered: Boolean = true,
    val assignedPlayerId: String? = null,
)

@Serializable
data class GymChallengeDefinition(
    val id: String,
    val title: String,
    val subtitle: String,
    val durationSeconds: Int,
    val workoutType: GymWorkoutType,
    val difficultyLabel: String,
    val equipmentLabel: String,
    val bestLabel: String? = null,
)

@Serializable
data class GymMetricSummary(
    val calories: Int = 0,
    val distanceKm: Double = 0.0,
    val strengthVolumeKg: Double = 0.0,
    val repetitions: Int = 0,
    val averageHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val averagePowerWatts: Int? = null,
    val maxPowerWatts: Int? = null,
)

@Serializable
data class GymSessionPlayerRecord(
    val profileId: String,
    val displayName: String,
    val score: Int,
    val xpEarned: Int,
    val achievements: List<String> = emptyList(),
    val personalRecords: List<String> = emptyList(),
    val metrics: GymMetricSummary = GymMetricSummary(),
)

@Serializable
data class GymSessionRecord(
    val id: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long,
    val workoutType: GymWorkoutType,
    val challengeId: String? = null,
    val challengeTitle: String? = null,
    val durationSeconds: Int,
    val activeSeconds: Int,
    val pausedSeconds: Int,
    val players: List<GymSessionPlayerRecord>,
    val notes: String = "",
)

data class FitnessTelemetry(
    val timestampEpochMs: Long,
    val heartRate: Int? = null,
    val calories: Double? = null,
    val powerWatts: Double? = null,
    val cadenceRpm: Double? = null,
    val speedKph: Double? = null,
    val resistance: Double? = null,
    val distanceKm: Double? = null,
    val loadLeftKg: Double? = null,
    val loadRightKg: Double? = null,
    val repetitions: Int? = null,
    val setNumber: Int? = null,
    val rangeOfMotion: Double? = null,
    val totalVolumeKg: Double? = null,
    val timeUnderTensionMs: Long? = null,
)

data class FitnessDeviceSnapshot(
    val deviceId: String,
    val displayName: String,
    val subtitle: String,
    val kind: FitnessDeviceKind,
    val adapterId: String = defaultAdapterId(kind),
    val integrationLabel: String = "",
    val state: FitnessConnectionState = FitnessConnectionState.DISCONNECTED,
    val assignedPlayerId: String? = null,
    val autoConnect: Boolean = true,
    val preferred: Boolean = false,
    val lastTelemetry: FitnessTelemetry? = null,
    val lastPacketAgeSeconds: Int? = null,
    val reconnectCount: Int = 0,
    val errorMessage: String? = null,
)

data class GymScoreEvent(
    val id: Long,
    val title: String,
    val detail: String,
    val scoreDelta: Int,
)

data class GymPlayerLiveStats(
    val profileId: String,
    val displayName: String,
    val avatarLabel: String,
    val accentColorArgb: Int,
    val score: Int = 0,
    val xpEarned: Int = 0,
    val combo: Int = 0,
    val heartRate: Int? = null,
    val heartRateZone: String? = null,
    val targetHeartRate: Int = 145,
    val effortMultiplier: Float = 1f,
    val calories: Int = 0,
    val powerWatts: Int? = null,
    val cadenceRpm: Int? = null,
    val speedKph: Double? = null,
    val resistance: Int? = null,
    val distanceKm: Double = 0.0,
    val repetitions: Int = 0,
    val loadLeftKg: Double? = null,
    val loadRightKg: Double? = null,
    val volumeKg: Double = 0.0,
    val achievements: List<String> = emptyList(),
    val personalRecords: List<String> = emptyList(),
)

data class GymActiveSessionState(
    val sessionId: String,
    val workoutType: GymWorkoutType,
    val challenge: GymChallengeDefinition? = null,
    val generatedWorkout: GymGeneratedWorkoutPlan? = null,
    val startedAtEpochMs: Long,
    val elapsedSeconds: Int = 0,
    val activeSeconds: Int = 0,
    val pausedSeconds: Int = 0,
    val isPaused: Boolean = false,
    val players: List<GymPlayerLiveStats> = emptyList(),
    val primaryDeviceKind: FitnessDeviceKind = FitnessDeviceKind.CARDIO,
    val recentEvents: List<GymScoreEvent> = emptyList(),
    val statusMessage: String? = null,
)

data class GymSessionSummaryState(
    val session: GymSessionRecord,
    val title: String,
    val subtitle: String,
)

data class GymStoredState(
    val featureSettings: GymFeatureSettings,
    val profiles: List<GymProfile>,
    val devicePreferences: List<FitnessDevicePreference>,
    val sessionHistory: List<GymSessionRecord>,
)

data class GymRuntimeState(
    val devices: List<FitnessDeviceSnapshot> = emptyList(),
    val activeSession: GymActiveSessionState? = null,
    val latestSummary: GymSessionSummaryState? = null,
)

data class GymWeeklyProgress(
    val days: List<Boolean> = List(7) { false },
    val completedWorkouts: Int = 0,
    val targetWorkouts: Int = 5,
    val weeklyMinutes: Int = 0,
    val weeklyCalories: Int = 0,
    val weeklyVolumeKg: Double = 0.0,
    val weeklyDistanceKm: Double = 0.0,
    val streakDays: Int = 0,
)

data class GymDashboardStats(
    val workouts: Int = 0,
    val timeMinutes: Int = 0,
    val calories: Int = 0,
    val streakDays: Int = 0,
    val distanceKm: Double = 0.0,
    val bestPowerWatts: Int = 0,
    val strengthVolumeKg: Double = 0.0,
    val totalRepetitions: Int = 0,
)

data class GymProfileDashboardSnapshot(
    val profile: GymProfile,
    val weeklyProgress: GymWeeklyProgress = GymWeeklyProgress(),
    val dashboardStats: GymDashboardStats = GymDashboardStats(),
    val heartRateSummary: String = "--",
)

data class GymUiState(
    val featureSettings: GymFeatureSettings = GymFeatureSettings(),
    val profiles: List<GymProfile> = defaultGymProfiles(),
    val devicePreferences: List<FitnessDevicePreference> = defaultGymDevicePreferences(),
    val devices: List<FitnessDeviceSnapshot> = defaultGymDevicePreferences().map { it.toSnapshot() },
    val sessionHistory: List<GymSessionRecord> = emptyList(),
    val weeklyProgress: GymWeeklyProgress = GymWeeklyProgress(),
    val dashboardStats: GymDashboardStats = GymDashboardStats(),
    val activeSession: GymActiveSessionState? = null,
    val latestSummary: GymSessionSummaryState? = null,
    val availableChallenges: List<GymChallengeDefinition> = defaultGymChallenges(),
    val dashboardTab: GymDashboardTab = GymDashboardTab.HOME,
    val setupVisible: Boolean = false,
    val connectionCenterExpanded: Boolean = false,
    val selectedWorkoutType: GymWorkoutType = GymWorkoutType.HYBRID,
    val selectedPlayerIds: List<String> = defaultGymProfiles().take(1).map { it.id },
    val selectedProfileDashboards: List<GymProfileDashboardSnapshot> = emptyList(),
    val activeProfileCount: Int = 1,
    val profileSheetProfile: GymProfile? = null,
    val selectedChallengeId: String? = defaultGymChallenges().firstOrNull()?.id,
    val workoutLibraryFilter: String = "All",
    val generatorStep: GymGeneratorStep = GymGeneratorStep.MUSCLES,
    val generatorPreferences: GymGeneratorPreferences = defaultGymGeneratorPreferences(),
    val generatedWorkout: GymGeneratedWorkoutPlan = buildGymGeneratedWorkoutPlan(defaultGymGeneratorPreferences()),
    val exerciseCatalogCount: Int = 0,
    val exerciseCatalogHighlights: List<String> = emptyList(),
    val exerciseCatalog: List<GymExerciseCatalogEntry> = emptyList(),
)

fun defaultGymProfiles(): List<GymProfile> = listOf(
    GymProfile(
        id = "player_1",
        name = "Player 1",
        avatarLabel = "P1",
        accentColorArgb = 0xFF57C7FF.toInt(),
        totalXp = 8420,
        totalWorkouts = 18,
        streakDays = 7,
    ),
    GymProfile(
        id = "player_2",
        name = "Player 2",
        avatarLabel = "P2",
        accentColorArgb = 0xFFFF8A5B.toInt(),
        totalXp = 6840,
        totalWorkouts = 14,
        streakDays = 4,
    ),
)

fun defaultGymDevicePreferences(): List<FitnessDevicePreference> = listOf(
    FitnessDevicePreference(
        deviceId = "vitruvian_mock",
        displayName = "Vitruvian",
        subtitle = "Trainer+",
        kind = FitnessDeviceKind.STRENGTH,
        adapterId = GymBuiltInAdapterIds.VITRUVIAN_COMMUNITY,
        preferred = true,
        assignedPlayerId = "player_1",
    ),
    FitnessDevicePreference(
        deviceId = "echelon_mock",
        displayName = "Echelon Bike",
        subtitle = "EX-5",
        kind = FitnessDeviceKind.CARDIO,
        adapterId = GymBuiltInAdapterIds.ECHELON_COMMUNITY,
        preferred = true,
        assignedPlayerId = "player_1",
    ),
    FitnessDevicePreference(
        deviceId = "watch_player_1",
        displayName = "Heart Rate",
        subtitle = "Makensley's Watch",
        kind = FitnessDeviceKind.HEART_RATE,
        adapterId = GymBuiltInAdapterIds.HEART_RATE_RELAY,
        preferred = true,
        assignedPlayerId = "player_1",
    ),
    FitnessDevicePreference(
        deviceId = "watch_player_2",
        displayName = "Heart Rate",
        subtitle = "Valerie's Polar H10",
        kind = FitnessDeviceKind.HEART_RATE,
        adapterId = GymBuiltInAdapterIds.HEART_RATE_RELAY,
        preferred = true,
        assignedPlayerId = "player_2",
    ),
)

fun defaultGymChallenges(): List<GymChallengeDefinition> = listOf(
    GymChallengeDefinition(
        id = "sprint_30",
        title = "Sprint 30",
        subtitle = "30 seconds of maximum controlled output",
        durationSeconds = 30,
        workoutType = GymWorkoutType.CHALLENGE,
        difficultyLabel = "Explosive",
        equipmentLabel = "Bike",
        bestLabel = "584 W",
    ),
    GymChallengeDefinition(
        id = "volume_battle",
        title = "Volume Battle",
        subtitle = "Hit the highest quality lifting volume",
        durationSeconds = 600,
        workoutType = GymWorkoutType.CHALLENGE,
        difficultyLabel = "Strength",
        equipmentLabel = "Vitruvian",
        bestLabel = "11,240 kg",
    ),
    GymChallengeDefinition(
        id = "gym_gauntlet",
        title = "Gym Gauntlet",
        subtitle = "Bike, lift, sprint, repeat",
        durationSeconds = 900,
        workoutType = GymWorkoutType.HYBRID,
        difficultyLabel = "Hybrid",
        equipmentLabel = "Bike + Strength",
        bestLabel = "12:42",
    ),
)

fun FitnessDevicePreference.toSnapshot(): FitnessDeviceSnapshot = FitnessDeviceSnapshot(
    deviceId = deviceId,
    displayName = displayName,
    subtitle = subtitle,
    kind = kind,
    adapterId = adapterId,
    assignedPlayerId = assignedPlayerId,
    autoConnect = autoConnect,
    preferred = preferred,
)

val GymTrainingGoal.displayLabel: String
    get() = when (this) {
        GymTrainingGoal.BUILD_MUSCLE -> "Build Muscle"
        GymTrainingGoal.GAIN_STRENGTH -> "Gain Strength"
        GymTrainingGoal.LOSE_WEIGHT -> "Lose Weight"
        GymTrainingGoal.MOBILITY -> "Mobility"
        GymTrainingGoal.RECOVERY -> "Recovery"
    }

val GymTrainingGoal.description: String
    get() = when (this) {
        GymTrainingGoal.BUILD_MUSCLE -> "Hypertrophy-focused volume with short rests and clean tempo."
        GymTrainingGoal.GAIN_STRENGTH -> "Compound lifts, lower rep targets, and more recovery per set."
        GymTrainingGoal.LOSE_WEIGHT -> "Continuous work blocks with low setup friction and elevated heart rate."
        GymTrainingGoal.MOBILITY -> "Move better with range, posture, and trunk control at the center."
        GymTrainingGoal.RECOVERY -> "Reset tissues and joints with lower-intensity restorative work."
    }

val GymTrainingLevel.displayLabel: String
    get() = when (this) {
        GymTrainingLevel.NOVICE -> "Novice"
        GymTrainingLevel.BEGINNER -> "Beginner"
        GymTrainingLevel.INTERMEDIATE -> "Intermediate"
        GymTrainingLevel.ADVANCED -> "Advanced"
    }

val GymTrainingLevel.description: String
    get() = when (this) {
        GymTrainingLevel.NOVICE -> "New to structured training and building movement confidence."
        GymTrainingLevel.BEGINNER -> "Comfortable with basic lifts and ready for more volume."
        GymTrainingLevel.INTERMEDIATE -> "Consistent training base with solid technique across patterns."
        GymTrainingLevel.ADVANCED -> "Ready for denser loading, bigger complexity, and harder finishers."
    }

val GymEquipmentOption.displayLabel: String
    get() = when (this) {
        GymEquipmentOption.BODYWEIGHT -> "Bodyweight"
        GymEquipmentOption.DUMBBELLS -> "Dumbbells"
        GymEquipmentOption.BARBELL -> "Barbell"
        GymEquipmentOption.KETTLEBELLS -> "Kettlebells"
        GymEquipmentOption.BANDS -> "Bands"
        GymEquipmentOption.CABLES -> "Cables"
        GymEquipmentOption.BENCH -> "Bench"
        GymEquipmentOption.BIKE -> "Bike"
        GymEquipmentOption.ROWING -> "Rower"
    }

val GymMuscleGroup.displayLabel: String
    get() = when (this) {
        GymMuscleGroup.CHEST -> "Chest"
        GymMuscleGroup.BACK -> "Back"
        GymMuscleGroup.SHOULDERS -> "Shoulders"
        GymMuscleGroup.ARMS -> "Arms"
        GymMuscleGroup.CORE -> "Core"
        GymMuscleGroup.GLUTES -> "Glutes"
        GymMuscleGroup.QUADS -> "Quads"
        GymMuscleGroup.HAMSTRINGS -> "Hamstrings"
        GymMuscleGroup.CALVES -> "Calves"
    }

val GymGeneratorStep.displayLabel: String
    get() = when (this) {
        GymGeneratorStep.MUSCLES -> "Muscles"
        GymGeneratorStep.EXERCISE_COUNT -> "Size"
        GymGeneratorStep.PREVIEW -> "Preview"
    }

val GymDashboardTab.displayLabel: String
    get() = when (this) {
        GymDashboardTab.HOME -> "Home"
        GymDashboardTab.WORKOUTS -> "Workouts"
    }

val GymWorkoutType.displayLabel: String
    get() = when (this) {
        GymWorkoutType.FREE_WORKOUT -> "Quick Start"
        GymWorkoutType.STRENGTH -> "Generator"
        GymWorkoutType.CYCLING -> "Cycling"
        GymWorkoutType.HYBRID -> "Program"
        GymWorkoutType.CHALLENGE -> "Challenge"
        GymWorkoutType.MULTIPLAYER -> "Multiplayer"
    }
