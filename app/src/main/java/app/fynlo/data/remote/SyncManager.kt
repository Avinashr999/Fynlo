package app.fynlo.data.remote

import app.fynlo.data.SyncStatus
import app.fynlo.data.local.FynloDao
import app.fynlo.data.model.*
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Listens to Firestore snapshot updates for all collections and writes
 * incoming changes back to Room, implementing last-write-wins via updatedAt.
 *
 * This is the ONLY path that writes to Room without going through
 * FinanceRepository, so it never triggers a second Firestore write.
 */
class SyncManager(
    val userId: String,
    private val dao: FynloDao
) {
    private val db     = Firebase.firestore
    private val scope  = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val listeners = mutableListOf<ListenerRegistration>()

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Initialising)
    val status: StateFlow<SyncStatus> = _status

    /** Classifies a Firestore listener error — avoids false orange on transient network blips. */
    private fun handleErr(err: FirebaseFirestoreException?) {
        if (err == null) return
        _status.value = when (err.code) {
            FirebaseFirestoreException.Code.UNAUTHENTICATED -> SyncStatus.Error("Sign in required")
            else -> SyncStatus.Offline  // network, permissions, timeouts all show offline
        }
    }

    private fun col(name: String) =
        db.collection("users").document(userId).collection(name)

    /** Start real-time listeners for every collection. */
    fun startListening() {
        if (userId.isEmpty()) return

        listeners += col("borrowers").addSnapshotListener { snap, err ->
            if (err != null || snap == null) { handleErr(err); return@addSnapshotListener }
            scope.launch {
                snap.documentChanges.forEach { change ->
                    runCatching {
                        val doc = change.document
                        if (change.type == DocumentChange.Type.REMOVED) { dao.deleteBorrowerById(doc.id); return@runCatching }
                        val remote = Borrower(
                            id        = doc.id,
                            name      = doc.str("name"),
                            phone     = doc.str("phone"),
                            address   = doc.str("address"),
                            guarantor = doc.str("guarantor"),
                            amount    = doc.dbl("amount"),
                            rate      = doc.dbl("rate"),
                            date      = doc.str("date"),
                            due       = doc.str("due"),
                            tenure    = doc.int("tenure"),
                            type      = doc.str("type"),
                            paid      = doc.dbl("paid"),
                            paidPrincipal  = doc.dbl("paidPrincipal"),
                            paidInterest   = doc.dbl("paidInterest"),
                            status    = doc.str("status"),
                            defaultDate    = doc.str("defaultDate"),
                            frozenInterest = doc.dbl("frozenInterest"),
                            sourceAccount  = doc.str("sourceAccount"),
                            notes     = doc.str("notes"),
                            projectId = doc.str("projectId"),
                            updatedAt = doc.lng("updatedAt"),
                            createdAt = doc.lng("createdAt")
                        )
                        // Only overwrite local if remote is newer (last-write-wins)
                        val local = dao.getBorrowerById(doc.id)
                        if (local == null || remote.updatedAt >= local.updatedAt) {
                            dao.insertBorrower(remote)
                        }
                    }
                }
                _status.value = SyncStatus.Synced
            }
        }

        listeners += col("transactions").addSnapshotListener { snap, err ->
            if (err != null || snap == null) { handleErr(err); return@addSnapshotListener }
            scope.launch {
                snap.documentChanges.forEach { change ->
                    runCatching {
                        val doc = change.document
                        if (change.type == DocumentChange.Type.REMOVED) {
                            dao.deleteTransactionById(doc.id)
                            return@runCatching
                        }
                        val remote = Transaction(
                            id        = doc.id,
                            date      = doc.str("date"),
                            type      = doc.str("type"),
                            amount    = doc.dbl("amount"),
                            fromAcct  = doc.str("fromAcct"),
                            toAcct    = doc.str("toAcct"),
                            category  = doc.str("category"),
                            subcat    = doc.str("subcat"),
                            person    = doc.str("person"),
                            desc      = doc.str("desc"),
                            ref       = doc.str("ref"),
                            notes     = doc.str("notes"),
                            tags      = doc.str("tags"),
                            projectId = doc.str("projectId"),
                            updatedAt = doc.lng("updatedAt"),
                            createdAt = doc.lng("createdAt")
                        )
                        // LWW: only overwrite if remote is newer
                        val localTxn = dao.getTransactionById(remote.id)
                        if (localTxn == null || remote.updatedAt >= localTxn.updatedAt) {
                            dao.insertTransaction(remote)
                        }
                    }
                }
                _status.value = SyncStatus.Synced
            }
        }

        listeners += col("accounts").addSnapshotListener { snap, err ->
            if (err != null || snap == null) { handleErr(err); return@addSnapshotListener }
            scope.launch {
                snap.documentChanges.forEach { change ->
                    runCatching {
                        val doc = change.document
                        val remoteAcct = Account(
                            id        = doc.id,
                            name      = doc.str("name"),
                            type      = doc.str("type"),
                            balance   = doc.dbl("balance"),
                            icon      = doc.str("icon"),
                            color     = doc.str("color"),
                            notes     = doc.str("notes"),
                            projectId = doc.str("projectId"),
                            updatedAt = doc.lng("updatedAt"),
                            createdAt = doc.lng("createdAt")
                        )
                        val localAcct = dao.getAccountById(doc.id)
                        if (localAcct == null || remoteAcct.updatedAt >= localAcct.updatedAt) {
                            dao.insertAccount(remoteAcct)
                        }
                    }
                }
                _status.value = SyncStatus.Synced
            }
        }

        listeners += col("investments").addSnapshotListener { snap, err ->
            if (err != null || snap == null) { handleErr(err); return@addSnapshotListener }
            scope.launch {
                snap.documentChanges.forEach { change ->
                    runCatching {
                        val doc = change.document
                        dao.insertInvestment(Investment(
                            id           = doc.id,
                            name         = doc.str("name"),
                            type         = doc.str("type"),
                            subtype      = doc.str("subtype"),
                            invested     = doc.dbl("invested"),
                            currentVal   = doc.dbl("currentVal"),
                            date         = doc.str("date"),
                            maturityDate = doc.str("maturityDate"),
                            rate         = doc.dbl("rate"),
                            realized     = doc.dbl("realized"),
                            withdrawn    = doc.dbl("withdrawn"),
                            notes        = doc.str("notes"),
                            projectId    = doc.str("projectId"),
                            updatedAt    = doc.lng("updatedAt"),
                            fundingSource = doc.str("fundingSource"),
                            sourceType    = doc.str("sourceType"),
                            linkedDebtId  = doc.str("linkedDebtId"),
                            createdAt     = doc.lng("createdAt")
                        ))
                    }
                }
                _status.value = SyncStatus.Synced
            }
        }

        listeners += col("investment_valuations").addSnapshotListener { snap, err ->
            if (err != null || snap == null) { handleErr(err); return@addSnapshotListener }
            scope.launch {
                snap.documentChanges.forEach { change ->
                    runCatching {
                        val doc = change.document
                        if (change.type == DocumentChange.Type.REMOVED) {
                            // dao.deleteValuationById(doc.id) // if we add this
                            return@runCatching
                        }
                        val remote = InvestmentValuation(
                            id = doc.id,
                            investmentId = doc.str("investmentId"),
                            date = doc.str("date"),
                            value = doc.dbl("value"),
                            notes = doc.str("notes"),
                            updatedAt = doc.lng("updatedAt")
                        )
                        dao.insertValuation(remote)
                    }
                }
                _status.value = SyncStatus.Synced
            }
        }

        listeners += col("debts").addSnapshotListener { snap, err ->
            if (err != null || snap == null) { handleErr(err); return@addSnapshotListener }
            scope.launch {
                snap.documentChanges.forEach { change ->
                    runCatching {
                        val doc = change.document
                        dao.insertDebt(Debt(
                            id        = doc.id,
                            name      = doc.str("name"),
                            phone     = doc.str("phone"),
                            type      = doc.str("type"),
                            amount    = doc.dbl("amount"),
                            rate      = doc.dbl("rate"),
                            date      = doc.str("date"),
                            due       = doc.str("due"),
                            tenure    = doc.int("tenure"),
                            intType   = doc.str("intType"),
                            paid      = doc.dbl("paid"),
                            paidPrincipal = doc.dbl("paidPrincipal"),
                            paidInterest  = doc.dbl("paidInterest"),
                            status    = doc.str("status"),
                            collateral = doc.str("collateral"),
                            notes     = doc.str("notes"),
                            projectId = doc.str("projectId"),
                            updatedAt = doc.lng("updatedAt"),
                            createdAt = doc.lng("createdAt")
                        ))
                    }
                }
                _status.value = SyncStatus.Synced
            }
        }

        listeners += col("people").addSnapshotListener { snap, err ->
            if (err != null || snap == null) { handleErr(err); return@addSnapshotListener }
            scope.launch {
                snap.documentChanges.forEach { change ->
                    runCatching {
                        val doc = change.document
                        dao.insertPerson(Person(
                            id        = doc.id,
                            name      = doc.str("name"),
                            phone     = doc.str("phone"),
                            type      = doc.str("type"),
                            notes     = doc.str("notes"),
                            projectId = doc.str("projectId"),
                            updatedAt = doc.lng("updatedAt"),
                            createdAt = doc.lng("createdAt")
                        ))
                    }
                }
                _status.value = SyncStatus.Synced
            }
        }

        listeners += col("projects").addSnapshotListener { snap, err ->
            if (err != null || snap == null) { handleErr(err); return@addSnapshotListener }
            scope.launch {
                snap.documentChanges.forEach { change ->
                    runCatching {
                        val doc = change.document
                        dao.insertProject(Project(
                            id        = doc.id,
                            name      = doc.str("name"),
                            icon      = doc.str("icon"),
                            color     = doc.str("color"),
                            currency  = doc.str("currency"),
                            createdAt = doc.str("createdAt"),
                            updatedAt = doc.lng("updatedAt")
                        ))
                    }
                }
                _status.value = SyncStatus.Synced
            }
        }

        // ── Payments (loan repayments) — previously write-only, now synced down ──
        listeners += col("payments").addSnapshotListener { snap, err ->
            if (err != null || snap == null) { handleErr(err); return@addSnapshotListener }
            scope.launch {
                snap.documentChanges.forEach { change ->
                    runCatching {
                        val doc = change.document
                        if (change.type == DocumentChange.Type.REMOVED) { dao.deletePaymentById(doc.id); return@runCatching }
                        dao.insertPayment(Payment(
                            id        = doc.id,
                            loanId    = doc.str("loanId"),
                            name      = doc.str("name"),
                            date      = doc.str("date"),
                            type      = doc.str("type"),
                            amount    = doc.dbl("amount"),
                            principal = doc.dbl("principal"),
                            interest  = doc.dbl("interest"),
                            mode      = doc.str("mode"),
                            notes     = doc.str("notes"),
                            projectId = doc.str("projectId"),
                            updatedAt = doc.lng("updatedAt"),
                            createdAt = doc.lng("createdAt")
                        ))
                    }
                }
                _status.value = SyncStatus.Synced
            }
        }

        // ── Debt payments — previously write-only, now synced down ──────────────
        listeners += col("debt_payments").addSnapshotListener { snap, err ->
            if (err != null || snap == null) { handleErr(err); return@addSnapshotListener }
            scope.launch {
                snap.documentChanges.forEach { change ->
                    runCatching {
                        val doc = change.document
                        if (change.type == DocumentChange.Type.REMOVED) { dao.deleteDebtPaymentById(doc.id); return@runCatching }
                        dao.insertDebtPayment(DebtPayment(
                            id        = doc.id,
                            debtId    = doc.str("debtId"),
                            name      = doc.str("name"),
                            date      = doc.str("date"),
                            type      = doc.str("type"),
                            amount    = doc.dbl("amount"),
                            principal = doc.dbl("principal"),
                            interest  = doc.dbl("interest"),
                            mode      = doc.str("mode"),
                            notes     = doc.str("notes"),
                            projectId = doc.str("projectId"),
                            updatedAt = doc.lng("updatedAt"),
                            createdAt = doc.lng("createdAt")
                        ))
                    }
                }
                _status.value = SyncStatus.Synced
            }
        }

        // ── Budgets — previously write-only, now synced down ────────────────────
        listeners += col("budgets").addSnapshotListener { snap, err ->
            if (err != null || snap == null) { handleErr(err); return@addSnapshotListener }
            scope.launch {
                snap.documentChanges.forEach { change ->
                    runCatching {
                        val doc = change.document
                        if (change.type == DocumentChange.Type.REMOVED) { dao.deleteBudgetByCategory(doc.id); return@runCatching }
                        dao.insertBudget(Budget(
                            category    = doc.id,
                            limitAmount = doc.dbl("limitAmount"),
                            period      = doc.str("period"),
                            projectId   = doc.str("projectId"),
                            updatedAt   = doc.lng("updatedAt"),
                            createdAt   = doc.lng("createdAt")
                        ))
                    }
                }
                _status.value = SyncStatus.Synced
            }
        }

        // ── Goals — previously write-only, now synced down ──────────────────────
        listeners += col("goals").addSnapshotListener { snap, err ->
            if (err != null || snap == null) { handleErr(err); return@addSnapshotListener }
            scope.launch {
                snap.documentChanges.forEach { change ->
                    runCatching {
                        val doc = change.document
                        if (change.type == DocumentChange.Type.REMOVED) { dao.deleteGoalById(doc.id); return@runCatching }
                        dao.insertGoal(Goal(
                            id           = doc.id,
                            name         = doc.str("name"),
                            targetAmount = doc.dbl("targetAmount"),
                            savedAmount  = doc.dbl("savedAmount"),
                            deadline     = doc.str("deadline"),
                            notes        = doc.str("notes"),
                            projectId    = doc.str("projectId"),
                            updatedAt    = doc.lng("updatedAt"),
                            createdAt    = doc.lng("createdAt")
                        ))
                    }
                }
                _status.value = SyncStatus.Synced
            }
        }

        _status.value = SyncStatus.Synced
    }

    fun setSyncing() { _status.value = SyncStatus.Syncing }
    fun setSynced()  { _status.value = SyncStatus.Synced  }

    /** Call from Application.onTerminate or when user signs out. */
    fun stopListening() {
        listeners.forEach { it.remove() }
        listeners.clear()
        scope.cancel()
    }

    // ── Extension helpers to safely read Firestore fields ─────────────────────
    private fun DocumentSnapshot.str(key: String)  = getString(key)  ?: ""
    private fun DocumentSnapshot.dbl(key: String)  = getDouble(key)  ?: 0.0
    private fun DocumentSnapshot.lng(key: String)  = getLong(key)    ?: 0L
    private fun DocumentSnapshot.int(key: String)  = getLong(key)?.toInt() ?: 0
}