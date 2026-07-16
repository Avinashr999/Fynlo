package app.fynlo.ui.theme

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
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

// -- Carbon + Emerald - Light -------------------------------------------------
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

// -- Carbon + Emerald - Dark --------------------------------------------------
private val DarkColorScheme = darkColorScheme(
    primary                  = Emerald400,
    onPrimary                = Carbon950,
    primaryContainer         = Color(0xFF0B6B50),
    onPrimaryContainer       = Color(0xFFE4FFF4),
    secondary                = Color(0xFF7ADCB9),
    onSecondary              = Carbon950,
    secondaryContainer       = Color(0xFF174F3E),
    onSecondaryContainer     = Color(0xFFE4FFF4),
    tertiary                 = Color(0xFF9FCBFF),
    onTertiary               = Carbon950,
    background               = Carbon950,
    onBackground             = Color(0xFFF3FAF6),
    surface                  = Color(0xFF101C17),
    onSurface                = Color(0xFFF3FAF6),
    surfaceVariant           = Color(0xFF20332B),
    onSurfaceVariant         = Color(0xFFE2E8E4),
    surfaceContainer         = Color(0xFF182922),
    surfaceContainerHigh     = Color(0xFF22372F),
    surfaceContainerHighest  = Color(0xFF2C453B),
    surfaceContainerLow      = Color(0xFF13211B),
    surfaceContainerLowest   = Color(0xFF0B1712),
    outline                  = Color(0xFF8EA098),
    outlineVariant           = Color(0xFF53655D),
    error                    = Color(0xFFFFB4AB),
    onError                  = Color(0xFF690005),
    errorContainer           = Color(0xFF4A1719),
    onErrorContainer         = Color(0xFFFFDAD6),
    inverseSurface           = Color(0xFFE5ECE7),
    inverseOnSurface         = Carbon900,
    inversePrimary           = Emerald600,
    scrim                    = Color.Black
)

private val FynloShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp),
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
            val activity = view.context as? ComponentActivity ?: return@SideEffect
            val systemBarColor = colorScheme.background.toArgb()
            val useDarkIcons = colorScheme.background == LightBackground
            activity.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(
                    lightScrim = systemBarColor,
                    darkScrim = systemBarColor,
                    detectDarkMode = { !useDarkIcons }
                ),
                navigationBarStyle = SystemBarStyle.auto(
                    lightScrim = systemBarColor,
                    darkScrim = systemBarColor,
                    detectDarkMode = { !useDarkIcons }
                )
            )
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = useDarkIcons
                isAppearanceLightNavigationBars = useDarkIcons
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        shapes      = FynloShapes,
        content     = content
    )
}
