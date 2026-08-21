package com.sconcept.mirrordash.gym

import java.util.Calendar

/** Local, deterministic assignments. No health, injury, pain, or medical information is used. */
enum class GymAchievementCadence { DAILY, WEEKLY }

/** A passive achievement; every session in its window contributes to progress. */
data class GymActiveAchievement(
    val id: String,
    val title: String,
    val subtitle: String,
    val cadence: GymAchievementCadence,
    val progressSeconds: Int,
    val targetSeconds: Int,
    val rewardMultiplier: Int,
) {
    val isComplete: Boolean get() = progressSeconds >= targetSeconds
}

fun buildActiveAchievements(
    profile: GymProfile,
    history: List<GymSessionRecord>,
    nowEpochMs: Long = System.currentTimeMillis(),
): List<GymActiveAchievement> {
    val calendar = Calendar.getInstance().apply { timeInMillis = nowEpochMs }
    val dayKey = "%04d%02d%02d".format(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH))
    val weeklyTarget = when (profile.workoutLevel) {
        GymTrainingLevel.NOVICE -> 60 * 60
        GymTrainingLevel.BEGINNER -> 90 * 60
        GymTrainingLevel.INTERMEDIATE -> 120 * 60
        GymTrainingLevel.ADVANCED -> 150 * 60
    }
    val dailyTarget = if (profile.workoutLevel.ordinal <= GymTrainingLevel.BEGINNER.ordinal) 15 * 60 else 20 * 60
    fun progress(cadence: GymAchievementCadence) = history
        .filter { it.players.any { player -> player.profileId == profile.id } && it.endedAtEpochMs.isInAchievementWindow(cadence, nowEpochMs) }
        .sumOf { it.activeSeconds }
    return listOf(
        GymActiveAchievement(
            id = "active_weekly_${calendar.weekYear}_${calendar.get(Calendar.WEEK_OF_YEAR)}",
            title = "Week in Motion",
            subtitle = "Build ${weeklyTarget / 60} active minutes across the week",
            cadence = GymAchievementCadence.WEEKLY,
            progressSeconds = progress(GymAchievementCadence.WEEKLY),
            targetSeconds = weeklyTarget,
            rewardMultiplier = 5,
        ),
        GymActiveAchievement(
            id = "active_daily_$dayKey",
            title = "Today's Spark",
            subtitle = "${dailyTarget / 60} active minutes today",
            cadence = GymAchievementCadence.DAILY,
            progressSeconds = progress(GymAchievementCadence.DAILY),
            targetSeconds = dailyTarget,
            rewardMultiplier = 2,
        ),
    )
}

private fun Long.isInAchievementWindow(cadence: GymAchievementCadence, nowEpochMs: Long): Boolean {
    val session = Calendar.getInstance().apply { timeInMillis = this@isInAchievementWindow }
    val now = Calendar.getInstance().apply { timeInMillis = nowEpochMs }
    return when (cadence) {
        GymAchievementCadence.DAILY -> session.get(Calendar.YEAR) == now.get(Calendar.YEAR) && session.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
        GymAchievementCadence.WEEKLY -> session.weekYear == now.weekYear && session.get(Calendar.WEEK_OF_YEAR) == now.get(Calendar.WEEK_OF_YEAR)
    }
}

/** Awards only the largest time-bound achievement completed by this saved session. */
fun activeAchievementCompletionMultiplier(profile: GymProfile, record: GymSessionRecord, history: List<GymSessionRecord>): Int =
    buildActiveAchievements(profile, history, record.endedAtEpochMs)
        .filter { achievement ->
            achievement.isComplete && history
                .filterNot { it.id == record.id }
                .filter { it.players.any { player -> player.profileId == profile.id } && it.endedAtEpochMs.isInAchievementWindow(achievement.cadence, record.endedAtEpochMs) }
                .sumOf { it.activeSeconds } < achievement.targetSeconds
        }
        .maxOfOrNull { it.rewardMultiplier }
        ?: 1
