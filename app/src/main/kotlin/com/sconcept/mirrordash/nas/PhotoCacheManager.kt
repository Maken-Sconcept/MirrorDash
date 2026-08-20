package com.sconcept.mirrordash.nas

import android.content.Context
import android.util.Log
import com.sconcept.mirrordash.nas.model.LanPhoto
import com.sconcept.mirrordash.nas.model.SmbShare
import java.io.File
import java.security.MessageDigest

/**
 * A small disk cache of recently-used Photorama photos, keyed by a hash of their SMB URL.
 * Bounded to a configurable max size via oldest-accessed-first eviction. Ported unchanged from
 * BerthierOptions: this is a cache, not a mirror - it never proactively downloads the whole
 * share, only what the slideshow has actually shown or preloaded. Coil loads/decodes from the
 * plain [File] this hands back; this class never touches bitmaps itself.
 */
class PhotoCacheManager(context: Context) {

    private val cacheDir: File = File(context.applicationContext.cacheDir, "photorama_photos").apply { mkdirs() }
    private val smbRepository = SmbRepository(context.applicationContext)
    // NAS SMB clients allocate connection buffers while opening a stream. The visible image and
    // background preload must not open separate connections together on the low-memory mirror.
    private val downloadLock = Any()

    private fun keyFor(photo: LanPhoto): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(photo.url.toByteArray(Charsets.UTF_8))
        val hash = digest.joinToString("") { "%02x".format(it) }
        val ext = photo.name.substringAfterLast('.', "jpg")
        return "$hash.$ext"
    }

    /** Returns the cached file if present, touching its last-modified time so it counts as
     * recently used for eviction purposes. Does not hit the network. */
    fun getCachedFile(photo: LanPhoto): File? {
        val file = File(cacheDir, keyFor(photo))
        if (!file.exists()) return null
        file.setLastModified(System.currentTimeMillis())
        return file
    }

    /** Downloads [photo] from [share] into the cache if not already present, then enforces
     * [maxSizeBytes]. Blocking - must be called off the UI thread. Returns null on failure. */
    fun ensureCached(photo: LanPhoto, share: SmbShare, maxSizeBytes: Long): File? = synchronized(downloadLock) {
        getCachedFile(photo)?.let { return@synchronized it }

        val destination = File(cacheDir, keyFor(photo))
        val temp = File(cacheDir, "${destination.name}.tmp")
        try {
            smbRepository.openStream(share, photo.url).use { input ->
                temp.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            temp.renameTo(destination)
            enforceLimit(maxSizeBytes)
            destination
        } catch (e: Exception) {
            Log.e("PhotoCacheManager", "failed to cache ${photo.name}: ${e.message}")
            temp.delete()
            null
        } catch (e: OutOfMemoryError) {
            // Keep the launcher alive if the camera/audio encoder and a NAS fetch briefly contend
            // for the VM heap. The slideshow retains its last image and will retry next cycle.
            Log.w("PhotoCacheManager", "skipped ${photo.name}; insufficient memory for NAS fetch")
            temp.delete()
            null
        }
    }

    fun currentCacheSizeBytes(): Long = cacheDir.listFiles()?.sumOf { it.length() } ?: 0L

    fun clearCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    private fun enforceLimit(maxSizeBytes: Long) {
        val files = cacheDir.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") } ?: return
        var totalSize = files.sumOf { it.length() }
        if (totalSize <= maxSizeBytes) return

        val oldestFirst = files.sortedBy { it.lastModified() }
        for (file in oldestFirst) {
            if (totalSize <= maxSizeBytes) break
            totalSize -= file.length()
            file.delete()
        }
    }
}
