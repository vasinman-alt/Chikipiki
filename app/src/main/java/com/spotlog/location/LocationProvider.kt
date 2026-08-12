// ==== ФАЙЛ: LocationProvider.kt ====
package com.spotlog.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import org.osmdroid.util.GeoPoint

/**
 * Единый singleton, отвечающий за получение текущей геопозиции.
 *
 * Главная цель — дать пользователю **мгновенный** ответ: сначала пробуем
 * `lastLocation` (кэшированное значение, без задержки), параллельно
 * запрашиваем более точный GPS‑фикс.
 */
class LocationProvider private constructor(context: Application) {

    private val locationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val appContext = context

    private val _currentLocation = MutableStateFlow<GeoPoint?>(null)
    val currentLocation: StateFlow<GeoPoint?> = _currentLocation.asStateFlow()

    private var lastFixTimeMs = 0L
    private var locationJob: Job? = null

    companion object {
        @Volatile
        private var instance: LocationProvider? = null

        fun init(application: Application) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = LocationProvider(application)
                    }
                }
            }
        }

        fun getInstance(): LocationProvider {
            return instance ?: throw IllegalStateException(
                "LocationProvider must be initialized in Application.onCreate()"
            )
        }
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    fun getLastFixTimeMs(): Long = lastFixTimeMs

    /**
     * Возвращает последнее известное местоположение из кэша Google Play Services
     * (может быть null, если устройство никогда не сообщало координаты).
     * Метод НЕ блокирует UI — выполняется очень быстро (обычно < 50 мс).
     */
    @SuppressLint("MissingPermission")
    suspend fun lastKnownLocation(): GeoPoint? {
        if (!hasPermission()) return null
        return try {
            val location = locationClient.lastLocation.await()
            location?.let { GeoPoint(it.latitude, it.longitude) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Принудительно обновляет координаты: сначала возвращает `lastLocation`
     * (если оно свежее), затем параллельно запрашивает более точный GPS‑фикс.
     * При успехе обновляет `_currentLocation` и `lastFixTimeMs`.
     */
    fun refresh(scope: CoroutineScope = CoroutineScope(Dispatchers.Main)) {
        scope.launch {
            refreshInternal()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun refreshInternal() {
        if (!hasPermission()) return

        // 1️⃣ Мгновенно пробуем отдать последнюю известную точку
        try {
            val last = locationClient.lastLocation.await()
            if (last != null) {
                _currentLocation.value = GeoPoint(last.latitude, last.longitude)
                lastFixTimeMs = System.currentTimeMillis()
            }
        } catch (_: Exception) {
            // Игнорируем – основной фикс ниже попробует ещё раз
        }

        // 2️⃣ Запускаем «точный» запрос с таймаутом 10 сек
        locationJob?.cancel()
        locationJob = CoroutineScope(Dispatchers.Main).launch {
            val cts = CancellationTokenSource()
            try {
                val location = withTimeout(10_000L) {
                    locationClient.getCurrentLocation(
                        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                        cts.token
                    ).await()
                }
                if (location != null) {
                    _currentLocation.value = GeoPoint(location.latitude, location.longitude)
                    lastFixTimeMs = System.currentTimeMillis()
                }
            } catch (_: CancellationException) {
                // корутину отменили – ничего не делаем
            } catch (_: Exception) {
                // сеть/GPS недоступны – пользователь останется с last known
            } finally {
                cts.cancel()
            }
        }
    }
}
