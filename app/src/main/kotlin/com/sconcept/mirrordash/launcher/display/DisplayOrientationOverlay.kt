package com.sconcept.mirrordash.launcher.display

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * The piece that was actually missing from [DisplayOrientationController]'s first pass: a
 * zero-size, invisible overlay window whose *own* `LayoutParams.screenOrientation` field is set
 * to the desired mode. Reverse-engineered from a third-party "Rotation Control" utility already
 * installed on this exact hardware (decompiled via apktool at the user's request, to confirm
 * the working technique rather than guess at one) - its `RotationControlService` builds
 * precisely this: a bare `View` added via `WindowManager` with `type=TYPE_SYSTEM_OVERLAY`,
 * `flags=FLAG_NOT_FOCUSABLE`, `format=PixelFormat.TRANSLUCENT`, `gravity=Gravity.TOP`, and
 * `screenOrientation` set to the target `ActivityInfo.SCREEN_ORIENTATION_*` constant.
 *
 * On-device testing showed `Activity.requestedOrientation` alone (what
 * [DisplayOrientationController] used before this) reliably resolves the two landscape modes on
 * this ROM but silently fails to reach portrait, even with `WRITE_SETTINGS` granted - this
 * overlay is the mechanism that actually forces it, because a `screenOrientation` set on *any*
 * visible window influences the WindowManagerPolicy's rotation resolution for the whole display,
 * not just the requesting Activity's own resolution. Requires `SYSTEM_ALERT_WINDOW`
 * ("draw over other apps"), a separate special-access permission from `WRITE_SETTINGS`.
 */
object DisplayOrientationOverlay {

    private var overlayView: View? = null
    private var windowManager: WindowManager? = null

    fun hasOverlayPermission(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun apply(context: Context, mode: DisplayOrientationMode) {
        if (!hasOverlayPermission(context)) return

        val wm = windowManager ?: (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).also { windowManager = it }
        val params = WindowManager.LayoutParams(
            0,
            0,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
            screenOrientation = mode.activityOrientation()
        }

        val existing = overlayView
        if (existing == null) {
            val view = View(context.applicationContext)
            overlayView = view
            runCatching { wm.addView(view, params) }
        } else {
            runCatching { wm.updateViewLayout(existing, params) }
        }
    }

    fun remove() {
        val wm = windowManager
        val view = overlayView
        if (wm != null && view != null) {
            runCatching { wm.removeView(view) }
        }
        overlayView = null
    }

    private fun overlayWindowType(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
        @Suppress("DEPRECATION")
        WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
    }
}
