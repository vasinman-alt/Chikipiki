package com.spotlog.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val category: String,
    val country: String? = null,
    val region: String? = null,
    val comment: String = ""   // новое поле
)