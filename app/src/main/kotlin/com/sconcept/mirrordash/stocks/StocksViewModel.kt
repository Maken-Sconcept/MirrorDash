package com.sconcept.mirrordash.stocks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sconcept.mirrordash.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes

data class StocksUiState(
    val quotesBySymbol: Map<String, StockQuote> = emptyMap(),
    val isLoading: Boolean = false,
)

/** Drives every Stocks widget on the Clock page from one shared poll of the union of all
 * configured symbols, same shape as [com.sconcept.mirrordash.weather.WeatherViewModel]. */
class StocksViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    private val repository = StocksRepository()

    private val _uiState = MutableStateFlow(StocksUiState())
    val uiState: StateFlow<StocksUiState> = _uiState

    private val symbols = settingsRepository.settings
        .map { it.stocksWidgets.flatMap { widget -> widget.symbols }.map(String::trim).filter(String::isNotBlank).distinct() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            while (true) {
                refreshInternal()
                kotlinx.coroutines.delay(REFRESH_INTERVAL_MINUTES.minutes)
            }
        }
    }

    fun refreshNow() {
        viewModelScope.launch { refreshInternal() }
    }

    private suspend fun refreshInternal() {
        val current = symbols.value
        if (current.isEmpty()) {
            _uiState.value = StocksUiState()
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true)
        val result = repository.fetchQuotes(current)
        result.fold(
            onSuccess = { quotes ->
                _uiState.value = StocksUiState(
                    quotesBySymbol = quotes.associateBy { it.symbol.uppercase() },
                    isLoading = false,
                )
            },
            onFailure = {
                _uiState.value = _uiState.value.copy(isLoading = false)
            },
        )
    }

    companion object {
        private const val REFRESH_INTERVAL_MINUTES = 5

        fun factory(settingsRepository: SettingsRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    StocksViewModel(settingsRepository) as T
            }
    }
}
