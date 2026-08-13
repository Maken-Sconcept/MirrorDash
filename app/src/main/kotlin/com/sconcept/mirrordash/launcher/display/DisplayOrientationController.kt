package com.sconcept.mirrordash.launcher.display

import android.app.Activity
import android.content.Context
import android.provider.Settings
import android.util.Log

private const val TAG = "DisplayOrientation"

/**
 * Applies a [DisplayOrientationMode] three ways, layered:
 * 1. `activity.requestedOrientation` - reliable for the two landscape modes on this hardware,
 *    no permission needed, so the launcher lands close to right even if the next two steps are
 *    skipped.
 * 2. [DisplayOrientationOverlay] - the mechanism actually load-bearing for portrait on this ROM,
 *    reverse-engineered from a third-party "Rotation Control" utility already on this device
 *    (see that file's doc comment). Requires `SYSTEM_ALERT_WINDOW`.
 * 3. `Settings.System.ACCELEROMETER_ROTATION`/`USER_ROTATION` - the direct system-setting write
 *    BerthierOptions uses on this same hardware family, and what the Rotation Control utility
 *    also writes alongside its overlay. Requires `WRITE_SETTINGS`.
 * Each layer is independently best-effort and silently skipped if its permission isn't granted,
 * so partially-configured units still get the closest achievable result instead of nothing.
 */
object DisplayOrientationController {

    fun hasWriteSettingsPermission(context: Context): Boolean = Settings.System.canWrite(context)

    fun hasOverlayPermission(context: Context): Boolean = DisplayOrientationOverlay.hasOverlayPermission(context)

    fun apply(activity: Activity, mode: DisplayOrientationMode) {
        activity.requestedOrientation = mode.activityOrientation()
        if (mode == DisplayOrientationMode.AUTO) {
            // FULL_SENSOR on the forcing overlay is a no-op steady-state on this gyro-less ROM -
            // it doesn't resolve a rotation, it just leaves whichever one was last forced stuck
            // (and desyncs the touch digitizer's own rotation transform from it). AUTO means
            // "stop forcing," so the overlay window is removed rather than re-applied.
            DisplayOrientationOverlay.remove()
        } else {
            DisplayOrientationOverlay.apply(activity, mode)
        }
        applySystemSetting(activity, mode)
    }

    private fun applySystemSetting(context: Context, mode: DisplayOrientationMode) {
        if (!hasWriteSettingsPermission(context)) return

        try {
            val resolver = context.contentResolver
            val userRotation = mode.userRotation()
            if (userRotation == null) {
                Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 1)
            } else {
                Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0)
                Settings.System.putInt(resolver, Settings.System.USER_ROTATION, userRotation)
            }
        } catch (e: Exception) {
            Log.w(TAG, "failed applying system rotation setting for mode=$mode", e)
        }
    }
}
