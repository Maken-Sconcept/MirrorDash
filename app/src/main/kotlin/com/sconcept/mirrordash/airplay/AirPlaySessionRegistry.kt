package com.sconcept.mirrordash.airplay

import android.content.Context

/** Process-wide singleton so every UI surface (Settings, Clock widget) shares one
 * [AirPlayNsdBridge] and native code only ever has one callback target registered. */
object AirPlaySessionRegistry {
    @Volatile
    private var bridge: AirPlayNsdBridge? = null

    fun getBridge(context: Context): AirPlayNsdBridge {
        bridge?.let { return it }
        return synchronized(this) {
            bridge ?: AirPlayNsdBridge(context.applicationContext).also {
                AirPlayNative.nativeSetCallback(it)
                bridge = it
            }
        }
    }
}
