package com.spotlog.premium

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "premium")

object PremiumManager {
    private val PREMIUM_KEY = booleanPreferencesKey("is_premium")
    
    fun isPremiumFlow(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { prefs: Preferences ->
            prefs[PREMIUM_KEY] ?: true // в разработке все premium
        }
    }
    
    fun isPremiumCached(): Boolean {
        // Заглушка – в реальном приложении нужно синхронизировать с DataStore
        return true
    }
}

class PremiumFeatureGate : FeatureGate {
    private val isPremium = true // в разработке всегда true
    
    override fun isEnabled(feature: Feature): Boolean {
        return when (feature) {
            Feature.PHOTOS -> isPremium
            Feature.ORIGINAL_PHOTO_QUALITY -> isPremium
            Feature.UNLIMITED_PLACES -> isPremium
            Feature.ADVANCED_STATS -> isPremium
        }
    }
}