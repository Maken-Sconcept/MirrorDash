package com.sconcept.mirrordash.rtsp

import java.net.NetworkInterface

/** The address a LAN viewer can use; excludes VPN, loopback and public interfaces. */
fun localRtspIpv4Address(): String? = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .asSequence()
        .flatMap { it.inetAddresses.toList().asSequence() }
        .firstOrNull { address ->
            !address.isLoopbackAddress && address.isSiteLocalAddress && (address.hostAddress?.indexOf(':') ?: -1) < 0
        }
        ?.hostAddress
}.getOrNull()
