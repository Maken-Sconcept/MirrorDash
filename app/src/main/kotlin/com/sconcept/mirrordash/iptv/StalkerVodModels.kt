package com.sconcept.mirrordash.iptv

enum class VodContentType { MOVIES, SERIES }

/** One category from `type=vod`/`type=series` `get_categories` - same shape as [StalkerGenre]
 * but kept as its own type since VOD/series categories carry none of live TV's genre-specific
 * fields (censored flag, tv_genre_id matching), and mixing them would make every future itv-only
 * change to [StalkerGenre] a question of "does this also apply to VOD categories". */
data class StalkerVodCategory(val id: String, val title: String)

/** One movie or series entry from `get_ordered_list`. Series ships as a flat list for now - each
 * entry plays/downloads through the same `cmd`/`create_link` path as a movie, with no season or
 * episode drill-down (see [StalkerPortalClient.fetchSeriesItems]'s doc comment for why). */
data class StalkerVodItem(
    val id: String,
    val name: String,
    val cmd: String,
    val categoryId: String,
    val logoUrl: String?,
    val contentType: VodContentType,
    /** The portal's own synopsis, when it sends one - unverified field name (`description`), same
     * best-effort caveat as the rest of this file: absent/blank just means the list row shows no
     * synopsis rather than the fetch failing. */
    val description: String? = null,
    /** IMDb rating as the portal reports it (`rating_imdb`) - a raw string ("7.4"), not parsed to
     * a number, since it's only ever displayed, never compared/sorted. */
    val ratingImdb: String? = null,
    /** Rotten Tomatoes rating, if this provider's portal happens to send one - unlike
     * `rating_imdb`, this isn't a field the standard Ministra/Stalker protocol is known to
     * include, so this is speculative (tries a couple of plausible field names) and null far more
     * often than [ratingImdb]. */
    val ratingTomatoes: String? = null,
)

/** Result of a background reachability probe for one [StalkerVodItem] - see
 * [StreamHealthChecker]. [UNKNOWN] is the initial/uncached state a card starts in before its
 * check has even been requested; [CHECKING] once requested but not yet resolved. */
enum class StreamHealth { UNKNOWN, CHECKING, ONLINE, OFFLINE }

/** One batch from [StalkerPortalClient.fetchVodItems]/[fetchSeriesItems] - a category's catalog
 * is fetched in bounded chunks rather than all at once (some providers' Movies/Series categories
 * run into the thousands), so every fetch reports what to ask for next. [nextPage] is the portal
 * page to resume from on the next chunk; [hasMore] is false once a chunk's own request came back
 * with zero items, the only end-of-catalog signal this uses (see that function's doc comment for
 * why total_items isn't relied on here the way [StalkerPortalClient.fetchCensoredGenreChannels]
 * uses it). [totalItems] is purely informational (the category's own item count, straight off the
 * portal's `total_items` field) - shown in the Movies/Series list header so the user can see how
 * many are loaded out of how many exist, not used to decide [hasMore]. 0 means the portal didn't
 * report one. */
data class VodPage(val items: List<StalkerVodItem>, val nextPage: Int, val hasMore: Boolean, val totalItems: Int = 0)

/** Card grid vs. compact list, for browsing Movies/Series - remembered per content type (see
 * [com.sconcept.mirrordash.settings.MirrorDashSettings.iptvMoviesViewMode]/`iptvSeriesViewMode`),
 * not per-session UI state, so switching back to a tab later keeps whichever the user left it on. */
enum class VodViewMode(val storageKey: String) {
    GRID("grid"),
    LIST("list"),
    ;

    companion object {
        fun fromStorageKey(key: String): VodViewMode = entries.firstOrNull { it.storageKey == key } ?: GRID
    }
}
