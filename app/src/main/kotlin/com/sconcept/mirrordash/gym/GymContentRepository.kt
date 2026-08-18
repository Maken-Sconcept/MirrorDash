package com.sconcept.mirrordash.gym

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class GymContentRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun loadPhoenixSeedCatalog(): List<GymExerciseCatalogEntry> = withContext(Dispatchers.IO) {
        val payload = context.assets
            .open("gym/phoenix_seed_catalog.json")
            .bufferedReader()
            .use { it.readText() }
        json.decodeFromString(ListSerializer(GymExerciseCatalogEntry.serializer()), payload)
            .map { it.copy(name = it.name.trim()) }
            .sortedBy { it.name.lowercase() }
    }
}
