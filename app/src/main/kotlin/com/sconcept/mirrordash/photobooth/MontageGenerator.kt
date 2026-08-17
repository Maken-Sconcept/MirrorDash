package com.sconcept.mirrordash.photobooth

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import java.io.ByteArrayOutputStream

private const val STRIP_WIDTH = 800
private const val PHOTO_PADDING = 24
private const val MONTAGE_JPEG_QUALITY = 90

/**
 * Combines exactly 3 captured photos into one classic vertical photo-strip montage (brief §41 -
 * deliberately one simple layout for v1; frames/filters/other layouts are extension points for
 * later, not implemented now). Each photo is downscaled to [STRIP_WIDTH] first so the montage
 * stays a reasonable size for MirrorDrop to transfer.
 */
object MontageGenerator {

    fun generate(photoJpegs: List<ByteArray>): ByteArray {
        require(photoJpegs.size == 3) { "Montage generation needs exactly 3 photos" }
        val bitmaps = photoJpegs.map { decodeScaled(it, STRIP_WIDTH) }
        try {
            val totalHeight = PHOTO_PADDING * 4 + bitmaps.sumOf { it.height }
            val montage = Bitmap.createBitmap(STRIP_WIDTH, totalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(montage)
            canvas.drawColor(Color.parseColor("#16181c"))

            var y = PHOTO_PADDING
            bitmaps.forEach { bitmap ->
                val dest = RectF(
                    PHOTO_PADDING.toFloat(),
                    y.toFloat(),
                    (STRIP_WIDTH - PHOTO_PADDING).toFloat(),
                    (y + bitmap.height).toFloat(),
                )
                canvas.drawBitmap(bitmap, null, dest, null)
                y += bitmap.height + PHOTO_PADDING
            }

            val output = ByteArrayOutputStream()
            montage.compress(Bitmap.CompressFormat.JPEG, MONTAGE_JPEG_QUALITY, output)
            montage.recycle()
            return output.toByteArray()
        } finally {
            bitmaps.forEach { it.recycle() }
        }
    }

    private fun decodeScaled(jpeg: ByteArray, targetWidth: Int): Bitmap {
        val original = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        val destWidth = targetWidth - PHOTO_PADDING * 2
        val scale = destWidth.toFloat() / original.width
        val destHeight = (original.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(original, destWidth, destHeight, true)
        if (scaled !== original) original.recycle()
        return scaled
    }
}
