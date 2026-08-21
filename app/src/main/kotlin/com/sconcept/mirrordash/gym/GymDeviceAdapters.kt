package com.sconcept.mirrordash.gym

import android.content.Context
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

object GymBuiltInAdapterIds {
    const val VITRUVIAN_COMMUNITY = "vitruvian.community.mock"
    const val ECHELON_COMMUNITY = "echelon.community.mock"
    const val HEART_RATE_RELAY = "heartrate.bridge.mock"
    const val SAMSUNG_HEALTH_CONNECT = "samsung.health.connect"
}

fun defaultAdapterId(kind: FitnessDeviceKind): String = when (kind) {
    FitnessDeviceKind.STRENGTH -> GymBuiltInAdapterIds.VITRUVIAN_COMMUNITY
    FitnessDeviceKind.CARDIO -> GymBuiltInAdapterIds.ECHELON_COMMUNITY
    FitnessDeviceKind.HEART_RATE -> GymBuiltInAdapterIds.HEART_RATE_RELAY
}

data class GymDeviceTransition(
    val delayMs: Long,
    val state: FitnessConnectionState,
    val errorMessage: String? = null,
    val clearTelemetry: Boolean = false,
    val resetPacketAge: Boolean = false,
    val incrementReconnectCount: Boolean = false,
)

interface GymDeviceAdapter {
    val adapterId: String
    val integrationLabel: String

    fun isAvailable(mockDevicesEnabled: Boolean): Boolean = mockDevicesEnabled

    fun buildSnapshot(
        preference: FitnessDevicePreference,
        existing: FitnessDeviceSnapshot?,
    ): FitnessDeviceSnapshot

    fun connectSequence(preference: FitnessDevicePreference): List<GymDeviceTransition>

    fun disconnectSequence(
        preference: FitnessDevicePreference,
        activeSession: Boolean,
    ): List<GymDeviceTransition>

    fun sampleTelemetry(
        device: FitnessDeviceSnapshot,
        elapsedSeconds: Int,
        activeSeconds: Int,
    ): FitnessTelemetry?

    fun onConnected(preference: FitnessDevicePreference) = Unit

    fun refreshTelemetry() = Unit

    fun statusMessage(): String? = null
}

class GymAdapterRegistry(
    adapters: List<GymDeviceAdapter>,
) {
    private val adaptersById = adapters.associateBy { it.adapterId }

    fun adapterFor(preference: FitnessDevicePreference): GymDeviceAdapter =
        adaptersById[preference.adapterId] ?: fallbackAdapter(preference.kind)

    fun adapterFor(device: FitnessDeviceSnapshot): GymDeviceAdapter =
        adaptersById[device.adapterId] ?: fallbackAdapter(device.kind)

    private fun fallbackAdapter(kind: FitnessDeviceKind): GymDeviceAdapter =
        adaptersById.getValue(defaultAdapterId(kind))

    companion object {
        fun createDefault(healthConnectGateway: GymHealthConnectGateway): GymAdapterRegistry = GymAdapterRegistry(
            listOf(
                VitruvianCommunityAdapter,
                EchelonCommunityAdapter,
                HeartRateRelayAdapter,
                SamsungHealthConnectAdapter(healthConnectGateway),
            ),
        )
    }
}

private fun mergeSnapshot(
    preference: FitnessDevicePreference,
    existing: FitnessDeviceSnapshot?,
    integrationLabel: String,
): FitnessDeviceSnapshot =
    (existing ?: preference.toSnapshot()).copy(
        deviceId = preference.deviceId,
        displayName = preference.displayName,
        subtitle = preference.subtitle,
        kind = preference.kind,
        assignedPlayerId = preference.assignedPlayerId,
        autoConnect = preference.autoConnect,
        preferred = preference.preferred,
        adapterId = preference.adapterId,
        integrationLabel = integrationLabel,
    )

private object VitruvianCommunityAdapter : GymDeviceAdapter {
    override val adapterId: String = GymBuiltInAdapterIds.VITRUVIAN_COMMUNITY
    override val integrationLabel: String = "Vitruvian Community Layer"

    override fun buildSnapshot(
        preference: FitnessDevicePreference,
        existing: FitnessDeviceSnapshot?,
    ): FitnessDeviceSnapshot = mergeSnapshot(preference, existing, integrationLabel)

    override fun connectSequence(preference: FitnessDevicePreference): List<GymDeviceTransition> = listOf(
        GymDeviceTransition(delayMs = 0, state = FitnessConnectionState.SCANNING, errorMessage = null),
        GymDeviceTransition(delayMs = 800, state = FitnessConnectionState.CONNECTING),
        GymDeviceTransition(delayMs = 900, state = FitnessConnectionState.READY, resetPacketAge = true),
    )

    override fun disconnectSequence(
        preference: FitnessDevicePreference,
        activeSession: Boolean,
    ): List<GymDeviceTransition> = if (activeSession) {
        listOf(
            GymDeviceTransition(
                delayMs = 0,
                state = FitnessConnectionState.RECONNECTING,
                errorMessage = "${preference.displayName} disconnected. Trying to reconnect...",
                incrementReconnectCount = true,
            ),
            GymDeviceTransition(
                delayMs = 2200,
                state = FitnessConnectionState.ACTIVE,
                errorMessage = null,
                resetPacketAge = true,
            ),
        )
    } else {
        listOf(
            GymDeviceTransition(
                delayMs = 0,
                state = FitnessConnectionState.DISCONNECTED,
                clearTelemetry = true,
            ),
        )
    }

    override fun sampleTelemetry(
        device: FitnessDeviceSnapshot,
        elapsedSeconds: Int,
        activeSeconds: Int,
    ): FitnessTelemetry {
        val wave = sin((elapsedSeconds / 6.0) * PI)
        val reps = (activeSeconds / 6).coerceAtLeast(0)
        val set = (reps / 10) + 1
        val repInSet = (reps % 10) + 1
        val volume = reps * 65.0
        return FitnessTelemetry(
            timestampEpochMs = System.currentTimeMillis(),
            repetitions = repInSet,
            setNumber = set,
            loadLeftKg = 32.5 + (wave * 1.5),
            loadRightKg = 32.5 + (wave * 1.5),
            totalVolumeKg = volume,
            calories = activeSeconds * 0.25,
            rangeOfMotion = 0.73 + (wave * 0.08),
            timeUnderTensionMs = 2200L,
        )
    }
}

private object EchelonCommunityAdapter : GymDeviceAdapter {
    override val adapterId: String = GymBuiltInAdapterIds.ECHELON_COMMUNITY
    override val integrationLabel: String = "Cardio Community Layer"

    override fun buildSnapshot(
        preference: FitnessDevicePreference,
        existing: FitnessDeviceSnapshot?,
    ): FitnessDeviceSnapshot = mergeSnapshot(preference, existing, integrationLabel)

    override fun connectSequence(preference: FitnessDevicePreference): List<GymDeviceTransition> = listOf(
        GymDeviceTransition(delayMs = 0, state = FitnessConnectionState.SCANNING, errorMessage = null),
        GymDeviceTransition(delayMs = 900, state = FitnessConnectionState.CONNECTING),
        GymDeviceTransition(delayMs = 1100, state = FitnessConnectionState.READY, resetPacketAge = true),
    )

    override fun disconnectSequence(
        preference: FitnessDevicePreference,
        activeSession: Boolean,
    ): List<GymDeviceTransition> = if (activeSession) {
        listOf(
            GymDeviceTransition(
                delayMs = 0,
                state = FitnessConnectionState.RECONNECTING,
                errorMessage = "${preference.displayName} disconnected. Trying to reconnect...",
                incrementReconnectCount = true,
            ),
            GymDeviceTransition(
                delayMs = 2200,
                state = FitnessConnectionState.ACTIVE,
                errorMessage = null,
                resetPacketAge = true,
            ),
        )
    } else {
        listOf(
            GymDeviceTransition(
                delayMs = 0,
                state = FitnessConnectionState.DISCONNECTED,
                clearTelemetry = true,
            ),
        )
    }

    override fun sampleTelemetry(
        device: FitnessDeviceSnapshot,
        elapsedSeconds: Int,
        activeSeconds: Int,
    ): FitnessTelemetry {
        val wave = sin((elapsedSeconds / 6.0) * PI)
        return FitnessTelemetry(
            timestampEpochMs = System.currentTimeMillis(),
            powerWatts = 245 + (wave * 42),
            cadenceRpm = 92 + (wave * 8),
            speedKph = 33.2 + (wave * 2.8),
            resistance = 28 + (wave * 4),
            distanceKm = activeSeconds / 90.0,
            calories = activeSeconds * 0.42,
        )
    }
}

private object HeartRateRelayAdapter : GymDeviceAdapter {
    override val adapterId: String = GymBuiltInAdapterIds.HEART_RATE_RELAY
    override val integrationLabel: String = "Heart Rate Relay"

    override fun buildSnapshot(
        preference: FitnessDevicePreference,
        existing: FitnessDeviceSnapshot?,
    ): FitnessDeviceSnapshot = mergeSnapshot(preference, existing, integrationLabel)

    override fun connectSequence(preference: FitnessDevicePreference): List<GymDeviceTransition> = listOf(
        GymDeviceTransition(delayMs = 0, state = FitnessConnectionState.SCANNING, errorMessage = null),
        GymDeviceTransition(delayMs = 500, state = FitnessConnectionState.READY, resetPacketAge = true),
    )

    override fun disconnectSequence(
        preference: FitnessDevicePreference,
        activeSession: Boolean,
    ): List<GymDeviceTransition> = if (activeSession) {
        listOf(
            GymDeviceTransition(
                delayMs = 0,
                state = FitnessConnectionState.RECONNECTING,
                errorMessage = "${preference.displayName} disconnected. Trying to reconnect...",
                incrementReconnectCount = true,
            ),
            GymDeviceTransition(
                delayMs = 1600,
                state = FitnessConnectionState.ACTIVE,
                errorMessage = null,
                resetPacketAge = true,
            ),
        )
    } else {
        listOf(
            GymDeviceTransition(
                delayMs = 0,
                state = FitnessConnectionState.DISCONNECTED,
                clearTelemetry = true,
            ),
        )
    }

    override fun sampleTelemetry(
        device: FitnessDeviceSnapshot,
        elapsedSeconds: Int,
        activeSeconds: Int,
    ): FitnessTelemetry {
        val wave = sin((elapsedSeconds / 6.0) * PI)
        return FitnessTelemetry(
            timestampEpochMs = System.currentTimeMillis(),
            heartRate = (142 + (wave * 14)).roundToInt(),
            calories = activeSeconds * 0.18,
        )
    }
}

private class SamsungHealthConnectAdapter(
    private val healthConnect: GymHealthConnectGateway,
) : GymDeviceAdapter {
    override val adapterId: String = GymBuiltInAdapterIds.SAMSUNG_HEALTH_CONNECT
    override val integrationLabel: String = "Samsung Health via Health Connect"

    override fun isAvailable(mockDevicesEnabled: Boolean): Boolean = healthConnect.isSamsungHealthInstalled()

    override fun buildSnapshot(
        preference: FitnessDevicePreference,
        existing: FitnessDeviceSnapshot?,
    ): FitnessDeviceSnapshot = mergeSnapshot(preference, existing, integrationLabel)

    override fun connectSequence(preference: FitnessDevicePreference): List<GymDeviceTransition> = listOf(
        GymDeviceTransition(delayMs = 0, state = FitnessConnectionState.CONNECTING, errorMessage = null),
        GymDeviceTransition(delayMs = 350, state = FitnessConnectionState.READY, errorMessage = healthConnect.statusMessage(), resetPacketAge = true),
    )

    override fun disconnectSequence(
        preference: FitnessDevicePreference,
        activeSession: Boolean,
    ): List<GymDeviceTransition> = listOf(
        GymDeviceTransition(delayMs = 0, state = FitnessConnectionState.DISCONNECTED, clearTelemetry = true),
    )

    override fun sampleTelemetry(
        device: FitnessDeviceSnapshot,
        elapsedSeconds: Int,
        activeSeconds: Int,
    ): FitnessTelemetry? = healthConnect.currentTelemetry()

    override fun onConnected(preference: FitnessDevicePreference) {
        healthConnect.refreshLatestHeartRate()
    }

    override fun refreshTelemetry() {
        healthConnect.refreshLatestHeartRate()
    }

    override fun statusMessage(): String? = healthConnect.statusMessage()
}
