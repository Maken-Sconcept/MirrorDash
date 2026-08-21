package com.sconcept.mirrordash.gym

import android.content.Context
import android.content.pm.PackageManager
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val SAMSUNG_HEALTH_PACKAGE = "com.sec.android.app.shealth"

/** On-device Galaxy Health reader. Values are fitness/wellness information, never medical advice. */
class GymHealthConnectGateway(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val healthConnect by lazy { HealthConnectClient.getOrCreate(context) }
    private val _snapshot = MutableStateFlow(GymWearableHealthSnapshot())
    val snapshot = _snapshot.asStateFlow()

    @Volatile private var lastRefreshEpochMs: Long = 0

    fun isSamsungHealthInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(SAMSUNG_HEALTH_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) { false }

    fun currentTelemetry(): FitnessTelemetry? = snapshot.value.latestHeartRate?.let { heartRate ->
        FitnessTelemetry(snapshot.value.updatedAtEpochMs ?: System.currentTimeMillis(), heartRate = heartRate)
    }

    fun statusMessage(): String? = snapshot.value.status.takeUnless { it == "Connected" }

    fun refreshLatestHeartRate() = refreshHealthSummary()

    fun refreshHealthSummary(force: Boolean = false) {
        val nowMs = System.currentTimeMillis()
        if (!force && nowMs - lastRefreshEpochMs < 5_000) return
        lastRefreshEpochMs = nowMs
        when {
            !isSamsungHealthInstalled() -> updateStatus("Install Samsung Health on the paired phone first")
            HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE -> updateStatus("Health Connect is unavailable on this device")
            else -> scope.launch { loadSummary() }
        }
    }

    private suspend fun loadSummary() {
        val granted = healthConnect.permissionController.getGrantedPermissions()
        val heartRatePermission = HealthPermission.getReadPermission(HeartRateRecord::class)
        val stepsPermission = HealthPermission.getReadPermission(StepsRecord::class)
        val caloriesPermission = HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)
        val distancePermission = HealthPermission.getReadPermission(DistanceRecord::class)
        val exercisePermission = HealthPermission.getReadPermission(ExerciseSessionRecord::class)
        val sleepPermission = HealthPermission.getReadPermission(SleepSessionRecord::class)
        val oxygenPermission = HealthPermission.getReadPermission(OxygenSaturationRecord::class)
        val restingHeartRatePermission = HealthPermission.getReadPermission(RestingHeartRateRecord::class)
        val weightPermission = HealthPermission.getReadPermission(WeightRecord::class)
        val bodyFatPermission = HealthPermission.getReadPermission(BodyFatRecord::class)
        if (heartRatePermission !in granted) {
            updateStatus("Allow wearable data access in Health Connect")
            return
        }
        runCatching {
            val now = Instant.now()
            val zone = ZoneId.systemDefault()
            val dayStart = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
            val weekStart = now.atZone(zone).toLocalDate().minusDays(6).atStartOfDay(zone).toInstant()
            val overnightStart = now.minusSeconds(48 * 60 * 60)
            val origin = setOf(DataOrigin(SAMSUNG_HEALTH_PACKAGE))
            fun range(start: Instant) = TimeRangeFilter.between(start, now)

            val heartRates = healthConnect.readRecords(ReadRecordsRequest(HeartRateRecord::class, range(now.minusSeconds(15 * 60)), dataOriginFilter = origin)).records
            val latestHeartRate = heartRates.flatMap { it.samples }.maxByOrNull { it.time }?.beatsPerMinute?.toInt()
            val steps = if (stepsPermission in granted) healthConnect.readRecords(ReadRecordsRequest(StepsRecord::class, range(dayStart), dataOriginFilter = origin)).records.sumOf { it.count } else null
            val calories = if (caloriesPermission in granted) healthConnect.readRecords(ReadRecordsRequest(TotalCaloriesBurnedRecord::class, range(dayStart), dataOriginFilter = origin)).records.sumOf { it.energy.inKilocalories }.toInt() else null
            val distance = if (distancePermission in granted) healthConnect.readRecords(ReadRecordsRequest(DistanceRecord::class, range(dayStart), dataOriginFilter = origin)).records.sumOf { it.distance.inKilometers } else null
            val workouts = if (exercisePermission in granted) healthConnect.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, range(weekStart), dataOriginFilter = origin)).records else emptyList()
            val sleep = if (sleepPermission in granted) healthConnect.readRecords(ReadRecordsRequest(SleepSessionRecord::class, range(overnightStart), dataOriginFilter = origin)).records.maxByOrNull { it.endTime } else null
            val oxygen = if (oxygenPermission in granted) healthConnect.readRecords(ReadRecordsRequest(OxygenSaturationRecord::class, range(now.minusSeconds(7 * 24 * 60 * 60)), dataOriginFilter = origin)).records.maxByOrNull { it.time }?.percentage?.value else null
            val restingHr = if (restingHeartRatePermission in granted) healthConnect.readRecords(ReadRecordsRequest(RestingHeartRateRecord::class, range(now.minusSeconds(7 * 24 * 60 * 60)), dataOriginFilter = origin)).records.maxByOrNull { it.time }?.beatsPerMinute?.toInt() else null
            val weight = if (weightPermission in granted) healthConnect.readRecords(ReadRecordsRequest(WeightRecord::class, range(now.minusSeconds(90 * 24 * 60 * 60)), dataOriginFilter = origin)).records.maxByOrNull { it.time }?.weight?.inKilograms else null
            val bodyFat = if (bodyFatPermission in granted) healthConnect.readRecords(ReadRecordsRequest(BodyFatRecord::class, range(now.minusSeconds(90 * 24 * 60 * 60)), dataOriginFilter = origin)).records.maxByOrNull { it.time }?.percentage?.value else null
            val stages = sleep?.stages.orEmpty()
            fun stageMinutes(type: Int) = stages.filter { it.stage == type }.sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes() }.toInt()
            GymWearableHealthSnapshot(
                status = "Connected",
                updatedAtEpochMs = System.currentTimeMillis(),
                latestHeartRate = latestHeartRate,
                restingHeartRate = restingHr,
                stepsToday = steps,
                activeCaloriesToday = calories,
                distanceTodayKm = distance,
                workoutsThisWeek = workouts.size.takeIf { exercisePermission in granted },
                lastWorkoutLabel = workouts.maxByOrNull { it.endTime }?.title,
                sleepMinutes = sleep?.let { java.time.Duration.between(it.startTime, it.endTime).toMinutes().toInt() },
                sleepDeepMinutes = stageMinutes(SleepSessionRecord.STAGE_TYPE_DEEP).takeIf { sleep != null },
                sleepRemMinutes = stageMinutes(SleepSessionRecord.STAGE_TYPE_REM).takeIf { sleep != null },
                sleepLightMinutes = stageMinutes(SleepSessionRecord.STAGE_TYPE_LIGHT).takeIf { sleep != null },
                sleepStartEpochMs = sleep?.startTime?.toEpochMilli(),
                bloodOxygenPercent = oxygen?.times(100),
                bodyWeightKg = weight,
                bodyFatPercent = bodyFat?.times(100),
            )
        }.onSuccess { _snapshot.value = it }.onFailure { updateStatus("Could not read Samsung Health data") }
    }

    private fun updateStatus(message: String) { _snapshot.value = _snapshot.value.copy(status = message) }
}
