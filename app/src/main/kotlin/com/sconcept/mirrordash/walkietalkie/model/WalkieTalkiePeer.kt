package com.sconcept.mirrordash.walkietalkie.model

import kotlinx.serialization.Serializable

/**
 * One configured walkie-talkie destination. "All" (broadcast-to-everyone) isn't a peer - it's
 * a separate target mode alongside a specific peer's [ip]. Structurally simpler than
 * BerthierOptions' version: this drops `showOnStage`/`stageIcon`, which only existed to
 * configure Berthier's PhotoClockView-embedded PTT buttons (out of scope here), and is
 * serialized with kotlinx.serialization instead of a hand-rolled `|`/`;`-delimited string.
 */
@Serializable
data class WalkieTalkiePeer(
    val name: String,
    val ip: String,
) {
    /** Short initials shown as the peer's avatar badge in lists. */
    fun initials(): String {
        val words = name.split(" ", "-", "_").filter { it.isNotBlank() }
        return when {
            words.size >= 2 -> "${words[0].first()}${words[1].first()}".uppercase()
            name.isNotBlank() -> name.trim().take(2).uppercase()
            else -> "?"
        }
    }
}

@Serializable
data class WalkieTalkieDiscoveredPeer(
    val serviceName: String,
    val name: String,
    val ip: String,
    val port: Int,
)
