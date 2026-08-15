package com.sconcept.mirrordash.iptv

import kotlin.random.Random

/** MAC address generation matching the range real MAG-series boxes (and STBEMU, which emulates
 * them) ship with - the `00:1A:79` OUI block is registered to Infomir, the manufacturer, so IPTV
 * portals commonly treat it as the signal that a client is a set-top box worth authenticating by
 * MAC alone. A locally-invented prefix would work with some portals but gets silently rejected by
 * others that filter on it. */
object IptvMac {
    private const val INFOMIR_OUI = "00:1A:79"
    private val MAC_REGEX = Regex("^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}$")

    fun generateRandom(): String {
        val suffix = (1..3).joinToString(":") { Random.nextInt(0, 256).toString(16).padStart(2, '0').uppercase() }
        return "$INFOMIR_OUI:$suffix"
    }

    fun isValid(mac: String): Boolean = MAC_REGEX.matches(mac.trim())

    fun normalize(mac: String): String = mac.trim().uppercase()
}
