package com.spotlog.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spotlog.data.AppDatabase
import com.spotlog.data.entity.PhotoEntity
import com.spotlog.data.repository.PlaceRepository
import com.spotlog.util.safeCall
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PhotoGalleryViewModel(
    application: Application,
    private val placeId: Long
) : AndroidViewModel(application) {

    private val repository = PlaceRepository(application, viewModelScope)
    private val db = AppDatabase.getDatabase(application)

    private val _photos = MutableStateFlow<List<PhotoEntity>>(emptyList())
    val photos: StateFlow<List<PhotoEntity>> = _photos.asStateFlow()

    private val _error = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val error: SharedFlow<String> = _error.asSharedFlow()

    private val toggleMutex = Mutex()
    private var isToggling = false

    init {
        loadPhotos()
    }

    private fun loadPhotos() {
        viewModelScope.launch {
            db.photoDao().getPhotosForPlace(placeId).collect { list ->
                _photos.value = list
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
        viewModelScope.launch {
            safeCall(
                onError = { msg -> _error.emit(msg) },
                errorMessage = "Ошибка установки обложки"
            ) {
                repository.setCoverPhoto(placeId, photoId)
            }
        }
    }

    fun toggleCoverPhoto(photoId: Long) {
        viewModelScope.launch {
            if (isToggling) return@launch
            toggleMutex.withLock {
                isToggling = true
                try {
                    safeCall(
                        onError = { msg -> _error.emit(msg) },
                        errorMessage = "Ошибка переключения обложки"
                    ) {
                        val currentPhotos = _photos.value
                        val currentPhoto = currentPhotos.find { it.id == photoId }
                            ?: return@safeCall

                        if (currentPhoto.isCover) {
                            repository.clearCoverPhoto(photoId)
                        } else {
                            repository.setCoverPhoto(placeId, photoId)
                        }
                    }
                } finally {
                    isToggling = false
                }
            }
        }
    }

    fun refresh() {
        loadPhotos()
    }
}