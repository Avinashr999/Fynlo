package app.fynlo.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "undo_actions",
    indices = [Index(value = ["projectId", "expiresAt", "consumedAt"], name = "idx_undo_actions_active")]
)
data class UndoAction(
    @PrimaryKey val id: String,
    val action: String,
    val entityType: String,
    val entityId: String,
    val title: String,
    val beforeJson: String = "",
    val afterJson: String = "",
    val projectId: String = "personal",
    val expiresAt: Long,
    val consumedAt: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
)
