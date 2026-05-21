package app.fynlo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey val id: String,
    val name: String,
    val targetAmount: Double,
    val savedAmount: Double = 0.0,
    val deadline: String = "",
    val notes: String = "",
    val projectId: String = "personal",
    val updatedAt: Long = 0L,
    val createdAt: Long = 0L
)
