package com.example.cashmemo.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary        = DarkPrimary,
    onPrimary      = Color.Black,
    secondary      = PrimaryLight,
    background     = DarkBackground,
    surface        = DarkSurface,
    surfaceVariant = Color(0xFF2C2C2C),
    error          = ErrorRed
)

private val LightColorScheme = lightColorScheme(
    primary        = LightPrimary,
    onPrimary      = Color.White,
    secondary      = PrimaryLight,
    background     = LightBackground,
    surface        = LightSurface,
    surfaceVariant = Color(0xFFF1F3F4),
    surfaceContainer = Color(0xFFF1F3F4),
    surfaceContainerHigh = Color(0xFFECECEC),
    surfaceContainerHighest = Color(0xFFE5E5E5),
    surfaceContainerLow = Color(0xFFF5F5F5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    error          = ErrorRed
)

/** App-wide theme override: null = follow system, true = force dark, false = force light */
object ThemeController {
    var darkModeOverride by mutableStateOf<Boolean?>(null)

    fun load(context: android.content.Context) {
        val prefs = context.getSharedPreferences("cashmemo_theme", android.content.Context.MODE_PRIVATE)
        darkModeOverride = when (prefs.getString("mode", "system")) {
            "dark"  -> true
            "light" -> false
            else    -> null
        }
    }

    fun save(context: android.content.Context) {
        val prefs = context.getSharedPreferences("cashmemo_theme", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("mode", when (darkModeOverride) {
            true  -> "dark"
            false -> "light"
            null  -> "system"
        }).apply()
    }
}

@Composable
fun CashMemoTheme(
    darkTheme: Boolean = ThemeController.darkModeOverride ?: isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view        = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}
