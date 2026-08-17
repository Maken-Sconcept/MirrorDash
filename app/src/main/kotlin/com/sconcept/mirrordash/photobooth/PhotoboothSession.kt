package com.sconcept.mirrordash.photobooth

import kotlinx.serialization.Serializable

@Serializable
data class PhotoboothSession(
    val id: String,
    val createdAtMs: Long,
    val photoFileNames: List<String>,
    val montageFileName: String,
)

/** One image the repository can hand back, resolved to real bytes on disk - deliberately the same
 * shape MirrorDrop's [com.sconcept.mirrordash.mirrordrop.MirrorDropFileHandle] wants, so wiring
 * Photobooth into a MirrorDrop [com.sconcept.mirrordash.mirrordrop.MirrorDropPhotoboothSource]
 * (Phase 9) is a direct mapping with no extra translation layer. */
data class PhotoboothImage(
    val id: String,
    val sessionId: String,
    val name: String,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is PhotoboothImage && id == other.id && sessionId == other.sessionId && name == other.name && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = id.hashCode() * 31 + sessionId.hashCode()
}
