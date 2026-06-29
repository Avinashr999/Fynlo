package app.fynlo.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "borrowers")
@androidx.compose.runtime.Immutable
data class Borrower(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String = "",
    // C03b Stage #3 (3.2.90) — additive FK into the `people` table.
    // Multiple loans to the same person link to ONE Person row (dedup
    // by phone at write time; backfill on v24→v25 migration). Empty
    // string means "unresolved" (legacy row with empty phone, or
    // pre-Stage-3 row whose backfill didn't find/create a match).
    // Display still uses `name` + `phone` for now; Stage #3b will flip
    // those reads to resolve via `peopleId`.
    val peopleId: String = "",
    val address: String = "",
    val guarantor: String = "",
    val amount: Double,
    val rate: Double,
    val date: String,
    val due: String = "",
    val tenure: Int = 0,
    // C03b Stage #2 (3.2.85) — Kotlin property renamed to `intType` to
    // match `Debt.intType`'s naming, closing the audit's "same concept,
    // two field names" complaint. `@ColumnInfo` pins the DB column name
    // to "type" so no schema migration is needed and existing rows read
    // through unchanged. `@SerialName` pins the backup JSON field name
    // to "type" so backups round-trip across the rename (old backups
    // restore without a sanitiser pass; new backups stay compatible
    // with any external tooling that read the legacy field name).
    @ColumnInfo(name = "type")
    @SerialName("type")
    val intType: String = "Simple Interest", // Simple Interest, Both, Compound Interest
    val paid: Double = 0.0,           // total paid (paidPrincipal + paidInterest) — kept for compat
    val paidPrincipal: Double = 0.0,  // only principal repayments — reduces loan base
    val paidInterest: Double = 0.0,   // only interest payments — tracks interest collected
    val interestWaived: Double = 0.0, // non-cash interest forgiven/grace adjustment
    val status: String = "Active",    // Active, Overdue, Defaulted, WrittenOff, Cleared
    val defaultDate: String = "",     // date borrower was marked defaulted
    val frozenInterest: Double = 0.0, // interest frozen at defaultDate — stops accruing
    val sourceAccount: String = "",  // account the loan was disbursed from
    val notes: String = "",
    val projectId: String = "personal",
    val updatedAt: Long = 0L,
    val createdAt: Long = 0L
)
