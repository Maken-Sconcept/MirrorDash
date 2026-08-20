package com.sconcept.mirrordash.launcher

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import coil.imageLoader

/**
 * Keeps long-running launcher sessions from retaining image-cache memory indefinitely. This only
 * touches recreatable Coil bitmaps; RTSP codecs, audio buffers, and the photo currently displayed
 * remain owned by their respective components.
 */
class MemoryTrimCoordinator(context: Context) : ComponentCallbacks2 {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val trimRunnable: Runnable = Runnable {
        trimIfNeeded()
        handler.postDelayed(trimRunnable, PERIODIC_TRIM_MS)
    }

    fun start() {
        synchronized(MemoryTrimCoordinator::class.java) {
            if (started) return
            started = true
        }
        appContext.registerComponentCallbacks(this)
        handler.postDelayed(trimRunnable, PERIODIC_TRIM_MS)
    }

    override fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) trim()
    }

    override fun onLowMemory() = trim()

    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    private fun trimIfNeeded() {
        val runtime = Runtime.getRuntime()
        val usedBytes = runtime.totalMemory() - runtime.freeMemory()
        if (usedBytes * 100 >= runtime.maxMemory() * HEAP_PRESSURE_PERCENT) trim()
    }

    private fun trim() {
        appContext.imageLoader.memoryCache?.clear()
        // Explicit collection is deliberately reserved for a low-memory callback or a process
        // already over its pressure threshold; doing it on every timer tick would cause jank.
        Runtime.getRuntime().gc()
    }

    private companion object {
        @Volatile
        var started = false
        const val PERIODIC_TRIM_MS = 10 * 60 * 1000L
        const val HEAP_PRESSURE_PERCENT = 60L
    }
}
