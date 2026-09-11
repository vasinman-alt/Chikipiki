package com.spotlog.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Результат парсинга одного чек-ина из файла экспорта Foursquare.
 */
data class FoursquareCheckin(
    val id: String,
    val venueId: String,
    val venueName: String,
    val lat: Double,
    val lng: Double,
    val createdAtMs: Long,
    val shout: String?,
    val isPrivate: Boolean
)

/**
 * Ошибки парсинга.
 */
sealed class FoursquareParseError {
    object InvalidJson : FoursquareParseError()
    object MissingItems : FoursquareParseError()
    data class InvalidItem(val index: Int, val reason: String) : FoursquareParseError()
}

/**
 * Парсер файла экспорта Foursquare.
 *
 * Особенности формата:
 * - Все ключи содержат пробел в конце: "id ", "createdAt ", "venue " и т.д.
 * - Строковые значения содержат пробел в конце.
 * - Дата в формате "2026-06-03 15:42:23.000000" (локальная).
 * - timeZoneOffset в минутах от UTC (180 = UTC+3).
 * - shout (комментарий) и private присутствуют не у всех записей.
 * - У старых записей (2017) отсутствует поле "hacc".
 */
object FoursquareParser {

    private const val DATE_FORMAT = "yyyy-MM-dd HH:mm:ss"

    /**
     * Парсит файл и возвращает пару: список валидных чек-инов + список ошибок.
     */
    fun parse(file: File): Pair<List<FoursquareCheckin>, List<FoursquareParseError>> {
        val checkins = mutableListOf<FoursquareCheckin>()
        val errors = mutableListOf<FoursquareParseError>()

        val jsonText = try {
            file.readText()
        } catch (_: Exception) {
            return Pair(emptyList(), listOf(FoursquareParseError.InvalidJson))
        }

        val root = try {
            JSONObject(jsonText)
        } catch (_: Exception) {
            return Pair(emptyList(), listOf(FoursquareParseError.InvalidJson))
        }

        val items = getJSONArray(root, "items")
        if (items == null) {
            return Pair(emptyList(), listOf(FoursquareParseError.MissingItems))
        }

        for (i in 0 until items.length()) {
            val item = try {
                items.getJSONObject(i)
            } catch (_: Exception) {
                errors.add(FoursquareParseError.InvalidItem(i, "Элемент не является объектом"))
                continue
            }

            val checkin = parseCheckin(item)
            if (checkin != null) {
                checkins.add(checkin)
            } else {
                errors.add(FoursquareParseError.InvalidItem(i, "Отсутствуют обязательные поля"))
            }
        }

        return Pair(checkins, errors)
    }

    private fun parseCheckin(obj: JSONObject): FoursquareCheckin? {
        val id = getString(obj, "id") ?: return null
        val createdAtStr = getString(obj, "createdAt") ?: return null
        val lat = getDouble(obj, "lat") ?: return null
        val lng = getDouble(obj, "lng") ?: return null
        val timeZoneOffset = getInt(obj, "timeZoneOffset") ?: 0
        val isPrivate = getBoolean(obj, "private")

        val shout = getString(obj, "shout")?.takeIf { it.isNotBlank() }

        val venue = getJSONObject(obj, "venue") ?: return null
        val venueId = getString(venue, "id") ?: return null
        val venueName = getString(venue, "name") ?: return null

        if (venueName.isBlank()) return null

        val createdAtMs = parseDate(createdAtStr, timeZoneOffset) ?: return null

        return FoursquareCheckin(
            id = id,
            venueId = venueId,
            venueName = venueName,
            lat = lat,
            lng = lng,
            createdAtMs = createdAtMs,
            shout = shout,
            isPrivate = isPrivate
        )
    }

    /**
     * Ищет ключ с учётом пробелов: "id " найдётся при запросе "id".
     */
    private fun findKey(obj: JSONObject, key: String): String? {
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (k.trim() == key) return k
        }
        return null
    }

    private fun getString(obj: JSONObject, key: String): String? {
        val actualKey = findKey(obj, key) ?: return null
        return try {
            obj.getString(actualKey).trim()
        } catch (_: Exception) {
            null
        }
    }

    private fun getDouble(obj: JSONObject, key: String): Double? {
        val actualKey = findKey(obj, key) ?: return null
        return try {
            obj.getDouble(actualKey)
        } catch (_: Exception) {
            null
        }
    }

    private fun getInt(obj: JSONObject, key: String): Int? {
        val actualKey = findKey(obj, key) ?: return null
        return try {
            obj.getInt(actualKey)
        } catch (_: Exception) {
            null
        }
    }

    private fun getBoolean(obj: JSONObject, key: String): Boolean {
        val actualKey = findKey(obj, key) ?: return false
        return try {
            obj.getBoolean(actualKey)
        } catch (_: Exception) {
            false
        }
    }

    private fun getJSONObject(obj: JSONObject, key: String): JSONObject? {
        val actualKey = findKey(obj, key) ?: return null
        return try {
            obj.getJSONObject(actualKey)
        } catch (_: Exception) {
            null
        }
    }

    private fun getJSONArray(obj: JSONObject, key: String): JSONArray? {
        val actualKey = findKey(obj, key) ?: return null
        return try {
            obj.getJSONArray(actualKey)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Конвертирует дату чек-ина в epoch millis (UTC).
     *
     * "2026-06-03 15:42:23.000000" + timeZoneOffset=180
     * → парсим как 15:42:23
     * → вычитаем 180 минут
     * → получаем 12:42:23 UTC
     */
    private fun parseDate(dateStr: String, timeZoneOffsetMinutes: Int): Long? {
        return try {
            val cleanStr = if (dateStr.length >= 19) {
                dateStr.substring(0, 19)
            } else {
                dateStr
            }
            val sdf = SimpleDateFormat(DATE_FORMAT, Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val parsedMs = sdf.parse(cleanStr)?.time ?: return null
            parsedMs - timeZoneOffsetMinutes * 60L * 1000L
        } catch (_: Exception) {
            null
        }
    }
}