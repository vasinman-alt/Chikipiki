package com.spotlog.premium

enum class Feature {
    PHOTOS,
    ORIGINAL_PHOTO_QUALITY,
    UNLIMITED_PLACES,
    ADVANCED_STATS
}

interface FeatureGate {
    fun isEnabled(feature: Feature): Boolean
}