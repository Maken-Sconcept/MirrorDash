package com.sconcept.mirrordash.gym

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.sconcept.mirrordash.nas.SmbPaths
import com.sconcept.mirrordash.nas.SmbRepository
import com.sconcept.mirrordash.nas.model.SmbResult
import com.sconcept.mirrordash.settings.SettingsRepository
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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

    /**
     * The library is deliberately app-specific external storage: it lives on the removable card
     * when one is mounted, does not need broad storage permission, and keeps the NAS tree below
     * `Workouts/` unchanged (for example `Legs/.../exercise.mp4`).
     */
    private fun memoryCardWorkoutRoot(): File? {
        // An adopted card is private storage. Android only grants an app access to it after the
        // app itself has been moved there, in which case its files directory resolves below
        // /mnt/expand/<volume-id>. Keep the mirror entirely on that card in this mode.
        val adoptedCardFiles = context.filesDir.takeIf(::isMountedMemoryCardDirectory)
        if (adoptedCardFiles != null) return File(adoptedCardFiles, "Workouts")

        return context
            .getExternalFilesDirs("Workouts")
            .filterNotNull()
            .firstOrNull(::isMountedMemoryCardDirectory)
    }

    /**
     * Android can format a physical SD card as adopted/private storage. In that mode it is
     * exposed below /mnt/expand/<volume-id> and may no longer report itself as "removable" to
     * [Environment]. It is still the physical card, unlike the device's /storage/emulated root.
     */
    private fun isMountedMemoryCardDirectory(directory: File): Boolean {
        if (Environment.getExternalStorageState(directory) != Environment.MEDIA_MOUNTED) return false
        if (Environment.isExternalStorageRemovable(directory)) return true
        val path = runCatching { directory.canonicalPath.replace('\\', '/') }.getOrDefault("")
        return path.contains("/mnt/expand/")
    }

    suspend fun syncWorkoutLibraryToMemoryCard(
        onProgress: (GymWorkoutSyncStatus) -> Unit = {},
    ): WorkoutLibrarySyncResult = withContext(Dispatchers.IO) {
        val targetRoot = memoryCardWorkoutRoot()
            ?: return@withContext WorkoutLibrarySyncResult.NoMemoryCard
        val settings = settingsRepository.settings.first()
        if (settings.gymFeatureSettings.workoutLibrarySource != GymWorkoutLibrarySource.MEMORY_CARD) {
            return@withContext WorkoutLibrarySyncResult.Skipped
        }
        val share = settingsRepository.smbShareWithPassword()
        if (!share.isConfigured) return@withContext WorkoutLibrarySyncResult.Failed("Configure the NAS connection to synchronize workouts.")

        runCatching {
            check(targetRoot.mkdirs() || targetRoot.isDirectory) { "Couldn't create the memory-card workout folder." }
            val marker = File(targetRoot, SYNC_MARKER_FILE)
            val remoteFiles = linkedMapOf<String, RemoteWorkoutFile>()
            onProgress(GymWorkoutSyncStatus.Indexing)
            collectRemoteFiles(share, NAS_WORKOUT_ROOT, "", remoteFiles)
            val filesToCopy = remoteFiles.values.filter { remote ->
                val local = safeChild(targetRoot, remote.relativePath)
                !local.isFile || local.length() != remote.sizeBytes
            }

            var copied = 0
            filesToCopy.forEachIndexed { index, remote ->
                onProgress(
                    GymWorkoutSyncStatus.Syncing(
                        currentFileName = remote.relativePath.substringAfterLast('/'),
                        currentFile = index + 1,
                        totalFiles = filesToCopy.size,
                    ),
                )
                val local = safeChild(targetRoot, remote.relativePath)
                if (!local.parentFile!!.exists()) check(local.parentFile!!.mkdirs()) { "Couldn't create ${local.parentFile}" }
                val temporary = File(local.parentFile, ".${local.name}.syncing")
                try {
                    smbRepository.openStream(share, remote.url).use { input ->
                        temporary.outputStream().use(input::copyTo)
                    }
                    check(temporary.length() == remote.sizeBytes) { "Incomplete copy for ${remote.relativePath}" }
                    temporary.copyTo(local, overwrite = true)
                    check(local.length() == remote.sizeBytes) { "Incomplete copy for ${remote.relativePath}" }
                    copied++
                } finally {
                    if (temporary.exists()) temporary.delete()
                }
            }

            // This app-owned `Workouts` directory is a true mirror, not a download cache:
            // remove files and empty folders that disappeared from the NAS on every pass.
            targetRoot.walkBottomUp().forEach { local ->
                if (local == targetRoot || local == marker) return@forEach
                val relative = local.relativeTo(targetRoot).invariantSeparatorsPath
                if (local.isFile && relative !in remoteFiles) local.delete()
                if (local.isDirectory && local.list().isNullOrEmpty()) local.delete()
            }
            marker.writeText("MirrorDash workout library mirror\n")
            WorkoutLibrarySyncResult.Success(totalFiles = remoteFiles.size, copiedFiles = copied)
        }.getOrElse { error -> WorkoutLibrarySyncResult.Failed(error.message ?: "Couldn't synchronize the workout library.") }
    }

    suspend fun workoutLibraryStatus(): GymWorkoutLibraryStatus = withContext(Dispatchers.IO) {
        val targetRoot = memoryCardWorkoutRoot()
            ?: return@withContext GymWorkoutLibraryStatus(message = "No removable memory card is mounted.")
        val cardVideoCount = targetRoot.walkTopDown().count { it.isFile && it.name != SYNC_MARKER_FILE }
        val settings = settingsRepository.settings.first()
        val share = settingsRepository.smbShareWithPassword()
        if (!share.isConfigured) return@withContext GymWorkoutLibraryStatus(
            cardVideoCount = cardVideoCount,
            message = "Configure the NAS connection to check the workout library.",
        )
        runCatching {
            val remoteFiles = linkedMapOf<String, RemoteWorkoutFile>()
            collectRemoteFiles(share, NAS_WORKOUT_ROOT, "", remoteFiles)
            val remaining = remoteFiles.values.count { remote ->
                val local = safeChild(targetRoot, remote.relativePath)
                !local.isFile || local.length() != remote.sizeBytes
            }
            GymWorkoutLibraryStatus(
                cardVideoCount = cardVideoCount,
                nasVideoCount = remoteFiles.size,
                remainingVideoCount = remaining,
            )
        }.getOrElse { error -> GymWorkoutLibraryStatus(cardVideoCount = cardVideoCount, message = error.message ?: "Couldn't check the NAS workout library.") }
    }

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

    /** Resolves a video from the card first (when selected), then falls back to the NAS cache. */
    suspend fun resolveVideoUri(video: GymExerciseVideo): Result<String> = withContext(Dispatchers.IO) {
        val local = video.localUri?.trim().orEmpty()
        if (local.isNotBlank()) {
            return@withContext runCatching { Uri.parse(local).toString() }
        }

        val settings = settingsRepository.settings.first()
        if (settings.gymFeatureSettings.workoutLibrarySource == GymWorkoutLibrarySource.MEMORY_CARD) {
            memoryCardWorkoutRoot()
                ?.let { root -> safeChild(root, video.relativePath) }
                ?.takeIf(File::isFile)
                ?.let { return@withContext Result.success(Uri.fromFile(it).toString()) }
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

    private fun collectRemoteFiles(
        share: com.sconcept.mirrordash.nas.model.SmbShare,
        remotePath: String,
        localPath: String,
        destination: MutableMap<String, RemoteWorkoutFile>,
    ) {
        val items = when (val result = smbRepository.list(share, SmbPaths.displayPath(share, remotePath))) {
            is SmbResult.Success -> result.value
            is SmbResult.Failure -> error(result.message)
        }
        items.forEach { item ->
            require(item.name.isNotBlank() && '/' !in item.name && '\\' !in item.name) { "Invalid NAS library item name." }
            val childLocalPath = listOf(localPath, item.name).filter(String::isNotBlank).joinToString("/")
            val childRemotePath = SmbPaths.childPath(remotePath, item.name)
            if (item.isDirectory) {
                collectRemoteFiles(share, childRemotePath, childLocalPath, destination)
            } else {
                destination[childLocalPath] = RemoteWorkoutFile(childLocalPath, item.url, item.sizeBytes)
            }
        }
    }

    private fun safeChild(root: File, relativePath: String): File {
        val child = File(root, relativePath)
        val rootPath = root.canonicalFile.path + File.separator
        require(child.canonicalFile.path.startsWith(rootPath)) { "Invalid workout library path." }
        return child
    }

    private data class RemoteWorkoutFile(val relativePath: String, val url: String, val sizeBytes: Long)

    companion object {
        private const val NAS_WORKOUT_ROOT = "Entertainment/Workouts"
        private const val SYNC_MARKER_FILE = ".mirrordash-workout-library"
    }
}

sealed interface WorkoutLibrarySyncResult {
    data class Success(val totalFiles: Int, val copiedFiles: Int) : WorkoutLibrarySyncResult
    data object NoMemoryCard : WorkoutLibrarySyncResult
    data object Skipped : WorkoutLibrarySyncResult
    data class Failed(val message: String) : WorkoutLibrarySyncResult
}
