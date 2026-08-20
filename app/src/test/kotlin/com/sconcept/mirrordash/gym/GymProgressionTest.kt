package com.sconcept.mirrordash.gym

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GymProgressionTest {
    @Test fun `level curve is progressive and reversible for current level`() {
        val xp = GymProgression.xpRequiredForLevel(1) + GymProgression.xpRequiredForLevel(2)
        assertEquals(3, GymProgression.levelFromXp(xp))
        assertEquals(0, GymProgression.xpIntoLevel(xp))
    }

    @Test fun `short unqualified workout earns no progression xp`() {
        val record = record(activeSeconds = 90)
        assertEquals(0, GymProgression.workoutXp(record, firstToday = true, weeklyTargetReached = true))
    }

    @Test fun `qualified workout includes configurable source rewards`() {
        assertTrue(GymProgression.workoutXp(record(activeSeconds = 600), true, true) >= 185)
    }

    @Test fun `catalog evaluates co-op achievements from shared session history`() {
        val me = GymProfile(id = "me", name = "Me", avatarLabel = "M", accentColorArgb = 0, totalWorkouts = 1)
        val partner = GymProfile(id = "partner", name = "Partner", avatarLabel = "P", accentColorArgb = 0)
        val shared = GymSessionRecord(
            id = "shared", startedAtEpochMs = 0, endedAtEpochMs = 600_000, workoutType = GymWorkoutType.MULTIPLAYER,
            durationSeconds = 600, activeSeconds = 600, pausedSeconds = 0,
            players = listOf(
                GymSessionPlayerRecord("me", "Me", 0, 0),
                GymSessionPlayerRecord("partner", "Partner", 0, 0),
            ),
        )

        val dynamicDuo = evaluateAchievements(me, listOf(shared), partner).first { it.definition.id == "dynamic_duo" }

        assertEquals(1.0, dynamicDuo.current, 0.0)
        assertEquals(1, dynamicDuo.currentTier)
    }

    private fun record(activeSeconds: Int) = GymSessionRecord(
        id = "test", startedAtEpochMs = 0, endedAtEpochMs = 0, workoutType = GymWorkoutType.STRENGTH,
        durationSeconds = activeSeconds, activeSeconds = activeSeconds, pausedSeconds = 0,
        players = listOf(GymSessionPlayerRecord("p", "Player", 0, 0)),
    )
}
