package ru.vkassistant.mobile

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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
    private const val LEGACY_FILE = "vk_assistant_private"
    private const val SECURE_FILE = "vk_assistant_secure"
    private const val MIGRATION_FLAG = "_secure_storage_migrated"
    private const val MAX_HISTORY_ITEMS = 60
    private val lock = Any()
    @Volatile private var securePrefs: SharedPreferences? = null

    /**
     * Secrets and settings are stored with AndroidX Security Crypto.
     * If encryption cannot be initialized, fail closed instead of silently
     * falling back to plaintext SharedPreferences.
     */
    private fun p(context: Context): SharedPreferences {
        securePrefs?.let { return it }
        return synchronized(lock) {
            securePrefs?.let { return@synchronized it }
            val appContext = context.applicationContext
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val encrypted = EncryptedSharedPreferences.create(
                appContext,
                SECURE_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            migrateLegacyPreferences(appContext, encrypted)
            securePrefs = encrypted
            encrypted
        }
    }

    private fun migrateLegacyPreferences(context: Context, target: SharedPreferences) {
        if (target.getBoolean(MIGRATION_FLAG, false)) return
        val legacy = context.getSharedPreferences(LEGACY_FILE, Context.MODE_PRIVATE)
        val values = legacy.all
        val editor = target.edit()
        values.forEach { (key, value) ->
            if (key == MIGRATION_FLAG) return@forEach
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> {
                    val strings = value.filterIsInstance<String>().toSet()
                    editor.putStringSet(key, strings)
                }
            }
        }
        editor.putBoolean(MIGRATION_FLAG, true).commit()
        // Clear legacy plaintext only after encrypted data was committed.
        legacy.edit().clear().commit()
    }

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

    /** Reads communities defensively so malformed legacy JSON cannot crash the app. */
    fun communities(context: Context): MutableList<Community> = synchronized(lock) {
        val raw = get(context, "communities", "[]")
        val array = runCatching { JSONArray(raw) }.getOrElse { return@synchronized mutableListOf() }
        val result = LinkedHashMap<Long, Community>()

        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
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
        val safeLine = line.replace("\\n", " ").replace("\\r", " ").take(500)
        if (safeLine.isBlank()) return
        synchronized(lock) {
            val previous = get(context, "history", "")
            val entries = (listOf(safeLine) + previous.split("\\n").filter { it.isNotBlank() })
                .take(MAX_HISTORY_ITEMS)
            put(context, "history", entries.joinToString("\\n"))
        }
    }
}
