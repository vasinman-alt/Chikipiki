package com.spotlog.data.repository

import android.content.Context
import com.spotlog.data.AppDatabase
import com.spotlog.data.ImportParser
import com.spotlog.data.ImportResult
import com.spotlog.data.ValidatedImportPlace
import com.spotlog.data.entity.VisitEntity
import com.spotlog.data.entity.VisitSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ImportRepository(
    private val context: Context
) {
    private val db = AppDatabase.getDatabase(context)

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
            } catch (e: Exception) {
                ImportResult.Error(com.spotlog.data.ImportValidationError.InvalidJson)
            }
        }
    }

    private suspend fun importValidatedPlaces(places: List<ValidatedImportPlace>): List<ValidatedImportPlace> {
        return withContext(Dispatchers.IO) {
            val importedPlaces = mutableListOf<ValidatedImportPlace>()
            places.forEach { place ->
                try {
                    val placeId = db.placeDao().insertPlace(
                        com.spotlog.data.entity.PlaceEntity(
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
                } catch (e: Exception) {
                    // пропускаем ошибки при импорте одного места
                }
            }
            importedPlaces
        }
    }
}