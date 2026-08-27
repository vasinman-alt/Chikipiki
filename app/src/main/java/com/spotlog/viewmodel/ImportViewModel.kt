package com.spotlog.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spotlog.data.ImportResult
import com.spotlog.data.ImportValidationError
import com.spotlog.data.repository.ImportRepository
import com.spotlog.data.repository.PlaceRepository
import com.spotlog.ui.PendingVisit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ImportViewModel(application: Application) : AndroidViewModel(application) {
    private val importRepository = ImportRepository(application)
    private val placeRepository = PlaceRepository(application, viewModelScope)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _importResult = MutableStateFlow<ImportResult?>(null)
    val importResult: StateFlow<ImportResult?> = _importResult.asStateFlow()

    fun importFromFile(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val tempFile = withContext(Dispatchers.IO) {
                    val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: return@withContext null
                    val file = File(getApplication<Application>().cacheDir, "import_temp.json")
                    file.outputStream().use { output ->
                        inputStream.copyTo(output)
                    }
                    file
                }
                if (tempFile != null) {
                    val result = importRepository.importFromFile(tempFile)
                    _importResult.value = result
                } else {
                    _importResult.value = ImportResult.Error(ImportValidationError.InvalidJson)
                }
            } catch (_: Exception) {
                _importResult.value = ImportResult.Error(ImportValidationError.InvalidJson)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addManualOldPlace(name: String, lat: Double, lon: Double, category: String, visits: List<PendingVisit>) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val placeId = placeRepository.addManualCheckin(
                    name = name,
                    lat = lat,
                    lon = lon,
                    category = category,
                    timestamp = visits.firstOrNull()?.timestamp ?: System.currentTimeMillis(),
                    comment = ""
                )
                visits.forEach { visit ->
                    placeRepository.addHistoricalVisitToExistingPlace(
                        placeId = placeId,
                        timestamp = visit.timestamp,
                        comment = visit.comment
                        // ИСПРАВЛЕНО: убран photoUri, так как метод в PlaceRepository его не принимает
                    )
                }
                _importResult.value = ImportResult.Success(emptyList())
            } catch (_: Exception) {
                _importResult.value = ImportResult.Error(ImportValidationError.InvalidJson)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearResult() {
        _importResult.value = null
    }
}