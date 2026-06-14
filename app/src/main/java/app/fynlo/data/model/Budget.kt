package app.fynlo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey val category: String, // Category name as key (Food, Fuel, etc.)
    val limitAmount: Double,
    val period: String = "Monthly", // Monthly, Weekly
    val projectId: String = "personal",
    val updatedAt: Long = 0L,
    val createdAt: Long = 0L,
    // C22 (3.2.57) — per-budget warning threshold (50..95, default 80).
    // The BudgetCard switches to the amber "NEAR LIMIT" state when
    // `spend / limit * 100 >= alertThresholdPct`. Before 3.2.57 this was
    // hardcoded at 80 across all budgets; users wanted to tighten it for
    // "must not slip" categories and loosen it for "rough cap" categories.
    // Stored as Int (not Float) to keep the value space discrete and the
    // schema small.
    val alertThresholdPct: Int = 80,
)
