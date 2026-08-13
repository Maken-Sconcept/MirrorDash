package com.sconcept.mirrordash.launcher.apps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AppDrawerUiState(
    val allApps: List<LauncherAppInfo> = emptyList(),
    val query: String = "",
) {
    val visibleApps: List<LauncherAppInfo> = if (query.isBlank()) {
        allApps
    } else {
        allApps.filter { it.label.contains(query, ignoreCase = true) }
    }
}

class AppDrawerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppRepository(application)
    private val _query = MutableStateFlow("")

    val uiState: StateFlow<AppDrawerUiState> = combine(repository.installedApps(), _query) { apps, query ->
        AppDrawerUiState(apps, query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppDrawerUiState())

    fun onQueryChange(query: String) {
        _query.value = query
    }

    fun launch(app: LauncherAppInfo) {
        repository.launch(app)
        _query.value = ""
    }

    fun openAppInfo(app: LauncherAppInfo) {
        repository.openAppInfo(app)
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = AppDrawerViewModel(application) as T
            }
    }
}
