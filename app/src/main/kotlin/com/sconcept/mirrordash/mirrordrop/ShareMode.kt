package com.sconcept.mirrordash.mirrordrop

/**
 * What a share session exposes (brief §13/§37) - chosen once, when sharing starts, and fixed for
 * the lifetime of that [ShareSession]. Each mode restricts the manifest to exactly the files it
 * resolves to (see [MirrorDropShareModeResolver]) - e.g. Latest Photo mode never leaks any other
 * session's existence, Selected Session mode never leaks images outside that session.
 */
sealed class ShareMode {
    data object LatestPhoto : ShareMode()
    data object LatestMontage : ShareMode()
    data object CurrentSession : ShareMode()
    data class SelectedImages(val imageIds: List<String>) : ShareMode()
    data class SelectedSession(val sessionId: String) : ShareMode()
    data object EntireLibrary : ShareMode()

    /** Shown next to the QR code and sent as the manifest's `sessionLabel` (brief §43). */
    val label: String
        get() = when (this) {
            LatestPhoto -> "Latest Photo"
            LatestMontage -> "Latest Montage"
            CurrentSession -> "Current Session"
            is SelectedImages -> "Selected Photos"
            is SelectedSession -> "Selected Session"
            EntireLibrary -> "Entire Library"
        }
}

/**
 * The Photobooth-side query surface [MirrorDropShareModeResolver] needs (brief §40 - MirrorDrop
 * stays decoupled from Photobooth's storage details). [MirrorDropPhotoboothRepositorySource]
 * implements this against the real `PhotoboothRepository`.
 */
interface MirrorDropPhotoboothSource {
    fun latestPhoto(): MirrorDropFileHandle?
    fun latestMontage(): MirrorDropFileHandle?
    fun currentSessionImages(): List<MirrorDropFileHandle>
    fun images(imageIds: List<String>): List<MirrorDropFileHandle>
    fun sessionImages(sessionId: String): List<MirrorDropFileHandle>
    fun allImages(): List<MirrorDropFileHandle>
}

/**
 * Resolves a [ShareMode] against a [MirrorDropPhotoboothSource] into the exact file set a share
 * session's manifest will expose - the single place brief §13/§37's privacy rules are enforced,
 * so [MirrorDropTransferManager] and the wire protocol never need mode-specific logic of their own.
 */
object MirrorDropShareModeResolver {
    fun resolve(mode: ShareMode, source: MirrorDropPhotoboothSource): List<MirrorDropFileHandle> = when (mode) {
        ShareMode.LatestPhoto -> listOfNotNull(source.latestPhoto())
        ShareMode.LatestMontage -> listOfNotNull(source.latestMontage())
        ShareMode.CurrentSession -> source.currentSessionImages()
        is ShareMode.SelectedImages -> source.images(mode.imageIds)
        is ShareMode.SelectedSession -> source.sessionImages(mode.sessionId)
        ShareMode.EntireLibrary -> source.allImages()
    }
}
