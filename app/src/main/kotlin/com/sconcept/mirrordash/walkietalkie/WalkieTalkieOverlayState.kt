package com.sconcept.mirrordash.walkietalkie

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether [PttOverlayAccessibilityService] is currently bound by the system. Settings reads
 * this to show "Accessibility access granted/not granted" without needing a live reference to
 * the service itself (which only exists while the OS has it connected).
 */
object WalkieTalkieOverlayState {
    private val _isServiceConnected = MutableStateFlow(false)
    val isServiceConnected: StateFlow<Boolean> = _isServiceConnected

    fun onServiceAvailable() {
        _isServiceConnected.value = true
    }

    fun onServiceUnavailable() {
        _isServiceConnected.value = false
    }
}
