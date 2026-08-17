package com.sconcept.mirrordash.photobooth

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.serialization.json.Json

private const val TAG = "PhotoboothRepository"
private val dateFormat get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)
private val json = Json { ignoreUnknownKeys = true }

/**
 * Owns Photobooth's on-disk storage (brief §8): `Photobooth/YYYY-MM-DD/session_<ms>/` holding
 * `photo_01.jpg`/`photo_02.jpg`/`photo_03.jpg`, `montage.jpg`, and `session.json`. Every image is
 * addressed by one opaque id (`"<dateDir>/session_<ms>/<fileName>"`, relative to the Photobooth
 * root) so callers - including MirrorDrop's Phase 9 wiring - never see a raw filesystem path;
 * [resolveFile] is the only place an id ever becomes a [File], and it refuses anything that would
 * resolve outside [root] (brief §14/§36 - the same "no arbitrary path" posture MirrorDrop's own
 * content sources follow).
 */
class PhotoboothRepository(context: Context) {

    private val root = File(context.filesDir, "Photobooth").apply { mkdirs() }

    fun saveSession(photoJpegs: List<ByteArray>, montageJpeg: ByteArray): PhotoboothSession {
        require(photoJpegs.size == 3) { "Photobooth sessions are always exactly 3 photos" }
        val now = System.currentTimeMillis()
        val dateDir = File(root, dateFormat.format(Date(now))).apply { mkdirs() }
        val sessionDir = File(dateDir, "session_$now").apply { mkdirs() }
        val sessionId = "${dateDir.name}/${sessionDir.name}"

        val photoNames = photoJpegs.mapIndexed { index, bytes ->
            "photo_%02d.jpg".format(index + 1).also { name -> File(sessionDir, name).writeBytes(bytes) }
        }
        val montageName = "montage.jpg"
        File(sessionDir, montageName).writeBytes(montageJpeg)

        val session = PhotoboothSession(
            id = sessionId,
            createdAtMs = now,
            photoFileNames = photoNames,
            montageFileName = montageName,
        )
        runCatching {
            File(sessionDir, "session.json").writeText(json.encodeToString(PhotoboothSession.serializer(), session))
        }.onFailure { Log.w(TAG, "Failed writing session.json for $sessionId", it) }

        return session
    }

    fun getSessions(): List<PhotoboothSession> =
        root.listFiles { file -> file.isDirectory }.orEmpty()
            .flatMap { dateDir -> dateDir.listFiles { file -> file.isDirectory }.orEmpty().toList() }
            .mapNotNull { sessionDir -> readSession(sessionDir) }
            .sortedByDescending { it.createdAtMs }

    fun getLatestSession(): PhotoboothSession? = getSessions().firstOrNull()

    fun getLatestPhoto(): PhotoboothImage? {
        val session = getLatestSession() ?: return null
        val name = session.photoFileNames.lastOrNull() ?: return null
        return getImage("${session.id}/$name")
    }

    fun getLatestMontage(): PhotoboothImage? {
        val session = getLatestSession() ?: return null
        return getImage("${session.id}/${session.montageFileName}")
    }

    fun getImagesForSession(sessionId: String): List<PhotoboothImage> {
        val session = getSessions().firstOrNull { it.id == sessionId } ?: return emptyList()
        return (session.photoFileNames + session.montageFileName).mapNotNull { name -> getImage("${session.id}/$name") }
    }

    fun getAllPhotoboothImages(): List<PhotoboothImage> = getSessions().flatMap { getImagesForSession(it.id) }

    fun getImage(id: String): PhotoboothImage? {
        val file = resolveFile(id) ?: return null
        if (!file.isFile) return null
        return PhotoboothImage(id = id, sessionId = id.substringBeforeLast('/'), name = file.name, bytes = file.readBytes())
    }

    private fun resolveFile(id: String): File? {
        if (id.isBlank() || id.contains("..")) return null
        val normalizedRoot = root.canonicalFile
        val normalizedCandidate = File(root, id).canonicalFile
        if (!normalizedCandidate.path.startsWith(normalizedRoot.path + File.separator)) return null
        return normalizedCandidate
    }

    private fun readSession(sessionDir: File): PhotoboothSession? {
        val jsonFile = File(sessionDir, "session.json")
        if (!jsonFile.isFile) return null
        return runCatching { json.decodeFromString(PhotoboothSession.serializer(), jsonFile.readText()) }
            .onFailure { Log.w(TAG, "Failed reading ${jsonFile.path}", it) }
            .getOrNull()
    }
}
