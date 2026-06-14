package app.fynlo.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * C22 (3.2.55) — Goals icon picker.
 *
 * Six curated icons cover the audit's "savings goal" use cases:
 *   - star       → generic / milestone (default; matches pre-3.2.55 look)
 *   - car        → vehicle purchase
 *   - house      → property / down payment
 *   - plane      → travel / holiday
 *   - graduation → education
 *   - gift       → wedding / gift / occasion
 *
 * Stable string keys (not ImageVectors) are stored on the Goal entity so
 * the icon set can be expanded later without a schema migration. Unknown
 * keys fall back to "star".
 */
object GoalIcons {
    data class Option(val key: String, val label: String, val icon: ImageVector)

    val all: List<Option> = listOf(
        Option("star", "Generic", Icons.Default.Star),
        Option("car", "Vehicle", Icons.Default.DirectionsCar),
        Option("house", "Home", Icons.Default.Home),
        Option("plane", "Travel", Icons.Default.Flight),
        Option("graduation", "Education", Icons.Default.School),
        Option("gift", "Occasion", Icons.Default.CardGiftcard),
    )

    fun iconFor(key: String): ImageVector =
        all.firstOrNull { it.key == key }?.icon ?: Icons.Default.Star
}
