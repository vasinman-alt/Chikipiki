// ==== ФАЙЛ: MapViewModel.kt ====
package com.spotlog.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spotlog.data.AppDatabase
import com.spotlog.data.PoiRepository
import com.spotlog.data.entity.PlaceEntity
import com.spotlog.data.repository.PlaceRepository
import com.spotlog.location.LocationProvider
import com.spotlog.model.PoiData
import com.spotlog.util.safeCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.osmdroid.util.GeoPoint

/* -------------------------------------------------------------
 *  События для UI (удаление места, диалоги и т.д.)
 * ------------------------------------------------------------- */
sealed class MapEvent {
    data class ShowDeletePlaceDialog(val placeId: Long, val placeName: String) : MapEvent()
}

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PlaceRepository(application, viewModelScope)
    private val poiRepository = PoiRepository(application, viewModelScope)
    private val locationProvider = LocationProvider.getInstance()
    private val db = AppDatabase.getDatabase(application)

    private val _places = MutableStateFlow<List<PlaceEntity>>(emptyList())
    val places: StateFlow<List<PlaceEntity>> = _places.asStateFlow()

    private val _currentLocation = MutableStateFlow<GeoPoint?>(null)
    val currentLocation: StateFlow<GeoPoint?> = _currentLocation.asStateFlow()

    private val _isLocating = MutableStateFlow(false)
    val isLocating: StateFlow<Boolean> = _isLocating.asStateFlow()

    private val _isInitialLocationLoaded = MutableStateFlow(false)
    val isInitialLocationLoaded: StateFlow<Boolean> = _isInitialLocationLoaded.asStateFlow()

    private val _errorEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    // ---------------- POI‑поиск ----------------
    private val _poiSearchQuery = MutableStateFlow("")
    val poiSearchQuery: StateFlow<String> = _poiSearchQuery.asStateFlow()

    private val _activeFilterId = MutableStateFlow<String?>(null)
    val activeFilterId: StateFlow<String?> = _activeFilterId.asStateFlow()

    private val _allSearchResults = MutableStateFlow<List<PoiData>>(emptyList())
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    val filteredSearchResults: StateFlow<List<PoiData>> = combine(
        _allSearchResults,
        _poiSearchQuery,
        _activeFilterId
    ) { results, query, filterId ->
        val filter = CategoryFilter.ALL_FILTERS.find { it.id == filterId } ?: CategoryFilter.ALL
        results.filter { poi ->
            val matchesQuery = query.isBlank() || poi.name.contains(query, ignoreCase = true)
            val matchesCategory = filter.categories.isEmpty() ||
                    filter.categories.any { cat -> poi.category.contains(cat, ignoreCase = true) }
            matchesQuery && matchesCategory
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---------------- UI‑события ----------------
    private val _mapEvent = MutableSharedFlow<MapEvent>(extraBufferCapacity = 1)
    val mapEvent: SharedFlow<MapEvent> = _mapEvent.asSharedFlow()

    fun emitMapEvent(event: MapEvent) = viewModelScope.launch {
        _mapEvent.emit(event)
    }

    init {
        // Подписка на все места из БД
        viewModelScope.launch {
            db.placeDao().getAllPlaces().collect { list ->
                _places.value = list
            }
        }
        // Подписка на изменения геопозиции
        viewModelScope.launch {
            locationProvider.currentLocation.collect { loc ->
                _currentLocation.value = loc
            }
        }
    }

    fun hasLocationPermission(): Boolean = locationProvider.hasPermission()

    fun markInitialLocationLoaded() {
        _isInitialLocationLoaded.value = true
    }

    /**
     * FIX: мгновенно отдаём last known location (если есть), параллельно пытаемся
     * получить более точный GPS‑фикс с таймаутом 10 сек. Это устраняет «долго
     * думает, не сразу находит».
     */
    fun fetchCurrentLocation(
        onResult: (GeoPoint) -> Unit = {},
        showError: Boolean = true
    ) {
        if (!hasLocationPermission()) return

        _isLocating.value = true

        viewModelScope.launch {
            // 1️⃣ Мгновенно (без таймаута) пытаемся взять кэшированную точку
            val cached = locationProvider.lastKnownLocation()
            if (cached != null) {
                _currentLocation.value = cached
                onResult(cached)
            }

            // 2️⃣ Параллельно запускаем точный фикс (без блокировки UI)
            locationProvider.refresh(viewModelScope)

            // 3️⃣ Ждём «свежий» фикс максимум 10 сек
            try {
                val fresh = withTimeoutOrNull(10_000L) {
                    locationProvider.currentLocation
                        .filter { it != null && it != cached }
                        .first()
                }
                if (fresh != null) {
                    _currentLocation.value = fresh
                    onResult(fresh)
                } else if (showError && _currentLocation.value == null) {
                    _errorEvents.emit("Не удалось получить местоположение")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (showError && _currentLocation.value == null) {
                    _errorEvents.emit("Ошибка геолокации")
                }
            } finally {
                _isLocating.value = false
            }
        }
    }

    /** Поиск POI рядом с указанной точкой */
    fun searchNearby(center: GeoPoint) {
        viewModelScope.launch {
            _isSearching.value = true
            safeCall(
                onError = { msg -> _errorEvents.emit(msg) },
                errorMessage = "Не удалось выполнить поиск POI"
            ) {
                val results = poiRepository.getNearby(center.latitude, center.longitude)
                _allSearchResults.value = results
            }
            _isSearching.value = false
        }
    }

    fun setPoiSearchQuery(query: String) {
        _poiSearchQuery.value = query
    }

    fun setCategoryFilter(filterId: String?) {
        _activeFilterId.value = filterId
    }

    fun addManualVisit(
        lat: Double,
        lon: Double,
        name: String,
        category: String,
        comment: String,
        timestamp: Long
    ) {
        viewModelScope.launch {
            safeCall(
                onError = { msg -> _errorEvents.emit(msg) },
                errorMessage = "Ошибка добавления места"
            ) {
                repository.addManualCheckin(
                    name = name,
                    lat = lat,
                    lon = lon,
                    category = category,
                    timestamp = timestamp,
                    comment = comment
                )
            }
        }
    }

    fun addPlaceFromPoi(poi: PoiData) {
        viewModelScope.launch {
            safeCall(
                onError = { msg -> _errorEvents.emit(msg) },
                errorMessage = "Ошибка добавления POI"
            ) {
                repository.addManualCheckin(
                    name = poi.name,
                    lat = poi.lat,
                    lon = poi.lon,
                    category = poi.category,
                    timestamp = System.currentTimeMillis(),
                    comment = ""
                )
            }
        }
    }

    fun deletePlace(placeId: Long) = viewModelScope.launch {
        safeCall(
            onError = { msg -> _errorEvents.emit(msg) },
            errorMessage = "Не удалось удалить место"
        ) {
            repository.deletePlace(placeId)
        }
    }
}
