package com.spotlog.util

import kotlin.math.*

fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2).pow(2)
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))
}

/**
 * Форматирует расстояние в человекочитаемый вид.
 * - до 1000 м: "850 м"
 * - от 1000 м до 10 км: "1.5 км" (с десятой долей)
 * - от 10 км: "12 км" (целые километры)
 */
fun formatDistance(meters: Double): String {
    return when {
        meters < 1000 -> "${meters.toInt()} м"
        meters < 10000 -> {
            val km = meters / 1000.0
            val formatted = String.format("%.1f", km)
            "$formatted км"
        }
        else -> "${(meters / 1000.0).toInt()} км"
    }
}