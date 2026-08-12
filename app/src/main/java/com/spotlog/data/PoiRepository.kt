// ==== ФАЙЛ: PoiRepository.kt ====
package com.spotlog.data

import android.content.Context
import android.util.Log
import com.spotlog.model.PoiData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Репозиторий поиска POI через Overpass API.
 *
 * Ключевые отличия этой версии:
 *  - Список зеркал (по умолчанию — основной Overpass + два публичных зеркала).
 *  - При сбое (timeout / HTTP 429 / 5xx / недоступность) — автоматический переход
 *    на следующее зеркало. Это устраняет «пустой список» и «ошибку поиска» при
 *    временной недоступности одного сервера (например, из‑за VPN‑блокировок).
 *  - Кэш двухуровневый (память + файл), TTL 30 минут.
 */
class PoiRepository(
    private val context: Context,
    @Suppress("UNUSED_PARAMETER") private val scope: kotlinx.coroutines.CoroutineScope
) {
    private val cacheMutex = Mutex()
    private val memoryCache = mutableMapOf<String, Pair<Long, String>>()
    private val cacheTtlMs = TimeUnit.MINUTES.toMillis(30)
    private val searchRadius = 500

    /**
     * Список зеркал Overpass API. Используются по очереди; при ошибке
     * одной попытки сразу пробуем следующую. Источник: публичный список
     * https://wiki.openstreetmap.org/wiki/Overpass_API (по состоянию на 2024).
     */
    private val overpassMirrors = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.private.coffee/api/interpreter"
    )

    @Suppress("SameParameterValue")
    private fun regionKey(lat: Double, lon: Double): String {
        // Округление до 3 знаков (~110м) — чтобы соседние поиски попадали в кэш
        val latR = (lat * 1000).toInt() / 1000.0
        val lonR = (lon * 1000).toInt() / 1000.0
        return "%.3f_%.3f".format(latR, lonR)
    }

    private fun getCacheFile(key: String): File =
        File(context.cacheDir, "poi_cache_${key}.json")

    /** Основная точка входа: кэш + сеть с перебором зеркал */
    suspend fun getNearby(lat: Double, lon: Double, radius: Int = searchRadius): List<PoiData> {
        val key = regionKey(lat, lon)

        // 1. Проверка memory‑кэша
        cacheMutex.withLock {
            memoryCache[key]?.let { (timestamp, json) ->
                if (System.currentTimeMillis() - timestamp < cacheTtlMs) {
                    return parsePoiJson(json)
                }
            }
        }

        // 2. Проверка файлового кэша
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
            return parsePoiJson(cachedJson)
        }

        // 3. Сетевой запрос с автоматическим перебором зеркал
        val query = buildOverpassQuery(lat, lon, radius)
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

        return parsePoiJson(json)
    }

    /**
     * Пробует каждый URL из списка зеркал.
     * Возвращает результат первой успешной попытки.
     * Если все попытки неуспешны — бросает последнее исключение.
     */
    private suspend fun fetchFromAnyMirror(query: String): String {
        var lastError: Throwable? = null
        for ((index, url) in overpassMirrors.withIndex()) {
            try {
                Log.d("PoiRepository", "Trying mirror #${index + 1}: $url")
                val json = fetchPoiJson(url, query)
                Log.d("PoiRepository", "Mirror #${index + 1} responded with ${json.length} chars")
                return json
            } catch (e: Exception) {
                Log.w("PoiRepository", "Mirror #${index + 1} failed: ${e.message}")
                lastError = e
                // Продолжаем со следующим зеркалом
            }
        }
        throw lastError ?: IOException("All Overpass mirrors failed")
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
                    conn.setRequestProperty("User-Agent", "SpotLog/1.0 (Android)")

                    conn.outputStream.use { os ->
                        os.write("data=${query}".toByteArray(Charsets.UTF_8))
                    }

                    val code = conn.responseCode
                    // Считаем «успешным» только HTTP_OK. 429/5xx — повод перейти к другому зеркалу.
                    if (code != HttpURLConnection.HTTP_OK) {
                        val body = runCatching { conn.errorStream?.bufferedReader()?.readText() }
                            .getOrDefault("")
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

    @SuppressWarnings("SpellCheckingInspection")
    private fun buildOverpassQuery(lat: Double, lon: Double, radius: Int): String = """
        [out:json][timeout:25];
        (
            node["amenity"](around:$radius,$lat,$lon);
            node["shop"](around:$radius,$lat,$lon);
            node["tourism"](around:$radius,$lat,$lon);
            node["historic"](around:$radius,$lat,$lon);
            node["leisure"](around:$radius,$lat,$lon);
            way["amenity"](around:$radius,$lat,$lon);
            way["shop"](around:$radius,$lat,$lon);
            way["tourism"](around:$radius,$lat,$lon);
            way["historic"](around:$radius,$lat,$lon);
            way["leisure"](around:$radius,$lat,$lon);
            relation["amenity"](around:$radius,$lat,$lon);
            relation["shop"](around:$radius,$lat,$lon);
            relation["tourism"](around:$radius,$lat,$lon);
            relation["historic"](around:$radius,$lat,$lon);
            relation["leisure"](around:$radius,$lat,$lon);
        );
        out body;
        >;
        out skel qt;
    """.trimIndent()

    private fun parsePoiJson(json: String): List<PoiData> {
        val result = mutableListOf<PoiData>()
        try {
            val root = JSONObject(json)
            val elements = root.optJSONArray("elements") ?: return emptyList()
            val seenNames = mutableSetOf<String>()

            for (i in 0 until elements.length()) {
                val el = elements.optJSONObject(i) ?: continue
                val tags = el.optJSONObject("tags") ?: continue
                val name = tags.optString("name", "")
                if (name.isEmpty()) continue
                if (seenNames.contains(name)) continue
                seenNames.add(name)

                val lat = el.optDouble("lat", Double.NaN)
                val lon = el.optDouble("lon", Double.NaN)
                if (lat.isNaN() || lon.isNaN()) continue

                val category = tags.optString("amenity", "")
                    .ifEmpty { tags.optString("shop", "") }
                    .ifEmpty { tags.optString("tourism", "") }
                    .ifEmpty { tags.optString("historic", "") }
                    .ifEmpty { tags.optString("leisure", "") }
                    .ifEmpty { "other" }

                result.add(PoiData(name, lat, lon, category))
            }
        } catch (e: Exception) {
            Log.e("PoiRepository", "Parse error", e)
        }
        return result
    }
}
