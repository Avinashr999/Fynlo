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
    val createdAt: Long = 0L
)
