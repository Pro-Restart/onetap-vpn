package com.onetapvpn.parser

import com.onetapvpn.model.Engine
import com.onetapvpn.model.ServerProfile
import java.net.URLDecoder
import java.util.UUID

/**
 * Разбирает файл со ссылками и определяет движок для каждой.
 *
 * ВАЖНО (история бага): в предыдущей версии функция `detect()` была написана
 * как цепочка `if (link.startsWith(prefix)) { ... } else if (...) ...`, и из-за
 * ошибки при её наборе почти все ветки при УДАЧНОМ совпадении вели туда же,
 * куда и полное несовпадение — то есть срабатывал возврат null для xray/
 * amnezia/freeturn-ссылок, и реально строился профиль только для tt://.
 * Здесь та же логика переписана через явный `when`, где каждая ветка сразу
 * возвращает готовый ServerProfile — так структурно невозможно случайно
 * перепутать ветку "совпало" с веткой "не совпало".
 */
object LinkParser {

    fun parse(content: String): List<ServerProfile> {
        return content
            .split(",", "\n", "\r\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { detect(it) }
    }

    fun detect(link: String): ServerProfile? {
        val lower = link.lowercase()

        val engine = when {
            // Xray / sing-box совместимые схемы
            lower.startsWith("vless://") -> Engine.XRAY_SINGBOX
            lower.startsWith("vmess://") -> Engine.XRAY_SINGBOX
            lower.startsWith("trojan://") -> Engine.XRAY_SINGBOX
            lower.startsWith("ss://") -> Engine.XRAY_SINGBOX
            lower.startsWith("ssr://") -> Engine.XRAY_SINGBOX
            lower.startsWith("hysteria://") -> Engine.XRAY_SINGBOX
            lower.startsWith("hysteria2://") -> Engine.XRAY_SINGBOX
            lower.startsWith("hy2://") -> Engine.XRAY_SINGBOX
            lower.startsWith("tuic://") -> Engine.XRAY_SINGBOX

            // TrustTunnel
            lower.startsWith("tt://") -> Engine.TRUSTTUNNEL

            // AmneziaWG / обычный WireGuard-конфиг
            lower.startsWith("wg://") -> Engine.AMNEZIA_WG
            lower.startsWith("amnezia://") -> Engine.AMNEZIA_WG
            looksLikeWireguardConfig(link) -> Engine.AMNEZIA_WG

            // FreeTurn
            lower.startsWith("freeturn://") -> Engine.FREETURN
            looksLikeFreeTurnLink(lower) -> Engine.FREETURN

            else -> null
        } ?: return null

        return ServerProfile(
            engine = engine,
            rawLink = link,
            label = extractLabel(link, engine)
        )
    }

    private fun looksLikeWireguardConfig(link: String): Boolean {
        return link.contains("[Interface]", ignoreCase = true) &&
            link.contains("[Peer]", ignoreCase = true)
    }

    private fun looksLikeFreeTurnLink(lowerLink: String): Boolean {
        return lowerLink.contains("vk.com/call") ||
            lowerLink.contains("vk.me/call") ||
            lowerLink.contains("vk://call")
    }

    private fun extractLabel(link: String, engine: Engine): String {
        val fragmentIndex = link.indexOf('#')
        if (fragmentIndex != -1 && fragmentIndex < link.length - 1) {
            val raw = link.substring(fragmentIndex + 1)
            return runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
        }
        return "${engine.name} · ${UUID.randomUUID().toString().take(4)}"
    }
}
