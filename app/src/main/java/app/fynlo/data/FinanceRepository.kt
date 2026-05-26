package app.fynlo.data

import app.fynlo.data.local.FynloDao
import app.fynlo.data.local.FynloDatabase
import app.fynlo.data.model.*
import app.fynlo.data.remote.FirestoreRepository
import app.fynlo.data.remote.SyncManager
import app.fynlo.data.remote.deleteFirestoreUserTree
import app.fynlo.logic.CurrencyFormatter
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
    suspend fun insertTransaction(transaction: Transaction) {
        // C03a Stage 2: scrub forbidden literal categories ("Expense" / "Income"
        // / "Transfer" — those are types, not categories) before persisting.
        val sanitized = TransactionValidator.sanitize(transaction)
        val affectedAccounts = mutableListOf<String>()
        db.withTransaction {
            val now = System.currentTimeMillis()
            val t = sanitized.copy(updatedAt = now, createdAt = if (sanitized.createdAt == 0L) now else sanitized.createdAt)
            dao.insertTransaction(t)
            when (transaction.type.lowercase()) {
                "expense"  -> { dao.updateAccountBalance(transaction.fromAcct, -transaction.amount); affectedAccounts += transaction.fromAcct }
                "income"   -> { dao.updateAccountBalance(transaction.toAcct, transaction.amount); affectedAccounts += transaction.toAcct }
                "transfer" -> { dao.updateAccountBalance(transaction.fromAcct, -transaction.amount); dao.updateAccountBalance(transaction.toAcct, transaction.amount); affectedAccounts += transaction.fromAcct; affectedAccounts += transaction.toAcct }
            }
            sync { setTransaction(t) }
        }
        Analytics.transactionAdded(type = transaction.type, category = transaction.category)
        // Sync AFTER withTransaction commits so we push the updated balance
        affectedAccounts.forEach { syncAccountByName(it) }
    }
    suspend fun editTransaction(old: Transaction, newRaw: Transaction) {
        // C03a Stage 2: scrub forbidden literal categories from the new
        // value before applying the edit. `old` is read-only — its
        // category is just used for the old-side Payment-row lookup and
        // doesn't get re-written, so no sanitization needed there.
        val new = TransactionValidator.sanitize(newRaw)
        db.withTransaction {
            // 1. Reverse old transaction effect
            when (old.type.lowercase()) {
                "expense"  -> dao.updateAccountBalance(old.fromAcct,  old.amount)
                "income"   -> dao.updateAccountBalance(old.toAcct,   -old.amount)
                "transfer" -> {
                    dao.updateAccountBalance(old.fromAcct,  old.amount)
                    dao.updateAccountBalance(old.toAcct,   -old.amount)
                }
            }

            // 2. Apply new transaction effect
            when (new.type.lowercase()) {
                "expense"  -> dao.updateAccountBalance(new.fromAcct, -new.amount)
                "income"   -> dao.updateAccountBalance(new.toAcct,    new.amount)
                "transfer" -> {
                    dao.updateAccountBalance(new.fromAcct, -new.amount)
                    dao.updateAccountBalance(new.toAcct,    new.amount)
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
                    sync { deletePayment(matching.id) }
                }
                touchedBorrowers += old.ref
            }
            if (new.category == "Loan Repayment" && new.ref.isNotBlank()) {
                val borrower = dao.getBorrowerById(new.ref)
                if (borrower != null) {
                    val now = System.currentTimeMillis()
                    val p = Payment(
                        id        = java.util.UUID.randomUUID().toString(),
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
                    sync { deleteDebtPayment(matching.id) }
                }
                touchedDebts += old.ref
            }
            if (new.category == "Debt Repayment" && new.ref.isNotBlank()) {
                val debt = dao.getDebtById(new.ref)
                if (debt != null) {
                    val now = System.currentTimeMillis()
                    val p = DebtPayment(
                        id        = java.util.UUID.randomUUID().toString(),
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
        db.withTransaction {
            // Guard: only reverse balance if account name is non-blank
            when (transaction.type.lowercase()) {
                "expense"  -> if (transaction.fromAcct.isNotBlank())
                                  dao.updateAccountBalance(transaction.fromAcct,  transaction.amount)
                "income"   -> if (transaction.toAcct.isNotBlank())
                                  dao.updateAccountBalance(transaction.toAcct,   -transaction.amount)
                "transfer" -> {
                    if (transaction.fromAcct.isNotBlank()) dao.updateAccountBalance(transaction.fromAcct,  transaction.amount)
                    if (transaction.toAcct.isNotBlank())   dao.updateAccountBalance(transaction.toAcct,   -transaction.amount)
                }
            }
            android.util.Log.d("FynloDelete", "deleteTransaction: type=${transaction.type} " +
                "from='${transaction.fromAcct}' to='${transaction.toAcct}' " +
                "cat=${transaction.category} amount=${transaction.amount}")

            // Handle payment reversals if transaction belongs to a loan/debt.
            // IMPORTANT: also delete the Payment record from payments table so that
            // rebuildBorrowerPaidFromPayments() recalculates correctly on next startup.
            if (transaction.category == "Loan Repayment" && transaction.ref.isNotBlank()) {
                // Find and delete the matching payment record (same loanId, amount, date).
                // If no matching Payment exists, `paid` is left as-is to preserve
                // the invariant paid == SUM(payments). (Pre-C01 this branch
                // reversed `paid` directly via updateBorrowerPaidAmount, which
                // broke the invariant — see decisions/2026-05-26-c01-fix-strategy.md
                // Stage 2.)
                val matchingPayment = dao.getPaymentsForLoanOnce(transaction.ref)
                    .filter { it.amount == transaction.amount && it.date == transaction.date }
                    .maxByOrNull { it.updatedAt }
                if (matchingPayment != null) {
                    dao.deletePayment(matchingPayment)
                    sync { deletePayment(matchingPayment.id) }
                }
                dao.rebuildBorrowerPaidFromPayments()
                val b = dao.getBorrowerById(transaction.ref)
                sync { b?.let { setBorrower(it) } }
            } else if (transaction.category == "Debt Repayment" && transaction.ref.isNotBlank()) {
                val matchingPayment = dao.getDebtPaymentsForDebtOnce(transaction.ref)
                    .filter { it.amount == transaction.amount && it.date == transaction.date }
                    .maxByOrNull { it.updatedAt }
                if (matchingPayment != null) {
                    dao.deleteDebtPayment(matchingPayment)
                    sync { deleteDebtPayment(matchingPayment.id) }
                }
                dao.rebuildDebtPaidFromDebtPayments()
                val d = dao.getDebtById(transaction.ref)
                sync { d?.let { setDebt(it) } }
            }

            dao.deleteTransaction(transaction)
        }
        // Sync reversed account balances AFTER withTransaction commits
        when (transaction.type.lowercase()) {
            "expense"  -> if (transaction.fromAcct.isNotBlank()) syncAccountByName(transaction.fromAcct)
            "income"   -> if (transaction.toAcct.isNotBlank())   syncAccountByName(transaction.toAcct)
            "transfer" -> {
                if (transaction.fromAcct.isNotBlank()) syncAccountByName(transaction.fromAcct)
                if (transaction.toAcct.isNotBlank())   syncAccountByName(transaction.toAcct)
            }
        }
        sync { deleteTransaction(transaction.id) }
    }
    suspend fun insertBorrower(borrower: Borrower) = insertBorrowerWithSource(borrower, "Cash in Hand")

    suspend fun updateBorrower(borrower: Borrower) {
        val b = borrower.copy(updatedAt = System.currentTimeMillis())
        dao.insertBorrower(b)
        sync { setBorrower(b) }
    }

    suspend fun updateDebt(debt: Debt) {
        val d = debt.copy(updatedAt = System.currentTimeMillis())
        dao.insertDebt(d)
        sync { setDebt(d) }
    }
    suspend fun insertBorrowerWithSource(borrower: Borrower, sourceAccount: String, projectId: String = borrower.projectId) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            val b = borrower.copy(projectId = projectId, updatedAt = now, createdAt = if (borrower.createdAt == 0L) now else borrower.createdAt)
            dao.insertBorrower(b)
            dao.updateAccountBalance(sourceAccount, -borrower.amount)
            val t = Transaction(java.util.UUID.randomUUID().toString(), borrower.date, "Expense", borrower.amount, fromAcct = sourceAccount, category = "Lending", desc = "Lent to ${borrower.name}", ref = borrower.id, notes = borrower.notes, projectId = projectId, updatedAt = now, createdAt = now)
            dao.insertTransaction(t)
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

        db.withTransaction {
            linkedTxns.forEach { txn ->
                when (txn.type.lowercase()) {
                    "expense"  -> dao.updateAccountBalance(txn.fromAcct,  txn.amount)
                    "income"   -> dao.updateAccountBalance(txn.toAcct,   -txn.amount)
                    "transfer" -> { dao.updateAccountBalance(txn.fromAcct, txn.amount); dao.updateAccountBalance(txn.toAcct, -txn.amount) }
                }
                dao.deleteTransaction(txn)
            }
            dao.getPaymentsForLoanOnce(borrower.id).forEach { p -> dao.deletePayment(p) }
            dao.deleteBorrower(borrower)
        }
        linkedTxns.forEach { sync { deleteTransaction(it.id) } }
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

    suspend fun recalculateAllBalances() {
        // C01 fix — Sprint 1 (decisions/2026-05-26-c01-fix-strategy.md).
        // Derives `paid` / `paidPrincipal` / `paidInterest` from the
        // `payments` and `debt_payments` tables (the single source of truth).
        // The previously-called dao.recalculateBorrowerPaid() /
        // recalculateDebtPaid() queries (UPDATE ... SET paid =
        // paidPrincipal + paidInterest) silently zeroed legacy rows whose
        // principal/interest split was never populated and whose only
        // repayment record lived on the cumulative `paid` field. They are
        // intentionally NOT called here. The v15→v16 backfill migration
        // (FynloDatabase.kt) ensures every legacy row has at least one
        // Payment row before this code can run on an upgraded install.
        //
        // Account balances are NOT recomputed from transactions because:
        // 1. Opening balances have no corresponding transaction record
        // 2. Starting from current balance + replaying transactions = double-counting
        // Account balances are kept correct in real-time by individual operations.
        dao.backfillBorrowerSourceAccount()
        dao.rebuildBorrowerPaidFromPayments()
        dao.rebuildDebtPaidFromDebtPayments()
    }

        /** Directly set account balance (for corrections). Creates a balancing transaction. */
    suspend fun quickEditBalance(accountName: String, newBalance: Double, oldBalance: Double) {
        db.withTransaction {
            val diff = newBalance - oldBalance
            dao.updateAccountBalance(accountName, diff)
            val t = Transaction(
                id       = java.util.UUID.randomUUID().toString(),
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
            sync { setTransaction(t) }
        }
        // Push updated account
        syncAccountByName(accountName)
    }

    suspend fun insertAccount(account: Account) {
        val now = System.currentTimeMillis()
        val a = account.copy(updatedAt = now, createdAt = if (account.createdAt == 0L) now else account.createdAt)
        dao.insertAccount(a); sync { setAccount(a) }
    }
    // ─── Investment — funded by own account ────────────────────────────────────
    suspend fun insertInvestmentFundedByAccount(investment: Investment, accountName: String, projectId: String = investment.projectId) {
        db.withTransaction {
            val i = investment.copy(sourceType = "account", fundingSource = accountName, projectId = projectId, updatedAt = System.currentTimeMillis(), createdAt = if (investment.createdAt == 0L) System.currentTimeMillis() else investment.createdAt)
            dao.insertInvestment(i)
            dao.updateAccountBalance(accountName, -investment.invested)
            val t = Transaction(java.util.UUID.randomUUID().toString(), investment.date, "Expense", investment.invested,
                fromAcct = accountName, category = "Investment",
                desc = "Invested in ${investment.name}", ref = i.id,
                notes = investment.notes, projectId = projectId, updatedAt = System.currentTimeMillis())
            dao.insertTransaction(t)
            sync { setInvestment(i); setTransaction(t) }
        }
        Analytics.investmentCreated(type = investment.type)
        syncAccountByName(accountName)
    }

    // ─── Investment — funded by existing recorded debt ──────────────────────────
    suspend fun insertInvestmentFundedByExistingDebt(investment: Investment, debt: app.fynlo.data.model.Debt, projectId: String = investment.projectId) {
        db.withTransaction {
            val i = investment.copy(sourceType = "existing_debt", fundingSource = debt.name, linkedDebtId = debt.id, projectId = projectId, updatedAt = System.currentTimeMillis(), createdAt = if (investment.createdAt == 0L) System.currentTimeMillis() else investment.createdAt)
            dao.insertInvestment(i)
            val t = Transaction(java.util.UUID.randomUUID().toString(), investment.date, "Transfer", investment.invested,
                fromAcct = debt.name, toAcct = investment.name, category = "Investment",
                desc = "Invested in ${investment.name} using ${debt.name} loan funds",
                notes = investment.notes, projectId = projectId, updatedAt = System.currentTimeMillis())
            dao.insertTransaction(t)
            sync { setInvestment(i); setTransaction(t) }
        }
        Analytics.investmentCreated(type = investment.type)
    }

    // ─── Investment — auto-create new loan + link to investment ─────────────────
    suspend fun insertInvestmentFundedByNewLoan(investment: Investment, newDebt: app.fynlo.data.model.Debt, projectId: String = investment.projectId) {
        db.withTransaction {
            val d = newDebt.copy(projectId = projectId, updatedAt = System.currentTimeMillis())
            dao.insertDebt(d)
            val i = investment.copy(sourceType = "new_loan", fundingSource = d.name, linkedDebtId = d.id, projectId = projectId, updatedAt = System.currentTimeMillis(), createdAt = if (investment.createdAt == 0L) System.currentTimeMillis() else investment.createdAt)
            dao.insertInvestment(i)
            val t = Transaction(java.util.UUID.randomUUID().toString(), investment.date, "Transfer", investment.invested,
                fromAcct = d.name, toAcct = investment.name, category = "Investment",
                desc = "Invested ₹${investment.invested.toInt()} in ${investment.name} via ${d.name} loan",
                notes = investment.notes, projectId = projectId, updatedAt = System.currentTimeMillis())
            dao.insertTransaction(t)
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
        db.withTransaction {
            // Remove the investment transaction that moved money out of an account
            val invTxn = dao.getTransactionsByRef(investment.id)
                .firstOrNull { it.category == "Investment" }
            if (invTxn != null) {
                dao.deleteTransaction(invTxn)
                sync { deleteTransaction(invTxn.id) }
            }
            dao.deleteInvestment(investment)
        }
        sync { deleteInvestment(investment.id) }
    }

    // ─── Delete — record + reverse source account balance ─────────────────────
    suspend fun deleteInvestmentAndReverseAccount(investment: Investment) {
        db.withTransaction {
            // Restore account balance
            dao.updateAccountBalance(investment.fundingSource, investment.invested)
            // Also delete the Investment expense transaction (find by ref or desc)
            val invTxn = dao.getTransactionsByRef(investment.id)
                .firstOrNull { it.category == "Investment" }
                ?: dao.getTransactionsByDesc("Invested in ${investment.name}")
                    .firstOrNull { it.category == "Investment" && it.ref.isBlank() }
            if (invTxn != null) {
                dao.deleteTransaction(invTxn)
                sync { deleteTransaction(invTxn.id) }
            }
            dao.deleteInvestment(investment)
        }
        sync { deleteInvestment(investment.id) }
        syncAccountByName(investment.fundingSource)
    }

    // ─── Delete — record + delete the linked loan that was auto-created ────────
    suspend fun deleteInvestmentAndLinkedLoan(investment: Investment) {
        db.withTransaction {
            // Delete the investment transaction
            val invTxn = dao.getTransactionsByRef(investment.id)
                .firstOrNull { it.category == "Investment" }
            if (invTxn != null) {
                dao.deleteTransaction(invTxn)
                sync { deleteTransaction(invTxn.id) }
            }
            // Delete the linked debt and its transactions
            if (investment.linkedDebtId.isNotEmpty()) {
                val linkedDebt = dao.getDebtById(investment.linkedDebtId)
                if (linkedDebt != null) {
                    val debtTxn = dao.getTransactionsByRef(investment.linkedDebtId)
                        .firstOrNull { it.type.equals("Income", ignoreCase = true) }
                    if (debtTxn != null) {
                        dao.updateAccountBalance(debtTxn.toAcct, -linkedDebt.amount)
                        dao.deleteTransaction(debtTxn)
                        sync { deleteTransaction(debtTxn.id) }
                    }
                }
                dao.deleteDebtById(investment.linkedDebtId)
            }
            dao.deleteInvestment(investment)
        }
        sync {
            if (investment.linkedDebtId.isNotEmpty()) deleteDebt(investment.linkedDebtId)
            deleteInvestment(investment.id)
        }
    }

    suspend fun upsertAccount(account: Account) {
        dao.insertAccount(account)
        sync { setAccount(account) }
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
                        id = java.util.UUID.randomUUID().toString(),
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
                            id = java.util.UUID.randomUUID().toString(),
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
                        id = java.util.UUID.randomUUID().toString(),
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
                id = java.util.UUID.randomUUID().toString(),
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
            val now = System.currentTimeMillis()
            val d = debt.copy(projectId = projectId, updatedAt = now, createdAt = if (debt.createdAt == 0L) now else debt.createdAt)
            dao.insertDebt(d)
            dao.updateAccountBalance(destinationAccount, debt.amount)
            val t = Transaction(java.util.UUID.randomUUID().toString(), debt.date, "Income", debt.amount, toAcct = destinationAccount, category = "Debt Received", desc = "Loan received from ${debt.name}", ref = d.id, notes = debt.notes, projectId = projectId, updatedAt = now, createdAt = now)
            dao.insertTransaction(t)
            sync { setDebt(d); setTransaction(t) }
        }
        syncAccountByName(destinationAccount)
    }
    suspend fun deleteDebt(debt: Debt) {
        val linkedTxns = dao.getTransactionsByRef(debt.id)
        db.withTransaction {
            linkedTxns.forEach { txn ->
                when (txn.type.lowercase()) {
                    "expense"  -> dao.updateAccountBalance(txn.fromAcct,  txn.amount)
                    "income"   -> dao.updateAccountBalance(txn.toAcct,   -txn.amount)
                    "transfer" -> { dao.updateAccountBalance(txn.fromAcct, txn.amount); dao.updateAccountBalance(txn.toAcct, -txn.amount) }
                }
                dao.deleteTransaction(txn)
            }
            dao.getDebtPaymentsForDebtOnce(debt.id).forEach { p -> dao.deleteDebtPayment(p) }
            dao.deleteDebt(debt)
        }
        linkedTxns.forEach { sync { deleteTransaction(it.id) } }
        sync { deleteDebt(debt.id) }
        linkedTxns.map { it.fromAcct }.filter { it.isNotBlank() }.distinct().forEach { syncAccountByName(it) }
        linkedTxns.map { it.toAcct }.filter { it.isNotBlank() }.distinct().forEach { syncAccountByName(it) }
    }
    suspend fun insertPaymentWithDest(payment: Payment, destinationAccount: String, projectId: String = payment.projectId) {
        db.withTransaction {
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
                id = java.util.UUID.randomUUID().toString(),
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
                id = java.util.UUID.randomUUID().toString(),
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
                    id = java.util.UUID.randomUUID().toString(),
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
                id        = java.util.UUID.randomUUID().toString(),
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
            borrower.amount, borrower.rate, borrower.date, borrower.type,
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
    suspend fun writeOffBorrower(borrower: Borrower, fromAccount: String = "Cash in Hand") {
        val outstanding = if (borrower.status == "Defaulted" && borrower.frozenInterest > 0) {
            // Use frozen interest for defaulted borrowers
            (borrower.amount - borrower.paidPrincipal) + maxOf(0.0, borrower.frozenInterest - borrower.paidInterest)
        } else {
            val interest = app.fynlo.logic.InterestEngine.calcIntAccrued(
                borrower.amount, borrower.rate, borrower.date, borrower.type, borrower.due, borrower.paidPrincipal
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
                id        = java.util.UUID.randomUUID().toString(),
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
    suspend fun pushAllLocalToFirestore() {
        val uid = syncManager.userId
        if (uid.isEmpty()) return
        val fs = firestore
        android.util.Log.d("FynloSync", "Starting full local→Firestore push for user $uid")

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

        android.util.Log.d("FynloSync", "Full push complete: $pushed succeeded, $failed failed")
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
        val rec = r.copy(updatedAt = System.currentTimeMillis())
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
            "projects", "backup_meta", "backups", "net_worth_snapshots",
            "investment_valuations"
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
        val data = Json.decodeFromString<BackupData>(json)
        when (val verdict = BackupIntegrity.check(data)) {
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
        db.withTransaction {
            // Clear everything first so a restore is a true replace, not a merge.
            dao.deleteAllAccounts(); dao.deleteAllTransactions(); dao.deleteAllBorrowers()
            dao.deleteAllInvestments(); dao.deleteAllDebts(); dao.deleteAllPeople(); dao.deleteAllProjects()
            dao.deleteAllPayments(); dao.deleteAllDebtPayments(); dao.deleteAllBudgets(); dao.deleteAllGoals()
            data.accounts.forEach { dao.insertAccount(it) }; data.transactions.forEach { dao.insertTransaction(it) }
            data.borrowers.forEach { dao.insertBorrower(it) }; data.investments.forEach { dao.insertInvestment(it) }
            data.debts.forEach { dao.insertDebt(it) }; data.people.forEach { dao.insertPerson(it) }
            data.projects.forEach { dao.insertProject(it) }
            data.payments.forEach { dao.insertPayment(it) }; data.debtPayments.forEach { dao.insertDebtPayment(it) }
            data.budgets.forEach { dao.insertBudget(it) }; data.goals.forEach { dao.insertGoal(it) }
            data.recurringTransactions.forEach { dao.insertRecurringTransaction(it) }
        }
    }
}
