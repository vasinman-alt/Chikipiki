package com.spotlog.data

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

object ImportParser {

    private const val SUPPORTED_VERSION = 1

    fun parse(jsonString: String): ImportResult {
        try {
            val root = JSONObject(jsonString)
            val version = root.optInt("version", 0)
            if (version != SUPPORTED_VERSION) {
                return ImportResult.Error(ImportValidationError.UnsupportedVersion(version))
            }

            val placesArray = root.optJSONArray("places") ?: JSONArray()
            val places = mutableListOf<ValidatedImportPlace>()
            val errors = mutableListOf<ImportValidationError>()

            // Создаём форматтер локально на каждый вызов (не потокобезопасен, но каждый вызов изолирован)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            // Используем локальный часовой пояс устройства
            dateFormat.timeZone = TimeZone.getDefault()

            for (i in 0 until placesArray.length()) {
                val placeObj = placesArray.getJSONObject(i)
                val name = placeObj.optString("name", "")
                if (name.isBlank()) {
                    errors.add(ImportValidationError.EmptyPlaceName)
                    continue
                }

                val lat = placeObj.optDouble("lat", Double.NaN)
                val lon = placeObj.optDouble("lon", Double.NaN)
                if (lat.isNaN() || lon.isNaN() || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                    errors.add(ImportValidationError.InvalidCoordinates)
                    continue
                }

                val category = placeObj.optString("category", "other")
                val comment = placeObj.optString("comment", "")

                val visitsArray = placeObj.optJSONArray("visits") ?: JSONArray()
                val visits = mutableListOf<ImportVisit>()

                for (j in 0 until visitsArray.length()) {
                    val visitObj = visitsArray.getJSONObject(j)
                    val timestampStr = visitObj.optString("timestamp", "")
                    val timestamp = try {
                        dateFormat.parse(timestampStr)?.time
                    } catch (e: Exception) {
                        errors.add(ImportValidationError.InvalidTimestamp)
                        null
                    }
                    if (timestamp == null) continue

                    val visitComment = visitObj.optString("comment", "")
                    visits.add(ImportVisit(timestamp, visitComment))
                }

                if (visits.isEmpty()) {
                    errors.add(ImportValidationError.InvalidTimestamp)
                    continue
                }

                places.add(
                    ValidatedImportPlace(
                        name = name,
                        lat = lat,
                        lon = lon,
                        category = category,
                        comment = comment,
                        visits = visits
                    )
                )
            }

            return if (errors.isEmpty()) {
                ImportResult.Success(places)
            } else {
                ImportResult.PartialSuccess(places, errors)
            }

        } catch (e: Exception) {
            Log.e("ImportParser", "Parse error", e)
            return ImportResult.Error(ImportValidationError.InvalidJson)
        }
    }
}

sealed class ImportResult {
    data class Success(val places: List<ValidatedImportPlace>) : ImportResult()
    data class PartialSuccess(val places: List<ValidatedImportPlace>, val errors: List<ImportValidationError>) :
        ImportResult()
    data class Error(val error: ImportValidationError) : ImportResult()
}

data class ValidatedImportPlace(
    val name: String,
    val lat: Double,
    val lon: Double,
    val category: String,
    val comment: String,
    val visits: List<ImportVisit>
)

data class ImportVisit(
    val timestamp: Long,
    val comment: String
)

sealed class ImportValidationError {
    object EmptyPlaceName : ImportValidationError()
    object InvalidCoordinates : ImportValidationError()
    object InvalidTimestamp : ImportValidationError()
    object InvalidJson : ImportValidationError()
    data class UnsupportedVersion(val version: Int) : ImportValidationError()
}