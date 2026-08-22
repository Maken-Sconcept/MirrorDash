package com.sconcept.mirrordash.walkietalkie

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.sconcept.mirrordash.settings.DEFAULT_WALKIE_TALKIE_PORT
import com.sconcept.mirrordash.walkietalkie.model.WalkieTalkieDiscoveredPeer
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import java.util.concurrent.CopyOnWriteArraySet

/**
 * mDNS/NSD peer discovery, ported from BerthierOptions. Structural change: the advertised
 * device name/port are pushed in via [updateAdvertisedInfo] instead of read from a synchronous
 * `Context.config` on every registration - this class has no settings-storage dependency.
 * Service type is kept identical to BerthierOptions' (`_berthierwalkie._udp`) so MirrorDash and
 * BerthierOptions units can discover each other on a mixed network during a transition period.
 */
class WalkieTalkieDiscoveryBridge private constructor(context: Context) {

    interface Listener {
        fun onNearbyPeersChanged(peers: List<WalkieTalkieDiscoveredPeer>)
    }

    companion object {
        private const val TAG = "WalkieTalkieDiscovery"
        private const val SERVICE_TYPE = "_berthierwalkie._udp"
        private const val DEFAULT_DEVICE_NAME = "MirrorDash"

        @Volatile
        private var instance: WalkieTalkieDiscoveryBridge? = null

        fun get(context: Context): WalkieTalkieDiscoveryBridge {
            return instance ?: synchronized(this) {
                instance ?: WalkieTalkieDiscoveryBridge(context.applicationContext).also { instance = it }
            }
        }
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val listeners = CopyOnWriteArraySet<Listener>()
    private val stateLock = Any()
    private val peersByIp = linkedMapOf<String, WalkieTalkieDiscoveredPeer>()
    private val serviceToIp = mutableMapOf<String, String>()

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    @Volatile
    private var active = false

    @Volatile
    private var localIp = ""

    @Volatile
    private var advertisedDeviceName = DEFAULT_DEVICE_NAME

    @Volatile
    private var advertisedPort = DEFAULT_WALKIE_TALKIE_PORT

    fun addListener(listener: Listener) {
        listeners.add(listener)
        mainHandler.post { listener.onNearbyPeersChanged(snapshot()) }
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun snapshot(): List<WalkieTalkieDiscoveredPeer> = synchronized(stateLock) {
        peersByIp.values.sortedWith(compareBy({ it.name.lowercase() }, { it.ip }))
    }

    fun localIpAddress(): String {
        val resolved = localIp.ifBlank { resolveLocalIp() }
        if (localIp.isBlank()) {
            localIp = resolved
        }
        return resolved
    }

    /** Must be called (at least once, before [setActive]) whenever the device name or port the
     * user configured in Settings changes; [refreshRegistration] re-advertises with these. */
    fun updateAdvertisedInfo(deviceName: String, port: Int) {
        advertisedDeviceName = deviceName.ifBlank { DEFAULT_DEVICE_NAME }
        advertisedPort = port
        if (active) refreshRegistration()
    }

    fun setActive(enabled: Boolean) {
        if (enabled == active) {
            if (enabled) {
                refreshRegistration()
            }
            return
        }

        active = enabled
        if (enabled) {
            localIp = resolveLocalIp()
            acquireMulticastLock()
            startRegistration()
            startDiscovery()
        } else {
            stopDiscovery()
            stopRegistration()
            releaseMulticastLock()
            synchronized(stateLock) {
                peersByIp.clear()
                serviceToIp.clear()
            }
            notifyListeners()
        }
    }

    fun refreshRegistration() {
        if (!active) {
            return
        }
        stopRegistration()
        startRegistration()
    }

    /** Restarts NSD browsing so a user can immediately request a fresh peer list. */
    fun refreshDiscovery() {
        if (!active) return
        stopDiscovery()
        synchronized(stateLock) {
            peersByIp.clear()
            serviceToIp.clear()
        }
        notifyListeners()
        // NsdManager finishes stopping asynchronously; give it a short hand-off before starting
        // the next browse session so the restart is accepted on older Android versions too.
        mainHandler.postDelayed({
            if (active) startDiscovery()
        }, 250L)
    }

    private fun startRegistration() {
        if (registrationListener != null) {
            return
        }

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = advertisedDeviceName
            serviceType = "$SERVICE_TYPE."
            port = advertisedPort
            setAttribute("device", advertisedDeviceName)
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.i(TAG, "registered walkie service ${info.serviceName}:${info.port}")
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "walkie registration failed for ${info.serviceName}: $errorCode")
                registrationListener = null
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {
                Log.i(TAG, "unregistered walkie service ${info.serviceName}")
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "walkie unregistration failed for ${info.serviceName}: $errorCode")
            }
        }

        registrationListener = listener
        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.e(TAG, "registerService failed", e)
            registrationListener = null
        }
    }

    private fun stopRegistration() {
        val listener = registrationListener ?: return
        registrationListener = null
        try {
            nsdManager.unregisterService(listener)
        } catch (e: Exception) {
            Log.w(TAG, "unregisterService failed: ${e.message}")
        }
    }

    private fun startDiscovery() {
        if (discoveryListener != null) {
            return
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.i(TAG, "walkie discovery started for $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "walkie discovery failed to start for $serviceType: $errorCode")
                discoveryListener = null
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "walkie discovery failed to stop for $serviceType: $errorCode")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.i(TAG, "walkie discovery stopped for $serviceType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType != "$SERVICE_TYPE.") {
                    return
                }
                resolveService(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                synchronized(stateLock) {
                    val ip = serviceToIp.remove(serviceInfo.serviceName) ?: return
                    peersByIp.remove(ip)
                }
                notifyListeners()
            }
        }

        discoveryListener = listener
        try {
            nsdManager.discoverServices("$SERVICE_TYPE.", NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.e(TAG, "discoverServices failed", e)
            discoveryListener = null
        }
    }

    private fun stopDiscovery() {
        val listener = discoveryListener ?: return
        discoveryListener = null
        try {
            nsdManager.stopServiceDiscovery(listener)
        } catch (e: Exception) {
            Log.w(TAG, "stopServiceDiscovery failed: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveService(serviceInfo: NsdServiceInfo) {
        try {
            nsdManager.resolveService(
                serviceInfo,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        Log.w(TAG, "resolve failed for ${info.serviceName}: $errorCode")
                    }

                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val ip = info.host?.hostAddress.orEmpty()
                        if (ip.isBlank() || ip == localIpAddress()) {
                            return
                        }
                        val displayName = info.attributes["device"]?.toString(Charsets.UTF_8)?.ifBlank { null }
                            ?: info.serviceName
                        val peer = WalkieTalkieDiscoveredPeer(
                            serviceName = info.serviceName,
                            name = displayName,
                            ip = ip,
                            port = info.port,
                        )
                        synchronized(stateLock) {
                            serviceToIp[info.serviceName] = ip
                            peersByIp[ip] = peer
                        }
                        notifyListeners()
                    }
                },
            )
        } catch (e: Exception) {
            Log.w(TAG, "resolveService threw for ${serviceInfo.serviceName}: ${e.message}")
        }
    }

    private fun notifyListeners() {
        val snapshot = snapshot()
        mainHandler.post {
            listeners.forEach { it.onNearbyPeersChanged(snapshot) }
        }
    }

    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) {
            return
        }
        val lock = wifiManager?.createMulticastLock("mirrordash-walkie-discovery") ?: return
        lock.setReferenceCounted(false)
        runCatching { lock.acquire() }
        multicastLock = lock
    }

    private fun releaseMulticastLock() {
        val lock = multicastLock ?: return
        runCatching {
            if (lock.isHeld) {
                lock.release()
            }
        }
        multicastLock = null
    }

    private fun resolveLocalIp(): String {
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.let { Collections.list(it) }.orEmpty()
            interfaces
                .filter { it.isUp && !it.isLoopback }
                .flatMap { Collections.list(it.inetAddresses) }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                ?.hostAddress
                .orEmpty()
        }.getOrDefault("")
    }
}
