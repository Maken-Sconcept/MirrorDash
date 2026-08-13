package com.sconcept.mirrordash.walkietalkie

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import java.lang.ref.WeakReference

/**
 * Owns the lifecycle of the floating PTT overlay window. A raw [WindowManager]-added view has
 * none of the ViewTree owners Compose normally gets for free from an Activity, so this class
 * provides a minimal, always-[Lifecycle.State.RESUMED] host explicitly - see [OverlayLifecycleOwner].
 * Kept deliberately tiny: this is the entire surface area of MirrorDash's one AccessibilityService,
 * unlike BerthierOptions' GestureBarService which also replaced system navigation.
 */
object PttOverlayController {

    private var serviceRef: WeakReference<AccessibilityService>? = null
    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    val isServiceConnected: Boolean
        get() = serviceRef?.get() != null

    fun attach(service: AccessibilityService) {
        serviceRef = WeakReference(service)
        windowManager = service.getSystemService(AccessibilityService.WINDOW_SERVICE) as WindowManager
        WalkieTalkieOverlayState.onServiceAvailable()
    }

    fun detach(service: AccessibilityService) {
        if (serviceRef?.get() === service) {
            hide()
            serviceRef = null
            windowManager = null
        }
        WalkieTalkieOverlayState.onServiceUnavailable()
    }

    fun show(anchorXFraction: Float, anchorYFraction: Float, content: @androidx.compose.runtime.Composable () -> Unit) {
        val wm = windowManager ?: return
        if (overlayView != null) return

        val owner = OverlayLifecycleOwner().also { it.onCreate() }
        lifecycleOwner = owner

        val view = ComposeView(serviceRef?.get() ?: return).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent(content)
        }
        overlayView = view

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        wm.addView(view, params)
        owner.onStart()
    }

    fun updatePosition(xPx: Int, yPx: Int) {
        val wm = windowManager ?: return
        val view = overlayView ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        params.x = xPx
        params.y = yPx
        wm.updateViewLayout(view, params)
    }

    fun hide() {
        val wm = windowManager
        val view = overlayView
        if (wm != null && view != null) {
            runCatching { wm.removeView(view) }
        }
        lifecycleOwner?.onDestroy()
        lifecycleOwner = null
        overlayView = null
    }
}

/** Always advances straight to RESUMED - overlay windows don't have "stopped but visible"
 * semantics the way an Activity does, so a full lifecycle state machine would be unused
 * complexity here. */
private class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    fun onCreate() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun onStart() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
    }
}
