package com.onetapvpn.ui

import android.content.Intent
import android.net.VpnService
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onetapvpn.engine.AppVpnService
import com.onetapvpn.model.ServerProfile
import com.onetapvpn.parser.LinkParser
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat

class MainActivity : ComponentActivity() {

    private var profilesState = mutableStateOf<List<ServerProfile>>(emptyList())
    private var selectedProfileState = mutableStateOf<ServerProfile?>(null)
    private var statusMessageState = mutableStateOf("Загрузите файл со ссылками")

    private var pendingProfile: ServerProfile? = null

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { loadProfilesFrom(it) } }

    // Системный запрос разрешения на создание VPN-интерфейса.
    // БЕЗ этого шага VpnService.Builder().establish() всегда будет падать —
    // именно это было незаметно пропущено раньше (permission на СЕРВИС и
    // permission на VPN-туннель — разные вещи: BIND_VPN_SERVICE защищает
    // сервис, а VpnService.prepare()/этот launcher — это согласие пользователя
    // на сам перехват трафика).
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            pendingProfile?.let { startVpn(it) }
        } else {
            statusMessageState.value = "Разрешение на VPN не получено"
        }
        pendingProfile = null
    }
    companion object {
        private const val REQ_POST_NOTIFICATIONS = 1001
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        requestNotificationPermission()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        profiles = profilesState.value,
                        selectedProfile = selectedProfileState.value,
                        statusMessage = statusMessageState.value,
                        onPickFile = { filePicker.launch("text/plain") },
                        onSelectProfile = { selectedProfileState.value = it },
                        onConnectClick = { requestConnect() }
                    )
                }
            }
        }
    }

    private fun loadProfilesFrom(uri: Uri) {
        try {
            val content = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: run { statusMessageState.value = "Не удалось прочитать файл"; return }

            val parsed = LinkParser.parse(content)
            profilesState.value = parsed
            selectedProfileState.value = parsed.firstOrNull()
            statusMessageState.value = if (parsed.isEmpty())
                "Ни одна ссылка не распознана" else "Распознано профилей: ${parsed.size}"
        } catch (e: Exception) {
            statusMessageState.value = "Ошибка чтения файла: ${e.message}"
        }
    }

    private fun requestConnect() {
        val profile = selectedProfileState.value ?: run {
            Toast.makeText(this, "Выберите профиль", Toast.LENGTH_SHORT).show()
            return
        }

        // Обязательный системный шаг: VpnService.prepare() возвращает Intent,
        // только если разрешение ещё не выдано этому приложению — если null,
        // можно стартовать сразу.
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            pendingProfile = profile
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            startVpn(profile)
        }
    }

    private fun startVpn(profile: ServerProfile) {
        AppVpnService.start(this, profile)
        statusMessageState.value = "Запуск подключения через ${profile.engine}…"
    }
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "onetap_vpn",
                "VPN",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    REQ_POST_NOTIFICATIONS
                )
            }
        }
    }
}

@Composable
fun MainScreen(
    profiles: List<ServerProfile>,
    selectedProfile: ServerProfile?,
    statusMessage: String,
    onPickFile: () -> Unit,
    onSelectProfile: (ServerProfile) -> Unit,
    onConnectClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("OneTap VPN", fontSize = 26.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp))

        OutlinedButton(onClick = onPickFile, modifier = Modifier.fillMaxWidth()) {
            Text("Загрузить файл со ссылками (.txt)")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (profiles.isNotEmpty()) {
            Text("Выберите сервер:", fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Start))
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(profiles) { profile ->
                    ProfileRow(
                        profile = profile,
                        isSelected = profile == selectedProfile,
                        onClick = { onSelectProfile(profile) }
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        Text(statusMessage, modifier = Modifier.padding(vertical = 12.dp))

        Button(
            onClick = onConnectClick,
            enabled = selectedProfile != null,
            modifier = Modifier.fillMaxWidth().height(64.dp)
        ) {
            Text(
                text = selectedProfile?.let { "Подключиться (${it.engine})" } ?: "Подключиться",
                fontSize = 18.sp
            )
        }
    }
}

@Composable
fun ProfileRow(profile: ServerProfile, isSelected: Boolean, onClick: () -> Unit) {
    val bg = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    Surface(color = bg, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), onClick = onClick) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(profile.label, fontWeight = FontWeight.Medium)
            Text(profile.engine.name, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
