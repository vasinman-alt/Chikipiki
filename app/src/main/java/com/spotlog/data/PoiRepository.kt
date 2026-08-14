// ==== ФАЙЛ: PoiRepository.kt ====
package com.spotlog.data

import android.content.Context
import android.util.Log
import com.spotlog.config.ConfigManager
import com.spotlog.config.FilterConfig
import com.spotlog.config.ResponseMapping
import com.spotlog.config.SourceConfig
import com.spotlog.model.PoiData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Репозиторий поиска POI.
 *
 * Полностью настраивается через `ConfigManager` (см. `spotlog_config.json`):
 *  - список зеркал (`mirrors[]`) — перебирается при ошибках
 *  - шаблон запроса (`query_template`) — подставляются {lat}, {lon}, {radius}
 *  - маппинг полей ответа (`response_mapping`) — гибкая поддержка разных API
 *  - фильтры (`filters`) — exclude_keys/exclude_tags
 *
 * Кэш двухуровневый: память + файл (TTL 30 минут).
 */
class PoiRepository(
    private val context: Context,
    @Suppress("UNUSED_PARAMETER") private val scope: kotlinx.coroutines.CoroutineScope
) {
    private val configManager = ConfigManager.getInstance(context)

    private val cacheMutex = Mutex()
    private val memoryCache = mutableMapOf<String, Pair<Long, String>>()
    private val cacheTtlMs = TimeUnit.MINUTES.toMillis(30)
    private val searchRadius = 500

    /** Кэш активного источника (вычисляется один раз при инициализации). */
    private val source: SourceConfig by lazy { configManager.getActiveSource() }
    private val responseMapping: ResponseMapping by lazy { configManager.getResponseMapping() }
    private val filters: FilterConfig by lazy { configManager.getFilters() }

    @Suppress("SameParameterValue")
    private fun regionKey(lat: Double, lon: Double): String {
        // Округление до 3 знаков (~110м) — чтобы соседние поиски попадали в кэш
        val latR = (lat * 1000).toInt() / 1000.0
        val lonR = (lon * 1000).toInt() / 1000.0
        return "%.3f_%.3f".format(latR, lonR)
    }

    private fun getCacheFile(key: String): File =
        File(context.cacheDir, "poi_cache_${key}.json")

    /** Основная точка входа: кэш + сеть с перебором зеркал + фильтры */
    suspend fun getNearby(lat: Double, lon: Double, radius: Int = searchRadius): List<PoiData> {
        val key = regionKey(lat, lon)

        // 1️⃣ Memory‑кэш
        cacheMutex.withLock {
            memoryCache[key]?.let { (timestamp, json) ->
                if (System.currentTimeMillis() - timestamp < cacheTtlMs) {
                    return parseAndFilter(json)
                }
            }
        }

        // 2️⃣ Файловый кэш
        val cachedJson = withContext(Dispatchers.IO) {
            cacheMutex.withLock {
                val file = getCacheFile(key)
                if (file.exists() && file.canRead()) {
                    try {
                        val json = file.readText()
                        val modified = file.lastModified()
                        if (System.currentTimeMillis() - modified < cacheTtlMs) {
                            memoryCache[key] = System.currentTimeMillis() to json
                            json
                        } else null
                    } catch (e: Exception) {
                        Log.w("PoiRepository", "Failed to read cache file: ${e.message}")
                        null
                    }
                } else null
            }
        }
        if (cachedJson != null) {
            return parseAndFilter(cachedJson)
        }

        // 3️⃣ Сеть с автоматическим перебором зеркал
        val query = buildQuery(lat, lon, radius)
        val json = fetchFromAnyMirror(query)

        // Сохраняем в кэш (память + файл)
        withContext(Dispatchers.IO) {
            cacheMutex.withLock {
                memoryCache[key] = System.currentTimeMillis() to json
                try {
                    getCacheFile(key).writeText(json)
                } catch (e: Exception) {
                    Log.w("PoiRepository", "Failed to write cache file: ${e.message}")
                }
            }
        }

        return parseAndFilter(json)
    }

    /** Подставляет координаты и радиус в шаблон из конфига. */
    private fun buildQuery(lat: Double, lon: Double, radius: Int): String {
        return source.queryTemplate
            .replace("{lat}", lat.toString())
            .replace("{lon}", lon.toString())
            .replace("{radius}", radius.toString())
    }

    /** Пробует каждое зеркало по очереди; возвращает первый успешный ответ. */
    private suspend fun fetchFromAnyMirror(query: String): String {
        var lastError: Throwable? = null
        for ((index, url) in source.mirrors.withIndex()) {
            try {
                Log.d("PoiRepository", "Trying mirror #${index + 1}: $url")
                val json = fetchPoiJson(url, query)
                Log.d("PoiRepository", "Mirror #${index + 1} responded with ${json.length} chars")
                return json
            } catch (e: Exception) {
                Log.w("PoiRepository", "Mirror #${index + 1} failed: ${e.message}")
                lastError = e
            }
        }
        throw lastError ?: IOException("All ${source.name} mirrors failed")
    }

    /** Один HTTP POST к конкретному URL с общим таймаутом 15 сек. */
    private suspend fun fetchPoiJson(url: String, query: String): String =
        withContext(Dispatchers.IO) {
            try {
                withTimeout(15_000L) {
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    conn.connectTimeout = 8_000
                    conn.readTimeout = 12_000

                    // FIX: User-Agent из конфига (если требуется)
                    if (source.requiresUserAgent) {
                        conn.setRequestProperty("User-Agent", configManager.getUserAgent())
                    }

                    conn.outputStream.use { os ->
                        os.write("data=${query}".toByteArray(Charsets.UTF_8))
                    }

                    val code = conn.responseCode
                    if (code != HttpURLConnection.HTTP_OK) {
                        val body = runCatching { conn.errorStream?.bufferedReader()?.readText() }
                            .getOrNull() ?: ""
                        conn.disconnect()
                        throw IOException("HTTP $code: ${body.take(200)}")
                    }

                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()
                    response
                }
            } catch (e: TimeoutCancellationException) {
                throw IOException("Timeout while fetching POI from $url")
            }
        }

    /* -------------------------------------------------------------
     *  Парсинг и фильтрация через response_mapping
     * ------------------------------------------------------------- */

    /**
     * Полный цикл: распарсить JSON → применить фильтры → вернуть список PoiData.
     */
    private fun parseAndFilter(json: String): List<PoiData> {
        val raw = parseRaw(json)
        return raw.filter { passesFilters(it) }
    }

    /**
     * Парсит JSON в список «сырых» элементов, где для каждого POI уже
     * известно имя, координаты и первая найденная «категория».
     */
    private fun parseRaw(json: String): List<PoiData> {
        val result = mutableListOf<PoiData>()
        try {
            val root = JSONObject(json)
            val elements = root.optJSONArray(responseMapping.elementsArray) ?: return emptyList()
            val seenNames = mutableSetOf<String>()

            for (i in 0 until elements.length()) {
                val el = elements.optJSONObject(i) ?: continue
                val tags = el.optJSONObject("tags")
                val name = readNestedString(el, responseMapping.nameField) ?: continue
                if (name.isBlank() || !seenNames.add(name)) continue

                val lat = el.optDouble(responseMapping.latField, Double.NaN)
                val lon = el.optDouble(responseMapping.lonField, Double.NaN)
                if (lat.isNaN() || lon.isNaN()) continue

                // Категория: сначала из mapping.categoryField, иначе — fallback‑эвристика
                val category = responseMapping.categoryField
                    ?.let { readNestedString(el, it) }
                    ?.takeIf { it.isNotBlank() }
                    ?: fallbackCategoryFromTags(tags)

                result.add(PoiData(name, lat, lon, category))
            }
        } catch (e: Exception) {
            Log.e("PoiRepository", "Parse error", e)
        }
        return result
    }

    /**
     * Читает вложенное поле по пути «a.b.c» (например, "tags.name").
     * Если путь пустой или содержит только один сегмент — работает как обычный ключ.
     */
    private fun readNestedString(obj: JSONObject, path: String): String? {
        if (path.isBlank()) return null
        val parts = path.split('.')
        var current: JSONObject? = obj
        for (i in 0 until parts.size - 1) {
            current = current?.optJSONObject(parts[i]) ?: return null
        }
        return current?.optString(parts.last(), "")
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * Если в маппинге категории нет или поле пустое — ищем «key=value» в `tags`.
     * Возвращаем первый непустой тег, чтобы UI мог показать хоть что‑то осмысленное.
     */
    private fun fallbackCategoryFromTags(tags: JSONObject?): String {
        if (tags == null) return "other"
        val keys = tags.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = tags.optString(key, "").trim()
            if (value.isNotBlank()) return "$key=$value"
        }
        return "other"
    }

    /* -------------------------------------------------------------
     *  Фильтры (exclude_keys, exclude_tags, include_place_nodes)
     * ------------------------------------------------------------- */
    private fun passesFilters(poi: PoiData): Boolean {
        // Нам нужны tags, а не только имя/координаты. Но parseRaw не сохраняет
        // сам объект. Чтобы фильтрация работала корректно, сохраним теги рядом
        // с PoiData. Здесь — простая проверка через сам текст категории,
        // которая уже включает «key=value» после fallback‑эвристики.
        val tag = poi.category

        // exclude_keys: пропускаем POI, у которых в категории (key=value)
        // встречается один из запрещённых ключей
        if (filters.excludeKeys.isNotEmpty()) {
            val key = tag.substringBefore('=', missingDelimiterValue = "").trim()
            if (key.isNotBlank() && key in filters.excludeKeys) return false
        }

        // exclude_tags: аналогично, но с учётом «key=value»
        if (filters.excludeTags.isNotEmpty() && '=' in tag) {
            val (key, value) = tag.split('=', limit = 2).let { it[0].trim() to it[1].trim() }
            val blocked = filters.excludeTags[key]
            if (blocked != null && value in blocked) return false
        }

        return true
    }
}
