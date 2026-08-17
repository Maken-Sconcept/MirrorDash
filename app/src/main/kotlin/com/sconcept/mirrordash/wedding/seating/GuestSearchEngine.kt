package com.sconcept.mirrordash.wedding.seating

import java.text.Normalizer
import java.util.Locale

object GuestSearchEngine {
    const val MIN_QUERY_LENGTH = 2
    const val DEFAULT_RESULT_LIMIT = 16

    fun search(
        query: String,
        data: WeddingSeatingData,
        limit: Int = DEFAULT_RESULT_LIMIT,
    ): WeddingSearchResults {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.length < MIN_QUERY_LENGTH || limit <= 0) return WeddingSearchResults()

        val queryTokens = normalizedQuery.split(' ').filter(String::isNotEmpty)
        val tablesById = data.tables.associateBy(WeddingTable::id)
        val scored = data.guests.asSequence()
            .filter(WeddingGuest::isActive)
            .mapNotNull { guest ->
                val table = tablesById[guest.tableId] ?: return@mapNotNull null
                val normalizedName = normalize(guest.displayName)
                if (queryTokens.any { token -> token !in normalizedName }) return@mapNotNull null
                ScoredMatch(
                    match = WeddingGuestMatch(guest, table),
                    score = score(normalizedName, normalizedQuery, queryTokens),
                    normalizedName = normalizedName,
                )
            }
            .sortedWith(compareBy(ScoredMatch::score, ScoredMatch::normalizedName))
            .toList()

        return WeddingSearchResults(
            matches = scored.take(limit).map(ScoredMatch::match),
            totalCount = scored.size,
        )
    }

    fun normalize(value: String): String {
        val withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .lowercase(Locale.ROOT)
        return buildString(withoutAccents.length) {
            var previousWasSpace = true
            withoutAccents.forEach { character ->
                if (character.isLetterOrDigit()) {
                    append(character)
                    previousWasSpace = false
                } else if (!previousWasSpace) {
                    append(' ')
                    previousWasSpace = true
                }
            }
        }.trim()
    }

    private fun score(name: String, query: String, queryTokens: List<String>): Int {
        if (name == query) return 0
        if (name.startsWith(query)) return 10
        if (query in name) return 20

        val nameTokens = name.split(' ')
        val startsAtTokenBoundary = queryTokens.count { queryToken ->
            nameTokens.any { nameToken -> nameToken.startsWith(queryToken) }
        }
        return 40 - startsAtTokenBoundary.coerceAtMost(queryTokens.size) * 3
    }

    private data class ScoredMatch(
        val match: WeddingGuestMatch,
        val score: Int,
        val normalizedName: String,
    )

    private val COMBINING_MARKS = "\\p{M}+".toRegex()
}
