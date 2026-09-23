package com.onetapvpn.dispatcher

import android.net.VpnService
import com.onetapvpn.engine.AmneziaWgEngine
import com.onetapvpn.engine.FreeTurnEngine
import com.onetapvpn.engine.TrustTunnelEngine
import com.onetapvpn.engine.VpnEngine
import com.onetapvpn.engine.XraySingboxEngine
import com.onetapvpn.model.Engine
import com.onetapvpn.model.ServerProfile

class EngineDispatcher(private val vpnService: VpnService) {

    private val trustTunnel by lazy { TrustTunnelEngine(vpnService) }
    private val amneziaWg by lazy { AmneziaWgEngine(vpnService) }
    private val xraySingbox by lazy { XraySingboxEngine() }
    private val freeTurn by lazy { FreeTurnEngine(vpnService) }

    private var active: VpnEngine? = null

    fun engineFor(profile: ServerProfile): VpnEngine = when (profile.engine) {
        Engine.TRUSTTUNNEL -> trustTunnel
        Engine.AMNEZIA_WG -> amneziaWg
        Engine.XRAY_SINGBOX -> xraySingbox
        Engine.FREETURN -> freeTurn
    }

    suspend fun connect(profile: ServerProfile) {
        active?.disconnect()
        val engine = engineFor(profile)
        engine.connect(profile)
        active = engine
    }

    suspend fun disconnect() {
        active?.disconnect()
        active = null
    }

    fun isConnected(): Boolean = active?.isConnected() == true
}
