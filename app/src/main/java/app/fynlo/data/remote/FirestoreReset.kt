package app.fynlo.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Every subcollection under `users/{uid}` that makes up a user's Firestore
 * document tree. Kept in one place so Reset All Data and its instrumented test
 * stay in sync — when a new synced collection is added, add it here too.
 */
val USER_FIRESTORE_COLLECTIONS = listOf(
    "accounts", "transactions", "borrowers", "investments", "debts",
    "people", "payments", "debt_payments", "budgets", "goals",
    "projects", "recurring_transactions", "backup_meta", "backups",
    "net_worth_snapshots", "investment_valuations", "audit_events"
)

/**
 * Subcollections that legacy `backups/{date}` docs may contain. Older builds
 * snapshotted accounts + borrowers under each daily backup; current builds write
 * only the summary doc. The Firestore client SDK cannot enumerate subcollections,
 * so these legacy ones are deleted by name during a reset to avoid orphans.
 */
val LEGACY_BACKUP_SUBCOLLECTIONS = listOf("accounts", "borrowers")

/**
 * Deletes the entire `users/{uid}` Firestore tree: every document in each known
 * subcollection (plus the legacy nested snapshots under each `backups` doc),
 * then the user root document. Per-collection failures are logged and skipped so
 * one failure never aborts the rest. The caller must still be authenticated as
 * [uid] for the deletes to be permitted by the production rules.
 */
suspend fun deleteFirestoreUserTree(fs: FirebaseFirestore, uid: String) {
    if (uid.isBlank()) return
    val userDoc = fs.collection("users").document(uid)
    USER_FIRESTORE_COLLECTIONS.forEach { colName ->
        try {
            val snapshot = userDoc.collection(colName).get().await()
            snapshot.documents.forEach { doc ->
                // Older app versions snapshotted accounts/borrowers under each
                // daily backups/{date} doc. The client SDK can't list
                // subcollections, so clear the known legacy ones by name first —
                // otherwise they would be orphaned when the parent doc is deleted.
                if (colName == "backups") {
                    LEGACY_BACKUP_SUBCOLLECTIONS.forEach { sub ->
                        runCatching {
                            doc.reference.collection(sub).get().await()
                                .documents.forEach { it.reference.delete().await() }
                        }
                    }
                }
                doc.reference.delete().await()
            }
        } catch (e: Exception) {
            android.util.Log.e("Reset", "Failed to wipe Firestore $colName: ${e.message}")
        }
    }
    // Best-effort: drop the user root document too.
    runCatching { userDoc.delete().await() }
}
