package ru.vkassistant.mobile

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Community(
    val screenName: String,
    val ownerId: Long,
    val title: String,
    val lastDate: Long,
    val lastId: Long,
    val autoPublish: Boolean = false
)

object Prefs {
    private const val FILE = "vk_assistant_private"
    private const val MAX_HISTORY_ITEMS = 60
    private val lock = Any()

    private fun p(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun get(context: Context, key: String, default: String = ""): String =
        runCatching { p(context).getString(key, default) ?: default }.getOrDefault(default)

    fun put(context: Context, key: String, value: String) {
        p(context).edit().putString(key, value).apply()
    }

    fun isRunning(context: Context): Boolean =
        p(context).getBoolean("running", false)

    fun setRunning(context: Context, value: Boolean) {
        p(context).edit().putBoolean("running", value).apply()
    }

    /**
     * Reads communities defensively. A malformed old preference must not crash
     * the Activity or the foreground service.
     */
    fun communities(context: Context): MutableList<Community> = synchronized(lock) {
        val raw = get(context, "communities", "[]")
        val array = runCatching { JSONArray(raw) }.getOrElse { return@synchronized mutableListOf() }
        val result = LinkedHashMap<Long, Community>()

        for (index in 0 until array.length()) {
            val item = runCatching { array.optJSONObject(index) }.getOrNull() ?: continue
            val ownerId = item.optLong("ownerId")
            val screenName = item.optString("screenName").trim()
            val title = item.optString("title").trim()
            if (ownerId >= 0L || screenName.isBlank() || title.isBlank()) continue

            result[ownerId] = Community(
                screenName = screenName,
                ownerId = ownerId,
                title = title,
                lastDate = item.optLong("lastDate").coerceAtLeast(0L),
                lastId = item.optLong("lastId").coerceAtLeast(0L),
                autoPublish = item.optBoolean("autoPublish", false)
            )
        }
        result.values.toMutableList()
    }

    fun saveCommunities(context: Context, items: List<Community>) {
        val array = JSONArray()
        items.filter { it.ownerId < 0L && it.screenName.isNotBlank() && it.title.isNotBlank() }
            .distinctBy { it.ownerId }
            .forEach { item ->
                array.put(
                    JSONObject().apply {
                        put("screenName", item.screenName)
                        put("ownerId", item.ownerId)
                        put("title", item.title)
                        put("lastDate", item.lastDate.coerceAtLeast(0L))
                        put("lastId", item.lastId.coerceAtLeast(0L))
                        put("autoPublish", item.autoPublish)
                    }
                )
            }
        put(context, "communities", array.toString())
    }

    fun addHistory(context: Context, line: String) {
        val safeLine = line
            .replace("\n", " ")
            .replace("\r", " ")
            .take(500)
        if (safeLine.isBlank()) return

        synchronized(lock) {
            val previous = get(context, "history", "")
            val entries = (listOf(safeLine) + previous.split("\n").filter { it.isNotBlank() })
                .take(MAX_HISTORY_ITEMS)
            put(context, "history", entries.joinToString("\n"))
        }
    }
}
