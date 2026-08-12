package com.spotlog.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val ChikipikiLightScheme = lightColorScheme(
    primary = ChikipikiOrange,
    onPrimary = Color.White,
    primaryContainer = ChikipikiOrangeLight,
    onPrimaryContainer = ChikipikiOrange,

    secondary = Warm700,
    onSecondary = Color.White,
    secondaryContainer = Cream100,
    onSecondaryContainer = Warm700,

    background = Cream50,
    onBackground = Ink900,

    surface = Cream50,
    onSurface = Ink900,
    surfaceVariant = Cream100,
    onSurfaceVariant = Warm700,

    outline = Cream200,
    outlineVariant = Cream200,

    error = SoftRed,
    onError = Color.White,
    errorContainer = Color(0xFFF6DEDA),
    onErrorContainer = SoftRed
)

val ChikipikiDarkScheme = darkColorScheme(
    primary = ChikipikiOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5C3212),
    onPrimaryContainer = ChikipikiOrangeLight,

    secondary = Warm400,
    onSecondary = Surface900,
    secondaryContainer = Surface700,
    onSecondaryContainer = Ink50,

    background = Surface900,
    onBackground = Ink50,

    surface = Surface900,
    onSurface = Ink50,
    surfaceVariant = Surface800,
    onSurfaceVariant = Warm400,

    outline = Surface700,
    outlineVariant = Surface700,

    error = SoftRed,
    onError = Color.White,
    errorContainer = Color(0xFF4A241D),
    onErrorContainer = Color(0xFFF6DEDA)
)

/**
 * Тема приложения. dynamicColor выключен по умолчанию — фирменная палитра важнее
 * системного Material You, бренд должен выглядеть одинаково на всех устройствах.
 */
@Composable
fun ChikipikiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> ChikipikiDarkScheme
        else -> ChikipikiLightScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ChikipikiTypography,
        shapes = ChikipikiShapes,
        content = content
    )
}
