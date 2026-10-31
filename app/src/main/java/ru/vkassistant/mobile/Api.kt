package ru.vkassistant.mobile

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Small synchronous VK/Telegram HTTP client.
 * Call from a worker thread, never from the Android main thread.
 */
object Api {
    private const val VK_VERSION = "5.199"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 20_000

    private fun enc(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun readResponse(connection: HttpURLConnection): String {
        val code = connection.responseCode
        val stream = if (code in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
                ?: throw IllegalStateException("HTTP $code: сервер не вернул описание ошибки")
        }
        val body = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        if (code !in 200..299) {
            val message = runCatching {
                JSONObject(body).optJSONObject("error")?.optString("error_msg")
                    ?.takeIf { it.isNotBlank() }
            }.getOrNull()
            throw IllegalStateException(message ?: "HTTP $code")
        }
        return body
    }

    private fun vkGet(method: String, params: Map<String, String>, token: String): JSONObject {
        require(token.isNotBlank()) { "Не задан токен VK" }
        val query = (params + mapOf("access_token" to token, "v" to VK_VERSION))
            .entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
        val connection = (URL("https://api.vk.com/method/$method?$query")
            .openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            useCaches = false
        }
        return try {
            val json = JSONObject(readResponse(connection))
            if (json.has("error")) {
                val error = json.optJSONObject("error")
                throw IllegalStateException(
                    error?.optString("error_msg")?.takeIf { it.isNotBlank() }
                        ?: "Ошибка VK API"
                )
            }
            json
        } finally {
            connection.disconnect()
        }
    }

    fun resolveCommunity(raw: String, vkKey: String): Community {
        val input = raw.trim()
        require(input.isNotBlank()) { "Вставь ссылку на сообщество VK" }

        val normalized = if (input.startsWith("http://") || input.startsWith("https://")) {
            input
        } else {
            "https://$input"
        }
        val uri = runCatching { java.net.URI(normalized) }
            .getOrElse { throw IllegalArgumentException("Некорректная ссылка на сообщество") }

        val host = uri.host?.lowercase()?.removePrefix("www.")
        require(host == "vk.com" || host == "m.vk.com") {
            "Разрешены только ссылки vk.com"
        }
        val screenName = uri.path.orEmpty().trim('/').substringBefore('/')
        require(screenName.isNotBlank() && screenName.matches(Regex("[A-Za-z0-9_.-]+"))) {
            "Не удалось определить адрес сообщества VK"
        }

        val response = vkGet(
            "groups.getById",
            mapOf("group_id" to screenName),
            vkKey
        )
        val groups = response.optJSONArray("response")
            ?: response.optJSONObject("response")?.optJSONArray("groups)
            ?: throw IllegalStateException("VK вернул неожиданный формат данных о сообществе")
        if (groups.length() == 0) throw IllegalStateException("Сообщество не найдено")

        val group = groups.getJSONObject(0)
        val id = group.optLong("id")
        require(id > 0) { "VK вернул некорректный ID сообщества" }
        return Community(
            screenName = group.optString("screen_name").ifBlank { screenName },
            ownerId = -id,
            title = group.optString("name").ifBlank { screenName },
            lastDate = 0,
            lastId = 0
        )
    }

    fun latestPosts(ownerId: Long, vkKey: String): List<JSONObject> {
        require(ownerId < 0) { "Некорректный ID сообщества VK" }
        val response = vkGet(
            "wall.get",
            mapOf(
                "owner_id" to ownerId.toString(),
                "count" to "20",
                "filter" to "all"
            ),
            vkKey
        )
        val items = response.optJSONObject("response")?.optJSONArray("items")
            ?: return emptyList()
        return (0 until items.length())
            .map { items.getJSONObject(it) }
            .filter { it.optLong("id") > 0 && it.optLong("date") > 0 }
            .sortedWith(compareBy<JSONObject> { it.optLong("date") }.thenBy { it.optLong("id") })
    }

    fun createComment(
        ownerId: Long,
        postId: Long,
        message: String,
        vkToken: String,
        guid: String
    ): Long {
        require(ownerId < 0) { "Некорректный ID сообщества VK" }
        require(postId > 0) { "Некорректный ID публикации VK" }
        require(message.isNotBlank()) { "Комментарий пустой" }
        require(guid.isNotBlank()) { "Не задан идентификатор запроса" }
        require(vkToken.isNotBlank()) { "Не задан токен VK" }

        val connection = (URL("https://api.vk.com/method/wall.createComment")
            .openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "POST"
            doOutput = true
            useCaches = false
            setRequestProperty(
                "Content-Type",
                "application/x-www-form-urlencoded; charset=UTF-8"
            )
        }
        val body = mapOf(
            "owner_id" to ownerId.toString(),
            "post_id" to postId.toString(),
            "message" to message,
            "guid" to guid,
            "access_token" to vkToken,
            "v" to VK_VERSION
        ).entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }

        return try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val json = JSONObject(readResponse(connection))
            if (json.has("error")) {
                val error = json.optJSONObject("error")
                throw IllegalStateException(
                    error?.optString("error_msg")?.takeIf { it.isNotBlank() }
                        ?: "VK не принял комментарий"
                )
            }
            val commentId = json.optJSONObject("response")?.optLong("comment_id") ?: 0L
            if (commentId <= 0L) throw IllegalStateException("VK не вернул ID комментария")
            commentId
        } finally {
            connection.disconnect()
        }
    }

    fun sendTelegram(token: String, chatId: String, message: String) {
        require(token.isNotBlank()) { "Не задан токен Telegram" }
        require(chatId.isNotBlank()) { "Не задан Telegram Chat ID" }
        require(message.isNotBlank()) { "Сообщение Telegram пустое" }

        val connection = (URL("https://api.telegram.org/bot$token/sendMessage")
            .openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "POST"
            doOutput = true
            useCaches = false
            setRequestProperty(
                "Content-Type",
                "application/x-www-form-urlencoded; charset=UTF-8"
            )
        }
        val body = mapOf(
            "chat_id" to chatId,
            "text" to message,
            "disable_web_page_preview" to "true"
        ).entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }

        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val json = JSONObject(readResponse(connection))
            if (!json.optBoolean("ok")) {
                throw IllegalStateException(
                    json.optString("description").ifBlank { "Telegram API отклонил сообщение" }
                )
            }
        } finally {
            connection.disconnect()
        }
    }
}
