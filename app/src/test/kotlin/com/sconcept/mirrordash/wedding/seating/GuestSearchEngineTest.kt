package com.sconcept.mirrordash.wedding.seating

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuestSearchEngineTest {
    private val table = WeddingTable(id = "table", name = "Table 12")
    private val data = WeddingSeatingData(
        tables = listOf(table),
        guests = listOf(
            guest("1", "Marie Tremblay"),
            guest("2", "Marc Gagnon"),
            guest("3", "Jean-Marc Fortin"),
            guest("4", "André Leduc"),
            guest("5", "MARIANNE Roy"),
            guest("6", "Inactive Martin", active = false),
        ),
    )

    @Test
    fun partialSearch_matchesAnywhereInCompleteName() {
        val names = GuestSearchEngine.search("mar", data).matches.map { it.guest.displayName }

        assertEquals(setOf("Marc Gagnon", "Marie Tremblay", "MARIANNE Roy", "Jean-Marc Fortin"), names.toSet())
    }

    @Test
    fun search_isCaseAndAccentInsensitive() {
        assertEquals("André Leduc", GuestSearchEngine.search("ANDRE", data).matches.single().guest.displayName)
    }

    @Test
    fun multipleTokens_canBeEnteredInAnyOrder() {
        val result = GuestSearchEngine.search("fort mar", data)

        assertEquals("Jean-Marc Fortin", result.matches.single().guest.displayName)
    }

    @Test
    fun inactiveGuestsAreExcluded() {
        assertTrue(GuestSearchEngine.search("inactive", data).matches.isEmpty())
    }

    @Test
    fun shortQueriesDoNotExposeTheGuestList() {
        assertTrue(GuestSearchEngine.search("m", data).matches.isEmpty())
    }

    @Test
    fun resultLimitPreservesTotalCount() {
        val result = GuestSearchEngine.search("mar", data, limit = 2)

        assertEquals(2, result.matches.size)
        assertEquals(4, result.totalCount)
    }

    @Test
    fun guestsWithMissingTablesAreNotShown() {
        val brokenData = WeddingSeatingData(
            tables = emptyList(),
            guests = listOf(guest("missing", "Marie Missing")),
        )

        assertFalse(GuestSearchEngine.search("marie", brokenData).matches.any())
    }

    @Test
    fun duplicateNamesRemainSeparateResultsWithTheirOwnTables() {
        val rose = WeddingTable(id = "rose", name = "Rose")
        val paris = WeddingTable(id = "paris", name = "Paris")
        val duplicateData = WeddingSeatingData(
            tables = listOf(rose, paris),
            guests = listOf(
                WeddingGuest(id = "rose-marie", displayName = "Marie Tremblay", tableId = rose.id),
                WeddingGuest(id = "paris-marie", displayName = "Marie Tremblay", tableId = paris.id),
            ),
        )

        val matches = GuestSearchEngine.search("marie", duplicateData).matches

        assertEquals(2, matches.size)
        assertEquals(setOf("Rose", "Paris"), matches.map { it.table.name }.toSet())
    }

    @Test
    fun searchHandlesThousandsOfGuests() {
        val largeData = WeddingSeatingData(
            tables = listOf(table),
            guests = (1..2_000).map { index -> guest(index.toString(), "Guest $index") },
        )

        val result = GuestSearchEngine.search("guest 1999", largeData)

        assertEquals("Guest 1999", result.matches.single().guest.displayName)
    }

    private fun guest(id: String, name: String, active: Boolean = true) = WeddingGuest(
        id = id,
        displayName = name,
        tableId = table.id,
        isActive = active,
    )
}
