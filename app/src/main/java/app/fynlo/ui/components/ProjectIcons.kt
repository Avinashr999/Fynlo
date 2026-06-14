package app.fynlo.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * C22 (3.2.56) — Projects icon picker.
 *
 * Six curated icons cover the audit's project use-cases:
 *   - business → company / side-business (default; matches pre-3.2.56 look)
 *   - person   → personal / individual
 *   - home     → household / family budget
 *   - school   → education / learning
 *   - flight   → travel / holiday fund
 *   - heart    → charity / shared with partner
 *
 * Stable string keys persisted on the Project entity (the column has
 * existed since v3→v4; this stage just surfaces a picker for it). Unknown
 * keys fall back to "business" — same fallback the entity default uses.
 */
object ProjectIcons {
    data class Option(val key: String, val label: String, val icon: ImageVector)

    val all: List<Option> = listOf(
        Option("business", "Business", Icons.Default.Business),
        Option("person", "Personal", Icons.Default.Person),
        Option("home", "Household", Icons.Default.Home),
        Option("school", "Education", Icons.Default.School),
        Option("flight", "Travel", Icons.Default.Flight),
        Option("heart", "Shared", Icons.Default.Favorite),
    )

    fun iconFor(key: String): ImageVector =
        all.firstOrNull { it.key == key }?.icon ?: Icons.Default.Business
}
