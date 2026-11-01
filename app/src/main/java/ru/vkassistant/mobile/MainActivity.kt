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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import ru.vkassistant.mobile.ui.AssistantMainScreen
import ru.vkassistant.mobile.ui.AssistantTheme

class MainActivity : ComponentActivity() {
    private val settingKeys = listOf(
        "vk_key", "vk_profile", "tg_token", "tg_chat", "ai_key", "ai_model",
        "style", "filters", "quiet_start", "quiet_end"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()

        setContent {
            AssistantTheme {
                var refreshKey by remember { mutableIntStateOf(0) }
                LaunchedEffect(Unit) {
                    while (true) {
                        refreshKey++
                        delay(10_000)
                    }
                }

                val settings = remember(refreshKey) {
                    settingKeys.associateWith { Prefs.get(this@MainActivity, it) }
                }
                val communities = remember(refreshKey) { Prefs.communities(this@MainActivity).toList() }
                val history = remember(refreshKey) {
                    Prefs.get(this@MainActivity, "history", "")
                        .lineSequence().filter { it.isNotBlank() }.take(60).toList()
                }
                val events = history.take(8)

                AssistantMainScreen(
                    isMonitoring = Prefs.isRunning(this@MainActivity),
                    connectedServices = listOf(
                        settings["vk_key"], settings["tg_token"]
                    ).count { !it.isNullOrBlank() },
                    communityCount = communities.size,
                    communities = communities,
                    recentEvents = events,
                    history = history,
                    settings = settings,
                    onVerifyVkToken = { token ->
                        Thread({
                            try {
                                val profile = Api.verifyVkToken(token)
                                Prefs.put(this@MainActivity, "vk_key", token)
                                Prefs.put(this@MainActivity, "vk_profile", profile)
                                runOnUiThread {
                                    Toast.makeText(this@MainActivity, "VK подключён: $profile", Toast.LENGTH_LONG).show()
                                    refreshKey++
                                }
                            } catch (e: Exception) {
                                runOnUiThread {
                                    Toast.makeText(this@MainActivity, e.message ?: "Не удалось проверить токен", Toast.LENGTH_LONG).show()
                                }
                            }
                        }, "VerifyVkToken").start()
                    },
                    onSaveSettings = { values ->
                        values.forEach { (key, value) -> Prefs.put(this@MainActivity, key, value) }
                        Toast.makeText(this@MainActivity, "Настройки сохранены", Toast.LENGTH_SHORT).show()
                        refreshKey++
                    },
                    onAddCommunity = { link ->
                        Thread({
                            try {
                                val token = Prefs.get(this@MainActivity, "vk_key")
                                val community = Api.resolveCommunity(link, token)
                                val current = Prefs.communities(this@MainActivity)
                                if (current.any { it.ownerId == community.ownerId }) {
                                    throw IllegalArgumentException("Это сообщество уже добавлено")
                                }
                                Prefs.saveCommunities(this@MainActivity, current + community)
                                runOnUiThread {
                                    Toast.makeText(this@MainActivity, "Сообщество добавлено", Toast.LENGTH_SHORT).show()
                                    refreshKey++
                                }
                            } catch (e: Exception) {
                                runOnUiThread {
                                    Toast.makeText(
                                        this@MainActivity,
                                        e.message ?: "Не удалось добавить сообщество",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }, "AddVkCommunity").start()
                    },
                    onRemoveCommunity = { community ->
                        Prefs.saveCommunities(
                            this@MainActivity,
                            Prefs.communities(this@MainActivity).filterNot { it.ownerId == community.ownerId }
                        )
                        refreshKey++
                    },
                    onToggleAutoPublish = { community, enabled ->
                        val updated = Prefs.communities(this@MainActivity).map {
                            if (it.ownerId == community.ownerId) it.copy(autoPublish = enabled) else it
                        }
                        Prefs.saveCommunities(this@MainActivity, updated)
                        Prefs.addHistory(
                            this@MainActivity,
                            "SETTINGS | ${community.title} | автопубликация ${if (enabled) "включена" else "выключена"}"
                        )
                        refreshKey++
                    },
                    onStartMonitoring = { startMonitoring() },
                    onStopMonitoring = { stopMonitoring() }
                )
            }
        }
    }

    private fun startMonitoring() {
        if (Prefs.get(this, "vk_key").isBlank() ||
            Prefs.get(this, "tg_token").isBlank() ||
            Prefs.get(this, "tg_chat").isBlank()
        ) {
            Toast.makeText(this, "Сначала заполни настройки VK и Telegram.", Toast.LENGTH_LONG).show()
            return
        }
        if (Prefs.communities(this).isEmpty()) {
            Toast.makeText(this, "Сначала добавь сообщество VK.", Toast.LENGTH_LONG).show()
            return
        }
        ContextCompat.startForegroundService(this, Intent(this, MonitorService::class.java))
    }

    private fun stopMonitoring() {
        val intent = Intent(this, MonitorService::class.java).apply {
            action = MonitorService.ACTION_STOP
        }
        startService(intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 12)
        }
    }
}
