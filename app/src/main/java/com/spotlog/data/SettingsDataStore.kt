package com.spotlog.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: SettingsDataStore? = null

        fun getInstance(context: Context): SettingsDataStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsDataStore(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val dataStore = context.settingsDataStore

    private val ASK_PHOTO_ON_VISIT_KEY = booleanPreferencesKey("ask_photo_on_visit")

    val askPhotoOnVisit: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[ASK_PHOTO_ON_VISIT_KEY] ?: true // по умолчанию включено
        }

    suspend fun setAskPhotoOnVisit(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[ASK_PHOTO_ON_VISIT_KEY] = enabled
        }
    }
}