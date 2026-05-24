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
    "net_worth_snapshots", "investment_valuations"
)

/**
 * Deletes the entire `users/{uid}` Firestore tree: every document in each known
 * subcollection, then the user root document. Per-collection failures are
 * logged and skipped so one failure (e.g. a security rule that forbids deleting
 * backups) never aborts the rest. The caller must still be authenticated as
 * [uid] for the deletes to be permitted by the production rules.
 */
suspend fun deleteFirestoreUserTree(fs: FirebaseFirestore, uid: String) {
    if (uid.isBlank()) return
    val userDoc = fs.collection("users").document(uid)
    USER_FIRESTORE_COLLECTIONS.forEach { colName ->
        try {
            val snapshot = userDoc.collection(colName).get().await()
            snapshot.documents.forEach { it.reference.delete().await() }
        } catch (e: Exception) {
            android.util.Log.e("Reset", "Failed to wipe Firestore $colName: ${e.message}")
        }
    }
    // Best-effort: drop the user root document too.
    runCatching { userDoc.delete().await() }
}
