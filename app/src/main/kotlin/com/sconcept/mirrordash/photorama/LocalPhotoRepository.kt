package com.sconcept.mirrordash.photorama

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.sconcept.mirrordash.nas.model.LanPhoto
import com.sconcept.mirrordash.nas.model.SmbConnectionState
import com.sconcept.mirrordash.nas.model.SmbResult

/** [com.sconcept.mirrordash.nas.LanPhotoRepository]'s local-storage counterpart - indexes a
 * user-picked SAF folder tree (internal storage, an SD card, a USB drive - whatever the system
 * folder picker resolves to) instead of an SMB share. Reuses [SmbResult]/[SmbConnectionState]
 * rather than a parallel local-only result type, since [PhotoramaViewModel] already knows how to
 * drive its whole connecting/reconnecting/failed state machine off those - a local folder just
 * never actually reconnects, it's either readable right now or it isn't. [LanPhoto.url] carries
 * the file's own `content://` URI string, which downstream code (see [PhotoramaViewModel.showCurrent])
 * recognizes and resolves directly instead of routing through [com.sconcept.mirrordash.nas.PhotoCacheManager] -
 * a local file needs no NAS-style fetch-and-cache round trip. */
class LocalPhotoRepository(private val context: Context) {

    companion object {
        private val SUPPORTED_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif")
        private const val MAX_RECURSION_DEPTH = 6

        fun isSupportedPhoto(name: String): Boolean {
            val ext = name.substringAfterLast('.', "").lowercase()
            return ext in SUPPORTED_EXTENSIONS
        }
    }

    fun scanFolder(treeUriString: String, includeSubfolders: Boolean): SmbResult<List<LanPhoto>> {
        if (treeUriString.isBlank()) {
            return SmbResult.Failure(SmbConnectionState.SHARE_UNAVAILABLE, "No folder selected.")
        }
        return try {
            val root = DocumentFile.fromTreeUri(context, Uri.parse(treeUriString))
            if (root == null || !root.exists() || !root.canRead()) {
                return SmbResult.Failure(
                    SmbConnectionState.SHARE_UNAVAILABLE,
                    "Lost access to the selected folder - choose it again in Settings.",
                )
            }
            val photos = mutableListOf<LanPhoto>()
            scanRecursive(root, includeSubfolders, photos, depth = 0)
            SmbResult.Success(photos.toList())
        } catch (e: Exception) {
            SmbResult.Failure(SmbConnectionState.SHARE_UNAVAILABLE, e.message ?: "Couldn't read the selected folder.")
        }
    }

    private fun scanRecursive(dir: DocumentFile, includeSubfolders: Boolean, into: MutableList<LanPhoto>, depth: Int) {
        if (depth > MAX_RECURSION_DEPTH) return
        for (child in dir.listFiles()) {
            if (child.isDirectory) {
                if (includeSubfolders) scanRecursive(child, includeSubfolders, into, depth + 1)
            } else {
                val name = child.name.orEmpty()
                if (isSupportedPhoto(name)) {
                    into.add(LanPhoto(name = name, url = child.uri.toString(), sizeBytes = child.length()))
                }
            }
        }
    }
}
