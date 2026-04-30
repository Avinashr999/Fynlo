package com.example.cashmemo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cashmemo.data.FinanceRepository
import com.example.cashmemo.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FinanceViewModel(private val repository: FinanceRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val borrowers = repository.allBorrowers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val transactions = repository.allTransactions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val accounts = repository.allAccounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val investments = repository.allInvestments.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val debts = repository.allDebts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val people = repository.allPeople.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val budgets = repository.allBudgets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val goals = repository.allGoals.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredTransactions = combine(transactions, _searchQuery) { trans, query ->
        if (query.isEmpty()) trans
        else trans.filter { 
            it.category.contains(query, ignoreCase = true) || 
            it.desc.contains(query, ignoreCase = true) ||
            it.notes.contains(query, ignoreCase = true) ||
            it.date.contains(query)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    val expenseAnalytics: StateFlow<Map<String, Double>> = transactions.map { trans ->
        trans.filter { it.type.lowercase() == "expense" }
            .groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val financialSummary: StateFlow<FinancialSummary> = combine(
        transactions, accounts, investments, borrowers, debts
    ) { trans, accts, invs, brws, dbts ->
        val totalCash = accts.sumOf { it.balance }
        val totalInvestments = invs.sumOf { it.currentVal }
        val totalReceivables = brws.sumOf { b ->
            val accrued = com.example.cashmemo.logic.InterestEngine.calcIntAccrued(
                b.amount, b.rate, b.date, b.type, b.due,
                totalPaid = b.paid
            )
            b.amount + accrued - b.paid
        }
        
        val totalAssets = totalCash + totalInvestments + totalReceivables
        
        // Accurate Debts: Remaining Principal + Accrued Interest
        val totalDebtPrincipal = dbts.sumOf { it.amount - it.paid }
        val totalDebtInterest = dbts.sumOf { d ->
            com.example.cashmemo.logic.InterestEngine.calcIntAccrued(
                d.amount, d.rate, d.date, d.intType, d.due,
                totalPaid = d.paid // Ensure Engine knows about payments
            )
        }

        val totalExpenses = trans.filter { it.type.lowercase() == "expense" }.sumOf { it.amount }
        val totalIncome = trans.filter { it.type.lowercase() == "income" }.sumOf { it.amount }
        
        val invGrowth = invs.sumOf { it.currentVal - it.invested }
        val avgYield = if (brws.isNotEmpty()) brws.map { it.rate }.average() else 0.0
        val net = totalAssets - (totalDebtPrincipal + totalDebtInterest)
        
        val accountsMap = accts.associate { it.name to it.balance }

        FinancialSummary(
            totalCash = totalCash,
            totalInvestments = totalInvestments,
            totalReceivables = totalReceivables,
            totalAssets = totalAssets,
            totalDebtPrincipal = totalDebtPrincipal,
            totalDebtInterest = totalDebtInterest,
            totalExpenses = totalExpenses,
            totalIncome = totalIncome,
            netWorth = net,
            investmentGrowth = invGrowth,
            lendingYield = avgYield,
            debtBurden = if (net != 0.0) ((totalDebtPrincipal + totalDebtInterest) / net) * 100 else 0.0,
            accountBreakdown = accountsMap
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FinancialSummary())

    fun populateDummyData() {
        viewModelScope.launch(Dispatchers.IO) {
            // Accounts
            val cash = Account("1", "Cash in Hand", "Cash", 5000.0)
            val bank = Account("2", "HDFC Bank", "Bank", 45000.0)
            repository.insertAccount(cash)
            repository.insertAccount(bank)

            // Borrowers (Lending money to others)
            val borrower1 = Borrower(
                "b1", "John Doe", amount = 10000.0, rate = 2.0, 
                date = "2024-01-10", due = "2024-12-31", type = "Simple Interest",
                notes = "Personal loan for home renovation. Promised to return by December."
            )
            repository.insertBorrowerWithSource(borrower1, "Cash in Hand")

            // Investments
            val gold = Investment(
                "i1", "Gold Coins", "Gold", invested = 50000.0, currentVal = 58000.0, 
                date = "2023-05-20", notes = "Bought from Tanishq. 24 Karat, 10gm."
            )
            val stocks = Investment(
                "i2", "Nifty 50 ETF", "Stocks", invested = 20000.0, currentVal = 22500.0, 
                date = "2024-02-15", notes = "Long term wealth creation portfolio."
            )
            repository.insertInvestmentWithSource(gold, "HDFC Bank")
            repository.insertInvestmentWithSource(stocks, "HDFC Bank")

            // Debts (Money you owe)
            val debt1 = Debt(
                "d1", "Home Loan", amount = 150000.0, rate = 8.5, 
                date = "2023-01-01", paid = 20000.0, 
                notes = "Monthly EMI is ₹2500. 15 years tenure."
            )
            repository.insertDebtWithDestination(debt1, "HDFC Bank")

            // Transactions (Expenses & Income)
            repository.insertTransaction(Transaction("t1", "2024-04-20", "Expense", 1200.0, fromAcct = "Cash in Hand", category = "Food", notes = "Dinner with family at Barbeque Nation."))
            repository.insertTransaction(Transaction("t2", "2024-04-21", "Expense", 2500.0, fromAcct = "HDFC Bank", category = "Fuel", notes = "Full tank refill for the car."))
            repository.insertTransaction(Transaction("t3", "2024-04-22", "Income", 60000.0, toAcct = "HDFC Bank", category = "Salary", notes = "April 2024 Salary Credit."))
            repository.insertTransaction(Transaction("t4", "2024-04-23", "Expense", 800.0, fromAcct = "Cash in Hand", category = "Shopping", notes = "New charger cable from Amazon."))
        }
    }

    fun addDebtWithDestination(debt: Debt, destination: String) {
        viewModelScope.launch {
            repository.insertDebtWithDestination(debt, destination)
        }
    }

    fun addBorrowerWithSource(borrower: Borrower, source: String) {
        viewModelScope.launch {
            repository.insertBorrowerWithSource(borrower, source)
        }
    }

    fun deleteBorrower(borrower: Borrower) {
        viewModelScope.launch {
            repository.deleteBorrower(borrower)
        }
    }

    fun addInvestmentWithSource(investment: Investment, source: String) {
        viewModelScope.launch {
            repository.insertInvestmentWithSource(investment, source)
        }
    }

    fun deleteInvestment(investment: Investment) {
        viewModelScope.launch {
            repository.deleteInvestment(investment)
        }
    }

    fun addTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.insertTransaction(transaction)
        }
    }

    fun collectLoanPayment(payment: Payment, destination: String) {
        viewModelScope.launch {
            repository.insertPaymentWithDest(payment, destination)
        }
    }

    fun payDebt(payment: DebtPayment, source: String) {
        viewModelScope.launch {
            repository.insertDebtPaymentWithSource(payment, source)
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
        }
    }

    fun deleteDebt(debt: Debt) {
        viewModelScope.launch {
            repository.deleteDebt(debt)
        }
    }

    fun addPerson(person: Person) {
        viewModelScope.launch {
            repository.insertPerson(person)
        }
    }

    fun deletePerson(person: Person) {
        viewModelScope.launch {
            repository.deletePerson(person)
        }
    }

    fun addBudget(budget: Budget) {
        viewModelScope.launch {
            repository.insertBudget(budget)
        }
    }

    fun deleteBudget(budget: Budget) {
        viewModelScope.launch {
            repository.deleteBudget(budget)
        }
    }

    fun addGoal(goal: Goal) {
        viewModelScope.launch {
            repository.insertGoal(goal)
        }
    }

    fun deleteGoal(goal: Goal) {
        viewModelScope.launch {
            repository.deleteGoal(goal)
        }
    }

    fun updateBorrower(borrower: Borrower) {
        viewModelScope.launch {
            repository.insertBorrowerWithSource(borrower, "Cash in Hand") // Simplified update
        }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.insertTransaction(transaction)
        }
    }

    suspend fun exportAllData(): String {
        return repository.getAllDataAsJson()
    }

    fun restoreData(json: String) {
        viewModelScope.launch {
            repository.restoreDataFromJson(json)
        }
    }

    fun exportToCSV(): String {
        return com.example.cashmemo.logic.ExportUtility.generateCSV(
            transactions.value,
            borrowers.value,
            investments.value
        )
    }

    fun exportToPDF(outputStream: java.io.OutputStream) {
        com.example.cashmemo.logic.ExportUtility.generatePDF(
            outputStream,
            financialSummary.value,
            transactions.value,
            borrowers.value,
            investments.value
        )
    }
}