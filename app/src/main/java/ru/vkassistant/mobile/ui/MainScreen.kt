package ru.vkassistant.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private enum class AssistantTab(val label: String, val symbol: String) {
    HOME("Главная", "⌂"),
    COMMUNITIES("Группы", "▦"),
    AI("ИИ", "✦"),
    HISTORY("История", "◷"),
    SETTINGS("Настройки", "⚙")
}

/**
 * Material 3 UI scaffold for VK Assistant 3.3.
 *
 * This screen is intentionally UI-only until the secure 3.1 project is
 * integrated. Connect callbacks to the existing service and encrypted
 * preferences before shipping.
 */
@Composable
fun AssistantMainScreen(
    isMonitoring: Boolean = false,
    connectedServices: Int = 0,
    communityCount: Int = 0,
    recentEvents: List<String> = emptyList(),
    onStartMonitoring: () -> Unit = {},
    onStopMonitoring: () -> Unit = {},
    onOpenTab: (String) -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = AssistantTab.entries

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("VK Assistant", fontWeight = FontWeight.Bold)
                        Text(
                            "Версия 3.3",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            onOpenTab(tab.name.lowercase())
                        },
                        icon = { Text(tab.symbol, style = MaterialTheme.typography.titleMedium) },
                        label = { Text(tab.label, maxLines = 1) }
                    )
                }
            }
        }
    ) { padding ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(padding),
            color = MaterialTheme.colorScheme.background
        ) {
            when (tabs[selectedTab]) {
                AssistantTab.HOME -> HomeDashboard(
                    isMonitoring = isMonitoring,
                    connectedServices = connectedServices,
                    communityCount = communityCount,
                    recentEvents = recentEvents,
                    onStart = onStartMonitoring,
                    onStop = onStopMonitoring
                )
                AssistantTab.COMMUNITIES -> PlaceholderPage(
                    title = "Сообщества",
                    subtitle = "Управление группами и отдельными разрешениями на автопубликацию."
                )
                AssistantTab.AI -> PlaceholderPage(
                    title = "ИИ-помощник",
                    subtitle = "Создание комментариев, проверка и сохранение черновиков."
                )
                AssistantTab.HISTORY -> PlaceholderPage(
                    title = "История",
                    subtitle = "Журнал мониторинга, генерации и отправленных уведомлений."
                )
                AssistantTab.SETTINGS -> PlaceholderPage(
                    title = "Настройки",
                    subtitle = "Подключения, фильтры, тихие часы, тема и безопасность."
                )
            }
        }
    }
}

@Composable
private fun HomeDashboard(
    isMonitoring: Boolean,
    connectedServices: Int,
    communityCount: Int,
    recentEvents: List<String>,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(top = 18.dp, bottom = 2.dp)) {
                Text("Панель управления", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Мониторинг публикаций и подготовка ответов",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("СОСТОЯНИЕ МОНИТОРИНГА", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (isMonitoring) "Мониторинг активен" else "Мониторинг остановлен",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (isMonitoring) "Сервис проверяет доступные публикации." else "Запусти мониторинг, когда подключения настроены.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(14.dp))
                    if (isMonitoring) {
                        OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                            Text("Остановить мониторинг")
                        }
                    } else {
                        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                            Text("Запустить мониторинг")
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("Подключения", "$connectedServices", Modifier.weight(1f))
                MetricCard("Сообщества", "$communityCount", Modifier.weight(1f))
            }
        }
        item {
            Text("Последние события", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (recentEvents.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Здесь появятся события мониторинга и работы ИИ.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(recentEvents.take(8)) { event ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(event, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Text(
                "Автопубликация выключена по умолчанию. Включай её отдельно для сообщества и только при наличии разрешения.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 18.dp)
            )
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlaceholderPage(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Раздел будет подключён к функциям приложения на следующем этапе.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
