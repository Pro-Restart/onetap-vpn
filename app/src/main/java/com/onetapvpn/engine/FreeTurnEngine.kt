package com.onetapvpn.engine

import android.content.Context
import android.util.Log
import com.onetapvpn.model.ServerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * FreeTurn пока частично реализован: сам транспорт FreeTurn — это Go CLI-
 * бинарник (см. анализ libfreeturn-*.so — это исполняемый файл, а не
 * shared-библиотека), который поднимает локальный порт, поверх которого
 * должен работать обычный WireGuard (тот же движок, что и AmneziaWgEngine).
 *
 * ВНИМАНИЕ: полная связка "поднять freeturn-процесс -> прописать его как
 * Endpoint в WireGuard-конфиге -> поднять WireGuard поверх него" пока не
 * собрана в единый пайплайн — этот класс запускает только сам транспорт.
 * Обвязку с WireGuard нужно дописать по аналогии с AmneziaWgEngine.
 */
class FreeTurnEngine(private val context: Context) : VpnEngine {

    companion object {
        private const val TAG = "FreeTurnEngine"
        private const val BINARY_ASSET_NAME = "freeturn_client"
        private const val LOCAL_LISTEN = "127.0.0.1:9000"
    }

    private var process: Process? = null

    override suspend fun connect(profile: ServerProfile) = withContext(Dispatchers.IO) {
        // Ожидаемый формат rawLink для freeturn — см. LinkParser: либо
        // freeturn://-ссылка, либо прямая ссылка на VK-звонок.
        // TODO: распарсить profile.rawLink на составляющие (peer/link/dns) —
        // сейчас это заглушка структуры вызова, реальный парсинг вручную.
        throw NotImplementedError(
            "FreeTurnEngine: транспорт запускается (см. installBinaryIfNeeded), " +
                "но связка с WireGuard поверх него ещё не дописана. " +
                "Нужно: 1) распарсить rawLink на peer/vk-link/dns, " +
                "2) запустить бинарник, 3) поднять WireGuard с Endpoint=$LOCAL_LISTEN " +
                "через тот же GoBackend, что использует AmneziaWgEngine."
        )
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        process?.destroy()
        process = null
    }

    override fun isConnected(): Boolean = process?.isAlive == true

    private fun installBinaryIfNeeded(): File {
        val outFile = File(context.filesDir, BINARY_ASSET_NAME)
        if (!outFile.exists()) {
            context.assets.open(BINARY_ASSET_NAME).use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        if (!outFile.canExecute()) outFile.setExecutable(true, true)
        return outFile
    }
}
