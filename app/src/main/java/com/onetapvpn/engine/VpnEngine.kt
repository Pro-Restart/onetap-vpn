package com.onetapvpn.engine

import com.onetapvpn.model.ServerProfile

interface VpnEngine {
    suspend fun connect(profile: ServerProfile)
    suspend fun disconnect()
    fun isConnected(): Boolean
}
