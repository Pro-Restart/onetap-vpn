package com.onetapvpn.engine

import android.content.Context
import android.util.Log
import com.adguard.trusttunnel.AppNotifier
import com.adguard.trusttunnel.DeepLink
import com.adguard.trusttunnel.VpnService as TrustTunnelVpnService
import com.onetapvpn.model.ServerProfile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

class TrustTunnelEngine(private val context: Context) : VpnEngine, AppNotifier {

    companion object {
        private const val TAG = "TrustTunnelEngine"
        private const val STATE_CONNECTED = 2
    }

    private var connected = false
    private var initialized = false

    override suspend fun connect(profile: ServerProfile) = withContext(Dispatchers.IO) {
        try {
            if (!initialized) {
                TrustTunnelVpnService.initialize(context)
                val queryLogFile = File(context.filesDir, "trusttunnel_query_log.dat")
                TrustTunnelVpnService.setAppNotifier(queryLogFile, this@TrustTunnelEngine)
                initialized = true
            }

            val config = DeepLink.decode(profile.rawLink)
            Log.d(TAG, "Decoded config length=${config.length}")
            Log.d(TAG, "Decoded config (sanitized)=${config.take(200)}...")
    
            withTimeout(30_000) {
                TrustTunnelVpnService.start(context, config)
            }
            Log.d(TAG, "TrustTunnelVpnService.start() returned successfully")
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "TrustTunnelVpnService.start() timed out after 30s", e)
            throw e
        } catch (t: Throwable) {
            Log.e(TAG, "TrustTunnelVpnService.start() failed", t)
            throw t
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        TrustTunnelVpnService.stop(context)
        connected = false
    }

    override fun isConnected(): Boolean = connected

    override fun onStateChanged(state: Int) {
        Log.d(TAG, "TrustTunnel state=$state")
        connected = state == STATE_CONNECTED
    }

    override fun onConnectionInfo(info: String) {
        Log.d(TAG, "TrustTunnel connection info: $info")
    }
}
