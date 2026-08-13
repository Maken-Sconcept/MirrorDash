package com.sconcept.mirrordash.airplay

import com.sconcept.mirrordash.settings.AIRPLAY_AUTH_MODE_OPEN
import com.sconcept.mirrordash.settings.AIRPLAY_AUTH_MODE_PASSWORD
import com.sconcept.mirrordash.settings.AIRPLAY_AUTH_MODE_RANDOM_PIN
import com.sconcept.mirrordash.settings.AIRPLAY_MIRROR_PRESET_1080P
import com.sconcept.mirrordash.settings.AIRPLAY_MIRROR_PRESET_4K
import com.sconcept.mirrordash.settings.AIRPLAY_MIRROR_PRESET_720P
import com.sconcept.mirrordash.settings.AIRPLAY_MIRROR_PRESET_DISPLAY

/** Ported from BerthierOptions' object of the same name, minus the `Context`-resolved string
 * resources - MirrorDash's Settings UI uses inline Compose strings throughout rather than
 * `strings.xml`, so labels live directly here. */
object AirPlayAuthMode {
    val ALL = listOf(AIRPLAY_AUTH_MODE_OPEN, AIRPLAY_AUTH_MODE_RANDOM_PIN, AIRPLAY_AUTH_MODE_PASSWORD)

    fun label(id: String): String = when (id) {
        AIRPLAY_AUTH_MODE_RANDOM_PIN -> "Random PIN"
        AIRPLAY_AUTH_MODE_PASSWORD -> "Password"
        else -> "Open (no pairing)"
    }
}

object AirPlayMirrorPreset {
    val ALL = listOf(
        AIRPLAY_MIRROR_PRESET_DISPLAY,
        AIRPLAY_MIRROR_PRESET_720P,
        AIRPLAY_MIRROR_PRESET_1080P,
        AIRPLAY_MIRROR_PRESET_4K,
    )

    fun label(id: String): String = when (id) {
        AIRPLAY_MIRROR_PRESET_720P -> "720p"
        AIRPLAY_MIRROR_PRESET_1080P -> "1080p"
        AIRPLAY_MIRROR_PRESET_4K -> "4K"
        else -> "Match this display"
    }

    /** Resolves a preset + this display's own size into concrete mirror dimensions, swapping
     * width/height for portrait displays so a landscape preset (e.g. 1920x1080) still comes out
     * tall when the mirror itself is mounted vertically. */
    fun resolveSize(preset: String, fallbackWidth: Int, fallbackHeight: Int): Pair<Int, Int> {
        val width = fallbackWidth.takeIf { it > 0 } ?: 1280
        val height = fallbackHeight.takeIf { it > 0 } ?: 720

        val target = when (preset) {
            AIRPLAY_MIRROR_PRESET_720P -> 1280 to 720
            AIRPLAY_MIRROR_PRESET_1080P -> 1920 to 1080
            AIRPLAY_MIRROR_PRESET_4K -> 3840 to 2160
            else -> return width to height
        }
        return if (width >= height) target else target.second to target.first
    }
}
