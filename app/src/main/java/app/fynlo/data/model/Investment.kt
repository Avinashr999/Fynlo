package app.fynlo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "investments")
@androidx.compose.runtime.Immutable
data class Investment(
    @PrimaryKey val id: String,
    val name: String,
    val type: String, // Stocks, Mutual Funds, Gold, etc.
    val subtype: String = "",
    val invested: Double,
    val currentVal: Double,
    val date: String,
    val maturityDate: String = "",
    val rate: Double = 0.0,
    val realized: Double = 0.0,
    val withdrawn: Double = 0.0,
    val notes: String = "",
    val projectId: String = "personal",
    val updatedAt: Long = 0L,
    // Funding source tracking — added v2.10
    val fundingSource: String = "",   // account name or debt/lender name
    val sourceType: String = "",      // "account" | "existing_debt" | "new_loan"
    val linkedDebtId: String = "",    // non-empty only when sourceType = "new_loan"
    val createdAt: Long = 0L          // audit: first-created timestamp (#05)
)
