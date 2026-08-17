package com.sconcept.mirrordash.wedding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sconcept.mirrordash.wedding.seating.GuestSearchEngine
import com.sconcept.mirrordash.wedding.seating.WeddingCsvImportResult
import com.sconcept.mirrordash.wedding.seating.WeddingGuest
import com.sconcept.mirrordash.wedding.seating.WeddingGuestMatch
import com.sconcept.mirrordash.wedding.seating.WeddingSeatingCsvImporter
import com.sconcept.mirrordash.wedding.seating.WeddingSeatingRepository
import com.sconcept.mirrordash.wedding.seating.WeddingSeatingState
import com.sconcept.mirrordash.wedding.seating.WeddingTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WeddingAdminState(
    val isImporting: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
)

data class WeddingUiState(
    val seating: WeddingSeatingState = WeddingSeatingState(),
    val query: String = "",
    val searchResults: List<WeddingGuestMatch> = emptyList(),
    val totalResultCount: Int = 0,
    val selectedGuest: WeddingGuest? = null,
    val selectedTable: WeddingTable? = null,
    val admin: WeddingAdminState = WeddingAdminState(),
)

class WeddingViewModel(private val seatingRepository: WeddingSeatingRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val selectedGuestId = MutableStateFlow<String?>(null)
    private val adminState = MutableStateFlow(WeddingAdminState())

    private val searchResults = combine(seatingRepository.state, query) { seating, value ->
        seating.data to value
    }.map { (data, value) ->
        withContext(Dispatchers.Default) { GuestSearchEngine.search(value, data) }
    }

    val uiState: StateFlow<WeddingUiState> = combine(
        seatingRepository.state,
        query,
        selectedGuestId,
        searchResults,
        adminState,
    ) { seating, value, selectedId, results, admin ->
        val selectedGuest = seating.data.guests.firstOrNull { guest ->
            guest.id == selectedId && guest.isActive
        }
        WeddingUiState(
            seating = seating,
            query = value,
            searchResults = results.matches,
            totalResultCount = results.totalCount,
            selectedGuest = selectedGuest,
            selectedTable = selectedGuest?.let { guest ->
                seating.data.tables.firstOrNull { table -> table.id == guest.tableId }
            },
            admin = admin,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeddingUiState())

    fun onQueryChange(value: String) {
        query.value = value.take(120)
        selectedGuestId.value = null
    }

    fun selectGuest(match: WeddingGuestMatch) {
        selectedGuestId.value = match.guest.id
    }

    fun clearGuestSession() {
        query.value = ""
        selectedGuestId.value = null
    }

    fun importCsv(csv: String) {
        if (adminState.value.isImporting) return
        adminState.value = WeddingAdminState(isImporting = true)
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.Default) { WeddingSeatingCsvImporter.import(csv) }) {
                is WeddingCsvImportResult.Failure -> {
                    adminState.value = WeddingAdminState(
                        message = result.errors.joinToString("\n"),
                        messageIsError = true,
                    )
                }
                is WeddingCsvImportResult.Success -> {
                    runCatching { seatingRepository.replace(result.data) }
                        .onSuccess {
                            clearGuestSession()
                            adminState.value = WeddingAdminState(
                                message = "Imported ${result.data.guests.size} guests across ${result.data.tables.size} tables.",
                            )
                        }
                        .onFailure { error ->
                            adminState.value = WeddingAdminState(
                                message = error.message ?: "Seating data could not be saved.",
                                messageIsError = true,
                            )
                        }
                }
            }
        }
    }

    fun clearSeating() {
        if (adminState.value.isImporting) return
        adminState.update { it.copy(isImporting = true, message = null) }
        viewModelScope.launch {
            runCatching { seatingRepository.clear() }
                .onSuccess {
                    clearGuestSession()
                    adminState.value = WeddingAdminState(message = "Guest and table data cleared.")
                }
                .onFailure { error ->
                    adminState.value = WeddingAdminState(
                        message = error.message ?: "Seating data could not be cleared.",
                        messageIsError = true,
                    )
                }
        }
    }

    companion object {
        fun factory(repository: WeddingSeatingRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    WeddingViewModel(repository) as T
            }
    }
}
