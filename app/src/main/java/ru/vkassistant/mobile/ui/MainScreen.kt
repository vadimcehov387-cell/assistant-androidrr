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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.vkassistant.mobile.Community
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext

private enum class AssistantTab(val label: String, val symbol: String, val route: String) {
    HOME("Главная", "⌂", "home"),
    COMMUNITIES("Группы", "▦", "communities"),
    AI("ИИ", "✦", "ai"),
    HISTORY("История", "◷", "history"),
    SETTINGS("Настройки", "⚙", "settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantMainScreen(
    isMonitoring: Boolean,
    connectedServices: Int,
    communityCount: Int,
    communities: List<Community>,
    recentEvents: List<String>,
    history: List<String>,
    settings: Map<String, String>,
    onSaveSettings: (Map<String, String>) -> Unit,
    onAddCommunity: (String) -> Unit,
    onRemoveCommunity: (Community) -> Unit,
    onToggleAutoPublish: (Community, Boolean) -> Unit,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tabs = AssistantTab.values()
    val safeTabIndex = selectedTabIndex.coerceIn(tabs.indices)

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("VK Assistant", fontWeight = FontWeight.Bold)
                    Text("Версия 3.3", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            })
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = safeTabIndex == index,
                        onClick = { selectedTabIndex = index },
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
            when (tabs[safeTabIndex]) {
                AssistantTab.HOME -> HomeDashboard(
                    isMonitoring, connectedServices, communityCount, recentEvents,
                    onStartMonitoring, onStopMonitoring
                )
                AssistantTab.COMMUNITIES -> CommunitiesPage(
                    communities, onAddCommunity, onRemoveCommunity, onToggleAutoPublish
                )
                AssistantTab.AI -> AiPage(
                    apiKey = settings["ai_key"].orEmpty(),
                    model = settings["ai_model"].orEmpty().ifBlank { "gpt-4o-mini" },
                    style = settings["style"].orEmpty().ifBlank { "friendly" }
                )
                AssistantTab.HISTORY -> HistoryPage(history)
                AssistantTab.SETTINGS -> SettingsPage(settings, onSaveSettings)
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
            Column(Modifier.padding(top = 18.dp)) {
                Text("Панель управления", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold)
                Text("Мониторинг публикаций и подготовка ответов",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )) {
                Column(Modifier.padding(18.dp)) {
                    Text("СОСТОЯНИЕ МОНИТОРИНГА", style = MaterialTheme.typography.labelMedium)
                    Text(if (isMonitoring) "Мониторинг активен" else "Мониторинг остановлен",
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(if (isMonitoring) "Сервис проверяет публикации."
                        else "Запусти мониторинг после настройки подключений.")
                    Spacer(Modifier.height(12.dp))
                    if (isMonitoring) OutlinedButton(onClick = onStop, Modifier.fillMaxWidth()) {
                        Text("Остановить мониторинг")
                    } else Button(onClick = onStart, Modifier.fillMaxWidth()) {
                        Text("Запустить мониторинг")
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard("Подключения", connectedServices.toString(), Modifier.weight(1f))
                MetricCard("Сообщества", communityCount.toString(), Modifier.weight(1f))
            }
        }
        item { Text("Последние события", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold) }
        if (recentEvents.isEmpty()) item {
            Card(Modifier.fillMaxWidth()) { Text("Здесь появятся события мониторинга.",
                Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else items(recentEvents.take(8)) { event -> EventCard(event) }
        item {
            Text("Автопубликация выключена по умолчанию. Включай её отдельно для каждого сообщества.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp))
        }
    }
}

@Composable
private fun CommunitiesPage(
    communities: List<Community>,
    onAdd: (String) -> Unit,
    onRemove: (Community) -> Unit,
    onToggle: (Community, Boolean) -> Unit
) {
    var link by rememberSaveable { mutableStateOf("") }
    var confirmRemove by remember { mutableStateOf<Community?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Сообщества VK", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text("Добавляй публичные сообщества по короткой ссылке или адресу vk.com.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(link, { link = it }, Modifier.fillMaxWidth(),
                label = { Text("Ссылка на сообщество") },
                placeholder = { Text("https://vk.com/example") }, singleLine = true)
            Button(onClick = { onAdd(link.trim()); link = "" },
                enabled = link.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text("Добавить сообщество")
            }
        }
        if (communities.isEmpty()) item {
            Card(Modifier.fillMaxWidth()) { Text("Пока нет добавленных сообществ.",
                Modifier.padding(16.dp)) }
        }
        items(communities, key = { it.ownerId }) { community ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(community.title, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text("vk.com/${community.screenName}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()) {
                        Text("Автопубликация")
                        Switch(checked = community.autoPublish,
                            onCheckedChange = { onToggle(community, it) })
                    }
                    Text("Включай только если имеешь право публиковать комментарии от своего аккаунта.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { confirmRemove = community }) {
                        Text("Удалить")
                    }
                }
            }
        }
    }
    confirmRemove?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Удалить сообщество?") },
            text = { Text("«${item.title}» будет удалено из списка мониторинга.") },
            confirmButton = { TextButton(onClick = { onRemove(item); confirmRemove = null }) {
                Text("Удалить") } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun AiPage(apiKey: String, model: String, style: String) {
    var post by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LazyColumn(Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("ИИ-помощник", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text("Вставь текст публикации — ИИ подготовит комментарий для проверки.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(post, { post = it; error = "" }, Modifier.fillMaxWidth(),
                label = { Text("Текст публикации") }, minLines = 5)
            Button(
                onClick = {
                    loading = true
                    result = ""
                    error = ""
                    scope.launch {
                        try {
                            val draft = kotlinx.coroutines.withContext(Dispatchers.IO) {
                                ru.vkassistant.mobile.AiDrafts.generateSingle(
                                    post, apiKey, model, style
                                )
                            }
                            result = draft.orEmpty()
                            if (result.isBlank()) error = "Не удалось получить ответ. Проверь API-ключ и модель."
                        } catch (e: Exception) {
                            error = e.message ?: "Ошибка генерации"
                        } finally { loading = false }
                    }
                },
                enabled = post.isNotBlank() && apiKey.isNotBlank() && !loading,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (loading) "Генерирую…" else "Создать комментарий") }
            if (apiKey.isBlank()) Text("Сначала добавь OpenAI API-ключ в настройках.",
                color = MaterialTheme.colorScheme.error)
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        }
        if (result.isNotBlank()) item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Черновик", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    Text(result)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("AI draft", result))
                        }) { Text("Копировать") }
                        TextButton(onClick = { result = "" }) { Text("Очистить") }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryPage(history: List<String>) {
    LazyColumn(Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("История событий", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text("Последние записи мониторинга, фильтрации и отправки уведомлений.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (history.isEmpty()) item {
            Card(Modifier.fillMaxWidth()) { Text("История пока пуста.", Modifier.padding(16.dp)) }
        }
        items(history, key = { it.hashCode().toString() + it.take(24) }) { EventCard(it) }
    }
}

@Composable
private fun SettingsPage(
    current: Map<String, String>,
    onSave: (Map<String, String>) -> Unit
) {
    var vkKey by rememberSaveable(current["vk_key"]) { mutableStateOf(current["vk_key"].orEmpty()) }
    var tgToken by rememberSaveable(current["tg_token"]) { mutableStateOf(current["tg_token"].orEmpty()) }
    var tgChat by rememberSaveable(current["tg_chat"]) { mutableStateOf(current["tg_chat"].orEmpty()) }
    var aiKey by rememberSaveable(current["ai_key"]) { mutableStateOf(current["ai_key"].orEmpty()) }
    var aiModel by rememberSaveable(current["ai_model"]) { mutableStateOf(current["ai_model"].orEmpty().ifBlank { "gpt-4o-mini" }) }
    var style by rememberSaveable(current["style"]) { mutableStateOf(current["style"].orEmpty().ifBlank { "friendly" }) }
    var filters by rememberSaveable(current["filters"]) { mutableStateOf(current["filters"].orEmpty().ifBlank { "казино,ставки,розыгрыш,политика,выборы" }) }
    var quietStart by rememberSaveable(current["quiet_start"]) { mutableStateOf(current["quiet_start"].orEmpty()) }
    var quietEnd by rememberSaveable(current["quiet_end"]) { mutableStateOf(current["quiet_end"].orEmpty()) }

    LazyColumn(Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Настройки", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text("Ключи хранятся в зашифрованном локальном хранилище.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { SettingField("Токен VK", vkKey, { vkKey = it }, secret = true) }
        item { SettingField("Токен Telegram", tgToken, { tgToken = it }, secret = true) }
        item { SettingField("Telegram Chat ID", tgChat, { tgChat = it }) }
        item { SettingField("OpenAI API-ключ", aiKey, { aiKey = it }, secret = true) }
        item { SettingField("Модель ИИ", aiModel, { aiModel = it }) }
        item {
            Text("Стиль комментариев", fontWeight = FontWeight.SemiBold)
            val styles = listOf("friendly", "neutral", "expert", "intellectual", "ironic")
            styles.forEach { value ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(when (value) {
                        "friendly" -> "Дружелюбный"
                        "neutral" -> "Нейтральный"
                        "expert" -> "Экспертный"
                        "intellectual" -> "Интеллектуальный"
                        else -> "С лёгкой иронией"
                    })
                    androidx.compose.material3.RadioButton(
                        selected = style == value, onClick = { style = value }
                    )
                }
            }
        }
        item { SettingField("Фильтры через запятую", filters, { filters = it }, minLines = 3) }
        item { SettingField("Тихие часы — начало (HH:mm)", quietStart, { quietStart = it }) }
        item { SettingField("Тихие часы — конец (HH:mm)", quietEnd, { quietEnd = it }) }
        item {
            Button(onClick = {
                onSave(mapOf(
                    "vk_key" to vkKey.trim(), "tg_token" to tgToken.trim(),
                    "tg_chat" to tgChat.trim(), "ai_key" to aiKey.trim(),
                    "ai_model" to aiModel.trim(), "style" to style,
                    "filters" to filters, "quiet_start" to quietStart.trim(),
                    "quiet_end" to quietEnd.trim()
                ))
            }, modifier = Modifier.fillMaxWidth()) { Text("Сохранить настройки") }
        }
        item {
            Text("Автопубликация настраивается отдельно в карточке каждого сообщества.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    secret: Boolean = false,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = minLines == 1,
        minLines = minLines,
        visualTransformation = if (secret)
            androidx.compose.ui.text.input.PasswordVisualTransformation()
        else androidx.compose.ui.text.input.VisualTransformation.None
    )
}

@Composable
private fun EventCard(text: String) {
    Card(Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
