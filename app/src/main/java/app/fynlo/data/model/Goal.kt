package app.fynlo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey val id: String,
    val name: String,
    val targetAmount: Double,
    val savedAmount: Double = 0.0,
    val deadline: String = "",
    val notes: String = "",
    val projectId: String = "personal",
    val updatedAt: Long = 0L,
    val createdAt: Long = 0L,
    // C22 (3.2.55) — Goals icon picker + account link.
    // `iconKey` is a stable sentinel ("star", "car", "house", "plane",
    // "graduation", "gift") rendered by GoalIcons.iconFor(). Defaults to
    // "star" so all pre-3.2.55 goals continue showing the same icon.
    // `linkedAccount` is an optional account name; when set, the goal card
    // surfaces a "Linked to: X" badge. Saved-amount math is unchanged for
    // this stage — auto-deduct on contribution is a separate item.
    val iconKey: String = "star",
    val linkedAccount: String = "",
)
