package app.fynlo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "borrowers")
data class Borrower(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String = "",
    val address: String = "",
    val guarantor: String = "",
    val amount: Double,
    val rate: Double,
    val date: String,
    val due: String = "",
    val tenure: Int = 0,
    val type: String = "Simple Interest", // Simple Interest, Both, Compound Interest
    val paid: Double = 0.0,           // total paid (paidPrincipal + paidInterest) — kept for compat
    val paidPrincipal: Double = 0.0,  // only principal repayments — reduces loan base
    val paidInterest: Double = 0.0,   // only interest payments — tracks interest collected
    val status: String = "Active",    // Active, Overdue, Defaulted, WrittenOff, Cleared
    val defaultDate: String = "",     // date borrower was marked defaulted
    val frozenInterest: Double = 0.0, // interest frozen at defaultDate — stops accruing
    val notes: String = "",
    val projectId: String = "personal",
    val updatedAt: Long = 0L
)
