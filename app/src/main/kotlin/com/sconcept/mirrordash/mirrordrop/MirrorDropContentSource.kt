package com.sconcept.mirrordash.mirrordrop

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.sconcept.mirrordash.mirrordrop.protocol.ManifestFileEntry
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

data class MirrorDropFileHandle(
    val id: String,
    val name: String,
    val mimeType: String,
    val bytes: ByteArray,
    val sha256Hex: String,
)

/**
 * Resolves the opaque file IDs the browser sends in `requestFiles` (brief §14/§36 - never a
 * filesystem path crosses this boundary). [MirrorDropTransferManager] only ever talks to this
 * interface, so swapping the backing content (this dev-test source today, a
 * PhotoboothRepository-backed one from Phase 7/9 onward) needs no changes on the wire-protocol or
 * transfer-framing side.
 */
interface MirrorDropContentSource {
    fun listFiles(): List<ManifestFileEntry>
    fun openFile(fileId: String): MirrorDropFileHandle?
}

/** A fixed snapshot of files, resolved once. [MirrorDropShareModeResolver] produces the handles;
 * this just adapts a plain list to the [MirrorDropContentSource] interface [MirrorDropTransferManager]
 * expects, so ShareMode-specific logic never leaks past this one call site. */
class MirrorDropStaticContentSource(handles: List<MirrorDropFileHandle>) : MirrorDropContentSource {
    private val byId = handles.associateBy { it.id }
    override fun listFiles(): List<ManifestFileEntry> =
        byId.values.map { ManifestFileEntry(id = it.id, name = it.name, mimeType = it.mimeType, size = it.bytes.size.toLong()) }
    override fun openFile(fileId: String): MirrorDropFileHandle? = byId[fileId]
}

/** Re-resolves [mode] against [source] on every call rather than snapshotting once (brief §25 -
 * "auto-share the newest montage": if a Latest Montage/Photo/Current Session/Entire Library share
 * is active when a new Photobooth session finishes, the very next manifest a browser asks for -
 * whether from a fresh page load or the push in [MirrorDropEngine.notifyPhotoboothContentChanged] -
 * reflects it automatically, with no need to stop and restart sharing. Selected Images/Session
 * modes resolve against fixed ids either way, so "live" vs. "snapshot" makes no difference to them. */
class MirrorDropLiveContentSource(
    private val mode: ShareMode,
    private val source: MirrorDropPhotoboothSource,
) : MirrorDropContentSource {
    override fun listFiles(): List<ManifestFileEntry> =
        MirrorDropShareModeResolver.resolve(mode, source)
            .map { ManifestFileEntry(id = it.id, name = it.name, mimeType = it.mimeType, size = it.bytes.size.toLong()) }

    override fun openFile(fileId: String): MirrorDropFileHandle? =
        MirrorDropShareModeResolver.resolve(mode, source).firstOrNull { it.id == fileId }
}

/**
 * Phase 5's stand-in content source (brief §52 Phase 5 dev-test scope): PhotoboothRepository
 * doesn't exist until Phase 8, so this generates one small in-memory JPEG once and serves it under
 * a fixed ID, just to exercise the real chunked-transfer + SHA-256 verification path end to end.
 */
class MirrorDropDevTestContentSource : MirrorDropContentSource {

    private val handle: MirrorDropFileHandle by lazy { buildTestImage() }

    override fun listFiles(): List<ManifestFileEntry> = listOf(
        ManifestFileEntry(id = handle.id, name = handle.name, mimeType = handle.mimeType, size = handle.bytes.size.toLong()),
    )

    override fun openFile(fileId: String): MirrorDropFileHandle? = handle.takeIf { it.id == fileId }

    private fun buildTestImage(): MirrorDropFileHandle {
        // Large + noisy enough that JPEG compression can't shrink it below a handful of
        // MIRRORDROP_CHUNK_SIZE_BYTES chunks - a single-chunk transfer wouldn't exercise the
        // multi-chunk reassembly/backpressure path at all (brief §22).
        val size = 1600
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#0b0c0e"))
        val random = java.util.Random(42)
        val noisePaint = Paint()
        repeat(4000) {
            noisePaint.color = Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
            val x = random.nextInt(size).toFloat()
            val y = random.nextInt(size).toFloat()
            canvas.drawCircle(x, y, 6f + random.nextInt(20), noisePaint)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#e8a659")
            textAlign = Paint.Align.CENTER
        }
        paint.textSize = 96f
        canvas.drawText("MirrorDrop", size / 2f, size / 2f - 40f, paint)
        paint.textSize = 56f
        canvas.drawText("test transfer", size / 2f, size / 2f + 60f, paint)
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
        bitmap.recycle()
        val bytes = output.toByteArray()
        val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return MirrorDropFileHandle(
            id = "devtest-image",
            name = "mirrordrop-test.jpg",
            mimeType = "image/jpeg",
            bytes = bytes,
            sha256Hex = sha256,
        )
    }
}
