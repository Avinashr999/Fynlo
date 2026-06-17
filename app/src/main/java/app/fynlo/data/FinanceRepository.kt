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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class FinanceRepository(
    val dao: FynloDao,
    private val db: FynloDatabase,
    private var firestore: FirestoreRepository,
    var syncManager: SyncManager
) {
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
    private val ioScope = CoroutineScope(Dispatchers.IO)
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

    private suspend fun tombstoneRemoteDoc(collection: String, id: String) {
        if (id.isNotBlank()) {
            dao.insertDeletedRemoteDoc(DeletedRemoteDoc(collection, id))
        }
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
        val before = dao.getBorrowerById(borrower.id)
        val b = borrower.copy(updatedAt = System.currentTimeMillis())
        dao.insertBorrower(b)
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
        sync { setBorrower(b) }
    }

    suspend fun updateDebt(debt: Debt) {
        val before = dao.getDebtById(debt.id)
        val d = debt.copy(updatedAt = System.currentTimeMillis())
        dao.insertDebt(d)
        recordAudit(
            action = "EDIT",
            entityType = "debt",
            entityId = d.id,
            title = "Debt edited: ${d.name}",
            beforeValue = before?.let { "${it.name}:${it.amount}:paid=${it.paid}:status=${it.status}" } ?: "",
            afterValue = "${d.name}:${d.amount}:paid=${d.paid}:status=${d.status}",
            amountDelta = d.amount - (before?.amount ?: d.amount),
            projectId = d.projectId,
        )
        sync { setDebt(d) }
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
            dao.insertBorrower(b)
            dao.updateAccountBalance(sourceAccount, -borrower.amount)
            val t = Transaction(app.fynlo.logic.Ids.newId(), borrower.date, "Expense", borrower.amount, fromAcct = sourceAccount, category = "Lending", desc = "Lent to ${borrower.name}", ref = borrower.id, notes = borrower.notes, projectId = projectId, updatedAt = now, createdAt = now)
            dao.insertTransaction(t)
            recordAudit(
                action = "CREATE",
                entityType = "loan",
                entityId = b.id,
                title = "Loan created: ${b.name}",
                afterValue = "lent=${b.amount}:from=$sourceAccount:txn=${t.id}",
                amountDelta = -b.amount,
                accountName = sourceAccount,
                projectId = projectId,
            )
            sync { setBorrower(b); setTransaction(t) }
        }
        Analytics.loanCreated(hasInterest = borrower.rate > 0.0)
        syncAccountByName(sourceAccount)
    }
    suspend fun deleteBorrower(borrower: Borrower) {
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

        /** Directly set account balance (for corrections). Creates a balancing transaction. */
    suspend fun quickEditBalance(accountName: String, newBalance: Double, oldBalance: Double) {
        db.withTransaction {
            val diff = newBalance - oldBalance
            dao.updateAccountBalance(accountName, diff)
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
                fromAcct = if (diff < 0) accountName else "",
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
            sync { setInvestment(i); setTransaction(t) }
        }
        Analytics.investmentCreated(type = investment.type)
        syncAccountByName(accountName)
    }

    // ─── Investment — funded by existing recorded debt ──────────────────────────
    suspend fun insertInvestmentFundedByExistingDebt(investment: Investment, debt: app.fynlo.data.model.Debt, projectId: String = investment.projectId) {
        db.withTransaction {
            if (dao.getInvestmentById(investment.id) != null) return@withTransaction
            val i = investment.copy(sourceType = "existing_debt", fundingSource = debt.name, linkedDebtId = debt.id, projectId = projectId, updatedAt = System.currentTimeMillis(), createdAt = if (investment.createdAt == 0L) System.currentTimeMillis() else investment.createdAt)
            dao.insertInvestment(i)
            val t = Transaction(app.fynlo.logic.Ids.newId(), investment.date, "Transfer", investment.invested,
                fromAcct = debt.name, toAcct = investment.name, category = "Investment",
                desc = "Invested in ${investment.name} using ${debt.name} loan funds",
                notes = investment.notes, projectId = projectId, updatedAt = System.currentTimeMillis())
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
            sync { setInvestment(i); setTransaction(t) }
        }
        Analytics.investmentCreated(type = investment.type)
    }

    // ─── Investment — auto-create new loan + link to investment ─────────────────
    suspend fun insertInvestmentFundedByNewLoan(investment: Investment, newDebt: app.fynlo.data.model.Debt, projectId: String = investment.projectId) {
        db.withTransaction {
            if (dao.getInvestmentById(investment.id) != null) return@withTransaction
            val d = newDebt.copy(projectId = projectId, updatedAt = System.currentTimeMillis())
            dao.insertDebt(d)
            val i = investment.copy(sourceType = "new_loan", fundingSource = d.name, linkedDebtId = d.id, projectId = projectId, updatedAt = System.currentTimeMillis(), createdAt = if (investment.createdAt == 0L) System.currentTimeMillis() else investment.createdAt)
            dao.insertInvestment(i)
            val projectCurrency = dao.getProjectById(projectId)?.currency ?: "INR"
            val investedText = app.fynlo.logic.CurrencyFormatter.detail(investment.invested, projectCurrency)
            val t = Transaction(app.fynlo.logic.Ids.newId(), investment.date, "Transfer", investment.invested,
                fromAcct = d.name, toAcct = investment.name, category = "Investment",
                desc = "Invested $investedText in ${investment.name} via ${d.name} loan",
                notes = investment.notes, projectId = projectId, updatedAt = System.currentTimeMillis())
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
        var didDelete = false
        db.withTransaction {
            val current = dao.getInvestmentById(investment.id) ?: return@withTransaction
            // Remove the investment transaction that moved money out of an account
            val invTxn = dao.getTransactionsByRef(current.id)
                .firstOrNull { it.category == "Investment" }
            if (invTxn != null) {
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
        var deletedInvestment: Investment? = null
        db.withTransaction {
            val current = dao.getInvestmentById(investment.id) ?: return@withTransaction
            // Delete the investment transaction
            val invTxn = dao.getTransactionsByRef(current.id)
                .firstOrNull { it.category == "Investment" }
            if (invTxn != null) {
                dao.deleteTransaction(invTxn)
                tombstoneRemoteDoc("transactions", invTxn.id)
                sync { deleteTransaction(invTxn.id) }
            }
            // Delete the linked debt and its transactions
            if (current.linkedDebtId.isNotEmpty()) {
                val linkedDebt = dao.getDebtById(current.linkedDebtId)
                if (linkedDebt != null) {
                    val debtTxn = dao.getTransactionsByRef(current.linkedDebtId)
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

    suspend fun executeLinkedInvestment(
        investment: Investment,
        fundingSourceType: String, // "Account", "Debt", "Already Settled"
        sourceName: String,
        debtDetails: Debt? = null
    ) {
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
                    sync { setTransaction(t) }
                    syncAccountByName(sourceName)
                }
                "Debt" -> {
                    // Record a new Debt liability
                    debtDetails?.let {
                        val d = it.copy(updatedAt = System.currentTimeMillis())
                        dao.insertDebt(d)
                        val t = Transaction(
                            id = app.fynlo.logic.Ids.newId(),
                            date = investment.date,
                            type = "Transfer", // Debt -> Investment is a balance sheet move
                            amount = investment.invested,
                            fromAcct = "Loan: ${d.name}",
                            toAcct = "Investment: ${investment.name}",
                            category = "Debt",
                            desc = "Investment funded by loan from ${d.name}",
                            ref = d.id,
                            projectId = pid,
                            updatedAt = System.currentTimeMillis()
                        )
                        dao.insertTransaction(t)
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
            dao.updateAccountBalance(destinationAccount, debt.amount)
            val t = Transaction(app.fynlo.logic.Ids.newId(), debt.date, "Income", debt.amount, toAcct = destinationAccount, category = "Debt Received", desc = "Loan received from ${debt.name}", ref = d.id, notes = debt.notes, projectId = projectId, updatedAt = now, createdAt = now)
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
            sync { setDebt(d); setTransaction(t) }
        }
        syncAccountByName(destinationAccount)
    }
    suspend fun deleteDebt(debt: Debt) {
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
            sync {
                setPayment(p)
                setTransaction(t)
                updatedBorrower?.let { setBorrower(it) }
            }
        }
        syncAccountByName(destinationAccount)
    }
    suspend fun insertDebtPaymentWithSource(payment: DebtPayment, sourceAccount: String, projectId: String = payment.projectId) {
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
            (borrower.amount - borrower.paidPrincipal) + maxOf(0.0, borrower.frozenInterest - borrower.paidInterest)
        } else {
            val interest = app.fynlo.logic.InterestEngine.calcIntAccrued(
                borrower.amount, borrower.rate, borrower.date, borrower.intType, borrower.due, borrower.paidPrincipal
            )
            (borrower.amount - borrower.paidPrincipal) + maxOf(0.0, interest - borrower.paidInterest)
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
            recurringTransactions = dao.getAllRecurringTransactionsOnce()
        )
        val hash = BackupIntegrity.computeHash(draft)
        return Json.encodeToString(draft.copy(contentHash = hash))
    }

    /**
     * Restores from a v1 or v2 backup. Throws on:
     *  - `IllegalStateException` for an unsupported (newer) backup format
     *    — caller should surface "please update Fynlo".
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
                    "Update Fynlo and try again."
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
            data.accounts.forEach { dao.insertAccount(it) }; data.transactions.forEach { dao.insertTransaction(it) }
            data.borrowers.forEach { dao.insertBorrower(it) }; data.investments.forEach { dao.insertInvestment(it) }
            data.debts.forEach { dao.insertDebt(it) }; data.people.forEach { dao.insertPerson(it) }
            data.projects.forEach { dao.insertProject(it) }
            data.payments.forEach { dao.insertPayment(it) }; data.debtPayments.forEach { dao.insertDebtPayment(it) }
            data.budgets.forEach { dao.insertBudget(it) }; data.goals.forEach { dao.insertGoal(it) }
            data.recurringTransactions.forEach { dao.insertRecurringTransaction(it) }
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
