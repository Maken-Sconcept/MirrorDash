package com.sconcept.mirrordash.gym

import com.sconcept.mirrordash.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class GymRepository(private val settingsRepository: SettingsRepository) {

    val storedState: Flow<GymStoredState> = settingsRepository.settings.map { settings ->
        GymStoredState(
            featureSettings = settings.gymFeatureSettings,
            profiles = settings.gymProfiles.ifEmpty { defaultGymProfiles() },
            devicePreferences = settings.gymDevicePreferences
                .ifEmpty { defaultGymDevicePreferences() }
                .let { preferences ->
                    if (preferences.any { it.adapterId == GymBuiltInAdapterIds.SAMSUNG_HEALTH_CONNECT }) preferences
                    else preferences + defaultGymDevicePreferences().first { it.adapterId == GymBuiltInAdapterIds.SAMSUNG_HEALTH_CONNECT }
                },
            sessionHistory = settings.gymSessionHistory,
            displayOrientationMode = settings.displayOrientationMode,
        )
    }

    suspend fun setGymEnabled(enabled: Boolean) {
        settingsRepository.update { gymEnabled = enabled }
    }

    suspend fun updateFeatureSettings(transform: (GymFeatureSettings) -> GymFeatureSettings) {
        settingsRepository.update { gymFeatureSettings = transform(gymFeatureSettings) }
    }

    suspend fun updateProfiles(transform: (List<GymProfile>) -> List<GymProfile>) {
        settingsRepository.update { gymProfiles = transform(gymProfiles) }
    }

    suspend fun updateDevicePreferences(transform: (List<FitnessDevicePreference>) -> List<FitnessDevicePreference>) {
        settingsRepository.update { gymDevicePreferences = transform(gymDevicePreferences) }
    }

    suspend fun appendSession(record: GymSessionRecord) {
        if (settingsRepository.settings.first().gymSessionHistory.any { it.id == record.id }) return
        settingsRepository.update { gymSessionHistory = (gymSessionHistory + record).takeLast(40) }
        awardSessionProgression(record)
    }

    private suspend fun awardSessionProgression(record: GymSessionRecord) {
        settingsRepository.update {
            val playersById = gymProfiles.associateBy { it.id }.toMutableMap()
            record.players.forEach { player ->
                val existing = playersById[player.profileId] ?: return@forEach
                if (record.id in existing.progression.processedSessionIds) return@forEach
                val playerHistory = gymSessionHistory.filter { session -> session.players.any { it.profileId == player.profileId } }
                val qualifying = playerHistory.filter(GymProgression::isQualifying)
                val week = GymProgression.weekKey(record.endedAtEpochMs)
                val weekCount = qualifying.count { GymProgression.weekKey(it.endedAtEpochMs) == week }
                val firstToday = qualifying.count { it.endedAtEpochMs / 86_400_000L == record.endedAtEpochMs / 86_400_000L } == 1
                val reachedGoal = weekCount == existing.progression.weeklyWorkoutTarget
                val baseXp = GymProgression.workoutXp(record, firstToday, reachedGoal) + player.xpEarned
                val multiplier = activeAchievementCompletionMultiplier(existing, record, gymSessionHistory)
                val xp = baseXp * multiplier
                val totalWorkouts = existing.totalWorkouts + if (GymProgression.isQualifying(record)) 1 else 0
                val minutes = existing.progression.lifetimeMinutes + record.activeSeconds / 60
                val progress = existing.progression.copy(
                    lifetimeMinutes = minutes,
                    currentWeeklyStreak = if (reachedGoal) maxOf(1, existing.progression.currentWeeklyStreak) else existing.progression.currentWeeklyStreak,
                    longestWeeklyStreak = maxOf(existing.progression.longestWeeklyStreak, if (reachedGoal) maxOf(1, existing.progression.currentWeeklyStreak) else 0),
                    processedSessionIds = (existing.progression.processedSessionIds + record.id).takeLast(200),
                    recentUnlocks = GymProgression.achievementProgress(totalWorkouts, minutes).filter { it.currentTier > 0 }.map { it.name }.take(5),
                )
                playersById[player.profileId] = existing.copy(
                    totalXp = existing.totalXp + xp,
                    totalWorkouts = totalWorkouts,
                    streakDays = progress.currentWeeklyStreak,
                    progression = progress,
                )
            }
            gymProfiles = gymProfiles.map { profile -> playersById[profile.id] ?: profile }
        }
    }
}
