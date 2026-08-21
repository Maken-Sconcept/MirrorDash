package com.sconcept.mirrordash.gym

import android.content.Context
import android.net.Uri
import com.sconcept.mirrordash.nas.SmbPaths
import com.sconcept.mirrordash.nas.SmbRepository
import com.sconcept.mirrordash.settings.SettingsRepository
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class GymContentRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val smbRepository = SmbRepository(context.applicationContext)
    private val videoCacheDir = File(context.cacheDir, "workout_videos").apply { mkdirs() }

    /** The production catalog is generated from the Workout NAS library. */
    suspend fun loadWorkoutCatalog(): List<GymExerciseCatalogEntry> = withContext(Dispatchers.IO) {
        val payload = context.assets
            .open("gym/workout_catalog.json")
            .bufferedReader()
            .use { it.readText() }
        val catalog = json.decodeFromString(ListSerializer(GymExerciseCatalogEntry.serializer()), payload)
        val videosByName = loadWorkoutLibrary()
            .associateBy { it.name.trim().lowercase() }

        catalog
            .map { entry ->
                val libraryEntry = videosByName[entry.name.trim().lowercase()]
                entry.copy(
                    videos = libraryEntry?.videos.orEmpty(),
                    libraryGroup = libraryEntry?.muscleGroup,
                )
            }
            .map { it.copy(name = it.name.trim()) }
            .sortedBy { it.name.lowercase() }
    }

    /** Resolves a video exactly as specified by the library: local URI first, then SMB cache. */
    suspend fun resolveVideoUri(video: GymExerciseVideo): Result<String> = withContext(Dispatchers.IO) {
        val local = video.localUri?.trim().orEmpty()
        if (local.isNotBlank()) {
            return@withContext runCatching { Uri.parse(local).toString() }
        }

        runCatching {
            val share = settingsRepository.smbShareWithPassword()
            require(share.isConfigured) { "Configure the NAS connection in Settings before playing NAS workout videos." }
            val relativePath = video.nasRelativePath?.takeIf { it.isNotBlank() }
                ?: "Entertainment/Workouts/${video.relativePath}"
            val cacheFile = File(videoCacheDir, cacheFileName(relativePath, video.filename))
            if (!cacheFile.exists() || cacheFile.length() == 0L) {
                val temp = File(videoCacheDir, "${cacheFile.name}.tmp")
                try {
                    smbRepository.openStream(share, SmbPaths.displayPath(share, relativePath)).use { input ->
                        temp.outputStream().use(input::copyTo)
                    }
                    check(temp.renameTo(cacheFile)) { "Could not save the NAS video locally." }
                } finally {
                    if (temp.exists()) temp.delete()
                }
            }
            Uri.fromFile(cacheFile).toString()
        }
    }

    private fun loadWorkoutLibrary(): List<GymWorkoutLibraryExercise> {
        val payload = context.assets.open("gym/workout_library.json").bufferedReader().use { it.readText() }
        return json.decodeFromString(GymWorkoutLibraryDocument.serializer(), payload).exercises
    }

    private fun cacheFileName(path: String, filename: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(path.toByteArray(Charsets.UTF_8))
        val hash = digest.joinToString("") { "%02x".format(it) }
        return "$hash.${filename.substringAfterLast('.', "mp4")}" 
    }
}
