package app.fynlo.data.model

import androidx.room.Entity

@Entity(tableName = "deleted_remote_docs", primaryKeys = ["collection", "id"])
data class DeletedRemoteDoc(
    val collection: String,
    val id: String,
    val deletedAt: Long = System.currentTimeMillis(),
)
