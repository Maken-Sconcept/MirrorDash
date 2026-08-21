package com.sconcept.mirrordash.gym

private data class GymExerciseTemplate(
    val title: String,
    val focusLabel: String,
    val targetedMuscles: List<String>,
    val equipment: Set<GymEquipmentOption>,
    val supportedGoals: Set<GymTrainingGoal>,
    val minimumLevel: GymTrainingLevel,
    val durationMinutes: Int,
    val repScheme: String,
    val coachingCue: String,
    val workSeconds: Int = durationMinutes * 60,
    val restSeconds: Int = recommendedRestSeconds(repScheme),
    val intensity: String = "steady",
)

fun defaultGymGeneratorPreferences(): GymGeneratorPreferences = GymGeneratorPreferences(
    equipment = listOf(
        GymEquipmentOption.BODYWEIGHT,
        GymEquipmentOption.DUMBBELLS,
        GymEquipmentOption.BANDS,
    ),
)

fun buildGymGeneratedWorkoutPlan(
    preferences: GymGeneratorPreferences,
    catalogEntries: List<GymExerciseCatalogEntry> = emptyList(),
): GymGeneratedWorkoutPlan {
    val equipment = preferences.equipment.ifEmpty { defaultGymGeneratorPreferences().equipment }
    val templates = gymExerciseTemplates + catalogEntries.toCatalogTemplates()
    val equipmentEligible = templates.filter { template ->
        template.equipment.any { it in equipment } && template.minimumLevel.ordinal <= preferences.level.ordinal
    }.ifEmpty {
        templates.filter { it.minimumLevel.ordinal <= preferences.level.ordinal }
    }
    val selectedMuscles = preferences.muscleGroups.map { it.displayLabel.lowercase() }
    val eligible = equipmentEligible.filter { template ->
        selectedMuscles.isEmpty() || template.targetedMuscles.any { muscle ->
            selectedMuscles.any { selected -> muscle.lowercase().contains(selected) }
        }
    }.ifEmpty { equipmentEligible }
    val ranked = eligible.sortedByDescending { templateScore(it, preferences, equipment) }
    val selected = mutableListOf<GymExerciseTemplate>()
    val usedFocus = mutableMapOf<String, Int>()
    ranked.forEach { template ->
        if (selected.size >= minOf(preferences.exerciseCount, (preferences.durationMinutes / 5).coerceAtLeast(2))) return@forEach
        val focusHits = usedFocus[template.focusLabel].orEmpty()
        if (focusHits >= 2 && ranked.count { it.focusLabel == template.focusLabel } > 2) return@forEach
        selected += template
        usedFocus[template.focusLabel] = focusHits + 1
    }
    if (selected.size < preferences.exerciseCount) {
        ranked.forEach { template ->
            if (selected.size >= minOf(preferences.exerciseCount, (preferences.durationMinutes / 5).coerceAtLeast(2))) return@forEach
            if (template !in selected) selected += template
        }
    }

    val exercises = selected.mapIndexed { index, template ->
        GymGeneratedExercise(
            order = index + 1,
            title = template.title,
            focusLabel = template.focusLabel,
            targetedMuscles = template.targetedMuscles,
            durationMinutes = template.durationMinutes,
            workSeconds = template.workSeconds,
            restSeconds = template.restSeconds,
            intensity = template.intensity,
            repScheme = template.repScheme,
            coachingCue = template.coachingCue,
        )
    }
    val targetedMuscles = exercises
        .flatMap { it.targetedMuscles }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .map { it.key }
        .take(4)
    val title = when (preferences.goal) {
        GymTrainingGoal.BUILD_MUSCLE -> "Hypertrophy Builder"
        GymTrainingGoal.GAIN_STRENGTH -> "Strength Ladder"
        GymTrainingGoal.LOSE_WEIGHT -> "Metabolic Burn"
        GymTrainingGoal.MOBILITY -> "Mobility Flow"
        GymTrainingGoal.RECOVERY -> "Recovery Reset"
    }
    val equipmentSummary = when {
        GymEquipmentOption.BODYWEIGHT in equipment && equipment.size == 1 -> "Bodyweight only"
        GymEquipmentOption.BARBELL in equipment -> "Strength rack ready"
        GymEquipmentOption.BIKE in equipment -> "Bike + floor circuit"
        else -> equipment.take(3).joinToString(" / ") { it.displayLabel }
    }
    val subtitle = "${preferences.level.displayLabel} / $equipmentSummary / ${preferences.durationMinutes} min"
    val estimatedMinutes = preferences.durationMinutes
    val focusSummary = when (preferences.goal) {
        GymTrainingGoal.BUILD_MUSCLE -> "Volume-focused sets with tempo control and short rests."
        GymTrainingGoal.GAIN_STRENGTH -> "Heavy compound emphasis with crisp technique and full recovery."
        GymTrainingGoal.LOSE_WEIGHT -> "Continuous movement with low setup friction and a cardio finisher."
        GymTrainingGoal.MOBILITY -> "Range-of-motion first with posture, breath, and control cues."
        GymTrainingGoal.RECOVERY -> "Lower-intensity tissue work to reset joints and restore rhythm."
    }
    return GymGeneratedWorkoutPlan(
        title = title,
        subtitle = subtitle,
        estimatedMinutes = estimatedMinutes,
        targetedMuscles = targetedMuscles,
        focusSummary = focusSummary,
        exercises = exercises,
    )
}

private fun templateScore(
    template: GymExerciseTemplate,
    preferences: GymGeneratorPreferences,
    equipment: List<GymEquipmentOption>,
): Int {
    var score = 0
    if (preferences.goal in template.supportedGoals) score += 6
    if (preferences.muscleGroups.any { group -> template.targetedMuscles.any { it.contains(group.displayLabel, ignoreCase = true) } }) score += 8
    score += template.equipment.count { it in equipment } * 3
    score -= (preferences.level.ordinal - template.minimumLevel.ordinal).coerceAtLeast(0)
    if (preferences.goal == GymTrainingGoal.LOSE_WEIGHT && template.durationMinutes <= 5) score += 2
    if (preferences.goal == GymTrainingGoal.RECOVERY && "Mobility" in template.focusLabel) score += 3
    return score
}

private fun Int?.orEmpty(): Int = this ?: 0

private fun List<GymExerciseCatalogEntry>.toCatalogTemplates(): List<GymExerciseTemplate> = mapNotNull { entry ->
    if (entry.name.isBlank()) return@mapNotNull null
    val mappedEquipment = entry.equipment.mapNotNull(::mapCatalogEquipment).toSet()
        .ifEmpty { setOf(GymEquipmentOption.BODYWEIGHT) }
    val targetedMuscles = entry.muscleGroups
        .ifEmpty { entry.muscles.map(::humanizeCatalogToken) }
        .ifEmpty { listOf("Full Body") }
        .distinct()
        .take(4)
    val focusLabel = targetedMuscles.firstOrNull() ?: "Full Body"
    GymExerciseTemplate(
        title = entry.name,
        focusLabel = focusLabel,
        targetedMuscles = targetedMuscles,
        equipment = mappedEquipment,
        supportedGoals = inferGoals(targetedMuscles),
        minimumLevel = entry.level.toGymTrainingLevel(),
        durationMinutes = inferDurationMinutes(targetedMuscles, mappedEquipment),
        repScheme = inferRepScheme(targetedMuscles, mappedEquipment),
        coachingCue = inferCoachingCue(targetedMuscles, mappedEquipment),
        workSeconds = entry.pacing.workSeconds,
        restSeconds = entry.pacing.restSeconds,
        intensity = entry.pacing.intensity,
    )
}.distinctBy { it.title.lowercase() }

private fun mapCatalogEquipment(value: String): GymEquipmentOption? = when (value.uppercase()) {
    "DUMBBELL", "DUMBBELLS" -> GymEquipmentOption.DUMBBELLS
    "BARBELL", "BARBELLS" -> GymEquipmentOption.BARBELL
    "KETTLEBELL", "KETTLEBELLS" -> GymEquipmentOption.KETTLEBELLS
    "BAND", "BANDS" -> GymEquipmentOption.BANDS
    "CABLE", "CABLES" -> GymEquipmentOption.CABLES
    "BENCH" -> GymEquipmentOption.BENCH
    "BIKE", "CYCLE", "CYCLING" -> GymEquipmentOption.BIKE
    "ROW", "ROWER", "ROWING" -> GymEquipmentOption.ROWING
    "BODYWEIGHT" -> GymEquipmentOption.BODYWEIGHT
    else -> null
}

private fun humanizeCatalogToken(value: String): String =
    value.lowercase()
        .split('_', '-', ' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { token -> token.replaceFirstChar { it.uppercase() } }

private fun inferGoals(targetedMuscles: List<String>): Set<GymTrainingGoal> {
    val joined = targetedMuscles.joinToString(" ").lowercase()
    return when {
        "cardio" in joined -> setOf(GymTrainingGoal.LOSE_WEIGHT, GymTrainingGoal.RECOVERY)
        "mobility" in joined || "stretch" in joined -> setOf(GymTrainingGoal.MOBILITY, GymTrainingGoal.RECOVERY)
        "core" in joined -> setOf(GymTrainingGoal.BUILD_MUSCLE, GymTrainingGoal.LOSE_WEIGHT, GymTrainingGoal.MOBILITY)
        else -> setOf(GymTrainingGoal.BUILD_MUSCLE, GymTrainingGoal.GAIN_STRENGTH, GymTrainingGoal.LOSE_WEIGHT)
    }
}

private fun inferMinimumLevel(entry: GymExerciseCatalogEntry): GymTrainingLevel = when (entry.sidedness?.lowercase()) {
    "alternating", "unilateral" -> GymTrainingLevel.BEGINNER
    else -> GymTrainingLevel.NOVICE
}

private fun String.toGymTrainingLevel(): GymTrainingLevel = when (uppercase()) {
    "NOVICE" -> GymTrainingLevel.NOVICE
    "INTERMEDIATE" -> GymTrainingLevel.INTERMEDIATE
    "ADVANCED" -> GymTrainingLevel.ADVANCED
    else -> GymTrainingLevel.BEGINNER
}

private fun inferDurationMinutes(
    targetedMuscles: List<String>,
    equipment: Set<GymEquipmentOption>,
): Int {
    val joined = targetedMuscles.joinToString(" ").lowercase()
    return when {
        GymEquipmentOption.BIKE in equipment || GymEquipmentOption.ROWING in equipment -> 6
        "mobility" in joined || "stretch" in joined -> 3
        else -> 4
    }
}

private fun inferRepScheme(
    targetedMuscles: List<String>,
    equipment: Set<GymEquipmentOption>,
): String {
    val joined = targetedMuscles.joinToString(" ").lowercase()
    return when {
        GymEquipmentOption.BIKE in equipment || GymEquipmentOption.ROWING in equipment -> "5 rounds / 45 sec push / 30 sec easy"
        "mobility" in joined || "stretch" in joined -> "2 rounds / 45 sec each side"
        "core" in joined -> "3 sets / 10 controlled reps"
        else -> "3 sets / 8 to 12 reps"
    }
}

private fun inferCoachingCue(
    targetedMuscles: List<String>,
    equipment: Set<GymEquipmentOption>,
): String {
    val joined = targetedMuscles.joinToString(" ").lowercase()
    return when {
        GymEquipmentOption.BIKE in equipment || GymEquipmentOption.ROWING in equipment ->
            "Build pressure smoothly, stay tall through the torso, and keep the finish repeatable."
        "mobility" in joined || "stretch" in joined ->
            "Move slowly enough to own the end range and let your breathing set the pace."
        "core" in joined ->
            "Brace first, keep the ribs stacked, and stop each rep before posture starts leaking."
        else ->
            "Own the full range, keep the setup balanced, and make each rep look the same as the one before it."
    }
}

private val gymExerciseTemplates = listOf(
    GymExerciseTemplate(
        title = "Bodyweight Squat Pulse",
        focusLabel = "Lower Body",
        targetedMuscles = listOf("Quads", "Glutes"),
        equipment = setOf(GymEquipmentOption.BODYWEIGHT),
        supportedGoals = setOf(GymTrainingGoal.LOSE_WEIGHT, GymTrainingGoal.MOBILITY, GymTrainingGoal.RECOVERY),
        minimumLevel = GymTrainingLevel.NOVICE,
        durationMinutes = 4,
        repScheme = "3 rounds • 45 sec on / 15 sec reset",
        coachingCue = "Stay tall through the chest and keep the knees tracking over the toes.",
    ),
    GymExerciseTemplate(
        title = "Goblet Squat",
        focusLabel = "Lower Body",
        targetedMuscles = listOf("Quads", "Glutes", "Core"),
        equipment = setOf(GymEquipmentOption.DUMBBELLS, GymEquipmentOption.KETTLEBELLS),
        supportedGoals = setOf(GymTrainingGoal.BUILD_MUSCLE, GymTrainingGoal.GAIN_STRENGTH, GymTrainingGoal.LOSE_WEIGHT),
        minimumLevel = GymTrainingLevel.BEGINNER,
        durationMinutes = 5,
        repScheme = "4 sets • 10 reps",
        coachingCue = "Brace first, sit between the hips, and drive through the full foot on the way up.",
    ),
    GymExerciseTemplate(
        title = "Barbell Back Squat",
        focusLabel = "Lower Body",
        targetedMuscles = listOf("Quads", "Glutes", "Spinal Erectors"),
        equipment = setOf(GymEquipmentOption.BARBELL, GymEquipmentOption.BENCH),
        supportedGoals = setOf(GymTrainingGoal.GAIN_STRENGTH, GymTrainingGoal.BUILD_MUSCLE),
        minimumLevel = GymTrainingLevel.INTERMEDIATE,
        durationMinutes = 6,
        repScheme = "5 sets • 5 reps",
        coachingCue = "Create upper-back tension, control the descent, and punch up with the hips and legs together.",
    ),
    GymExerciseTemplate(
        title = "Romanian Deadlift",
        focusLabel = "Posterior Chain",
        targetedMuscles = listOf("Hamstrings", "Glutes", "Lower Back"),
        equipment = setOf(GymEquipmentOption.DUMBBELLS, GymEquipmentOption.BARBELL, GymEquipmentOption.KETTLEBELLS),
        supportedGoals = setOf(GymTrainingGoal.BUILD_MUSCLE, GymTrainingGoal.GAIN_STRENGTH),
        minimumLevel = GymTrainingLevel.BEGINNER,
        durationMinutes = 5,
        repScheme = "4 sets • 8 reps",
        coachingCue = "Push the hips back, keep the lats packed, and stop when the hamstrings fully load.",
    ),
    GymExerciseTemplate(
        title = "Bench Press",
        focusLabel = "Push",
        targetedMuscles = listOf("Chest", "Triceps", "Front Delts"),
        equipment = setOf(GymEquipmentOption.BARBELL, GymEquipmentOption.BENCH),
        supportedGoals = setOf(GymTrainingGoal.GAIN_STRENGTH, GymTrainingGoal.BUILD_MUSCLE),
        minimumLevel = GymTrainingLevel.INTERMEDIATE,
        durationMinutes = 6,
        repScheme = "5 sets • 5 reps",
        coachingCue = "Drive the shoulder blades into the bench and keep the bar path stacked over the wrists.",
    ),
    GymExerciseTemplate(
        title = "Dumbbell Bench Press",
        focusLabel = "Push",
        targetedMuscles = listOf("Chest", "Triceps", "Front Delts"),
        equipment = setOf(GymEquipmentOption.DUMBBELLS, GymEquipmentOption.BENCH),
        supportedGoals = setOf(GymTrainingGoal.BUILD_MUSCLE, GymTrainingGoal.GAIN_STRENGTH),
        minimumLevel = GymTrainingLevel.BEGINNER,
        durationMinutes = 5,
        repScheme = "4 sets • 10 reps",
        coachingCue = "Lower with control, keep the elbows 30 to 45 degrees from the torso, and exhale through the press.",
    ),
    GymExerciseTemplate(
        title = "Push-Up Plus",
        focusLabel = "Push",
        targetedMuscles = listOf("Chest", "Triceps", "Serratus"),
        equipment = setOf(GymEquipmentOption.BODYWEIGHT),
        supportedGoals = setOf(GymTrainingGoal.LOSE_WEIGHT, GymTrainingGoal.BUILD_MUSCLE, GymTrainingGoal.MOBILITY),
        minimumLevel = GymTrainingLevel.NOVICE,
        durationMinutes = 4,
        repScheme = "3 sets • 12 reps",
        coachingCue = "Finish each rep by reaching the upper back long without shrugging the shoulders.",
    ),
    GymExerciseTemplate(
        title = "Single-Arm Row",
        focusLabel = "Pull",
        targetedMuscles = listOf("Lats", "Mid Back", "Biceps"),
        equipment = setOf(GymEquipmentOption.DUMBBELLS, GymEquipmentOption.BENCH),
        supportedGoals = setOf(GymTrainingGoal.BUILD_MUSCLE, GymTrainingGoal.GAIN_STRENGTH),
        minimumLevel = GymTrainingLevel.BEGINNER,
        durationMinutes = 5,
        repScheme = "4 sets • 10 reps each side",
        coachingCue = "Set the torso first, then pull the elbow toward the hip instead of yanking the weight upward.",
    ),
    GymExerciseTemplate(
        title = "Bent-Over Row",
        focusLabel = "Pull",
        targetedMuscles = listOf("Lats", "Mid Back", "Rear Delts"),
        equipment = setOf(GymEquipmentOption.BARBELL, GymEquipmentOption.DUMBBELLS),
        supportedGoals = setOf(GymTrainingGoal.GAIN_STRENGTH, GymTrainingGoal.BUILD_MUSCLE),
        minimumLevel = GymTrainingLevel.INTERMEDIATE,
        durationMinutes = 5,
        repScheme = "4 sets • 8 reps",
        coachingCue = "Hold the hinge, keep the ribs down, and pause the handle against the body at the top.",
    ),
    GymExerciseTemplate(
        title = "Band Pull-Apart",
        focusLabel = "Upper Back",
        targetedMuscles = listOf("Rear Delts", "Mid Back"),
        equipment = setOf(GymEquipmentOption.BANDS),
        supportedGoals = setOf(GymTrainingGoal.RECOVERY, GymTrainingGoal.MOBILITY, GymTrainingGoal.BUILD_MUSCLE),
        minimumLevel = GymTrainingLevel.NOVICE,
        durationMinutes = 3,
        repScheme = "3 sets • 15 reps",
        coachingCue = "Keep the ribs tucked and separate the hands by moving through the upper back, not the low back.",
    ),
    GymExerciseTemplate(
        title = "Cable Press + Row Ladder",
        focusLabel = "Push/Pull",
        targetedMuscles = listOf("Chest", "Lats", "Core"),
        equipment = setOf(GymEquipmentOption.CABLES),
        supportedGoals = setOf(GymTrainingGoal.LOSE_WEIGHT, GymTrainingGoal.BUILD_MUSCLE),
        minimumLevel = GymTrainingLevel.BEGINNER,
        durationMinutes = 5,
        repScheme = "3 rounds • 10 press / 10 row",
        coachingCue = "Stay square through the torso and keep every rep smooth enough to own the end range.",
    ),
    GymExerciseTemplate(
        title = "Kettlebell Swing",
        focusLabel = "Conditioning",
        targetedMuscles = listOf("Glutes", "Hamstrings", "Core"),
        equipment = setOf(GymEquipmentOption.KETTLEBELLS),
        supportedGoals = setOf(GymTrainingGoal.LOSE_WEIGHT, GymTrainingGoal.GAIN_STRENGTH),
        minimumLevel = GymTrainingLevel.BEGINNER,
        durationMinutes = 4,
        repScheme = "6 rounds • 20 sec on / 20 sec off",
        coachingCue = "Snap from the hips, let the bell float, and keep the shoulders packed the whole time.",
    ),
    GymExerciseTemplate(
        title = "Bike Sprint Builder",
        focusLabel = "Conditioning",
        targetedMuscles = listOf("Quads", "Calves", "Cardio"),
        equipment = setOf(GymEquipmentOption.BIKE),
        supportedGoals = setOf(GymTrainingGoal.LOSE_WEIGHT, GymTrainingGoal.RECOVERY),
        minimumLevel = GymTrainingLevel.NOVICE,
        durationMinutes = 6,
        repScheme = "8 rounds • 30 sec push / 30 sec easy",
        coachingCue = "Build the cadence progressively and stay relaxed through the shoulders between surges.",
    ),
    GymExerciseTemplate(
        title = "Rower Power Pulls",
        focusLabel = "Conditioning",
        targetedMuscles = listOf("Back", "Legs", "Cardio"),
        equipment = setOf(GymEquipmentOption.ROWING),
        supportedGoals = setOf(GymTrainingGoal.LOSE_WEIGHT, GymTrainingGoal.GAIN_STRENGTH),
        minimumLevel = GymTrainingLevel.BEGINNER,
        durationMinutes = 6,
        repScheme = "5 rounds • 250 m / 45 sec recovery",
        coachingCue = "Push with the legs first, then finish the stroke with a clean pull to the lower ribs.",
    ),
    GymExerciseTemplate(
        title = "Reverse Lunge",
        focusLabel = "Single-Leg",
        targetedMuscles = listOf("Quads", "Glutes", "Adductors"),
        equipment = setOf(GymEquipmentOption.BODYWEIGHT, GymEquipmentOption.DUMBBELLS),
        supportedGoals = setOf(GymTrainingGoal.BUILD_MUSCLE, GymTrainingGoal.LOSE_WEIGHT, GymTrainingGoal.MOBILITY),
        minimumLevel = GymTrainingLevel.NOVICE,
        durationMinutes = 4,
        repScheme = "3 sets • 10 reps each side",
        coachingCue = "Step long enough to keep the front heel grounded and drive back to center with control.",
    ),
    GymExerciseTemplate(
        title = "Half-Kneeling Press",
        focusLabel = "Shoulders",
        targetedMuscles = listOf("Shoulders", "Core", "Triceps"),
        equipment = setOf(GymEquipmentOption.DUMBBELLS, GymEquipmentOption.BANDS),
        supportedGoals = setOf(GymTrainingGoal.BUILD_MUSCLE, GymTrainingGoal.MOBILITY),
        minimumLevel = GymTrainingLevel.BEGINNER,
        durationMinutes = 4,
        repScheme = "3 sets • 8 reps each side",
        coachingCue = "Squeeze the glute of the down leg and press straight up without leaning back.",
    ),
    GymExerciseTemplate(
        title = "Dead Bug Reach",
        focusLabel = "Core",
        targetedMuscles = listOf("Core", "Hip Flexors"),
        equipment = setOf(GymEquipmentOption.BODYWEIGHT),
        supportedGoals = setOf(GymTrainingGoal.RECOVERY, GymTrainingGoal.MOBILITY, GymTrainingGoal.LOSE_WEIGHT),
        minimumLevel = GymTrainingLevel.NOVICE,
        durationMinutes = 3,
        repScheme = "3 sets • 8 controlled reps each side",
        coachingCue = "Pin the ribs down and only move as far as you can without losing the lower-back contact.",
    ),
    GymExerciseTemplate(
        title = "Plank Shoulder Tap",
        focusLabel = "Core",
        targetedMuscles = listOf("Core", "Shoulders"),
        equipment = setOf(GymEquipmentOption.BODYWEIGHT),
        supportedGoals = setOf(GymTrainingGoal.LOSE_WEIGHT, GymTrainingGoal.BUILD_MUSCLE),
        minimumLevel = GymTrainingLevel.BEGINNER,
        durationMinutes = 3,
        repScheme = "3 rounds • 20 taps each side",
        coachingCue = "Keep the hips quiet and spread the floor away with the planted hand.",
    ),
    GymExerciseTemplate(
        title = "Glute Bridge Iso Hold",
        focusLabel = "Posterior Chain",
        targetedMuscles = listOf("Glutes", "Hamstrings"),
        equipment = setOf(GymEquipmentOption.BODYWEIGHT, GymEquipmentOption.BANDS),
        supportedGoals = setOf(GymTrainingGoal.RECOVERY, GymTrainingGoal.MOBILITY, GymTrainingGoal.BUILD_MUSCLE),
        minimumLevel = GymTrainingLevel.NOVICE,
        durationMinutes = 3,
        repScheme = "3 rounds • 30 sec hold / 10 pulses",
        coachingCue = "Tuck the pelvis gently and finish every rep by squeezing through the glutes instead of the low back.",
    ),
    GymExerciseTemplate(
        title = "World's Greatest Stretch",
        focusLabel = "Mobility",
        targetedMuscles = listOf("Hips", "Thoracic Spine", "Hamstrings"),
        equipment = setOf(GymEquipmentOption.BODYWEIGHT),
        supportedGoals = setOf(GymTrainingGoal.MOBILITY, GymTrainingGoal.RECOVERY),
        minimumLevel = GymTrainingLevel.NOVICE,
        durationMinutes = 3,
        repScheme = "2 rounds • 45 sec each side",
        coachingCue = "Own the breath, reach long through the spine, and spend a beat in each end range.",
    ),
    GymExerciseTemplate(
        title = "Thoracic Opener Flow",
        focusLabel = "Mobility",
        targetedMuscles = listOf("Thoracic Spine", "Shoulders"),
        equipment = setOf(GymEquipmentOption.BODYWEIGHT, GymEquipmentOption.BANDS),
        supportedGoals = setOf(GymTrainingGoal.MOBILITY, GymTrainingGoal.RECOVERY),
        minimumLevel = GymTrainingLevel.NOVICE,
        durationMinutes = 3,
        repScheme = "3 rounds • 6 reps each side",
        coachingCue = "Rotate from the ribcage and keep the hips stacked so the movement stays in the upper back.",
    ),
    GymExerciseTemplate(
        title = "Recovery Ride",
        focusLabel = "Mobility",
        targetedMuscles = listOf("Cardio", "Quads"),
        equipment = setOf(GymEquipmentOption.BIKE),
        supportedGoals = setOf(GymTrainingGoal.RECOVERY, GymTrainingGoal.MOBILITY),
        minimumLevel = GymTrainingLevel.NOVICE,
        durationMinutes = 8,
        repScheme = "8 min • easy spin",
        coachingCue = "Keep the resistance light enough that you can breathe through the nose and relax the grip.",
    ),
)
