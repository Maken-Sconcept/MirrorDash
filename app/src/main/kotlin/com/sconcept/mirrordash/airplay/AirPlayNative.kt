package com.sconcept.mirrordash.airplay

/**
 * Thin JNI surface over the vendored UxPlay/OpenSSL/libplist native stack (see
 * app/src/main/cpp - ported near-verbatim from BerthierOptions, whose protocol/crypto layer is
 * unmodified vendored UxPlay C; only the Bonjour backend and this JNI glue are app-specific).
 * The native side owns the RAOP/FairPlay receiver, while Kotlin provides Android-specific pieces
 * such as NSD advertisement and MediaCodec rendering.
 */
object AirPlayNative {

    const val CODEC_H264 = 1
    const val CODEC_H265 = 2
    const val AUTH_OPEN = 0
    const val AUTH_RANDOM_PIN = 1
    const val AUTH_PASSWORD = 2

    init {
        System.loadLibrary("airplay_jni")
    }

    /** Registers the Kotlin bridge used for NSD advertisement + media events. */
    external fun nativeSetCallback(callback: AirPlayNsdBridge)

    /** Returns 0 on success, or a negative/native error code on failure. */
    external fun nativeStartReceiver(
        name: String,
        hwAddr: ByteArray,
        displayWidth: Int,
        displayHeight: Int,
        authMode: Int,
        password: String,
        allowHevc: Boolean,
    ): Int

    external fun nativeStopReceiver()

    external fun nativeIsRunning(): Boolean
}
