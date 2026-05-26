package app.fynlo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "net_worth_snapshots")
data class NetWorthSnapshot(
    @PrimaryKey val date: String = "",   // yyyy-MM-dd
    val netWorth: Double = 0.0,
    val totalAssets: Double = 0.0,
    val totalLiabilities: Double = 0.0,
    val projectId: String = "personal",
    val createdAt: Long = 0L,            // added v16→v17 (C03a Stage 2; UX_AUDIT §C03 item #2 — backfilled from `date` since this entity has no `updatedAt`)
)