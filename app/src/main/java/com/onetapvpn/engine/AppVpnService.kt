package com.onetapvpn.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.onetapvpn.dispatcher.EngineDispatcher
import com.onetapvpn.model.Engine
import com.onetapvpn.model.ServerProfile
import com.onetapvpn.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppVpnService : VpnService() {

    companion object {
        private const val TAG = "AppVpnService"
        private const val CHANNEL_ID = "onetap_vpn"
        private const val NOTIFICATION_ID = 1

        private const val EXTRA_ENGINE = "engine"
        private const val EXTRA_LINK = "raw_link"
        private const val EXTRA_LABEL = "label"
        private const val ACTION_DISCONNECT = "com.onetapvpn.action.DISCONNECT"

        fun start(context: Context, profile: ServerProfile) {
            val intent = Intent(context, AppVpnService::class.java).apply {
                putExtra(EXTRA_ENGINE, profile.engine.name)
                putExtra(EXTRA_LINK, profile.rawLink)
                putExtra(EXTRA_LABEL, profile.label)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AppVpnService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            context.startService(intent)
        }
    }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job)

    private lateinit var dispatcher: EngineDispatcher

    override fun onCreate() {
        super.onCreate()
        dispatcher = EngineDispatcher(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            stopTunnel()
            return START_NOT_STICKY
        }

        val engineName = intent?.getStringExtra(EXTRA_ENGINE)
        val link = intent?.getStringExtra(EXTRA_LINK)
        val label = intent?.getStringExtra(EXTRA_LABEL) ?: "OneTap VPN"

        if (engineName == null || link == null) {
            Log.w(TAG, "onStartCommand без профиля — останавливаемся")
            stopSelf()
            return START_NOT_STICKY
        }

        val profile = ServerProfile(
            engine = Engine.valueOf(engineName),
            rawLink = link,
            label = label
        )

        startForeground(NOTIFICATION_ID, buildNotification("Подключение…"))
        startTunnel(profile)
        return START_STICKY
    }

    private fun startTunnel(profile: ServerProfile) {
        scope.launch {
            try {
                dispatcher.connect(profile)
                updateNotification("Подключено: ${profile.label}")
                Log.i(TAG, "Подключено через ${profile.engine}")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка подключения", e)
                updateNotification("Ошибка: ${e.message}")
                // Не убиваем сервис сразу, чтобы пользователь успел увидеть
                // уведомление об ошибке в статусе foreground-сервиса.
            }
        }
    }

    private fun stopTunnel() {
        scope.launch {
            dispatcher.disconnect()
            stopForeground(true)
            stopSelf()
        }
    }

    override fun onRevoke() {
        // Система/пользователь отозвали VPN-разрешение извне (например,
        // включили другой VPN) — обязаны аккуратно закрыть свой туннель.
        stopTunnel()
        super.onRevoke()
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "OneTap VPN", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OneTap VPN")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(text))
    }
}
