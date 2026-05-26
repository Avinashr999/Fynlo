package app.fynlo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * A saved Smart Flow Wizard template.
 * Users can save recurring flows (e.g. "Monthly Salary Split")
 * and replay them with a single tap.
 */
@Serializable
@Entity(tableName = "flow_templates")
data class FlowTemplate(
    @PrimaryKey val id: String,
    val name: String,                   // "Monthly Salary", "Fuel Fill-up", etc.
    val eventType: String,              // "Received" | "Spent" | "Moved" | "Lent" | "Borrowed"
    val category: String = "",
    val fromAccount: String = "",
    val toAccount: String = "",
    val projectId: String = "personal",
    val updatedAt: Long = 0L,
    val createdAt: Long = 0L,        // added v16→v17 (C03a Stage 2; UX_AUDIT §C03 item #2)
)