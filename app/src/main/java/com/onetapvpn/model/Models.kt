package com.onetapvpn.model

/**
 * Движки/протоколы, которые приложение умеет обслуживать.
 */
enum class Engine {
    XRAY_SINGBOX,
    TRUSTTUNNEL,
    AMNEZIA_WG,
    FREETURN
}

/**
 * Один распознанный профиль подключения — результат разбора одной ссылки
 * из загруженного файла.
 *
 * @param rawLink   исходная ссылка как есть — именно её получает движок
 *                  (движок сам отвечает за то, как её разобрать/декодировать)
 * @param label     человекочитаемое имя для UI
 */
data class ServerProfile(
    val engine: Engine,
    val rawLink: String,
    val label: String
)
