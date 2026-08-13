package com.sconcept.mirrordash.launcher.gestures

/**
 * Feeds Travel mode's idle-to-Ambient timer (see [LauncherGestureHost]) from more than just raw
 * touches on the launcher's own root gesture surface. Typing on the on-screen keyboard delivers
 * keystrokes through the IME's own window, never as a touch event on the app's surface, so a
 * focused Settings text field with no root-level touches for
 * [com.sconcept.mirrordash.ui.theme.MirrorDashMotion.TRAVEL_IDLE_TIMEOUT_MS] used to collapse
 * Travel -> Ambient out from under someone still reading or typing into it - the very next tap
 * (e.g. to keep typing, or to move to the next field) then replayed the shrink-into-Travel
 * animation, even though the user never actually left Settings. Any composable that owns a text
 * input marks itself here (see `Modifier.trackFieldFocusForIdleTimer()` in WeatherSettings.kt) so
 * "a field has focus" counts as ongoing activity, not just literal touches.
 */
object LauncherIdleTracker {
    private var focusedFieldCount = 0

    val hasFocusedField: Boolean
        get() = focusedFieldCount > 0

    fun fieldFocused() {
        focusedFieldCount += 1
    }

    fun fieldUnfocused() {
        focusedFieldCount = (focusedFieldCount - 1).coerceAtLeast(0)
    }
}
