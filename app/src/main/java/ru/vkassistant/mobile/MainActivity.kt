package ru.vkassistant.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import ru.vkassistant.mobile.ui.AssistantMainScreen
import ru.vkassistant.mobile.ui.AssistantTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()

        setContent {
            AssistantTheme {
                var refreshKey by remember { mutableIntStateOf(0) }
                val events = remember { mutableStateListOf<String>() }

                LaunchedEffect(Unit) {
                    while (true) {
                        refreshKey++
                        events.clear()
                        events.addAll(
                            Prefs.get(this@MainActivity, "history", "")
                                .lineSequence()
                                .filter { it.isNotBlank() }
                                .take(8)
                                .toList()
                        )
                        delay(2_000)
                    }
                }

                val communities = Prefs.communities(this@MainActivity)
                // refreshKey intentionally participates in recomposition so the
                // dashboard reflects service status and preference changes.
                refreshKey
                AssistantMainScreen(
                    isMonitoring = Prefs.isRunning(this@MainActivity),
                    connectedServices = listOf(
                        Prefs.get(this@MainActivity, "vk_key"),
                        Prefs.get(this@MainActivity, "tg_token")
                    ).count { it.isNotBlank() },
                    communityCount = communities.size,
                    recentEvents = events.toList(),
                    onStartMonitoring = { startMonitoring() },
                    onStopMonitoring = { stopMonitoring() },
                    onOpenTab = { route ->
                        if (route != "home") {
                            Toast.makeText(
                                this@MainActivity,
                                "Раздел «$route» пока находится в разработке",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }
        }
    }

    private fun startMonitoring() {
        if (Prefs.get(this, "vk_key").isBlank() ||
            Prefs.get(this, "tg_token").isBlank() ||
            Prefs.get(this, "tg_chat").isBlank()
        ) {
            Toast.makeText(
                this,
                "Сначала настрой VK и Telegram. Экран настроек ещё не подключён.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        if (Prefs.communities(this).isEmpty()) {
            Toast.makeText(this, "Добавь хотя бы одно сообщество VK.", Toast.LENGTH_LONG).show()
            return
        }
        val serviceIntent = Intent(this, MonitorService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun stopMonitoring() {
        val serviceIntent = Intent(this, MonitorService::class.java).apply {
            action = MonitorService.ACTION_STOP
        }
        startService(serviceIntent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 12)
        }
    }
}
