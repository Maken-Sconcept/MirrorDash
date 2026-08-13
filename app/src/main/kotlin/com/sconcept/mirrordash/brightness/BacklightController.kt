package com.sconcept.mirrordash.brightness

import android.app.Activity
import android.content.Context
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "BacklightController"

/**
 * Three independent, best-effort layers - see the plan's architecture note on why this is
 * simpler than BerthierOptions' equivalent (`DisplayRotationService`/its own `BacklightController`):
 * MirrorDash is the Home app and owns the whole screen almost always, so it doesn't need a
 * `SYSTEM_ALERT_WINDOW` system overlay to dim *other* apps the way BerthierOptions does.
 *
 * 1. [applyWindowBrightness] - MirrorDash's own window brightness override. No permission, always
 *    works, real hardware dimming.
 * 2. The in-app dim overlay ([BrightnessDimOverlay]) - not this file, pure Compose UI.
 * 3. [applyRootLayer] - sysfs backlight + framebuffer-blank writes (for true backlight-off, since
 *    layer 1's floor is above true zero on many panels) and `Settings.System.SCREEN_BRIGHTNESS`,
 *    via root or `WRITE_SETTINGS` if either is available, silently skipped otherwise - same
 *    graceful-degradation shape as [com.sconcept.mirrordash.launcher.display.DisplayOrientationController].
 */
object BacklightController {

    private val knownSysfsCandidates = listOf(
        "/sys/class/leds/lcd-backlight/brightness",
        "/sys/class/backlight/backlight/brightness",
        "/sys/class/backlight/panel0-backlight/brightness",
    )

    private const val FB_BLANK_PATH = "/sys/class/graphics/fb0/blank"
    private const val FB_BLANK_UNBLANK = "0"
    private const val FB_BLANK_POWERDOWN = "4"

    fun hasWriteSettingsPermission(context: Context): Boolean = Settings.System.canWrite(context)

    fun applyWindowBrightness(activity: Activity, level255: Int) {
        val fraction = (level255.coerceIn(0, 255) / 255f).coerceIn(0f, 1f)
        val attrs = activity.window.attributes
        attrs.screenBrightness = fraction
        activity.window.attributes = attrs
    }

    /** Blocking (shells out / touches files) - always call from [Dispatchers.IO]. */
    suspend fun applyRootLayer(context: Context, level255: Int) = withContext(Dispatchers.IO) {
        val clamped = level255.coerceIn(0, 255)
        writeSysfsBrightness(clamped)
        writeFramebufferBlank(shouldBlank = clamped == 0)
        writeSystemBrightnessSetting(context, clamped)
    }

    private fun writeSysfsBrightness(value255: Int) {
        val target = knownSysfsCandidates.firstOrNull { File(it).exists() } ?: findBacklightNodeByScan() ?: return
        if (!writePlain(target, value255.toString())) {
            RootShell.run("printf '%s' '$value255' > '$target'")
        }
    }

    private fun writeFramebufferBlank(shouldBlank: Boolean) {
        if (!File(FB_BLANK_PATH).exists()) return
        val value = if (shouldBlank) FB_BLANK_POWERDOWN else FB_BLANK_UNBLANK
        if (!writePlain(FB_BLANK_PATH, value)) {
            RootShell.run("printf '%s' '$value' > '$FB_BLANK_PATH'")
        }
    }

    private fun writeSystemBrightnessSetting(context: Context, value255: Int) {
        if (hasWriteSettingsPermission(context)) {
            try {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                )
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value255)
                return
            } catch (e: Exception) {
                Log.w(TAG, "failed writing Settings.System.SCREEN_BRIGHTNESS", e)
            }
        }
        RootShell.run("settings put system screen_brightness $value255")
    }

    private fun writePlain(path: String, value: String): Boolean = try {
        File(path).writeText(value)
        true
    } catch (e: Exception) {
        false
    }

    /** Sysfs directory listings are world-readable even when the `brightness` leaf itself isn't
     * writable without root, so this scan never needs elevation - only the write does. */
    private fun findBacklightNodeByScan(): String? {
        for (root in listOf(File("/sys/class/leds"), File("/sys/class/backlight"))) {
            val dirs = root.listFiles() ?: continue
            for (dir in dirs) {
                if (!dir.isDirectory) continue
                val name = dir.name.lowercase()
                if ("backlight" in name || "lcd" in name || "panel" in name || "display" in name) {
                    val brightnessFile = File(dir, "brightness")
                    if (brightnessFile.exists()) return brightnessFile.absolutePath
                }
            }
        }
        return null
    }
}
