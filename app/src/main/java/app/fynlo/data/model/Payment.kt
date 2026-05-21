package app.fynlo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "payments")
data class Payment(
    @PrimaryKey val id: String,
    val loanId: String,
    val name: String,
    val date: String,
    val type: String, // Interest Only, Principal Only, Both, Full Settlement
    val amount: Double,
    val principal: Double = 0.0,
    val interest: Double = 0.0,
    val mode: String = "",
    val notes: String = "",
    val projectId: String = "personal",
    val updatedAt: Long = 0L,
    val createdAt: Long = 0L
)
