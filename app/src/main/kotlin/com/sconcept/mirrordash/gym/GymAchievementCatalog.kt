package com.sconcept.mirrordash.gym

import java.util.Calendar

/**
 * Data-driven achievement rules. Adding or retuning an achievement changes a definition below,
 * never the evaluation engine.
 */
enum class AchievementScope { SELF, TOGETHER }

enum class AchievementMetric {
    WORKOUT_COUNT,
    ACTIVE_MINUTES,
    WEEKLY_WORKOUTS,
    DISTANCE_KM,
    STRENGTH_VOLUME_KG,
    REPETITIONS,
    LONGEST_WORKOUT_MINUTES,
    COOP_WORKOUTS,
    COOP_ACTIVE_MINUTES,
    SHORT_WORKOUTS,
    SLOW_RIDES,
    FREE_RIDE_SESSIONS,
    FREE_RIDE_MINUTES,
}

data class GymAchievementDefinition(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    /** Detail-card-only explanation; intentionally omitted from achievement list cards. */
    val unlockCriteria: String,
    val metric: AchievementMetric,
    val tiers: List<Double>,
    val rewards: List<Int>,
    val scope: AchievementScope = AchievementScope.SELF,
    val hidden: Boolean = false,
    val rarity: String = "Common",
)

data class GymAchievementStatus(
    val definition: GymAchievementDefinition,
    val current: Double,
) {
    val currentTier: Int get() = definition.tiers.count { current >= it }
    val nextTarget: Double? get() = definition.tiers.firstOrNull { current < it }
    val percentToNext: Float get() = nextTarget?.let { (current / it).toFloat().coerceIn(0f, 1f) } ?: 1f
    val nextReward: Int? get() = definition.rewards.getOrNull(currentTier)
}

object GymAchievementCatalog {
    val definitions = listOf(
        GymAchievementDefinition("first_step", "FIRST STEP", "Consistency", "Complete qualifying workouts.", "Finish workouts with at least 5 active minutes.", AchievementMetric.WORKOUT_COUNT, listOf(1.0, 5.0, 10.0, 25.0, 50.0), listOf(50, 100, 200, 350, 500)),
        GymAchievementDefinition("clocked_in", "CLOCKED IN", "Endurance", "Accumulate active training time.", "Your active minutes add up across every recorded workout.", AchievementMetric.ACTIVE_MINUTES, listOf(300.0, 600.0, 1500.0, 3000.0, 6000.0), listOf(50, 100, 200, 350, 500)),
        GymAchievementDefinition("built_to_last", "BUILT TO LAST", "Endurance", "Stay active in one workout.", "Complete one continuous workout for the tier's full duration.", AchievementMetric.LONGEST_WORKOUT_MINUTES, listOf(15.0, 30.0, 45.0, 60.0, 90.0, 120.0), listOf(50, 100, 200, 350, 500, 750)),
        GymAchievementDefinition("road_warrior", "ROAD WARRIOR", "Cycling", "Accumulate distance across rides.", "Ride a bike; every recorded kilometre contributes to your total.", AchievementMetric.DISTANCE_KM, listOf(10.0, 25.0, 50.0, 100.0, 250.0, 500.0, 1000.0), listOf(50, 100, 200, 350, 500, 750, 1000)),
        GymAchievementDefinition("ton_of_iron", "TON OF IRON", "Strength", "Move cumulative strength volume.", "Record strength work; weight multiplied by reps counts toward volume.", AchievementMetric.STRENGTH_VOLUME_KG, listOf(1_000.0, 5_000.0, 10_000.0, 50_000.0, 100_000.0), listOf(50, 100, 200, 350, 500)),
        GymAchievementDefinition("rep_machine", "REP MACHINE", "Strength", "Complete repetitions in recorded workouts.", "Every completed rep in a recorded workout counts.", AchievementMetric.REPETITIONS, listOf(100.0, 500.0, 1_000.0, 5_000.0), listOf(50, 100, 200, 350)),
        GymAchievementDefinition("weekly_warrior", "WEEKLY WARRIOR", "Consistency", "Reach your weekly workout target.", "Finish the number of qualifying workouts set as your weekly target.", AchievementMetric.WEEKLY_WORKOUTS, listOf(4.0), listOf(100)),
        GymAchievementDefinition("well_that_was_quick", "WELL, THAT WAS QUICK", "Story", "Finish an exceptionally short workout.", "End a workout after starting it but before reaching 5 active minutes.", AchievementMetric.SHORT_WORKOUTS, listOf(1.0, 5.0, 10.0), listOf(25, 50, 100), rarity = "Uncommon"),
        GymAchievementDefinition("sunday_driver", "SUNDAY DRIVER", "Story", "Finish a deliberately slow ride.", "Complete a 5-minute bike ride with an average speed below 8 km/h.", AchievementMetric.SLOW_RIDES, listOf(1.0, 5.0, 10.0), listOf(25, 50, 100), rarity = "Uncommon"),
        GymAchievementDefinition("open_road", "OPEN ROAD", "Cycling", "Ride your way.", "Complete unstructured Free Ride sessions on the bike.", AchievementMetric.FREE_RIDE_SESSIONS, listOf(1.0, 5.0, 25.0, 100.0), listOf(50, 125, 300, 750)),
        GymAchievementDefinition("after_credits", "AFTER CREDITS", "Cycling", "Keep pedalling after the video ends.", "Accumulate active minutes in unstructured Free Ride sessions.", AchievementMetric.FREE_RIDE_MINUTES, listOf(30.0, 300.0, 1_500.0), listOf(75, 250, 600), rarity = "Uncommon"),
        GymAchievementDefinition("dynamic_duo", "DYNAMIC DUO", "Together", "Complete workouts side by side.", "Both you and your partner must be recorded in the same qualifying workout.", AchievementMetric.COOP_WORKOUTS, listOf(1.0, 10.0, 25.0, 100.0), listOf(50, 150, 350, 750), scope = AchievementScope.TOGETHER),
        GymAchievementDefinition("sweat_together", "SWEAT TOGETHER", "Together", "Accumulate active time together.", "Active minutes count only when both you and your partner are in the same workout.", AchievementMetric.COOP_ACTIVE_MINUTES, listOf(60.0, 600.0, 3_000.0), listOf(50, 200, 500), scope = AchievementScope.TOGETHER),
    )
}

fun evaluateAchievements(
    profile: GymProfile,
    history: List<GymSessionRecord>,
    partner: GymProfile? = null,
): List<GymAchievementStatus> {
    val profileSessions = history.filter { record -> record.players.any { it.profileId == profile.id } }
    val sharedSessions = partner?.let { other ->
        history.filter { record -> record.players.any { it.profileId == profile.id } && record.players.any { it.profileId == other.id } }
    }.orEmpty()
    fun playerMetric(record: GymSessionRecord) = record.players.firstOrNull { it.profileId == profile.id }?.metrics ?: GymMetricSummary()
    val currentWeek = profileSessions.count { GymProgression.weekKey(it.endedAtEpochMs) == GymProgression.weekKey(System.currentTimeMillis()) }
    fun value(metric: AchievementMetric): Double = when (metric) {
        AchievementMetric.WORKOUT_COUNT -> profile.totalWorkouts.toDouble()
        AchievementMetric.ACTIVE_MINUTES -> profile.progression.lifetimeMinutes.toDouble()
        AchievementMetric.WEEKLY_WORKOUTS -> currentWeek.toDouble()
        AchievementMetric.DISTANCE_KM -> profileSessions.sumOf { playerMetric(it).distanceKm }
        AchievementMetric.STRENGTH_VOLUME_KG -> profileSessions.sumOf { playerMetric(it).strengthVolumeKg }
        AchievementMetric.REPETITIONS -> profileSessions.sumOf { playerMetric(it).repetitions }.toDouble()
        AchievementMetric.LONGEST_WORKOUT_MINUTES -> (profileSessions.maxOfOrNull { it.activeSeconds } ?: 0).toDouble() / 60
        AchievementMetric.COOP_WORKOUTS -> sharedSessions.count(GymProgression::isQualifying).toDouble()
        AchievementMetric.COOP_ACTIVE_MINUTES -> sharedSessions.sumOf { it.activeSeconds }.toDouble() / 60
        AchievementMetric.SHORT_WORKOUTS -> profileSessions.count { it.activeSeconds in 1 until GymProgression.MIN_QUALIFYING_SECONDS }.toDouble()
        AchievementMetric.SLOW_RIDES -> profileSessions.count { record ->
            record.workoutType == GymWorkoutType.CYCLING && record.activeSeconds >= GymProgression.MIN_QUALIFYING_SECONDS &&
                playerMetric(record).distanceKm > 0 && playerMetric(record).distanceKm / (record.activeSeconds / 3600.0) < 8.0
        }.toDouble()
        AchievementMetric.FREE_RIDE_SESSIONS -> profileSessions.count { it.workoutType == GymWorkoutType.CYCLING && it.challengeId == null }.toDouble()
        AchievementMetric.FREE_RIDE_MINUTES -> profileSessions.filter { it.workoutType == GymWorkoutType.CYCLING && it.challengeId == null }.sumOf { it.activeSeconds }.toDouble() / 60
    }
    return GymAchievementCatalog.definitions
        .filter { it.scope == AchievementScope.SELF || partner != null }
        .map { definition ->
            val effectiveDefinition = if (definition.metric == AchievementMetric.WEEKLY_WORKOUTS) {
                definition.copy(tiers = listOf(profile.progression.weeklyWorkoutTarget.coerceAtLeast(1).toDouble()))
            } else definition
            GymAchievementStatus(effectiveDefinition, value(effectiveDefinition.metric))
        }
}

fun achievementValueLabel(status: GymAchievementStatus): String = when (status.definition.metric) {
    AchievementMetric.ACTIVE_MINUTES, AchievementMetric.LONGEST_WORKOUT_MINUTES, AchievementMetric.COOP_ACTIVE_MINUTES, AchievementMetric.FREE_RIDE_MINUTES -> "${status.current.toInt()} min"
    AchievementMetric.DISTANCE_KM -> "${"%.1f".format(status.current)} km"
    AchievementMetric.STRENGTH_VOLUME_KG -> "${status.current.toInt()} kg"
    else -> status.current.toInt().toString()
}
