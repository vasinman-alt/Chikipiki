// ==== ФАЙЛ: PlaceRepository.kt ====
package com.spotlog.data.repository

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.spotlog.data.AppDatabase
import com.spotlog.data.NominatimGeocoder
import com.spotlog.data.dao.VisitWithPlace
import com.spotlog.data.entity.*
import com.spotlog.premium.Feature
import com.spotlog.premium.FeatureGate
import com.spotlog.util.CHECKIN_RADIUS_METERS
import com.spotlog.util.calculateDistance
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

    val places: StateFlow<List<PlaceEntity>> = placeDao.getAllPlaces()
        .stateIn(repositoryScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val visits: StateFlow<List<VisitWithPlace>> = visitDao.getAllVisitsWithPlace()
        .stateIn(repositoryScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun getPlaceById(placeId: Long): PlaceEntity? = withContext(Dispatchers.IO) {
        placeDao.getPlaceById(placeId)
    }

    suspend fun getPlaceFlow(placeId: Long): Flow<PlaceEntity?> = flow {
        emit(getPlaceById(placeId))
    }

    suspend fun getVisitsForPlace(placeId: Long): Flow<List<VisitWithPlace>> =
        visitDao.getVisitsForPlace(placeId)

    /** 1️⃣ Добавление обычного (текущего) чек‑ина */
    suspend fun addManualCheckin(
        name: String,
        lat: Double,
        lon: Double,
        category: String,
        timestamp: Long,
        comment: String
    ): Long = withContext(Dispatchers.IO) {
        val existing = placeDao.findNearby(lat, lon)
            .firstOrNull { it.name.equals(name, ignoreCase = true) }

        val placeId = if (existing != null) {
            if (existing.country.isNullOrBlank()) {
                repositoryScope.launch { ensureCountryForPlace(existing.id) }
            }
            existing.id
        } else {
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

        visitDao.insertVisit(
            VisitEntity(
                placeId = placeId,
                timestamp = timestamp,
                comment = comment
            )
        )
        placeId
    }

    /** 2️⃣ Быстрый чек‑ин (используется в DetailScreen) */
    suspend fun quickCheckin(placeId: Long) = withContext(Dispatchers.IO) {
        visitDao.insertVisit(
            VisitEntity(
                placeId = placeId,
                timestamp = System.currentTimeMillis(),
                comment = ""
            )
        )
    }

    /**
     * FIX 1: добавление исторического визита — без photoUri.
     * Исторические визиты не могут иметь фото (бизнес‑правило).
     */
    suspend fun addHistoricalVisitToExistingPlace(
        placeId: Long,
        timestamp: Long,
        comment: String
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

        ensureCountryForPlace(placeId)
        visitId
    }

    /** 3️⃣ Обновление данных визита/места */
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

    /** 4️⃣ Удаление визита (с авто‑удалением места) */
    suspend fun deleteVisit(visitId: Long) = withContext(Dispatchers.IO) {
        val visit = visitDao.getVisitById(visitId) ?: return@withContext
        visitDao.deleteVisit(visitId)

        val remaining = visitDao.getVisitsForPlace(visit.placeId).firstOrNull()?.size ?: 0
        if (remaining == 0) {
            deletePlace(visit.placeId)
        }
    }

    /** 5️⃣ Обновление информации о месте */
    suspend fun updatePlaceInfo(placeId: Long, newName: String, newCategory: String, newComment: String) = withContext(Dispatchers.IO) {
        db.withTransaction {
            placeDao.updatePlaceInfo(placeId, newName, newCategory)
            placeDao.updatePlaceComment(placeId, newComment)
        }
    }

    /** 6️⃣ Фото‑операции (для МЕСТА, не для визита) */
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

    /**
     * Прикрепить фото (с камеры) к визиту — со вторым уровнем валидации.
     * Только для ручных визитов сегодняшней даты в пределах радиуса чекина.
     */
    suspend fun addPhotoToVisitValidated(
        visitId: Long,
        placeId: Long,
        photoPath: String,
        currentLat: Double?,
        currentLon: Double?,
        featureGate: FeatureGate
    ) = withContext(Dispatchers.IO) {
        require(featureGate.isEnabled(Feature.PHOTOS)) { "Photos require premium" }

        val visit = visitDao.getVisitById(visitId)
            ?: throw IllegalStateException("Визит не найден")
        if (visit.placeId != placeId) {
            throw IllegalStateException("Визит не принадлежит этому месту")
        }
        if (visit.source != VisitSource.MANUAL) {
            throw IllegalStateException("Фото можно добавлять только к ручным визитам")
        }

        val place = placeDao.getPlaceById(placeId)
        if (place != null && currentLat != null && currentLon != null) {
            val dist = calculateDistance(currentLat, currentLon, place.latitude, place.longitude)
            if (dist > CHECKIN_RADIUS_METERS) {
                throw IllegalStateException("Слишком далеко от места (${dist.toInt()} м)")
            }
        }

        if (!isSameDay(visit.timestamp, System.currentTimeMillis())) {
            throw IllegalStateException("Фото можно прикрепить только в день визита")
        }

        val existingPhotos = photoDao.getPhotosForPlace(placeId).first()
        if (existingPhotos.any { it.visitId == visitId }) {
            throw IllegalStateException("К этому визиту уже прикреплено фото")
        }

        photoDao.insertPhoto(
            PhotoEntity(
                placeId = placeId,
                visitId = visitId,
                filePath = photoPath,
                source = PhotoSource.CAMERA
            )
        )
    }

    /** 7️⃣ Удаление фото */
    suspend fun deletePhoto(photoId: Long) = withContext(Dispatchers.IO) {
        val photo = photoDao.getPhotoById(photoId) ?: return@withContext
        photoDao.deletePhoto(photoId)
        File(photo.filePath).delete()
    }

    /** 8️⃣ Управление обложкой */
    suspend fun setCoverPhoto(placeId: Long, photoId: Long) = withContext(Dispatchers.IO) {
        db.withTransaction {
            photoDao.clearCover(placeId)
            photoDao.setCover(photoId)
        }
    }

    suspend fun clearCoverPhoto(photoId: Long) = withContext(Dispatchers.IO) {
        photoDao.clearCoverForPhoto(photoId)
    }

    /** 9️⃣ Геокодинг */
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

    suspend fun fillAllMissingCountries(limit: Int = 100) = withContext(Dispatchers.IO) {
        val allPlaces = placeDao.getAllPlaces().first()
        val missing = allPlaces.filter { it.country.isNullOrBlank() }.take(limit)
        missing.forEachIndexed { index, place ->
            if (index > 0) delay(2000)
            ensureCountryForPlace(place.id)
        }
    }

    /** 🔟 Удаление места */
    suspend fun deletePlace(placeId: Long) = withContext(Dispatchers.IO) {
        placeDao.deletePlace(placeId)
    }

    /** Сравнение по yyyy-MM-dd в локальной TZ. */
    private fun isSameDay(t1: Long, t2: Long): Boolean {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return fmt.format(Date(t1)) == fmt.format(Date(t2))
    }

    /**
     * FIX 2: формулировка системной пометки — "Добавлен ДД.ММ.ГГГГ"
     * (мужской род, согласуется с "визит", а не "место").
     */
    private fun buildImportNote(): String {
        val today = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
        return "Добавлен $today"
    }
}
