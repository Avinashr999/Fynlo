package app.fynlo.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "monthly_closes",
    indices = [Index(value = ["projectId", "month"], name = "idx_monthly_closes_project_month")]
)
data class MonthlyClose(
    @PrimaryKey val id: String,
    val projectId: String = "personal",
    val month: String,
    val status: String = "Closed",
    val note: String = "",
    val closedAt: Long = System.currentTimeMillis(),
    val reopenedAt: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
)
