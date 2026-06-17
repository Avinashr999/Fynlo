package app.fynlo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "audit_events")
data class AuditEvent(
    @PrimaryKey val id: String,
    val timestamp: Long = System.currentTimeMillis(),
    val action: String,
    val entityType: String,
    val entityId: String,
    val title: String,
    val beforeValue: String = "",
    val afterValue: String = "",
    val amountDelta: Double = 0.0,
    val accountName: String = "",
    val projectId: String = "personal",
    val reason: String = "",
    val actor: String = "local",
)
