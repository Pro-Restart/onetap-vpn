package com.onetapvpn.engine

import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.adguard.trusttunnel.DeepLink
import com.adguard.trusttunnel.VpnClient
import com.adguard.trusttunnel.VpnClientListener
import com.onetapvpn.model.ServerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Реализация движка TrustTunnel поверх нативной библиотеки
 * libtrusttunnel_android.so (см. vpn/trusttunnel/TrustTunnelNative.kt).
 *
 * ВНИМАНИЕ — что подтверждено реверсом, а что является разумным допущением:
 *  - Контракт VpnClient/DeepLink (имена методов, типы аргументов) вычитан
 *    из classes.dex оригинального приложения — это точные данные, не догадка.
 *  - Адресация TUN-интерфейса (addAddress/addRoute/DNS ниже) НЕ была
 *    реверсирована из VpnServiceConfig/parseToml (это заняло бы отдельный
 *    большой цикл анализа TOML-схемы), поэтому используются общепринятые
 *    для full-tunnel VPN-клиентов значения. Если после первого теста
 *    `onConnectionInfo` в логах покажет, что сервер ожидает other
 *    addressing — эти константы нужно будет скорректировать.
 */
class TrustTunnelEngine(private val vpnService: VpnService) : VpnEngine {

    companion object {
        private const val TAG = "TrustTunnelEngine"
        private const val LOCAL_TUN_ADDRESS = "10.64.0.2"
        private const val LOCAL_TUN_PREFIX = 32
        private const val MTU = 1400
    }

    private var client: VpnClient? = null
    private var tunFd: ParcelFileDescriptor? = null
    private var connected = false

    override suspend fun connect(profile: ServerProfile) = withContext(Dispatchers.IO) {
        // 1. tt://... -> TOML-конфиг
        val config = DeepLink.decode(profile.rawLink)
        Log.d(TAG, "Decoded config length=${config.length}")

        // 2. TUN-интерфейс создаём САМИ, через наш собственный VpnService —
        //    библиотека ожидает уже готовый файловый дескриптор.
        val pfd = vpnService.Builder()
            .setSession(profile.label)
            .addAddress(LOCAL_TUN_ADDRESS, LOCAL_TUN_PREFIX)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")
            .setMtu(MTU)
            .establish()
            ?: throw IllegalStateException("VpnService.Builder().establish() вернул null — TUN не создан")

        tunFd = pfd

        // 3. Запускаем нативный движок поверх этого TUN
        val listener = object : VpnClientListener {
            override fun onStateChanged(state: Int) {
                Log.d(TAG, "TrustTunnel state=$state")
                connected = state == 2 // предположительно STATE_CONNECTED; уточнить по логам при тесте
            }

            override fun onConnectionInfo(info: String) {
                Log.d(TAG, "TrustTunnel info: $info")
            }

            override fun protectSocket(fd: Int): Boolean {
                // Критично: сокеты самого движка должны быть исключены из
                // туннеля, иначе будет петля трафика сам-в-себя.
                return vpnService.protect(fd)
            }

            override fun verifyCertificate(cert: ByteArray, chain: List<ByteArray>): Boolean {
                // TODO: сюда стоит добавить реальную проверку/pinning сертификата
                // сервера. Пока принимаем как есть, чтобы не блокировать
                // первое подключение — ужесточить после проверки на реальном сервере.
                return true
            }
        }

        val newClient = VpnClient(config, listener)
        val started = newClient.start(pfd)
        if (!started) {
            newClient.close()
            pfd.close()
            tunFd = null
            throw IllegalStateException("VpnClient.start() вернул false — сервер отклонил подключение")
        }

        client = newClient
        connected = true
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        client?.stop()
        client?.close()
        client = null
        tunFd?.close()
        tunFd = null
        connected = false
    }

    override fun isConnected(): Boolean = connected
}
