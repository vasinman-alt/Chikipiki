package com.spotlog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.spotlog.location.LocationProvider
import com.spotlog.navigation.AppNavGraph
import com.spotlog.theme.ChikipikiTheme
import com.spotlog.util.CrashLogger
import com.spotlog.util.OsmInitializer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CrashLogger.init(applicationContext)
        OsmInitializer.init(this)

        // Инициализация единого LocationProvider
        LocationProvider.init(application)

        setContent {
            ChikipikiTheme {
                AppNavGraph()
            }
        }
    }
}