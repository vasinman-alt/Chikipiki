// ==== ФАЙЛ: PlaceDetailViewModel.kt ====
package com.spotlog.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spotlog.data.AppDatabase
import com.spotlog.data.dao.VisitWithPlace
import com.spotlog.data.entity.PhotoEntity
import com.spotlog.data.entity.PhotoSource
import com.spotlog.data.entity.PlaceEntity
import com.spotlog.data.entity.VisitEntity
import com.spotlog.data.entity.VisitSource
import com.spotlog.data.repository.PlaceRepository
import com.spotlog.location.LocationProvider
import com.spotlog.premium.Feature
import com.spotlog.premium.FeatureGate
import com.spotlog.premium.PremiumFeatureGate
import com.spotlog.premium.PremiumManager
import com.spotlog.util.CHECKIN_RADIUS_METERS
import com.spotlog.util.PhotoProcessor
import com.spotlog.util.calculateDistance
import com.spotlog.util.safeCall
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class PlaceDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PlaceRepository(application, viewModelScope)
    private val locationProvider = LocationProvider.getInstance()
    private val db = AppDatabase.getDatabase(application)
    private val featureGate: FeatureGate = PremiumFeatureGate(PremiumManager(application))

    private val _placeId = MutableStateFlow<Long?>(null)

    val place: StateFlow<PlaceEntity?> = _placeId.flatMapLatest { id ->
        if (id != null) repository.getPlaceFlow(id) else flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val visits: StateFlow<List<VisitWithPlace>> = _placeId.flatMapLatest { id ->
        if (id != null) repository.getVisitsForPlace(id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _photos = MutableStateFlow<List<PhotoEntity>>(emptyList())
    val photos: StateFlow<List<PhotoEntity>> = _photos.asStateFlow()

    private val _canCheckinReason = MutableStateFlow<String?>(null)
    val canCheckinReason: StateFlow<String?> = _canCheckinReason.asStateFlow()

    private val _error = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val error: SharedFlow<String> = _error.asSharedFlow()

    private fun tickerFlow(intervalMs: Long = 1000L) = flow {
        while (true) {
            emit(Unit)
            delay(intervalMs)
        }
    }

    val canCheckin: StateFlow<Boolean> = combine(
        place.filterNotNull(),
        locationProvider.currentLocation.filterNotNull(),
        tickerFlow()
    ) { place, location, _ ->
        val dist = calculateDistance(location.latitude, location.longitude, place.latitude, place.longitude)
        val distStr = if (dist >= 1000) "%.1f км".format(dist / 1000) else "${dist.toInt()} м"
        val lastFix = locationProvider.getLastFixTimeMs()
        val isFresh = System.currentTimeMillis() - lastFix < 30_000
        val withinRadius = dist <= CHECKIN_RADIUS_METERS

        _canCheckinReason.value = when {
            !locationProvider.hasPermission() -> "Нужен доступ к геолокации"
            !isFresh -> "Определяем местоположение…"
            !withinRadius -> "Слишком далеко (~$distStr)"
            else -> null
        }
        withinRadius && isFresh && locationProvider.hasPermission()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun init(placeId: Long) {
        _placeId.value = placeId
        if (locationProvider.hasPermission()) {
            locationProvider.refresh(viewModelScope)
        }
        loadPhotos(placeId)
    }

    private fun loadPhotos(placeId: Long) {
        viewModelScope.launch {
            db.photoDao().getPhotosForPlace(placeId).collect {
                _photos.value = it
            }
        }
    }

    /** Фото к месту (из галереи) */
    fun addPhotoToPlace(uri: Uri) {
        val p = place.value ?: return
        viewModelScope.launch {
            safeCall(
                onError = { msg -> _error.emit(msg) },
                errorMessage = "Ошибка добавления фото"
            ) {
                val keepOriginalSize = featureGate.isEnabled(Feature.ORIGINAL_PHOTO_QUALITY)
                val path = PhotoProcessor.processAndStore(getApplication(), uri, keepOriginalSize)
                if (path != null) {
                    repository.addPhotoToPlace(p.id, path, PhotoSource.GALLERY, featureGate)
                } else {
                    _error.emit("Не удалось обработать фото")
                }
            }
        }
    }

    fun deletePhoto(photoId: Long) {
        viewModelScope.launch {
            safeCall(
                onError = { msg -> _error.emit(msg) },
                errorMessage = "Ошибка удаления фото"
            ) {
                repository.deletePhoto(photoId)
            }
        }
    }

    fun setCoverPhoto(photoId: Long) {
        val p = place.value ?: return
        viewModelScope.launch {
            safeCall(
                onError = { msg -> _error.emit(msg) },
                errorMessage = "Ошибка установки обложки"
            ) {
                repository.setCoverPhoto(p.id, photoId)
            }
        }
    }

    fun updatePlaceDetails(newName: String, newCategory: String, newComment: String) {
        val p = place.value ?: return
        viewModelScope.launch {
            safeCall(
                onError = { msg -> _error.emit(msg) },
                errorMessage = "Ошибка обновления места"
            ) {
                repository.updatePlaceInfo(p.id, newName, newCategory, newComment)
            }
        }
    }

    /** «Отметиться» = мгновенное создание визита */
    fun addVisit(timestamp: Long = System.currentTimeMillis(), comment: String = "") {
        val p = place.value ?: return
        viewModelScope.launch {
            safeCall(
                onError = { msg -> _error.emit(msg) },
                errorMessage = "Ошибка отметки"
            ) {
                repository.addManualCheckin(
                    name = p.name,
                    lat = p.latitude,
                    lon = p.longitude,
                    category = p.category,
                    timestamp = timestamp,
                    comment = comment
                )
            }
        }
    }

    /**
     * FIX: добавление исторического визита — без photoUri.
     * Фото для исторических визитов запрещено бизнес‑правилом.
     */
    fun addHistoricalVisit(timestamp: Long, comment: String) {
        val placeId = _placeId.value ?: return
        viewModelScope.launch {
            safeCall(
                onError = { msg -> _error.emit(msg) },
                errorMessage = "Ошибка добавления исторического визита"
            ) {
                repository.addHistoricalVisitToExistingPlace(placeId, timestamp, comment)
            }
        }
    }

    fun updateVisitComment(visitId: Long, newComment: String) {
        viewModelScope.launch {
            safeCall(
                onError = { msg -> _error.emit(msg) },
                errorMessage = "Ошибка обновления комментария"
            ) {
                repository.updateCheckinDetails(visitId, place.value?.name ?: "", place.value?.category ?: "", newComment)
            }
        }
    }

    fun deleteVisit(visitId: Long) {
        viewModelScope.launch {
            safeCall(
                onError = { msg -> _error.emit(msg) },
                errorMessage = "Ошибка удаления визита"
            ) {
                repository.deleteVisit(visitId)
            }
        }
    }

    fun addPhotoToVisit(visitId: Long, uri: Uri) {
        val p = place.value ?: return
        viewModelScope.launch {
            safeCall(
                onError = { msg -> _error.emit(msg) },
                errorMessage = "Ошибка прикрепления фото"
            ) {
                val keepOriginalSize = featureGate.isEnabled(Feature.ORIGINAL_PHOTO_QUALITY)
                val path = PhotoProcessor.processAndStore(getApplication(), uri, keepOriginalSize)
                if (path == null) {
                    _error.emit("Не удалось обработать фото")
                    return@safeCall
                }
                val loc = locationProvider.currentLocation.value
                repository.addPhotoToVisitValidated(
                    visitId = visitId,
                    placeId = p.id,
                    photoPath = path,
                    currentLat = loc?.latitude,
                    currentLon = loc?.longitude,
                    featureGate = featureGate
                )
            }
        }
    }

    /** UI‑уровневая проверка (для задизейбливания кнопки). */
    fun canAttachPhotoToVisit(
        visitTimestamp: Long,
        placeLat: Double,
        placeLon: Double
    ): Boolean {
        val loc = locationProvider.currentLocation.value ?: return false
        if (!locationProvider.hasPermission()) return false
        val dist = calculateDistance(loc.latitude, loc.longitude, placeLat, placeLon)
        if (dist > CHECKIN_RADIUS_METERS) return false
        return isSameDay(visitTimestamp, System.currentTimeMillis())
    }

    /** Пояснение, почему кнопка фото не активна. */
    fun photoBlockedReason(
        visitTimestamp: Long,
        placeLat: Double,
        placeLon: Double
    ): String? {
        if (!locationProvider.hasPermission()) return "Нужен доступ к геолокации"
        val loc = locationProvider.currentLocation.value ?: return "Определяем местоположение…"
        val dist = calculateDistance(loc.latitude, loc.longitude, placeLat, placeLon)
        if (dist > CHECKIN_RADIUS_METERS) {
            val distStr = if (dist >= 1000) "%.1f км".format(dist / 1000) else "${dist.toInt()} м"
            return "Слишком далеко (~$distStr)"
        }
        if (!isSameDay(visitTimestamp, System.currentTimeMillis())) {
            return "Фото можно прикрепить только в день визита"
        }
        return null
    }

    /** Возвращает true, если к визиту можно прикрепить фото (только для MANUAL). */
    fun visitAllowsPhoto(visit: VisitWithPlace): Boolean {
        // FIX: проверяем именно source, а не только ручной тип
        return visit.source == VisitSource.MANUAL.name
    }

    private fun isSameDay(t1: Long, t2: Long): Boolean {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return fmt.format(java.util.Date(t1)) == fmt.format(java.util.Date(t2))
    }

    fun refreshLocation() {
        if (locationProvider.hasPermission()) {
            locationProvider.refresh(viewModelScope)
        }
    }
}
