package com.sconcept.mirrordash.nas

import android.content.Context
import com.sconcept.mirrordash.nas.model.LanPhoto
import com.sconcept.mirrordash.nas.model.SmbResult
import com.sconcept.mirrordash.nas.model.SmbShare

/**
 * Indexes the configured SMB Photorama folder into a flat list of supported photos. Ported
 * unchanged from BerthierOptions. Blocking / must be called off the UI thread - callers should
 * cache the resulting list and only re-scan periodically (see PhotoramaController).
 */
class LanPhotoRepository(context: Context) {

    private val smbRepository = SmbRepository(context)

    companion object {
        private val SUPPORTED_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif")
        private const val MAX_RECURSION_DEPTH = 6

        fun isSupportedPhoto(name: String): Boolean {
            val ext = name.substringAfterLast('.', "").lowercase()
            return ext in SUPPORTED_EXTENSIONS
        }
    }

    fun scanFolder(share: SmbShare, folderPath: String, includeSubfolders: Boolean): SmbResult<List<LanPhoto>> {
        val photos = mutableListOf<LanPhoto>()
        return when (val result = scanRecursive(share, folderPath, includeSubfolders, photos, depth = 0)) {
            is SmbResult.Failure -> result
            is SmbResult.Success -> SmbResult.Success(photos.toList())
        }
    }

    private fun scanRecursive(
        share: SmbShare,
        path: String,
        includeSubfolders: Boolean,
        into: MutableList<LanPhoto>,
        depth: Int,
    ): SmbResult<Unit> {
        if (depth > MAX_RECURSION_DEPTH) return SmbResult.Success(Unit)

        val items = when (val listResult = smbRepository.list(share, path)) {
            is SmbResult.Success -> listResult.value
            is SmbResult.Failure -> return listResult
        }

        for (item in items) {
            if (item.isDirectory) {
                if (includeSubfolders) {
                    val childPath = SmbPaths.childPath(path, item.name)
                    val childResult = scanRecursive(share, childPath, includeSubfolders, into, depth + 1)
                    if (childResult is SmbResult.Failure) return childResult
                }
            } else if (isSupportedPhoto(item.name)) {
                into.add(LanPhoto(name = item.name, url = item.url, sizeBytes = item.sizeBytes))
            }
        }
        return SmbResult.Success(Unit)
    }
}
