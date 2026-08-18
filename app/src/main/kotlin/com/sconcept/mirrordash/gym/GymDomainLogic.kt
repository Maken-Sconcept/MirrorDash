package com.sconcept.mirrordash.gym

data class GymSessionClock(
    val elapsedSeconds: Int = 0,
    val activeSeconds: Int = 0,
    val pausedSeconds: Int = 0,
)

object GymDomainLogic {
    fun tickClock(
        clock: GymSessionClock,
        paused: Boolean,
    ): GymSessionClock {
        return if (paused) {
            clock.copy(
                elapsedSeconds = clock.elapsedSeconds + 1,
                pausedSeconds = clock.pausedSeconds + 1,
            )
        } else {
            clock.copy(
                elapsedSeconds = clock.elapsedSeconds + 1,
                activeSeconds = clock.activeSeconds + 1,
            )
        }
    }

    fun heartRateZone(heartRate: Int?): String? = when {
        heartRate == null -> null
        heartRate >= 170 -> "ZONE 5"
        heartRate >= 151 -> "ZONE 4"
        heartRate >= 131 -> "ZONE 3"
        heartRate >= 111 -> "ZONE 2"
        else -> "ZONE 1"
    }

    fun scoreGain(
        heartRate: Int?,
        cadence: Int?,
        repDelta: Int,
        combo: Int,
    ): Int {
        var score = 6
        if ((heartRate ?: 0) in 130..150) score += 10
        if ((heartRate ?: 0) in 151..165) score += 14
        if ((cadence ?: 0) in 84..100) score += 12
        if (repDelta > 0) score += repDelta * 18
        score += combo.coerceAtMost(5) * 3
        return score
    }
}
