package com.example.cashmemo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cashmemo.data.FinanceRepository
import com.example.cashmemo.data.SyncStatus
import com.example.cashmemo.data.model.*
import com.example.cashmemo.data.model.FlowResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class FinanceViewModel(private val repository: FinanceRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Sync status forwarded from repository -> SyncManager
    val syncStatus: StateFlow<SyncStatus> = repository.syncStatus

    // ─── Active project ───────────────────────────────────────────────────────

    private val _currentProjectId = MutableStateFlow("")
    val currentProjectId: StateFlow<String> = _currentProjectId.asStateFlow()

    val projects = repository.allProjects
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // True once projects arrive OR after 10 second timeout
    private val _isSyncReady = MutableStateFlow(false)
    val isSyncReady: StateFlow<Boolean> = _isSyncReady.asStateFlow()

    init {
        // Watch projects — auto-select first one when it arrives
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
        // 10-second safety timeout — show dashboard even if Firestore is slow
        viewModelScope.launch {
            delay(10_000)
            if (!_isSyncReady.value) {
                if (projects.value.isEmpty()) {
                    val defaultProject = Project(
                        id        = "personal",
                        name      = "Personal",
                        icon      = "person",
                        color     = "#3b82f6",
                        currency  = "INR",
                        createdAt = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    )
                    repository.insertProject(defaultProject)
                    _currentProjectId.value = "personal"
                }
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
            // Fall back to personal if active project was deleted
            if (_currentProjectId.value == project.id) _currentProjectId.value = "personal"
        }
    }

    // ─── Project-filtered data flows ──────────────────────────────────────────
    // Each raw repository flow is combined with currentProjectId so every
    // downstream flow (financialSummary, expenseAnalytics, etc.) automatically
    // reflects the active project without any extra wiring.

    val borrowers: StateFlow<List<Borrower>> =
        combine(repository.allBorrowers, _currentProjectId) { list, pid ->
            list.filter { it.projectId == pid || it.projectId.isEmpty() || it.projectId == "personal" }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val transactions: StateFlow<List<Transaction>> =
        combine(repository.allTransactions, _currentProjectId) { list, pid ->
            list.filter { it.projectId == pid || it.projectId.isEmpty() || it.projectId == "personal" }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // All accounts across ALL projects — used by wizard and account pickers
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

    // ─── Search ───────────────────────────────────────────────────────────────

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

    // ─── Analytics ────────────────────────────────────────────────────────────

    val expenseAnalytics: StateFlow<Map<String, Double>> = transactions.map { trans ->
        trans.filter { it.type.lowercase() == "expense" }
            .groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // ─── Financial Summary (auto-uses project-filtered flows) ─────────────────

    val financialSummary: StateFlow<FinancialSummary> = combine(
        transactions, accounts, investments, borrowers, debts
    ) { trans, accts, invs, brws, dbts ->
        val totalCash        = accts.sumOf { it.balance }
        val totalInvestments = invs.sumOf { it.currentVal }
        val totalReceivables = brws.sumOf { b ->
            val accrued = com.example.cashmemo.logic.InterestEngine.calcIntAccrued(
                b.amount, b.rate, b.date, b.type, b.due, totalPaid = b.paid
            )
            b.amount + accrued - b.paid
        }
        val totalAssets       = totalCash + totalInvestments + totalReceivables
        val totalDebtPrincipal = dbts.sumOf { it.amount - it.paid }
        val totalDebtInterest  = dbts.sumOf { d ->
            com.example.cashmemo.logic.InterestEngine.calcIntAccrued(
                d.amount, d.rate, d.date, d.intType, d.due, totalPaid = d.paid
            )
        }
        val totalExpenses  = trans.filter { it.type.lowercase() == "expense" }.sumOf { it.amount }
        val totalIncome    = trans.filter { it.type.lowercase() == "income"  }.sumOf { it.amount }
        val invGrowth      = invs.sumOf { it.currentVal - it.invested }
        val avgYield       = if (brws.isNotEmpty()) brws.map { it.rate }.average() else 0.0
        val net            = totalAssets - (totalDebtPrincipal + totalDebtInterest)
        val accountsMap    = accts.associate { it.name to it.balance }

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
            accountBreakdown   = accountsMap
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FinancialSummary())

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private val today get() = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    private val pid   get() = _currentProjectId.value.ifEmpty { "personal" }

    // ─── Add / Delete actions ─────────────────────────────────────────────────

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
            repository.insertBorrowerWithSource(borrower.copy(projectId = pid), "Cash in Hand", pid)
        }
    }

    fun addInvestmentWithSource(investment: Investment, source: String) {
        viewModelScope.launch {
            repository.insertInvestmentWithSource(investment.copy(projectId = pid), source, pid)
        }
    }

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

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch { repository.deleteTransaction(transaction) }
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

    fun addGoal(goal: Goal) {
        viewModelScope.launch {
            repository.insertGoal(goal.copy(projectId = pid))
        }
    }

    fun deleteGoal(goal: Goal) {
        viewModelScope.launch { repository.deleteGoal(goal) }
    }

    fun executeFlow(result: com.example.cashmemo.data.model.FlowResult) {
        viewModelScope.launch {
            val id = java.util.UUID.randomUUID().toString()
            when (result.eventType) {
                "Received" -> {
                    val t = com.example.cashmemo.data.model.Transaction(
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
                    val t = com.example.cashmemo.data.model.Transaction(
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
                    val t = com.example.cashmemo.data.model.Transaction(
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
                    val borrower = com.example.cashmemo.data.model.Borrower(
                        id = id,
                        name = result.personName,
                        phone = result.personPhone,
                        amount = result.amount,
                        rate = 0.0, // Default for wizard
                        date = result.date,
                        notes = result.notes,
                        projectId = result.projectId
                    )
                    repository.insertBorrowerWithSource(borrower, result.fromAccount, result.projectId)
                }
                "Borrowed" -> {
                    val debt = com.example.cashmemo.data.model.Debt(
                        id = id,
                        name = result.personName,
                        phone = result.personPhone,
                        amount = result.amount,
                        rate = 0.0, // Default for wizard
                        date = result.date,
                        notes = result.notes,
                        projectId = result.projectId
                    )
                    repository.insertDebtWithDestination(debt, result.toAccount, result.projectId)
                }
            }
        }
    }

    // ─── Export / Import ──────────────────────────────────────────────────────

    suspend fun exportAllData(): String = repository.getAllDataAsJson()

    fun restoreData(json: String) {
        viewModelScope.launch { repository.restoreDataFromJson(json) }
    }

    fun exportToCSV(): String =
        com.example.cashmemo.logic.ExportUtility.generateCSV(
            transactions.value, borrowers.value, investments.value
        )

    fun exportToPDF(outputStream: java.io.OutputStream) {
        com.example.cashmemo.logic.ExportUtility.generatePDF(
            outputStream, financialSummary.value,
            transactions.value, borrowers.value, investments.value
        )
    }

    // ─── Sample data ──────────────────────────────────────────────────────────

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
