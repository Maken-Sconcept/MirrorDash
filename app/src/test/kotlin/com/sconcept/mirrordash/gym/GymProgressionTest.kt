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

    private fun record(activeSeconds: Int) = GymSessionRecord(
        id = "test", startedAtEpochMs = 0, endedAtEpochMs = 0, workoutType = GymWorkoutType.STRENGTH,
        durationSeconds = activeSeconds, activeSeconds = activeSeconds, pausedSeconds = 0,
        players = listOf(GymSessionPlayerRecord("p", "Player", 0, 0)),
    )
}
