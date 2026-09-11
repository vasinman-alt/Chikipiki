package com.spotlog.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spotlog.data.AppDatabase
import com.spotlog.data.repository.PlaceRepository
import com.spotlog.util.safeCall
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class RegionStat(
    val region: String,
    val visitCount: Int,
    val firstVisit: Long,
    val lastVisit: Long
)

data class CountryStat(
    val country: String,
    val visitCount: Int,
    val firstVisit: Long,
    val lastVisit: Long,
    val regions: List<RegionStat>
)

data class YearStat(
    val year: Int,
    val visitCount: Int
)

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val repository = PlaceRepository(application, viewModelScope)

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val error: SharedFlow<String> = _error.asSharedFlow()

    val countries: StateFlow<List<CountryStat>> = combine(
        db.placeDao().getCountryStats(),
        db.placeDao().getRegionStats()
    ) { countryStatsRaw, regionStatsRaw ->
        val regionsByCountry = regionStatsRaw.groupBy { it.country }

        countryStatsRaw.map { rawCountry ->
            CountryStat(
                country = rawCountry.country,
                visitCount = rawCountry.visitCount,
                firstVisit = rawCountry.firstVisit,
                lastVisit = rawCountry.lastVisit,
                regions = regionsByCountry[rawCountry.country]?.map { rawRegion ->
                    RegionStat(
                        region = rawRegion.region,
                        visitCount = rawRegion.visitCount,
                        firstVisit = rawRegion.firstVisit,
                        lastVisit = rawRegion.lastVisit
                    )
                } ?: emptyList()
            )
        }
    }
        .onEach { _isLoading.value = false }
        .catch { _error.emit("Не удалось загрузить статистику") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val visitsByYear: StateFlow<List<YearStat>> = db.placeDao().getVisitsByYear()
        .map { rawList ->
            rawList.map { YearStat(year = it.year, visitCount = it.visitCount) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Автоматически запускаем геокодирование для мест без страны
        viewModelScope.launch {
            safeCall(
                onError = { msg -> _error.emit(msg) },
                errorMessage = "Не удалось обновить геоданные"
            ) {
                repository.fillAllMissingCountries()
            }
        }
    }

    fun retryMissingGeocoding() {
        viewModelScope.launch {
            safeCall(
                onError = { msg -> _error.emit(msg) },
                errorMessage = "Не удалось обновить геоданные"
            ) {
                repository.fillAllMissingCountries()
            }
        }
    }
}