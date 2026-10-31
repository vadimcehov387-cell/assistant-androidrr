package ru.vkassistant.mobile

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** OpenAI client. Invoke only from a background thread; never log the API key. */
object AiDrafts {
    private const val ENDPOINT = "https://api.openai.com/v1/chat/completions"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val DEFAULT_MODEL = "gpt-4o-mini"
    private const val MAX_POST_LENGTH = 5_000
    private const val MAX_COMMENT_LENGTH = 500

    fun generateSingle(postText: String, apiKey: String, model: String, style: String): String? {
        if (apiKey.isBlank()) return null
        val prompt = buildPrompt(postText, style, false)
        return request(apiKey, model, prompt, 180)
            ?.lineSequence()
            ?.map(::cleanLine)
            ?.firstOrNull { it.isNotBlank() }
            ?.take(MAX_COMMENT_LENGTH)
    }

    fun generate(postText: String, apiKey: String, model: String, style: String): List<String>? {
        if (apiKey.isBlank()) return null
        val prompt = buildPrompt(postText, style, true)
        val content = request(apiKey, model, prompt, 450) ?: return null
        val options = content.lineSequence()
            .map(::cleanLine)
            .filter(String::isNotBlank)
            .distinct()
            .take(3)
            .map { it.take(MAX_COMMENT_LENGTH) }
            .toList()
        return options.takeIf { it.size == 3 }
    }

    private fun buildPrompt(postText: String, style: String, multiple: Boolean): String {
        val quantity = if (multiple) "ровно три разных" else "один"
        val numbering = if (multiple) "Каждый вариант начни с отдельной строки и номера." else ""
        return """
            Напиши $quantity коротких, естественных и содержательных комментария
            к публикации ВКонтакте.
            Стиль: $style.
            Не рекламируй товары, не проси перейти в профиль, не используй хештеги,
            не выдумывай личный опыт или факты и не повторяй публикацию дословно.
            Если контекста недостаточно, задай нейтральный уместный вопрос.
            Верни только комментарии, без вступления и пояснений.
            $numbering
            Публикация:
            ${postText.take(MAX_POST_LENGTH)}
        """.trimIndent()
    }

    private fun request(apiKey: String, model: String, prompt: String, maxTokens: Int): String? {
        val requestBody = JSONObject().apply {
            put("model", model.trim().ifBlank { DEFAULT_MODEL })
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            put("temperature", 0.65)
            put("max_tokens", maxTokens)
        }
        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "POST"
            doOutput = true
            useCaches = false
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }
        return try {
            connection.outputStream.use {
                it.write(requestBody.toString().toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                ?: throw IllegalStateException("AI API вернул HTTP $status")
            val responseText = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (status !in 200..299) {
                val message = runCatching {
                    JSONObject(responseText).optJSONObject("error")?.optString("message")
                        ?.takeIf { it.isNotBlank() }
                }.getOrNull()
                throw IllegalStateException(message ?: "AI API вернул HTTP $status")
            }
            val choices = JSONObject(responseText).optJSONArray("choices")
                ?: throw IllegalStateException("AI API вернул ответ без вариантов")
            if (choices.length() == 0) return null
            choices.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                ?.trim()?.takeIf { it.isNotBlank() }
        } finally {
            connection.disconnect()
        }
    }

    private fun cleanLine(line: String): String =
        line.trim()
            .replace(Regex("^\\s*(?:вариант\\s*)?\\d+[.)\\-:]\\s*", RegexOption.IGNORE_CASE), "")
            .trim()
            .trim('"', '«', '»')
}
