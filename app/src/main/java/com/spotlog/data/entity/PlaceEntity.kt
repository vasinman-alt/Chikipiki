package com.spotlog.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "places",
    indices = [
        Index("category"), // Для фильтрации по категориям
        Index("country"), // Для статистики по странам
        Index("region"), // Для статистики по регионам
        Index(value = ["latitude", "longitude"]) // Для гео-запросов
    ]
)
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val category: String,
    val country: String? = null,
    val region: String? = null,
    val comment: String = ""
)