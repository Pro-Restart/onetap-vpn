package com.onetapvpn.engine

import com.onetapvpn.model.ServerProfile

/**
 * Xray/sing-box движок пока НЕ реализован — библиотека libgojni.so
 * (движок NekoBox+) ещё не была реверс-инжинирена в той же степени, что
 * TrustTunnel. Это следующий логичный шаг: контракт JNI придётся вычитывать
 * из classes.dex NekoBox+ (класс libcore.Libcore, метод
 * newSingBoxInstance/newSingBoxInstanceWithPaths и т.д.) тем же способом,
 * которым был разобран TrustTunnel.VpnClient.
 *
 * Явно бросаем понятную ошибку вместо тихого "зависания" — тот же класс
 * симптомов, что был у прошлой версии TrustTunnelEngine, только для этого
 * протокола он никуда пока не делся.
 */
class XraySingboxEngine : VpnEngine {

    override suspend fun connect(profile: ServerProfile) {
        throw NotImplementedError(
            "Xray/sing-box движок ещё не реализован в этой сборке. " +
                "Нужен отдельный этап реверс-инжиниринга libgojni.so — " +
                "см. комментарий в XraySingboxEngine.kt."
        )
    }

    override suspend fun disconnect() = Unit

    override fun isConnected(): Boolean = false
}
