package com.spotlog.data.repository

import android.content.Context
import com.spotlog.data.AppDatabase
import com.spotlog.data.FoursquareParser
import com.spotlog.data.ImportParser
import com.spotlog.data.ImportResult
import com.spotlog.data.ImportValidationError
import com.spotlog.data.ImportVisit
import com.spotlog.data.ValidatedImportPlace
import com.spotlog.data.entity.PlaceEntity
import com.spotlog.data.entity.VisitEntity
import com.spotlog.data.entity.VisitSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Формат файла импорта.
 */
enum class ImportFormat {
    APP,        // Собственный формат приложения ({"version":1, "places":[...]})
    FOURSQUARE  // Экспорт Foursquare ({"count ":60, "items ":[...]})
}

class ImportRepository(
    private val context: Context
) {
    private val db = AppDatabase.getDatabase(context)

    /**
     * Автоопределение формата и импорт.
     * Вызывается из ImportViewModel вместо importFromFile.
     */
    suspend fun importAutoDetect(file: File): ImportResult {
        return withContext(Dispatchers.IO) {
            try {
                val format = detectFormat(file)
                when (format) {
                    ImportFormat.FOURSQUARE -> importFromFoursquare(file)
                    ImportFormat.APP -> importFromFile(file)
                }
            } catch (_: Exception) {
                ImportResult.Error(ImportValidationError.InvalidJson)
            }
        }
    }

    /**
     * Определяет формат файла по ключам корневого JSON-объекта.
     * Ключи тримятся, т.к. Foursquare добавляет пробел после каждого ключа.
     */
    private fun detectFormat(file: File): ImportFormat {
        return try {
            val text = file.readText()
            val root = JSONObject(text)
            val keys = root.keys().asSequence().map { it.trim() }.toSet()
            when {
                "items" in keys -> ImportFormat.FOURSQUARE
                "places" in keys || "version" in keys -> ImportFormat.APP
                else -> ImportFormat.APP
            }
        } catch (_: Exception) {
            ImportFormat.APP
        }
    }

    /**
     * Импорт из собственного формата приложения (версионированный).
     */
    suspend fun importFromFile(file: File): ImportResult {
        return withContext(Dispatchers.IO) {
            try {
                val json = file.readText()
                val parseResult = ImportParser.parse(json)
                when (parseResult) {
                    is ImportResult.Success -> {
                        val imported = importValidatedPlaces(parseResult.places)
                        ImportResult.Success(imported)
                    }
                    is ImportResult.PartialSuccess -> {
                        val imported = importValidatedPlaces(parseResult.places)
                        ImportResult.PartialSuccess(imported, parseResult.errors)
                    }
                    is ImportResult.Error -> parseResult
                }
            } catch (_: Exception) {
                ImportResult.Error(ImportValidationError.InvalidJson)
            }
        }
    }

    /**
     * Импорт из файла экспорта Foursquare.
     */
    suspend fun importFromFoursquare(file: File): ImportResult {
        return withContext(Dispatchers.IO) {
            try {
                val (checkins, parseErrors) = FoursquareParser.parse(file)

                if (checkins.isEmpty()) {
                    return@withContext ImportResult.Error(
                        ImportValidationError.InvalidJson
                    )
                }

                val groupedByVenue = checkins.groupBy { it.venueId }
                val importedPlaces = mutableListOf<ValidatedImportPlace>()

                for ((_, venueCheckins) in groupedByVenue) {
                    try {
                        val firstCheckin = venueCheckins.first()
                        val venueName = firstCheckin.venueName

                        val nearbyPlaces = db.placeDao().findNearby(
                            firstCheckin.lat, firstCheckin.lng
                        )
                        val existingPlace = nearbyPlaces.firstOrNull {
                            it.name.equals(venueName, ignoreCase = true)
                        }

                        val placeId: Long
                        if (existingPlace != null) {
                            placeId = existingPlace.id
                        } else {
                            placeId = db.placeDao().insertPlace(
                                PlaceEntity(
                                    name = venueName,
                                    latitude = firstCheckin.lat,
                                    longitude = firstCheckin.lng,
                                    category = "custom",
                                    comment = "",
                                    country = null,
                                    region = null
                                )
                            )
                        }

                        val existingVisits = db.visitDao()
                            .getVisitsForPlace(placeId)
                            .first()
                        val existingTimestamps = existingVisits
                            .map { it.timestamp }
                            .toSet()

                        val newVisits = mutableListOf<ImportVisit>()
                        for (checkin in venueCheckins) {
                            if (checkin.createdAtMs !in existingTimestamps) {
                                db.visitDao().insertVisit(
                                    VisitEntity(
                                        placeId = placeId,
                                        timestamp = checkin.createdAtMs,
                                        comment = checkin.shout ?: "",
                                        systemNote = "Импортировано из Foursquare",
                                        source = VisitSource.IMPORTED_FILE
                                    )
                                )
                                newVisits.add(
                                    ImportVisit(
                                        timestamp = checkin.createdAtMs,
                                        comment = checkin.shout ?: ""
                                    )
                                )
                            }
                        }

                        if (newVisits.isNotEmpty() || existingPlace == null) {
                            importedPlaces.add(
                                ValidatedImportPlace(
                                    name = venueName,
                                    lat = firstCheckin.lat,
                                    lon = firstCheckin.lng,
                                    category = "custom",
                                    comment = "",
                                    visits = newVisits
                                )
                            )
                        }
                    } catch (_: Exception) {
                        // Пропускаем это место, продолжаем с остальными
                    }
                }

                if (parseErrors.isNotEmpty()) {
                    ImportResult.PartialSuccess(
                        importedPlaces,
                        listOf(ImportValidationError.InvalidJson)
                    )
                } else {
                    ImportResult.Success(importedPlaces)
                }
            } catch (_: Exception) {
                ImportResult.Error(ImportValidationError.InvalidJson)
            }
        }
    }

    private suspend fun importValidatedPlaces(
        places: List<ValidatedImportPlace>
    ): List<ValidatedImportPlace> {
        return withContext(Dispatchers.IO) {
            val importedPlaces = mutableListOf<ValidatedImportPlace>()
            places.forEach { place ->
                try {
                    val placeId = db.placeDao().insertPlace(
                        PlaceEntity(
                            name = place.name,
                            latitude = place.lat,
                            longitude = place.lon,
                            category = place.category,
                            comment = place.comment,
                            country = null,
                            region = null
                        )
                    )
                    place.visits.forEach { visit ->
                        db.visitDao().insertVisit(
                            VisitEntity(
                                placeId = placeId,
                                timestamp = visit.timestamp,
                                comment = visit.comment,
                                systemNote = "Импортировано",
                                source = VisitSource.IMPORTED_FILE
                            )
                        )
                    }
                    importedPlaces.add(place)
                } catch (_: Exception) {
                    // пропускаем ошибки при импорте одного места
                }
            }
            importedPlaces
        }
    }
}