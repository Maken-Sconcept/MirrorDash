package com.sconcept.mirrordash.calendar

import android.Manifest
import android.app.Application
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.minutes

data class CalendarAgendaUiState(
    val permissionGranted: Boolean = false,
    val events: List<CalendarAgendaEntry> = emptyList(),
    val isLoading: Boolean = false,
)

/** Drives every Calendar-agenda widget on the Clock page from one shared poll, same
 * "while(true) { refresh(); delay(x) }" shape as [com.sconcept.mirrordash.weather.WeatherViewModel]
 * - fetches one upper-bound window ([MAX_LOOKAHEAD_DAYS]/[MAX_EVENTS]) and each widget trims it
 * down to its own `lookaheadDays`/`itemCount` at render time, since re-querying per widget
 * instance for what's ultimately the same calendar data isn't worth the complexity. */
class CalendarAgendaViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CalendarAgendaRepository(application)

    private val _uiState = MutableStateFlow(CalendarAgendaUiState())
    val uiState: StateFlow<CalendarAgendaUiState> = _uiState

    init {
        viewModelScope.launch {
            while (true) {
                refreshInternal()
                delay(REFRESH_INTERVAL_MINUTES)
            }
        }
    }

    fun refreshNow() {
        viewModelScope.launch { refreshInternal() }
    }

    private suspend fun refreshInternal() {
        val granted = ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.READ_CALENDAR,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!granted) {
            _uiState.value = CalendarAgendaUiState(permissionGranted = false)
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, permissionGranted = true)
        val events = withContext(Dispatchers.IO) {
            repository.upcomingEvents(lookaheadDays = MAX_LOOKAHEAD_DAYS, limit = MAX_EVENTS)
        }
        _uiState.value = CalendarAgendaUiState(permissionGranted = true, events = events, isLoading = false)
    }

    private suspend fun delay(minutes: Int) = kotlinx.coroutines.delay(minutes.minutes)

    companion object {
        private const val REFRESH_INTERVAL_MINUTES = 15
        private const val MAX_LOOKAHEAD_DAYS = 14
        private const val MAX_EVENTS = 30

        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                    CalendarAgendaViewModel(application) as T
            }
    }
}
