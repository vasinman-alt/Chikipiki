package com.spotlog.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spotlog.data.dao.VisitWithPlace
import com.spotlog.data.entity.PhotoEntity
import com.spotlog.data.entity.PlaceEntity
import com.spotlog.data.entity.PhotoSource
import com.spotlog.data.repository.PlaceRepository
import com.spotlog.location.LocationProvider
import com.spotlog.premium.Feature
import com.spotlog.premium.FeatureGate
import com.spotlog.premium.PremiumFeatureGate
import com.spotlog.util.calculateDistance
import com.spotlog.util.safeCall
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class PlaceDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PlaceRepository(application, viewModelScope)
    private val locationProvider = LocationProvider.getInstance()
    private val featureGate: FeatureGate = PremiumFeatureGate()

    private val _place = MutableStateFlow<PlaceEntity?>(null)
    val place: StateFlow<PlaceEntity?> = _place.asStateFlow()

    private val _visits = MutableStateFlow<List<VisitWithPlace>>(emptyList())
    val visits: StateFlow<List<VisitWithPlace>> = _visits.asStateFlow()

    private val _photos = MutableStateFlow<List<PhotoEntity>>(emptyList())
    val photos: StateFlow<List<PhotoEntity>> = _photos.asStateFlow()

    private val checkinState = combine(
        _place,
        locationProvider.currentLocation
    ) { place, loc ->
        when {
            place == null -> Pair(false, null)
            loc == null -> Pair(false, "Нет геолокации")
            else -> {
                val distance = calculateDistance(place.latitude, place.longitude, loc.latitude, loc.longitude)
                val maxDistance = 100.0
                if (distance <= maxDistance) {
                    Pair(true, "Отметиться")
                } else {
                    Pair(false, "Слишком далеко (${distance.toInt()} м)")
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = Pair(false, null)
    )

    val canCheckin: StateFlow<Boolean> = checkinState
        .map { it.first }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val canCheckinReason: StateFlow<String?> = checkinState
        .map { it.second }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _error = MutableSharedFlow<String>()
    val error: SharedFlow<String> = _error.asSharedFlow()

    fun init(placeId: Long) {
        viewModelScope.launch {
            repository.getPlaceFlow(placeId).collect { p ->
                _place.value = p
            }
        }
        viewModelScope.launch {
            repository.getVisitsForPlace(placeId).collect { v ->
                _visits.value = v
            }
        }
        viewModelScope.launch {
            repository.getPhotosForPlaceFlow(placeId).collect { p ->
                _photos.value = p
            }
        }
    }

    fun addVisit() {
        val p = _place.value ?: return
        viewModelScope.launch {
            safeCall(
                onError = { msg -> _error.emit(msg) },
                errorMessage = "Ошибка добавления визита"
            ) {
                val loc = locationProvider.currentLocation.value
                repository.addVisitToPlace(
                    placeId = p.id,
                    timestamp = System.currentTimeMillis(),
                    comment = "",
                    currentLat = loc?.latitude,
                    currentLon = loc?.longitude
                )
            }
        }
    }

    fun updateVisitComment(visitId: Long, comment: String) {
        viewModelScope.launch {
            safeCall(
                onError = { msg -> _error.emit(msg) },
                errorMessage = "Ошибка обновления комментария"
            ) {
                repository.updateVisitComment(visitId, comment)
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

    fun addHistoricalVisit(timestamp: Long, comment: String) {
        val p = _place.value ?: return
        viewModelScope.launch {
            safeCall(
                onError = { msg -> _error.emit(msg) },
                errorMessage = "Ошибка добавления визита"
            ) {
                repository.addHistoricalVisitToExistingPlace(
                    placeId = p.id,
                    timestamp = timestamp,
                    comment = comment
                )
            }
        }
    }

    fun updatePlaceDetails(name: String, category: String, comment: String) {
        val p = _place.value ?: return
        viewModelScope.launch {
            safeCall(
                onError = { msg -> _error.emit(msg) },
                errorMessage = "Ошибка обновления места"
            ) {
                repository.updatePlaceInfo(p.id, name, category, comment)
            }
        }
    }

    fun addPhotoToPlace(uri: Uri) {
        val p = _place.value ?: return
        viewModelScope.launch {
            safeCall(
                onError = { msg -> _error.emit(msg) },
                errorMessage = "Ошибка добавления фото"
            ) {
                val keepOriginalSize = featureGate.isEnabled(Feature.ORIGINAL_PHOTO_QUALITY)
                val path = com.spotlog.util.PhotoProcessor.processAndStore(getApplication(), uri, keepOriginalSize)
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
        val p = _place.value ?: return
        viewModelScope.launch {
            safeCall(
                onError = { msg -> _error.emit(msg) },
                errorMessage = "Ошибка установки обложки"
            ) {
                repository.setCoverPhoto(p.id, photoId)
            }
        }
    }

    fun addPhotoToVisit(visitId: Long, uri: Uri) {
        val p = _place.value ?: return
        viewModelScope.launch {
            safeCall(
                onError = { msg -> _error.emit(msg) },
                errorMessage = "Ошибка прикрепления фото"
            ) {
                val keepOriginalSize = featureGate.isEnabled(Feature.ORIGINAL_PHOTO_QUALITY)
                val path = com.spotlog.util.PhotoProcessor.processAndStore(getApplication(), uri, keepOriginalSize)
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
}