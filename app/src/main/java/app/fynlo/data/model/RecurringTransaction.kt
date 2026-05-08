package app.fynlo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recurring_transactions")
data class RecurringTransaction(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val name        : String  = "",          // e.g. "Monthly Rent"
    val type        : String  = "Expense",   // Income / Expense
    val amount      : Double  = 0.0,         // 0 = prompt user each time
    val category    : String  = "",
    val fromAcct    : String  = "",
    val toAcct      : String  = "",
    val frequency   : String  = "Monthly",   // Daily / Weekly / Monthly / Yearly
    val dayOfMonth  : Int     = 1,           // 1-28
    val notes       : String  = "",
    val isActive    : Boolean = true,
    val lastRun     : String  = "",          // yyyy-MM-dd of last auto-log
    val projectId   : String  = "personal",
    val updatedAt   : Long    = 0L
)