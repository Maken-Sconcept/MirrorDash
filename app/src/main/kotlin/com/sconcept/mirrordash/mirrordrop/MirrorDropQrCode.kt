package com.sconcept.mirrordash.mirrordrop

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/** QR *encoding* only (brief §6) - there's no scanning/decoding anywhere in MirrorDash, so ZXing's
 * `core` artifact alone covers it without pulling in a camera-based barcode-scanning dependency. */
object MirrorDropQrCode {
    fun generate(content: String, sizePx: Int = 512): Bitmap {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}

/** The URL a browser needs to open to reach an active share session (brief §6) - built once here
 * so Settings UI and any future Share screen (Phase 9) can't drift on the query param names. */
fun buildMirrorDropShareUrl(localIp: String, port: Int, token: String): String =
    "http://$localIp:$port/?token=$token"
