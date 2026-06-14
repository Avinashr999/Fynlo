package app.fynlo.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey val id: String,
    val date: String,
    val type: String, // income, expense, transfer
    val amount: Double,
    val fromAcct: String = "",
    val toAcct: String = "",
    // C03b Stage #1a (3.2.86) — additive immutable account references.
    // Empty string means "unresolved" (no matching Account.name when this
    // row was written, or pre-migration data the v22→v23 backfill couldn't
    // resolve). When non-empty, this is the authoritative pointer to the
    // owning account: surviving account renames that would otherwise
    // orphan the row (the 3.2.59 bug class).
    //
    // Reads still use `fromAcct` / `toAcct` (display + balance lookups);
    // Stage #1b will flip those reads to resolve via id.
    val fromAcctId: String = "",
    val toAcctId: String = "",
    val category: String,
    val subcat: String = "",
    val person: String = "",
    val desc: String = "",
    val ref: String = "",
    val notes: String = "",
    val tags: String = "",
    val projectId: String = "personal",
    val updatedAt: Long = 0L,
    val createdAt: Long = 0L
)
