package com.sconcept.mirrordash.rtsp

import com.sconcept.mirrordash.brightness.RootShell

/**
 * Keeps the embedded RTSP listener private even if the router is misconfigured to forward its
 * port. The filter permits only RFC1918 source addresses and rejects every other TCP connection
 * before it reaches the application. Root is required; callers must fail closed when it is absent.
 */
internal object RtspLanFirewall {
    private const val CHAIN = "MIRRORDASH_RTSP"
    private val ipv4 = Regex("""^(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)(?:\\.(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}$""")

    fun install(port: Int, allowedClientIps: List<String>): Boolean {
        val accepted = allowedClientIps.filter { ipv4.matches(it) }.distinct()
        val allowRules = accepted.joinToString(separator = " ") { "iptables -A $CHAIN -s $it -j RETURN;" }
        return RootShell.run(
        "iptables -N $CHAIN 2>/dev/null || true; " +
            "iptables -F $CHAIN; " +
            allowRules +
            "iptables -A $CHAIN -j DROP; " +
            "iptables -D INPUT -p tcp --dport $port -j $CHAIN 2>/dev/null || true; " +
            "iptables -I INPUT -p tcp --dport $port -j $CHAIN",
        )
    }

    fun remove(port: Int) {
        RootShell.run("iptables -D INPUT -p tcp --dport $port -j $CHAIN 2>/dev/null || true")
    }
}
