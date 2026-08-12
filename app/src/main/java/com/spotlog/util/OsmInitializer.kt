package com.spotlog.util

import android.content.Context
import org.osmdroid.config.Configuration
import java.io.File

object OsmInitializer {

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val appContext = context.applicationContext

            val prefs = appContext.getSharedPreferences(
                appContext.packageName + "_preferences",
                Context.MODE_PRIVATE
            )

            val externalDir = appContext.getExternalFilesDir(null) ?: appContext.filesDir
            val tileCacheDir = File(externalDir, "osmdroid/tiles").apply { mkdirs() }

            Configuration.getInstance().apply {
                load(appContext, prefs)
                userAgentValue = "Chikipiki/1.0 (Android)"
                osmdroidTileCache = tileCacheDir
            }
            initialized = true
        }
    }
}