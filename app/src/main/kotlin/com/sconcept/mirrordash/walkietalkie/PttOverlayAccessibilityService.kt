package com.sconcept.mirrordash.walkietalkie

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.sconcept.mirrordash.launcher.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Hosts the floating push-to-talk button as a [android.view.WindowManager] overlay so it stays
 * reachable while another app is in the foreground (brief: "also system-wide overlay"). This
 * service does nothing else - unlike BerthierOptions' GestureBarService it does not also
 * replace system navigation, so its footprint (and the trust the user has to place in it) is
 * limited to exactly the PTT button. It only draws the overlay when the user has both enabled
 * Walkie-Talkie and turned on "Floating button" in Settings; otherwise it stays connected but
 * idle, drawing nothing.
 */
class PttOverlayAccessibilityService : AccessibilityService() {

    private var scope: CoroutineScope? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        PttOverlayController.attach(this)

        val engine = AppContainer.get(applicationContext).walkieTalkieEngine
        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = serviceScope

        serviceScope.launch {
            engine.uiState
                .distinctUntilChanged { old, new ->
                    old.enabled == new.enabled &&
                        old.overlayEnabled == new.overlayEnabled &&
                        old.pttAnchorX == new.pttAnchorX &&
                        old.pttAnchorY == new.pttAnchorY
                }
                .collectLatest { state ->
                    if (state.enabled && state.overlayEnabled) {
                        PttOverlayController.show(state.pttAnchorX, state.pttAnchorY) {
                            OverlayPttContent(engine)
                        }
                    } else {
                        PttOverlayController.hide()
                    }
                }
        }
    }

    override fun onDestroy() {
        PttOverlayController.detach(this)
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit
}

@Composable
private fun OverlayPttContent(engine: WalkieTalkieEngine) {
    val state by engine.uiState.collectAsState()
    PttButton(
        isTransmitting = state.isTransmitting,
        enabled = state.hasMicPermission,
        onPressStart = engine::pressToTalk,
        onPressEnd = engine::releaseToTalk,
    )
}
