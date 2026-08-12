package com.spotlog.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spotlog.data.AppDatabase
import com.spotlog.data.repository.PlaceRepository
import com.spotlog.location.LocationProvider
import com.spotlog.util.calculateDistance
import com.spotlog.util.safeCall
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SortMode { BY_DATE, BY_DISTANCE, BY_ALPHABET }

data class PlaceCardUi(
    val placeId: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val category: String,
    val country: String?,
    val region: String?,
    val comment: String,
    val visitCount: Int,
    val lastVisitTimestamp: Long?,
    val distanceMeters: Double? = null,
    val coverPhotoPath: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class PlacesViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = PlaceRepository(application, viewModelScope)
    private val locationProvider = LocationProvider.getInstance()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.BY_DATE)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private val _error = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val error: SharedFlow<String> = _error.asSharedFlow()

    private val allCards: StateFlow<List<PlaceCardUi>> = combine(
        db.placeDao().getPlaceCardsWithCover(),
        locationProvider.currentLocation
    ) { cardsWithCover, location ->
        cardsWithCover.map { card ->
            val distance = location?.let {
                calculateDistance(it.latitude, it.longitude, card.latitude, card.longitude)
            }
            PlaceCardUi(
                placeId = card.placeId,
                name = card.name,
                latitude = card.latitude,
                longitude = card.longitude,
                category = card.category,
                country = card.country,
                region = card.region,
                comment = card.comment,
                visitCount = card.visitCount,
                lastVisitTimestamp = card.lastVisitTimestamp,
                distanceMeters = distance,
                coverPhotoPath = card.coverPhotoPath
            )
        }
    }
        .onEach { _isLoading.value = false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val placeCards: StateFlow<List<PlaceCardUi>> = combine(
        allCards,
        _searchQuery,
        _sortMode
    ) { cards, query, sortMode ->
        val filtered = if (query.isBlank()) cards
        else cards.filter { it.name.contains(query, ignoreCase = true) }

        when (sortMode) {
            SortMode.BY_DATE -> filtered.sortedByDescending { it.lastVisitTimestamp ?: 0L }
            SortMode.BY_DISTANCE -> filtered.sortedBy { it.distanceMeters ?: Double.MAX_VALUE }
            SortMode.BY_ALPHABET -> filtered.sortedBy { it.name.lowercase() }
        }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.fillAllMissingCountries()
        }
    }

    fun deletePlace(placeId: Long) {
        viewModelScope.launch {
            safeCall(
                onError = { msg -> _error.emit(msg) },
                errorMessage = "Не удалось удалить место"
            ) {
                repository.deletePlace(placeId)
            }
        }
    }
}