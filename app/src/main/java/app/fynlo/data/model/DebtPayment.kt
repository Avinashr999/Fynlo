package app.fynlo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "debt_payments")
data class DebtPayment(
    @PrimaryKey val id: String,
    val debtId: String,
    val name: String,
    val date: String,
    val type: String,
    val amount: Double,
    val principal: Double = 0.0,
    val interest: Double = 0.0,
    val interestPeriodStartDate: String = "",
    val interestPeriodEndDate: String = "",
    val interestAllocationType: String = "UNKNOWN_REVIEW",
    val mode: String = "",
    val notes: String = "",
    // v3.3.0 (DB v32) — exact penalty paise when a payment overpays the total due
    // by ₹1 or more. Old rows / docs / backups read as 0.
    val penaltyPaise: Long = 0L,
    // v3.3.0 (DB v32) — signed whole-rupee settlement rounding: + small gain,
    // − write-off (see InterestEngine.allocatePaymentPaise). Old rows read as 0.
    // Per row: interest + principal + penaltyPaise + roundingPaise == amount.
    val roundingPaise: Long = 0L,
    val projectId: String = "personal",
    val updatedAt: Long = 0L,
    val createdAt: Long = 0L
)
