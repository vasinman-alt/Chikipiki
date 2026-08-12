// ==== ФАЙЛ: PlaceRepository.kt ====
package com.spotlog.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.spotlog.data.AppDatabase
import com.spotlog.data.NominatimGeocoder
import com.spotlog.data.dao.VisitWithPlace
import com.spotlog.data.entity.*
import com.spotlog.premium.Feature
import com.spotlog.premium.FeatureGate
import com.spotlog.util.PhotoProcessor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class PlaceRepository(
    private val context: Context,
    private val repositoryScope: CoroutineScope
) {
    private val db = AppDatabase.getDatabase(context)
    private val placeDao = db.placeDao()
    private val visitDao = db.visitDao()
    private val geocodeCacheDao = db.geocodeCacheDao()
    private val photoDao = db.photoDao()

    /** Поток всех мест – нужен только для UI‑списков */
    val places: StateFlow<List<PlaceEntity>> = placeDao.getAllPlaces()
        .stateIn(repositoryScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Поток всех визитов (Place+Visit) */
    val visits: StateFlow<List<VisitWithPlace>> = visitDao.getAllVisitsWithPlace()
        .stateIn(repositoryScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Получить место по id (однократный запрос) */
    suspend fun getPlaceById(placeId: Long): PlaceEntity? = withContext(Dispatchers.IO) {
        placeDao.getPlaceById(placeId)
    }

    /** Поток места (для DetailView) */
    suspend fun getPlaceFlow(placeId: Long): Flow<PlaceEntity?> = flow {
        emit(getPlaceById(placeId))
    }

    /** Поток визитов конкретного места */
    suspend fun getVisitsForPlace(placeId: Long): Flow<List<VisitWithPlace>> =
        visitDao.getVisitsForPlace(placeId)

    /** -----------------------------------------------------------------
     *  1️⃣ Добавление обычного (текущего) чек‑ина
     *  ----------------------------------------------------------------- */
    suspend fun addManualCheckin(
        name: String,
        lat: Double,
        lon: Double,
        category: String,
        timestamp: Long,
        comment: String
    ): Long = withContext(Dispatchers.IO) {
        // Ищем уже существующее место рядом с тем же именем
        val existing = placeDao.findNearby(lat, lon)
            .firstOrNull { it.name.equals(name, ignoreCase = true) }

        val placeId = if (existing != null) {
            // Если у найденного места ещё нет country/region – пытаемся дозаполнить асинхронно
            if (existing.country.isNullOrBlank()) {
                repositoryScope.launch { ensureCountryForPlace(existing.id) }
            }
            existing.id
        } else {
            // Новое место – сразу делаем запрос геокодинга (это блокирующий запрос,
            // но происходит в Dispatchers.IO, поэтому UI не «зависает»)
            val (country, region) = NominatimGeocoder.reverseGeocode(context, lat, lon, geocodeCacheDao)
            placeDao.insertPlace(
                PlaceEntity(
                    name = name,
                    latitude = lat,
                    longitude = lon,
                    category = category,
                    country = country,
                    region = region
                )
            )
        }

        // Сохраняем визит
        visitDao.insertVisit(
            VisitEntity(
                placeId = placeId,
                timestamp = timestamp,
                comment = comment
            )
        )
        placeId
    }

    /** -----------------------------------------------------------------
     *  2️⃣ Быстрый чек‑ин без диалога (используется в DetailScreen)
     *  ----------------------------------------------------------------- */
    suspend fun quickCheckin(placeId: Long) = withContext(Dispatchers.IO) {
        visitDao.insertVisit(
            VisitEntity(
                placeId = placeId,
                timestamp = System.currentTimeMillis(),
                comment = ""
            )
        )
    }

    /** -----------------------------------------------------------------
     *  3️⃣ Добавление исторического визита к уже существующему месту
     *  ----------------------------------------------------------------- */
    suspend fun addHistoricalVisitToExistingPlace(
        placeId: Long,
        timestamp: Long,
        comment: String,
        photoUri: String?
    ): Long = withContext(Dispatchers.IO) {
        val visitId = visitDao.insertVisit(
            VisitEntity(
                placeId = placeId,
                timestamp = timestamp,
                comment = comment,
                systemNote = buildImportNote(),
                source = VisitSource.IMPORTED_MANUAL_OLD
            )
        )

        if (!photoUri.isNullOrBlank()) {
            val processedPath = PhotoProcessor.processAndStore(context, Uri.parse(photoUri), keepOriginalSize = false)
            if (processedPath != null) {
                photoDao.insertPhoto(
                    PhotoEntity(
                        placeId = placeId,
                        visitId = visitId,
                        filePath = processedPath,
                        source = PhotoSource.GALLERY
                    )
                )
            }
        }

        // После импорта сразу пытаемся заполнить country/region (может занять время)
        ensureCountryForPlace(placeId)

        visitId
    }

    /** -----------------------------------------------------------------
     *  4️⃣ Обновление данных визита/места
     *  ----------------------------------------------------------------- */
    suspend fun updateCheckinDetails(
        visitId: Long,
        newName: String,
        newCategory: String,
        newComment: String
    ) = withContext(Dispatchers.IO) {
        val visit = visitDao.getVisitById(visitId) ?: return@withContext
        db.withTransaction {
            placeDao.updatePlaceInfo(visit.placeId, newName, newCategory)
            visitDao.updateVisit(visit.copy(comment = newComment))
        }
    }

    /** -----------------------------------------------------------------
     *  5️⃣ Удаление визита (и авто‑удаление места, если оно стало пустым)
     *  ----------------------------------------------------------------- */
    suspend fun deleteVisit(visitId: Long) = withContext(Dispatchers.IO) {
        val visit = visitDao.getVisitById(visitId) ?: return@withContext
        visitDao.deleteVisit(visitId)

        // После удаления проверяем, осталось ли у места хотя бы один визит.
        // Если нет – удаляем само место (каскадно удалятся фото и т.д.).
        val remaining = visitDao.getVisitsForPlace(visit.placeId).firstOrNull()?.size ?: 0
        if (remaining == 0) {
            deletePlace(visit.placeId)
        }
    }

    /** -----------------------------------------------------------------
     *  6️⃣ Обновление информации о месте
     *  ----------------------------------------------------------------- */
    suspend fun updatePlaceInfo(placeId: Long, newName: String, newCategory: String, newComment: String) = withContext(Dispatchers.IO) {
        db.withTransaction {
            placeDao.updatePlaceInfo(placeId, newName, newCategory)
            placeDao.updatePlaceComment(placeId, newComment)
        }
    }

    /** -----------------------------------------------------------------
     *  7️⃣ Фото‑операции
     *  ----------------------------------------------------------------- */
    suspend fun addPhotoToPlace(
        placeId: Long,
        photoPath: String,
        source: PhotoSource,
        featureGate: FeatureGate
    ) = withContext(Dispatchers.IO) {
        require(featureGate.isEnabled(Feature.PHOTOS)) { "Photos require premium" }
        photoDao.insertPhoto(
            PhotoEntity(
                placeId = placeId,
                filePath = photoPath,
                isCover = false,
                source = source
            )
        )
    }

    suspend fun addPhotoToVisit(
        visitId: Long,
        placeId: Long,
        photoPath: String,
        featureGate: FeatureGate
    ) = withContext(Dispatchers.IO) {
        require(featureGate.isEnabled(Feature.PHOTOS)) { "Photos require premium" }
        photoDao.insertPhoto(
            PhotoEntity(
                placeId = placeId,
                visitId = visitId,
                filePath = photoPath,
                source = PhotoSource.CAMERA
            )
        )
    }

    /** -----------------------------------------------------------------
     *  8️⃣ Удаление фото (физический файл тоже удаляется)
     *  ----------------------------------------------------------------- */
    suspend fun deletePhoto(photoId: Long) = withContext(Dispatchers.IO) {
        val photo = photoDao.getPhotoById(photoId) ?: return@withContext
        photoDao.deletePhoto(photoId)
        File(photo.filePath).delete()
    }

    /** -----------------------------------------------------------------
     *  9️⃣ Управление обложкой
     *  ----------------------------------------------------------------- */
    suspend fun setCoverPhoto(placeId: Long, photoId: Long) = withContext(Dispatchers.IO) {
        db.withTransaction {
            photoDao.clearCover(placeId)
            photoDao.setCover(photoId)
        }
    }

    suspend fun clearCoverPhoto(photoId: Long) = withContext(Dispatchers.IO) {
        photoDao.clearCoverForPhoto(photoId)
    }

    /** -----------------------------------------------------------------
     * 10️⃣ Геокодинг (получаем country/region для места)
     *  ----------------------------------------------------------------- */
    suspend fun updatePlaceCountry(placeId: Long, country: String?, region: String?) = withContext(Dispatchers.IO) {
        placeDao.updatePlaceCountry(placeId, country, region)
    }

    suspend fun ensureCountryForPlace(placeId: Long) = withContext(Dispatchers.IO) {
        val place = placeDao.getPlaceById(placeId) ?: return@withContext
        if (!place.country.isNullOrBlank()) return@withContext
        try {
            val (country, region) = NominatimGeocoder.reverseGeocode(context, place.latitude, place.longitude, geocodeCacheDao)
            if (country != null) {
                placeDao.updatePlaceCountry(placeId, country, region)
            }
        } catch (e: Exception) {
            Log.e("PlaceRepository", "ensureCountryForPlace failed for placeId=$placeId", e)
        }
    }

    /** -----------------------------------------------------------------
     * 11️⃣ Запуск фонового «заполнения» всех недостающих country/region
     *  ----------------------------------------------------------------- */
    suspend fun fillAllMissingCountries(limit: Int = 100) = withContext(Dispatchers.IO) {
        val allPlaces = placeDao.getAllPlaces().first()
        val missing = allPlaces.filter { it.country.isNullOrBlank() }.take(limit)
        missing.forEachIndexed { index, place ->
            if (index > 0) delay(2000)          // небольшая пауза – не превышаем лимит Nominatim
            ensureCountryForPlace(place.id)
        }
    }

    /** -----------------------------------------------------------------
     * 12️⃣ Удаление места (используется в карте и в репозитории)
     *  ----------------------------------------------------------------- */
    suspend fun deletePlace(placeId: Long) = withContext(Dispatchers.IO) {
        placeDao.deletePlace(placeId) // ON DELETE CASCADE уберёт визиты и фото автоматически
    }

    /** -----------------------------------------------------------------
     * 13️⃣ Внутренняя утилита для формата системной заметки импорта
     *  ----------------------------------------------------------------- */
    private fun buildImportNote(): String {
        val today = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
        return "Добавлено $today"
    }
}
