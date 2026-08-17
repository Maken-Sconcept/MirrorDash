package com.sconcept.mirrordash.iptv.player

import android.content.Context

object IptvPlayerFactory {
    fun create(backend: PlayerBackend, context: Context): IptvPlayer = when (backend) {
        PlayerBackend.EXOPLAYER -> ExoIptvPlayer(context)
        PlayerBackend.VLC -> VlcIptvPlayer(context)
        PlayerBackend.IJKPLAYER -> IjkIptvPlayer(context)
    }
}
