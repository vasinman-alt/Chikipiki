package com.spotlog.premium

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "premium")

class PremiumManager(private val context: Context) {

    private val PREMIUM_KEY = booleanPreferencesKey("is_premium")

    val isPremiumFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PREMIUM_KEY] ?: true   // в разработке все premium
    }

    fun isPremiumCached(): Boolean {
        // Заглушка – в реальном приложении нужно синхронизировать с DataStore
        return true
    }
}

class PremiumFeatureGate(premiumManager: PremiumManager) : FeatureGate {
    private val isPremium = premiumManager.isPremiumCached()
    override fun isEnabled(feature: Feature): Boolean {
        return when (feature) {
            Feature.PHOTOS -> isPremium
            Feature.ORIGINAL_PHOTO_QUALITY -> isPremium
            Feature.UNLIMITED_PLACES -> isPremium
            Feature.ADVANCED_STATS -> isPremium
        }
    }
}