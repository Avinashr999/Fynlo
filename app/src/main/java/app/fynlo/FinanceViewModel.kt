package app.fynlo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.fynlo.data.FinanceRepository
import app.fynlo.data.ProjectScope
import app.fynlo.data.RecalcCoordinator
import app.fynlo.data.RecentlyUsedTracker
import app.fynlo.data.RepositoryHelper
import app.fynlo.data.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import app.fynlo.data.model.*
import app.fynlo.logic.LedgerAccountability
import app.fynlo.logic.LedgerAccountabilityReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import app.fynlo.delegate.*

@HiltViewModel
class FinanceViewModel @Inject constructor(
    private val repository: FinanceRepository,
    private val recalcCoordinator: RecalcCoordinator,
    private val recentlyUsedTracker: RecentlyUsedTracker,
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _feedbackEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val feedbackEvents: SharedFlow<String> = _feedbackEvents.asSharedFlow()

    fun showFeedback(message: String) {
        _feedbackEvents.tryEmit(message)
    }

    val syncStatus: StateFlow<SyncStatus>
        get() = repository.syncStatus

    private fun runMoneyAction(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { block() }
                .onFailure { e ->
                    val message = if (e is FinanceRepository.ClosedPeriodException || e is RepositoryHelper.ClosedPeriodException) {
                        e.message ?: "This month is closed. Reopen it before editing."
                    } else {
                        e.message ?: "Action could not be completed."
                    }
                    _feedbackEvents.tryEmit(message)
                }
        }
    }

    val projectSyncDelegate = ProjectSyncDelegate(
        ViewModelContext(
            repository = repository, recalcCoordinator = recalcCoordinator, recentlyUsedTracker = recentlyUsedTracker, context = context,
            scope = viewModelScope, showFeedback = ::showFeedback, runMoneyAction = ::runMoneyAction,
            currentProjectId = { "" }, today = { LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) }
        )
    )

    private val pid get() = projectSyncDelegate.currentProjectId.value.ifEmpty { "personal" }
    private val today get() = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    
    // Core context with properly resolved pid
    private val ctx = ViewModelContext(
        repository = repository, recalcCoordinator = recalcCoordinator, recentlyUsedTracker = recentlyUsedTracker, context = context,
        scope = viewModelScope, showFeedback = ::showFeedback, runMoneyAction = ::runMoneyAction,
        currentProjectId = ::pid, today = ::today
    )

    val isPrivacyMode = projectSyncDelegate.isPrivacyMode
    fun togglePrivacyMode() = projectSyncDelegate.togglePrivacyMode()
    val currentProjectId = projectSyncDelegate.currentProjectId
    val projects = projectSyncDelegate.projects
    val isSyncReady = projectSyncDelegate.isSyncReady
    val currentProject = projectSyncDelegate.currentProject
    fun switchProject(projectId: String) = projectSyncDelegate.switchProject(projectId)
    fun createProject(project: Project) = projectSyncDelegate.createProject(project)
    fun deleteProject(project: Project) = projectSyncDelegate.deleteProject(project)
    fun restoreRealData() = projectSyncDelegate.restoreRealData()
    fun cleanupSeeederData() = projectSyncDelegate.cleanupSeeederData()
    fun loadDummyData() = projectSyncDelegate.loadDummyData()
    fun populateDummyData() = projectSyncDelegate.populateDummyData()
    fun refresh(onComplete: () -> Unit) = projectSyncDelegate.refresh(onComplete)
    fun wipeAllData() = projectSyncDelegate.wipeAllData()
    fun resetCloudSyncToLocal(onComplete: (Boolean) -> Unit) = projectSyncDelegate.resetCloudSyncToLocal(onComplete)
    fun resetAllData(context: android.content.Context, authManager: app.fynlo.data.AuthManager, onComplete: () -> Unit) = projectSyncDelegate.resetAllData(context, authManager, onComplete)
    fun deleteAccountPermanently(authManager: app.fynlo.data.AuthManager, onResult: (Boolean) -> Unit) = projectSyncDelegate.deleteAccountPermanently(authManager, onResult)

    val borrowers: StateFlow<List<Borrower>> = combine(repository.allBorrowers, currentProjectId) { list, pid ->
        list.filter { ProjectScope.belongsToSelectedProject(it.projectId, pid) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val transactions: StateFlow<List<Transaction>> = combine(repository.allTransactions, currentProjectId) { list, pid ->
        list.filter { ProjectScope.belongsToSelectedProject(it.projectId, pid) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allAccountsUnfiltered: StateFlow<List<Account>> = repository.allAccounts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val accounts: StateFlow<List<Account>> = combine(repository.allAccounts, currentProjectId) { list, pid ->
        list.filter { ProjectScope.belongsToSelectedProject(it.projectId, pid) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val investments: StateFlow<List<Investment>> = combine(repository.allInvestments, currentProjectId) { list, pid ->
        list.filter { ProjectScope.belongsToSelectedProject(it.projectId, pid) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val debts: StateFlow<List<Debt>> = combine(repository.allDebts, currentProjectId) { list, pid ->
        list.filter { ProjectScope.belongsToSelectedProject(it.projectId, pid) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val payments: StateFlow<List<Payment>> = repository.allPayments.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val debtPayments: StateFlow<List<DebtPayment>> = repository.allDebtPayments.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val people: StateFlow<List<Person>> = combine(repository.allPeople, currentProjectId) { list, pid ->
        list.filter { ProjectScope.belongsToSelectedProject(it.projectId, pid) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val budgets: StateFlow<List<Budget>> = combine(repository.allBudgets, currentProjectId) { list, pid ->
        list.filter { ProjectScope.belongsToSelectedProject(it.projectId, pid) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val goals: StateFlow<List<Goal>> = combine(repository.allGoals, currentProjectId) { list, pid ->
        list.filter { ProjectScope.belongsToSelectedProject(it.projectId, pid) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val valuations: StateFlow<List<InvestmentValuation>> = combine(repository.allValuations, currentProjectId) { list, pid ->
        list.filter { ProjectScope.belongsToSelectedProject(it.projectId, pid) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val auditEvents: StateFlow<List<AuditEvent>> = combine(repository.allAuditEvents, currentProjectId) { list, pid ->
        list.filter { ProjectScope.belongsToSelectedProject(it.projectId, pid) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val monthlyCloses: StateFlow<List<MonthlyClose>> = combine(repository.allMonthlyCloses, currentProjectId) { list, pid ->
        list.filter { ProjectScope.belongsToSelectedProject(it.projectId, pid) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val openSyncConflicts: StateFlow<List<SyncConflict>> = combine(repository.openSyncConflicts, currentProjectId) { list, pid ->
        list.filter { ProjectScope.belongsToSelectedProject(it.projectId, pid) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val proofAttachments: StateFlow<List<ProofAttachment>> = combine(repository.allProofAttachments, currentProjectId) { list, pid ->
        list.filter { ProjectScope.belongsToSelectedProject(it.projectId, pid) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val ledgerAccountabilityReport: StateFlow<LedgerAccountabilityReport> = combine(
        combine(accounts, transactions, borrowers) { accts, txns, loans -> Triple(accts, txns, loans) },
        combine(debts, investments, payments) { debtRows, investmentRows, loanPayments -> Triple(debtRows, investmentRows, loanPayments) },
        combine(debtPayments, syncStatus) { debtPaymentRows, sync -> debtPaymentRows to sync },
    ) { left, middle, right ->
        LedgerAccountability.inspect(
            accounts = left.first, transactions = left.second, borrowers = left.third,
            debts = middle.first, investments = middle.second, payments = middle.third,
            debtPayments = right.first, syncStatus = right.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LedgerAccountability.inspect(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), SyncStatus.Initialising))

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.fixPaidDoubleCount()
            repository.repairDeletedAuditResidue()
            repository.repairDebtFundedInvestmentTransferTraces()
            repository.repairDebtFundedInvestmentJournalTraceRefs()
            repository.repairDebtReceiptAmountMismatches()
            repository.repairTransactionAccountIds()
            repository.repairAccountBalanceDriftFromLedger()
            repository.fixPaidDoubleCount()
        }
    }

    // Delegates
    val transactionDelegate = TransactionDelegate(ctx, transactions)
    val searchQuery = transactionDelegate.searchQuery
    val filteredTransactions = transactionDelegate.filteredTransactions
    fun updateSearchQuery(query: String) = transactionDelegate.updateSearchQuery(query)
    fun addTransaction(transaction: Transaction) = transactionDelegate.addTransaction(transaction)
    fun updateTransaction(transaction: Transaction) = transactionDelegate.updateTransaction(transaction)
    fun editTransaction(old: Transaction, new: Transaction) = transactionDelegate.editTransaction(old, new)
    fun deleteTransaction(transaction: Transaction) = transactionDelegate.deleteTransaction(transaction)
    fun deleteTransactions(transactions: List<Transaction>) = transactionDelegate.deleteTransactions(transactions)
    fun executeFlow(result: FlowResult) = transactionDelegate.executeFlow(result)

    val lendingDelegate = LendingDelegate(ctx)
    fun addBorrowerWithSource(borrower: Borrower, source: String) = lendingDelegate.addBorrowerWithSource(borrower, source)
    fun deleteBorrower(borrower: Borrower) = lendingDelegate.deleteBorrower(borrower)
    fun updateBorrower(borrower: Borrower) = lendingDelegate.updateBorrower(borrower)
    fun updateBorrowerWithSource(borrower: Borrower, source: String) = lendingDelegate.updateBorrowerWithSource(borrower, source)
    fun restoreBorrowerToActive(borrower: Borrower) = lendingDelegate.restoreBorrowerToActive(borrower)
    fun markBorrowerDefaulted(borrower: Borrower) = lendingDelegate.markBorrowerDefaulted(borrower)
    fun writeOffBorrower(borrower: Borrower) = lendingDelegate.writeOffBorrower(borrower)
    fun collectLoanPayment(payment: Payment, destination: String) = lendingDelegate.collectLoanPayment(payment, destination)
    fun waiveBorrowerInterest(borrower: Borrower, amount: Double, reason: String) = lendingDelegate.waiveBorrowerInterest(borrower, amount, reason)

    val debtDelegate = DebtDelegate(ctx)
    fun updateDebt(debt: Debt) = debtDelegate.updateDebt(debt)
    fun updateDebtWithDestination(debt: Debt, destination: String) = debtDelegate.updateDebtWithDestination(debt, destination)
    fun addDebtWithDestination(debt: Debt, destination: String) = debtDelegate.addDebtWithDestination(debt, destination)
    fun deleteDebt(debt: Debt) = debtDelegate.deleteDebt(debt)
    fun payDebt(payment: DebtPayment, source: String) = debtDelegate.payDebt(payment, source)
    fun waiveDebtInterest(debt: Debt, amount: Double, reason: String) = debtDelegate.waiveDebtInterest(debt, amount, reason)

    val investmentDelegate = InvestmentDelegate(ctx)
    fun withdrawFromInvestment(investment: Investment, amount: Double, toAccount: String) = investmentDelegate.withdrawFromInvestment(investment, amount, toAccount)
    fun addInvestmentFundedByAccount(investment: Investment, accountName: String, accountId: String = "") = investmentDelegate.addInvestmentFundedByAccount(investment, accountName, accountId)
    fun addInvestmentFundedByExistingDebt(investment: Investment, debt: Debt) = investmentDelegate.addInvestmentFundedByExistingDebt(investment, debt)
    fun addInvestmentFundedByNewLoan(investment: Investment, newDebt: Debt) = investmentDelegate.addInvestmentFundedByNewLoan(investment, newDebt)
    fun addInvestmentWithSource(investment: Investment, source: String) = investmentDelegate.addInvestmentWithSource(investment, source)
    fun updateInvestmentValue(investment: Investment, newCurrentVal: Double) = investmentDelegate.updateInvestmentValue(investment, newCurrentVal)
    fun updateInvestment(investment: Investment) = investmentDelegate.updateInvestment(investment)
    fun updateInvestmentFundedByAccount(investment: Investment, accountName: String, accountId: String = "") = investmentDelegate.updateInvestmentFundedByAccount(investment, accountName, accountId)
    fun updateInvestmentFundedByExistingDebt(investment: Investment, debt: Debt) = investmentDelegate.updateInvestmentFundedByExistingDebt(investment, debt)
    fun executeLinkedInvestment(investment: Investment, fundingSourceType: String, sourceName: String, debtDetails: Debt?= null) = investmentDelegate.executeLinkedInvestment(investment, fundingSourceType, sourceName, debtDetails)
    fun addValuation(v: InvestmentValuation) = investmentDelegate.addValuation(v)
    fun getValuationsForInvestment(invId: String) = investmentDelegate.getValuationsForInvestment(invId)
    fun deleteInvestment(investment: Investment) = investmentDelegate.deleteInvestment(investment)
    fun deleteInvestmentAndReverseAccount(investment: Investment) = investmentDelegate.deleteInvestmentAndReverseAccount(investment)
    fun deleteInvestmentAndLinkedLoan(investment: Investment) = investmentDelegate.deleteInvestmentAndLinkedLoan(investment)

    val accountDelegate = AccountDelegate(ctx)
    fun quickEditBalance(accountName: String, newBalance: Double, oldBalance: Double, accountId: String = "") = accountDelegate.quickEditBalance(accountName, newBalance, oldBalance, accountId)
    fun saveAccountFromDialog(previous: Account?, account: Account) = accountDelegate.saveAccountFromDialog(previous, account)
    fun saveAccount(account: Account) = accountDelegate.saveAccount(account)
    fun transferBetweenAccounts(from: Account, to: Account, amount: Double) = accountDelegate.transferBetweenAccounts(from, to, amount)
    fun closeAccount(account: Account) = accountDelegate.closeAccount(account)
    fun saveAndCloseAccountFromDialog(previous: Account?, account: Account) = accountDelegate.saveAndCloseAccountFromDialog(previous, account)
    fun deleteUnusedAccount(account: Account) = accountDelegate.deleteUnusedAccount(account)

    val planningDelegate = PlanningDelegate(ctx)
    fun addPerson(person: Person) = planningDelegate.addPerson(person)
    fun updatePerson(person: Person) = planningDelegate.updatePerson(person)
    fun deletePerson(person: Person) = planningDelegate.deletePerson(person)
    fun addBudget(budget: Budget) = planningDelegate.addBudget(budget)
    fun deleteBudget(budget: Budget) = planningDelegate.deleteBudget(budget)
    fun addGoal(goal: Goal) = planningDelegate.addGoal(goal)
    fun deleteGoal(goal: Goal) = planningDelegate.deleteGoal(goal)
    val recurringTransactions = ctx.repository.getAllRecurringTransactions().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    fun addRecurringTransaction(r: RecurringTransaction) = planningDelegate.addRecurringTransaction(r)
    fun deleteRecurringTransaction(r: RecurringTransaction) = planningDelegate.deleteRecurringTransaction(r)
    fun triggerDueRecurring() = planningDelegate.triggerDueRecurring()

    val financialAnalyticsDelegate = FinancialAnalyticsDelegate(ctx, transactions, accounts, investments, borrowers, debts, valuations, payments, debtPayments)
    val expenseAnalytics = financialAnalyticsDelegate.expenseAnalytics
    val financialSummary = financialAnalyticsDelegate.financialSummary
    fun getNetWorthSnapshots() = financialAnalyticsDelegate.getNetWorthSnapshots()
    fun saveSnapshotNow() = financialAnalyticsDelegate.saveSnapshotNow()
    fun backfillNetWorthHistory(onDone: (Int) -> Unit = {}) = financialAnalyticsDelegate.backfillNetWorthHistory(onDone)

    val exportDelegate = ExportDelegate(ctx, accounts, transactions, borrowers, debts, investments, people, budgets, goals, payments, debtPayments, financialSummary, auditEvents, currentProject)
    fun exportAuditTrailCsv() = exportDelegate.exportAuditTrailCsv()
    suspend fun exportAllData() = exportDelegate.exportAllData()
    fun restoreData(json: String) = exportDelegate.restoreData(json)
    suspend fun restoreDataNow(json: String) = exportDelegate.restoreDataNow(json)
    suspend fun exportToCSV() = exportDelegate.exportToCSV()
    suspend fun exportDataToCSV(scope: String) = exportDelegate.exportDataToCSV(scope)
    suspend fun exportToPDF(outputStream: java.io.OutputStream, dateFormat: String = app.fynlo.logic.DateUtils.DEFAULT_COMPACT_PATTERN) = exportDelegate.exportToPDF(outputStream, dateFormat)
    suspend fun exportDataToPDF(outputStream: java.io.OutputStream, scope: String, dateFormat: String = app.fynlo.logic.DateUtils.DEFAULT_COMPACT_PATTERN) = exportDelegate.exportDataToPDF(outputStream, scope, dateFormat)
    suspend fun exportToXLSX(outputStream: java.io.OutputStream, dateFormat: String = app.fynlo.logic.DateUtils.DEFAULT_COMPACT_PATTERN) = exportDelegate.exportToXLSX(outputStream, dateFormat)

    val bookRepairDelegate = BookRepairDelegate(ctx, { financialSummary.value }, financialSummary)
    fun closeMonth(month: String, note: String = "") = bookRepairDelegate.closeMonth(month, note)
    fun reopenMonth(month: String, note: String = "") = bookRepairDelegate.reopenMonth(month, note)
    fun undoLastMoneyAction() = bookRepairDelegate.undoLastMoneyAction()
    fun resolveSyncConflict(id: String, resolution: String) = bookRepairDelegate.resolveSyncConflict(id, resolution)
    fun addProofAttachment(ownerType: String, ownerId: String, displayName: String, mimeType: String, localUri: String, note: String = "") = bookRepairDelegate.addProofAttachment(ownerType, ownerId, displayName, mimeType, localUri, note)
    fun deleteProofAttachment(id: String) = bookRepairDelegate.deleteProofAttachment(id)
    fun recalculateAllBalances() = bookRepairDelegate.recalculateAllBalances()
    suspend fun recalculateAllBalancesCapturingDelta() = bookRepairDelegate.recalculateAllBalancesCapturingDelta()
    fun runSafeBookRepair(onComplete: (BookRepairResult) -> Unit) = bookRepairDelegate.runSafeBookRepair(onComplete)

    val smartPrefillDelegate = SmartPrefillDelegate(ctx, expenseAnalytics, budgets)
    suspend fun rememberLastTransactionCategory(isIncome: Boolean) = smartPrefillDelegate.rememberLastTransactionCategory(isIncome)
    fun recordTransactionCategory(isIncome: Boolean, category: String) = smartPrefillDelegate.recordTransactionCategory(isIncome, category)
    suspend fun rememberLastRecurringCategory(isIncome: Boolean) = smartPrefillDelegate.rememberLastRecurringCategory(isIncome)
    fun recordRecurringCategory(isIncome: Boolean, category: String) = smartPrefillDelegate.recordRecurringCategory(isIncome, category)
    fun suggestBudgetCategory() = smartPrefillDelegate.suggestBudgetCategory()
    suspend fun rememberLastBudgetCategory() = smartPrefillDelegate.rememberLastBudgetCategory()
    fun recordBudgetCategory(category: String) = smartPrefillDelegate.recordBudgetCategory(category)
    suspend fun rememberLastCurrencyOrLocale(locale: java.util.Locale = java.util.Locale.getDefault()) = smartPrefillDelegate.rememberLastCurrencyOrLocale(locale)
    fun recordCurrency(code: String) = smartPrefillDelegate.recordCurrency(code)
    fun observeRecentCurrencies(n: Int = 5) = smartPrefillDelegate.observeRecentCurrencies(n)
}
