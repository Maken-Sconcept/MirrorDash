package com.sconcept.mirrordash.launcher.gestures

/**
 * What the launcher's interaction surface is currently doing. Kept as an explicit, small,
 * closed set of states (brief section 43) rather than scattering booleans through the pager
 * and gesture code, so every transition is a single, greppable assignment. The two "opening"
 * states exist because the drawer/notifications sheets track the finger continuously while
 * being dragged open - see LauncherGestureHost - and other UI (e.g. the scrim) needs to know
 * "a sheet is moving" independent of exactly how far it's moved.
 */
sealed interface LauncherInteractionState {
    data object Ambient : LauncherInteractionState
    data object Travel : LauncherInteractionState
    data object OpeningAppDrawer : LauncherInteractionState
    data object AppDrawer : LauncherInteractionState
    data object OpeningNotifications : LauncherInteractionState
    data object Notifications : LauncherInteractionState
}

internal fun LauncherInteractionState.isSheetInvolved(): Boolean = this !is LauncherInteractionState.Ambient &&
    this !is LauncherInteractionState.Travel
