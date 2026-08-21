package com.sconcept.mirrordash.gym

import kotlinx.serialization.Serializable

@Serializable
data class GymExercisePacingDefinition(
    val workSeconds: Int = 90,
    val restSeconds: Int = 75,
    val intensity: String = "steady",
    val continuous: Boolean = false,
)

@Serializable
data class GymExerciseCatalogEntry(
    val id: String,
    val name: String,
    val equipment: List<String> = emptyList(),
    val muscleGroups: List<String> = emptyList(),
    val muscles: List<String> = emptyList(),
    val sidedness: String? = null,
    val level: String = "BEGINNER",
    val pacing: GymExercisePacingDefinition = GymExercisePacingDefinition(),
    val videos: List<GymExerciseVideo> = emptyList(),
    val libraryGroup: String? = null,
)

/** A playback source from workout_library.json. `localUri` wins when supplied; otherwise the
 * NAS-relative path is fetched through the user's configured SMB connection. */
@Serializable
data class GymExerciseVideo(
    val filename: String,
    val relativePath: String,
    val localUri: String? = null,
    val nasRelativePath: String? = null,
    val durationSeconds: Int? = null,
)

@Serializable
internal data class GymWorkoutLibraryDocument(
    val exercises: List<GymWorkoutLibraryExercise> = emptyList(),
)

@Serializable
internal data class GymWorkoutLibraryExercise(
    val id: String,
    val name: String,
    val muscleGroup: String = "Full Body",
    val videos: List<GymExerciseVideo> = emptyList(),
)
