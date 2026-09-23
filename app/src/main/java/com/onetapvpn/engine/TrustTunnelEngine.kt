package com.onetapvpn.engine

import android.content.Context
import android.util.Log
import com.adguard.trusttunnel.AppNotifier
import com.adguard.trusttunnel.DeepLink
import com.adguard.trusttunnel.VpnPrepareActivity
import com.adguard.trusttunnel.VpnService as TrustTunnelVpnService
import com.onetapvpn.model.ServerProfile
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class TrustTunnelEngine(private val context: Context) : VpnEngine, AppNotifier {

    companion object {
        private const val TAG = "TrustTunnelEngine"
        private const val STATE_CONNECTED = 2
        private const val CONNECTION_TIMEOUT_MS = 30_000L
    }

    @Volatile
    private var connected = false
    private var initialized = false
    @Volatile
    private var connectionResult: CompletableDeferred<Unit>? = null

    override suspend fun connect(profile: ServerProfile) = withContext(Dispatchers.IO) {
        try {
            if (!initialized) {
                Log.d(TAG, "Initializing TrustTunnel SDK")
                TrustTunnelVpnService.initialize(context)
                val queryLogFile = File(context.filesDir, "trusttunnel_query_log.dat")
                TrustTunnelVpnService.setAppNotifier(queryLogFile, this@TrustTunnelEngine)
                initialized = true
            }

            // The SDK's own sample app uses VpnPrepareActivity rather than
            // relying only on a caller's VpnService.prepare() invocation. It
            // blocks until Android confirms that the SDK service may establish
            // its TUN interface, so it must run off the main thread.
            if (!TrustTunnelVpnService.isPrepared(context)) {
                Log.i(TAG, "Preparing VPN through TrustTunnel activity")
                VpnPrepareActivity.start(context)
            }

            val config = DeepLink.decode(profile.rawLink)
            Log.d(TAG, "Starting TrustTunnel (decoded config length=${config.length})")

            // start() only enqueues an Intent for the SDK service; a successful
            // return does not mean that a TUN interface was established.  Wait
            // for the SDK's callback so AppVpnService never reports a false
            // successful connection.
            val result = CompletableDeferred<Unit>()
            connectionResult = result
            TrustTunnelVpnService.start(context, config)

            try {
                withTimeout(CONNECTION_TIMEOUT_MS) {
                    result.await()
                }
            } catch (e: TimeoutCancellationException) {
                TrustTunnelVpnService.stop(context)
                throw IllegalStateException(
                    "TrustTunnel did not report an established connection within 30 seconds",
                    e
                )
            }
        } finally {
            connectionResult = null
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        connectionResult?.cancel()
        TrustTunnelVpnService.stop(context)
        connected = false
    }

    override fun isConnected(): Boolean = connected

    override fun onStateChanged(state: Int) {
        Log.d(TAG, "TrustTunnel state=$state")
        connected = state == STATE_CONNECTED
        if (connected) {
            connectionResult?.complete(Unit)
        }
    }

    override fun onConnectionInfo(info: String) {
        Log.d(TAG, "TrustTunnel connection info: $info")
    }
}
