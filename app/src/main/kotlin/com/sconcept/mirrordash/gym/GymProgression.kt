package com.sconcept.mirrordash.gym

import java.util.Calendar

/** Central, deterministic progression rules. Device adapters only supply session records. */
object GymProgression {
    const val MIN_QUALIFYING_SECONDS = 5 * 60
    const val WORKOUT_COMPLETE_XP = 50
    const val XP_PER_ACTIVE_MINUTE = 2
    const val XP_PER_EXERCISE = 5
    const val FIRST_DAILY_WORKOUT_XP = 15
    const val WEEKLY_TARGET_XP = 100
    const val PERSONAL_RECORD_XP = 75

    fun xpRequiredForLevel(level: Int): Int = 100 + (level.coerceAtLeast(1) - 1) * 45

    fun levelFromXp(totalXp: Int): Int {
        var remaining = totalXp.coerceAtLeast(0)
        var level = 1
        while (remaining >= xpRequiredForLevel(level)) { remaining -= xpRequiredForLevel(level); level++ }
        return level
    }

    fun xpIntoLevel(totalXp: Int): Int {
        var remaining = totalXp.coerceAtLeast(0)
        var level = 1
        while (remaining >= xpRequiredForLevel(level)) { remaining -= xpRequiredForLevel(level); level++ }
        return remaining
    }

    fun isQualifying(record: GymSessionRecord) = record.activeSeconds >= MIN_QUALIFYING_SECONDS || record.challengeId != null

    fun workoutXp(record: GymSessionRecord, firstToday: Boolean, weeklyTargetReached: Boolean): Int {
        if (!isQualifying(record)) return 0
        val minutes = record.activeSeconds / 60
        val exercises = (record.players.maxOfOrNull { it.metrics.repetitions } ?: 0).coerceAtMost(20)
        return WORKOUT_COMPLETE_XP + minutes * XP_PER_ACTIVE_MINUTE + exercises * XP_PER_EXERCISE +
            (if (firstToday) FIRST_DAILY_WORKOUT_XP else 0) +
            (if (weeklyTargetReached) WEEKLY_TARGET_XP else 0)
    }

    fun weekKey(epochMs: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = epochMs }
        return "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.WEEK_OF_YEAR)}"
    }

    fun achievementProgress(workouts: Int, minutes: Int): List<GymAchievementProgress> = listOf(
        GymAchievementProgress("first_step", "FIRST STEP", "Complete qualifying workouts.", workouts, listOf(1, 5, 10, 25, 50)),
        GymAchievementProgress("clocked_in", "CLOCKED IN", "Accumulate active training time.", minutes, listOf(300, 600, 1500, 3000, 6000)),
    )
}

@kotlinx.serialization.Serializable
data class GymAchievementProgress(
    val id: String,
    val name: String,
    val description: String,
    val currentValue: Int,
    val tiers: List<Int>,
) {
    val currentTier: Int get() = tiers.count { currentValue >= it }
    val nextTarget: Int? get() = tiers.firstOrNull { currentValue < it }
    val percentToNext: Float get() = nextTarget?.let { currentValue.toFloat() / it }?.coerceIn(0f, 1f) ?: 1f
}

@kotlinx.serialization.Serializable
data class GymProgressionProfile(
    val weeklyWorkoutTarget: Int = 4,
    val lifetimeMinutes: Int = 0,
    val currentWeeklyStreak: Int = 0,
    val longestWeeklyStreak: Int = 0,
    val processedSessionIds: List<String> = emptyList(),
    val recentUnlocks: List<String> = emptyList(),
)
