package com.sconcept.mirrordash.brightness

import android.content.Context
import android.widget.Toast
import com.sconcept.mirrordash.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** How long a failsafe input (volume-up, or a touch held anywhere on screen) must be held before
 * brightness resets to full - shared by both trigger paths. Matches BerthierOptions' proven
 * timing: an 8s hold to fire, with a warning surfaced at the 5s mark (3s before it fires) so a
 * deliberate hold doesn't come as a total surprise. */
const val BRIGHTNESS_FAILSAFE_HOLD_MS = 8000L
const val BRIGHTNESS_FAILSAFE_WARNING_LEAD_MS = 3000L

/** The shared reset action both failsafe triggers ([MirrorDashActivity]'s volume-key watcher and
 * [LauncherGestureHost]'s hold-anywhere watcher) call - forces brightness back to fully lit so a
 * unit that's been dimmed to near-invisible in a dark bedroom is always recoverable by touch or
 * volume button alone, without needing to see the screen at all. */
object BrightnessFailsafe {
    fun warn(context: Context) {
        Toast.makeText(context, "Keep holding to reset brightness…", Toast.LENGTH_SHORT).show()
    }

    fun trigger(context: Context, scope: CoroutineScope, settingsRepository: SettingsRepository) {
        scope.launch {
            settingsRepository.update {
                brightnessLevel255 = 255
                brightnessExtraDimPercent = 0
            }
        }
        Toast.makeText(context, "Brightness reset to full", Toast.LENGTH_LONG).show()
    }
}
