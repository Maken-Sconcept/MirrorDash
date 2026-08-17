package com.sconcept.mirrordash.mirrordrop

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

/**
 * LAN address/Wi-Fi-state detection shared by the Photobooth diagnostics screen (brief §6) and
 * the MirrorDrop server itself (§29 - regenerate the QR/address whenever this changes). Never
 * hardcodes an address (§29): always walks live [NetworkInterface]s and ignores loopback/down/
 * virtual interfaces so it keeps working across Wi-Fi reconnects and IP changes.
 */
object MirrorDropNetworkUtils {

    fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /** Current Wi-Fi SSID, best-effort (quoted by the OS on many versions - stripped here). */
    fun currentSsid(context: Context): String? {
        return runCatching {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                ?: return null
            val info = wifiManager.connectionInfo ?: return null
            val ssid = info.ssid ?: return null
            if (ssid == "<unknown ssid>" || ssid.isBlank()) null else ssid.trim('"')
        }.getOrNull()
    }

    /**
     * First non-loopback, non-virtual IPv4 address bound to a live interface - deliberately not
     * limited to `wlan0` by name, since the Rockchip board's actual Wi-Fi interface name is
     * unverified (brief §5's "don't assume" applies here too).
     */
    fun getLocalIpv4Address(): String? {
        return runCatching {
            NetworkInterface.getNetworkInterfaces()?.asSequence()
                ?.filter { iface -> iface.isUp && !iface.isLoopback && !iface.isVirtual }
                ?.flatMap { iface -> iface.inetAddresses.asSequence() }
                ?.filterIsInstance<Inet4Address>()
                ?.firstOrNull { !it.isLoopbackAddress }
                ?.hostAddress
                ?.lowercase(Locale.ROOT)
        }.getOrNull()
    }
}
