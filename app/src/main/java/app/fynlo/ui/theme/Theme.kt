package app.fynlo.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Carbon + Emerald — Light ─────────────────────────────────────────────────
private val LightColorScheme = lightColorScheme(
    primary                  = Emerald500,
    onPrimary                = Color.White,
    primaryContainer         = Emerald100,
    onPrimaryContainer       = Emerald700,
    secondary                = Emerald600,
    onSecondary              = Color.White,
    secondaryContainer       = Emerald100,
    onSecondaryContainer     = Emerald700,
    tertiary                 = SemanticBlue,
    onTertiary               = Color.White,
    background               = LightBackground,
    onBackground             = Carbon900,
    surface                  = LightSurface,
    onSurface                = Carbon900,
    surfaceVariant           = Carbon100,
    onSurfaceVariant         = Carbon500,
    surfaceContainer         = Carbon100,
    surfaceContainerHigh     = Carbon200,
    surfaceContainerHighest  = Color(0xFFE4E4E7),
    surfaceContainerLow      = Carbon50,
    surfaceContainerLowest   = Color.White,
    outline                  = Carbon200,
    outlineVariant           = Color(0xFFE4E4E7),
    error                    = SemanticRed,
    onError                  = Color.White,
    inverseSurface           = Carbon900,
    inverseOnSurface         = Carbon50,
    inversePrimary           = Emerald400,
    scrim                    = Color.Black
)

// ── Carbon + Emerald — Dark ──────────────────────────────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary                  = Emerald400,
    onPrimary                = Carbon950,
    primaryContainer         = Emerald700,
    onPrimaryContainer       = Emerald100,
    secondary                = Emerald400,
    onSecondary              = Carbon950,
    secondaryContainer       = Emerald700,
    onSecondaryContainer     = Emerald100,
    tertiary                 = SemanticBlue,
    onTertiary               = Color.White,
    background               = DarkBackground,
    onBackground             = Carbon50,
    surface                  = DarkSurface,
    onSurface                = Carbon50,
    surfaceVariant           = Carbon700,
    onSurfaceVariant         = Carbon400,
    surfaceContainer         = Carbon800,
    surfaceContainerHigh     = Carbon700,
    surfaceContainerHighest  = Carbon600,
    surfaceContainerLow      = Color(0xFF1F1F22),
    surfaceContainerLowest   = Carbon900,
    outline                  = Carbon700,
    outlineVariant           = Carbon700,
    error                    = SemanticRed,
    onError                  = Color.White,
    inverseSurface           = Carbon50,
    inverseOnSurface         = Carbon900,
    inversePrimary           = Emerald500,
    scrim                    = Color.Black
)

@Composable
fun FynloTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        ThemeController.darkModeOverride == true  -> DarkColorScheme
        ThemeController.darkModeOverride == false -> LightColorScheme
        darkTheme                                  -> DarkColorScheme
        else                                       -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                colorScheme.background == LightBackground
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}
