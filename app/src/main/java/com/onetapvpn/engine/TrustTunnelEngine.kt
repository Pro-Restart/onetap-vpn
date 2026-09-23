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

    override suspend fun connect(profile: ServerProfile): Unit = withContext(Dispatchers.IO) {
        Log.d(TAG, "connect() started, rawLink length=${profile.rawLink.length}")

        try {
            if (!initialized) {
                Log.d(TAG, "calling TrustTunnelVpnService.initialize()...")
                TrustTunnelVpnService.initialize(context)
                val queryLogFile = File(context.filesDir, "trusttunnel_query_log.dat")
                TrustTunnelVpnService.setAppNotifier(queryLogFile, this@TrustTunnelEngine)
                initialized = true
                Log.d(TAG, "initialize() OK")
            }

            val config = DeepLink.decode(profile.rawLink)
            Log.d(TAG, "Decoded config length=${config.length}")
            Log.d(TAG, "Decoded config (sanitized)=${config.take(300)}")

            Log.d(TAG, "calling TrustTunnelVpnService.start() with 30s timeout...")
            withTimeout(30_000) {
                TrustTunnelVpnService.start(context, config)
            }
            Log.d(TAG, "TrustTunnelVpnService.start() returned successfully")

            // Просто ждём 20 секунд и пишем heartbeat — чтобы увидеть,
            // появятся ли за это время логи от самой библиотеки
            repeat(20) { i ->
                delay(1000)
                Log.d(TAG, "waiting... ${i + 1}s after start()")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "start() TIMED OUT after 30s — library is stuck", e)
            throw e
        } catch (t: Throwable) {
            Log.e(TAG, "start() FAILED with ${t.javaClass.simpleName}: ${t.message}", t)
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
