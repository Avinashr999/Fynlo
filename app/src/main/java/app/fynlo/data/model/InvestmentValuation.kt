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
    val projectId: String = "personal",   // added v16→v17 (C03a Stage 2; UX_AUDIT §C03 item #3 — was the only scoped sub-entity without projectId)
    val updatedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = 0L,             // added v16→v17 (C03a Stage 2; UX_AUDIT §C03 item #2)
)
