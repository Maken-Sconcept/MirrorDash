package com.sconcept.mirrordash.launcher.display

import android.content.pm.ActivityInfo
import android.view.Surface

/**
 * MirrorDash's take on BerthierOptions' `DisplayRotationMode`, covering both landscape and
 * portrait mounts - this hardware has no gyroscope (confirmed via `dumpsys sensorservice`:
 * accelerometer + barometer only) and its ROM reports `mSupportAutoRotation=false` in
 * `dumpsys window`, a framework-level capability flag no app can override. [AUTO] is offered
 * anyway (mirroring what Berthier ships) since it costs nothing to request and works if a given
 * unit's ROM build differs, but the four fixed modes are the reliable path, verified in this
 * session: fixed orientation requests work on this hardware regardless of the auto-rotation
 * flag, because resolving a *fixed* requested orientation is a different framework code path
 * than following the sensor. The UI/gesture layer doesn't distinguish landscape from portrait -
 * paging stays horizontal and the drawer/notification edges stay bottom/top in every mode,
 * matching every other Android device's own convention regardless of rotation; only the page
 * content's own aspect ratio changes.
 */
enum class DisplayOrientationMode {
    AUTO,
    LANDSCAPE,
    REVERSE_LANDSCAPE,
    PORTRAIT,
    REVERSE_PORTRAIT,
    ;

    companion object {
        fun fromStorageKey(key: String): DisplayOrientationMode = entries.firstOrNull { it.name == key } ?: AUTO
    }
}

fun DisplayOrientationMode.storageKey(): String = name

/** What to request via `Activity.requestedOrientation` - proven reliable on this hardware for
 * the fixed modes; best-effort for AUTO. */
fun DisplayOrientationMode.activityOrientation(): Int = when (this) {
    DisplayOrientationMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
    DisplayOrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    DisplayOrientationMode.REVERSE_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
    DisplayOrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    DisplayOrientationMode.REVERSE_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
}

/** Matching `Settings.System.USER_ROTATION` value for the fixed modes - null for AUTO, where
 * `ACCELEROMETER_ROTATION` is toggled on instead (see DisplayOrientationController). */
fun DisplayOrientationMode.userRotation(): Int? = when (this) {
    DisplayOrientationMode.AUTO -> null
    DisplayOrientationMode.LANDSCAPE -> Surface.ROTATION_90
    DisplayOrientationMode.REVERSE_LANDSCAPE -> Surface.ROTATION_270
    DisplayOrientationMode.PORTRAIT -> Surface.ROTATION_0
    DisplayOrientationMode.REVERSE_PORTRAIT -> Surface.ROTATION_180
}
