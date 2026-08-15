package com.sconcept.mirrordash.launcher.notifications

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.provider.Settings
import com.sconcept.mirrordash.brightness.RootShell

enum class QuickSettingKind { WIFI, BLUETOOTH, DND, AIRPLANE }

data class QuickSettingState(val kind: QuickSettingKind, val enabled: Boolean, val available: Boolean)

/**
 * Backs the Notifications panel's quick-toggle row (Wi-Fi, Bluetooth, DND, Airplane mode) - see
 * [NotificationsScreen]. Every toggle here is best-effort, same shape as [BacklightController]
 * and [com.sconcept.mirrordash.launcher.display.DisplayOrientationController]: try the direct
 * unprivileged API first, fall back to a root shell command, and if neither works the caller
 * falls back further still to just opening the matching system Settings screen - never a dead
 * end, never a crash.
 *
 * On this hardware's actual API 25 target, Wi-Fi/Bluetooth toggle directly with no root needed
 * (Android only started blocking `WifiManager.setWifiEnabled`/`BluetoothAdapter.enable` for
 * regular apps in later versions) - the root fallback exists for other/newer units this build
 * might end up running on. DND needs the user to grant "Notification policy access" once (a
 * separate special-access toggle from notification *listener* access); Airplane mode is
 * `WRITE_SECURE_SETTINGS`-gated on every Android version, so root or the Settings screen are the
 * only two paths that will ever work for it.
 */
object QuickSettingsController {

    fun currentStates(context: Context): List<QuickSettingState> {
        val appContext = context.applicationContext
        return listOf(
            QuickSettingState(QuickSettingKind.WIFI, isWifiEnabled(appContext), available = true),
            QuickSettingState(QuickSettingKind.BLUETOOTH, isBluetoothEnabled(), available = bluetoothAdapter() != null),
            QuickSettingState(QuickSettingKind.DND, isDndEnabled(appContext), available = true),
            QuickSettingState(QuickSettingKind.AIRPLANE, isAirplaneModeEnabled(appContext), available = true),
        )
    }

    fun hasNotificationPolicyAccess(context: Context): Boolean =
        notificationManager(context).isNotificationPolicyAccessGranted

    /** Returns true if the toggle actually took effect (direct API or root); false means the
     * caller should fall back to opening [settingsIntentFor] instead. */
    @Suppress("DEPRECATION")
    fun toggle(context: Context, kind: QuickSettingKind, enable: Boolean): Boolean {
        val appContext = context.applicationContext
        return when (kind) {
            QuickSettingKind.WIFI -> {
                val direct = runCatching {
                    (appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.setWifiEnabled(enable) == true
                }.getOrDefault(false)
                direct || RootShell.run("svc wifi ${if (enable) "enable" else "disable"}")
            }
            QuickSettingKind.BLUETOOTH -> {
                val adapter = bluetoothAdapter()
                val direct = runCatching {
                    if (adapter == null) false else if (enable) adapter.enable() else adapter.disable()
                }.getOrDefault(false)
                direct || RootShell.run("svc bluetooth ${if (enable) "enable" else "disable"}")
            }
            QuickSettingKind.DND -> {
                if (hasNotificationPolicyAccess(appContext)) {
                    notificationManager(appContext).setInterruptionFilter(
                        if (enable) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL,
                    )
                    true
                } else {
                    false
                }
            }
            QuickSettingKind.AIRPLANE -> {
                RootShell.run("settings put global airplane_mode_on ${if (enable) 1 else 0}") &&
                    RootShell.run("am broadcast -a android.intent.action.AIRPLANE_MODE --ez state $enable")
            }
        }
    }

    fun settingsIntentFor(kind: QuickSettingKind): Intent = Intent(
        when (kind) {
            QuickSettingKind.WIFI -> Settings.ACTION_WIFI_SETTINGS
            QuickSettingKind.BLUETOOTH -> Settings.ACTION_BLUETOOTH_SETTINGS
            QuickSettingKind.DND -> Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
            QuickSettingKind.AIRPLANE -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
        },
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun bluetoothAdapter(): BluetoothAdapter? = runCatching { BluetoothAdapter.getDefaultAdapter() }.getOrNull()

    private fun isWifiEnabled(context: Context): Boolean =
        runCatching { (context.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.isWifiEnabled == true }.getOrDefault(false)

    private fun isBluetoothEnabled(): Boolean = runCatching { bluetoothAdapter()?.isEnabled == true }.getOrDefault(false)

    private fun isDndEnabled(context: Context): Boolean =
        notificationManager(context).currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL

    private fun isAirplaneModeEnabled(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0

    private fun notificationManager(context: Context): NotificationManager =
        context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}
