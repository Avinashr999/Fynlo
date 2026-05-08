package app.fynlo.ui.theme

import androidx.compose.ui.graphics.Color

// ── Fynlo Design System — Carbon + Emerald ──────────────────────────────────

// Emerald (primary brand)
val Emerald500 = Color(0xFF059669)
val Emerald600 = Color(0xFF047857)
val Emerald700 = Color(0xFF065F46)
val Emerald400 = Color(0xFF34D399)
val Emerald100 = Color(0xFFD1FAE5)
val Emerald50  = Color(0xFFECFDF5)

// Carbon (neutral base)
val Carbon950  = Color(0xFF09090B)  // deepest
val Carbon900  = Color(0xFF18181B)  // dark bg
val Carbon800  = Color(0xFF27272A)  // dark surface
val Carbon700  = Color(0xFF3F3F46)  // dark surface2 / border dark
val Carbon600  = Color(0xFF52525B)  // muted text dark
val Carbon500  = Color(0xFF71717A)  // secondary text
val Carbon400  = Color(0xFFA1A1AA)  // tertiary text
val Carbon200  = Color(0xFFE4E4E7)  // border light
val Carbon100  = Color(0xFFF4F4F5)  // surface light
val Carbon50   = Color(0xFFFAFAFA)  // bg light

// Semantic
val SemanticRed    = Color(0xFFEF4444)
val SemanticAmber  = Color(0xFFF59E0B)
val SemanticBlue   = Color(0xFF3B82F6)
val SemanticViolet = Color(0xFF6B7280)

// Light theme surface colors
val LightBackground = Carbon50
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
