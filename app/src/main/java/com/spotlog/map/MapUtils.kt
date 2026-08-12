package com.spotlog.map

import com.spotlog.util.calculateDistance

// Пока просто реэкспорт, чтобы экраны могли использовать удобно
fun distanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double) =
    calculateDistance(lat1, lon1, lat2, lon2)