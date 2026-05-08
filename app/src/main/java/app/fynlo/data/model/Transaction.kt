package app.fynlo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey val id: String,
    val date: String,
    val type: String, // income, expense, transfer
    val amount: Double,
    val fromAcct: String = "",
    val toAcct: String = "",
    val category: String,
    val subcat: String = "",
    val person: String = "",
    val desc: String = "",
    val ref: String = "",
    val notes: String = "",
    val tags: String = "",
    val projectId: String = "personal",
    val updatedAt: Long = 0L
)
