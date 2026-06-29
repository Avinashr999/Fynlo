package app.fynlo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "sync_conflicts")
data class SyncConflict(
    @PrimaryKey val id: String,
    val collection: String,
    val entityId: String,
    val fieldSummary: String,
    val localJson: String,
    val remoteJson: String,
    val resolution: String = "Open",
    val projectId: String = "personal",
    val resolvedAt: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
)
