package app.fynlo.data

import app.fynlo.data.local.FynloDao
import app.fynlo.data.local.FynloDatabase
import app.fynlo.data.model.*
import app.fynlo.data.remote.FirestoreRepository
import app.fynlo.data.remote.SyncManager
import app.fynlo.data.remote.deleteFirestoreUserTree
import app.fynlo.logic.CurrencyFormatter
import app.fynlo.logic.isGeneratedJournalEntry
import app.fynlo.logic.resolveAccountIdsWith
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.room.withTransaction
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class FinanceRepository(
    val dao: FynloDao,
    private val db: FynloDatabase,
    private var firestore: FirestoreRepository,
    var syncManager: SyncManager
) {
    @kotlinx.serialization.Serializable
    private data class LoanUndoBundle(
        val borrower: Borrower,
        val transactions: List<Transaction> = emptyList(),
        val payments: List<Payment> = emptyList(),
    )

    @kotlinx.serialization.Serializable
    private data class DebtUndoBundle(
        val debt: Debt,
        val transactions: List<Transaction> = emptyList(),
        val payments: List<DebtPayment> = emptyList(),
    )

    @kotlinx.serialization.Serializable
    private data class PaymentUndoBundle(
        val payment: Payment,
        val transactions: List<Transaction> = emptyList(),
    )

    @kotlinx.serialization.Serializable
    private data class DebtPaymentUndoBundle(
        val payment: DebtPayment,
        val transactions: List<Transaction> = emptyList(),
    )

    @kotlinx.serialization.Serializable
    private data class InvestmentUndoBundle(
        val investment: Investment,
        val transactions: List<Transaction> = emptyList(),
        val linkedDebt: Debt? = null,
        val replayBalancesOnRestore: Boolean = true,
    )

    @kotlinx.serialization.Serializable
    private data class InvestmentEditUndoBundle(
        val before: Investment,
        val createdTransactions: List<Transaction> = emptyList(),
    )

    val syncStatus: StateFlow<SyncStatus> get() = syncManager.status

    /** Called after anonymous auth completes — swaps in the real instances. */
    fun updateRemote(newFirestore: FirestoreRepository, newSync: SyncManager) {
        firestore   = newFirestore
        syncManager = newSync
    }
    val allBorrowers: Flow<List<Borrower>>       = dao.getAllBorrowers()
    val allTransactions: Flow<List<Transaction>> = dao.getAllTransactions()
    val allAccounts: Flow<List<Account>>         = dao.getAllAccounts()
    val allInvestments: Flow<List<Investment>>   = dao.getAllInvestments()
    val allDebts: Flow<List<Debt>>               = dao.getAllDebts()
    val allPayments: Flow<List<Payment>>         = dao.getAllPayments()
    val allDebtPayments: Flow<List<DebtPayment>> = dao.getAllDebtPayments()
    val allPeople: Flow<List<Person>>            = dao.getAllPeople()
    val allBudgets: Flow<List<Budget>>           = dao.getAllBudgets()
    val allGoals: Flow<List<Goal>>               = dao.getAllGoals()
    val allProjects: Flow<List<Project>>         = dao.getAllProjects()
    val allValuations: Flow<List<InvestmentValuation>> = dao.getAllValuations()
    val allAuditEvents: Flow<List<AuditEvent>>   = dao.getAllAuditEvents()
    val allMonthlyCloses: Flow<List<MonthlyClose>> = dao.getAllMonthlyCloses()
    val allProofAttachments: Flow<List<ProofAttachment>> = dao.getAllProofAttachments()
    val openSyncConflicts: Flow<List<SyncConflict>> = dao.getOpenSyncConflicts()
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private val undoJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    fun sync(block: suspend FirestoreRepository.() -> Unit) {
        // Don't attempt Firestore writes without a real authenticated user
        if (syncManager.userId.isEmpty()) return
        ioScope.launch {
            syncManager.setSyncing()
            runCatching { firestore.block() }
                .onFailure { e ->
                    android.util.Log.e("FynloSync", "Firestore write failed: ${e.message}")
                }
            syncManager.setSynced()
        }
    }

    /** Fetch account from Room by name and push updated balance directly to Firestore. */
    private suspend fun syncAccountByName(name: String) {
        if (name.isBlank()) return
        runCatching {
            kotlinx.coroutines.delay(200)
            val account = dao.getAccountByName(name) ?: return
            val uid     = syncManager.userId
            if (uid.isEmpty()) return

            // Use Firebase directly with the confirmed current UID
            // This bypasses any stale FirestoreRepository reference
            val fs = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            if (account.id.isNotBlank()) {
                fs.collection("users")
                    .document(uid)
                    .collection("accounts")
                    .document(account.id)
                    .update("balance", account.balance, "updatedAt", System.currentTimeMillis())
                    .await()
            }
        }
    }
    suspend fun insertProject(project: Project) {
        val p = project.copy(updatedAt = System.currentTimeMillis())
        dao.insertProject(p); sync { setProject(p) }
    }
    suspend fun deleteProject(project: Project) {
        dao.deleteProject(project); sync { deleteProject(project.id) }
    }
    /**
     * C03b Stage #1a (3.2.86) — adapter around the pure
     * `Transaction.withResolvedAccountIds` helper in `logic/`. Wires the
     * DAO's `getAccountByName` into the resolver's name→id callback so
     * every insert/edit path populates the immutable id mirror.
     *
     * If a name has no matching account (typo, deleted account, etc.), the
     * id stays `""` — Stage #1b will read this back and surface those rows
     * to the existing 3.2.59 orphan-repair tool.
     */
    /**
     * C03b Stage #1b-1 (3.2.87) — apply a balance delta using the immutable
     * account id when available, falling back to name-based lookup for
     * legacy rows whose id mirror is still empty.
     *
     * Why the fallback exists: Stage #1a's resolver populates ids on every
     * NEW write, and the v22→v23 migration backfilled most existing rows
     * by joining on name. But a row whose `fromAcct` doesn't match any
     * current account (typo, deleted account) keeps id = "" — those rows
     * still need the legacy name-keyed UPDATE to behave the same way it
     * did pre-Stage #1a, so the orphan-repair tool from 3.2.59 keeps
     * working.
     *
     * For rows with id set, this is **rename-safe**: an account rename
     * mutates `Account.name` only; `Account.id` is immutable, so the
     * balance update lands on the right row regardless of any name
     * changes since the transaction was recorded.
     */
    private suspend fun applyAccountDelta(idOrEmpty: String, nameFallback: String, delta: Double) {
        if (idOrEmpty.isNotEmpty()) {
            dao.updateAccountBalanceById(idOrEmpty, delta)
        } else if (nameFallback.isNotBlank()) {
            dao.updateAccountBalance(nameFallback, delta)
        }
    }

    private suspend fun applyTransactionBalance(txn: Transaction) {
        if (txn.tags.contains("journal_only", ignoreCase = true)) return
        when (txn.type.lowercase()) {
            "expense" -> applyAccountDelta(txn.fromAcctId, txn.fromAcct, -txn.amount)
            "income" -> applyAccountDelta(txn.toAcctId, txn.toAcct, txn.amount)
            "transfer" -> {
                applyAccountDelta(txn.fromAcctId, txn.fromAcct, -txn.amount)
                applyAccountDelta(txn.toAcctId, txn.toAcct, txn.amount)
            }
        }
    }

    private suspend fun reverseTransactionBalance(txn: Transaction) {
        if (txn.tags.contains("journal_only", ignoreCase = true)) return
        when (txn.type.lowercase()) {
            "expense" -> applyAccountDelta(txn.fromAcctId, txn.fromAcct, txn.amount)
            "income" -> applyAccountDelta(txn.toAcctId, txn.toAcct, -txn.amount)
            "transfer" -> {
                applyAccountDelta(txn.fromAcctId, txn.fromAcct, txn.amount)
                applyAccountDelta(txn.toAcctId, txn.toAcct, -txn.amount)
            }
        }
    }

    private suspend fun syncTouchedAccounts(transactions: List<Transaction>) {
        transactions.flatMap { listOf(it.fromAcct, it.toAcct) }
            .filter { it.isNotBlank() }
            .distinct()
            .forEach { syncAccountByName(it) }
    }

    private suspend fun tombstoneRemoteDoc(collection: String, id: String) {
        if (id.isNotBlank()) {
            dao.insertDeletedRemoteDoc(DeletedRemoteDoc(collection, id))
        }
    }

    class ClosedPeriodException(month: String) : IllegalStateException(
        "This period is closed ($month). Reopen the month before changing past entries."
    )

    private fun monthKey(date: String): String = date.take(7)

    private suspend fun requireOpenDate(date: String, projectId: String) {
        val month = monthKey(date)
        if (month.length == 7 && dao.getClosedMonth(projectId.ifBlank { "personal" }, month) != null) {
            throw ClosedPeriodException(month)
        }
    }

    private suspend fun recordUndo(
        action: String,
        entityType: String,
        entityId: String,
        title: String,
        beforeJson: String = "",
        afterJson: String = "",
        projectId: String = "personal",
    ) {
        val now = System.currentTimeMillis()
        dao.pruneUndoActions(now)
        dao.insertUndoAction(
            UndoAction(
                id = app.fynlo.logic.Ids.newId(),
                action = action,
                entityType = entityType,
                entityId = entityId,
                title = title.take(120),
                beforeJson = beforeJson,
                afterJson = afterJson,
                projectId = projectId.ifBlank { "personal" },
                expiresAt = now + 10 * 60 * 1000L,
                updatedAt = now,
                createdAt = now,
            )
        )
    }

    suspend fun closeMonth(projectId: String, month: String, note: String = "") {
        val now = System.currentTimeMillis()
        val close = MonthlyClose(
            id = "${projectId.ifBlank { "personal" }}:$month",
            projectId = projectId.ifBlank { "personal" },
            month = month,
            status = "Closed",
            note = note,
            closedAt = now,
            updatedAt = now,
            createdAt = now,
        )
        dao.insertMonthlyClose(close)
        recordAudit("CLOSE", "monthly_close", close.id, "Month closed: $month", afterValue = note, projectId = close.projectId)
        sync { setMonthlyClose(close) }
    }

    suspend fun reopenMonth(projectId: String, month: String, note: String = "") {
        val now = System.currentTimeMillis()
        val close = MonthlyClose(
            id = "${projectId.ifBlank { "personal" }}:$month",
            projectId = projectId.ifBlank { "personal" },
            month = month,
            status = "Reopened",
            note = note,
            closedAt = 0L,
            reopenedAt = now,
            updatedAt = now,
            createdAt = now,
        )
        dao.insertMonthlyClose(close)
        recordAudit("REOPEN", "monthly_close", close.id, "Month reopened: $month", afterValue = note, projectId = close.projectId)
        sync { setMonthlyClose(close) }
    }

    suspend fun addProofAttachment(attachment: ProofAttachment) {
        val now = System.currentTimeMillis()
        val saved = attachment.copy(updatedAt = now, createdAt = if (attachment.createdAt == 0L) now else attachment.createdAt)
        dao.insertProofAttachment(saved)
        recordAudit("ATTACH", saved.ownerType, saved.ownerId, "Proof attached: ${saved.displayName}", afterValue = saved.localUri, projectId = saved.projectId)
        sync { setProofAttachment(saved) }
    }

    suspend fun deleteProofAttachment(id: String) {
        dao.deleteProofAttachmentById(id)
        sync { deleteProofAttachment(id) }
    }

    suspend fun resolveSyncConflict(id: String, resolution: String) {
        val now = System.currentTimeMillis()
        val conflict = dao.getSyncConflictById(id)
        if (conflict != null) {
            val selectedJson = when (resolution) {
                "KeepPhone" -> conflict.localJson
                "KeepCloud" -> conflict.remoteJson
                else -> ""
            }
            if (selectedJson.isNotBlank()) {
                applySyncConflictSnapshot(conflict, selectedJson, now)
            }
        }
        val label = when (resolution) {
            "KeepPhone" -> "Kept phone copy"
            "KeepCloud" -> "Kept cloud copy"
            else -> resolution
        }
        dao.resolveSyncConflict(id, label, now)
    }

    private suspend fun applySyncConflictSnapshot(conflict: SyncConflict, snapshotJson: String, now: Long) {
        when (conflict.collection) {
            "accounts" -> {
                val account = runCatching { undoJson.decodeFromString<Account>(snapshotJson) }.getOrNull() ?: return
                val saved = account.copy(updatedAt = now)
                dao.insertAccount(saved)
                recordAudit(
                    action = "SYNC_RESOLVE",
                    entityType = "account",
                    entityId = saved.id,
                    title = "Sync conflict resolved: ${saved.name}",
                    afterValue = "${saved.name} ${CurrencyFormatter.detail(saved.balance)}",
                    reason = "Applied ${conflict.collection} ${conflict.entityId}",
                    projectId = saved.projectId,
                )
                sync { setAccount(saved) }
            }
            "transactions" -> {
                val txn = runCatching { undoJson.decodeFromString<Transaction>(snapshotJson) }.getOrNull() ?: return
                val saved = txn.copy(updatedAt = now).withResolvedAccountIds()
                dao.insertTransaction(saved)
                repairAccountBalanceDriftFromLedger()
                recordAudit(
                    action = "SYNC_RESOLVE",
                    entityType = "transaction",
                    entityId = saved.id,
                    title = "Sync conflict resolved: ${saved.desc.ifBlank { saved.category }}",
                    afterValue = "${saved.type} ${CurrencyFormatter.detail(saved.amount)}",
                    reason = "Applied ${conflict.collection} ${conflict.entityId}",
                    projectId = saved.projectId,
                )
                sync { setTransaction(saved) }
            }
        }
    }

    suspend fun undoLastMoneyAction(): Boolean {
        val now = System.currentTimeMillis()
        val action = dao.getLatestUndoAction(now) ?: return false
        when (action.entityType) {
            "transaction" -> when (action.action) {
                "CREATE" -> dao.getTransactionById(action.entityId)?.let { deleteTransaction(it) }
                "DELETE" -> {
                    val restored = undoJson.decodeFromString<Transaction>(action.beforeJson)
                    insertTransaction(restored.copy(updatedAt = now))
                }
                "EDIT" -> {
                    val before = undoJson.decodeFromString<Transaction>(action.beforeJson)
                    val current = dao.getTransactionById(action.entityId) ?: before
                    editTransaction(current, before.copy(updatedAt = now))
                }
            }
            "loan" -> when (action.action) {
                "CREATE" -> dao.getBorrowerById(action.entityId)?.let { deleteBorrower(it) }
                "DELETE" -> restoreLoanBundle(undoJson.decodeFromString(action.beforeJson))
                else -> return false
            }
            "debt" -> when (action.action) {
                "CREATE" -> dao.getDebtById(action.entityId)?.let { deleteDebt(it) }
                "DELETE" -> restoreDebtBundle(undoJson.decodeFromString(action.beforeJson))
                else -> return false
            }
            "payment" -> when (action.action) {
                "CREATE" -> undoPaymentCreate(undoJson.decodeFromString(action.afterJson))
                else -> return false
            }
            "debt_payment" -> when (action.action) {
                "CREATE" -> undoDebtPaymentCreate(undoJson.decodeFromString(action.afterJson))
                else -> return false
            }
            "investment" -> when (action.action) {
                "CREATE" -> undoInvestmentCreate(undoJson.decodeFromString(action.afterJson))
                "DELETE" -> restoreInvestmentBundle(undoJson.decodeFromString(action.beforeJson))
                "EDIT" -> undoInvestmentEdit(undoJson.decodeFromString(action.beforeJson))
                else -> return false
            }
            else -> return false
        }
        dao.markUndoConsumed(action.id, now)
        recordAudit("UNDO", action.entityType, action.entityId, "Undo: ${action.title}", projectId = action.projectId)
        return true
    }

    private suspend fun restoreLoanBundle(bundle: LoanUndoBundle) {
        db.withTransaction {
            dao.insertBorrower(bundle.borrower.copy(updatedAt = System.currentTimeMillis()))
            bundle.transactions.forEach {
                applyTransactionBalance(it)
                dao.insertTransaction(it.copy(updatedAt = System.currentTimeMillis()))
            }
            bundle.payments.forEach { dao.insertPayment(it.copy(updatedAt = System.currentTimeMillis())) }
            dao.rebuildBorrowerPaidFromPayments()
        }
        sync { setBorrower(bundle.borrower) }
        bundle.transactions.forEach { sync { setTransaction(it) } }
        bundle.payments.forEach { sync { setPayment(it) } }
        syncTouchedAccounts(bundle.transactions)
    }

    private suspend fun restoreDebtBundle(bundle: DebtUndoBundle) {
        db.withTransaction {
            dao.insertDebt(bundle.debt.copy(updatedAt = System.currentTimeMillis()))
            bundle.transactions.forEach {
                applyTransactionBalance(it)
                dao.insertTransaction(it.copy(updatedAt = System.currentTimeMillis()))
            }
            bundle.payments.forEach { dao.insertDebtPayment(it.copy(updatedAt = System.currentTimeMillis())) }
            dao.rebuildDebtPaidFromDebtPayments()
        }
        sync { setDebt(bundle.debt) }
        bundle.transactions.forEach { sync { setTransaction(it) } }
        bundle.payments.forEach { sync { setDebtPayment(it) } }
        syncTouchedAccounts(bundle.transactions)
    }

    private suspend fun undoPaymentCreate(bundle: PaymentUndoBundle) {
        db.withTransaction {
            bundle.transactions.forEach {
                reverseTransactionBalance(it)
                dao.deleteTransaction(it)
                tombstoneRemoteDoc("transactions", it.id)
            }
            dao.deletePayment(bundle.payment)
            tombstoneRemoteDoc("payments", bundle.payment.id)
            dao.rebuildBorrowerPaidFromPayments()
        }
        bundle.transactions.forEach { sync { deleteTransaction(it.id) } }
        sync { deletePayment(bundle.payment.id) }
        syncTouchedAccounts(bundle.transactions)
    }

    private suspend fun undoDebtPaymentCreate(bundle: DebtPaymentUndoBundle) {
        db.withTransaction {
            bundle.transactions.forEach {
                reverseTransactionBalance(it)
                dao.deleteTransaction(it)
                tombstoneRemoteDoc("transactions", it.id)
            }
            dao.deleteDebtPayment(bundle.payment)
            tombstoneRemoteDoc("debt_payments", bundle.payment.id)
            dao.rebuildDebtPaidFromDebtPayments()
        }
        bundle.transactions.forEach { sync { deleteTransaction(it.id) } }
        sync { deleteDebtPayment(bundle.payment.id) }
        syncTouchedAccounts(bundle.transactions)
    }

    private suspend fun undoInvestmentCreate(bundle: InvestmentUndoBundle) {
        db.withTransaction {
            bundle.transactions.forEach {
                reverseTransactionBalance(it)
                dao.deleteTransaction(it)
                tombstoneRemoteDoc("transactions", it.id)
            }
            bundle.linkedDebt?.let {
                dao.deleteDebt(it)
                tombstoneRemoteDoc("debts", it.id)
            }
            dao.deleteInvestment(bundle.investment)
            tombstoneRemoteDoc("investments", bundle.investment.id)
            dao.rebuildDebtPaidFromDebtPayments()
        }
        bundle.transactions.forEach { sync { deleteTransaction(it.id) } }
        bundle.linkedDebt?.let { sync { deleteDebt(it.id) } }
        sync { deleteInvestment(bundle.investment.id) }
        syncTouchedAccounts(bundle.transactions)
    }

    private suspend fun restoreInvestmentBundle(bundle: InvestmentUndoBundle) {
        db.withTransaction {
            bundle.linkedDebt?.let { dao.insertDebt(it.copy(updatedAt = System.currentTimeMillis())) }
            dao.insertInvestment(bundle.investment.copy(updatedAt = System.currentTimeMillis()))
            bundle.transactions.forEach {
                if (bundle.replayBalancesOnRestore) applyTransactionBalance(it)
                dao.insertTransaction(it.copy(updatedAt = System.currentTimeMillis()))
            }
            dao.rebuildDebtPaidFromDebtPayments()
        }
        bundle.linkedDebt?.let { sync { setDebt(it) } }
        sync { setInvestment(bundle.investment) }
        bundle.transactions.forEach { sync { setTransaction(it) } }
        syncTouchedAccounts(bundle.transactions)
    }

    private suspend fun undoInvestmentEdit(bundle: InvestmentEditUndoBundle) {
        db.withTransaction {
            bundle.createdTransactions.forEach {
                reverseTransactionBalance(it)
                dao.deleteTransaction(it)
                tombstoneRemoteDoc("transactions", it.id)
            }
            dao.insertInvestment(bundle.before.copy(updatedAt = System.currentTimeMillis()))
        }
        bundle.createdTransactions.forEach { sync { deleteTransaction(it.id) } }
        sync { setInvestment(bundle.before) }
        syncTouchedAccounts(bundle.createdTransactions)
    }

    private suspend fun recordAudit(
        action: String,
        entityType: String,
        entityId: String,
        title: String,
        beforeValue: String = "",
        afterValue: String = "",
        amountDelta: Double = 0.0,
        accountName: String = "",
        projectId: String = "personal",
        reason: String = "",
    ) {
        val event = AuditEvent(
            id = app.fynlo.logic.Ids.newId(),
            timestamp = System.currentTimeMillis(),
            action = action,
            entityType = entityType,
            entityId = entityId,
            title = title.take(120),
            beforeValue = beforeValue.take(600),
            afterValue = afterValue.take(600),
            amountDelta = amountDelta,
            accountName = accountName.take(120),
            projectId = projectId.ifBlank { "personal" },
            reason = reason.take(300),
        )
        dao.insertAuditEvent(event)
        sync { setAuditEvent(event) }
    }

    private fun Transaction.auditSummary(): String =
        "${type}:${category}:${amount}:${fromAcct}->${toAcct}:${date}:${desc}"

    private suspend fun Transaction.withResolvedAccountIds(): Transaction {
        // Resolve up front so the lookup closure passed to the pure helper
        // is synchronous (DAO calls are suspend; the helper is not).
        val fromId = if (fromAcct.isEmpty()) null else dao.getAccountByName(fromAcct)?.id
        val toId   = if (toAcct.isEmpty())   null else dao.getAccountByName(toAcct)?.id
        return resolveAccountIdsWith { name ->
            when (name) {
                fromAcct -> fromId
                toAcct   -> toId
                else     -> null
            }
        }
    }

    suspend fun insertTransaction(transaction: Transaction) {
        // C03a Stage 2: scrub forbidden literal categories ("Expense" / "Income"
        // / "Transfer" — those are types, not categories) before persisting.
        // C03b Stage #1a: resolve account-name strings into immutable
        // account ids so a rename can't orphan the row.
        val sanitized = TransactionValidator.sanitize(transaction).withResolvedAccountIds()
        requireOpenDate(sanitized.date, sanitized.projectId)
        val affectedAccounts = mutableListOf<String>()
        db.withTransaction {
            if (dao.getTransactionById(sanitized.id) != null) return@withTransaction
            val now = System.currentTimeMillis()
            val t = sanitized.copy(updatedAt = now, createdAt = if (sanitized.createdAt == 0L) now else sanitized.createdAt)
            dao.insertTransaction(t)
            // 3.2.72 — audit-log every balance write so the diagnostic
            // screen can show which subsystem moved the number. Note: a
            // typed-account-name mismatch will still update 0 rows
            // silently (orphan-account bug from 3.2.59) but the audit
            // entry records what was *attempted* — useful for spotting
            // a typo retroactively.
            val srcTag = app.fynlo.logic.BalanceAuditLog.Source.MANUAL_TXN
            val note   = "Add ${transaction.type.lowercase()} \"${transaction.desc.take(28)}\""
            // C03b Stage #1b-1: use `t` (post-resolver) here — its
            // fromAcctId / toAcctId are populated for rows with a matching
            // account, so the balance update is rename-safe.
            when (transaction.type.lowercase()) {
                "expense"  -> {
                    applyAccountDelta(t.fromAcctId, t.fromAcct, -t.amount)
                    affectedAccounts += t.fromAcct
                    app.fynlo.logic.BalanceAuditLog.record(srcTag, t.fromAcct, -t.amount, note)
                }
                "income"   -> {
                    applyAccountDelta(t.toAcctId, t.toAcct, t.amount)
                    affectedAccounts += t.toAcct
                    app.fynlo.logic.BalanceAuditLog.record(srcTag, t.toAcct, t.amount, note)
                }
                "transfer" -> {
                    applyAccountDelta(t.fromAcctId, t.fromAcct, -t.amount)
                    applyAccountDelta(t.toAcctId,   t.toAcct,    t.amount)
                    affectedAccounts += t.fromAcct
                    affectedAccounts += t.toAcct
                    app.fynlo.logic.BalanceAuditLog.record(srcTag, t.fromAcct, -t.amount, "$note (out)")
                    app.fynlo.logic.BalanceAuditLog.record(srcTag, t.toAcct,    t.amount, "$note (in)")
                }
            }
            recordAudit(
                action = "CREATE",
                entityType = "transaction",
                entityId = t.id,
                title = "Transaction added: ${t.category}",
                afterValue = t.auditSummary(),
                amountDelta = when (t.type.lowercase()) {
                    "expense" -> -t.amount
                    "income" -> t.amount
                    else -> 0.0
                },
                accountName = listOf(t.fromAcct, t.toAcct).filter { it.isNotBlank() }.joinToString(" -> "),
                projectId = t.projectId,
            )
            recordUndo(
                action = "CREATE",
                entityType = "transaction",
                entityId = t.id,
                title = "Undo added ${t.category}",
                afterJson = undoJson.encodeToString(t),
                projectId = t.projectId,
            )
            sync { setTransaction(t) }
        }
        Analytics.transactionAdded(type = transaction.type, category = transaction.category)
        // Sync AFTER withTransaction commits so we push the updated balance
        affectedAccounts.forEach { syncAccountByName(it) }
    }
    suspend fun editTransaction(old: Transaction, newRaw: Transaction) {
        if (old.isGeneratedJournalEntry()) return
        // C03a Stage 2: scrub forbidden literal categories from the new
        // value before applying the edit. `old` is read-only — its
        // category is just used for the old-side Payment-row lookup and
        // doesn't get re-written, so no sanitization needed there.
        // C03b Stage #1a: resolve account-name strings into ids on the
        // new value so the edit refreshes the immutable mirror.
        val new = TransactionValidator.sanitize(newRaw).withResolvedAccountIds()
        requireOpenDate(old.date, old.projectId)
        requireOpenDate(new.date, new.projectId)
        db.withTransaction {
            // 3.2.72 — audit log for both halves of the edit (reverse + apply).
            val revTag = app.fynlo.logic.BalanceAuditLog.Source.EDIT_TXN_REVERSE
            val appTag = app.fynlo.logic.BalanceAuditLog.Source.EDIT_TXN_APPLY
            val oldDesc = old.desc.take(28)
            val newDesc = new.desc.take(28)

            // 1. Reverse old transaction effect (id-keyed via Stage #1b-1).
            when (old.type.lowercase()) {
                "expense"  -> {
                    applyAccountDelta(old.fromAcctId, old.fromAcct,  old.amount)
                    app.fynlo.logic.BalanceAuditLog.record(revTag, old.fromAcct,  old.amount, "Reverse old expense \"$oldDesc\"")
                }
                "income"   -> {
                    applyAccountDelta(old.toAcctId, old.toAcct,   -old.amount)
                    app.fynlo.logic.BalanceAuditLog.record(revTag, old.toAcct,   -old.amount, "Reverse old income \"$oldDesc\"")
                }
                "transfer" -> {
                    applyAccountDelta(old.fromAcctId, old.fromAcct,  old.amount)
                    applyAccountDelta(old.toAcctId,   old.toAcct,   -old.amount)
                    app.fynlo.logic.BalanceAuditLog.record(revTag, old.fromAcct,  old.amount, "Reverse old transfer out \"$oldDesc\"")
                    app.fynlo.logic.BalanceAuditLog.record(revTag, old.toAcct,   -old.amount, "Reverse old transfer in \"$oldDesc\"")
                }
            }

            // 2. Apply new transaction effect (id-keyed via Stage #1b-1).
            when (new.type.lowercase()) {
                "expense"  -> {
                    applyAccountDelta(new.fromAcctId, new.fromAcct, -new.amount)
                    app.fynlo.logic.BalanceAuditLog.record(appTag, new.fromAcct, -new.amount, "Apply new expense \"$newDesc\"")
                }
                "income"   -> {
                    applyAccountDelta(new.toAcctId, new.toAcct,    new.amount)
                    app.fynlo.logic.BalanceAuditLog.record(appTag, new.toAcct,    new.amount, "Apply new income \"$newDesc\"")
                }
                "transfer" -> {
                    applyAccountDelta(new.fromAcctId, new.fromAcct, -new.amount)
                    applyAccountDelta(new.toAcctId,   new.toAcct,    new.amount)
                    app.fynlo.logic.BalanceAuditLog.record(appTag, new.fromAcct, -new.amount, "Apply new transfer out \"$newDesc\"")
                    app.fynlo.logic.BalanceAuditLog.record(appTag, new.toAcct,    new.amount, "Apply new transfer in \"$newDesc\"")
                }
            }

            // 3. Sync Borrower/Debt paid amounts via the payments tables
            //    (single source of truth per decisions/2026-05-26-c01-fix-strategy.md
            //    Stage 2). The old matching Payment / DebtPayment row is deleted
            //    when the edit moves away from a repayment category; a new row
            //    is inserted when the edit moves toward a repayment category.
            //    `paid` / `paidPrincipal` / `paidInterest` are re-derived at the
            //    end via the rebuild queries — never mutated directly.
            val touchedBorrowers = mutableSetOf<String>()
            val touchedDebts     = mutableSetOf<String>()

            if (old.category == "Loan Repayment" && old.ref.isNotBlank()) {
                val matching = dao.getPaymentsForLoanOnce(old.ref)
                    .filter { it.amount == old.amount && it.date == old.date }
                    .maxByOrNull { it.updatedAt }
                if (matching != null) {
                    dao.deletePayment(matching)
                    tombstoneRemoteDoc("payments", matching.id)
                    sync { deletePayment(matching.id) }
                }
                touchedBorrowers += old.ref
            }
            if (new.category == "Loan Repayment" && new.ref.isNotBlank()) {
                val borrower = dao.getBorrowerById(new.ref)
                if (borrower != null) {
                    val now = System.currentTimeMillis()
                    val p = Payment(
                        id        = app.fynlo.logic.Ids.newId(),
                        loanId    = new.ref,
                        name      = borrower.name,
                        date      = new.date,
                        type      = "Both",
                        amount    = new.amount,
                        principal = 0.0,
                        interest  = 0.0,
                        mode      = "",
                        notes     = new.notes,
                        projectId = new.projectId,
                        updatedAt = now,
                        createdAt = now,
                    )
                    dao.insertPayment(p)
                    sync { setPayment(p) }
                }
                touchedBorrowers += new.ref
            }
            if (old.category == "Debt Repayment" && old.ref.isNotBlank()) {
                val matching = dao.getDebtPaymentsForDebtOnce(old.ref)
                    .filter { it.amount == old.amount && it.date == old.date }
                    .maxByOrNull { it.updatedAt }
                if (matching != null) {
                    dao.deleteDebtPayment(matching)
                    tombstoneRemoteDoc("debt_payments", matching.id)
                    sync { deleteDebtPayment(matching.id) }
                }
                touchedDebts += old.ref
            }
            if (new.category == "Debt Repayment" && new.ref.isNotBlank()) {
                val debt = dao.getDebtById(new.ref)
                if (debt != null) {
                    val now = System.currentTimeMillis()
                    val p = DebtPayment(
                        id        = app.fynlo.logic.Ids.newId(),
                        debtId    = new.ref,
                        name      = debt.name,
                        date      = new.date,
                        type      = "Both",
                        amount    = new.amount,
                        principal = 0.0,
                        interest  = 0.0,
                        mode      = "",
                        notes     = new.notes,
                        projectId = new.projectId,
                        updatedAt = now,
                        createdAt = now,
                    )
                    dao.insertDebtPayment(p)
                    sync { setDebtPayment(p) }
                }
                touchedDebts += new.ref
            }
            if (touchedBorrowers.isNotEmpty()) {
                dao.rebuildBorrowerPaidFromPayments()
                touchedBorrowers.forEach { id ->
                    dao.getBorrowerById(id)?.let { b -> sync { setBorrower(b) } }
                }
            }
            if (touchedDebts.isNotEmpty()) {
                dao.rebuildDebtPaidFromDebtPayments()
                touchedDebts.forEach { id ->
                    dao.getDebtById(id)?.let { d -> sync { setDebt(d) } }
                }
            }

            dao.insertTransaction(new)
            recordAudit(
                action = "EDIT",
                entityType = "transaction",
                entityId = new.id,
                title = "Transaction edited: ${new.category}",
                beforeValue = old.auditSummary(),
                afterValue = new.auditSummary(),
                amountDelta = when (new.type.lowercase()) {
                    "expense" -> -new.amount
                    "income" -> new.amount
                    else -> 0.0
                } - when (old.type.lowercase()) {
                    "expense" -> -old.amount
                    "income" -> old.amount
                    else -> 0.0
                },
                accountName = listOf(new.fromAcct, new.toAcct).filter { it.isNotBlank() }.joinToString(" -> "),
                projectId = new.projectId,
            )
            recordUndo(
                action = "EDIT",
                entityType = "transaction",
                entityId = new.id,
                title = "Undo edited ${new.category}",
                beforeJson = undoJson.encodeToString(old),
                afterJson = undoJson.encodeToString(new),
                projectId = new.projectId,
            )
            sync { setTransaction(new) }
        }
        // Sync affected accounts
        val affected = mutableSetOf<String>()
        if (old.fromAcct.isNotBlank()) affected.add(old.fromAcct)
        if (old.toAcct.isNotBlank())   affected.add(old.toAcct)
        if (new.fromAcct.isNotBlank()) affected.add(new.fromAcct)
        if (new.toAcct.isNotBlank())   affected.add(new.toAcct)
        affected.forEach { syncAccountByName(it) }
    }

    suspend fun deleteTransaction(transaction: Transaction) {
        requireOpenDate(transaction.date, transaction.projectId)
        var deleted: Transaction? = null
        db.withTransaction {
            val current = dao.getTransactionById(transaction.id) ?: return@withTransaction
            if (current.isGeneratedJournalEntry()) return@withTransaction
            // 3.2.72 — audit-log every reversal so a delete shows up in the
            // diagnostic timeline.
            val delTag = app.fynlo.logic.BalanceAuditLog.Source.DELETE_TXN
            val delNote = "Delete ${current.type.lowercase()} \"${current.desc.take(28)}\""

            // Guard: only reverse balance if account name is non-blank.
            // C03b Stage #1b-1: id-keyed (with name fallback for orphans).
            when (current.type.lowercase()) {
                "expense"  -> if (current.fromAcct.isNotBlank()) {
                    applyAccountDelta(current.fromAcctId, current.fromAcct,  current.amount)
                    app.fynlo.logic.BalanceAuditLog.record(delTag, current.fromAcct,  current.amount, delNote)
                }
                "income"   -> if (current.toAcct.isNotBlank()) {
                    applyAccountDelta(current.toAcctId, current.toAcct,   -current.amount)
                    app.fynlo.logic.BalanceAuditLog.record(delTag, current.toAcct,   -current.amount, delNote)
                }
                "transfer" -> {
                    if (current.fromAcct.isNotBlank()) {
                        applyAccountDelta(current.fromAcctId, current.fromAcct,  current.amount)
                        app.fynlo.logic.BalanceAuditLog.record(delTag, current.fromAcct,  current.amount, "$delNote (out)")
                    }
                    if (current.toAcct.isNotBlank()) {
                        applyAccountDelta(current.toAcctId, current.toAcct,   -current.amount)
                        app.fynlo.logic.BalanceAuditLog.record(delTag, current.toAcct,   -current.amount, "$delNote (in)")
                    }
                }
            }
            // Handle payment reversals if transaction belongs to a loan/debt.
            // IMPORTANT: also delete the Payment record from payments table so that
            // rebuildBorrowerPaidFromPayments() recalculates correctly on next startup.
            if (current.category == "Loan Repayment" && current.ref.isNotBlank()) {
                // Find and delete the matching payment record (same loanId, amount, date).
                // If no matching Payment exists, `paid` is left as-is to preserve
                // the invariant paid == SUM(payments). (Pre-C01 this branch
                // reversed `paid` directly via updateBorrowerPaidAmount, which
                // broke the invariant — see decisions/2026-05-26-c01-fix-strategy.md
                // Stage 2.)
                val matchingPayment = dao.getPaymentsForLoanOnce(current.ref)
                    .filter { it.amount == current.amount && it.date == current.date }
                    .maxByOrNull { it.updatedAt }
                if (matchingPayment != null) {
                    dao.deletePayment(matchingPayment)
                    tombstoneRemoteDoc("payments", matchingPayment.id)
                    sync { deletePayment(matchingPayment.id) }
                }
                dao.rebuildBorrowerPaidFromPayments()
                val b = dao.getBorrowerById(current.ref)
                sync { b?.let { setBorrower(it) } }
            } else if (current.category == "Debt Repayment" && current.ref.isNotBlank()) {
                val matchingPayment = dao.getDebtPaymentsForDebtOnce(current.ref)
                    .filter { it.amount == current.amount && it.date == current.date }
                    .maxByOrNull { it.updatedAt }
                if (matchingPayment != null) {
                    dao.deleteDebtPayment(matchingPayment)
                    tombstoneRemoteDoc("debt_payments", matchingPayment.id)
                    sync { deleteDebtPayment(matchingPayment.id) }
                }
                dao.rebuildDebtPaidFromDebtPayments()
                val d = dao.getDebtById(current.ref)
                sync { d?.let { setDebt(it) } }
            }

            dao.deleteTransaction(current)
            tombstoneRemoteDoc("transactions", current.id)
            recordAudit(
                action = "DELETE",
                entityType = "transaction",
                entityId = current.id,
                title = "Transaction deleted: ${current.category}",
                beforeValue = current.auditSummary(),
                amountDelta = when (current.type.lowercase()) {
                    "expense" -> current.amount
                    "income" -> -current.amount
                    else -> 0.0
                },
                accountName = listOf(current.fromAcct, current.toAcct).filter { it.isNotBlank() }.joinToString(" -> "),
                projectId = current.projectId,
            )
            recordUndo(
                action = "DELETE",
                entityType = "transaction",
                entityId = current.id,
                title = "Undo deleted ${current.category}",
                beforeJson = undoJson.encodeToString(current),
                projectId = current.projectId,
            )
            deleted = current
        }
        val current = deleted ?: return
        // Sync reversed account balances AFTER withTransaction commits
        when (current.type.lowercase()) {
            "expense"  -> if (current.fromAcct.isNotBlank()) syncAccountByName(current.fromAcct)
            "income"   -> if (current.toAcct.isNotBlank())   syncAccountByName(current.toAcct)
            "transfer" -> {
                if (current.fromAcct.isNotBlank()) syncAccountByName(current.fromAcct)
                if (current.toAcct.isNotBlank())   syncAccountByName(current.toAcct)
            }
        }
        sync { deleteTransaction(current.id) }
    }
    suspend fun insertBorrower(borrower: Borrower) = insertBorrowerWithSource(borrower, "Personal Cash")

    suspend fun updateBorrower(borrower: Borrower) {
        updateBorrowerWithSource(borrower, borrower.sourceAccount)
    }

    suspend fun updateBorrowerWithSource(borrower: Borrower, sourceAccount: String) {
        requireOpenDate(borrower.date, borrower.projectId)
        val before = dao.getBorrowerById(borrower.id)
        val now = System.currentTimeMillis()
        var oldSource = ""
        var newSource = ""
        var updatedBorrower: Borrower? = null
        var updatedFundingTxn: Transaction? = null
        db.withTransaction {
            val fundingTxn = dao.getTransactionsByRef(borrower.id)
                .firstOrNull { it.category == "Lending" && it.type.equals("Expense", ignoreCase = true) }
            oldSource = fundingTxn?.fromAcct ?: before?.sourceAccount.orEmpty()
            newSource = sourceAccount.ifBlank { oldSource.ifBlank { before?.sourceAccount.orEmpty() } }
            val newSourceId = dao.getAccountByName(newSource)?.id ?: ""
            val b = borrower.copy(sourceAccount = newSource, updatedAt = now)
            dao.insertBorrower(b)
            updatedBorrower = b
            if (before != null && fundingTxn != null) {
                applyAccountDelta(fundingTxn.fromAcctId, fundingTxn.fromAcct, fundingTxn.amount)
                applyAccountDelta(newSourceId, newSource, -b.amount)
                val fundingUpdate = fundingTxn.copy(
                    date = b.date,
                    amount = b.amount,
                    fromAcct = newSource,
                    fromAcctId = newSourceId,
                    category = "Lending",
                    desc = "Lent to ${b.name}",
                    notes = b.notes,
                    projectId = b.projectId,
                    updatedAt = now,
                )
                updatedFundingTxn = fundingUpdate
                dao.insertTransaction(fundingUpdate)
            }
            recordAudit(
                action = "EDIT",
                entityType = "loan",
                entityId = b.id,
                title = "Loan edited: ${b.name}",
                beforeValue = before?.let { "${it.name}:${it.amount}:paid=${it.paid}:source=${it.sourceAccount}:status=${it.status}" } ?: "",
                afterValue = "${b.name}:${b.amount}:paid=${b.paid}:source=${b.sourceAccount}:status=${b.status}",
                amountDelta = b.amount - (before?.amount ?: b.amount),
                accountName = b.sourceAccount,
                projectId = b.projectId,
            )
        }
        sync {
            updatedBorrower?.let { setBorrower(it) }
            updatedFundingTxn?.let { setTransaction(it) }
        }
        if (oldSource.isNotBlank()) syncAccountByName(oldSource)
        if (newSource.isNotBlank() && newSource != oldSource) syncAccountByName(newSource)
    }

    suspend fun updateDebt(debt: Debt) {
        val existingDestination = dao.getTransactionsByRef(debt.id)
            .firstOrNull { it.category == "Debt Received" && it.type.equals("Income", ignoreCase = true) }
            ?.toAcct.orEmpty()
        updateDebtWithDestination(debt, existingDestination)
    }

    suspend fun updateDebtWithDestination(debt: Debt, requestedDestinationAccount: String) {
        requireOpenDate(debt.date, debt.projectId)
        var destinationAccount = ""
        var oldDestinationAccount = ""
        var updatedFundingTxn: Transaction? = null
        val before = dao.getDebtById(debt.id)
        val now = System.currentTimeMillis()
        val d = debt.copy(updatedAt = now)
        db.withTransaction {
            val fundingTxn = dao.getTransactionsByRef(d.id)
                .firstOrNull { it.category == "Debt Received" && it.type.equals("Income", ignoreCase = true) }
            oldDestinationAccount = fundingTxn?.toAcct.orEmpty()
            destinationAccount = requestedDestinationAccount.ifBlank { oldDestinationAccount }
            val destinationAccountId = dao.getAccountByName(destinationAccount)?.id ?: ""
            dao.insertDebt(d)
            if (fundingTxn != null) {
                if (before != null) {
                    applyAccountDelta(fundingTxn.toAcctId, fundingTxn.toAcct, -fundingTxn.amount)
                    applyAccountDelta(destinationAccountId, destinationAccount, d.amount)
                }
                val fundingUpdate = fundingTxn.copy(
                    date = d.date,
                    amount = d.amount,
                    toAcct = destinationAccount,
                    toAcctId = destinationAccountId,
                    category = "Debt Received",
                    desc = "Loan received from ${d.name}",
                    notes = d.notes,
                    projectId = d.projectId,
                    updatedAt = now,
                )
                updatedFundingTxn = fundingUpdate
                dao.insertTransaction(fundingUpdate)
            }
            recordAudit(
                action = "EDIT",
                entityType = "debt",
                entityId = d.id,
                title = "Debt edited: ${d.name}",
                beforeValue = before?.let { "${it.name}:${it.amount}:paid=${it.paid}:status=${it.status}" } ?: "",
                afterValue = "${d.name}:${d.amount}:paid=${d.paid}:status=${d.status}:to=$destinationAccount",
                amountDelta = d.amount - (before?.amount ?: d.amount),
                accountName = destinationAccount,
                projectId = d.projectId,
            )
        }
        sync {
            setDebt(d)
            updatedFundingTxn?.let { setTransaction(it) }
        }
        if (oldDestinationAccount.isNotBlank()) syncAccountByName(oldDestinationAccount)
        if (destinationAccount.isNotBlank()) syncAccountByName(destinationAccount)
    }
    /**
     * C03b Stage #3 (3.2.90) — find a Person by phone, or create one and
     * return its id. The dedup spine for borrowers/debts: every loan to
     * the same phone number links to ONE Person. Empty-phone callers
     * get `""` back (can't dedup safely on name alone — the user has to
     * link manually via the People screen).
     *
     * Side effect: when creating, the new Person row is also pushed to
     * Firestore via the same sync path borrowers/debts use.
     */
    private suspend fun findOrCreatePersonId(name: String, phone: String, projectId: String): String {
        if (phone.isBlank()) return ""
        dao.getPersonByPhone(phone)?.let { return it.id }
        val now = System.currentTimeMillis()
        val newPerson = Person(
            id        = app.fynlo.logic.Ids.newId(),
            name      = name,
            phone     = phone,
            type      = "Individual",
            notes     = "",
            projectId = projectId,
            updatedAt = now,
            createdAt = now,
        )
        dao.insertPerson(newPerson)
        sync { setPerson(newPerson) }
        return newPerson.id
    }

    suspend fun insertBorrowerWithSource(borrower: Borrower, sourceAccount: String, projectId: String = borrower.projectId) {
        requireOpenDate(borrower.date, projectId)
        db.withTransaction {
            if (dao.getBorrowerById(borrower.id) != null) return@withTransaction
            val now = System.currentTimeMillis()
            // C03b Stage #3: resolve peopleId at write time so subsequent
            // loans to the same phone aggregate under one Person record.
            val resolvedPeopleId =
                if (borrower.peopleId.isNotEmpty()) borrower.peopleId
                else findOrCreatePersonId(borrower.name, borrower.phone, projectId)
            val b = borrower.copy(
                projectId = projectId,
                peopleId  = resolvedPeopleId,
                updatedAt = now,
                createdAt = if (borrower.createdAt == 0L) now else borrower.createdAt,
            )
            val bWithSource = b.copy(sourceAccount = sourceAccount)
            dao.insertBorrower(bWithSource)
            val sourceAccountId = dao.getAccountByName(sourceAccount)?.id ?: ""
            applyAccountDelta(sourceAccountId, sourceAccount, -borrower.amount)
            val t = Transaction(app.fynlo.logic.Ids.newId(), borrower.date, "Expense", borrower.amount, fromAcct = sourceAccount, fromAcctId = sourceAccountId, category = "Lending", desc = "Lent to ${borrower.name}", ref = borrower.id, notes = borrower.notes, projectId = projectId, updatedAt = now, createdAt = now)
            dao.insertTransaction(t)
            recordAudit(
                action = "CREATE",
                entityType = "loan",
                entityId = bWithSource.id,
                title = "Loan created: ${bWithSource.name}",
                afterValue = "lent=${bWithSource.amount}:from=$sourceAccount:txn=${t.id}",
                amountDelta = -bWithSource.amount,
                accountName = sourceAccount,
                projectId = projectId,
            )
            recordUndo(
                action = "CREATE",
                entityType = "loan",
                entityId = bWithSource.id,
                title = "Create loan: ${bWithSource.name}",
                afterJson = undoJson.encodeToString(LoanUndoBundle(borrower = bWithSource, transactions = listOf(t))),
                projectId = projectId,
            )
            sync { setBorrower(bWithSource); setTransaction(t) }
        }
        Analytics.loanCreated(hasInterest = borrower.rate > 0.0)
        syncAccountByName(sourceAccount)
    }
    suspend fun deleteBorrower(borrower: Borrower) {
        requireOpenDate(borrower.date, borrower.projectId)
        // Find linked transactions — try by ref first, fall back to desc for legacy records
        val byRef  = dao.getTransactionsByRef(borrower.id)
        val byDesc = dao.getTransactionsByDesc("Lent to ${borrower.name}")
            .filter { it.ref.isBlank() && it.category == "Lending" }
        val linkedTxns = (byRef + byDesc).distinctBy { it.id }
        var linkedPayments = emptyList<Payment>()

        db.withTransaction {
            linkedTxns.forEach { txn ->
                // C03b Stage #1b-1: id-keyed reversal (orphan rows fall back to name).
                when (txn.type.lowercase()) {
                    "expense"  -> applyAccountDelta(txn.fromAcctId, txn.fromAcct,  txn.amount)
                    "income"   -> applyAccountDelta(txn.toAcctId,   txn.toAcct,   -txn.amount)
                    "transfer" -> {
                        applyAccountDelta(txn.fromAcctId, txn.fromAcct,  txn.amount)
                        applyAccountDelta(txn.toAcctId,   txn.toAcct,   -txn.amount)
                    }
                }
                dao.deleteTransaction(txn)
                tombstoneRemoteDoc("transactions", txn.id)
            }
            linkedPayments = dao.getPaymentsForLoanOnce(borrower.id)
            linkedPayments.forEach { p ->
                dao.deletePayment(p)
                tombstoneRemoteDoc("payments", p.id)
            }
            recordUndo(
                action = "DELETE",
                entityType = "loan",
                entityId = borrower.id,
                title = "Delete loan: ${borrower.name}",
                beforeJson = undoJson.encodeToString(
                    LoanUndoBundle(
                        borrower = borrower,
                        transactions = linkedTxns,
                        payments = linkedPayments,
                    )
                ),
                projectId = borrower.projectId,
            )
            dao.deleteBorrower(borrower)
            tombstoneRemoteDoc("borrowers", borrower.id)
            recordAudit(
                action = "DELETE",
                entityType = "loan",
                entityId = borrower.id,
                title = "Loan deleted: ${borrower.name}",
                beforeValue = "lent=${borrower.amount}:paid=${borrower.paid}:source=${borrower.sourceAccount}",
                amountDelta = borrower.amount,
                accountName = borrower.sourceAccount,
                projectId = borrower.projectId,
            )
        }
        linkedTxns.forEach { sync { deleteTransaction(it.id) } }
        linkedPayments.forEach { sync { deletePayment(it.id) } }
        sync { deleteBorrower(borrower.id) }
        linkedTxns.map { it.fromAcct }.filter { it.isNotBlank() }.distinct().forEach { syncAccountByName(it) }
        linkedTxns.map { it.toAcct   }.filter { it.isNotBlank() }.distinct().forEach { syncAccountByName(it) }
    }
    /**
     * Recalculates all account balances from scratch by summing every transaction.
     * Fixes balances that got out of sync due to failed/partial deletes.
     */
    // ─── Fix double-counted paid field (safe to run on every startup) ────────
    suspend fun fixPaidDoubleCount() {
        // Backfill sourceAccount for borrowers that don't have it set yet
        dao.backfillBorrowerSourceAccount()
        // Rebuild paid, paidPrincipal, paidInterest from the payments table (source of truth).
        // This fixes any corruption from previous migrations that used the wrong
        // denormalized fields as the source.
        dao.rebuildBorrowerPaidFromPayments()
        dao.rebuildDebtPaidFromDebtPayments()
    }

    suspend fun repairDeletedAuditResidue(): Int {
        val latestDeleteByEntity = dao.getAllAuditEventsOnce()
            .filter { it.action == "DELETE" && it.entityId.isNotBlank() }
            .groupBy { it.entityType to it.entityId }
            .mapValues { (_, events) -> events.maxOf { it.timestamp } }

        val deletedLoanIds = latestDeleteByEntity.keys
            .filter { it.first == "loan" }
            .map { it.second }
            .toSet()
        val deletedDebtIds = latestDeleteByEntity.keys
            .filter { it.first == "debt" }
            .map { it.second }
            .toSet()
        val deletedTransactionIds = latestDeleteByEntity.keys
            .filter { it.first == "transaction" }
            .map { it.second }
            .toSet()

        val remoteTransactions = mutableSetOf<String>()
        val remotePayments = mutableSetOf<String>()
        val remoteDebtPayments = mutableSetOf<String>()
        val remoteBorrowers = mutableSetOf<String>()
        val remoteDebts = mutableSetOf<String>()
        var repaired = 0

        db.withTransaction {
            deletedTransactionIds.forEach { id ->
                val deletedAt = latestDeleteByEntity["transaction" to id] ?: return@forEach
                val txn = dao.getTransactionById(id)
                if (txn != null && txn.updatedAt <= deletedAt) {
                    dao.deleteTransaction(txn)
                    repaired++
                }
                tombstoneRemoteDoc("transactions", id)
                remoteTransactions += id
            }

            deletedLoanIds.forEach { id ->
                val deletedAt = latestDeleteByEntity["loan" to id] ?: return@forEach
                dao.getTransactionsByRef(id).forEach { txn ->
                    if (txn.updatedAt <= deletedAt) {
                        dao.deleteTransaction(txn)
                        tombstoneRemoteDoc("transactions", txn.id)
                        remoteTransactions += txn.id
                        repaired++
                    }
                }
                dao.getPaymentsForLoanOnce(id).forEach { payment ->
                    if (payment.updatedAt <= deletedAt) {
                        dao.deletePayment(payment)
                        tombstoneRemoteDoc("payments", payment.id)
                        remotePayments += payment.id
                        repaired++
                    }
                }
                dao.getBorrowerById(id)?.let { borrower ->
                    if (borrower.updatedAt <= deletedAt) {
                        dao.deleteBorrower(borrower)
                        repaired++
                    }
                }
                tombstoneRemoteDoc("borrowers", id)
                remoteBorrowers += id
            }

            deletedDebtIds.forEach { id ->
                val deletedAt = latestDeleteByEntity["debt" to id] ?: return@forEach
                dao.getTransactionsByRef(id).forEach { txn ->
                    if (txn.updatedAt <= deletedAt) {
                        dao.deleteTransaction(txn)
                        tombstoneRemoteDoc("transactions", txn.id)
                        remoteTransactions += txn.id
                        repaired++
                    }
                }
                dao.getDebtPaymentsForDebtOnce(id).forEach { payment ->
                    if (payment.updatedAt <= deletedAt) {
                        dao.deleteDebtPayment(payment)
                        tombstoneRemoteDoc("debt_payments", payment.id)
                        remoteDebtPayments += payment.id
                        repaired++
                    }
                }
                dao.getDebtById(id)?.let { debt ->
                    if (debt.updatedAt <= deletedAt) {
                        dao.deleteDebt(debt)
                        repaired++
                    }
                }
                tombstoneRemoteDoc("debts", id)
                remoteDebts += id
            }

            dao.rebuildBorrowerPaidFromPayments()
            dao.rebuildDebtPaidFromDebtPayments()
        }

        remoteTransactions.forEach { sync { deleteTransaction(it) } }
        remotePayments.forEach { sync { deletePayment(it) } }
        remoteDebtPayments.forEach { sync { deleteDebtPayment(it) } }
        remoteBorrowers.forEach { sync { deleteBorrower(it) } }
        remoteDebts.forEach { sync { deleteDebt(it) } }
        return repaired
    }

    /**
     * Legacy debt-funded investments used to create an Investment/Transfer
     * transaction for traceability. That is fine when it is only a journal
     * row, but any row whose from/to values happen to be real accounts can
     * distort cash balances during later edits or manual recalculation.
     *
     * Convert those old rows into explicit journal-only trace rows and reverse
     * the account movement once. The conversion is one-way: after type becomes
     * Info and account columns are blank, this method will not match it again.
     */
    suspend fun repairDebtFundedInvestmentTransferTraces(): Int {
        val accounts = dao.getAllAccountsList()
        val accountNames = accounts.map { it.name }.toSet()
        val accountIds = accounts.map { it.id }.toSet()
        val investments = dao.getAllInvestments().first()
            .filter { it.sourceType in setOf("existing_debt", "new_loan") && it.linkedDebtId.isNotBlank() }
        val transactions = dao.getAllTransactionsList()
        val remoteTransactions = mutableListOf<Transaction>()
        val touchedAccounts = mutableSetOf<String>()
        var repaired = 0

        db.withTransaction {
            investments.forEach { investment ->
                val candidates = transactions.filter { txn ->
                    txn.category.equals("Investment", ignoreCase = true) &&
                        txn.type.equals("Transfer", ignoreCase = true) &&
                        (txn.ref == investment.id ||
                            (txn.desc.contains(investment.name, ignoreCase = true) &&
                                (investment.fundingSource.isBlank() ||
                                    txn.desc.contains(investment.fundingSource, ignoreCase = true))))
                }

                candidates.forEach { txn ->
                    if (txn.fromAcct in accountNames || txn.fromAcctId in accountIds) {
                        applyAccountDelta(txn.fromAcctId, txn.fromAcct, txn.amount)
                        touchedAccounts += txn.fromAcct.takeIf { it.isNotBlank() }
                            ?: accounts.firstOrNull { it.id == txn.fromAcctId }?.name.orEmpty()
                    }
                    if (txn.toAcct in accountNames || txn.toAcctId in accountIds) {
                        applyAccountDelta(txn.toAcctId, txn.toAcct, -txn.amount)
                        touchedAccounts += txn.toAcct.takeIf { it.isNotBlank() }
                            ?: accounts.firstOrNull { it.id == txn.toAcctId }?.name.orEmpty()
                    }
                    val repairedTags = (txn.tags.split(",") + "journal_only")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .joinToString(",")
                    val repairedTxn = txn.copy(
                        type = "Info",
                        fromAcct = "",
                        toAcct = "",
                        fromAcctId = "",
                        toAcctId = "",
                        ref = investment.id,
                        tags = repairedTags,
                        updatedAt = System.currentTimeMillis(),
                    )
                    dao.insertTransaction(repairedTxn)
                    remoteTransactions += repairedTxn
                    recordAudit(
                        action = "REPAIR",
                        entityType = "transaction",
                        entityId = repairedTxn.id,
                        title = "Debt-funded investment trace neutralized: ${investment.name}",
                        beforeValue = txn.auditSummary(),
                        afterValue = repairedTxn.auditSummary(),
                        amountDelta = 0.0,
                        accountName = listOf(txn.fromAcct, txn.toAcct).filter { it.isNotBlank() }.joinToString(" -> "),
                        projectId = repairedTxn.projectId,
                        reason = "Debt-funded investment trace must not move account balances",
                    )
                    repaired++
                }
            }
        }

        remoteTransactions.forEach { txn -> sync { setTransaction(txn) } }
        touchedAccounts.filter { it.isNotBlank() }.forEach { syncAccountByName(it) }
        return repaired
    }

    /**
     * Debt-funded investments are journal trace rows: they explain which
     * liability funded an asset, but they must not move account balances.
     * Some legacy/edit paths left the right journal amount attached to the
     * wrong investment id, which made the money trail unclear even when the
     * account totals were correct.
     */
    suspend fun repairDebtFundedInvestmentJournalTraceRefs(): Int {
        val investments = dao.getAllInvestments().first()
            .filter { it.sourceType in setOf("existing_debt", "new_loan") && it.linkedDebtId.isNotBlank() }
        val remoteTransactions = mutableListOf<Transaction>()
        var repaired = 0

        db.withTransaction {
            val txCache = dao.getAllTransactionsList().toMutableList()

            fun replaceCached(updated: Transaction) {
                val index = txCache.indexOfFirst { it.id == updated.id }
                if (index >= 0) txCache[index] = updated else txCache += updated
            }

            fun hasExactTrace(investment: Investment): Boolean =
                txCache.any { txn ->
                    txn.ref == investment.id &&
                        isInvestmentJournalTrace(txn) &&
                        kotlin.math.abs(txn.amount - investment.invested) <= 0.005
                }

            investments.forEach { investment ->
                val linked = txCache.filter { txn ->
                    txn.ref == investment.id && isInvestmentJournalTrace(txn)
                }
                val wrongLinked = linked.filter { txn ->
                    kotlin.math.abs(txn.amount - investment.invested) > 0.005
                }

                wrongLinked.forEach { txn ->
                    val target = investments.firstOrNull { other ->
                        other.id != investment.id &&
                            !hasExactTrace(other) &&
                            other.name.equals(investment.name, ignoreCase = true) &&
                            other.fundingSource.equals(investment.fundingSource, ignoreCase = true) &&
                            kotlin.math.abs(other.invested - txn.amount) <= 0.005
                    }
                    if (target != null) {
                        val relinked = txn.copy(
                            ref = target.id,
                            updatedAt = System.currentTimeMillis(),
                        )
                        dao.insertTransaction(relinked)
                        replaceCached(relinked)
                        remoteTransactions += relinked
                        recordAudit(
                            action = "REPAIR",
                            entityType = "transaction",
                            entityId = relinked.id,
                            title = "Investment trace relinked: ${target.name}",
                            beforeValue = txn.auditSummary(),
                            afterValue = relinked.auditSummary(),
                            amountDelta = 0.0,
                            accountName = target.fundingSource,
                            projectId = relinked.projectId,
                            reason = "Debt-funded investment journal trace must point to the matching investment row",
                        )
                        repaired++
                    }
                }

                if (hasExactTrace(investment)) return@forEach

                val candidate = txCache.firstOrNull { txn ->
                    txn.ref != investment.id &&
                        isInvestmentJournalTrace(txn) &&
                        kotlin.math.abs(txn.amount - investment.invested) <= 0.005 &&
                        txn.desc.contains(investment.name, ignoreCase = true) &&
                        (investment.fundingSource.isBlank() ||
                            txn.desc.contains(investment.fundingSource, ignoreCase = true))
                }

                if (candidate != null) {
                    val relinked = candidate.copy(
                        ref = investment.id,
                        updatedAt = System.currentTimeMillis(),
                    )
                    dao.insertTransaction(relinked)
                    replaceCached(relinked)
                    remoteTransactions += relinked
                    recordAudit(
                        action = "REPAIR",
                        entityType = "transaction",
                        entityId = relinked.id,
                        title = "Investment trace relinked: ${investment.name}",
                        beforeValue = candidate.auditSummary(),
                        afterValue = relinked.auditSummary(),
                        amountDelta = 0.0,
                        accountName = investment.fundingSource,
                        projectId = relinked.projectId,
                        reason = "Debt-funded investment journal trace must point to the matching investment row",
                    )
                    repaired++
                } else {
                    val created = Transaction(
                        id = app.fynlo.logic.Ids.newId(),
                        date = investment.date,
                        type = "Info",
                        amount = investment.invested,
                        category = "Investment",
                        desc = "Invested in ${investment.name} using ${investment.fundingSource} loan funds",
                        ref = investment.id,
                        notes = investment.notes,
                        tags = "journal_only",
                        projectId = investment.projectId,
                        updatedAt = System.currentTimeMillis(),
                        createdAt = System.currentTimeMillis(),
                    )
                    dao.insertTransaction(created)
                    replaceCached(created)
                    remoteTransactions += created
                    recordAudit(
                        action = "REPAIR",
                        entityType = "transaction",
                        entityId = created.id,
                        title = "Investment trace created: ${investment.name}",
                        afterValue = created.auditSummary(),
                        amountDelta = 0.0,
                        accountName = investment.fundingSource,
                        projectId = created.projectId,
                        reason = "Debt-funded investment must have an exact journal-only source trace",
                    )
                    repaired++
                }
            }
        }

        remoteTransactions.forEach { txn -> sync { setTransaction(txn) } }
        return repaired
    }

    /**
     * Some legacy debt edits changed the Debt principal but left the linked
     * "Debt Received" transaction at the old amount. That makes the debt page
     * and the destination account disagree. The debt row is the source of
     * truth; this repair updates the linked receipt and applies only the
     * missing delta to the original destination account.
     */
    suspend fun repairDebtReceiptAmountMismatches(): Int {
        val debts = dao.getAllDebts().first()
        val transactions = dao.getAllTransactionsList()
        val remoteTransactions = mutableListOf<Transaction>()
        val touchedAccounts = mutableSetOf<String>()
        var repaired = 0

        db.withTransaction {
            debts.forEach { debt ->
                val receipt = transactions.firstOrNull { txn ->
                    txn.ref == debt.id && txn.category.equals("Debt Received", ignoreCase = true)
                } ?: return@forEach
                val delta = debt.amount - receipt.amount
                if (kotlin.math.abs(delta) <= 0.005) return@forEach

                val destinationId = receipt.toAcctId.ifBlank {
                    dao.getAccountByName(receipt.toAcct)?.id.orEmpty()
                }
                val destinationName = receipt.toAcct.ifBlank {
                    dao.getAccountById(destinationId)?.name.orEmpty()
                }
                applyAccountDelta(destinationId, destinationName, delta)
                val repairedReceipt = receipt.copy(
                    amount = debt.amount,
                    toAcct = destinationName,
                    toAcctId = destinationId,
                    updatedAt = System.currentTimeMillis(),
                )
                dao.insertTransaction(repairedReceipt)
                remoteTransactions += repairedReceipt
                if (destinationName.isNotBlank()) touchedAccounts += destinationName
                recordAudit(
                    action = "REPAIR",
                    entityType = "transaction",
                    entityId = repairedReceipt.id,
                    title = "Debt receipt amount repaired: ${debt.name}",
                    beforeValue = receipt.auditSummary(),
                    afterValue = repairedReceipt.auditSummary(),
                    amountDelta = delta,
                    accountName = destinationName,
                    projectId = repairedReceipt.projectId,
                    reason = "Debt principal and linked Debt Received transaction must match",
                )
                repaired++
            }
        }

        remoteTransactions.forEach { txn -> sync { setTransaction(txn) } }
        touchedAccounts.filter { it.isNotBlank() }.forEach { syncAccountByName(it) }
        return repaired
    }

    private fun isInvestmentJournalTrace(txn: Transaction): Boolean =
        txn.category.equals("Investment", ignoreCase = true) &&
            txn.type.equals("Info", ignoreCase = true) &&
            txn.tags.split(",").any { it.trim().equals("journal_only", ignoreCase = true) }

    /**
     * Runtime backfill for transactions restored after the schema migration.
     * It links legacy name-only rows to the current account id without moving
     * money or changing the transaction amount/type/category.
     */
    suspend fun repairTransactionAccountIds(): Int {
        val accounts = dao.getAllAccountsList()
        val idByName = accounts.associate { it.name to it.id }
        if (idByName.isEmpty()) return 0

        val repairedTransactions = dao.getAllTransactionsList().mapNotNull { txn ->
            val resolvedFrom = if (txn.fromAcctId.isBlank() && txn.fromAcct.isNotBlank()) {
                idByName[txn.fromAcct].orEmpty()
            } else {
                txn.fromAcctId
            }
            val resolvedTo = if (txn.toAcctId.isBlank() && txn.toAcct.isNotBlank()) {
                idByName[txn.toAcct].orEmpty()
            } else {
                txn.toAcctId
            }

            if (resolvedFrom == txn.fromAcctId && resolvedTo == txn.toAcctId) {
                null
            } else {
                txn.copy(
                    fromAcctId = resolvedFrom,
                    toAcctId = resolvedTo,
                    updatedAt = System.currentTimeMillis(),
                )
            }
        }
        if (repairedTransactions.isEmpty()) return 0

        db.withTransaction {
            repairedTransactions.forEach { dao.insertTransaction(it) }
        }
        repairedTransactions.forEach { txn -> sync { setTransaction(txn) } }
        return repairedTransactions.size
    }

    /**
     * Last-resort account reconciliation for stored balance drift. It uses the
     * account CREATE audit as the opening balance, then replays current
     * transaction rows. Rows marked Info or journal_only are ignored because
     * they are trace notes, not money movement.
     */
    suspend fun repairAccountBalanceDriftFromLedger(): Int {
        val accounts = dao.getAllAccountsList()
        val transactions = dao.getAllTransactionsList()
        val auditEvents = dao.getAllAuditEventsOnce()
        val repairedAccounts = mutableListOf<Account>()
        var repaired = 0

        db.withTransaction {
            accounts.forEach { account ->
                val openingAudit = auditEvents
                    .filter { event ->
                        event.action == "CREATE" &&
                            event.entityType == "account" &&
                            (event.entityId == account.id || event.accountName == account.name)
                    }
                    .minByOrNull { it.timestamp }
                    ?: return@forEach
                val expected = expectedBalanceFromLedger(
                    account = account,
                    openingBalance = openingAudit.amountDelta,
                    transactions = transactions,
                    auditEvents = auditEvents,
                )
                val delta = expected - account.balance
                if (kotlin.math.abs(delta) <= 0.005) return@forEach

                val repairedAccount = account.copy(
                    balance = expected,
                    updatedAt = System.currentTimeMillis(),
                )
                dao.insertAccount(repairedAccount)
                repairedAccounts += repairedAccount
                recordAudit(
                    action = "REPAIR",
                    entityType = "account",
                    entityId = account.id,
                    title = "Account balance reconciled: ${account.name}",
                    beforeValue = account.balance.toString(),
                    afterValue = expected.toString(),
                    amountDelta = delta,
                    accountName = account.name,
                    projectId = account.projectId,
                    reason = "Stored account balance must match opening balance plus transaction ledger",
                )
                repaired++
            }
        }

        repairedAccounts.forEach { account -> sync { setAccount(account) } }
        return repaired
    }

    private fun expectedBalanceFromLedger(
        account: Account,
        openingBalance: Double,
        transactions: List<Transaction>,
        auditEvents: List<AuditEvent>,
    ): Double {
        var balance = openingBalance + auditEvents
            .filter { event ->
                event.action == "EDIT" &&
                    event.entityType == "account" &&
                    event.title.startsWith("Account edited:") &&
                    (event.entityId == account.id || event.accountName == account.name)
            }
            .sumOf { it.amountDelta }
        transactions.forEach { txn ->
            if (txn.type.equals("Info", ignoreCase = true)) return@forEach
            if (txn.tags.split(",").any { it.trim().equals("journal_only", ignoreCase = true) }) return@forEach

            val fromMatches = accountMatches(account, txn.fromAcctId, txn.fromAcct)
            val toMatches = accountMatches(account, txn.toAcctId, txn.toAcct)
            when (txn.type.lowercase()) {
                "expense" -> if (fromMatches) balance -= txn.amount
                "income" -> if (toMatches) balance += txn.amount
                "transfer" -> {
                    if (fromMatches) balance -= txn.amount
                    if (toMatches) balance += txn.amount
                }
            }
        }
        return balance
    }

    private fun accountMatches(account: Account, id: String, name: String): Boolean =
        (id.isNotBlank() && id == account.id) || (name.isNotBlank() && name == account.name)

    suspend fun recalculateAllBalances() {
        // C01 fix — Sprint 1 (decisions/2026-05-26-c01-fix-strategy.md).
        // Derives `paid` / `paidPrincipal` / `paidInterest` from the
        // `payments` and `debt_payments` tables (the single source of truth).
        // … (unchanged comment block)
        //
        // 3.2.73 — wrap rebuilds with snapshot diff so the audit log captures
        // any borrower / debt rows whose `paid` total moved. A "no-op recalc"
        // (everything already in sync) records nothing; a recalc that fixes
        // real drift surfaces it per-row with the delta and old→new pair.
        dao.backfillBorrowerSourceAccount()
        val borrowersBefore = dao.getAllBorrowers().first().associate { it.id to (it.name to it.paid) }
        val debtsBefore     = dao.getAllDebts().first().associate     { it.id to (it.name to it.paid) }
        dao.rebuildBorrowerPaidFromPayments()
        dao.rebuildDebtPaidFromDebtPayments()
        val borrowersAfter = dao.getAllBorrowers().first().associate { it.id to it.paid }
        val debtsAfter     = dao.getAllDebts().first().associate     { it.id to it.paid }
        for ((id, namePaid) in borrowersBefore) {
            val (name, oldPaid) = namePaid
            val newPaid = borrowersAfter[id] ?: continue
            val delta = newPaid - oldPaid
            if (kotlin.math.abs(delta) > 0.005) {
                app.fynlo.logic.BalanceAuditLog.record(
                    source  = app.fynlo.logic.BalanceAuditLog.Source.RECALC_BORROWER_PAID,
                    account = "Loan: $name",
                    delta   = delta,
                    note    = "Recalc rebuilt borrower.paid from payments ($oldPaid → $newPaid)",
                )
                recordAudit(
                    action = "RECALC",
                    entityType = "loan",
                    entityId = id,
                    title = "Loan paid total rebuilt: $name",
                    beforeValue = oldPaid.toString(),
                    afterValue = newPaid.toString(),
                    amountDelta = delta,
                )
            }
        }
        for ((id, namePaid) in debtsBefore) {
            val (name, oldPaid) = namePaid
            val newPaid = debtsAfter[id] ?: continue
            val delta = newPaid - oldPaid
            if (kotlin.math.abs(delta) > 0.005) {
                app.fynlo.logic.BalanceAuditLog.record(
                    source  = app.fynlo.logic.BalanceAuditLog.Source.RECALC_DEBT_PAID,
                    account = "Debt: $name",
                    delta   = delta,
                    note    = "Recalc rebuilt debt.paid from debt_payments ($oldPaid → $newPaid)",
                )
                recordAudit(
                    action = "RECALC",
                    entityType = "debt",
                    entityId = id,
                    title = "Debt paid total rebuilt: $name",
                    beforeValue = oldPaid.toString(),
                    afterValue = newPaid.toString(),
                    amountDelta = delta,
                )
            }
        }
    }

    /** Directly set account balance for corrections by writing a balancing transaction. */
    suspend fun quickEditBalance(
        accountName: String,
        newBalance: Double,
        oldBalance: Double,
        accountId: String = "",
    ) {
        db.withTransaction {
            val diff = newBalance - oldBalance
            applyAccountDelta(accountId, accountName, diff)
            // 3.2.72 — manual balance edit audit entry.
            app.fynlo.logic.BalanceAuditLog.record(
                source  = app.fynlo.logic.BalanceAuditLog.Source.QUICK_EDIT_BALANCE,
                account = accountName,
                delta   = diff,
                note    = "Manual balance edit: $oldBalance → $newBalance",
            )
            val t = Transaction(
                id       = app.fynlo.logic.Ids.newId(),
                date     = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                type     = if (diff >= 0) "Income" else "Expense",
                amount   = Math.abs(diff),
                category = "Balance Correction",
                desc     = "Manual balance adjustment",
                toAcct   = if (diff >= 0) accountName else "",
                toAcctId = if (diff >= 0) accountId else "",
                fromAcct = if (diff < 0) accountName else "",
                fromAcctId = if (diff < 0) accountId else "",
                projectId = "personal",
                updatedAt = System.currentTimeMillis()
            )
            dao.insertTransaction(t)
            recordAudit(
                action = "EDIT",
                entityType = "account",
                entityId = accountName,
                title = "Account balance corrected: $accountName",
                beforeValue = oldBalance.toString(),
                afterValue = newBalance.toString(),
                amountDelta = diff,
                accountName = accountName,
                projectId = t.projectId,
                reason = "Manual balance correction",
            )
            sync { setTransaction(t) }
        }
        // Push updated account
        syncAccountByName(accountName)
    }

    suspend fun insertAccount(account: Account) {
        val now = System.currentTimeMillis()
        val a = account.copy(updatedAt = now, createdAt = if (account.createdAt == 0L) now else account.createdAt)
        dao.insertAccount(a)
        recordAudit(
            action = "CREATE",
            entityType = "account",
            entityId = a.id,
            title = "Account created: ${a.name}",
            afterValue = "${a.name}:${a.type}:${a.balance}",
            amountDelta = a.balance,
            accountName = a.name,
            projectId = a.projectId,
        )
        sync { setAccount(a) }
    }
    // ─── Investment — funded by own account ────────────────────────────────────
    suspend fun insertInvestmentFundedByAccount(investment: Investment, accountName: String, projectId: String = investment.projectId, accountId: String = "") {
        requireOpenDate(investment.date, projectId)
        db.withTransaction {
            if (dao.getInvestmentById(investment.id) != null) return@withTransaction
            val resolvedAccountId = accountId.ifBlank { dao.getAccountByName(accountName)?.id ?: "" }
            val i = investment.copy(sourceType = "account", fundingSource = accountName, projectId = projectId, updatedAt = System.currentTimeMillis(), createdAt = if (investment.createdAt == 0L) System.currentTimeMillis() else investment.createdAt)
            dao.insertInvestment(i)
            applyAccountDelta(resolvedAccountId, accountName, -investment.invested)
            val t = Transaction(app.fynlo.logic.Ids.newId(), investment.date, "Expense", investment.invested,
                fromAcct = accountName, fromAcctId = resolvedAccountId, category = "Investment",
                desc = "Invested in ${investment.name}", ref = i.id,
                notes = investment.notes, projectId = projectId, updatedAt = System.currentTimeMillis())
            dao.insertTransaction(t)
            recordAudit(
                action = "CREATE",
                entityType = "investment",
                entityId = i.id,
                title = "Investment created: ${i.name}",
                afterValue = "invested=${i.invested}:current=${i.currentVal}:fundedBy=$accountName:txn=${t.id}",
                amountDelta = -i.invested,
                accountName = accountName,
                projectId = projectId,
            )
            recordUndo(
                action = "CREATE",
                entityType = "investment",
                entityId = i.id,
                title = "Create investment: ${i.name}",
                afterJson = undoJson.encodeToString(
                    InvestmentUndoBundle(investment = i, transactions = listOf(t))
                ),
                projectId = projectId,
            )
            sync { setInvestment(i); setTransaction(t) }
        }
        Analytics.investmentCreated(type = investment.type)
        syncAccountByName(accountName)
    }

    // ─── Investment — funded by existing recorded debt ──────────────────────────
    suspend fun insertInvestmentFundedByExistingDebt(investment: Investment, debt: app.fynlo.data.model.Debt, projectId: String = investment.projectId) {
        requireOpenDate(investment.date, projectId)
        db.withTransaction {
            if (dao.getInvestmentById(investment.id) != null) return@withTransaction
            val i = investment.copy(sourceType = "existing_debt", fundingSource = debt.name, linkedDebtId = debt.id, projectId = projectId, updatedAt = System.currentTimeMillis(), createdAt = if (investment.createdAt == 0L) System.currentTimeMillis() else investment.createdAt)
            dao.insertInvestment(i)
            val t = Transaction(app.fynlo.logic.Ids.newId(), investment.date, "Info", investment.invested,
                category = "Investment",
                desc = "Invested in ${investment.name} using ${debt.name} loan funds",
                ref = i.id, notes = investment.notes, tags = "journal_only",
                projectId = projectId, updatedAt = System.currentTimeMillis())
            dao.insertTransaction(t)
            recordAudit(
                action = "CREATE",
                entityType = "investment",
                entityId = i.id,
                title = "Investment created from debt: ${i.name}",
                afterValue = "invested=${i.invested}:debt=${debt.name}:debtId=${debt.id}:txn=${t.id}",
                amountDelta = i.invested,
                accountName = debt.name,
                projectId = projectId,
            )
            recordUndo(
                action = "CREATE",
                entityType = "investment",
                entityId = i.id,
                title = "Create investment: ${i.name}",
                afterJson = undoJson.encodeToString(
                    InvestmentUndoBundle(investment = i, transactions = listOf(t))
                ),
                projectId = projectId,
            )
            sync { setInvestment(i); setTransaction(t) }
        }
        Analytics.investmentCreated(type = investment.type)
    }

    // ─── Investment — auto-create new loan + link to investment ─────────────────
    suspend fun insertInvestmentFundedByNewLoan(investment: Investment, newDebt: app.fynlo.data.model.Debt, projectId: String = investment.projectId) {
        requireOpenDate(investment.date, projectId)
        requireOpenDate(newDebt.date, projectId)
        db.withTransaction {
            if (dao.getInvestmentById(investment.id) != null) return@withTransaction
            val d = newDebt.copy(projectId = projectId, updatedAt = System.currentTimeMillis())
            dao.insertDebt(d)
            val i = investment.copy(sourceType = "new_loan", fundingSource = d.name, linkedDebtId = d.id, projectId = projectId, updatedAt = System.currentTimeMillis(), createdAt = if (investment.createdAt == 0L) System.currentTimeMillis() else investment.createdAt)
            dao.insertInvestment(i)
            val projectCurrency = dao.getProjectById(projectId)?.currency ?: "INR"
            val investedText = app.fynlo.logic.CurrencyFormatter.detail(investment.invested, projectCurrency)
            val t = Transaction(app.fynlo.logic.Ids.newId(), investment.date, "Info", investment.invested,
                category = "Investment",
                desc = "Invested $investedText in ${investment.name} via ${d.name} loan",
                ref = i.id, notes = investment.notes, tags = "journal_only",
                projectId = projectId, updatedAt = System.currentTimeMillis())
            dao.insertTransaction(t)
            recordAudit(
                action = "CREATE",
                entityType = "investment",
                entityId = i.id,
                title = "Investment created from new loan: ${i.name}",
                afterValue = "invested=${i.invested}:newDebt=${d.name}:debtId=${d.id}:txn=${t.id}",
                amountDelta = i.invested,
                accountName = d.name,
                projectId = projectId,
            )
            recordUndo(
                action = "CREATE",
                entityType = "investment",
                entityId = i.id,
                title = "Create investment: ${i.name}",
                afterJson = undoJson.encodeToString(
                    InvestmentUndoBundle(investment = i, transactions = listOf(t), linkedDebt = d)
                ),
                projectId = projectId,
            )
            sync { setDebt(d); setInvestment(i); setTransaction(t) }
        }
        Analytics.investmentCreated(type = investment.type)
    }

    // ─── Keep old function as no-op shim so nothing else breaks ───────────────
    suspend fun insertInvestmentWithSource(investment: Investment, sourceAccount: String, projectId: String = investment.projectId) {
        insertInvestmentFundedByAccount(investment, sourceAccount, projectId)
    }

    // ─── Delete — record only, no balance reversal ─────────────────────────────
    suspend fun deleteInvestmentOnly(investment: Investment) {
        requireOpenDate(investment.date, investment.projectId)
        var didDelete = false
        db.withTransaction {
            val current = dao.getInvestmentById(investment.id) ?: return@withTransaction
            // Remove the investment transaction that moved money out of an account
            val invTxns = dao.getTransactionsByRef(current.id)
                .filter { it.category == "Investment" }
            recordUndo(
                action = "DELETE",
                entityType = "investment",
                entityId = current.id,
                title = "Delete investment: ${current.name}",
                beforeJson = undoJson.encodeToString(
                    InvestmentUndoBundle(
                        investment = current,
                        transactions = invTxns,
                        replayBalancesOnRestore = false,
                    )
                ),
                projectId = current.projectId,
            )
            invTxns.forEach { invTxn ->
                dao.deleteTransaction(invTxn)
                tombstoneRemoteDoc("transactions", invTxn.id)
                sync { deleteTransaction(invTxn.id) }
            }
            dao.deleteInvestment(current)
            tombstoneRemoteDoc("investments", current.id)
            recordAudit(
                action = "DELETE",
                entityType = "investment",
                entityId = current.id,
                title = "Investment deleted: ${current.name}",
                beforeValue = "invested=${current.invested}:current=${current.currentVal}:source=${current.fundingSource}",
                amountDelta = -current.currentVal,
                accountName = current.fundingSource,
                projectId = current.projectId,
                reason = "Delete investment only",
            )
            didDelete = true
        }
        if (didDelete) sync { deleteInvestment(investment.id) }
    }

    // ─── Delete — record + reverse source account balance ─────────────────────
    suspend fun deleteInvestmentAndReverseAccount(investment: Investment) {
        requireOpenDate(investment.date, investment.projectId)
        var didDelete = false
        var fundingSourceToSync = ""
        db.withTransaction {
            val current = dao.getInvestmentById(investment.id) ?: return@withTransaction
            // Also delete the Investment expense transaction (find by ref or desc)
            val invTxns = dao.getTransactionsByRef(current.id)
                .filter { it.category == "Investment" }
                .ifEmpty {
                    dao.getTransactionsByDesc("Invested in ${current.name}")
                        .filter { it.category == "Investment" && it.ref.isBlank() }
                        .take(1)
                }
            recordUndo(
                action = "DELETE",
                entityType = "investment",
                entityId = current.id,
                title = "Delete investment: ${current.name}",
                beforeJson = undoJson.encodeToString(
                    InvestmentUndoBundle(
                        investment = current,
                        transactions = invTxns,
                        replayBalancesOnRestore = true,
                    )
                ),
                projectId = current.projectId,
            )
            invTxns.forEach { invTxn ->
                applyAccountDelta(invTxn.fromAcctId, invTxn.fromAcct, invTxn.amount)
                fundingSourceToSync = invTxn.fromAcct
                dao.deleteTransaction(invTxn)
                tombstoneRemoteDoc("transactions", invTxn.id)
                sync { deleteTransaction(invTxn.id) }
            }
            dao.deleteInvestment(current)
            tombstoneRemoteDoc("investments", current.id)
            recordAudit(
                action = "DELETE",
                entityType = "investment",
                entityId = current.id,
                title = "Investment deleted and funding reversed: ${current.name}",
                beforeValue = "invested=${current.invested}:current=${current.currentVal}:source=${current.fundingSource}",
                amountDelta = current.invested,
                accountName = fundingSourceToSync.ifBlank { current.fundingSource },
                projectId = current.projectId,
                reason = "Investment funding reversed to source account",
            )
            didDelete = true
        }
        if (didDelete) {
            sync { deleteInvestment(investment.id) }
            if (fundingSourceToSync.isNotBlank()) syncAccountByName(fundingSourceToSync)
        }
    }

    // ─── Delete — record + delete the linked loan that was auto-created ────────
    suspend fun deleteInvestmentAndLinkedLoan(investment: Investment) {
        requireOpenDate(investment.date, investment.projectId)
        var deletedInvestment: Investment? = null
        db.withTransaction {
            val current = dao.getInvestmentById(investment.id) ?: return@withTransaction
            // Delete the investment transaction
            val invTxns = dao.getTransactionsByRef(current.id)
                .filter { it.category == "Investment" }
            val linkedDebt = current.linkedDebtId.takeIf { it.isNotEmpty() }?.let { dao.getDebtById(it) }
            val linkedDebtTxns = current.linkedDebtId.takeIf { it.isNotEmpty() }
                ?.let { dao.getTransactionsByRef(it) }
                .orEmpty()
            recordUndo(
                action = "DELETE",
                entityType = "investment",
                entityId = current.id,
                title = "Delete investment: ${current.name}",
                beforeJson = undoJson.encodeToString(
                    InvestmentUndoBundle(
                        investment = current,
                        transactions = invTxns + linkedDebtTxns,
                        linkedDebt = linkedDebt,
                        replayBalancesOnRestore = true,
                    )
                ),
                projectId = current.projectId,
            )
            invTxns.forEach { invTxn ->
                dao.deleteTransaction(invTxn)
                tombstoneRemoteDoc("transactions", invTxn.id)
                sync { deleteTransaction(invTxn.id) }
            }
            // Delete the linked debt and its transactions
            if (current.linkedDebtId.isNotEmpty()) {
                if (linkedDebt != null) {
                    val debtTxn = linkedDebtTxns
                        .firstOrNull { it.type.equals("Income", ignoreCase = true) }
                    if (debtTxn != null) {
                        // C03b Stage #1b-1: id-keyed reversal of the income txn
                        // that originally credited the destination account.
                        applyAccountDelta(debtTxn.toAcctId, debtTxn.toAcct, -linkedDebt.amount)
                        dao.deleteTransaction(debtTxn)
                        tombstoneRemoteDoc("transactions", debtTxn.id)
                        sync { deleteTransaction(debtTxn.id) }
                    }
                }
                dao.deleteDebtById(current.linkedDebtId)
            }
            dao.deleteInvestment(current)
            tombstoneRemoteDoc("investments", current.id)
            recordAudit(
                action = "DELETE",
                entityType = "investment",
                entityId = current.id,
                title = "Investment and linked loan deleted: ${current.name}",
                beforeValue = "invested=${current.invested}:linkedDebt=${current.linkedDebtId}:source=${current.fundingSource}",
                amountDelta = -current.currentVal,
                accountName = current.fundingSource,
                projectId = current.projectId,
                reason = "Delete investment with linked debt",
            )
            deletedInvestment = current
        }
        val current = deletedInvestment ?: return
        sync {
            if (current.linkedDebtId.isNotEmpty()) deleteDebt(current.linkedDebtId)
            deleteInvestment(current.id)
        }
    }

    suspend fun upsertAccount(account: Account) {
        val before = dao.getAccountById(account.id)
        dao.insertAccount(account)
        recordAudit(
            action = if (before == null) "CREATE" else "EDIT",
            entityType = "account",
            entityId = account.id,
            title = if (before == null) "Account created: ${account.name}" else "Account edited: ${account.name}",
            beforeValue = before?.let { "${it.name}:${it.type}:${it.balance}" } ?: "",
            afterValue = "${account.name}:${account.type}:${account.balance}",
            amountDelta = account.balance - (before?.balance ?: 0.0),
            accountName = account.name,
            projectId = account.projectId,
        )
        sync { setAccount(account) }
    }

    suspend fun deleteUnusedAccount(account: Account) {
        dao.deleteAccountById(account.id)
        recordAudit(
            action = "DELETE",
            entityType = "account",
            entityId = account.id,
            title = "Account deleted: ${account.name}",
            beforeValue = "${account.name}:${account.type}:${account.balance}",
            amountDelta = -account.balance,
            accountName = account.name,
            projectId = account.projectId,
        )
        sync { deleteAccount(account.id) }
    }

    suspend fun updateInvestmentValue(investment: Investment, newCurrentVal: Double) {
        val updated = investment.copy(currentVal = newCurrentVal, updatedAt = System.currentTimeMillis())
        dao.insertInvestment(updated)
        sync { setInvestment(updated) }
    }

    suspend fun updateInvestment(investment: Investment) {
        val updated = investment.copy(updatedAt = System.currentTimeMillis())
        dao.insertInvestment(updated)
        sync { setInvestment(updated) }
    }

    suspend fun updateInvestmentFundedByAccount(
        investment: Investment,
        accountName: String,
        projectId: String = investment.projectId,
        accountId: String = "",
    ) {
        requireOpenDate(investment.date, projectId)
        val before = dao.getInvestmentById(investment.id)
        val now = System.currentTimeMillis()
        var oldSource = ""
        var newSource = accountName
        var updatedInvestment: Investment? = null
        var updatedFundingTxn: Transaction? = null
        db.withTransaction {
            val linkedTxns = dao.getTransactionsByRef(investment.id)
            val fundingTxn = linkedTxns
                .firstOrNull { it.category == "Investment" && it.type.equals("Expense", ignoreCase = true) }
            val journalTxn = linkedTxns
                .firstOrNull { isInvestmentJournalTrace(it) }
            oldSource = fundingTxn?.fromAcct ?: before?.fundingSource.orEmpty()
            newSource = accountName.ifBlank { oldSource }
            val resolvedAccountId = accountId.ifBlank { dao.getAccountByName(newSource)?.id ?: "" }
            val i = investment.copy(
                sourceType = "account",
                fundingSource = newSource,
                projectId = projectId,
                updatedAt = now,
                createdAt = if (investment.createdAt == 0L) before?.createdAt ?: now else investment.createdAt,
            )
            dao.insertInvestment(i)
            updatedInvestment = i
            if (before != null) {
                if (fundingTxn != null) {
                    applyAccountDelta(fundingTxn.fromAcctId, fundingTxn.fromAcct, fundingTxn.amount)
                }
                applyAccountDelta(resolvedAccountId, newSource, -i.invested)
                val fundingUpdate = (fundingTxn ?: journalTxn)?.copy(
                    date = i.date,
                    type = "Expense",
                    amount = i.invested,
                    fromAcct = newSource,
                    fromAcctId = resolvedAccountId,
                    toAcct = "",
                    toAcctId = "",
                    category = "Investment",
                    desc = "Invested in ${i.name}",
                    notes = i.notes,
                    tags = removeTag((fundingTxn ?: journalTxn)?.tags.orEmpty(), "journal_only"),
                    projectId = projectId,
                    updatedAt = now,
                ) ?: Transaction(
                    id = app.fynlo.logic.Ids.newId(),
                    date = i.date,
                    type = "Expense",
                    amount = i.invested,
                    fromAcct = newSource,
                    fromAcctId = resolvedAccountId,
                    category = "Investment",
                    desc = "Invested in ${i.name}",
                    ref = i.id,
                    notes = i.notes,
                    projectId = projectId,
                    updatedAt = now,
                    createdAt = now,
                )
                updatedFundingTxn = fundingUpdate
                dao.insertTransaction(fundingUpdate)
            }
            recordAudit(
                action = "EDIT",
                entityType = "investment",
                entityId = i.id,
                title = "Investment edited: ${i.name}",
                beforeValue = before?.let { "${it.name}:${it.invested}:current=${it.currentVal}:source=${it.fundingSource}" } ?: "",
                afterValue = "${i.name}:${i.invested}:current=${i.currentVal}:source=${i.fundingSource}",
                amountDelta = i.invested - (before?.invested ?: i.invested),
                accountName = i.fundingSource,
                projectId = projectId,
            )
        }
        sync {
            updatedInvestment?.let { setInvestment(it) }
            updatedFundingTxn?.let { setTransaction(it) }
        }
        if (oldSource.isNotBlank()) syncAccountByName(oldSource)
        if (newSource.isNotBlank() && newSource != oldSource) syncAccountByName(newSource)
    }

    suspend fun updateInvestmentFundedByExistingDebt(
        investment: Investment,
        debt: Debt,
        projectId: String = investment.projectId,
    ) {
        val before = dao.getInvestmentById(investment.id)
        val now = System.currentTimeMillis()
        val remoteTransactions = mutableListOf<Transaction>()
        val touchedAccounts = mutableSetOf<String>()
        var updatedInvestment: Investment? = null
        db.withTransaction {
            val linkedTxns = dao.getTransactionsByRef(investment.id)
            val oldFundingTxn = linkedTxns.firstOrNull {
                it.category == "Investment" && it.type.equals("Expense", ignoreCase = true)
            }
            val journalTxn = linkedTxns.firstOrNull { isInvestmentJournalTrace(it) }

            if (oldFundingTxn != null) {
                applyAccountDelta(oldFundingTxn.fromAcctId, oldFundingTxn.fromAcct, oldFundingTxn.amount)
                touchedAccounts += oldFundingTxn.fromAcct
            }

            val i = investment.copy(
                sourceType = "existing_debt",
                fundingSource = debt.name,
                linkedDebtId = debt.id,
                projectId = projectId,
                updatedAt = now,
                createdAt = if (investment.createdAt == 0L) before?.createdAt ?: now else investment.createdAt,
            )
            dao.insertInvestment(i)
            updatedInvestment = i

            val trace = (journalTxn ?: oldFundingTxn)?.copy(
                date = i.date,
                type = "Info",
                amount = i.invested,
                fromAcct = "",
                toAcct = "",
                fromAcctId = "",
                toAcctId = "",
                category = "Investment",
                desc = "Invested in ${i.name} using ${debt.name} loan funds",
                ref = i.id,
                notes = i.notes,
                tags = addTag((journalTxn ?: oldFundingTxn)?.tags.orEmpty(), "journal_only"),
                projectId = projectId,
                updatedAt = now,
            ) ?: Transaction(
                id = app.fynlo.logic.Ids.newId(),
                date = i.date,
                type = "Info",
                amount = i.invested,
                category = "Investment",
                desc = "Invested in ${i.name} using ${debt.name} loan funds",
                ref = i.id,
                notes = i.notes,
                tags = "journal_only",
                projectId = projectId,
                updatedAt = now,
                createdAt = now,
            )
            dao.insertTransaction(trace)
            remoteTransactions += trace
            recordAudit(
                action = "EDIT",
                entityType = "investment",
                entityId = i.id,
                title = "Investment edited: ${i.name}",
                beforeValue = before?.let { "${it.name}:${it.invested}:current=${it.currentVal}:source=${it.fundingSource}" } ?: "",
                afterValue = "${i.name}:${i.invested}:current=${i.currentVal}:source=${i.fundingSource}",
                amountDelta = i.invested - (before?.invested ?: i.invested),
                accountName = i.fundingSource,
                projectId = projectId,
            )
        }
        sync {
            updatedInvestment?.let { setInvestment(it) }
            remoteTransactions.forEach { setTransaction(it) }
        }
        touchedAccounts.filter { it.isNotBlank() }.forEach { syncAccountByName(it) }
    }

    private fun addTag(existing: String, tag: String): String =
        (existing.split(",") + tag)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .joinToString(",")

    private fun removeTag(existing: String, tag: String): String =
        existing.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals(tag, ignoreCase = true) }
            .joinToString(",")

    suspend fun executeLinkedInvestment(
        investment: Investment,
        fundingSourceType: String, // "Account", "Debt", "Already Settled"
        sourceName: String,
        debtDetails: Debt? = null
    ) {
        requireOpenDate(investment.date, investment.projectId)
        debtDetails?.let { requireOpenDate(it.date, it.projectId.ifBlank { investment.projectId }) }
        val createdTransactions = mutableListOf<Transaction>()
        var createdDebt: Debt? = null
        db.withTransaction {
            val pid = investment.projectId
            
            // 1. Record Investment Asset
            dao.insertInvestment(investment)
            
            // 2. Handle Funding Source
            when (fundingSourceType) {
                "Account" -> {
                    // Deduct from existing bank/cash
                    dao.updateAccountBalance(sourceName, -investment.invested)
                    val t = Transaction(
                        id = app.fynlo.logic.Ids.newId(),
                        date = investment.date,
                        type = "Expense",
                        amount = investment.invested,
                        fromAcct = sourceName,
                        category = "Investment",
                        desc = "Invested in ${investment.name}",
                        ref = investment.id,
                        projectId = pid,
                        updatedAt = System.currentTimeMillis()
                    )
                    dao.insertTransaction(t)
                    createdTransactions += t
                    sync { setTransaction(t) }
                    syncAccountByName(sourceName)
                }
                "Debt" -> {
                    // Record a new Debt liability
                    debtDetails?.let {
                        val d = it.copy(updatedAt = System.currentTimeMillis())
                        dao.insertDebt(d)
                        createdDebt = d
                        val t = Transaction(
                            id = app.fynlo.logic.Ids.newId(),
                            date = investment.date,
                            type = "Info",
                            amount = investment.invested,
                            category = "Debt",
                            desc = "Investment funded by loan from ${d.name}",
                            ref = d.id,
                            tags = "journal_only",
                            projectId = pid,
                            updatedAt = System.currentTimeMillis()
                        )
                        dao.insertTransaction(t)
                        createdTransactions += t
                        sync { 
                            setDebt(d)
                            setTransaction(t) 
                        }
                    }
                }
                "Already Settled" -> {
                    // Historical entry - just a journal record for traceability
                    val t = Transaction(
                        id = app.fynlo.logic.Ids.newId(),
                        date = investment.date,
                        type = "Info", 
                        amount = investment.invested,
                        category = "Historical Record",
                        desc = "Asset established long back",
                        ref = investment.id,
                        projectId = pid,
                        updatedAt = System.currentTimeMillis()
                    )
                    dao.insertTransaction(t)
                    createdTransactions += t
                    sync { setTransaction(t) }
                }
            }
            
            // 3. Initial Valuation
            val v = InvestmentValuation(
                id = app.fynlo.logic.Ids.newId(),
                investmentId = investment.id,
                date = investment.date,
                value = investment.invested,
                notes = "Initial purchase"
            )
            dao.insertValuation(v)
            
            sync { 
                setInvestment(investment)
                setValuation(v)
            }
            recordUndo(
                action = "CREATE",
                entityType = "investment",
                entityId = investment.id,
                title = "Create investment: ${investment.name}",
                afterJson = undoJson.encodeToString(
                    InvestmentUndoBundle(
                        investment = investment,
                        transactions = createdTransactions,
                        linkedDebt = createdDebt,
                    )
                ),
                projectId = pid,
            )
        }
    }

    suspend fun addValuation(v: InvestmentValuation) {
        db.withTransaction {
            dao.insertValuation(v)
            // Update investment currentVal for fast dashboard access
            val inv = dao.getAllInvestments().first().find { it.id == v.investmentId }
            inv?.let {
                val updated = it.copy(currentVal = v.value, updatedAt = System.currentTimeMillis())
                dao.insertInvestment(updated)
                sync { setInvestment(updated) }
            }
            sync { setValuation(v) }
        }
    }

    fun getValuationsForInvestment(invId: String) = dao.getValuationsForInvestment(invId)

    suspend fun deleteInvestment(investment: Investment) {
        // Legacy shim — use deleteInvestmentOnly, deleteInvestmentAndReverseAccount,
        // or deleteInvestmentAndLinkedLoan from the ViewModel based on user choice.
        deleteInvestmentOnly(investment)
    }
    suspend fun insertDebtWithDestination(debt: Debt, destinationAccount: String, projectId: String = debt.projectId) {
        requireOpenDate(debt.date, projectId)
        db.withTransaction {
            if (dao.getDebtById(debt.id) != null) return@withTransaction
            val now = System.currentTimeMillis()
            // C03b Stage #3: resolve peopleId for the lender, same dedup
            // spine borrowers use. Empty-phone debts (e.g. credit cards
            // with no human counterparty) stay unlinked — peopleId is
            // optional, not required.
            val resolvedPeopleId =
                if (debt.peopleId.isNotEmpty()) debt.peopleId
                else findOrCreatePersonId(debt.name, debt.phone, projectId)
            val d = debt.copy(
                projectId = projectId,
                peopleId  = resolvedPeopleId,
                updatedAt = now,
                createdAt = if (debt.createdAt == 0L) now else debt.createdAt,
            )
            dao.insertDebt(d)
            val destinationAccountId = dao.getAccountByName(destinationAccount)?.id ?: ""
            applyAccountDelta(destinationAccountId, destinationAccount, debt.amount)
            val t = Transaction(app.fynlo.logic.Ids.newId(), debt.date, "Income", debt.amount, toAcct = destinationAccount, toAcctId = destinationAccountId, category = "Debt Received", desc = "Loan received from ${debt.name}", ref = d.id, notes = debt.notes, projectId = projectId, updatedAt = now, createdAt = now)
            dao.insertTransaction(t)
            recordAudit(
                action = "CREATE",
                entityType = "debt",
                entityId = d.id,
                title = "Debt created: ${d.name}",
                afterValue = "borrowed=${d.amount}:to=$destinationAccount:txn=${t.id}",
                amountDelta = d.amount,
                accountName = destinationAccount,
                projectId = projectId,
            )
            recordUndo(
                action = "CREATE",
                entityType = "debt",
                entityId = d.id,
                title = "Create debt: ${d.name}",
                afterJson = undoJson.encodeToString(DebtUndoBundle(debt = d, transactions = listOf(t))),
                projectId = projectId,
            )
            sync { setDebt(d); setTransaction(t) }
        }
        syncAccountByName(destinationAccount)
    }
    suspend fun deleteDebt(debt: Debt) {
        requireOpenDate(debt.date, debt.projectId)
        var linkedTxns = emptyList<Transaction>()
        var linkedPayments = emptyList<DebtPayment>()
        var didDelete = false
        db.withTransaction {
            val current = dao.getDebtById(debt.id) ?: return@withTransaction
            linkedTxns = dao.getTransactionsByRef(current.id)
            linkedTxns.forEach { txn ->
                // C03b Stage #1b-1: id-keyed reversal (orphans fall back to name).
                when (txn.type.lowercase()) {
                    "expense"  -> applyAccountDelta(txn.fromAcctId, txn.fromAcct,  txn.amount)
                    "income"   -> applyAccountDelta(txn.toAcctId,   txn.toAcct,   -txn.amount)
                    "transfer" -> {
                        applyAccountDelta(txn.fromAcctId, txn.fromAcct,  txn.amount)
                        applyAccountDelta(txn.toAcctId,   txn.toAcct,   -txn.amount)
                    }
                }
                dao.deleteTransaction(txn)
                tombstoneRemoteDoc("transactions", txn.id)
            }
            linkedPayments = dao.getDebtPaymentsForDebtOnce(current.id)
            linkedPayments.forEach { p ->
                dao.deleteDebtPayment(p)
                tombstoneRemoteDoc("debt_payments", p.id)
            }
            recordUndo(
                action = "DELETE",
                entityType = "debt",
                entityId = current.id,
                title = "Delete debt: ${current.name}",
                beforeJson = undoJson.encodeToString(
                    DebtUndoBundle(
                        debt = current,
                        transactions = linkedTxns,
                        payments = linkedPayments,
                    )
                ),
                projectId = current.projectId,
            )
            dao.deleteDebt(current)
            tombstoneRemoteDoc("debts", current.id)
            recordAudit(
                action = "DELETE",
                entityType = "debt",
                entityId = current.id,
                title = "Debt deleted: ${current.name}",
                beforeValue = "borrowed=${current.amount}:paid=${current.paid}",
                amountDelta = -current.amount,
                accountName = linkedTxns.firstOrNull { it.type.equals("Income", ignoreCase = true) }?.toAcct ?: "",
                projectId = current.projectId,
            )
            didDelete = true
        }
        if (!didDelete) return
        linkedTxns.forEach { sync { deleteTransaction(it.id) } }
        linkedPayments.forEach { sync { deleteDebtPayment(it.id) } }
        sync { deleteDebt(debt.id) }
        linkedTxns.map { it.fromAcct }.filter { it.isNotBlank() }.distinct().forEach { syncAccountByName(it) }
        linkedTxns.map { it.toAcct }.filter { it.isNotBlank() }.distinct().forEach { syncAccountByName(it) }
    }
    suspend fun insertPaymentWithDest(payment: Payment, destinationAccount: String, projectId: String = payment.projectId) {
        requireOpenDate(payment.date, projectId)
        db.withTransaction {
            if (dao.getPaymentById(payment.id) != null) return@withTransaction
            val now = System.currentTimeMillis()
            val p = payment.copy(projectId = projectId, updatedAt = now, createdAt = if (payment.createdAt == 0L) now else payment.createdAt)
            dao.insertPayment(p)
            Analytics.paymentCollected()

            // Credit the destination account with full payment amount
            dao.updateAccountBalance(destinationAccount, payment.amount)

            // Derive paid / paidPrincipal / paidInterest from the payments
            // table (single source of truth per
            // decisions/2026-05-26-c01-fix-strategy.md Stage 2). The Payment
            // row was just inserted above, so the rebuild query picks it up.
            dao.rebuildBorrowerPaidFromPayments()

            // Main repayment transaction (full amount received)
            val t = Transaction(
                id = app.fynlo.logic.Ids.newId(),
                date = payment.date,
                type = "Income",
                amount = payment.amount,
                toAcct = destinationAccount,
                category = "Loan Repayment",
                desc = "Received from ${payment.name}",
                ref = payment.loanId,
                notes = payment.notes,
                projectId = projectId,
                updatedAt = now,
                createdAt = now
            )
            dao.insertTransaction(t)

            // Sync the updated borrower too
            val updatedBorrower = dao.getBorrowerById(payment.loanId)
            recordAudit(
                action = "PAYMENT",
                entityType = "loan",
                entityId = payment.loanId,
                title = "Loan payment received: ${payment.name}",
                afterValue = "amount=${p.amount}:to=$destinationAccount:payment=${p.id}:txn=${t.id}",
                amountDelta = p.amount,
                accountName = destinationAccount,
                projectId = projectId,
            )
            recordUndo(
                action = "CREATE",
                entityType = "payment",
                entityId = p.id,
                title = "Loan payment: ${p.name}",
                afterJson = undoJson.encodeToString(PaymentUndoBundle(payment = p, transactions = listOf(t))),
                projectId = projectId,
            )
            sync {
                setPayment(p)
                setTransaction(t)
                updatedBorrower?.let { setBorrower(it) }
            }
        }
        syncAccountByName(destinationAccount)
    }
    suspend fun insertDebtPaymentWithSource(payment: DebtPayment, sourceAccount: String, projectId: String = payment.projectId) {
        requireOpenDate(payment.date, projectId)
        db.withTransaction {
            if (dao.getDebtPaymentById(payment.id) != null) return@withTransaction
            val now = System.currentTimeMillis()
            val p = payment.copy(projectId = projectId, updatedAt = now, createdAt = if (payment.createdAt == 0L) now else payment.createdAt)
            dao.insertDebtPayment(p)

            // Debit source account with full payment amount
            dao.updateAccountBalance(sourceAccount, -payment.amount)

            // Derive paid / paidPrincipal / paidInterest from debt_payments
            // (single source of truth per
            // decisions/2026-05-26-c01-fix-strategy.md Stage 2). The
            // DebtPayment row was just inserted above, so the rebuild picks
            // it up.
            dao.rebuildDebtPaidFromDebtPayments()

            // interestPaid is still needed below for the auto-split
            // "Interest Expense" Transaction (an interest-only debt payment
            // shows up in P&L as a cost-of-borrowing line).
            val interestPaid = payment.interest.coerceAtLeast(0.0)

            // Main repayment transaction (full amount paid)
            val t = Transaction(
                id = app.fynlo.logic.Ids.newId(),
                date = payment.date,
                type = "Expense",
                amount = payment.amount,
                fromAcct = sourceAccount,
                category = "Debt Repayment",
                desc = "EMI/payment for ${payment.name}",
                ref = payment.debtId,
                notes = payment.notes,
                projectId = projectId,
                updatedAt = now,
                createdAt = now
            )
            dao.insertTransaction(t)
            val paymentTransactions = mutableListOf(t)

            // If there's an interest portion, also create a separate "Interest Expense" entry
            // so it shows in P&L as cost of borrowing
            if (interestPaid > 0.01) {
                val intTxn = Transaction(
                    id = app.fynlo.logic.Ids.newId(),
                    date = payment.date,
                    type = "Expense",
                    amount = interestPaid,
                    fromAcct = sourceAccount,
                    category = "Interest Expense",
                    desc = "Interest paid on ${payment.name}",
                    ref = payment.debtId,
                    notes = "Auto-split from debt payment",
                    projectId = projectId,
                    updatedAt = now,
                    createdAt = now
                )
                // Note: we do NOT double-deduct account balance here —
                // the account was already debited by the full payment amount above.
                // This is a JOURNAL ENTRY for P&L tracking only, not a cash movement.
                // We mark it with a special tag so it's excluded from cash calculations.
                val journalTxn = intTxn.copy(tags = "journal_only")
                dao.insertTransaction(journalTxn)
                paymentTransactions += journalTxn
                sync { setTransaction(journalTxn) }
            }

            val updatedDebt = dao.getDebtById(payment.debtId)
            recordAudit(
                action = "PAYMENT",
                entityType = "debt",
                entityId = payment.debtId,
                title = "Debt payment made: ${payment.name}",
                afterValue = "amount=${p.amount}:from=$sourceAccount:payment=${p.id}:txn=${t.id}:interest=$interestPaid",
                amountDelta = -p.amount,
                accountName = sourceAccount,
                projectId = projectId,
            )
            recordUndo(
                action = "CREATE",
                entityType = "debt_payment",
                entityId = p.id,
                title = "Debt payment: ${p.name}",
                afterJson = undoJson.encodeToString(
                    DebtPaymentUndoBundle(payment = p, transactions = paymentTransactions)
                ),
                projectId = projectId,
            )
            sync {
                setDebtPayment(p)
                setTransaction(t)
                updatedDebt?.let { setDebt(it) }
            }
        }
        syncAccountByName(sourceAccount)
    }
    suspend fun insertPerson(person: Person) { val now = System.currentTimeMillis(); val p = person.copy(updatedAt = now, createdAt = if (person.createdAt == 0L) now else person.createdAt); dao.insertPerson(p); sync { setPerson(p) } }
    suspend fun deletePerson(person: Person) { dao.deletePerson(person); sync { deletePerson(person.id) } }
    suspend fun insertBudget(budget: Budget) { val now = System.currentTimeMillis(); val b = budget.copy(updatedAt = now, createdAt = if (budget.createdAt == 0L) now else budget.createdAt); dao.insertBudget(b); sync { setBudget(b) } }
    suspend fun deleteBudget(budget: Budget) { dao.deleteBudget(budget); sync { deleteBudget(budget.category) } }
    suspend fun insertGoal(goal: Goal) { val now = System.currentTimeMillis(); val g = goal.copy(updatedAt = now, createdAt = if (goal.createdAt == 0L) now else goal.createdAt); dao.insertGoal(g); sync { setGoal(g) } }
    suspend fun deleteGoal(goal: Goal) { dao.deleteGoal(goal); sync { deleteGoal(goal.id) } }

    // ─── Investment Withdrawal Engine ──────────────────────────────────────────
    // Called when an FD matures, stocks sold, MF redeemed etc.
    // withdrawAmount: how much to withdraw (can be partial)
    // toAccount: bank account that receives the money
    // Returns the realized gain/loss for this withdrawal
    suspend fun withdrawFromInvestment(investment: Investment, withdrawAmount: Double, toAccount: String): Double {
        val today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        requireOpenDate(today, investment.projectId)
        val proportionWithdrawn = if (investment.currentVal > 0)
            (withdrawAmount / investment.currentVal).coerceIn(0.0, 1.0) else 0.0
        val costBasis   = investment.invested * proportionWithdrawn  // what we paid for this portion
        val gainLoss    = withdrawAmount - costBasis                  // profit or loss

        // Look up the project's currency for the persisted notes string.
        // Falls back to INR if the project lookup fails (orphaned investment, schema race, etc).
        val currencyCode = dao.getProjectById(investment.projectId)?.currency ?: "INR"

        db.withTransaction {
            // Update investment: reduce currentVal and track withdrawn amount
            val newCurrentVal  = (investment.currentVal  - withdrawAmount).coerceAtLeast(0.0)
            val newWithdrawn   = investment.withdrawn + withdrawAmount
            val newRealized    = investment.realized + gainLoss
            val updated = investment.copy(
                currentVal = newCurrentVal,
                withdrawn  = newWithdrawn,
                realized   = newRealized,
                updatedAt  = System.currentTimeMillis()
            )
            dao.insertInvestment(updated)

            // Credit destination account
            dao.updateAccountBalance(toAccount, withdrawAmount)

            // Create Income transaction for the full withdrawal
            val t = Transaction(
                id        = app.fynlo.logic.Ids.newId(),
                date      = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                type      = "Income",
                amount    = withdrawAmount,
                toAcct    = toAccount,
                category  = "Investment Returns",
                desc      = "Withdrawal from ${investment.name}",
                ref       = investment.id,
                notes     = if (gainLoss >= 0) "Gain: ${CurrencyFormatter.detail(gainLoss, currencyCode)}"
                            else "Loss: ${CurrencyFormatter.detail(-gainLoss, currencyCode)}",
                projectId = investment.projectId,
                updatedAt = System.currentTimeMillis()
            )
            dao.insertTransaction(t)
            recordUndo(
                action = "EDIT",
                entityType = "investment",
                entityId = investment.id,
                title = "Investment withdrawal: ${investment.name}",
                beforeJson = undoJson.encodeToString(
                    InvestmentEditUndoBundle(before = investment, createdTransactions = listOf(t))
                ),
                projectId = investment.projectId,
            )
            sync { setInvestment(updated); setTransaction(t) }
        }
        syncAccountByName(toAccount)
        return gainLoss
    }


    // ─── Restore Defaulted Borrower back to Active ────────────────────────────
    suspend fun restoreBorrowerToActive(borrower: Borrower) {
        val updated = borrower.copy(
            status        = "Active",
            defaultDate   = "",
            frozenInterest = 0.0,
            updatedAt     = System.currentTimeMillis()
        )
        dao.insertBorrower(updated)
        sync { setBorrower(updated) }
    }

    // ─── Mark Borrower as Defaulted ────────────────────────────────────────────
    // Freezes accrued interest at the default date — stops accumulating phantom interest
    suspend fun markBorrowerDefaulted(borrower: Borrower) {
        val today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val frozenInterest = app.fynlo.logic.InterestEngine.calcIntAccrued(
            borrower.amount, borrower.rate, borrower.date, borrower.intType,
            borrower.due, totalPaid = borrower.paidPrincipal, asOf = today
        )
        val updated = borrower.copy(
            status        = "Defaulted",
            defaultDate   = today,
            frozenInterest = frozenInterest,
            updatedAt     = System.currentTimeMillis()
        )
        dao.updateBorrowerDefaultStatus(borrower.id, "Defaulted", today, frozenInterest)
        sync { setBorrower(updated) }
    }

    // ─── Write Off Bad Debt ─────────────────────────────────────────────────────
    // Creates a Bad Debt Expense transaction so it hits your P&L
    suspend fun writeOffBorrower(borrower: Borrower, fromAccount: String = "Personal Cash") {
        val outstanding = if (borrower.status == "Defaulted" && borrower.frozenInterest > 0) {
            // Use frozen interest for defaulted borrowers
            (borrower.amount - borrower.paidPrincipal) + maxOf(0.0, borrower.frozenInterest - borrower.paidInterest - borrower.interestWaived)
        } else {
            val interest = app.fynlo.logic.InterestEngine.calcIntAccrued(
                borrower.amount, borrower.rate, borrower.date, borrower.intType, borrower.due, borrower.paidPrincipal
            )
            (borrower.amount - borrower.paidPrincipal) + maxOf(0.0, interest - borrower.paidInterest - borrower.interestWaived)
        }

        // Look up the project's currency for the persisted desc string.
        // Falls back to INR if the project lookup fails (orphaned borrower, schema race, etc).
        val currencyCode = dao.getProjectById(borrower.projectId)?.currency ?: "INR"

        db.withTransaction {
            val updated = borrower.copy(status = "WrittenOff", updatedAt = System.currentTimeMillis())
            dao.insertBorrower(updated)

            // Bad Debt Expense — shows in P&L (journal entry, no cash movement)
            val t = Transaction(
                id        = app.fynlo.logic.Ids.newId(),
                date      = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                type      = "Expense",
                amount    = outstanding,
                fromAcct  = "", // no actual cash movement
                category  = "Bad Debt",
                desc      = "Write-off: ${borrower.name} — outstanding ${CurrencyFormatter.detail(outstanding, currencyCode)}",
                ref       = borrower.id,
                tags      = "journal_only", // exclude from cash flow
                projectId = borrower.projectId,
                updatedAt = System.currentTimeMillis()
            )
            dao.insertTransaction(t)
            sync { setBorrower(updated); setTransaction(t) }
        }
    }

    fun getPaymentsForLoan(loanId: String) = dao.getPaymentsForLoan(loanId)

    suspend fun waiveBorrowerInterest(borrower: Borrower, amount: Double, reason: String) {
        requireOpenDate(
            java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")),
            borrower.projectId,
        )
        val targetId = borrower.id
        var updated: Borrower? = null
        db.withTransaction {
            val current = dao.getBorrowerById(targetId) ?: return@withTransaction
            val accrued = if (current.status == "Defaulted" && current.frozenInterest > 0.0) {
                current.frozenInterest
            } else {
                app.fynlo.logic.InterestEngine.calcIntAccrued(
                    current.amount,
                    current.rate,
                    current.date,
                    current.intType,
                    current.due,
                    totalPaid = current.paidPrincipal,
                )
            }
            val remainingInterest = (accrued - current.paidInterest - current.interestWaived).coerceAtLeast(0.0)
            val waiver = amount.coerceIn(0.0, remainingInterest)
            if (waiver <= 0.0) return@withTransaction

            val next = current.copy(
                interestWaived = current.interestWaived + waiver,
                updatedAt = System.currentTimeMillis(),
            )
            dao.insertBorrower(next)
            recordAudit(
                action = "WAIVE_INTEREST",
                entityType = "loan",
                entityId = next.id,
                title = "Interest waived: ${next.name}",
                beforeValue = "waived=${current.interestWaived}:interestOutstanding=$remainingInterest",
                afterValue = "waived=${next.interestWaived}:interestOutstanding=${(remainingInterest - waiver).coerceAtLeast(0.0)}",
                amountDelta = 0.0,
                projectId = next.projectId,
                reason = reason.ifBlank { "Interest grace/waiver" },
            )
            updated = next
        }
        updated?.let { sync { setBorrower(it) } }
    }

    suspend fun waiveDebtInterest(debt: Debt, amount: Double, reason: String) {
        requireOpenDate(
            java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")),
            debt.projectId,
        )
        val targetId = debt.id
        var updated: Debt? = null
        db.withTransaction {
            val current = dao.getDebtById(targetId) ?: return@withTransaction
            val accrued = app.fynlo.logic.InterestEngine.calcIntAccrued(
                current.amount,
                current.rate,
                current.date,
                current.intType,
                current.due,
                totalPaid = current.paidPrincipal,
            )
            val remainingInterest = (accrued - current.paidInterest - current.interestWaived).coerceAtLeast(0.0)
            val waiver = amount.coerceIn(0.0, remainingInterest)
            if (waiver <= 0.0) return@withTransaction

            val next = current.copy(
                interestWaived = current.interestWaived + waiver,
                updatedAt = System.currentTimeMillis(),
            )
            dao.insertDebt(next)
            recordAudit(
                action = "WAIVE_INTEREST",
                entityType = "debt",
                entityId = next.id,
                title = "Debt interest waived: ${next.name}",
                beforeValue = "waived=${current.interestWaived}:interestOutstanding=$remainingInterest",
                afterValue = "waived=${next.interestWaived}:interestOutstanding=${(remainingInterest - waiver).coerceAtLeast(0.0)}",
                amountDelta = 0.0,
                projectId = next.projectId,
                reason = reason.ifBlank { "Interest grace/waiver" },
            )
            updated = next
        }
        updated?.let { sync { setDebt(it) } }
    }

    /**
     * Pushes ALL local Room data to Firestore.
     * Called on sign-in to ensure data written offline reaches the cloud.
     * Safe to call multiple times — Firestore SET is idempotent.
     */
    /**
     * 3.2.74 — wipe Firestore for this user and push local state as the
     * new canonical version. Use when stale cloud data is "restoring"
     * itself into local on every sync (e.g. previous testing session left
     * non-matching values in the cloud). After this completes, cloud and
     * local are identical and the listener-overwrite churn stops.
     *
     * Local data is NOT touched. The push uses the current local rows as
     * the source of truth — anything that exists locally goes up; anything
     * that doesn't is implicitly absent from cloud after the wipe.
     *
     * Caller is responsible for showing a confirm dialog — this is
     * destructive on the cloud side.
     */
    suspend fun resetCloudSyncToLocal() {
        // Stop the listener BEFORE wiping so we don't race the delete with
        // a re-attach that pulls the docs we're about to delete back into
        // local. Same pattern as `resetAllData`'s wipe sequence.
        runCatching { syncManager.stopListening() }
        val uid = syncManager.userId
        if (uid.isNotBlank()) {
            deleteFirestoreUserTree(
                com.google.firebase.firestore.FirebaseFirestore.getInstance(), uid
            )
        }
        // Push current local as the new canonical cloud state.
        pushAllLocalToFirestore()
        // Resume listening — cloud is now in sync with local so the next
        // initial snapshot is a no-op.
        runCatching { syncManager.startListening() }
    }

    suspend fun pushAllLocalToFirestore() {
        val uid = syncManager.userId
        if (uid.isEmpty()) return
        val fs = firestore
        var pushed = 0
        var failed = 0

        suspend fun push(label: String, block: suspend () -> Unit) {
            runCatching { block() }
                .onSuccess { pushed++ }
                .onFailure { e ->
                    failed++
                    android.util.Log.e("FynloSync", "Push failed [$label]: ${e.message}")
                }
        }

        dao.getAllBorrowers().first().forEach    { push("borrower:${it.id}")    { fs.setBorrower(it) } }
        dao.getAllTransactions().first().forEach { push("txn:${it.id}")         { fs.setTransaction(it) } }
        dao.getAllAccounts().first().forEach     { push("account:${it.id}")     { fs.setAccount(it) } }
        dao.getAllInvestments().first().forEach  { push("investment:${it.id}")  { fs.setInvestment(it) } }
        dao.getAllDebts().first().forEach        { push("debt:${it.id}")        { fs.setDebt(it) } }
        dao.getAllPayments().first().forEach     { push("payment:${it.id}")     { fs.setPayment(it) } }
        dao.getAllDebtPayments().first().forEach { push("debtPay:${it.id}")     { fs.setDebtPayment(it) } }
        dao.getAllPeople().first().forEach       { push("person:${it.id}")      { fs.setPerson(it) } }
        dao.getAllBudgets().first().forEach      { push("budget:${it.category}") { fs.setBudget(it) } }
        dao.getAllGoals().first().forEach        { push("goal:${it.id}")        { fs.setGoal(it) } }
        dao.getAllProjects().first().forEach     { push("project:${it.id}")     { fs.setProject(it) } }
        dao.getAllRecurringTransactionsOnce().forEach { push("recurring:${it.id}") { fs.setRecurring(it) } }
        dao.getAllValuationsOnce().forEach       { push("valuation:${it.id}")   { fs.setValuation(it) } }
        dao.getAllAuditEventsOnce().forEach      { push("audit:${it.id}")       { fs.setAuditEvent(it) } }

        if (failed == 0) syncManager.setSynced() else syncManager.setSyncing()
    }

    /**
     * Normalizes all legacy records (empty or "personal" projectId) to the
     * real project UUID. Called once on startup after projects are loaded.
     */
    suspend fun normalizeLegacyProjectIds(realProjectId: String) {
        db.withTransaction {
            dao.normalizeAccountProjectIds(realProjectId)
            dao.normalizeTransactionProjectIds(realProjectId)
            dao.normalizeBorrowerProjectIds(realProjectId)
            dao.normalizeInvestmentProjectIds(realProjectId)
            dao.normalizeDebtProjectIds(realProjectId)
            dao.normalizePeopleProjectIds(realProjectId)
            dao.normalizePaymentProjectIds(realProjectId)
            dao.normalizeDebtPaymentProjectIds(realProjectId)
            dao.normalizeBudgetProjectIds(realProjectId)
            dao.normalizeGoalProjectIds(realProjectId)
        }
    }

    /**
     * Push all local accounts to Firestore — fixes accounts missing from cloud.
     */
    suspend fun pushAllAccountsToFirestore() {
        val accounts = dao.getAllAccounts().first()
        accounts.forEach { account ->
            runCatching { firestore.setAccount(account) }
        }
    }

    /**
     * After normalizeLegacyProjectIds runs, push ALL collections to Firestore
     * so every device gets correct projectIds, not the legacy empty/"personal" ones.
     */
    fun getNetWorthSnapshots(pid: String) = dao.getNetWorthSnapshots(pid)
    suspend fun saveNetWorthSnapshot(s: app.fynlo.data.model.NetWorthSnapshot) = dao.insertNetWorthSnapshot(s)

    fun getAllRecurringTransactions() = dao.getAllRecurringTransactions()
    suspend fun insertRecurringTransaction(r: app.fynlo.data.model.RecurringTransaction) {
        // C03b Stage #1c (3.2.89) — resolve account names to ids at write
        // time, mirroring the Transaction insertTransaction path. Future
        // RecurringWorker auto-fires will use the stored ids for
        // rename-safe balance updates.
        val fromId = if (r.fromAcct.isEmpty()) null else dao.getAccountByName(r.fromAcct)?.id
        val toId   = if (r.toAcct.isEmpty())   null else dao.getAccountByName(r.toAcct)?.id
        val resolved = r.resolveAccountIdsWith { name ->
            when (name) {
                r.fromAcct -> fromId
                r.toAcct   -> toId
                else       -> null
            }
        }
        val rec = resolved.copy(updatedAt = System.currentTimeMillis())
        dao.insertRecurringTransaction(rec); sync { setRecurring(rec) }
    }
    suspend fun deleteRecurringTransaction(r: app.fynlo.data.model.RecurringTransaction) {
        dao.deleteRecurringTransaction(r); sync { deleteRecurring(r.id) }
    }

    suspend fun pushAllCollectionsToFirestore() {
        val accounts      = dao.getAllAccounts().first()
        val transactions  = dao.getAllTransactions().first()
        val borrowers     = dao.getAllBorrowers().first()
        val investments   = dao.getAllInvestments().first()
        val debts         = dao.getAllDebts().first()
        val people        = dao.getAllPeople().first()
        accounts.forEach     { runCatching { firestore.setAccount(it) } }
        transactions.forEach { runCatching { firestore.setTransaction(it) } }
        borrowers.forEach    { runCatching { firestore.setBorrower(it) } }
        investments.forEach  { runCatching { firestore.setInvestment(it) } }
        debts.forEach        { runCatching { firestore.setDebt(it) } }
        people.forEach       { runCatching { firestore.setPerson(it) } }
    }

    /**
     * After normalization, push ONLY projectId-changed records back.
     * Safer than a full push — only updates what normalization changed.
     */
    suspend fun pushNormalizedProjectIds() {
        val accounts     = dao.getAllAccounts().first()
        val borrowers    = dao.getAllBorrowers().first()
        val transactions = dao.getAllTransactions().first()
        val debts        = dao.getAllDebts().first()
        val investments  = dao.getAllInvestments().first()
        // Only push records that have a non-personal projectId (actually normalized)
        accounts.filter    { it.projectId != "personal" }.forEach { runCatching { firestore.setAccount(it) } }
        borrowers.filter   { it.projectId != "personal" }.forEach { runCatching { firestore.setBorrower(it) } }
        transactions.filter{ it.projectId != "personal" }.forEach { runCatching { firestore.setTransaction(it) } }
        debts.filter       { it.projectId != "personal" }.forEach { runCatching { firestore.setDebt(it) } }
        investments.filter { it.projectId != "personal" }.forEach { runCatching { firestore.setInvestment(it) } }
    }

    /**
     * Takes a daily backup snapshot to Firestore.
     * Never backs up if accounts are empty. Runs silently once per day.
     */
    suspend fun takeBackupIfNeeded(uid: String) {
        if (uid.isBlank()) return
        try {
            val accounts = dao.getAllAccounts().first()
            if (accounts.isEmpty()) return

            val today   = java.time.LocalDate.now().toString()
            val fs      = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val metaRef = fs.collection("users").document(uid)
                           .collection("backup_meta").document("last_backup")

            val lastBackup = try { metaRef.get().await().getString("date") ?: "" }
                            catch (e: Exception) { "" }
            if (lastBackup == today) return

            val borrowers    = try { dao.getAllBorrowers().first() }   catch (e: Exception) { emptyList() }
            val transactions = try { dao.getAllTransactions().first() } catch (e: Exception) { emptyList() }
            val debts        = try { dao.getAllDebts().first() }        catch (e: Exception) { emptyList() }
            val investments  = try { dao.getAllInvestments().first() }  catch (e: Exception) { emptyList() }

            val backupRef = fs.collection("users").document(uid)
                             .collection("backups").document(today)

            // Write ONLY the summary document — no sub-collections
            // Sub-collections with dynamic IDs risk empty-ID crashes
            // The real data is already safe in Firestore main collections
            backupRef.set(mapOf(
                "date"          to today,
                "netWorth"      to accounts.sumOf { it.balance },
                "accountCount"  to accounts.size,
                "txnCount"      to transactions.size,
                "borrowerCount" to borrowers.size,
                "debtCount"     to debts.size,
                "investCount"   to investments.size,
                "accountNames"  to accounts.map { it.name },
                "accountBals"   to accounts.map { it.balance },
                "createdAt"     to System.currentTimeMillis()
            )).await()

            // Mark as backed up
            metaRef.set(mapOf("date" to today)).await()

        } catch (e: Exception) {
            android.util.Log.e("Backup", "takeBackupIfNeeded failed: ${e.message}", e)
        }
    }

    /**
     * Permanent Wipe: Deletes ALL data for this user from both Room and Firestore.
     * This ensures a completely clean state for testing.
     */
    suspend fun wipeAllData() {
        val uid = syncManager.userId
        if (uid.isBlank()) return
        val fs = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val userDoc = fs.collection("users").document(uid)

        val collections = listOf(
            "accounts", "transactions", "borrowers", "investments", "debts",
            "people", "payments", "debt_payments", "budgets", "goals",
            "projects", "recurring_transactions", "backup_meta", "backups",
            "net_worth_snapshots", "investment_valuations", "audit_events"
        )

        db.withTransaction {
            // 1. Wipe Room — every table, so no orphaned rows survive a wipe
            dao.deleteAllAccounts()
            dao.deleteAllTransactions()
            dao.deleteAllBorrowers()
            dao.deleteAllPayments()
            dao.deleteAllInvestments()
            dao.deleteAllDebts()
            dao.deleteAllDebtPayments()
            dao.deleteAllPeople()
            dao.deleteAllProjects()
            dao.deleteAllBudgets()
            dao.deleteAllGoals()
            dao.deleteAllAuditEvents()
            dao.deleteAllDeletedRemoteDocs()
            dao.deleteAllMonthlyCloses()
            dao.deleteAllProofAttachments()
            dao.deleteAllUndoActions()
            dao.deleteAllSyncConflicts()
            dao.deleteAllValuations()
            dao.deleteAllRecurringTransactions()
        }

        // 2. Wipe Firestore (Collection by Collection)
        collections.forEach { colName ->
            try {
                val snapshot = userDoc.collection(colName).get().await()
                snapshot.documents.forEach { it.reference.delete().await() }
            } catch (e: Exception) {
                android.util.Log.e("Wipe", "Failed to wipe $colName: ${e.message}")
            }
        }
    }

    /**
     * Reset All Data — the user-facing "start completely fresh" wipe.
     *
     * Deletes the user's entire Firestore document tree (while we still hold a
     * uid), then clears every Room table. If the per-table DAO clear throws for
     * any reason, falls back to deleting the underlying database file so the app
     * still relaunches from a truly empty state. [context] is needed only for
     * that deleteDatabase fallback.
     */
    suspend fun resetAllData(context: android.content.Context) {
        // 0. Stop Firestore listeners FIRST. Several collection listeners
        //    (accounts, investments, debts, people, projects) have no REMOVED
        //    handler and re-insert from the change document on every event — if
        //    left running they would race the wipe and re-add the docs we delete
        //    below back into Room. Stopping them makes the wipe deterministic.
        runCatching { syncManager.stopListening() }

        // 1. Firestore — delete the whole user tree before auth is cleared.
        //    Logic lives in deleteFirestoreUserTree so it can be exercised
        //    against the Firebase emulator by FirestoreResetTest.
        val uid = syncManager.userId
        if (uid.isNotBlank()) {
            deleteFirestoreUserTree(
                com.google.firebase.firestore.FirebaseFirestore.getInstance(), uid
            )
        }

        // 2. Room — clear every table; on any failure, drop the whole DB file.
        try {
            db.withTransaction {
                dao.deleteAllAccounts()
                dao.deleteAllTransactions()
                dao.deleteAllBorrowers()
                dao.deleteAllPayments()
                dao.deleteAllInvestments()
                dao.deleteAllDebts()
                dao.deleteAllDebtPayments()
                dao.deleteAllPeople()
                dao.deleteAllProjects()
                dao.deleteAllBudgets()
                dao.deleteAllGoals()
                dao.deleteAllAuditEvents()
                dao.deleteAllDeletedRemoteDocs()
                dao.deleteAllMonthlyCloses()
                dao.deleteAllProofAttachments()
                dao.deleteAllUndoActions()
                dao.deleteAllSyncConflicts()
                dao.deleteAllValuations()
                dao.deleteAllRecurringTransactions()
                dao.deleteAllNetWorthSnapshots()
                dao.deleteAllFlowTemplates()
            }
        } catch (e: Exception) {
            android.util.Log.e("Reset", "DAO clear failed — deleting database file: ${e.message}")
            runCatching { db.close() }
            runCatching { context.deleteDatabase("Fynlo_database") }
        }
    }


    /**
     * Builds a v2 (C03a) backup JSON: data + metadata + SHA-256
     * integrity hash. Caller passes [userId] (Firebase auth UID; empty
     * if signed out) so the repository stays Context-free.
     */
    suspend fun getAllDataAsJson(userId: String = ""): String {
        val draft = BackupData(
            schemaVersion = BackupIntegrity.CURRENT_SCHEMA_VERSION,
            appVersion    = app.fynlo.BuildConfig.VERSION_NAME,
            exportedAt    = java.time.Instant.now().toString(),
            userId        = userId,
            deviceName    = android.os.Build.MODEL ?: "",
            contentHash   = "",   // populated below after canonical-form hash
            accounts              = dao.getAllAccounts().first(),
            transactions          = dao.getAllTransactions().first(),
            borrowers             = dao.getAllBorrowers().first(),
            investments           = dao.getAllInvestments().first(),
            debts                 = dao.getAllDebts().first(),
            people                = dao.getAllPeople().first(),
            projects              = dao.getAllProjects().first(),
            payments              = dao.getAllPayments().first(),
            debtPayments          = dao.getAllDebtPayments().first(),
            budgets               = dao.getAllBudgets().first(),
            goals                 = dao.getAllGoals().first(),
            recurringTransactions = dao.getAllRecurringTransactionsOnce(),
            monthlyCloses         = dao.getAllMonthlyCloses().first(),
            proofAttachments      = dao.getAllProofAttachments().first(),
        )
        val hash = BackupIntegrity.computeHash(draft)
        return Json.encodeToString(draft.copy(contentHash = hash))
    }

    /**
     * Restores from a v1 or v2 backup. Throws on:
     *  - `IllegalStateException` for an unsupported (newer) backup format
     *    — caller should surface "please update Fynlo Ledger".
     *  - `IllegalStateException` for a hash mismatch on a v2 backup
     *    — file is corrupted or modified; do not restore.
     *
     * If the integrity check passes, the existing data is wiped and the
     * backup is loaded atomically inside `db.withTransaction`.
     */
    suspend fun restoreDataFromJson(json: String) {
        val raw = Json.decodeFromString<BackupData>(json)
        when (val verdict = BackupIntegrity.check(raw)) {
            is BackupIntegrity.Check.Ok -> { /* fall through to restore */ }
            is BackupIntegrity.Check.UnsupportedVersion ->
                throw IllegalStateException(
                    "Backup format v${verdict.version} is newer than this " +
                    "app supports (max v${BackupIntegrity.CURRENT_SCHEMA_VERSION}). " +
                    "Update Fynlo Ledger and try again."
                )
            is BackupIntegrity.Check.HashMismatch ->
                throw IllegalStateException(
                    "Backup integrity check failed (SHA-256 hash mismatch). " +
                    "The file may be corrupted or modified — not restoring."
                )
        }
        // 3.2.77 — same sweep the v20→v21 / v21→v22 migrations apply to the
        // live DB, but at the restore boundary. A user importing a backup
        // exported before 3.2.75 would otherwise re-introduce "Cash in Hand"
        // and un-do the rename — the migration only fires on schema version
        // bump, not on restore-replace.
        val data = sanitizeLegacyCashName(raw)
        db.withTransaction {
            // Clear everything first so a restore is a true replace, not a merge.
            dao.deleteAllAccounts(); dao.deleteAllTransactions(); dao.deleteAllBorrowers()
            dao.deleteAllInvestments(); dao.deleteAllDebts(); dao.deleteAllPeople(); dao.deleteAllProjects()
            dao.deleteAllPayments(); dao.deleteAllDebtPayments(); dao.deleteAllBudgets(); dao.deleteAllGoals()
            dao.deleteAllAuditEvents()
            dao.deleteAllMonthlyCloses(); dao.deleteAllProofAttachments(); dao.deleteAllUndoActions(); dao.deleteAllSyncConflicts()
            data.accounts.forEach { dao.insertAccount(it) }; data.transactions.forEach { dao.insertTransaction(it) }
            data.borrowers.forEach { dao.insertBorrower(it) }; data.investments.forEach { dao.insertInvestment(it) }
            data.debts.forEach { dao.insertDebt(it) }; data.people.forEach { dao.insertPerson(it) }
            data.projects.forEach { dao.insertProject(it) }
            data.payments.forEach { dao.insertPayment(it) }; data.debtPayments.forEach { dao.insertDebtPayment(it) }
            data.budgets.forEach { dao.insertBudget(it) }; data.goals.forEach { dao.insertGoal(it) }
            data.recurringTransactions.forEach { dao.insertRecurringTransaction(it) }
            data.monthlyCloses.forEach { dao.insertMonthlyClose(it) }
            data.proofAttachments.forEach { dao.insertProofAttachment(it) }
            recordAudit(
                action = "RESTORE",
                entityType = "backup",
                entityId = "restore",
                title = "Backup restored",
                afterValue = "accounts=${data.accounts.size}:transactions=${data.transactions.size}:loans=${data.borrowers.size}:debts=${data.debts.size}:investments=${data.investments.size}",
                projectId = data.projects.firstOrNull()?.id ?: "personal",
            )
        }
    }

    /**
     * 3.2.77 — rewrite the legacy "Cash in Hand" string to "Personal Cash"
     * everywhere it appears as an account-name key inside a [BackupData].
     * Mirrors [app.fynlo.data.local.MIGRATION_21_22]'s field set so backup
     * restore stays consistent with the live-DB rename.
     *
     * Pure function on a value type — no DAO calls — easy to unit-test
     * if we want. Idempotent.
     */
    private fun sanitizeLegacyCashName(input: BackupData): BackupData {
        val OLD = "Cash in Hand"
        val NEW = "Personal Cash"
        if (!input.accounts.any { it.name == OLD } &&
            !input.transactions.any { it.fromAcct == OLD || it.toAcct == OLD } &&
            !input.borrowers.any { it.sourceAccount == OLD } &&
            !input.recurringTransactions.any { it.fromAcct == OLD || it.toAcct == OLD }) {
            return input  // fast-path no-op for post-3.2.75 backups
        }
        return input.copy(
            accounts              = input.accounts.map { if (it.name == OLD) it.copy(name = NEW) else it },
            transactions          = input.transactions.map { t ->
                t.copy(
                    fromAcct = if (t.fromAcct == OLD) NEW else t.fromAcct,
                    toAcct   = if (t.toAcct   == OLD) NEW else t.toAcct,
                )
            },
            borrowers             = input.borrowers.map { if (it.sourceAccount == OLD) it.copy(sourceAccount = NEW) else it },
            recurringTransactions = input.recurringTransactions.map { r ->
                r.copy(
                    fromAcct = if (r.fromAcct == OLD) NEW else r.fromAcct,
                    toAcct   = if (r.toAcct   == OLD) NEW else r.toAcct,
                )
            },
        )
    }
}
