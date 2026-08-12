// ==== ФАЙЛ: StatisticsViewModel.kt ====
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
    val lastVisit: Long
)

data class CountryStat(
    val country: String,
    val visitCount: Int,
    val lastVisit: Long,
    val regions: List<RegionStat>
)

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)

    // Один общий репозиторий – используем уже существующий, чтобы не открывать новые потоки
    private val repository = PlaceRepository(application, viewModelScope)

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val error: SharedFlow<String> = _error.asSharedFlow()

    /**
     * Статистика собирается из двух Flow‑запросов DAO.
     * Благодаря тому, что в PlaceDao запросы объявлены как Flow,
     * при любой базе‑операции (добавление визита, геокодинг, удаление) список
     * автоматически пересчитывается и UI получает новый список без
     * дополнительных действий.
     */
    val countries: StateFlow<List<CountryStat>> = combine(
        db.placeDao().getCountryStats(),
        db.placeDao().getRegionStats()
    ) { countryStatsRaw, regionStatsRaw ->
        // Сгруппировать регионы по стране
        val regionsByCountry = regionStatsRaw.groupBy { it.country }

        // Преобразовать каждый CountryStatRaw в наш UI‑модель
        countryStatsRaw.map { rawCountry ->
            CountryStat(
                country = rawCountry.country,
                visitCount = rawCountry.visitCount,
                lastVisit = rawCountry.lastVisit,
                regions = regionsByCountry[rawCountry.country]?.map { rawRegion ->
                    RegionStat(
                        region = rawRegion.region,
                        visitCount = rawRegion.visitCount,
                        lastVisit = rawRegion.lastVisit
                    )
                } ?: emptyList()
            )
        }
    }
        .onEach { _isLoading.value = false }
        .catch { _error.emit("Не удалось загрузить статистику") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Кнопка «Обновить геоданные» – форсирует повторный проход
     * fillAllMissingCountries(). Если у некоторых мест country/region всё ещё null,
     * они будут повторно запросены у Nominatim.
     */
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
