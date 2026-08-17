package com.sconcept.mirrordash.wedding.seating

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeddingSeatingCsvImporterTest {

    @Test
    fun import_supportsDisplayNamesQuotedCommasAndTableNames() {
        val result = WeddingSeatingCsvImporter.import(
            "displayName,tableName,seatNumber,partyName\n\"Tremblay, Marie\",Rose,4,Family",
        ) as WeddingCsvImportResult.Success

        assertEquals("Tremblay, Marie", result.data.guests.single().displayName)
        assertEquals("4", result.data.guests.single().seatNumber)
        assertEquals("Family", result.data.guests.single().partyName)
        assertEquals("Rose", result.data.tables.single().name)
    }

    @Test
    fun import_buildsDisplayNameFromFirstAndLastName() {
        val result = WeddingSeatingCsvImporter.import(
            "firstName,lastName,tableName\nAndré,Leduc,Paris",
        ) as WeddingCsvImportResult.Success

        assertEquals("André Leduc", result.data.guests.single().displayName)
    }

    @Test
    fun import_deduplicatesTablesCaseInsensitively() {
        val result = WeddingSeatingCsvImporter.import(
            "displayName,tableName\nMarie,Rose\nMarc,rose",
        ) as WeddingCsvImportResult.Success

        assertEquals(1, result.data.tables.size)
        assertEquals(2, result.data.guests.size)
        assertEquals(result.data.guests[0].tableId, result.data.guests[1].tableId)
    }

    @Test
    fun import_preservesInactiveGuestsWithoutShowingThemInSearch() {
        val result = WeddingSeatingCsvImporter.import(
            "displayName,tableName,isActive\nMarie,Rose,no",
        ) as WeddingCsvImportResult.Success

        assertFalse(result.data.guests.single().isActive)
    }

    @Test
    fun import_reportsMissingRequiredHeader() {
        val result = WeddingSeatingCsvImporter.import("displayName\nMarie") as WeddingCsvImportResult.Failure

        assertTrue(result.errors.single().contains("tableName"))
    }

    @Test
    fun import_reportsRowNumbersForMissingValues() {
        val result = WeddingSeatingCsvImporter.import(
            "displayName,tableName\nMarie,\n,Rose",
        ) as WeddingCsvImportResult.Failure

        assertEquals(listOf("Row 2 has no table name.", "Row 3 has no guest name."), result.errors)
    }

    @Test
    fun import_rejectsUnclosedQuotes() {
        val result = WeddingSeatingCsvImporter.import(
            "displayName,tableName\n\"Marie,Rose",
        ) as WeddingCsvImportResult.Failure

        assertTrue(result.errors.single().contains("unclosed quoted value"))
    }
}
