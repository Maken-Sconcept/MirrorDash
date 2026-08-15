package com.sconcept.mirrordash.walkietalkie

import android.media.ToneGenerator

const val WALKIE_TALKIE_CHIME_SOFT_PING = "soft_ping"
const val WALKIE_TALKIE_CHIME_RADIO_BLIP = "radio_blip"
const val WALKIE_TALKIE_CHIME_SHARP_CHIRP = "sharp_chirp"
const val DEFAULT_WALKIE_TALKIE_CHIME = WALKIE_TALKIE_CHIME_RADIO_BLIP

data class WalkieTalkieChimeSpec(
    val key: String,
    val label: String,
    val description: String,
    val tone: Int,
    val volume: Int,
    val durationMs: Int,
)

object WalkieTalkieChimes {
    val options = listOf(
        WalkieTalkieChimeSpec(
            key = WALKIE_TALKIE_CHIME_SOFT_PING,
            label = "Soft ping",
            description = "Short and light",
            tone = ToneGenerator.TONE_PROP_ACK,
            volume = 28,
            durationMs = 52,
        ),
        WalkieTalkieChimeSpec(
            key = WALKIE_TALKIE_CHIME_RADIO_BLIP,
            label = "Radio blip",
            description = "Crisp comms-style cue",
            tone = ToneGenerator.TONE_PROP_BEEP2,
            volume = 34,
            durationMs = 62,
        ),
        WalkieTalkieChimeSpec(
            key = WALKIE_TALKIE_CHIME_SHARP_CHIRP,
            label = "Sharp chirp",
            description = "A slightly brighter alert",
            tone = ToneGenerator.TONE_PROP_PROMPT,
            volume = 38,
            durationMs = 68,
        ),
    )

    fun specFor(key: String): WalkieTalkieChimeSpec =
        options.firstOrNull { it.key == key } ?: options.first { it.key == DEFAULT_WALKIE_TALKIE_CHIME }
}
