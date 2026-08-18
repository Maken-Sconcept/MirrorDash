package com.sconcept.mirrordash.gym

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GymDomainLogicTest {

    @Test
    fun `tickClock increments active time when running`() {
        val next = GymDomainLogic.tickClock(
            clock = GymSessionClock(elapsedSeconds = 12, activeSeconds = 10, pausedSeconds = 2),
            paused = false,
        )

        assertEquals(13, next.elapsedSeconds)
        assertEquals(11, next.activeSeconds)
        assertEquals(2, next.pausedSeconds)
    }

    @Test
    fun `tickClock increments paused time when paused`() {
        val next = GymDomainLogic.tickClock(
            clock = GymSessionClock(elapsedSeconds = 12, activeSeconds = 10, pausedSeconds = 2),
            paused = true,
        )

        assertEquals(13, next.elapsedSeconds)
        assertEquals(10, next.activeSeconds)
        assertEquals(3, next.pausedSeconds)
    }

    @Test
    fun `heartRateZone buckets heart rate correctly`() {
        assertNull(GymDomainLogic.heartRateZone(null))
        assertEquals("ZONE 1", GymDomainLogic.heartRateZone(95))
        assertEquals("ZONE 2", GymDomainLogic.heartRateZone(120))
        assertEquals("ZONE 3", GymDomainLogic.heartRateZone(145))
        assertEquals("ZONE 4", GymDomainLogic.heartRateZone(160))
        assertEquals("ZONE 5", GymDomainLogic.heartRateZone(175))
    }

    @Test
    fun `scoreGain rewards cadence reps and combo without exploding`() {
        val gain = GymDomainLogic.scoreGain(
            heartRate = 148,
            cadence = 92,
            repDelta = 2,
            combo = 6,
        )

        assertEquals(81, gain)
    }
}
