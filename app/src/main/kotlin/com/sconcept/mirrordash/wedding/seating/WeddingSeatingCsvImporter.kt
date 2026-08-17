package com.sconcept.mirrordash.wedding.seating

import java.nio.charset.StandardCharsets
import java.util.UUID

sealed interface WeddingCsvImportResult {
    data class Success(val data: WeddingSeatingData) : WeddingCsvImportResult
    data class Failure(val errors: List<String>) : WeddingCsvImportResult
}

object WeddingSeatingCsvImporter {
    const val MAX_GUESTS = 10_000

    fun import(csv: String): WeddingCsvImportResult {
        val parsedRows = parseRows(csv)
            ?: return WeddingCsvImportResult.Failure(listOf("The CSV has an unclosed quoted value."))
        val rows = parsedRows.filterNot { row -> row.all(String::isBlank) }
        if (rows.isEmpty()) return WeddingCsvImportResult.Failure(listOf("The CSV file is empty."))

        val headers = rows.first().map(::normalizeHeader)
        val displayNameIndex = headers.indexOfAny("displayname", "name", "guestname")
        val firstNameIndex = headers.indexOfAny("firstname", "first")
        val lastNameIndex = headers.indexOfAny("lastname", "last", "surname")
        val tableNameIndex = headers.indexOfAny("tablename", "table")
        if (tableNameIndex < 0) {
            return WeddingCsvImportResult.Failure(listOf("Add a tableName column to the CSV header."))
        }
        if (displayNameIndex < 0 && firstNameIndex < 0 && lastNameIndex < 0) {
            return WeddingCsvImportResult.Failure(
                listOf("Add displayName, or firstName and lastName columns, to the CSV header."),
            )
        }

        val dataRows = rows.drop(1)
        if (dataRows.size > MAX_GUESTS) {
            return WeddingCsvImportResult.Failure(listOf("The CSV contains more than $MAX_GUESTS guests."))
        }

        val seatIndex = headers.indexOfAny("seatnumber", "seat")
        val groupIndex = headers.indexOfAny("groupname", "group")
        val partyIndex = headers.indexOfAny("partyname", "party")
        val notesIndex = headers.indexOfAny("notes", "note")
        val activeIndex = headers.indexOfAny("isactive", "active")
        val errors = mutableListOf<String>()
        val tableNames = linkedMapOf<String, String>()
        val guests = mutableListOf<WeddingGuest>()

        dataRows.forEachIndexed { dataIndex, row ->
            val rowNumber = dataIndex + 2
            val firstName = row.valueAt(firstNameIndex).trim().take(80)
            val lastName = row.valueAt(lastNameIndex).trim().take(80)
            val displayName = row.valueAt(displayNameIndex).trim()
                .ifEmpty { listOf(firstName, lastName).filter(String::isNotEmpty).joinToString(" ") }
            val tableName = row.valueAt(tableNameIndex).trim()

            if (displayName.isEmpty()) errors += "Row $rowNumber has no guest name."
            if (tableName.isEmpty()) errors += "Row $rowNumber has no table name."
            if (displayName.isEmpty() || tableName.isEmpty()) return@forEachIndexed

            val normalizedTableName = GuestSearchEngine.normalize(tableName)
            tableNames.putIfAbsent(normalizedTableName, tableName)
            val guestIdSeed = "$rowNumber|$displayName|$normalizedTableName"
            guests += WeddingGuest(
                id = stableId("guest", guestIdSeed),
                firstName = firstName,
                lastName = lastName,
                displayName = displayName.take(160),
                tableId = stableId("table", normalizedTableName),
                seatNumber = row.valueAt(seatIndex).trim().takeIf(String::isNotEmpty)?.take(40),
                groupName = row.valueAt(groupIndex).trim().takeIf(String::isNotEmpty)?.take(120),
                partyName = row.valueAt(partyIndex).trim().takeIf(String::isNotEmpty)?.take(120),
                notes = row.valueAt(notesIndex).trim().takeIf(String::isNotEmpty)?.take(500),
                isActive = parseActive(row.valueAt(activeIndex)),
            )
        }

        if (errors.isNotEmpty()) return WeddingCsvImportResult.Failure(errors.take(20))
        if (guests.isEmpty()) return WeddingCsvImportResult.Failure(listOf("The CSV contains no guest rows."))

        val tables = tableNames.entries.mapIndexed { index, (normalizedName, displayName) ->
            WeddingTable(
                id = stableId("table", normalizedName),
                name = displayName.take(120),
                displayOrder = index,
            )
        }
        return WeddingCsvImportResult.Success(WeddingSeatingData(tables = tables, guests = guests))
    }

    private fun parseRows(csv: String): List<List<String>>? {
        val rows = mutableListOf<MutableList<String>>()
        var row = mutableListOf<String>()
        val value = StringBuilder()
        var inQuotes = false
        var index = 0

        fun finishValue() {
            row += value.toString()
            value.clear()
        }
        fun finishRow() {
            finishValue()
            rows += row
            row = mutableListOf()
        }

        while (index < csv.length) {
            when (val character = csv[index]) {
                '"' -> {
                    if (inQuotes && index + 1 < csv.length && csv[index + 1] == '"') {
                        value.append('"')
                        index++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ',' -> if (inQuotes) value.append(character) else finishValue()
                '\n' -> if (inQuotes) value.append(character) else finishRow()
                '\r' -> if (inQuotes) value.append(character)
                else -> value.append(character)
            }
            index++
        }
        if (inQuotes) return null
        if (value.isNotEmpty() || row.isNotEmpty()) finishRow()
        return rows
    }

    private fun normalizeHeader(value: String): String = value
        .removePrefix("\uFEFF")
        .lowercase()
        .filter(Char::isLetterOrDigit)

    private fun List<String>.indexOfAny(vararg names: String): Int = indexOfFirst(names::contains)
    private fun List<String>.valueAt(index: Int): String = if (index >= 0) getOrElse(index) { "" } else ""

    private fun parseActive(value: String): Boolean = when (value.trim().lowercase()) {
        "false", "no", "0", "inactive" -> false
        else -> true
    }

    private fun stableId(prefix: String, value: String): String =
        UUID.nameUUIDFromBytes("$prefix:$value".toByteArray(StandardCharsets.UTF_8)).toString()
}
