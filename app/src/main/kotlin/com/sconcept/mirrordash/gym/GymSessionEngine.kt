package com.sconcept.mirrordash.gym

import android.content.Context
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class GymSessionEngine private constructor(
    private val repository: GymRepository,
    private val adapterRegistry: GymAdapterRegistry,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _runtimeState = MutableStateFlow(
        GymRuntimeState(devices = defaultGymDevicePreferences().map { it.toSnapshot() }),
    )
    val runtimeState = _runtimeState.asStateFlow()

    private var storedProfiles = defaultGymProfiles()
    private var storedPreferences = defaultGymDevicePreferences()
    private var mockDevicesEnabled = true

    init {
        scope.launch {
            repository.storedState.collect { stored ->
                storedProfiles = stored.profiles.ifEmpty { defaultGymProfiles() }
                storedPreferences = stored.devicePreferences.ifEmpty { defaultGymDevicePreferences() }
                mockDevicesEnabled = stored.featureSettings.mockDevicesEnabled
                syncDevices()
            }
        }
        scope.launch { sessionTicker() }
    }

    fun scanAndConnect(deviceId: String) {
        val preference = storedPreferences.firstOrNull { it.deviceId == deviceId } ?: return
        val adapter = adapterRegistry.adapterFor(preference)
        if (!adapter.isAvailable(mockDevicesEnabled)) {
            _runtimeState.update { state ->
                state.copy(
                    devices = state.devices.map { device ->
                        if (device.deviceId == deviceId) {
                            device.copy(
                                state = FitnessConnectionState.DISCONNECTED,
                                errorMessage = "${adapter.integrationLabel} is unavailable right now",
                            )
                        } else {
                            device
                        }
                    },
                )
            }
            return
        }
        scope.launch {
            applyDeviceTransitions(
                deviceId = deviceId,
                transitions = adapter.connectSequence(preference),
                statusMessage = { _, _ -> null },
            )
        }
    }

    fun disconnectDevice(deviceId: String) {
        val preference = storedPreferences.firstOrNull { it.deviceId == deviceId } ?: return
        val adapter = adapterRegistry.adapterFor(preference)
        val activeSession = _runtimeState.value.activeSession
        if (activeSession != null) {
            scope.launch {
                applyDeviceTransitions(
                    deviceId = deviceId,
                    transitions = adapter.disconnectSequence(preference, activeSession = true),
                    statusMessage = { state, displayName ->
                        when (state) {
                            FitnessConnectionState.RECONNECTING -> "Trying to reconnect $displayName..."
                            FitnessConnectionState.ACTIVE -> "$displayName connected"
                            else -> null
                        }
                    },
                )
            }
            return
        }

        scope.launch {
            applyDeviceTransitions(
                deviceId = deviceId,
                transitions = adapter.disconnectSequence(preference, activeSession = false),
                statusMessage = { _, _ -> null },
            )
        }
    }

    fun startSession(
        workoutType: GymWorkoutType,
        playerIds: List<String>,
        challengeId: String?,
        generatedWorkout: GymGeneratedWorkoutPlan?,
    ) {
        val sessionId = "session_${System.currentTimeMillis()}"
        val selectedProfiles = storedProfiles.filter { it.id in playerIds }.ifEmpty { storedProfiles.take(1) }
        val challenge = defaultGymChallenges().firstOrNull { it.id == challengeId }
        _runtimeState.update { state ->
            val players = selectedProfiles.map { profile ->
                GymPlayerLiveStats(
                    profileId = profile.id,
                    displayName = profile.name,
                    avatarLabel = profile.avatarLabel,
                    accentColorArgb = profile.accentColorArgb,
                )
            }
            state.copy(
                devices = state.devices.map { device ->
                    if (device.assignedPlayerId in playerIds && device.state != FitnessConnectionState.DISCONNECTED) {
                        device.copy(state = FitnessConnectionState.ACTIVE, errorMessage = null)
                    } else {
                        device
                    }
                },
                latestSummary = null,
                activeSession = GymActiveSessionState(
                    sessionId = sessionId,
                    workoutType = workoutType,
                    challenge = challenge,
                    generatedWorkout = generatedWorkout,
                    startedAtEpochMs = System.currentTimeMillis(),
                    players = players,
                    primaryDeviceKind = when (workoutType) {
                        GymWorkoutType.STRENGTH -> FitnessDeviceKind.STRENGTH
                        GymWorkoutType.CYCLING -> FitnessDeviceKind.CARDIO
                        else -> FitnessDeviceKind.CARDIO
                    },
                    statusMessage = when {
                        challenge != null -> "Challenge armed for ${players.size} ${if (players.size == 1) "profile" else "profiles"}: ${challenge.title}"
                        generatedWorkout != null -> "Session live for ${players.size} ${if (players.size == 1) "profile" else "profiles"}: ${generatedWorkout.title}"
                        else -> "Session live for ${players.size} ${if (players.size == 1) "profile" else "profiles"}"
                    },
                ),
            )
        }
    }

    fun pauseOrResumeSession() {
        _runtimeState.update { state ->
            val session = state.activeSession ?: return@update state
            val paused = !session.isPaused
            state.copy(
                devices = state.devices.map { device ->
                    if (device.state == FitnessConnectionState.ACTIVE || device.state == FitnessConnectionState.PAUSED) {
                        device.copy(state = if (paused) FitnessConnectionState.PAUSED else FitnessConnectionState.ACTIVE)
                    } else {
                        device
                    }
                },
                activeSession = session.copy(
                    isPaused = paused,
                    statusMessage = if (paused) "Workout paused" else "Workout resumed",
                ),
            )
        }
    }

    fun clearStatusMessage() {
        _runtimeState.update { state ->
            state.copy(activeSession = state.activeSession?.copy(statusMessage = null))
        }
    }

    fun dismissSummary() {
        _runtimeState.update { it.copy(latestSummary = null) }
    }

    fun endSession(save: Boolean) {
        val runtime = _runtimeState.value
        val session = runtime.activeSession ?: return
        val record = GymSessionRecord(
            id = session.sessionId,
            startedAtEpochMs = session.startedAtEpochMs,
            endedAtEpochMs = System.currentTimeMillis(),
            workoutType = session.workoutType,
            challengeId = session.challenge?.id,
            challengeTitle = session.challenge?.title,
            durationSeconds = session.elapsedSeconds,
            activeSeconds = session.activeSeconds,
            pausedSeconds = session.pausedSeconds,
            players = session.players.map { player ->
                GymSessionPlayerRecord(
                    profileId = player.profileId,
                    displayName = player.displayName,
                    score = player.score,
                    xpEarned = player.xpEarned,
                    achievements = player.achievements,
                    personalRecords = player.personalRecords,
                    metrics = GymMetricSummary(
                        calories = player.calories,
                        distanceKm = player.distanceKm,
                        strengthVolumeKg = player.volumeKg,
                        repetitions = player.repetitions,
                        averageHeartRate = player.heartRate,
                        maxHeartRate = player.heartRate,
                        averagePowerWatts = player.powerWatts,
                        maxPowerWatts = player.powerWatts,
                    ),
                )
            },
            notes = session.generatedWorkout?.title.orEmpty(),
        )
        if (save) {
            scope.launch { repository.appendSession(record) }
        }
        _runtimeState.update { state ->
            state.copy(
                devices = state.devices.map { device ->
                    if (device.state == FitnessConnectionState.ACTIVE || device.state == FitnessConnectionState.PAUSED) {
                        device.copy(state = FitnessConnectionState.READY)
                    } else {
                        device
                    }
                },
                activeSession = null,
                latestSummary = GymSessionSummaryState(
                    session = record,
                    title = session.generatedWorkout?.title ?: "Workout saved",
                    subtitle = "${record.durationSeconds / 60} min / ${record.players.sumOf { it.score }} pts",
                ),
            )
        }
    }

    private suspend fun sessionTicker() {
        while (scope.isActive) {
            delay(1000)
            tick()
        }
    }

    private fun tick() {
        val current = _runtimeState.value
        val session = current.activeSession
        if (session == null) {
            _runtimeState.update { state ->
                state.copy(
                    devices = state.devices.map { device ->
                        if (device.lastTelemetry != null) {
                            device.copy(lastPacketAgeSeconds = (device.lastPacketAgeSeconds ?: 0) + 1)
                        } else {
                            device
                        }
                    },
                )
            }
            return
        }

        if (session.isPaused) {
            val clock = GymDomainLogic.tickClock(
                GymSessionClock(
                    elapsedSeconds = session.elapsedSeconds,
                    activeSeconds = session.activeSeconds,
                    pausedSeconds = session.pausedSeconds,
                ),
                paused = true,
            )
            _runtimeState.update { state ->
                state.copy(
                    devices = state.devices.map { device ->
                        if (device.state == FitnessConnectionState.PAUSED) {
                            device.copy(lastPacketAgeSeconds = (device.lastPacketAgeSeconds ?: 0) + 1)
                        } else {
                            device
                        }
                    },
                    activeSession = state.activeSession?.copy(
                        elapsedSeconds = clock.elapsedSeconds,
                        activeSeconds = clock.activeSeconds,
                        pausedSeconds = clock.pausedSeconds,
                    ),
                )
            }
            return
        }

        val nextClock = GymDomainLogic.tickClock(
            GymSessionClock(
                elapsedSeconds = session.elapsedSeconds,
                activeSeconds = session.activeSeconds,
                pausedSeconds = session.pausedSeconds,
            ),
            paused = false,
        )
        val nextElapsed = nextClock.elapsedSeconds
        val nextActive = nextClock.activeSeconds
        val updatedDevices = current.devices.map { device ->
            if (device.assignedPlayerId !in session.players.map { it.profileId }) return@map device
            if (device.state == FitnessConnectionState.DISCONNECTED) return@map device
            val telemetry = adapterRegistry.adapterFor(device).sampleTelemetry(device, nextElapsed, nextActive)
            device.copy(
                state = FitnessConnectionState.ACTIVE,
                lastTelemetry = telemetry,
                lastPacketAgeSeconds = 0,
                errorMessage = null,
            )
        }

        val events = session.recentEvents.toMutableList()
        val updatedPlayers = session.players.map { player ->
            val assignedDevices = updatedDevices.filter { it.assignedPlayerId == player.profileId }
            val updated = evolvePlayer(player, assignedDevices, nextActive)
            val event = maybeCreateScoreEvent(player, updated, session.challenge, nextActive)
            if (event != null) {
                events += event
            }
            updated
        }

        _runtimeState.update { state ->
            state.copy(
                devices = updatedDevices,
                activeSession = session.copy(
                    elapsedSeconds = nextElapsed,
                    activeSeconds = nextActive,
                    players = updatedPlayers,
                    primaryDeviceKind = primaryDeviceKind(updatedDevices, session.workoutType),
                    recentEvents = events.takeLast(5),
                    statusMessage = when {
                        session.challenge != null && nextActive == session.challenge.durationSeconds -> "${session.challenge.title} complete"
                        else -> null
                    },
                ),
            )
        }
    }

    private fun evolvePlayer(
        player: GymPlayerLiveStats,
        assignedDevices: List<FitnessDeviceSnapshot>,
        activeSeconds: Int,
    ): GymPlayerLiveStats {
        val cardio = assignedDevices.firstOrNull { it.kind == FitnessDeviceKind.CARDIO }?.lastTelemetry
        val strength = assignedDevices.firstOrNull { it.kind == FitnessDeviceKind.STRENGTH }?.lastTelemetry
        val hr = assignedDevices.firstOrNull { it.kind == FitnessDeviceKind.HEART_RATE }?.lastTelemetry
        val heartRate = hr?.heartRate
        val zone = GymDomainLogic.heartRateZone(heartRate)
        val multiplier = when {
            heartRate == null -> 1f
            heartRate >= 165 -> 2f
            heartRate >= 150 -> 1.5f
            heartRate >= 130 -> 1.2f
            else -> 1f
        }
        val cadence = cardio?.cadenceRpm?.roundToInt()
        val power = cardio?.powerWatts?.roundToInt()
        val newReps = strength?.repetitions ?: player.repetitions
        val repDelta = (newReps - player.repetitions).coerceAtLeast(0)
        val inTarget = (cadence ?: 0) in 84..100 || repDelta > 0 || (heartRate ?: 0) in 130..165
        val combo = if (inTarget) player.combo + 1 else 0
        val scoreGain = (GymDomainLogic.scoreGain(heartRate = heartRate, cadence = cadence, repDelta = repDelta, combo = combo) * multiplier).toInt()
        val achievements = player.achievements.toMutableList()
        if (combo == 4 && "Perfect Zone x4" !in achievements) achievements += "Perfect Zone x4"
        if ((power ?: 0) >= 300 && "Power Breakthrough" !in achievements) achievements += "Power Breakthrough"
        if ((strength?.totalVolumeKg ?: 0.0) >= 500.0 && "Volume Builder" !in achievements) achievements += "Volume Builder"
        val records = player.personalRecords.toMutableList()
        if ((power ?: 0) >= 300 && "New Power PR 300W" !in records) records += "New Power PR 300W"
        if ((strength?.totalVolumeKg ?: 0.0) >= 520.0 && "Set Volume PR 520KG" !in records) records += "Set Volume PR 520KG"
        val calories = ((cardio?.calories ?: 0.0) + (strength?.calories ?: 0.0) + (hr?.calories ?: 0.0)).roundToInt()
        return player.copy(
            score = player.score + scoreGain,
            xpEarned = player.xpEarned + maxOf(1, scoreGain / 12),
            combo = combo,
            heartRate = heartRate,
            heartRateZone = zone,
            effortMultiplier = multiplier,
            calories = calories,
            powerWatts = power,
            cadenceRpm = cadence,
            speedKph = cardio?.speedKph,
            resistance = cardio?.resistance?.roundToInt(),
            distanceKm = cardio?.distanceKm ?: player.distanceKm,
            repetitions = newReps,
            loadLeftKg = strength?.loadLeftKg,
            loadRightKg = strength?.loadRightKg,
            volumeKg = strength?.totalVolumeKg ?: player.volumeKg,
            achievements = achievements.distinct(),
            personalRecords = records.distinct(),
        )
    }

    private fun maybeCreateScoreEvent(
        previous: GymPlayerLiveStats,
        current: GymPlayerLiveStats,
        challenge: GymChallengeDefinition?,
        activeSeconds: Int,
    ): GymScoreEvent? {
        if (current.personalRecords.size > previous.personalRecords.size) {
            val latest = current.personalRecords.last()
            return GymScoreEvent(
                id = System.currentTimeMillis(),
                title = "NEW PR",
                detail = latest.removePrefix("New ").uppercase(),
                scoreDelta = 75,
            )
        }
        if (current.combo >= 4 && current.combo > previous.combo) {
            return GymScoreEvent(
                id = System.currentTimeMillis(),
                title = "PERFECT ZONE",
                detail = "x${current.combo}",
                scoreDelta = 50,
            )
        }
        if (current.repetitions > previous.repetitions) {
            return GymScoreEvent(
                id = System.currentTimeMillis(),
                title = "${current.repetitions} REP STREAK",
                detail = current.displayName,
                scoreDelta = 40,
            )
        }
        if (challenge != null && activeSeconds == challenge.durationSeconds) {
            return GymScoreEvent(
                id = System.currentTimeMillis(),
                title = challenge.title.uppercase(),
                detail = "Challenge complete",
                scoreDelta = 100,
            )
        }
        return null
    }

    private fun primaryDeviceKind(
        devices: List<FitnessDeviceSnapshot>,
        workoutType: GymWorkoutType,
    ): FitnessDeviceKind {
        return when (workoutType) {
            GymWorkoutType.STRENGTH -> FitnessDeviceKind.STRENGTH
            GymWorkoutType.CYCLING -> FitnessDeviceKind.CARDIO
            else -> when {
                devices.any { it.kind == FitnessDeviceKind.STRENGTH && it.state == FitnessConnectionState.ACTIVE } -> FitnessDeviceKind.STRENGTH
                else -> FitnessDeviceKind.CARDIO
            }
        }
    }

    private suspend fun applyDeviceTransitions(
        deviceId: String,
        transitions: List<GymDeviceTransition>,
        statusMessage: (FitnessConnectionState, String) -> String?,
    ) {
        transitions.forEach { transition ->
            if (transition.delayMs > 0) {
                delay(transition.delayMs)
            }
            _runtimeState.update { state ->
                val deviceName = state.devices.firstOrNull { it.deviceId == deviceId }?.displayName ?: "device"
                state.copy(
                    devices = state.devices.map { device ->
                        if (device.deviceId == deviceId) {
                            device.applyTransition(transition)
                        } else {
                            device
                        }
                    },
                    activeSession = state.activeSession?.copy(
                        statusMessage = statusMessage(transition.state, deviceName),
                    ),
                )
            }
        }
    }

    private fun FitnessDeviceSnapshot.applyTransition(transition: GymDeviceTransition): FitnessDeviceSnapshot = copy(
        state = transition.state,
        lastTelemetry = if (transition.clearTelemetry) null else lastTelemetry,
        lastPacketAgeSeconds = when {
            transition.clearTelemetry -> null
            transition.resetPacketAge -> 0
            else -> lastPacketAgeSeconds
        },
        reconnectCount = reconnectCount + if (transition.incrementReconnectCount) 1 else 0,
        errorMessage = transition.errorMessage,
    )

    private fun syncDevices() {
        _runtimeState.update { current ->
            val existingById = current.devices.associateBy { it.deviceId }
            val devices = storedPreferences.map { pref ->
                val existing = existingById[pref.deviceId]
                val adapter = adapterRegistry.adapterFor(pref)
                val base = adapter.buildSnapshot(pref, existing)
                if (!mockDevicesEnabled) {
                    base.copy(
                        errorMessage = "${adapter.integrationLabel} is unavailable while mock equipment is disabled",
                        state = FitnessConnectionState.DISCONNECTED,
                        lastTelemetry = null,
                        lastPacketAgeSeconds = null,
                    )
                } else {
                    base
                }
            }
            current.copy(devices = devices)
        }
    }

    companion object {
        @Volatile
        private var instance: GymSessionEngine? = null

        fun get(
            context: Context,
            repository: GymRepository,
            adapterRegistry: GymAdapterRegistry = GymAdapterRegistry.createDefault(),
        ): GymSessionEngine = instance ?: synchronized(this) {
            instance ?: GymSessionEngine(repository, adapterRegistry).also { instance = it }
        }
    }
}
