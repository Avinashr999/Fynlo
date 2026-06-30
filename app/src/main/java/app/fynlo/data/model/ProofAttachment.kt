package app.fynlo.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "proof_attachments",
    indices = [Index(value = ["ownerType", "ownerId"], name = "idx_proof_attachments_owner")]
)
data class ProofAttachment(
    @PrimaryKey val id: String,
    val ownerType: String,
    val ownerId: String,
    val displayName: String,
    val mimeType: String = "",
    val localUri: String = "",
    val remotePath: String = "",
    val note: String = "",
    val projectId: String = "personal",
    val updatedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
)
