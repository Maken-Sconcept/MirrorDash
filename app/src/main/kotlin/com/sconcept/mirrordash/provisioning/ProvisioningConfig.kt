package com.sconcept.mirrordash.provisioning

import kotlinx.serialization.Serializable

/**
 * Shape of the on-device provisioning file (see [ProvisioningConfigLoader]) - one optional block
 * per feature so a config only needs to mention what it actually wants to seed. Field names
 * mirror the settings they end up writing, not the underlying DataStore keys.
 */
@Serializable
data class ProvisioningConfig(
    val jellyfin: JellyfinConfig? = null,
    val homeAssistant: HomeAssistantConfig? = null,
    val walkieTalkie: WalkieTalkieConfig? = null,
    val iptv: IptvConfig? = null,
    val rtsp: RtspConfig? = null,
    val nas: NasConfig? = null,
    val weather: WeatherConfig? = null,
)

@Serializable
data class JellyfinConfig(
    val url: String,
    val username: String,
    val password: String,
    val autoAuth: Boolean = true,
)

@Serializable
data class HomeAssistantConfig(
    val url: String,
    val username: String,
    val password: String,
    val autoAuth: Boolean = true,
)

@Serializable
data class WalkieTalkieConfig(
    val autoAddDiscovered: Boolean = true,
)

@Serializable
data class IptvConfig(
    val url: String,
    val mac: String,
)

@Serializable
data class RtspConfig(
    val enabled: Boolean = false,
    val allowedClientIps: List<String> = emptyList(),
    val quality: String = com.sconcept.mirrordash.rtsp.RTSP_QUALITY_MEDIUM,
)

@Serializable
data class NasConfig(
    val server: String,
    val shareName: String,
    val username: String,
    val password: String,
)

/** Overrides the app's own baked-in Montreal default (see
 * [com.sconcept.mirrordash.settings.DEFAULT_WEATHER_LOCATION_QUERY]) for a unit deployed
 * elsewhere. [latitude]/[longitude] are optional - when omitted, the location resolves via
 * geocoding from [locationQuery] on first refresh, same as typing a city into Settings. */
@Serializable
data class WeatherConfig(
    val locationQuery: String,
    val latitude: String? = null,
    val longitude: String? = null,
    val useFahrenheit: Boolean = false,
)
