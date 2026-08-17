package com.sconcept.mirrordash.wedding.seating

import kotlinx.serialization.Serializable

@Serializable
data class WeddingTable(
    val id: String,
    val name: String,
    val number: String? = null,
    val description: String? = null,
    val location: String? = null,
    val displayOrder: Int = 0,
)

@Serializable
data class WeddingGuest(
    val id: String,
    val firstName: String = "",
    val lastName: String = "",
    val displayName: String,
    val tableId: String,
    val seatNumber: String? = null,
    val groupName: String? = null,
    val partyName: String? = null,
    val qrToken: String? = null,
    val notes: String? = null,
    val isActive: Boolean = true,
)

@Serializable
data class WeddingSeatingData(
    val schemaVersion: Int = 1,
    val tables: List<WeddingTable> = emptyList(),
    val guests: List<WeddingGuest> = emptyList(),
)

data class WeddingGuestMatch(
    val guest: WeddingGuest,
    val table: WeddingTable,
)

data class WeddingSearchResults(
    val matches: List<WeddingGuestMatch> = emptyList(),
    val totalCount: Int = 0,
)
