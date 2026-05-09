package app.fynlo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Historical valuation log for an investment.
 * Follows the "Mark-to-Market" accounting principle.
 */
@Serializable
@Entity(tableName = "investment_valuations")
data class InvestmentValuation(
    @PrimaryKey val id: String,
    val investmentId: String,
    val date: String,
    val value: Double,
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
