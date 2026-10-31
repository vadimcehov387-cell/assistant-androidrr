package ru.vkassistant.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground monitor. All network work runs on one worker thread.
 * The AtomicBoolean prevents duplicate loops when start commands arrive quickly.
 */
class MonitorService : Service() {
    private val running = AtomicBoolean(false)
    @Volatile private var worker: Thread? = null
    @Volatile private var generation: Long = 0L
    private val channelId = "vk_monitor"
    private val notificationId = 10

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopMonitoring()
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startForeground(notificationId, notification("Мониторинг запускается"))
        if (running.compareAndSet(false, true)) {
            Prefs.setRunning(this, true)
            val runId = ++generation
            worker = Thread({ monitorLoop(runId) }, "VkAssistantMonitor-$runId").also { it.start() }
        }
        return START_STICKY
    }

    private fun monitorLoop(runId: Long) {
        try {
            while (isActive(runId)) {
                try {
                    checkCommunities(runId)
                } catch (error: Exception) {
                    Prefs.addHistory(this, "ERROR | ${safeMessage(error)}")
                    updateNotification("Ошибка мониторинга: ${safeMessage(error).take(90)}")
                }

                try {
                    Thread.sleep(POLL_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    if (!isActive(runId)) break
                }
            }
        } finally {
            if (runId == generation) {
                running.set(false)
                Prefs.setRunning(this, false)
                worker = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun checkCommunities(runId: Long) {
        val vkToken = Prefs.get(this, "vk_key")
        val tgToken = Prefs.get(this, "tg_token")
        val chatId = Prefs.get(this, "tg_chat")

        if (vkToken.isBlank() || tgToken.isBlank() || chatId.isBlank()) {
            updateNotification("Заполни настройки VK и Telegram")
            return
        }

        val communities = Prefs.communities(this)
        if (communities.isEmpty()) {
            updateNotification("Добавь хотя бы одно сообщество")
            return
        }

        for (community in communities) {
            if (!isActive(runId)) break
            try {
                processCommunity(runId, community, vkToken, tgToken, chatId)
            } catch (error: Exception) {
                Prefs.addHistory(
                    this,
                    "ERROR | ${community.title} | ${safeMessage(error)}"
                )
            }
        }
        updateNotification("Проверено сообществ: ${communities.size} · интервал 30 сек")
    }

    private fun processCommunity(
        runId: Long,
        community: Community,
        vkToken: String,
        tgToken: String,
        chatId: String
    ) {
        val posts = Api.latestPosts(community.ownerId, vkToken)
        val freshPosts = posts.filter { post ->
            val date = post.optLong("date")
            val id = post.optLong("id")
            date > community.lastDate ||
                (date == community.lastDate && id > community.lastId)
        }

        for (post in freshPosts) {
            if (!isActive(runId)) break
            processPost(runId, community, post, vkToken, tgToken, chatId)
        }
    }

    private fun processPost(
        runId: Long,
        community: Community,
        post: JSONObject,
        vkToken: String,
        tgToken: String,
        chatId: String
    ) {
        if (!isActive(runId)) return
        val postId = post.optLong("id")
        val postDate = post.optLong("date")
        if (postId <= 0L || postDate <= 0L) return

        val postText = post.optString("text").trim()
        val link = "https://vk.com/wall${community.ownerId}_$postId"
        val blockedWords = Prefs.get(
            this,
            "filters",
            "казино,ставки,розыгрыш,политика,выборы"
        ).split(",")
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() }

        val normalizedText = postText.lowercase(Locale.ROOT)
        val filtered = postText.isBlank() || blockedWords.any(normalizedText::contains)
        if (filtered) {
            Prefs.addHistory(this, "FILTERED | ${community.title} | $link")
            updateCursor(community.ownerId, post)
            return
        }

        val style = Prefs.get(this, "style", "friendly")
        val aiStyle = when (style) {
            "expert" -> "экспертный и профессиональный"
            "intellectual" -> "интеллектуальный и вдумчивый"
            "ironic" -> "лёгкая доброжелательная ирония"
            "neutral" -> "нейтральный и спокойный"
            else -> "дружелюбный и естественный"
        }

        val aiKey = Prefs.get(this, "ai_key")
        var aiGenerated = false
        val comment = try {
            AiDrafts.generateSingle(
                postText,
                aiKey,
                Prefs.get(this, "ai_model", "gpt-4o-mini"),
                aiStyle
            )?.also { aiGenerated = true } ?: makeComment(style)
        } catch (error: Exception) {
            Prefs.addHistory(this, "AI_FALLBACK | ${safeMessage(error)}")
            makeComment(style)
        }

        var published = false
        var publishStatus = "Подготовлен черновик. Автопубликация выключена."
        if (community.autoPublish && !aiGenerated) {
            publishStatus = "Публикация отменена: ИИ не подготовил комментарий. Черновик отправлен."
        } else if (community.autoPublish && isQuietNow()) {
            publishStatus = "Тихие часы: публикация приостановлена. Черновик отправлен."
        } else if (community.autoPublish) {
            if (reserveAutoPublishSlot()) {
                try {
                    val commentId = Api.createComment(
                        community.ownerId,
                        postId,
                        comment,
                        vkToken,
                        "vkassistant_${community.ownerId}_$postId"
                    )
                    published = true
                    publishStatus = "Комментарий опубликован. ID: $commentId"
                    Prefs.addHistory(
                        this,
                        "PUBLISHED | ${community.title} | $link | comment=$commentId"
                    )
                } catch (error: Exception) {
                    publishStatus = "VK не принял комментарий: ${safeMessage(error)}. Черновик отправлен."
                    Prefs.addHistory(
                        this,
                        "PUBLISH_ERROR | ${community.title} | ${safeMessage(error)}"
                    )
                }
            } else {
                publishStatus = "Защитный лимит достигнут. Комментарий не опубликован."
                Prefs.addHistory(this, "RATE_LIMIT | ${community.title} | $link")
            }
        }

        val telegramMessage = buildString {
            append(if (published) "Комментарий опубликован" else "Новая публикация")
            append(": ${community.title}\n$link\n\n")
            append("Комментарий:\n$comment\n\n")
            append(publishStatus)
        }

        // Stop between network operations when the user disables monitoring.
        if (!isActive(runId)) return
        // Advance the cursor only after Telegram confirms delivery. If delivery
        // fails, this post is retried on the next poll instead of being lost.
        Api.sendTelegram(tgToken, chatId, telegramMessage)
        updateCursor(community.ownerId, post)
        if (!published) Prefs.addHistory(this, "DRAFT | ${community.title} | $link")
    }

    private fun stopMonitoring() {
        Prefs.setRunning(this, false)
        generation++
        running.set(false)
        worker?.interrupt()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun updateCursor(ownerId: Long, post: JSONObject) {
        val communities = Prefs.communities(this)
        val index = communities.indexOfFirst { it.ownerId == ownerId }
        if (index < 0) return

        val previous = communities[index]
        val date = post.optLong("date")
        val id = post.optLong("id")
        if (date < previous.lastDate || (date == previous.lastDate && id <= previous.lastId)) {
            return
        }
        communities[index] = previous.copy(lastDate = date, lastId = id)
        Prefs.saveCommunities(this, communities)
    }

    // Limit: at most one auto-comment per minute and ten per clock hour.
    private fun reserveAutoPublishSlot(): Boolean {
        val now = System.currentTimeMillis()
        val last = Prefs.get(this, "auto_last_ms", "0").toLongOrNull() ?: 0L
        if (now - last < MIN_AUTO_PUBLISH_INTERVAL_MS) return false

        val hour = now / MILLIS_PER_HOUR
        val storedHour = Prefs.get(this, "auto_hour", "-1").toLongOrNull() ?: -1L
        val count = if (storedHour == hour) {
            Prefs.get(this, "auto_hour_count", "0").toIntOrNull() ?: 0
        } else {
            0
        }
        if (count >= MAX_AUTO_PUBLISH_PER_HOUR) return false

        Prefs.put(this, "auto_last_ms", now.toString())
        Prefs.put(this, "auto_hour", hour.toString())
        Prefs.put(this, "auto_hour_count", (count + 1).toString())
        return true
    }

    private fun makeComment(style: String): String = when (style) {
        "expert" -> "Интересная тема. Какие практические выводы из этого можно сделать?"
        "intellectual" -> "Здесь есть над чем поразмышлять. Спасибо, что подняли эту тему."
        "ironic" -> "Вот это уже интересный поворот событий."
        "neutral" -> "Спасибо за публикацию и полезную информацию."
        else -> "Интересная публикация, спасибо, что поделились."
    }

    private fun isQuietNow(): Boolean {
        val startText = Prefs.get(this, "quiet_start").trim()
        val endText = Prefs.get(this, "quiet_end").trim()
        if (startText.isBlank() || endText.isBlank()) return false

        return try {
            val formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
            val start = LocalTime.parse(startText, formatter)
            val end = LocalTime.parse(endText, formatter)
            val now = LocalTime.now()
            if (start == end) {
                true // Equal endpoints mean quiet mode all day.
            } else if (start.isBefore(end)) {
                !now.isBefore(start) && now.isBefore(end)
            } else {
                !now.isBefore(start) || now.isBefore(end)
            }
        } catch (_: DateTimeParseException) {
            Prefs.addHistory(this, "SETTINGS_WARNING | Некорректный формат тихих часов. Ожидается HH:mm.")
            false
        }
    }

    private fun safeMessage(error: Exception): String {
        var message = (error.message ?: error.javaClass.simpleName)
            .replace(Regex("[\\r\\n]+"), " ")
        message = message
            .replace(Regex("(?i)bot\\d+:[A-Za-z0-9_-]{20,}"), "[СКРЫТЫЙ TELEGRAM TOKEN]")
            .replace(Regex("(?i)Bearer\\s+[^\\s,;]+"), "Bearer [СКРЫТЫЙ КЛЮЧ]")
            .replace(Regex("(?i)access_token=[^&\\s]+"), "access_token=[СКРЫТ]")
            .replace(Regex("(?i)sk-[A-Za-z0-9_-]{16,}"), "[СКРЫТЫЙ API KEY]")
        return message.take(240)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "VK monitoring",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("VK Assistant 3.3")
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(notificationId, notification(text))
    }

    private fun isActive(runId: Long): Boolean =
        running.get() && runId == generation && Prefs.isRunning(this)

    override fun onDestroy() {
        generation++
        running.set(false)
        worker?.interrupt()
        Prefs.setRunning(this, false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "ru.vkassistant.mobile.STOP"
        private const val POLL_INTERVAL_MS = 30_000L
        private const val MIN_AUTO_PUBLISH_INTERVAL_MS = 60_000L
        private const val MILLIS_PER_HOUR = 3_600_000L
        private const val MAX_AUTO_PUBLISH_PER_HOUR = 10
    }
}
