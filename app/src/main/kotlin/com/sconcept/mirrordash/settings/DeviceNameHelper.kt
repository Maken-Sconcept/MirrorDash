package com.sconcept.mirrordash.settings

import android.content.Context
import android.os.Build
import android.provider.Settings

/** Resolves a sensible default device name when the user hasn't set one explicitly in Settings -
 * ported from BerthierOptions verbatim (device_name -> bluetooth_name -> model -> manufacturer
 * -> literal fallback). Shared by every feature that needs a human-readable name for this unit
 * (AirPlay, Walkie-Talkie, and anything added later) so they all fall back the same way. */
object DeviceNameHelper {
    fun defaultDeviceName(context: Context): String {
        val configured = listOf(
            runCatching { Settings.Global.getString(context.contentResolver, "device_name") }.getOrNull(),
            runCatching { Settings.Secure.getString(context.contentResolver, "bluetooth_name") }.getOrNull(),
        ).firstOrNull { !it.isNullOrBlank() }?.trim()

        if (!configured.isNullOrBlank()) {
            return configured
        }

        val model = Build.MODEL?.trim().orEmpty()
        if (model.isNotBlank()) {
            return model
        }

        val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
        if (manufacturer.isNotBlank()) {
            return manufacturer
        }

        return DEFAULT_DEVICE_NAME
    }
}
