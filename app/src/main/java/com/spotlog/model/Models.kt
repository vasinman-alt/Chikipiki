package com.spotlog.model

import java.util.UUID

data class PoiData(
    val name: String,
    val lat: Double,
    val lon: Double,
    val category: String
)

data class MarkerData(
    val id: String = UUID.randomUUID().toString(),
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val timestamp: Long = 0L,
    val comment: String = ""
)