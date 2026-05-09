package app.fynlo.data

import app.fynlo.data.local.FynloDao
import app.fynlo.data.local.FynloDatabase
import app.fynlo.data.model.*
import app.fynlo.data.remote.FirestoreRepository
import app.fynlo.data.remote.SyncManager
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
        ioScope.launch {
            syncManager.setSyncing()
            runCatching { firestore.block() }
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
        val affectedAccounts = mutableListOf<String>()
        db.withTransaction {
            val t = transaction.copy(updatedAt = System.currentTimeMillis())
            dao.insertTransaction(t)
            when (transaction.type.lowercase()) {
                "expense"  -> { dao.updateAccountBalance(transaction.fromAcct, -transaction.amount); affectedAccounts += transaction.fromAcct }
                "income"   -> { dao.updateAccountBalance(transaction.toAcct, transaction.amount); affectedAccounts += transaction.toAcct }
                "transfer" -> { dao.updateAccountBalance(transaction.fromAcct, -transaction.amount); dao.updateAccountBalance(transaction.toAcct, transaction.amount); affectedAccounts += transaction.fromAcct; affectedAccounts += transaction.toAcct }
            }
            sync { setTransaction(t) }
        }
        // Sync AFTER withTransaction commits so we push the updated balance
        affectedAccounts.forEach { syncAccountByName(it) }
    }
    suspend fun editTransaction(old: Transaction, new: Transaction) {
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

            // 3. Sync Borrower/Debt paid amounts if it's a repayment edit
            if (old.category == "Loan Repayment" && old.ref.isNotBlank()) {
                dao.updateBorrowerPaidAmount(old.ref, -old.amount)
            }
            if (new.category == "Loan Repayment" && new.ref.isNotBlank()) {
                dao.updateBorrowerPaidAmount(new.ref, new.amount)
                val b = dao.getBorrowerById(new.ref)
                sync { b?.let { setBorrower(it) } }
            }
            if (old.category == "Debt Repayment" && old.ref.isNotBlank()) {
                dao.updateDebtPaidAmount(old.ref, -old.amount)
            }
            if (new.category == "Debt Repayment" && new.ref.isNotBlank()) {
                dao.updateDebtPaidAmount(new.ref, new.amount)
                val d = dao.getDebtById(new.ref)
                sync { d?.let { setDebt(it) } }
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
            when (transaction.type.lowercase()) {
                "expense"  -> dao.updateAccountBalance(transaction.fromAcct,  transaction.amount)
                "income"   -> dao.updateAccountBalance(transaction.toAcct,   -transaction.amount)
                "transfer" -> { dao.updateAccountBalance(transaction.fromAcct, transaction.amount); dao.updateAccountBalance(transaction.toAcct, -transaction.amount) }
            }

            // Handle payment reversals if transaction belongs to a loan/debt
            if (transaction.category == "Loan Repayment" && transaction.ref.isNotBlank()) {
                dao.updateBorrowerPaidAmount(transaction.ref, -transaction.amount)
                val b = dao.getBorrowerById(transaction.ref)
                sync { b?.let { setBorrower(it) } }
            } else if (transaction.category == "Debt Repayment" && transaction.ref.isNotBlank()) {
                dao.updateDebtPaidAmount(transaction.ref, -transaction.amount)
                val d = dao.getDebtById(transaction.ref)
                sync { d?.let { setDebt(it) } }
            }

            dao.deleteTransaction(transaction)
        }
        // Sync reversed account balances AFTER withTransaction commits
        when (transaction.type.lowercase()) {
            "expense"  -> syncAccountByName(transaction.fromAcct)
            "income"   -> syncAccountByName(transaction.toAcct)
            "transfer" -> { syncAccountByName(transaction.fromAcct); syncAccountByName(transaction.toAcct) }
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
            val b = borrower.copy(projectId = projectId, updatedAt = System.currentTimeMillis())
            dao.insertBorrower(b)
            dao.updateAccountBalance(sourceAccount, -borrower.amount)
            val t = Transaction(java.util.UUID.randomUUID().toString(), borrower.date, "Expense", borrower.amount, fromAcct = sourceAccount, category = "Lending", desc = "Lent to ${borrower.name}", notes = borrower.notes, projectId = projectId, updatedAt = System.currentTimeMillis())
            dao.insertTransaction(t)
            sync { setBorrower(b); setTransaction(t) }
        }
        syncAccountByName(sourceAccount)
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
            val t = Transaction(java.util.UUID.randomUUID().toString(), investment.date, "Expense", investment.invested, fromAcct = sourceAccount, category = "Investment", desc = "Invested in ${investment.name}", notes = investment.notes, projectId = projectId, updatedAt = System.currentTimeMillis())
            dao.insertTransaction(t)
            sync { setInvestment(i); setTransaction(t) }
        }
        syncAccountByName(sourceAccount)
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
        db.withTransaction { dao.updateAccountBalance("HDFC Bank", investment.invested); dao.deleteInvestment(investment) }
        sync { deleteInvestment(investment.id) }
    }
    suspend fun insertDebtWithDestination(debt: Debt, destinationAccount: String, projectId: String = debt.projectId) {
        db.withTransaction {
            val d = debt.copy(projectId = projectId, updatedAt = System.currentTimeMillis())
            dao.insertDebt(d)
            dao.updateAccountBalance(destinationAccount, debt.amount)
            val t = Transaction(java.util.UUID.randomUUID().toString(), debt.date, "Income", debt.amount, toAcct = destinationAccount, category = "Debt", desc = "Loan from ${debt.name}", notes = debt.notes, projectId = projectId, updatedAt = System.currentTimeMillis())
            dao.insertTransaction(t)
            sync { setDebt(d); setTransaction(t) }
        }
        syncAccountByName(destinationAccount)
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
            dao.updateBorrowerPaidAmount(payment.loanId, payment.amount)
            val t = Transaction(
                id = java.util.UUID.randomUUID().toString(),
                date = payment.date,
                type = "Income",
                amount = payment.amount,
                toAcct = destinationAccount,
                category = "Loan Repayment",
                desc = "Received from ${payment.name}",
                ref = payment.loanId, // link to borrower
                notes = payment.notes,
                projectId = projectId,
                updatedAt = System.currentTimeMillis()
            )
            dao.insertTransaction(t)
            
            // Sync the updated borrower too!
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
            val p = payment.copy(projectId = projectId, updatedAt = System.currentTimeMillis())
            dao.insertDebtPayment(p)
            dao.updateAccountBalance(sourceAccount, -payment.amount)
            dao.updateDebtPaidAmount(payment.debtId, payment.amount)
            val t = Transaction(
                id = java.util.UUID.randomUUID().toString(),
                date = payment.date,
                type = "Expense",
                amount = payment.amount,
                fromAcct = sourceAccount,
                category = "Debt Repayment",
                desc = "Paid for ${payment.name}",
                ref = payment.debtId, // link to debt
                notes = payment.notes,
                projectId = projectId,
                updatedAt = System.currentTimeMillis()
            )
            dao.insertTransaction(t)
            
            // Sync the updated debt too!
            val updatedDebt = dao.getDebtById(payment.debtId)
            sync { 
                setDebtPayment(p)
                setTransaction(t)
                updatedDebt?.let { setDebt(it) }
            }
        }
        syncAccountByName(sourceAccount)
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
    suspend fun saveNetWorthSnapshot(s: app.fynlo.data.model.NetWorthSnapshot) = dao.insertNetWorthSnapshot(s)

    fun getAllRecurringTransactions() = dao.getAllRecurringTransactions()
    suspend fun insertRecurringTransaction(r: app.fynlo.data.model.RecurringTransaction) = dao.insertRecurringTransaction(r)
    suspend fun deleteRecurringTransaction(r: app.fynlo.data.model.RecurringTransaction) = dao.deleteRecurringTransaction(r)

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
            "projects", "backup_meta", "backups", "net_worth_snapshots"
        )

        db.withTransaction {
            // 1. Wipe Room
            dao.deleteAllAccounts()
            dao.deleteAllTransactions()
            dao.deleteAllBorrowers()
            dao.deleteAllInvestments()
            dao.deleteAllDebts()
            dao.deleteAllPeople()
            dao.deleteAllProjects()
            dao.deleteAllBudgets()
            dao.deleteAllGoals()
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
