package app.fynlo.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.view.WindowCompat
import androidx.compose.ui.unit.dp

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
    surfaceVariant           = Emerald50,
    onSurfaceVariant         = Carbon600,
    surfaceContainer         = Color(0xFFF0F6F1),
    surfaceContainerHigh     = Color(0xFFE8F1EA),
    surfaceContainerHighest  = Color(0xFFDCE8E0),
    surfaceContainerLow      = Color(0xFFF6FAF7),
    surfaceContainerLowest   = Color.White,
    outline                  = Carbon400.copy(alpha = 0.82f),
    outlineVariant           = Carbon200,
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
    surfaceVariant           = Carbon800,
    onSurfaceVariant         = Color(0xFFD4D4D8),
    surfaceContainer         = Carbon800,
    surfaceContainerHigh     = Carbon700,
    surfaceContainerHighest  = Carbon600,
    surfaceContainerLow      = Color(0xFF1F1F22),
    surfaceContainerLowest   = Carbon900,
    outline                  = Carbon500,
    outlineVariant           = Carbon400,
    error                    = SemanticRed,
    onError                  = Color.White,
    inverseSurface           = Carbon50,
    inverseOnSurface         = Carbon900,
    inversePrimary           = Emerald500,
    scrim                    = Color.Black
)

private val FynloShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
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
        shapes      = FynloShapes,
        content     = content
    )
}
