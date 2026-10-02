package app.fynlo.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

// ── Fynlo Design System — Carbon + Emerald ──────────────────────────────────

// Emerald (primary brand) - tuned to the approved template.
val Emerald500 = Color(0xFF008A62)
val Emerald600 = Color(0xFF007A58)
val Emerald700 = Color(0xFF005F46)
val Emerald800 = Color(0xFF12382D)
val Emerald900 = Color(0xFF0B241D)
val Emerald400 = Color(0xFF12A878)
val Emerald200 = Color(0xFFA9E7D0)
val Emerald100 = Color(0xFFDDF4EA)
val Emerald50  = Color(0xFFF0FAF5)

// Carbon (neutral base)
val Carbon950  = Color(0xFF101418)  // deepest
val Carbon900  = Color(0xFF182029)  // primary text
val Carbon800  = Color(0xFF242A30)  // dark surface
val Carbon700  = Color(0xFF39434D)  // dark surface2 / border dark
val Carbon600  = Color(0xFF526171)  // secondary text
val Carbon500  = Color(0xFF637282)  // secondary text
val Carbon400  = Color(0xFFA7B4AE)  // tertiary text
val Carbon200  = Color(0xFFDCE5DF)  // border light
val Carbon100  = Color(0xFFF0F5F1)  // surface light
val Carbon50   = Color(0xFFF7FAF6)  // bg light

// Semantic
val SemanticRed    = Color(0xFFE04444)
val SemanticAmber  = Color(0xFFA87312)
val SemanticBlue   = Color(0xFF337DB3)
val SemanticViolet = Color(0xFF6B7280)
val SemanticTeal   = Color(0xFF0F766E)

// Chart palette — brand-aligned, restrained (no neon)
val ChartColors = listOf(
    Emerald500,
    SemanticBlue,
    SemanticAmber,
    SemanticTeal,
    Carbon500,
    Emerald400,
    SemanticRed,
    Carbon400
)

// Light theme surface colors
val LedgerBlue = Color(0xFF0866FF)
val LedgerBlueDark = Color(0xFF4D94FF)
val LedgerIncome: Color
    @Composable get() = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF74DBB2) else Color(0xFF087A55)
val LightBackground = Color(0xFFFAFBFD)
val LightSurface    = Color(0xFFFFFFFF)
val LightSurface2   = Carbon100

// Dark theme surface colors
val DarkBackground  = Carbon900
val DarkSurface     = Carbon800
val DarkSurface2    = Carbon700

// Legacy aliases (keep so existing code compiles)
val PrimaryDark    = Emerald400
val PrimaryLight   = Emerald400
val SecondaryDark  = Carbon700
val AccentGold     = SemanticAmber
val ErrorRed       = SemanticRed
val SuccessGreen   = Emerald500
val LightPrimary   = Emerald500
val DarkPrimary    = Emerald400
