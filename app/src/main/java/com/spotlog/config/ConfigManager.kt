package com.spotlog.config

import android.content.Context
import org.json.JSONObject

class ConfigManager private constructor(context: Context) {
    private val config: JSONObject

    init {
        val json = context.assets.open("spotlog_config.json").bufferedReader().use { it.readText() }
        config = JSONObject(json)
    }

    fun getOverpassUrl(): String = config.getJSONObject("overpass").getString("url")

    companion object {
        @Volatile
        private var instance: ConfigManager? = null

        fun getInstance(context: Context): ConfigManager {
            return instance ?: synchronized(this) {
                instance ?: ConfigManager(context.applicationContext).also { instance = it }
            }
        }
    }
}