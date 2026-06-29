package app.fynlo.data.remote

import app.fynlo.data.SyncStatus
import app.fynlo.data.local.FynloDao
import app.fynlo.data.model.*
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

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
    private var scope  = CoroutineScope(Dispatchers.IO + SupervisorJob())
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

    private suspend fun deletedByAudit(entityType: String, entityId: String, remoteUpdatedAt: Long): Boolean {
        val deletedAt = dao.latestDeleteAuditTimestamp(entityType, entityId) ?: return false
        return deletedAt >= remoteUpdatedAt
    }

    private suspend fun rejectDeletedRemoteDoc(collection: String, doc: DocumentSnapshot) {
        dao.insertDeletedRemoteDoc(DeletedRemoteDoc(collection, doc.id))
        runCatching { doc.reference.delete().await() }
    }

    private suspend fun recordConflict(
        collection: String,
        entityId: String,
        fieldSummary: String,
        localValue: Any,
        remoteValue: Any,
        projectId: String,
    ) {
        val now = System.currentTimeMillis()
        dao.insertSyncConflict(
            SyncConflict(
                id = "$collection:$entityId:$now",
                collection = collection,
                entityId = entityId,
                fieldSummary = fieldSummary.take(220),
                localJson = localValue.toString().take(4_000),
                remoteJson = remoteValue.toString().take(4_000),
                projectId = projectId.ifBlank { "personal" },
                updatedAt = now,
                createdAt = now,
            )
        )
    }

    /** Start real-time listeners for every collection. */
    fun startListening() {
        if (userId.isEmpty()) return
        if (scope.coroutineContext[Job]?.isCancelled == true) {
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        }
        if (listeners.isNotEmpty()) return

        listeners += col("borrowers").addSnapshotListener { snap, err ->
            if (err != null || snap == null) { handleErr(err); return@addSnapshotListener }
            scope.launch {
                snap.documentChanges.forEach { change ->
                    runCatching {
                        val doc = change.document
                        if (change.type == DocumentChange.Type.REMOVED) { dao.deleteBorrowerById(doc.id); return@runCatching }
                        if (dao.isRemoteDocDeleted("borrowers", doc.id)) return@runCatching
                        if (deletedByAudit("loan", doc.id, doc.lng("updatedAt"))) {
                            dao.deleteBorrowerById(doc.id)
                            rejectDeletedRemoteDoc("borrowers", doc)
                            return@runCatching
                        }
                        val remote = Borrower(
                            id        = doc.id,
                            name      = doc.str("name"),
                            phone     = doc.str("phone"),
                            // C03b Stage #3 (3.2.90) — additive Person FK.
                            // Legacy docs lack this key; doc.str returns "".
                            peopleId  = doc.str("peopleId"),
                            address   = doc.str("address"),
                            guarantor = doc.str("guarantor"),
                            amount    = doc.dbl("amount"),
                            rate      = doc.dbl("rate"),
                            date      = doc.str("date"),
                            due       = doc.str("due"),
                            tenure    = doc.int("tenure"),
                            // C03b Stage #2 (3.2.85) — Kotlin property renamed
                            // to `intType`; Firestore document field stays "type"
                            // (matches the @SerialName + @ColumnInfo pinning).
                            intType   = doc.str("type"),
                            paid      = doc.dbl("paid"),
                            paidPrincipal  = doc.dbl("paidPrincipal"),
                            paidInterest   = doc.dbl("paidInterest"),
                            interestWaived = doc.dbl("interestWaived"),
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
                            // 3.2.73 — audit any `paid` delta caused by sync.
                            // Borrower.paid feeds into net worth via
                            // "outstanding = amount - paid" so a sync rewrite
                            // here shifts the hero number with no visible
                            // transaction. Threshold filters out exact-equal
                            // overwrites that are net no-ops.
                            val oldPaid = local?.paid ?: 0.0
                            val paidDelta = remote.paid - oldPaid
                            if (kotlin.math.abs(paidDelta) > 0.005) {
                                app.fynlo.logic.BalanceAuditLog.record(
                                    source  = app.fynlo.logic.BalanceAuditLog.Source.SYNC_PULL_BORROWER,
                                    account = "Loan: ${remote.name}",
                                    delta   = paidDelta,
                                    note    = if (local == null) "First sync of new borrower"
                                              else "Firestore overwrote local borrower.paid (${oldPaid} → ${remote.paid})",
                                )
                            }
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
                        if (dao.isRemoteDocDeleted("transactions", doc.id)) return@runCatching
                        val remote = Transaction(
                            id        = doc.id,
                            date      = doc.str("date"),
                            type      = doc.str("type"),
                            amount    = doc.dbl("amount"),
                            fromAcct  = doc.str("fromAcct"),
                            toAcct    = doc.str("toAcct"),
                            // C03b Stage #1a (3.2.86) — additive id mirror.
                            // Older Firestore docs lack these keys; doc.str
                            // returns "" in that case so legacy docs hydrate
                            // with empty ids (re-resolved on next local
                            // edit via withResolvedAccountIds).
                            fromAcctId = doc.str("fromAcctId"),
                            toAcctId   = doc.str("toAcctId"),
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
                        if (localTxn != null &&
                            remote.updatedAt > localTxn.updatedAt &&
                            localTxn.updatedAt > 0L &&
                            localTxn.copy(updatedAt = remote.updatedAt) != remote
                        ) {
                            recordConflict(
                                collection = "transactions",
                                entityId = remote.id,
                                fieldSummary = "Transaction changed on cloud after local edit",
                                localValue = localTxn,
                                remoteValue = remote,
                                projectId = remote.projectId,
                            )
                        }
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
                        if (change.type == DocumentChange.Type.REMOVED) {
                            dao.deleteAccountById(doc.id)
                            return@runCatching
                        }
                        if (dao.isRemoteDocDeleted("accounts", doc.id)) return@runCatching
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
                        if (localAcct != null &&
                            remoteAcct.updatedAt > localAcct.updatedAt &&
                            localAcct.updatedAt > 0L &&
                            localAcct.copy(updatedAt = remoteAcct.updatedAt) != remoteAcct
                        ) {
                            recordConflict(
                                collection = "accounts",
                                entityId = remoteAcct.id,
                                fieldSummary = "Account changed on cloud after local edit",
                                localValue = localAcct,
                                remoteValue = remoteAcct,
                                projectId = remoteAcct.projectId,
                            )
                        }
                        if (localAcct == null || remoteAcct.updatedAt >= localAcct.updatedAt) {
                            // 3.2.72 — audit the balance delta caused by a
                            // Firestore listener overwrite. This is THE
                            // suspected root-cause of "net worth changes on
                            // every install": if Firestore's balance lags
                            // local, the listener replaces the local row and
                            // your net worth jumps back to the cloud value.
                            // The delta surfaces in the diagnostic screen so
                            // the user can see exactly which account is
                            // bouncing and by how much.
                            val oldBal = localAcct?.balance ?: 0.0
                            val delta = remoteAcct.balance - oldBal
                            if (kotlin.math.abs(delta) > 0.005) {
                                app.fynlo.logic.BalanceAuditLog.record(
                                    source  = app.fynlo.logic.BalanceAuditLog.Source.SYNC_PULL,
                                    account = remoteAcct.name,
                                    delta   = delta,
                                    note    = if (localAcct == null) "First sync of new account"
                                              else "Firestore listener overwrote local balance ($oldBal → ${remoteAcct.balance})",
                                )
                            }
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
                        if (change.type == DocumentChange.Type.REMOVED) {
                            dao.deleteInvestmentById(doc.id)
                            return@runCatching
                        }
                        if (dao.isRemoteDocDeleted("investments", doc.id)) return@runCatching
                        val remote = Investment(
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
                        )
                        val local = dao.getInvestmentById(remote.id)
                        if (local == null || remote.updatedAt >= local.updatedAt) {
                            dao.insertInvestment(remote)
                        }
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
                            dao.deleteValuationById(doc.id)
                            return@runCatching
                        }
                        val remote = InvestmentValuation(
                            id = doc.id,
                            investmentId = doc.str("investmentId"),
                            date = doc.str("date"),
                            value = doc.dbl("value"),
                            notes = doc.str("notes"),
                            projectId = doc.strOr("projectId", "personal"),
                            updatedAt = doc.lng("updatedAt"),
                            createdAt = doc.lng("createdAt")
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
                        if (change.type == DocumentChange.Type.REMOVED) { dao.deleteDebtById(doc.id); return@runCatching }
                        if (dao.isRemoteDocDeleted("debts", doc.id)) return@runCatching
                        if (deletedByAudit("debt", doc.id, doc.lng("updatedAt"))) {
                            dao.deleteDebtById(doc.id)
                            rejectDeletedRemoteDoc("debts", doc)
                            return@runCatching
                        }
                        val remote = Debt(
                            id        = doc.id,
                            name      = doc.str("name"),
                            phone     = doc.str("phone"),
                            // C03b Stage #3 (3.2.90) — additive Person FK.
                            peopleId  = doc.str("peopleId"),
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
                            interestWaived = doc.dbl("interestWaived"),
                            status    = doc.str("status"),
                            collateral = doc.str("collateral"),
                            notes     = doc.str("notes"),
                            projectId = doc.str("projectId"),
                            updatedAt = doc.lng("updatedAt"),
                            createdAt = doc.lng("createdAt")
                        )
                        // 3.2.73 — was an unconditional `dao.insertDebt(remote)`
                        // (missing the `updatedAt >= local` guard that borrowers
                        // and accounts use). That meant every Firestore snapshot
                        // overwrote local debts — including the `paid` total —
                        // so local payments would silently revert to the cloud
                        // value on the next sync. Added the guard + audit log
                        // for any paid delta caused by the overwrite.
                        val local = dao.getDebtById(doc.id)
                        if (local == null || remote.updatedAt >= local.updatedAt) {
                            val oldPaid = local?.paid ?: 0.0
                            val paidDelta = remote.paid - oldPaid
                            if (kotlin.math.abs(paidDelta) > 0.005) {
                                app.fynlo.logic.BalanceAuditLog.record(
                                    source  = app.fynlo.logic.BalanceAuditLog.Source.SYNC_PULL_DEBT,
                                    account = "Debt: ${remote.name}",
                                    delta   = paidDelta,
                                    note    = if (local == null) "First sync of new debt"
                                              else "Firestore overwrote local debt.paid (${oldPaid} → ${remote.paid})",
                                )
                            }
                            dao.insertDebt(remote)
                        }
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
                        if (change.type == DocumentChange.Type.REMOVED) {
                            dao.deletePersonById(doc.id)
                            return@runCatching
                        }
                        if (dao.isRemoteDocDeleted("people", doc.id)) return@runCatching
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
                        if (change.type == DocumentChange.Type.REMOVED) {
                            dao.deleteProjectById(doc.id)
                            return@runCatching
                        }
                        if (dao.isRemoteDocDeleted("projects", doc.id)) return@runCatching
                        dao.insertProject(Project(
                            id        = doc.id,
                            name      = doc.str("name"),
                            icon      = doc.str("icon"),
                            color     = doc.str("color"),
                            currency  = doc.str("currency"),
                            createdAt = doc.str("createdAt"),
                            updatedAt = doc.lng("updatedAt"),
                            description = doc.str("description")
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
                        if (dao.isRemoteDocDeleted("payments", doc.id)) return@runCatching
                        if (deletedByAudit("loan", doc.str("loanId"), doc.lng("updatedAt"))) {
                            dao.deletePaymentById(doc.id)
                            rejectDeletedRemoteDoc("payments", doc)
                            return@runCatching
                        }
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
                        if (dao.isRemoteDocDeleted("debt_payments", doc.id)) return@runCatching
                        if (deletedByAudit("debt", doc.str("debtId"), doc.lng("updatedAt"))) {
                            dao.deleteDebtPaymentById(doc.id)
                            rejectDeletedRemoteDoc("debt_payments", doc)
                            return@runCatching
                        }
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
                            createdAt   = doc.lng("createdAt"),
                            alertThresholdPct = doc.intOr("alertThresholdPct", 80)
                        ))
                    }
                }
                _status.value = SyncStatus.Synced
            }
        }

        // ── Recurring transactions — previously local-only, now synced ──────────
        listeners += col("recurring_transactions").addSnapshotListener { snap, err ->
            if (err != null || snap == null) { handleErr(err); return@addSnapshotListener }
            scope.launch {
                snap.documentChanges.forEach { change ->
                    runCatching {
                        val doc = change.document
                        if (change.type == DocumentChange.Type.REMOVED) { dao.deleteRecurringById(doc.id); return@runCatching }
                        dao.insertRecurringTransaction(RecurringTransaction(
                            id         = doc.id,
                            name       = doc.str("name"),
                            type       = doc.str("type"),
                            amount     = doc.dbl("amount"),
                            category   = doc.str("category"),
                            fromAcct   = doc.str("fromAcct"),
                            toAcct     = doc.str("toAcct"),
                            // C03b Stage #1c (3.2.89) — additive id mirror.
                            // Legacy docs lack these keys; doc.str returns "".
                            fromAcctId = doc.str("fromAcctId"),
                            toAcctId   = doc.str("toAcctId"),
                            frequency  = doc.str("frequency"),
                            dayOfMonth = doc.int("dayOfMonth"),
                            notes      = doc.str("notes"),
                            isActive   = doc.bool("isActive"),
                            lastRun    = doc.str("lastRun"),
                            projectId  = doc.str("projectId"),
                            updatedAt  = doc.lng("updatedAt"),
                            createdAt  = doc.lng("createdAt")
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
                            createdAt    = doc.lng("createdAt"),
                            iconKey      = doc.strOr("iconKey", "star"),
                            linkedAccount = doc.str("linkedAccount")
                        ))
                    }
                }
                _status.value = SyncStatus.Synced
            }
        }

        // Audit trail is append-only diagnostic data. It must sync so a reinstall
        // or second device can explain money-action changes instead of showing an
        // empty trail after Firestore restore.
        listeners += col("audit_events").addSnapshotListener { snap, err ->
            if (err != null || snap == null) { handleErr(err); return@addSnapshotListener }
            scope.launch {
                snap.documentChanges.forEach { change ->
                    runCatching {
                        val doc = change.document
                        if (change.type == DocumentChange.Type.REMOVED) return@runCatching
                        dao.insertAuditEvent(AuditEvent(
                            id          = doc.id,
                            timestamp   = doc.lng("timestamp"),
                            action      = doc.str("action"),
                            entityType  = doc.str("entityType"),
                            entityId    = doc.str("entityId"),
                            title       = doc.str("title"),
                            beforeValue = doc.str("beforeValue"),
                            afterValue  = doc.str("afterValue"),
                            amountDelta = doc.dbl("amountDelta"),
                            accountName = doc.str("accountName"),
                            projectId   = doc.strOr("projectId", "personal"),
                            reason      = doc.str("reason"),
                            actor       = doc.strOr("actor", "remote")
                        ))
                    }
                }
                _status.value = SyncStatus.Synced
            }
        }

        listeners += col("monthly_closes").addSnapshotListener { snap, err ->
            if (err != null || snap == null) { handleErr(err); return@addSnapshotListener }
            scope.launch {
                snap.documentChanges.forEach { change ->
                    runCatching {
                        val doc = change.document
                        if (change.type == DocumentChange.Type.REMOVED) {
                            dao.deleteMonthlyCloseById(doc.id)
                            return@runCatching
                        }
                        dao.insertMonthlyClose(MonthlyClose(
                            id = doc.id,
                            projectId = doc.strOr("projectId", "personal"),
                            month = doc.str("month"),
                            status = doc.strOr("status", "Closed"),
                            note = doc.str("note"),
                            closedAt = doc.lng("closedAt"),
                            reopenedAt = doc.lng("reopenedAt"),
                            updatedAt = doc.lng("updatedAt"),
                            createdAt = doc.lng("createdAt"),
                        ))
                    }
                }
                _status.value = SyncStatus.Synced
            }
        }

        listeners += col("proof_attachments").addSnapshotListener { snap, err ->
            if (err != null || snap == null) { handleErr(err); return@addSnapshotListener }
            scope.launch {
                snap.documentChanges.forEach { change ->
                    runCatching {
                        val doc = change.document
                        if (change.type == DocumentChange.Type.REMOVED) {
                            dao.deleteProofAttachmentById(doc.id)
                            return@runCatching
                        }
                        dao.insertProofAttachment(ProofAttachment(
                            id = doc.id,
                            ownerType = doc.str("ownerType"),
                            ownerId = doc.str("ownerId"),
                            displayName = doc.str("displayName"),
                            mimeType = doc.str("mimeType"),
                            localUri = doc.str("localUri"),
                            remotePath = doc.str("remotePath"),
                            note = doc.str("note"),
                            projectId = doc.strOr("projectId", "personal"),
                            updatedAt = doc.lng("updatedAt"),
                            createdAt = doc.lng("createdAt"),
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
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    // ── Extension helpers to safely read Firestore fields ─────────────────────
    private fun DocumentSnapshot.str(key: String)  = getString(key)  ?: ""
    private fun DocumentSnapshot.strOr(key: String, default: String) = getString(key)?.takeIf { it.isNotBlank() } ?: default
    private fun DocumentSnapshot.dbl(key: String)  = getDouble(key)  ?: 0.0
    private fun DocumentSnapshot.lng(key: String)  = getLong(key)    ?: 0L
    private fun DocumentSnapshot.int(key: String)  = getLong(key)?.toInt() ?: 0
    private fun DocumentSnapshot.intOr(key: String, default: Int) = getLong(key)?.toInt() ?: default
    private fun DocumentSnapshot.bool(key: String) = getBoolean(key) ?: false
}
