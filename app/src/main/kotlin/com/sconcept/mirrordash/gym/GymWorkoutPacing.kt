package com.sconcept.mirrordash.gym

/** A transparent, non-medical pacing prescription used by every live workout. */
data class GymPacingBlock(
    val label: String,
    val workSeconds: Int,
    val restSeconds: Int,
)

data class GymWorkoutPacing(val blocks: List<GymPacingBlock>) {
    val totalSeconds: Int get() = blocks.sumOf { it.workSeconds + it.restSeconds }
    val plannedRestSeconds: Int get() = blocks.sumOf { it.restSeconds }
}

fun GymActiveSessionState.workoutPacing(): GymWorkoutPacing {
    generatedWorkout?.exercises?.takeIf { it.isNotEmpty() }?.let { exercises ->
        return GymWorkoutPacing(exercises.map { exercise ->
            GymPacingBlock(exercise.title, exercise.workSeconds, exercise.restSeconds)
        })
    }
    val challenge = challenge
    if (challenge != null) {
        val blocks = when {
            challenge.difficultyLabel.contains("HIIT", true) || challenge.title.contains("Interval", true) ->
                List((challenge.durationSeconds / 180).coerceAtLeast(1)) { GymPacingBlock("Interval ${it + 1}", 90, 90) }
            challenge.difficultyLabel.contains("Explosive", true) -> listOf(GymPacingBlock(challenge.title, 30, 90))
            challenge.title.contains("Cadence", true) -> List(5) { GymPacingBlock("Cadence block ${it + 1}", 150, 30) }
            challenge.title.contains("Climb", true) -> List(5) { GymPacingBlock("Climb block ${it + 1}", 240, 60) }
            challenge.title.contains("Long Haul", true) -> List(3) { GymPacingBlock("Endurance block ${it + 1}", 840, 60) }
            else -> listOf(GymPacingBlock(challenge.title, challenge.durationSeconds, 0))
        }
        return GymWorkoutPacing(blocks)
    }
    return when (workoutType) {
        GymWorkoutType.CYCLING -> GymWorkoutPacing(List(3) { GymPacingBlock("Ride block ${it + 1}", 840, 60) })
        GymWorkoutType.STRENGTH -> GymWorkoutPacing(List(4) { GymPacingBlock("Strength set ${it + 1}", 180, 90) })
        else -> GymWorkoutPacing(List(3) { GymPacingBlock("Workout block ${it + 1}", 480, 60) })
    }
}

/** Easier, higher-rep, and new-to-training work gets more recovery without becoming idle time. */
fun recommendedRestSeconds(repScheme: String): Int {
    val scheme = repScheme.lowercase()
    return when {
        "on /" in scheme || "push /" in scheme -> 30
        "5 reps" in scheme -> 150
        "8 reps" in scheme -> 105
        "10 reps" in scheme -> 75
        "12 reps" in scheme || "15 reps" in scheme -> 60
        "easy spin" in scheme -> 45
        else -> 75
    }
}
