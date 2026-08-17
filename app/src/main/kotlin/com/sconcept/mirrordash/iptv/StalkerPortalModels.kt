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
    /** The portal's own `censored` flag - confirmed live against edge.bz that this is purely
     * advisory: `create_link` resolves a stream for a censored channel with no PIN check at all,
     * server-side. Real STB firmware enforces it locally before ever calling `create_link`; so
     * does MirrorDash's own parental-control gate (see [ParentalControlMode]). */
    val censored: Boolean = false,
)

/** One category from `get_genres` - the portal's own grouping, not something MirrorDash invents.
 * The portal always includes an "All" entry with [ALL_GENRES_ID] as its id. */
data class StalkerGenre(val id: String, val title: String, val censored: Boolean = false)

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
}

/** Subscriber/account details from `type=account_info&action=get_main_info` - see
 * [StalkerPortalClient.fetchAccountInfo]. Every field but [mac] is nullable since which ones a
 * given provider's portal actually returns varies. */
data class StalkerAccountInfo(
    val mac: String,
    val login: String? = null,
    val fullName: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val tariffPlan: String? = null,
    /** Subscription/tariff expiry, epoch seconds - null if the portal didn't return one (or it
     * came back as the "never expires" sentinel some forks use, `0000-00-00 00:00:00`). */
    val expiryEpochSeconds: Long? = null,
    /** Mirrors [StalkerPortalClient.blockMessage] at fetch time - carried here too so a caller
     * that only has a [StalkerAccountInfo] (e.g. the Settings account card) doesn't need the
     * client itself just to know whether the account is blocked. */
    val blocked: Boolean = false,
    val blockMessage: String? = null,
)

/** Shared by the blocked-account screen ([IptvScreen]) and the Settings account card
 * ([com.sconcept.mirrordash.settings.ui.IptvSettingsContent]) so an expiry date reads the same
 * in both places. */
fun formatAccountExpiry(epochSeconds: Long): String =
    java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()).format(java.util.Date(epochSeconds * 1000))
