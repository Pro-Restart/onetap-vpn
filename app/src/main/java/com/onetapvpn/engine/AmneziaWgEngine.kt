package com.onetapvpn.engine

import android.content.Context
import com.onetapvpn.model.ServerProfile
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.StringReader

/**
 * AmneziaWG / обычный WireGuard через официальную открытую библиотеку
 * com.wireguard.android.backend (GitHub: WireGuard/wireguard-android).
 * Реверс-инжиниринга здесь не требовалось — публичный документированный API.
 *
 * ВАЖНО: сейчас реализован только путь, когда rawLink — это ГОТОВЫЙ текстовый
 * WireGuard-конфиг ([Interface]/[Peer]). Компактные ссылки вида wg:// или
 * amnezia:// используют СВОЙ формат кодирования конфига (base64/бинарный),
 * специфичный для AmneziaWG-приложения — чтобы его поддержать, потребуется
 * отдельно реверсить именно эту схему (аналогично тому, как мы разобрали
 * tt:// для TrustTunnel). Пока такие ссылки явно выбрасывают ошибку,
 * а не тихо падают в никуда.
 */
class AmneziaWgEngine(private val context: Context) : VpnEngine {

    private val backend: GoBackend by lazy { GoBackend(context) }
    private var tunnel: SimpleTunnel? = null

    private class SimpleTunnel(private val tunnelName: String) : Tunnel {
        @Volatile var state: Tunnel.State = Tunnel.State.DOWN
        override fun getName(): String = tunnelName
        override fun onStateChange(newState: Tunnel.State) {
            state = newState
        }
    }

    override suspend fun connect(profile: ServerProfile) = withContext(Dispatchers.IO) {
        val configText = when {
            profile.rawLink.contains("[Interface]", ignoreCase = true) -> profile.rawLink
            else -> throw IllegalStateException(
                "Формат ссылки wg://amnezia:// пока не поддержан — нужен отдельный " +
                    "реверс схемы кодирования конфига AmneziaWG. Используйте " +
                    "готовый текстовый .conf вместо короткой ссылки."
            )
        }

        val config = Config.parse(StringReader(configText).buffered())
        val newTunnel = SimpleTunnel(profile.label.ifBlank { "onetap-amnezia" })
        backend.setState(newTunnel, Tunnel.State.UP, config)
        tunnel = newTunnel
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        val t = tunnel ?: return@withContext
        backend.setState(t, Tunnel.State.DOWN, null)
        tunnel = null
    }

    override fun isConnected(): Boolean = tunnel?.state == Tunnel.State.UP
}
