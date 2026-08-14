// ==== ФАЙЛ: NominatimGeocoder.kt ====
package com.spotlog.data

import android.content.Context
import android.util.Log
import com.spotlog.data.dao.GeocodeCacheDao
import com.spotlog.data.entity.GeocodeCacheEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Обратное геокодирование через Nominatim.
 *
 * Реализует два важных требования их usage policy:
 *  1. Rate‑limit: не более 1 запроса в секунду (через Mutex‑slot).
 *  2. Корректный HTTP User-Agent (Nominatim требует идентификацию приложения).
 */
object NominatimGeocoder {
    private const val MIN_INTERVAL_MS = 1000L

    /**
     * Идентификатор приложения, который видит Nominatim.
     * Без User-Agent зеркала могут возвращать 403/429.
     */
    private const val USER_AGENT =
        "Chikipiki/1.0 (Android; contact: dev@example.com)"

    private val mutex = Mutex()
    private var lastRequestTime = 0L

    suspend fun reverseGeocode(
        context: Context,
        lat: Double,
        lon: Double,
        cacheDao: GeocodeCacheDao
    ): Pair<String?, String?> {
        val cellKey = geocodeCellKey(lat, lon)
        val cached = cacheDao.getByCellKey(cellKey)
        if (cached != null) {
            return cached.country to cached.region
        }

        // Мьютекс держит ТОЛЬКО резервирование временного слота (быстрая операция,
        // без сети) — не сам HTTP-запрос. Так медленный/зависший фоновый запрос
        // не блокирует интерактивный чекин пользователя.
        val slotTime = mutex.withLock {
            val now = System.currentTimeMillis()
            val next = maxOf(now, lastRequestTime + MIN_INTERVAL_MS)
            lastRequestTime = next
            next
        }
        val waitMs = slotTime - System.currentTimeMillis()
        if (waitMs > 0) delay(waitMs)

        val (country, region) = fetchReverseGeocode(lat, lon)

        if (country != null) {
            cacheDao.insert(
                GeocodeCacheEntity(
                    cellKey = cellKey,
                    country = country,
                    region = region,
                    cachedAt = System.currentTimeMillis()
                )
            )
        }
        return country to region
    }

    private suspend fun fetchReverseGeocode(lat: Double, lon: Double): Pair<String?, String?> =
        withContext(Dispatchers.IO) {
            val url = buildNominatimUrl(lat, lon)
            var conn: HttpURLConnection? = null
            try {
                conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                conn.requestMethod = "GET"

                // FIX: Nominatim usage policy требует идентификации клиента
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("Accept-Language", "ru")

                val responseCode = conn.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw IOException("HTTP error $responseCode")
                }

                val inputStream = conn.inputStream
                val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
                val stringBuilder = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    stringBuilder.append(line)
                }
                val json = stringBuilder.toString()
                val obj = JSONObject(json)

                val address = obj.optJSONObject("address")
                val country = address?.optString("country")?.takeIf { it.isNotEmpty() }
                val region = address?.optString("state")?.takeIf { it.isNotEmpty() }
                    ?: address?.optString("region")?.takeIf { it.isNotEmpty() }
                    ?: address?.optString("county")?.takeIf { it.isNotEmpty() }

                country to region
            } catch (e: Exception) {
                Log.e("NominatimGeocoder", "Geocoding error", e)
                null to null
            } finally {
                conn?.disconnect()
            }
        }

    private fun buildNominatimUrl(lat: Double, lon: Double): String {
        return "https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lon&zoom=18&addressdetails=1&accept-language=ru"
    }

    // %.2f сам корректно округляет — ручное усечение через .toInt() было лишним
    // и вдобавок асимметричным для отрицательных координат. Locale.US обязателен:
    // это ключ кэша (PK GeocodeCacheEntity), и без явной локали десятичный
    // разделитель зависит от локали устройства (может быть "," вместо ".").
    private fun geocodeCellKey(lat: Double, lon: Double): String =
        "%.2f_%.2f".format(Locale.US, lat, lon)
}
