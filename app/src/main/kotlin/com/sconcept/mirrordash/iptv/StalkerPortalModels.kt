package com.sconcept.mirrordash.iptv

/** One live channel from `get_all_channels`/`get_ordered_list` - only the fields the player and
 * channel list actually use, out of the several dozen a real portal response includes. */
data class StalkerChannel(
    val id: String,
    val number: String,
    val name: String,
    val logoUrl: String?,
    val cmd: String,
    /** The channel's `tv_genre_id`, matching [StalkerGenre.id] - used to filter the channel
     * list/Guide by category (Plex's row of "Featured / Bingeworthy / Movies / ..." tabs is the
     * same idea, sourced the same way most portal software does it: one genre list, every
     * channel tagged with one genre id). */
    val genreId: String,
)

/** One category from `get_genres` - the portal's own grouping, not something MirrorDash invents.
 * The portal always includes an "All" entry with [ALL_GENRES_ID] as its id. */
data class StalkerGenre(val id: String, val title: String)

const val ALL_GENRES_ID = "*"

/** One schedule entry from `get_short_epg` - a channel's next few programs, in order. Timestamps
 * are epoch seconds (portal-supplied, timezone-independent), not the accompanying `t_time`
 * strings - those are pre-formatted in the portal's own timezone and not worth parsing. */
data class EpgProgram(
    val id: String,
    val title: String,
    val description: String,
    val startEpochSeconds: Long,
    val endEpochSeconds: Long,
)

sealed class StalkerPortalError(message: String) : Exception(message) {
    class Network(message: String) : StalkerPortalError(message)
    class InvalidResponse(message: String) : StalkerPortalError(message)
    class PortalRejected(message: String) : StalkerPortalError(message)
}
