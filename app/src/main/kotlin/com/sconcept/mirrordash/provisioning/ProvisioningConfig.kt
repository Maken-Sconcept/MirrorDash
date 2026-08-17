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
    val nas: NasConfig? = null,
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
data class NasConfig(
    val server: String,
    val shareName: String,
    val username: String,
    val password: String,
)
