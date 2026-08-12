package com.spotlog.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "geocode_cache")
data class GeocodeCacheEntity(
    @PrimaryKey val cellKey: String,
    val country: String?,
    val region: String?,
    val cachedAt: Long
)