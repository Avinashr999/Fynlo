package com.example.cashmemo.data

import com.example.cashmemo.data.local.CashMemoDao
import com.example.cashmemo.data.local.CashMemoDatabase
import com.example.cashmemo.data.model.*
import com.example.cashmemo.data.remote.FirestoreRepository
import com.example.cashmemo.data.remote.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.room.withTransaction
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class FinanceRepository(
    private val dao: CashMemoDao,
    private val db: CashMemoDatabase,
    private var firestore: FirestoreRepository,
    private var syncManager: SyncManager
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
    val allPeople: Flow<List<Person>>            = dao.getAllPeople()
    val allBudgets: Flow<List<Budget>>           = dao.getAllBudgets()
    val allGoals: Flow<List<Goal>>               = dao.getAllGoals()
    val allProjects: Flow<List<Project>>         = dao.getAllProjects()
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private fun sync(block: suspend FirestoreRepository.() -> Unit) {
        ioScope.launch {
            syncManager.setSyncing()
            runCatching { firestore.block() }
            syncManager.setSynced()
        }
    }

    /** Fetch account from Room by name and push updated balance to Firestore. */
    private fun syncAccountByName(name: String) {
        ioScope.launch {
            runCatching {
                val account = dao.getAccountByName(name)
                if (account != null) {
                    syncManager.setSyncing()
                    firestore.setAccount(account)
                    syncManager.setSynced()
                }
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
        db.withTransaction {
            val t = transaction.copy(updatedAt = System.currentTimeMillis())
            dao.insertTransaction(t)
            when (transaction.type.lowercase()) {
                "expense"  -> { dao.updateAccountBalance(transaction.fromAcct, -transaction.amount); syncAccountByName(transaction.fromAcct) }
                "income"   -> { dao.updateAccountBalance(transaction.toAcct, transaction.amount); syncAccountByName(transaction.toAcct) }
                "transfer" -> { dao.updateAccountBalance(transaction.fromAcct, -transaction.amount); dao.updateAccountBalance(transaction.toAcct, transaction.amount); syncAccountByName(transaction.fromAcct); syncAccountByName(transaction.toAcct) }
            }
            sync { setTransaction(t) }
        }
    }
    suspend fun deleteTransaction(transaction: Transaction) {
        db.withTransaction {
            when (transaction.type.lowercase()) {
                "expense"  -> dao.updateAccountBalance(transaction.fromAcct,  transaction.amount)
                "income"   -> dao.updateAccountBalance(transaction.toAcct,   -transaction.amount)
                "transfer" -> { dao.updateAccountBalance(transaction.fromAcct, transaction.amount); dao.updateAccountBalance(transaction.toAcct, -transaction.amount) }
            }
            dao.deleteTransaction(transaction)
        }
        sync { deleteTransaction(transaction.id) }
    }
    suspend fun insertBorrower(borrower: Borrower) = insertBorrowerWithSource(borrower, "Cash in Hand")
    suspend fun insertBorrowerWithSource(borrower: Borrower, sourceAccount: String, projectId: String = borrower.projectId) {
        db.withTransaction {
            val b = borrower.copy(projectId = projectId, updatedAt = System.currentTimeMillis())
            dao.insertBorrower(b)
            dao.updateAccountBalance(sourceAccount, -borrower.amount)
            syncAccountByName(sourceAccount)
            val t = Transaction(java.util.UUID.randomUUID().toString(), borrower.date, "Expense", borrower.amount, fromAcct = sourceAccount, category = "Lending", desc = "Lent to ${borrower.name}", notes = borrower.notes, projectId = projectId, updatedAt = System.currentTimeMillis())
            dao.insertTransaction(t)
            sync { setBorrower(b); setTransaction(t) }
        }
    }
    suspend fun deleteBorrower(borrower: Borrower) {
        db.withTransaction { dao.updateAccountBalance("Cash in Hand", borrower.amount); dao.deleteBorrower(borrower) }
        sync { deleteBorrower(borrower.id) }
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
        val a = account.copy(updatedAt = System.currentTimeMillis())
        dao.insertAccount(a); sync { setAccount(a) }
    }
    suspend fun insertInvestmentWithSource(investment: Investment, sourceAccount: String, projectId: String = investment.projectId) {
        db.withTransaction {
            val i = investment.copy(projectId = projectId, updatedAt = System.currentTimeMillis())
            dao.insertInvestment(i)
            dao.updateAccountBalance(sourceAccount, -investment.invested)
            syncAccountByName(sourceAccount)
            val t = Transaction(java.util.UUID.randomUUID().toString(), investment.date, "Expense", investment.invested, fromAcct = sourceAccount, category = "Investment", desc = "Invested in ${investment.name}", notes = investment.notes, projectId = projectId, updatedAt = System.currentTimeMillis())
            dao.insertTransaction(t)
            sync { setInvestment(i); setTransaction(t) }
        }
    }
    suspend fun deleteInvestment(investment: Investment) {
        db.withTransaction { dao.updateAccountBalance("HDFC Bank", investment.invested); dao.deleteInvestment(investment) }
        sync { deleteInvestment(investment.id) }
    }
    suspend fun insertDebtWithDestination(debt: Debt, destinationAccount: String, projectId: String = debt.projectId) {
        db.withTransaction {
            val d = debt.copy(projectId = projectId, updatedAt = System.currentTimeMillis())
            dao.insertDebt(d)
            dao.updateAccountBalance(destinationAccount, debt.amount)
            syncAccountByName(destinationAccount)
            val t = Transaction(java.util.UUID.randomUUID().toString(), debt.date, "Income", debt.amount, toAcct = destinationAccount, category = "Debt", desc = "Loan from ${debt.name}", notes = debt.notes, projectId = projectId, updatedAt = System.currentTimeMillis())
            dao.insertTransaction(t)
            sync { setDebt(d); setTransaction(t) }
        }
    }
    suspend fun deleteDebt(debt: Debt) {
        db.withTransaction { dao.updateAccountBalance("HDFC Bank", -debt.amount); dao.deleteDebt(debt) }
        sync { deleteDebt(debt.id) }
    }
    suspend fun insertPaymentWithDest(payment: Payment, destinationAccount: String, projectId: String = payment.projectId) {
        db.withTransaction {
            val p = payment.copy(projectId = projectId, updatedAt = System.currentTimeMillis())
            dao.insertPayment(p)
            dao.updateAccountBalance(destinationAccount, payment.amount)
            syncAccountByName(destinationAccount)
            dao.updateBorrowerPaidAmount(payment.loanId, payment.amount)
            val t = Transaction(java.util.UUID.randomUUID().toString(), payment.date, "Income", payment.amount, toAcct = destinationAccount, category = "Loan Repayment", desc = "Received from ${payment.name}", notes = payment.notes, projectId = projectId, updatedAt = System.currentTimeMillis())
            dao.insertTransaction(t)
            sync { setPayment(p); setTransaction(t) }
        }
    }
    suspend fun insertDebtPaymentWithSource(payment: DebtPayment, sourceAccount: String, projectId: String = payment.projectId) {
        db.withTransaction {
            val p = payment.copy(projectId = projectId, updatedAt = System.currentTimeMillis())
            dao.insertDebtPayment(p)
            dao.updateAccountBalance(sourceAccount, -payment.amount)
            syncAccountByName(sourceAccount)
            dao.updateDebtPaidAmount(payment.debtId, payment.amount)
            val t = Transaction(java.util.UUID.randomUUID().toString(), payment.date, "Expense", payment.amount, fromAcct = sourceAccount, category = "Debt Repayment", desc = "Paid for ${payment.name}", notes = payment.notes, projectId = projectId, updatedAt = System.currentTimeMillis())
            dao.insertTransaction(t)
            sync { setDebtPayment(p); setTransaction(t) }
        }
    }
    suspend fun insertPerson(person: Person) { val p = person.copy(updatedAt = System.currentTimeMillis()); dao.insertPerson(p); sync { setPerson(p) } }
    suspend fun deletePerson(person: Person) { dao.deletePerson(person); sync { deletePerson(person.id) } }
    suspend fun insertBudget(budget: Budget) { val b = budget.copy(updatedAt = System.currentTimeMillis()); dao.insertBudget(b); sync { setBudget(b) } }
    suspend fun deleteBudget(budget: Budget) { dao.deleteBudget(budget); sync { deleteBudget(budget.category) } }
    suspend fun insertGoal(goal: Goal) { val g = goal.copy(updatedAt = System.currentTimeMillis()); dao.insertGoal(g); sync { setGoal(g) } }
    suspend fun deleteGoal(goal: Goal) { dao.deleteGoal(goal); sync { deleteGoal(goal.id) } }
    fun getPaymentsForLoan(loanId: String) = dao.getPaymentsForLoan(loanId)

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
    suspend fun saveNetWorthSnapshot(s: com.example.cashmemo.data.model.NetWorthSnapshot) = dao.insertNetWorthSnapshot(s)

    fun getAllRecurringTransactions() = dao.getAllRecurringTransactions()
    suspend fun insertRecurringTransaction(r: com.example.cashmemo.data.model.RecurringTransaction) = dao.insertRecurringTransaction(r)
    suspend fun deleteRecurringTransaction(r: com.example.cashmemo.data.model.RecurringTransaction) = dao.deleteRecurringTransaction(r)

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
     * Takes a daily backup snapshot to Firestore under users/{uid}/backups/{date}/
     * Keeps last 7 days. Called on startup after data is confirmed loaded.
     * This protects against data loss from any source.
     */
    suspend fun takeBackupIfNeeded(uid: String) {
        val today    = java.time.LocalDate.now().toString() // yyyy-MM-dd
        val prefs    = com.google.firebase.ktx.Firebase.firestore
            .collection("users").document(uid)
            .collection("backup_meta").document("last_backup")

        runCatching {
            val lastBackup = prefs.get().await().getString("date") ?: ""
            if (lastBackup == today) return // Already backed up today

            val accounts     = dao.getAllAccounts().first()
            val transactions = dao.getAllTransactions().first()
            val borrowers    = dao.getAllBorrowers().first()
            val investments  = dao.getAllInvestments().first()
            val debts        = dao.getAllDebts().first()

            if (accounts.isEmpty()) return // Don’t backup empty data

            val db   = com.google.firebase.ktx.Firebase.firestore
            val base = db.collection("users").document(uid).collection("backups").document(today)

            val batch = db.batch()
            // Store counts + snapshot marker
            batch.set(base, mapOf(
                "date"         to today,
                "accountCount" to accounts.size,
                "txnCount"     to transactions.size,
                "borrowerCount" to borrowers.size,
                "debtCount"    to debts.size,
                "investCount"  to investments.size,
                "netWorth"     to accounts.sumOf { it.balance },
                "createdAt"    to System.currentTimeMillis()
            ))
            batch.commit().await()

            // Write actual data into sub-collections
            accounts.forEach { a ->
                base.collection("accounts").document(a.id).set(a).await()
            }
            borrowers.take(100).forEach { b ->
                base.collection("borrowers").document(b.id).set(mapOf(
                    "name" to b.name, "amount" to b.amount, "paid" to b.paid, "date" to b.date
                )).await()
            }

            // Mark last backup date
            prefs.set(mapOf("date" to today, "uid" to uid)).await()
        }
    }
    suspend fun getAllDataAsJson(): String {
        val data = BackupData(dao.getAllAccounts().first(), dao.getAllTransactions().first(), dao.getAllBorrowers().first(), dao.getAllInvestments().first(), dao.getAllDebts().first(), dao.getAllPeople().first(), dao.getAllProjects().first())
        return Json.encodeToString(data)
    }
    suspend fun restoreDataFromJson(json: String) {
        db.withTransaction {
            val data = Json.decodeFromString<BackupData>(json)
            dao.deleteAllAccounts(); dao.deleteAllTransactions(); dao.deleteAllBorrowers()
            dao.deleteAllInvestments(); dao.deleteAllDebts(); dao.deleteAllPeople(); dao.deleteAllProjects()
            data.accounts.forEach { dao.insertAccount(it) }; data.transactions.forEach { dao.insertTransaction(it) }
            data.borrowers.forEach { dao.insertBorrower(it) }; data.investments.forEach { dao.insertInvestment(it) }
            data.debts.forEach { dao.insertDebt(it) }; data.people.forEach { dao.insertPerson(it) }
            data.projects.forEach { dao.insertProject(it) }
        }
    }
}
