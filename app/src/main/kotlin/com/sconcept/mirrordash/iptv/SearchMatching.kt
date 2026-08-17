package com.sconcept.mirrordash.iptv

import java.text.Normalizer

/** How a search box narrows its list - shared across Live TV's channel list and Movies/Series
 * (brief: "allow the user to select between search filtering or deep search"). [FILTER] only
 * narrows whatever's already loaded/shown (fast, no network); [DEEP] asks for everything matching
 * the query regardless of what's currently loaded or which category/genre is selected - for Live
 * TV that's still just an in-memory search (every channel is already fetched up front, see
 * [StalkerPortalClient.fetchChannels]), for Movies/Series it's a real portal round-trip (see
 * [StalkerPortalClient.searchVod]/`searchSeries`) since only a chunk of the catalog is ever loaded
 * locally (see [VodPage]). */
enum class IptvSearchMode { FILTER, DEEP }

/** Diacritic-insensitive - decomposes accented characters (e.g. "é" → "e" + a combining acute
 * accent) and strips the combining marks, so "café"/"cafe" both normalize the same way. Combined
 * with lowercasing, this is what makes [matchesSearch] "no matter accent, capitals" true for
 * anything this app matches locally - a portal's own [IptvSearchMode.DEEP] search results aren't
 * covered by this, since that matching happens server-side, outside this app's control. */
private fun normalizeForSearch(text: String): String {
    val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
    return decomposed.replace(Regex("\\p{M}+"), "").lowercase()
}

/** "Smart" substring match (brief: "if any part of the file name contains the text") - every
 * whitespace-separated word in [query] has to appear *somewhere* in [name], independent of order
 * or adjacency, so e.g. "man spider" still matches "Spider-Man 2" the same way a plain "spider"
 * would. Accent- and case-insensitive (see [normalizeForSearch]); a blank query matches
 * everything. */
fun matchesSearch(name: String, query: String): Boolean {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return true
    val normalizedName = normalizeForSearch(name)
    return normalizeForSearch(trimmed).split(Regex("\\s+")).all { token -> normalizedName.contains(token) }
}
