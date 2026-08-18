package com.sconcept.mirrordash.gym

import kotlinx.serialization.Serializable

@Serializable
data class GymExerciseCatalogEntry(
    val id: String,
    val name: String,
    val equipment: List<String> = emptyList(),
    val muscleGroups: List<String> = emptyList(),
    val muscles: List<String> = emptyList(),
    val sidedness: String? = null,
)
