package com.adguard.trusttunnel

import android.os.ParcelFileDescriptor

/**
 * ВАЖНО: пакет и имена классов здесь скопированы 1-в-1 с оригинального
 * приложения TrustTunnel (AdGuard, open source: github.com/TrustTunnel/TrustTunnel).
 * Это не случайность и не "подделка" — статическая линковка JNI работает
 * по имени символа вида Java_com_adguard_trusttunnel_VpnClient_startNative,
 * которое зашито в libtrusttunnel_android.so. Чтобы система нашла нужный
 * машинный код по вызову native-метода, вызывающий Kotlin-класс должен
 * называться ТОЧНО так же, как класс в оригинальном приложении — иначе
 * JVM просто не найдёт соответствующий символ при загрузке библиотеки.
 *
 * Сигнатуры методов восстановлены не наугад, а вычитаны из method_ids
 * таблицы classes.dex оригинального TrustTunnel_1_2_0.xapk путём разбора
 * бинарного DEX-формата (см. dex_methods.py в истории анализа).
 */

interface VpnClientListener {
    fun onStateChanged(state: Int)
    fun onConnectionInfo(info: String)
    fun protectSocket(fd: Int): Boolean
    fun verifyCertificate(cert: ByteArray, chain: List<ByteArray>): Boolean
}

class VpnClient(
    private val config: String,
    private val listener: VpnClientListener
) {
    private var handle: Long = 0

    companion object {
        init {
            System.loadLibrary("trusttunnel_android")
        }

        @JvmStatic
        external fun excludeCidr(routes: Array<String>, exclude: Array<String>): Array<String>

        @JvmStatic
        external fun setSystemDnsServersNative(servers: Array<String>, fallback: Array<String>): Boolean
    }

    init {
        handle = createNative(config)
    }

    /**
     * Запускает движок поверх уже созданного TUN-интерфейса.
     * @param pfd файловый дескриптор, полученный из VpnService.Builder().establish()
     *            в НАШЕМ собственном VpnService — библиотека сама трафик не
     *            перехватывает, ей нужно явно отдать готовый интерфейс.
     *
     * ВАЖНО: pfd нельзя закрывать вручную после передачи сюда — держите
     * ссылку на него в вызывающем VpnService, пока туннель активен, и
     * закрывайте только после close()/stop(), иначе система закроет TUN.
     */
    fun start(pfd: ParcelFileDescriptor): Boolean = startNative(handle, pfd.fd)

    fun stop() = stopNative(handle)

    fun close() {
        if (handle != 0L) {
            destroyNative(handle)
            handle = 0
        }
    }

    fun notifyNetworkChange(connected: Boolean) = notifyNetworkChangeNative(handle, connected)

    // --- вызывается нативной библиотекой обратно в Kotlin (callback-мост) ---
    fun onStateChanged(state: Int) = listener.onStateChanged(state)
    fun onConnectionInfo(info: String) = listener.onConnectionInfo(info)
    fun protectSocket(fd: Int): Boolean = listener.protectSocket(fd)
    fun verifyCertificate(cert: ByteArray, chain: List<ByteArray>): Boolean =
        listener.verifyCertificate(cert, chain)

    private external fun createNative(config: String): Long
    private external fun destroyNative(handle: Long)
    private external fun startNative(handle: Long, fd: Int): Boolean
    private external fun stopNative(handle: Long)
    private external fun notifyNetworkChangeNative(handle: Long, connected: Boolean)
}

object DeepLink {
    init {
        System.loadLibrary("trusttunnel_android")
    }

    /** Декодирует tt://-ссылку в конфиг (по всем признакам — TOML-текст). */
    external fun decode(link: String): String
}
