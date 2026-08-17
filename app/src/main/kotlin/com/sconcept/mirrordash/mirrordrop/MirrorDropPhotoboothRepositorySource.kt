package com.sconcept.mirrordash.mirrordrop

import com.sconcept.mirrordash.photobooth.PhotoboothImage
import com.sconcept.mirrordash.photobooth.PhotoboothRepository
import java.security.MessageDigest

/**
 * Adapts [PhotoboothRepository] to what [MirrorDropShareModeResolver] needs (brief §40 - the only
 * place in MirrorDrop that knows Photobooth's storage exists at all; everything else only ever
 * sees the [MirrorDropPhotoboothSource] interface).
 */
class MirrorDropPhotoboothRepositorySource(private val repository: PhotoboothRepository) : MirrorDropPhotoboothSource {

    override fun latestPhoto(): MirrorDropFileHandle? = repository.getLatestPhoto()?.toFileHandle()
    override fun latestMontage(): MirrorDropFileHandle? = repository.getLatestMontage()?.toFileHandle()

    override fun currentSessionImages(): List<MirrorDropFileHandle> {
        val session = repository.getLatestSession() ?: return emptyList()
        return repository.getImagesForSession(session.id).map { it.toFileHandle() }
    }

    override fun images(imageIds: List<String>): List<MirrorDropFileHandle> =
        imageIds.mapNotNull { repository.getImage(it)?.toFileHandle() }

    override fun sessionImages(sessionId: String): List<MirrorDropFileHandle> =
        repository.getImagesForSession(sessionId).map { it.toFileHandle() }

    override fun allImages(): List<MirrorDropFileHandle> =
        repository.getAllPhotoboothImages().map { it.toFileHandle() }

    private fun PhotoboothImage.toFileHandle(): MirrorDropFileHandle {
        val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return MirrorDropFileHandle(id = id, name = name, mimeType = "image/jpeg", bytes = bytes, sha256Hex = sha256)
    }
}
