package com.sconcept.mirrordash.launcher.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Process
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** A single launchable app - the component key doubles as a stable identity for
 * favorites/hidden-apps storage. */
data class LauncherAppInfo(
    val label: String,
    val packageName: String,
    val componentKey: String,
    val icon: Drawable,
)

/**
 * Queries installed launchable apps via [LauncherApps] (scoped to apps the user can actually
 * launch, same principle as BerthierOptions' manifest `<queries>` approach - no
 * `QUERY_ALL_PACKAGES`) and re-queries reactively on package add/remove/replace instead of
 * BerthierOptions' static one-shot list, via [installedApps].
 */
class AppRepository(private val context: Context) {

    private val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    /** Emits the current app list immediately, then again whenever installed packages change. */
    fun installedApps(): Flow<List<LauncherAppInfo>> = callbackFlow {
        fun emitCurrent() {
            trySend(queryApps())
        }

        val callback = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String?, user: android.os.UserHandle?) = emitCurrent()
            override fun onPackageRemoved(packageName: String?, user: android.os.UserHandle?) = emitCurrent()
            override fun onPackageChanged(packageName: String?, user: android.os.UserHandle?) = emitCurrent()
            override fun onPackagesAvailable(packageNames: Array<out String>?, user: android.os.UserHandle?, replacing: Boolean) = emitCurrent()
            override fun onPackagesUnavailable(packageNames: Array<out String>?, user: android.os.UserHandle?, replacing: Boolean) = emitCurrent()
            override fun onPackagesSuspended(packageNames: Array<out String>?, user: android.os.UserHandle?) = emitCurrent()
            override fun onPackagesUnsuspended(packageNames: Array<out String>?, user: android.os.UserHandle?) = emitCurrent()
        }
        launcherApps.registerCallback(callback)
        emitCurrent()

        awaitClose { launcherApps.unregisterCallback(callback) }
    }.distinctUntilChanged()

    fun launch(app: LauncherAppInfo) {
        val activities = launcherApps.getActivityList(app.packageName, Process.myUserHandle())
        val target = activities.firstOrNull { it.componentName.flattenToString() == app.componentKey } ?: activities.firstOrNull()
        target ?: return
        launcherApps.startMainActivity(target.componentName, Process.myUserHandle(), null, null)
    }

    fun openAppInfo(app: LauncherAppInfo) {
        runCatching {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", app.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private fun queryApps(): List<LauncherAppInfo> {
        val user = Process.myUserHandle()
        return launcherApps.getActivityList(null, user)
            .map { info ->
                LauncherAppInfo(
                    label = info.label.toString(),
                    packageName = info.applicationInfo.packageName,
                    componentKey = info.componentName.flattenToString(),
                    icon = info.getBadgedIcon(0),
                )
            }
            .filterNot { it.packageName == context.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
