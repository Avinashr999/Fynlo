package app.fynlo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fynlo.data.FinanceRepository
import app.fynlo.data.SyncStatus
import app.fynlo.data.model.*
import app.fynlo.data.model.FlowResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class FinanceViewModel(private val repository: FinanceRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val syncStatus: StateFlow<SyncStatus>
        get() = repository.syncStatus

    private val _currentProjectId = MutableStateFlow("")
    val currentProjectId: StateFlow<String> = _currentProjectId.asStateFlow()

    val projects = repository.allProjects
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _isSyncReady = MutableStateFlow(false)
    val isSyncReady: StateFlow<Boolean> = _isSyncReady.asStateFlow()

    init {
        viewModelScope.launch {
            projects.collect { list ->
                if (_currentProjectId.value.isEmpty() && list.isNotEmpty()) {
                    _currentProjectId.value = list.first().id
                }
                if (list.isNotEmpty()) {
                    _isSyncReady.value = true
                }
            }
        }
        viewModelScope.launch {
            delay(30_000)
            if (!_isSyncReady.value) {
                _isSyncReady.value = true
            }
        }
    }

    val currentProject: StateFlow<Project?> = combine(projects, _currentProjectId) { list, pid ->
        list.find { it.id == pid }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun switchProject(projectId: String) { _currentProjectId.value = projectId }

    fun createProject(project: Project) {
        viewModelScope.launch { repository.insertProject(project) }
    }

    fun deleteProject(project: Project) {
        viewModelScope.launch {
            repository.deleteProject(project)
            if (_currentProjectId.value == project.id) _currentProjectId.value = "personal"
        }
    }

    val borrowers: StateFlow<List<Borrower>> =
        combine(repository.allBorrowers, _currentProjectId) { list, pid ->
            list.filter { it.projectId == pid || it.projectId.isEmpty() || it.projectId == "personal" }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val transactions: StateFlow<List<Transaction>> =
        combine(repository.allTransactions, _currentProjectId) { list, pid ->
            list.filter { it.projectId == pid || it.projectId.isEmpty() || it.projectId == "personal" }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allAccountsUnfiltered: StateFlow<List<Account>> =
        repository.allAccounts
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val accounts: StateFlow<List<Account>> =
        combine(repository.allAccounts, _currentProjectId) { list, pid ->
            list.filter { it.projectId == pid || it.projectId.isEmpty() || it.projectId == "personal" }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val investments: StateFlow<List<Investment>> =
        combine(repository.allInvestments, _currentProjectId) { list, pid ->
            list.filter { it.projectId == pid || it.projectId.isEmpty() || it.projectId == "personal" }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val debts: StateFlow<List<Debt>> =
        combine(repository.allDebts, _currentProjectId) { list, pid ->
            list.filter { it.projectId == pid || it.projectId.isEmpty() || it.projectId == "personal" }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val payments: StateFlow<List<Payment>> =
        repository.allPayments.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val debtPayments: StateFlow<List<DebtPayment>> =
        repository.allDebtPayments.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val people: StateFlow<List<Person>> =
        combine(repository.allPeople, _currentProjectId) { list, pid ->
            list.filter { it.projectId == pid || it.projectId.isEmpty() || it.projectId == "personal" }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val budgets: StateFlow<List<Budget>> =
        combine(repository.allBudgets, _currentProjectId) { list, pid ->
            list.filter { it.projectId == pid || it.projectId.isEmpty() || it.projectId == "personal" }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val goals: StateFlow<List<Goal>> =
        combine(repository.allGoals, _currentProjectId) { list, pid ->
            list.filter { it.projectId == pid || it.projectId.isEmpty() || it.projectId == "personal" }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredTransactions: StateFlow<List<Transaction>> =
        combine(transactions, _searchQuery) { trans, query ->
            if (query.isEmpty()) trans
            else trans.filter {
                it.category.contains(query, ignoreCase = true) ||
                it.desc.contains(query, ignoreCase = true) ||
                it.notes.contains(query, ignoreCase = true) ||
                it.date.contains(query)
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun updateSearchQuery(query: String) { _searchQuery.value = query }

    val expenseAnalytics: StateFlow<Map<String, Double>> = transactions.map { trans ->
        trans.filter { it.type.lowercase() == "expense" }
            .groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val financialSummary: StateFlow<FinancialSummary> = combine(
        transactions, accounts, investments, borrowers, debts
    ) { trans, accts, invs, brws, dbts ->
        val totalCash        = accts.sumOf { it.balance }
        val totalInvestments = invs.sumOf { it.currentVal }
        val totalReceivables = brws.sumOf { b ->
            val accrued = app.fynlo.logic.InterestEngine.calcIntAccrued(
                b.amount, b.rate, b.date, b.type, b.due, totalPaid = b.paid
            )
            b.amount + accrued - b.paid
        }
        
        val totalInterestLoans = brws.filter { it.rate > 0 }.sumOf { b ->
            val accrued = app.fynlo.logic.InterestEngine.calcIntAccrued(
                b.amount, b.rate, b.date, b.type, b.due, totalPaid = b.paid
            )
            b.amount + accrued - b.paid
        }
        val totalHandLoans = brws.filter { it.rate <= 0 }.sumOf { it.amount - it.paid }

        val invTypeMap = invs.groupBy { it.type }
            .mapValues { it.value.sumOf { inv -> inv.currentVal } }

        val interestBrwMap = brws.filter { it.rate > 0 }.associate { b ->
            val accrued = app.fynlo.logic.InterestEngine.calcIntAccrued(
                b.amount, b.rate, b.date, b.type, b.due, totalPaid = b.paid
            )
            b.name to (b.amount + accrued - b.paid)
        }

        val handBrwMap = brws.filter { it.rate <= 0 }.associate { b ->
            b.name to (b.amount - b.paid)
        }

        val totalAssets       = totalCash + totalInvestments + totalReceivables
        val totalDebtPrincipal = dbts.sumOf { it.amount - it.paid }
        val totalDebtInterest  = dbts.sumOf { d ->
            app.fynlo.logic.InterestEngine.calcIntAccrued(
                d.amount, d.rate, d.date, d.intType, d.due, totalPaid = d.paid
            )
        }
        val totalExpenses  = trans.filter { it.type.lowercase() == "expense" }.sumOf { it.amount }
        val totalIncome    = trans.filter { it.type.lowercase() == "income"  }.sumOf { it.amount }
        val invGrowth      = invs.sumOf { it.currentVal - it.invested }
        val avgYield       = if (brws.isNotEmpty()) brws.map { it.rate }.average() else 0.0
        val net            = totalAssets - (totalDebtPrincipal + totalDebtInterest)
        val accountsMap    = accts.associate { it.name to it.balance }

        // Calculate per-account growth (Month-to-Date inflow - outflow)
        val currentMonthPrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))
        val thisMonthTrans = trans.filter { it.date.startsWith(currentMonthPrefix) }
        val growthMap = accts.associate { acct ->
            val inflow  = thisMonthTrans.filter { it.toAcct == acct.name }.sumOf { it.amount }
            val outflow = thisMonthTrans.filter { it.fromAcct == acct.name }.sumOf { it.amount }
            acct.name to (inflow - outflow)
        }

        FinancialSummary(
            totalCash          = totalCash,
            totalInvestments   = totalInvestments,
            totalReceivables   = totalReceivables,
            totalAssets        = totalAssets,
            totalDebtPrincipal = totalDebtPrincipal,
            totalDebtInterest  = totalDebtInterest,
            totalExpenses      = totalExpenses,
            totalIncome        = totalIncome,
            netWorth           = net,
            investmentGrowth   = invGrowth,
            lendingYield       = avgYield,
            debtBurden         = if (net != 0.0) ((totalDebtPrincipal + totalDebtInterest) / net) * 100 else 0.0,
            totalInterestLoans = totalInterestLoans,
            totalHandLoans     = totalHandLoans,
            investmentTypeBreakdown = invTypeMap,
            interestLendingBreakdown = interestBrwMap,
            handLendingBreakdown     = handBrwMap,
            accountBreakdown   = accountsMap,
            accountGrowthMap   = growthMap
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FinancialSummary())

    private val today get() = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    private val pid   get() = _currentProjectId.value.ifEmpty { "personal" }

    fun addBorrowerWithSource(borrower: Borrower, source: String) {
        viewModelScope.launch {
            repository.insertBorrowerWithSource(borrower.copy(projectId = pid), source, pid)
        }
    }

    fun deleteBorrower(borrower: Borrower) {
        viewModelScope.launch { repository.deleteBorrower(borrower) }
    }

    fun updateBorrower(borrower: Borrower) {
        viewModelScope.launch {
            repository.updateBorrower(borrower.copy(projectId = pid))
        }
    }

    fun updateDebt(debt: Debt) {
        viewModelScope.launch {
            repository.updateDebt(debt.copy(projectId = pid, updatedAt = System.currentTimeMillis()))
        }
    }

    fun addInvestmentWithSource(investment: Investment, source: String) {
        viewModelScope.launch {
            repository.insertInvestmentWithSource(investment.copy(projectId = pid), source, pid)
        }
    }

    fun updateInvestmentValue(investment: Investment, newCurrentVal: Double) {
        viewModelScope.launch {
            repository.updateInvestmentValue(investment, newCurrentVal)
        }
    }

    fun updateInvestment(investment: Investment) {
        viewModelScope.launch {
            repository.updateInvestment(investment.copy(projectId = pid))
        }
    }

    fun executeLinkedInvestment(
        investment: Investment,
        fundingSourceType: String,
        sourceName: String,
        debtDetails: Debt? = null
    ) {
        viewModelScope.launch {
            repository.executeLinkedInvestment(
                investment.copy(projectId = pid),
                fundingSourceType,
                sourceName,
                debtDetails?.copy(projectId = pid)
            )
        }
    }

    fun addValuation(v: InvestmentValuation) {
        viewModelScope.launch { repository.addValuation(v) }
    }

    fun getValuationsForInvestment(invId: String) = repository.getValuationsForInvestment(invId)

    fun deleteInvestment(investment: Investment) {
        viewModelScope.launch { repository.deleteInvestment(investment) }
    }

    fun addTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.insertTransaction(transaction.copy(projectId = pid))
        }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.insertTransaction(transaction.copy(projectId = pid))
        }
    }

    fun restoreRealData() {
        viewModelScope.launch {
            val uid = repository.syncManager.userId
            if (uid.isBlank()) return@launch
            val fs = com.google.firebase.firestore.FirebaseFirestore.getInstance()

            try {
                val txDocs = fs.collection("users").document(uid).collection("transactions").get().await()
                txDocs.documents.forEach { it.reference.delete().await() }
                repository.dao.deleteAllTransactions()
            } catch (e: Exception) { android.util.Log.e("Restore", "txn: ${e.message}") }

            val cashAccount = app.fynlo.data.model.Account(
                id = "1", name = "Cash in Hand", balance = 3962.0, type = "Cash"
            )
            val hdfcAccount = app.fynlo.data.model.Account(
                id = "2", name = "HDFC Bank", balance = 122500.0, type = "Bank"
            )
            try {
                repository.dao.insertAccount(cashAccount)
                repository.dao.insertAccount(hdfcAccount)
                fs.collection("users").document(uid).collection("accounts")
                    .document("1").set(mapOf("id" to "1", "name" to "Cash in Hand", "balance" to 3962.0, "type" to "Cash", "projectId" to "personal", "updatedAt" to System.currentTimeMillis())).await()
                fs.collection("users").document(uid).collection("accounts")
                    .document("2").set(mapOf("id" to "2", "name" to "HDFC Bank", "balance" to 122500.0, "type" to "Bank", "projectId" to "personal", "updatedAt" to System.currentTimeMillis())).await()
            } catch (e: Exception) { android.util.Log.e("Restore", "accounts: ${e.message}") }
        }
    }

    fun cleanupSeeederData() {
        viewModelScope.launch {
            val uid = repository.syncManager.userId
            if (uid.isBlank()) return@launch
            val fs = com.google.firebase.firestore.FirebaseFirestore.getInstance()

            try {
                val invDocs = fs.collection("users").document(uid).collection("investments").get().await()
                invDocs.documents.forEach { it.reference.delete().await() }
                repository.dao.deleteAllInvestments()
            } catch (e: Exception) { android.util.Log.e("Cleanup", "inv: ${e.message}") }

            try {
                val bDocs = fs.collection("users").document(uid).collection("borrowers").get().await()
                bDocs.documents.forEach { it.reference.delete().await() }
                repository.dao.deleteAllBorrowers()
            } catch (e: Exception) { android.util.Log.e("Cleanup", "borrowers: ${e.message}") }

            try {
                val txDocs = fs.collection("users").document(uid).collection("transactions").get().await()
                val seederDescs = setOf("Monthly Salary", "Grocery & Dining", "Petrol & Diesel",
                    "ATM Withdrawal", "Online Shopping", "Web Design Project",
                    "Miscellaneous", "Auto fuel", "Part time work",
                    "Lent to Ravi Kumar", "Lent to Suresh Babu", "Lent to Lakshmi Devi", "Lent to Mohan Rao",
                    "Loan from Home Loan EMI", "Loan from Personal Loan", "Loan from Gold Loan",
                    "Invested in Gold ETF", "Invested in LIC Policy", "Invested in FD - HDFC", "Invested in Mutual Fund SIP")
                txDocs.documents.forEach { doc ->
                    val desc = doc.getString("desc") ?: ""
                    if (desc in seederDescs) {
                        doc.reference.delete().await()
                        repository.dao.deleteTransactionById(doc.id)
                    }
                }
            } catch (e: Exception) { android.util.Log.e("Cleanup", "txn: ${e.message}") }

            try {
                val debtDocs = fs.collection("users").document(uid).collection("debts").get().await()
                debtDocs.documents.forEach { it.reference.delete().await() }
                repository.dao.deleteAllDebts()
            } catch (e: Exception) { android.util.Log.e("Cleanup", "debts: ${e.message}") }

            try {
                val seederAccIds = setOf("acc-cash", "acc-hdfc", "acc-sbi", "acc-petty")
                seederAccIds.forEach { id ->
                    fs.collection("users").document(uid).collection("accounts").document(id).delete().await()
                    repository.dao.deleteAccountById(id)
                }
            } catch (e: Exception) { android.util.Log.e("Cleanup", "accounts: ${e.message}") }
        }
    }

    fun loadDummyData() {
        viewModelScope.launch {
            val seeder = app.fynlo.logic.DummyDataSeeder
            repository.dao.deleteAllTransactions()
            repository.dao.deleteAllBorrowers()
            repository.dao.deleteAllDebts()
            repository.dao.deleteAllInvestments()
            repository.dao.deleteAllAccounts()

            val collections = listOf("transactions","borrowers","debts","investments","accounts","payments","debt_payments")
            val uid = repository.syncManager.userId
            if (uid.isNotBlank()) {
                val fs = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                collections.forEach { col ->
                    try {
                        val docs = fs.collection("users").document(uid).collection(col).get().await()
                        docs.documents.forEach { it.reference.delete() }
                    } catch (e: Exception) {
                        android.util.Log.e("Seeder", "Clear $col failed: ${e.message}")
                    }
                }
            }

            seeder.accounts().forEach { repository.upsertAccount(it) }
            seeder.borrowers().forEach { b ->
                repository.dao.insertBorrower(b)
                repository.sync { setBorrower(b) }
            }
            seeder.debts().forEach { d ->
                repository.dao.insertDebt(d)
                repository.sync { setDebt(d) }
            }
            seeder.investments().forEach { i ->
                repository.dao.insertInvestment(i)
                repository.sync { setInvestment(i) }
            }
            seeder.transactions().forEach { t ->
                repository.dao.insertTransaction(t)
                repository.sync { setTransaction(t) }
            }
            seeder.budgets().forEach { b ->
                repository.dao.insertBudget(b)
            }
        }
    }

    fun editTransaction(old: Transaction, new: Transaction) {
        viewModelScope.launch { repository.editTransaction(old, new) }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch { repository.deleteTransaction(transaction) }
    }

    fun deleteTransactions(transactions: List<Transaction>) {
        viewModelScope.launch {
            transactions.forEach { repository.deleteTransaction(it) }
        }
    }

    fun addDebtWithDestination(debt: Debt, destination: String) {
        viewModelScope.launch {
            repository.insertDebtWithDestination(debt.copy(projectId = pid), destination, pid)
        }
    }

    fun deleteDebt(debt: Debt) {
        viewModelScope.launch { repository.deleteDebt(debt) }
    }

    fun collectLoanPayment(payment: Payment, destination: String) {
        viewModelScope.launch {
            repository.insertPaymentWithDest(payment.copy(projectId = pid), destination, pid)
        }
    }

    fun payDebt(payment: DebtPayment, source: String) {
        viewModelScope.launch {
            repository.insertDebtPaymentWithSource(payment.copy(projectId = pid), source, pid)
        }
    }

    fun addPerson(person: Person) {
        viewModelScope.launch {
            repository.insertPerson(person.copy(projectId = pid))
        }
    }

    fun updatePerson(person: Person) {
        viewModelScope.launch {
            repository.insertPerson(person.copy(projectId = pid, updatedAt = System.currentTimeMillis()))
        }
    }

    fun deletePerson(person: Person) {
        viewModelScope.launch { repository.deletePerson(person) }
    }

    fun addBudget(budget: Budget) {
        viewModelScope.launch {
            repository.insertBudget(budget.copy(projectId = pid))
        }
    }

    fun deleteBudget(budget: Budget) {
        viewModelScope.launch { repository.deleteBudget(budget) }
    }

    fun quickEditBalance(accountName: String, newBalance: Double, oldBalance: Double) {
        viewModelScope.launch {
            repository.quickEditBalance(accountName, newBalance, oldBalance)
        }
    }

    fun addGoal(goal: Goal) {
        viewModelScope.launch {
            repository.insertGoal(goal.copy(projectId = pid))
        }
    }

    fun deleteGoal(goal: Goal) {
        viewModelScope.launch { repository.deleteGoal(goal) }
    }

    fun wipeAllData() {
        viewModelScope.launch { repository.wipeAllData() }
    }

    fun executeFlow(result: app.fynlo.data.model.FlowResult) {
        viewModelScope.launch {
            val id = java.util.UUID.randomUUID().toString()
            when (result.eventType) {
                "Received" -> {
                    val t = app.fynlo.data.model.Transaction(
                        id = id,
                        date = result.date,
                        type = "Income",
                        amount = result.amount,
                        toAcct = result.toAccount,
                        category = result.category,
                        desc = result.description,
                        notes = result.notes,
                        projectId = result.projectId
                    )
                    repository.insertTransaction(t)
                }
                "Spent" -> {
                    val t = app.fynlo.data.model.Transaction(
                        id = id,
                        date = result.date,
                        type = "Expense",
                        amount = result.amount,
                        fromAcct = result.fromAccount,
                        category = result.category,
                        desc = result.description,
                        notes = result.notes,
                        projectId = result.projectId
                    )
                    repository.insertTransaction(t)
                }
                "Moved" -> {
                    val t = app.fynlo.data.model.Transaction(
                        id = id,
                        date = result.date,
                        type = "Transfer",
                        amount = result.amount,
                        fromAcct = result.fromAccount,
                        toAcct = result.toAccount,
                        category = "Transfer",
                        desc = result.description.ifBlank { "Transfer: ${result.fromAccount} -> ${result.toAccount}" },
                        notes = result.notes,
                        projectId = result.projectId
                    )
                    repository.insertTransaction(t)
                }
                "Lent" -> {
                    val borrower = app.fynlo.data.model.Borrower(
                        id = id,
                        name = result.personName,
                        phone = result.personPhone,
                        amount = result.amount,
                        rate = 0.0,
                        date = result.date,
                        notes = result.notes,
                        projectId = result.projectId
                    )
                    repository.insertBorrowerWithSource(borrower, result.fromAccount, result.projectId)
                }
                "Borrowed" -> {
                    val debt = app.fynlo.data.model.Debt(
                        id = id,
                        name = result.personName,
                        phone = result.personPhone,
                        amount = result.amount,
                        rate = 0.0,
                        date = result.date,
                        notes = result.notes,
                        projectId = result.projectId
                    )
                    repository.insertDebtWithDestination(debt, result.toAccount, result.projectId)
                }
            }
        }
    }

    suspend fun exportAllData(): String = repository.getAllDataAsJson()

    fun restoreData(json: String) {
        viewModelScope.launch { repository.restoreDataFromJson(json) }
    }

    fun exportToCSV(): String =
        app.fynlo.logic.ExportUtility.generateCSV(
            transactions.value, borrowers.value, investments.value
        )

    fun exportToPDF(outputStream: java.io.OutputStream) {
        app.fynlo.logic.ExportUtility.generatePDF(
            outputStream, financialSummary.value,
            transactions.value, borrowers.value, investments.value
        )
    }

    fun getNetWorthSnapshots() = repository.getNetWorthSnapshots(pid)

    fun saveSnapshotNow() {
        viewModelScope.launch {
            val s = financialSummary.value
            repository.saveNetWorthSnapshot(
                app.fynlo.data.model.NetWorthSnapshot(
                    date             = today,
                    netWorth         = s.netWorth,
                    totalAssets      = s.totalAssets,
                    totalLiabilities = s.totalDebtPrincipal + s.totalDebtInterest,
                    projectId        = pid
                )
            )
        }
    }

    val recurringTransactions: StateFlow<List<app.fynlo.data.model.RecurringTransaction>> = repository.getAllRecurringTransactions()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addRecurringTransaction(r: app.fynlo.data.model.RecurringTransaction) {
        viewModelScope.launch { repository.insertRecurringTransaction(r) }
    }

    fun deleteRecurringTransaction(r: app.fynlo.data.model.RecurringTransaction) {
        viewModelScope.launch { repository.deleteRecurringTransaction(r) }
    }

    fun populateDummyData() {
        viewModelScope.launch(Dispatchers.IO) {
            val cash = Account("1", "Cash in Hand", "Cash",  5000.0,  projectId = pid)
            val bank = Account("2", "HDFC Bank",    "Bank",  45000.0, projectId = pid)
            repository.insertAccount(cash)
            repository.insertAccount(bank)

            val borrower1 = Borrower(
                "b1", "John Doe", amount = 10000.0, rate = 2.0,
                date = "2024-01-10", due = "2024-12-31", type = "Simple Interest",
                notes = "Personal loan for home renovation.", projectId = pid
            )
            repository.insertBorrowerWithSource(borrower1, "Cash in Hand", pid)

            val gold = Investment(
                "i1", "Gold Coins", "Gold", invested = 50000.0, currentVal = 58000.0,
                date = "2023-05-20", notes = "Bought from Tanishq. 24 Karat, 10gm.", projectId = pid
            )
            val stocks = Investment(
                "i2", "Nifty 50 ETF", "Stocks", invested = 20000.0, currentVal = 22500.0,
                date = "2024-02-15", notes = "Long term wealth creation.", projectId = pid
            )
            repository.insertInvestmentWithSource(gold,   "HDFC Bank", pid)
            repository.insertInvestmentWithSource(stocks, "HDFC Bank", pid)

            val debt1 = Debt(
                "d1", "Home Loan", amount = 150000.0, rate = 8.5,
                date = "2023-01-01", paid = 20000.0,
                notes = "Monthly EMI ₹2500. 15 years tenure.", projectId = pid
            )
            repository.insertDebtWithDestination(debt1, "HDFC Bank", pid)

            listOf(
                Transaction("t1", today, "Expense", 1200.0, fromAcct = "Cash in Hand", category = "Food",     notes = "Dinner at Barbeque Nation.", projectId = pid),
                Transaction("t2", today, "Expense", 2500.0, fromAcct = "HDFC Bank",    category = "Fuel",     notes = "Full tank refill.",           projectId = pid),
                Transaction("t3", today, "Income",  60000.0, toAcct  = "HDFC Bank",    category = "Salary",   notes = "April 2024 Salary.",           projectId = pid),
                Transaction("t4", today, "Expense", 800.0,  fromAcct = "Cash in Hand", category = "Shopping", notes = "New charger cable.",           projectId = pid)
            ).forEach { repository.insertTransaction(it) }
        }
    }
}
