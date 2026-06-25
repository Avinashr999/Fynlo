package app.fynlo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.edit
import app.fynlo.data.FinanceRepository
import app.fynlo.data.ProjectScope
import app.fynlo.data.RecalcCoordinator
import app.fynlo.data.RecentlyUsedTracker
import app.fynlo.data.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import app.fynlo.data.model.*
import app.fynlo.data.model.FlowResult
import app.fynlo.logic.XirrCalculator
import app.fynlo.logic.CagrCalculator
import app.fynlo.logic.LedgerAccountability
import app.fynlo.logic.LedgerAccountabilityReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@HiltViewModel
class FinanceViewModel @Inject constructor(
    private val repository: FinanceRepository,
    private val recalcCoordinator: RecalcCoordinator,
    private val recentlyUsedTracker: RecentlyUsedTracker,
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    val isPrivacyMode: StateFlow<Boolean> = app.fynlo.data.UserPreferences.privacyModeEnabled(context)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun togglePrivacyMode() {
        viewModelScope.launch {
            app.fynlo.data.UserPreferences.setPrivacyModeEnabled(context, !isPrivacyMode.value)
        }
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _feedbackEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val feedbackEvents: SharedFlow<String> = _feedbackEvents.asSharedFlow()

    fun showFeedback(message: String) {
        _feedbackEvents.tryEmit(message)
    }

    val syncStatus: StateFlow<SyncStatus>
        get() = repository.syncStatus

    private val _currentProjectId = MutableStateFlow("")
    val currentProjectId: StateFlow<String> = _currentProjectId.asStateFlow()

    val projects = repository.allProjects
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _isSyncReady = MutableStateFlow(false)
    val isSyncReady: StateFlow<Boolean> = _isSyncReady.asStateFlow()

    init {
        // Auto-fix: recalculate paid = paidPrincipal + paidInterest on every startup
        // This corrects any double-counted records from the pre-fix payment engine
        viewModelScope.launch(Dispatchers.IO) {
            repository.fixPaidDoubleCount()
            repository.repairDeletedAuditResidue()
            repository.fixPaidDoubleCount()
        }
        viewModelScope.launch(Dispatchers.IO) {
            projects.collect { list ->
                if (_currentProjectId.value.isEmpty() && list.isNotEmpty()) {
                    _currentProjectId.value = list.first().id
                }
                if (list.isNotEmpty()) {
                    _isSyncReady.value = true
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
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
        viewModelScope.launch(Dispatchers.IO) { repository.insertProject(project) }
    }

    fun deleteProject(project: Project) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteProject(project)
            if (_currentProjectId.value == project.id) _currentProjectId.value = "personal"
        }
    }

    val borrowers: StateFlow<List<Borrower>> =
        combine(repository.allBorrowers, _currentProjectId) { list, pid ->
            list.filter { ProjectScope.belongsToSelectedProject(it.projectId, pid) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val transactions: StateFlow<List<Transaction>> =
        combine(repository.allTransactions, _currentProjectId) { list, pid ->
            list.filter { ProjectScope.belongsToSelectedProject(it.projectId, pid) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allAccountsUnfiltered: StateFlow<List<Account>> =
        repository.allAccounts
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val accounts: StateFlow<List<Account>> =
        combine(repository.allAccounts, _currentProjectId) { list, pid ->
            list.filter { ProjectScope.belongsToSelectedProject(it.projectId, pid) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val investments: StateFlow<List<Investment>> =
        combine(repository.allInvestments, _currentProjectId) { list, pid ->
            list.filter { ProjectScope.belongsToSelectedProject(it.projectId, pid) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val debts: StateFlow<List<Debt>> =
        combine(repository.allDebts, _currentProjectId) { list, pid ->
            list.filter { ProjectScope.belongsToSelectedProject(it.projectId, pid) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val payments: StateFlow<List<Payment>> =
        repository.allPayments.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val debtPayments: StateFlow<List<DebtPayment>> =
        repository.allDebtPayments.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val people: StateFlow<List<Person>> =
        combine(repository.allPeople, _currentProjectId) { list, pid ->
            list.filter { ProjectScope.belongsToSelectedProject(it.projectId, pid) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val budgets: StateFlow<List<Budget>> =
        combine(repository.allBudgets, _currentProjectId) { list, pid ->
            list.filter { ProjectScope.belongsToSelectedProject(it.projectId, pid) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val goals: StateFlow<List<Goal>> =
        combine(repository.allGoals, _currentProjectId) { list, pid ->
            list.filter { ProjectScope.belongsToSelectedProject(it.projectId, pid) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val valuations: StateFlow<List<InvestmentValuation>> =
        combine(repository.allValuations, _currentProjectId) { list, pid ->
            list.filter { ProjectScope.belongsToSelectedProject(it.projectId, pid) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val ledgerAccountabilityReport: StateFlow<LedgerAccountabilityReport> =
        combine(
            combine(accounts, transactions, borrowers) { accts, txns, loans ->
                Triple(accts, txns, loans)
            },
            combine(debts, investments, payments) { debtRows, investmentRows, loanPayments ->
                Triple(debtRows, investmentRows, loanPayments)
            },
            combine(debtPayments, syncStatus) { debtPaymentRows, sync ->
                debtPaymentRows to sync
            },
        ) { left, middle, right ->
            LedgerAccountability.inspect(
                accounts = left.first,
                transactions = left.second,
                borrowers = left.third,
                debts = middle.first,
                investments = middle.second,
                payments = middle.third,
                debtPayments = right.first,
                syncStatus = right.second,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            LedgerAccountability.inspect(
                accounts = emptyList(),
                transactions = emptyList(),
                borrowers = emptyList(),
                debts = emptyList(),
                investments = emptyList(),
                payments = emptyList(),
                debtPayments = emptyList(),
                syncStatus = SyncStatus.Initialising,
            ),
        )

    val auditEvents: StateFlow<List<AuditEvent>> =
        combine(repository.allAuditEvents, _currentProjectId) { list, pid ->
            list.filter { ProjectScope.belongsToSelectedProject(it.projectId, pid) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun exportAuditTrailCsv(): String {
        fun csvCell(value: String): String = "\"${value.replace("\"", "\"\"")}\""
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        return buildString {
            appendLine("Timestamp,Action,Entity Type,Entity Id,Title,Account,Amount Delta,Before,After,Reason")
            auditEvents.value.forEach { event ->
                appendLine(
                    listOf(
                        dateFormat.format(java.util.Date(event.timestamp)),
                        event.action,
                        event.entityType,
                        event.entityId,
                        event.title,
                        event.accountName,
                        event.amountDelta.toString(),
                        event.beforeValue,
                        event.afterValue,
                        event.reason,
                    ).joinToString(",") { csvCell(it) }
                )
            }
        }
    }

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
        transactions, accounts, investments, borrowers, debts, valuations
    ) { args: Array<List<*>> ->
        val trans = args[0].requireTypedList<Transaction>()
        val accts = args[1].requireTypedList<Account>()
        val invs  = args[2].requireTypedList<Investment>()
        val brws  = args[3].requireTypedList<Borrower>()
        val dbts  = args[4].requireTypedList<Debt>()
        val vals  = args[5].requireTypedList<InvestmentValuation>()

        val totalCashVal     = accts.sumOf { it.balance }
        val totalInvestVal   = invs.sumOf { it.currentVal }

        // Exclude written-off borrowers from receivables (they're bad debt)
        val activeBrws = brws.filter { it.status != "WrittenOff" }

        val totalReceivables = activeBrws.sumOf { b ->
            val accrued = if (b.status == "Defaulted" && b.frozenInterest > 0)
                b.frozenInterest  // frozen at default date — no further accrual
            else
                app.fynlo.logic.InterestEngine.calcIntAccrued(
                    b.amount, b.rate, b.date, b.intType, b.due,
                    totalPaid = b.paidPrincipal  // only principal reduces interest base
                )
            app.fynlo.logic.InterestEngine.calcOutstanding(
                b.amount, accrued, b.paidPrincipal, b.paidInterest
            )
        }

        val totalInterestLoans = activeBrws.filter { it.rate > 0 }.sumOf { b ->
            val accrued = if (b.status == "Defaulted" && b.frozenInterest > 0) b.frozenInterest
            else app.fynlo.logic.InterestEngine.calcIntAccrued(
                b.amount, b.rate, b.date, b.intType, b.due, totalPaid = b.paidPrincipal
            )
            // Derive paidInterest from (paid - paidPrincipal) — more reliable than paidInterest field
            // which can get out of sync when rebuild queries run
            val derivedPaidInterest = (b.paid - b.paidPrincipal).coerceAtLeast(0.0)
            app.fynlo.logic.InterestEngine.calcOutstanding(b.amount, accrued, b.paidPrincipal, derivedPaidInterest)
        }
        // Hand loans: use 'paid' (mirrors isActive check for hand loans)
        // Interest loans are not counted here (they have a separate totalInterestLoans)
        val totalHandLoans = activeBrws.filter { it.rate <= 0 }.sumOf { b ->
            (b.amount - b.paid).coerceAtLeast(0.0)  // consistent with isActive: paid < amount
        }

        val invTypeMap = invs.groupBy { it.type }
            .mapValues { it.value.sumOf { inv -> inv.currentVal } }

        val interestBrwMap = activeBrws.filter { it.rate > 0 }.associate { b ->
            val accrued = if (b.status == "Defaulted" && b.frozenInterest > 0) b.frozenInterest
            else app.fynlo.logic.InterestEngine.calcIntAccrued(
                b.amount, b.rate, b.date, b.intType, b.due, totalPaid = b.paidPrincipal
            )
            b.name to app.fynlo.logic.InterestEngine.calcOutstanding(b.amount, accrued, b.paidPrincipal, b.paidInterest)
        }

        val handBrwMap = activeBrws.filter { it.rate <= 0 }.associate { b ->
            b.name to (b.amount - b.paidPrincipal).coerceAtLeast(0.0)
        }

        // Use totalInterestLoans + totalHandLoans (both correctly use paid/paidPrincipal per type)
        // totalReceivables uses paidPrincipal for all loans which is wrong for hand loans
        val totalAssets       = totalCashVal + totalInvestVal + totalInterestLoans + totalHandLoans
        val debtLiabilities = dbts.map { app.fynlo.logic.DebtLiabilityCalculator.outstanding(it) }
        val totalDebtPrincipal = debtLiabilities.sumOf { it.principal }
        val totalDebtInterest  = debtLiabilities.sumOf { it.interest }
        // Exclude journal_only entries (Bad Debt write-offs, Interest Expense P&L entries)
        // from cash flow totals — they are accounting entries, not actual cash movements
        val cashTrans     = trans.filter { it.tags != "journal_only" }
        val totalExpenses = cashTrans.filter { it.type.lowercase() == "expense" }.sumOf { it.amount }
        val totalIncome   = cashTrans.filter { it.type.lowercase() == "income"  }.sumOf { it.amount }
        // P&L includes journal entries (bad debts + interest expense are real economic costs)
        val totalBadDebtWriteOffs = trans.filter { it.category == "Bad Debt" }.sumOf { it.amount }
        val totalInterestExpense  = trans.filter { it.category == "Interest Expense" }.sumOf { it.amount }
        val totalInterestIncome   = trans.filter { it.category == "Loan Repayment" }.sumOf { it.amount }
        val invGrowth      = invs.sumOf { it.currentVal - (it.invested - it.withdrawn) }

        // C14 #5 (3.2.82) — Yield on ACTIVE interest-bearing loans only.
        val interestBearing = activeBrws.filter { it.rate > 0 }
        val avgYield       = if (interestBearing.isNotEmpty()) interestBearing.map { it.rate }.average() else 0.0

        val net            = totalAssets - (totalDebtPrincipal + totalDebtInterest)
        val accountsMap    = accts.associate { it.name to it.balance }

        // ── Performance Analytics (XIRR/CAGR) ──────────────────────────────────
        // C14 #5 (3.2.82) — Portfolio-wide annualised metrics.
        val todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        // 1. Investment Performance
        val invCagr = CagrCalculator.portfolio(
            invs.map { Triple(it.invested, it.currentVal, it.date) }
        )

        val invCashflows = mutableListOf<XirrCalculator.Cashflow>()
        val invIds = invs.map { it.id }.toSet()

        invs.forEach { inv ->
            // Initial investment (negative cashflow)
            if (inv.invested > 0) {
                invCashflows.add(XirrCalculator.Cashflow(-inv.invested, inv.date))
            }
            // Terminal market value today (positive cashflow)
            if (inv.currentVal > 0) {
                invCashflows.add(XirrCalculator.Cashflow(inv.currentVal, todayStr))
            }
        }

        // Add any "Investment Returns" (Income) for these investments (e.g. dividends)
        trans.filter { it.type.lowercase() == "income" && (it.category == "Investment Returns" || invIds.contains(it.ref)) }
            .forEach { t ->
                invCashflows.add(XirrCalculator.Cashflow(t.amount, t.date))
            }

        val invXirr = XirrCalculator.calc(invCashflows)

        // 2. Lending Performance (Interest-bearing only)
        val lendCashflows = mutableListOf<XirrCalculator.Cashflow>()
        val brwIds = activeBrws.filter { it.rate > 0 }.map { it.id }.toSet()
        val lendTrans = trans.filter { it.category == "Lending" || it.category == "Loan Repayment" || brwIds.contains(it.ref) }
        lendTrans.forEach { t ->
            val amt = if (t.type.lowercase() == "expense") -t.amount else t.amount
            lendCashflows.add(XirrCalculator.Cashflow(amt, t.date))
        }
        // Terminal outstanding principal + accrued interest
        activeBrws.filter { it.rate > 0 }.forEach { b ->
            val accrued = if (b.status == "Defaulted" && b.frozenInterest > 0) b.frozenInterest
            else app.fynlo.logic.InterestEngine.calcIntAccrued(
                b.amount, b.rate, b.date, b.intType, b.due, totalPaid = b.paidPrincipal
            )
            val outstanding = app.fynlo.logic.InterestEngine.calcOutstanding(b.amount, accrued, b.paidPrincipal, b.paidInterest)
            if (outstanding > 0) {
                lendCashflows.add(XirrCalculator.Cashflow(outstanding, todayStr))
            }
        }
        val lendXirr = XirrCalculator.calc(lendCashflows)

        // 3. Combined Portfolio XIRR (Deployed Capital)
        val portXirr = XirrCalculator.calc(invCashflows + lendCashflows)

        // Calculate per-account growth (Month-to-Date inflow - outflow)
        val currentMonthPrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))
        val thisMonthTrans = trans.filter { it.date.startsWith(currentMonthPrefix) }
        val growthMap = accts.associate { acct ->
            val inflow  = thisMonthTrans.filter { it.toAcct == acct.name }.sumOf { it.amount }
            val outflow = thisMonthTrans.filter { it.fromAcct == acct.name }.sumOf { it.amount }
            acct.name to (inflow - outflow)
        }

        FinancialSummary(
            totalCash              = totalCashVal,
            totalInvestments       = totalInvestVal,
            totalReceivables       = totalReceivables,
            totalAssets            = totalAssets,
            totalDebtPrincipal     = totalDebtPrincipal,
            totalDebtInterest      = totalDebtInterest,
            totalExpenses          = totalExpenses,
            totalIncome            = totalIncome,
            totalInterestIncome    = totalInterestIncome,
            totalInterestExpense   = totalInterestExpense,
            totalBadDebtWriteOffs  = totalBadDebtWriteOffs,
            netWorth               = net,
            investmentGrowth       = invGrowth,
            investmentCagr         = invCagr,
            investmentXirr         = invXirr,
            investmentTypeBreakdown  = invTypeMap,
            interestLendingBreakdown = interestBrwMap,
            handLendingBreakdown     = handBrwMap,
            lendingYield           = avgYield,
            lendingXirr            = lendXirr,
            portfolioXirr          = portXirr,
            debtBurden             = if (net != 0.0) ((totalDebtPrincipal + totalDebtInterest) / net) * 100 else 0.0,
            totalInterestLoans     = totalInterestLoans,
            totalHandLoans         = totalHandLoans,
            accountBreakdown       = accountsMap,
            accountGrowthMap       = growthMap
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FinancialSummary())

    private val today get() = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    private val pid   get() = _currentProjectId.value.ifEmpty { "personal" }

    fun withdrawFromInvestment(investment: app.fynlo.data.model.Investment, amount: Double, toAccount: String) {
        viewModelScope.launch(Dispatchers.IO) { repository.withdrawFromInvestment(investment, amount, toAccount) }
    }
    fun restoreBorrowerToActive(borrower: app.fynlo.data.model.Borrower) {
        viewModelScope.launch(Dispatchers.IO) { repository.restoreBorrowerToActive(borrower) }
    }
        fun markBorrowerDefaulted(borrower: app.fynlo.data.model.Borrower) {
        viewModelScope.launch(Dispatchers.IO) { repository.markBorrowerDefaulted(borrower) }
    }
    fun writeOffBorrower(borrower: app.fynlo.data.model.Borrower) {
        viewModelScope.launch(Dispatchers.IO) { repository.writeOffBorrower(borrower) }
    }
    /**
     * Manual recalc from Settings. Routed through `RecalcCoordinator` so
     * `lastRecalcAt` (the value that drives the Dashboard "Last updated"
     * subtitle and the launch-time debouncer) is kept consistent with
     * whatever code path triggered the recalc.
     */
    fun recalculateAllBalances() {
        viewModelScope.launch(Dispatchers.IO) { recalcCoordinator.runAndStamp() }
    }

    // ── C04: smart defaults for Add Transaction ───────────────────────────────
    // Reads from / writes to RecentlyUsedTracker so the next time the user
    // opens AddTransactionDialog, the category for the current type is
    // pre-selected without them tapping. Category recency is split by type
    // (CATEGORY_INCOME / CATEGORY_EXPENSE) so e.g. "Salary" never bleeds
    // into the Expense picker — same boundary C05 enforced for the chip
    // list itself.

    /**
     * Returns the most-recently-used category for an Add Transaction of
     * the given type, or `null` if the user has never submitted one yet.
     * Called from `AddTransactionDialog`'s on-open prefill.
     */
    suspend fun rememberLastTransactionCategory(isIncome: Boolean): String? {
        val fieldId = if (isIncome) RecentlyUsedTracker.FieldIds.CATEGORY_INCOME
                      else          RecentlyUsedTracker.FieldIds.CATEGORY_EXPENSE
        return recentlyUsedTracker.last(
            RecentlyUsedTracker.FormIds.ADD_TRANSACTION,
            fieldId,
        )
    }

    /**
     * Records the user's category choice after they submit an Add
     * Transaction. Fire-and-forget — the write is fast and a transient
     * failure shouldn't block the UI. Blank values are dropped by the
     * tracker so picker fields with no selection don't pollute recency.
     */
    fun recordTransactionCategory(isIncome: Boolean, category: String) {
        if (category.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val fieldId = if (isIncome) RecentlyUsedTracker.FieldIds.CATEGORY_INCOME
                          else          RecentlyUsedTracker.FieldIds.CATEGORY_EXPENSE
            recentlyUsedTracker.record(
                RecentlyUsedTracker.FormIds.ADD_TRANSACTION,
                fieldId,
                category,
            )
        }
    }

    // ── C04 Stage 3: recurring transaction category recency ───────────────
    // Mirrors the AddTransaction methods but keyed off ADD_RECURRING so the
    // user's "what category did I last assign to a recurring expense" memory
    // doesn't bleed into / out of the one-off AddTransaction flow. Split by
    // type for the same reason AddTransaction is — a recurring "Salary"
    // income shouldn't prefill an "Expense" recurring as Salary.

    suspend fun rememberLastRecurringCategory(isIncome: Boolean): String? {
        val fieldId = if (isIncome) RecentlyUsedTracker.FieldIds.CATEGORY_INCOME
                      else          RecentlyUsedTracker.FieldIds.CATEGORY_EXPENSE
        return recentlyUsedTracker.last(
            RecentlyUsedTracker.FormIds.ADD_RECURRING,
            fieldId,
        )
    }

    fun recordRecurringCategory(isIncome: Boolean, category: String) {
        if (category.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val fieldId = if (isIncome) RecentlyUsedTracker.FieldIds.CATEGORY_INCOME
                          else          RecentlyUsedTracker.FieldIds.CATEGORY_EXPENSE
            recentlyUsedTracker.record(
                RecentlyUsedTracker.FormIds.ADD_RECURRING,
                fieldId,
                category,
            )
        }
    }

    // ── C04 Stage 3: budget category — heuristic-first prefill ────────────
    // The audit's BudgetScreen criterion is "pre-select the category most
    // likely to need a budget," which it defines as "the category with the
    // biggest spend that doesn't already have a budget" — NOT pure recency.
    // [suggestBudgetCategory] computes this; [rememberLastBudgetCategory]
    // is the fallback used when there are no uncapped expenses (or no
    // expenses at all). Callers should chain them:
    //     viewModel.suggestBudgetCategory()
    //         ?: viewModel.rememberLastBudgetCategory()
    //         ?: ""   // fresh install, no spend, no recency

    /**
     * Highest-spend EXPENSE category that doesn't yet have a budget.
     * `null` when every category the user has spent on is already
     * budget-capped, or when there are no expense transactions at all.
     */
    fun suggestBudgetCategory(): String? =
        app.fynlo.data.BudgetSuggestion.suggest(
            cappedCategories = budgets.value.map { it.category }.toSet(),
            expenseAnalytics = expenseAnalytics.value,
        )

    suspend fun rememberLastBudgetCategory(): String? =
        recentlyUsedTracker.last(
            RecentlyUsedTracker.FormIds.ADD_BUDGET,
            RecentlyUsedTracker.FieldIds.CATEGORY_EXPENSE,
        )

    fun recordBudgetCategory(category: String) {
        if (category.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            recentlyUsedTracker.record(
                RecentlyUsedTracker.FormIds.ADD_BUDGET,
                RecentlyUsedTracker.FieldIds.CATEGORY_EXPENSE,
                category,
            )
        }
    }

    // ── C04 Stage 3: currency picker — locale-default-first prefill ───────
    // On fresh install we have no recency, so the picker falls back to the
    // device's locale-derived currency code (e.g., "INR" for an en_IN device,
    // "USD" for en_US, "EUR" for fr_FR). Once the user has picked at least
    // once, that pick wins — recency overrides locale, matching how every
    // other recency-driven prefill in the app works.

    /**
     * Returns the user's most-recently-picked currency code, or the system
     * locale's currency on first run. `Currency.getInstance(Locale)` can
     * throw `IllegalArgumentException` for locales without a country
     * (e.g., the bare `en` locale on some emulators); falls back to the
     * existing app default ("INR") so the picker has *something* to render.
     */
    suspend fun rememberLastCurrencyOrLocale(
        locale: java.util.Locale = java.util.Locale.getDefault(),
    ): String {
        recentlyUsedTracker.last(
            RecentlyUsedTracker.FormIds.SETTINGS_CURRENCY,
            RecentlyUsedTracker.FieldIds.CURRENCY,
        )?.let { return it }
        return runCatching { java.util.Currency.getInstance(locale).currencyCode }
            .getOrDefault("INR")
    }

    fun recordCurrency(code: String) {
        if (code.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            recentlyUsedTracker.record(
                RecentlyUsedTracker.FormIds.SETTINGS_CURRENCY,
                RecentlyUsedTracker.FieldIds.CURRENCY,
                code,
            )
        }
    }

    // ── C04 Stage 3: top-N flows for grouped-picker UX ────────────────────
    // For dropdowns (currency picker is the canonical case) — render a
    // "Recently used" group at the top with these N entries, then the full
    // alphabetical list below. Chip-based pickers (AddTransaction, Recurring,
    // Budget) don't need this since FilterChip layout already shows every
    // option equally; the prefill alone is the C04 win for those.

    fun observeRecentCurrencies(n: Int = 5): kotlinx.coroutines.flow.Flow<List<String>> =
        recentlyUsedTracker.observeTopN(
            RecentlyUsedTracker.FormIds.SETTINGS_CURRENCY,
            RecentlyUsedTracker.FieldIds.CURRENCY,
            n,
        )

    /**
     * C02 step 4: manual recalc that returns a before/after snapshot so the
     * Settings screen can show a result dialog ("Net worth: ₹268,081 → ₹241,663").
     *
     * Implementation note: `financialSummary` is a derived `StateFlow` over
     * the Room-backed list flows. When `runAndStamp()` returns, the DB writes
     * have committed but the StateFlow chain hasn't *necessarily* emitted the
     * post-state yet. So we wait briefly for the next *different* value,
     * with a 500 ms timeout for the (common, post-C01) case where nothing
     * actually changed — in which case `pre == post` and the dialog says
     * "no changes."
     */
    suspend fun recalculateAllBalancesCapturingDelta(): RecalcDelta {
        val pre = financialSummary.value
        recalcCoordinator.runAndStamp()
        val post = kotlinx.coroutines.withTimeoutOrNull(500L) {
            financialSummary.first { it != pre }
        } ?: pre
        return RecalcDelta(pre, post)
    }

    fun addBorrowerWithSource(borrower: Borrower, source: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertBorrowerWithSource(borrower.copy(projectId = pid), source, pid)
        }
    }

    fun deleteBorrower(borrower: Borrower) {
        viewModelScope.launch(Dispatchers.IO) { repository.deleteBorrower(borrower) }
    }

    fun updateBorrower(borrower: Borrower) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateBorrower(borrower.copy(projectId = pid))
        }
    }

    fun updateBorrowerWithSource(borrower: Borrower, source: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateBorrowerWithSource(borrower.copy(projectId = pid), source)
        }
    }

    fun updateDebt(debt: Debt) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateDebt(debt.copy(projectId = pid, updatedAt = System.currentTimeMillis()))
        }
    }

    fun updateDebtWithDestination(debt: Debt, destination: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateDebtWithDestination(debt.copy(projectId = pid, updatedAt = System.currentTimeMillis()), destination)
        }
    }

    // ─── Add investment — pick the right repository function by source type ─────
    fun addInvestmentFundedByAccount(investment: Investment, accountName: String, accountId: String = "") {
        viewModelScope.launch(Dispatchers.IO) { repository.insertInvestmentFundedByAccount(investment.copy(projectId = pid), accountName, pid, accountId) }
    }
    fun addInvestmentFundedByExistingDebt(investment: Investment, debt: app.fynlo.data.model.Debt) {
        viewModelScope.launch(Dispatchers.IO) { repository.insertInvestmentFundedByExistingDebt(investment.copy(projectId = pid), debt, pid) }
    }
    fun addInvestmentFundedByNewLoan(investment: Investment, newDebt: app.fynlo.data.model.Debt) {
        viewModelScope.launch(Dispatchers.IO) { repository.insertInvestmentFundedByNewLoan(investment.copy(projectId = pid), newDebt.copy(projectId = pid), pid) }
    }
    // Legacy shim kept so Navigation.kt compiles without changes until updated
    fun addInvestmentWithSource(investment: Investment, source: String) {
        viewModelScope.launch(Dispatchers.IO) { repository.insertInvestmentWithSource(investment.copy(projectId = pid), source, pid) }
    }

    fun updateInvestmentValue(investment: Investment, newCurrentVal: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateInvestmentValue(investment, newCurrentVal)
        }
    }

    fun updateInvestment(investment: Investment) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateInvestment(investment.copy(projectId = pid))
        }
    }

    fun updateInvestmentFundedByAccount(investment: Investment, accountName: String, accountId: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateInvestmentFundedByAccount(investment.copy(projectId = pid), accountName, pid, accountId)
        }
    }

    fun executeLinkedInvestment(
        investment: Investment,
        fundingSourceType: String,
        sourceName: String,
        debtDetails: Debt? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.executeLinkedInvestment(
                investment.copy(projectId = pid),
                fundingSourceType,
                sourceName,
                debtDetails?.copy(projectId = pid)
            )
        }
    }

    fun addValuation(v: InvestmentValuation) {
        viewModelScope.launch(Dispatchers.IO) { repository.addValuation(v) }
    }

    fun getValuationsForInvestment(invId: String) = repository.getValuationsForInvestment(invId)

    fun deleteInvestment(investment: Investment) {
        viewModelScope.launch(Dispatchers.IO) { repository.deleteInvestmentOnly(investment) }
    }
    fun deleteInvestmentAndReverseAccount(investment: Investment) {
        viewModelScope.launch(Dispatchers.IO) { repository.deleteInvestmentAndReverseAccount(investment) }
    }
    fun deleteInvestmentAndLinkedLoan(investment: Investment) {
        viewModelScope.launch(Dispatchers.IO) { repository.deleteInvestmentAndLinkedLoan(investment) }
    }

    fun addTransaction(transaction: Transaction) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertTransaction(transaction.copy(projectId = pid))
        }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertTransaction(transaction.copy(projectId = pid))
        }
    }

    fun restoreRealData() {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = repository.syncManager.userId
            if (uid.isBlank()) return@launch
            val fs = com.google.firebase.firestore.FirebaseFirestore.getInstance()

            try {
                val txDocs = fs.collection("users").document(uid).collection("transactions").get().await()
                txDocs.documents.forEach { it.reference.delete().await() }
                repository.dao.deleteAllTransactions()
            } catch (e: Exception) { android.util.Log.e("Restore", "txn: ${e.message}") }

            val cashAccount = app.fynlo.data.model.Account(
                id = "1", name = "Personal Cash", balance = 3962.0, type = "Cash"
            )
            val hdfcAccount = app.fynlo.data.model.Account(
                id = "2", name = "HDFC Bank", balance = 122500.0, type = "Bank"
            )
            try {
                repository.dao.insertAccount(cashAccount)
                repository.dao.insertAccount(hdfcAccount)
                fs.collection("users").document(uid).collection("accounts")
                    .document("1").set(mapOf("id" to "1", "name" to "Personal Cash", "balance" to 3962.0, "type" to "Cash", "projectId" to "personal", "updatedAt" to System.currentTimeMillis())).await()
                fs.collection("users").document(uid).collection("accounts")
                    .document("2").set(mapOf("id" to "2", "name" to "HDFC Bank", "balance" to 122500.0, "type" to "Bank", "projectId" to "personal", "updatedAt" to System.currentTimeMillis())).await()
            } catch (e: Exception) { android.util.Log.e("Restore", "accounts: ${e.message}") }
        }
    }

    fun cleanupSeeederData() {
        viewModelScope.launch(Dispatchers.IO) {
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
        viewModelScope.launch(Dispatchers.IO) {
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
        viewModelScope.launch(Dispatchers.IO) { repository.editTransaction(old, new) }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch(Dispatchers.IO) { repository.deleteTransaction(transaction) }
    }

    fun deleteTransactions(transactions: List<Transaction>) {
        viewModelScope.launch(Dispatchers.IO) {
            transactions.forEach { repository.deleteTransaction(it) }
        }
    }

    /**
     * Pull-to-refresh. Data is kept live by Firestore listeners, so this surfaces
     * the sync status (spinner) while any pending remote changes settle, then
     * marks synced. [onComplete] runs on the main thread to clear the indicator.
     */
    fun refresh(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.syncManager.setSyncing()
            kotlinx.coroutines.delay(900)
            repository.syncManager.setSynced()
            onComplete()
        }
    }

    fun addDebtWithDestination(debt: Debt, destination: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertDebtWithDestination(debt.copy(projectId = pid), destination, pid)
        }
    }

    fun deleteDebt(debt: Debt) {
        viewModelScope.launch(Dispatchers.IO) { repository.deleteDebt(debt) }
    }

    fun collectLoanPayment(payment: Payment, destination: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertPaymentWithDest(payment.copy(projectId = pid), destination, pid)
        }
    }

    fun payDebt(payment: DebtPayment, source: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertDebtPaymentWithSource(payment.copy(projectId = pid), source, pid)
        }
    }

    fun addPerson(person: Person) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertPerson(person.copy(projectId = pid))
        }
    }

    fun updatePerson(person: Person) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertPerson(person.copy(projectId = pid, updatedAt = System.currentTimeMillis()))
        }
    }

    fun deletePerson(person: Person) {
        viewModelScope.launch(Dispatchers.IO) { repository.deletePerson(person) }
    }

    fun addBudget(budget: Budget) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertBudget(budget.copy(projectId = pid))
        }
    }

    fun deleteBudget(budget: Budget) {
        viewModelScope.launch(Dispatchers.IO) { repository.deleteBudget(budget) }
    }

    fun quickEditBalance(accountName: String, newBalance: Double, oldBalance: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.quickEditBalance(accountName, newBalance, oldBalance)
        }
    }

    fun saveAccount(account: Account) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.upsertAccount(
                account.copy(
                    projectId = pid,
                    updatedAt = System.currentTimeMillis(),
                    createdAt = if (account.createdAt > 0L) account.createdAt else System.currentTimeMillis(),
                )
            )
        }
    }

    fun transferBetweenAccounts(from: Account, to: Account, amount: Double) {
        if (amount <= 0.0 || from.id == to.id) return
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            repository.insertTransaction(
                Transaction(
                    id = app.fynlo.logic.Ids.newId(),
                    date = today,
                    type = "Transfer",
                    amount = amount,
                    fromAcct = from.name,
                    toAcct = to.name,
                    fromAcctId = from.id,
                    toAcctId = to.id,
                    category = "Transfer",
                    desc = "Transfer from ${from.name} to ${to.name}",
                    projectId = pid,
                    updatedAt = now,
                    createdAt = now,
                )
            )
        }
    }

    fun closeAccount(account: Account) {
        if (kotlin.math.abs(account.balance) > 0.005) return
        val marker = "[fynlo:closed-account]"
        val notes = if (account.notes.contains(marker)) account.notes else listOf(account.notes, marker)
            .filter { it.isNotBlank() }
            .joinToString("\n")
        saveAccount(account.copy(notes = notes))
    }

    fun deleteUnusedAccount(account: Account) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteUnusedAccount(account)
        }
    }

    fun addGoal(goal: Goal) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertGoal(goal.copy(projectId = pid))
        }
    }

    fun deleteGoal(goal: Goal) {
        viewModelScope.launch(Dispatchers.IO) { repository.deleteGoal(goal) }
    }

    fun wipeAllData() {
        viewModelScope.launch(Dispatchers.IO) { repository.wipeAllData() }
    }

    /**
     * Reset All Data — wipes Firestore + Room, clears all preferences, removes
     * the PIN, signs out of Google/Firebase and cancels background work so the
     * app relaunches in a clean first-run state. [authManager] is passed in
     * because the ViewModel doesn't own it (same pattern as
     * [deleteAccountPermanently]). [onComplete] runs on the main thread once
     * everything is cleared — the caller uses it to restart the app process.
     */
    /**
     * 3.2.74 — wipe Firestore for this user and re-push local. Used when
     * stale cloud values are silently restoring themselves into local on
     * every sync, growing net worth without user input.
     */
    fun resetCloudSyncToLocal(onComplete: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching { repository.resetCloudSyncToLocal() }.isSuccess
            kotlinx.coroutines.withContext(Dispatchers.Main) { onComplete(ok) }
        }
    }

    fun resetAllData(
        context: android.content.Context,
        authManager: app.fynlo.data.AuthManager,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Firestore + Room (with deleteDatabase fallback).
            runCatching { repository.resetAllData(context) }
                .onFailure { android.util.Log.e("Reset", "resetAllData failed: ${it.message}") }

            // 2. DataStore preferences — then re-assert the first-run gates so the
            //    onboarding + setup wizard show again on next launch.
            runCatching {
                app.fynlo.data.UserPreferences.clearAll(context)
                app.fynlo.data.UserPreferences.setSetupDone(context, false)
                app.fynlo.data.UserPreferences.setOnboardingDone(context, false)
            }

            // 3. PIN / biometric + any legacy SharedPreferences.
            //    commit() (not apply()) — AppRestarter kills the process right
            //    after, which would drop apply()'s queued async write.
            runCatching {
                app.fynlo.data.PinManager(context).clearPinSync()
                context.getSharedPreferences("fynlo_prefs", android.content.Context.MODE_PRIVATE)
                    .edit(commit = true) { clear() }
                context.getSharedPreferences("theme_prefs", android.content.Context.MODE_PRIVATE)
                    .edit(commit = true) { clear() }
            }

            // 4. Sign out of Google / Firebase Auth.
            runCatching { authManager.signOut() }

            // 5. Stop recurring jobs (reminders etc.).
            runCatching { androidx.work.WorkManager.getInstance(context).cancelAllWork() }

            kotlinx.coroutines.withContext(Dispatchers.Main) { onComplete() }
        }
    }

    /**
     * Right-to-erasure: wipe all data (Room + Firestore) then delete the
     * Firebase Auth user. authManager is passed in because the ViewModel
     * doesn't own it. onResult(true) = fully erased; onResult(false) = data
     * wiped but auth deletion needs a fresh login (caller should re-sign-in).
     */
    fun deleteAccountPermanently(
        authManager: app.fynlo.data.AuthManager,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.wipeAllData()
            val deleted = authManager.deleteAccount().isSuccess
            kotlinx.coroutines.withContext(Dispatchers.Main) { onResult(deleted) }
        }
    }

    fun executeFlow(result: app.fynlo.data.model.FlowResult) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = app.fynlo.logic.Ids.newId()
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

    /**
     * All exports below recalc-then-export per UX_AUDIT §C02: "PDF, JSON,
     * XLSX must invoke recalc as a pre-step." `recalcCoordinator.runAndStamp()`
     * stamps `lastRecalcAt` so the timestamp embedded in the export header
     * (added in C02 Stage 2) reflects when the data was actually computed.
     */

    suspend fun exportAllData(): String {
        recalcCoordinator.runAndStamp()
        val json = repository.getAllDataAsJson()
        app.fynlo.data.Analytics.dataExported("json")
        return json
    }

    fun restoreData(json: String) {
        viewModelScope.launch(Dispatchers.IO) { repository.restoreDataFromJson(json) }
    }

    suspend fun restoreDataNow(json: String) {
        repository.restoreDataFromJson(json)
    }

    suspend fun exportToCSV(): String {
        recalcCoordinator.runAndStamp()
        val csv = app.fynlo.logic.ExportUtility.generateCSV(
            transactions.value, borrowers.value, investments.value
        )
        app.fynlo.data.Analytics.dataExported("csv")
        return csv
    }

    suspend fun exportDataToCSV(scope: String): String {
        recalcCoordinator.runAndStamp()
        val csv = app.fynlo.logic.ExportUtility.generateDataExportCSV(
            scope = scope,
            accounts = accounts.value,
            transactions = transactions.value,
            borrowers = borrowers.value,
            debts = debts.value,
            investments = investments.value,
            people = people.value,
            budgets = budgets.value,
            goals = goals.value,
        )
        app.fynlo.data.Analytics.dataExported("csv_${scope.lowercase()}")
        return csv
    }

    suspend fun exportToPDF(
        outputStream: java.io.OutputStream,
        // C11 (3.2.40) — user's Date Format pref, collected by the caller.
        // Default matches the in-app default if caller hasn't migrated.
        dateFormat: String = app.fynlo.logic.DateUtils.DEFAULT_COMPACT_PATTERN,
    ) {
        val recalcAt = recalcCoordinator.runAndStamp()
        // C08 Stage 4: project currency threads through so the PDF cards +
        // tables render amounts in the user's configured format
        // (₹2,41,663 / $241,663 / etc.) instead of the pre-3.2.18
        // hardcoded "₹X,XXX.XX".
        val currencyCode = currentProject.value?.currency ?: "INR"
        // C21 Stage 1 — thread project name + signed-in email onto the PDF
        // cover. AuthManager is lightweight to instantiate (just wraps
        // Firebase.auth singleton); reaching for it directly avoids adding
        // a constructor-injection just for the export read path.
        app.fynlo.logic.ExportUtility.generatePDF(
            outputStream, financialSummary.value,
            transactions.value, borrowers.value, investments.value,
            lastRecalcAt = recalcAt,
            currencyCode = currencyCode,
            projectName = currentProject.value?.name ?: "Personal",
            userEmail   = app.fynlo.data.AuthManager().userEmail,
            periodLabel = "All time",
            debts       = debts.value,
            // C21 Stage 3 — snapshots for the net-worth trend chart.
            // Fetched via repository.getNetWorthSnapshots(pid).first() so
            // we get the current list without subscribing in this scope.
            snapshots   = repository.getNetWorthSnapshots(pid).first(),
            dateFormat  = dateFormat,
        )
        app.fynlo.data.Analytics.dataExported("pdf")
    }

    suspend fun exportDataToPDF(
        outputStream: java.io.OutputStream,
        scope: String,
        dateFormat: String = app.fynlo.logic.DateUtils.DEFAULT_COMPACT_PATTERN,
    ) {
        recalcCoordinator.runAndStamp()
        app.fynlo.logic.ExportUtility.generateDataExportPDF(
            outputStream = outputStream,
            scope = scope,
            accounts = accounts.value,
            transactions = transactions.value,
            borrowers = borrowers.value,
            debts = debts.value,
            investments = investments.value,
            people = people.value,
            budgets = budgets.value,
            goals = goals.value,
            currencyCode = currentProject.value?.currency ?: "INR",
            projectName = currentProject.value?.name ?: "Personal",
            userEmail = app.fynlo.data.AuthManager().userEmail,
            dateFormat = dateFormat,
        )
        app.fynlo.data.Analytics.dataExported("pdf_${scope.lowercase()}")
    }

    /**
     * XLSX export wrapper. Previously the SettingsScreen launcher called
     * `ExcelExportUtility.generateFullBackup` directly, bypassing any
     * ViewModel-level pre-step. Routing through here gives XLSX the same
     * recalc-stamp contract as PDF / CSV / JSON (closes the C02 "all
     * formats" gap explicitly).
     */
    suspend fun exportToXLSX(
        outputStream: java.io.OutputStream,
        // C11 (3.2.40) — user's Date Format pref, collected by the caller.
        dateFormat: String = app.fynlo.logic.DateUtils.DEFAULT_COMPACT_PATTERN,
    ) {
        val recalcAt = recalcCoordinator.runAndStamp()
        // C21 Stage 4 — thread the Summary-sheet KPIs (FinancialSummary)
        // and currency code so amounts render with the active currency
        // symbol and the Summary sheet's KPIs match the PDF cover.
        app.fynlo.logic.ExcelExportUtility.generateFullBackup(
            outputStream,
            accounts.value,
            transactions.value,
            borrowers.value,
            debts.value,
            investments.value,
            payments.value,
            debtPayments.value,
            lastRecalcAt = recalcAt,
            summary      = financialSummary.value,
            currencyCode = currentProject.value?.currency ?: "INR",
            dateFormat   = dateFormat,
        )
        app.fynlo.data.Analytics.dataExported("xlsx")
    }

    fun getNetWorthSnapshots() = repository.getNetWorthSnapshots(pid)

    fun saveSnapshotNow() {
        viewModelScope.launch(Dispatchers.IO) {
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

    /**
     * C15c (3.2.31) — Backfill month-end net-worth snapshots from transaction
     * history (UX_AUDIT §C15c #5). For each calendar month-end between the
     * user's earliest transaction and last completed month, computes an
     * approximate net worth by walking the cash-basis cash flow forward to
     * today: `approxNW(monthEnd) = currentNW − (cumulative cash flow from
     * monthEnd+1 to today)`. Financing categories are excluded so debt
     * received / loans extended / investments don't double-count.
     *
     * Investment unrealized value changes aren't reconstructable from history
     * (we only know `currentVal`), so this is held flat. The result is a
     * cash-flow-based curve — accurate enough to read trend direction without
     * requiring a Pricing-history API. Existing snapshot dates are preserved.
     *
     * Calls [onDone] with the count of snapshots inserted (0 if no
     * transactions or every month already had one).
     */
    fun backfillNetWorthHistory(onDone: (Int) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val txns = transactions.value
            if (txns.isEmpty()) {
                kotlinx.coroutines.withContext(Dispatchers.Main) { onDone(0) }
                return@launch
            }
            val financingCats = setOf(
                "Debt Received", "Debt Repayment", "Lending",
                "Loan Recovery", "Loan Repayment", "Investment", "Investment Returns"
            )
            val cashTxns = txns.filter { it.tags != "journal_only" && it.category !in financingCats }
            if (cashTxns.isEmpty()) {
                kotlinx.coroutines.withContext(Dispatchers.Main) { onDone(0) }
                return@launch
            }
            val fmt    = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val today  = LocalDate.now()
            val currentNW = financialSummary.value.netWorth
            val existingDates = repository.getNetWorthSnapshots(pid).first().map { it.date }.toSet()
            val earliest = runCatching { LocalDate.parse(cashTxns.minOf { it.date }) }.getOrNull() ?: run {
                kotlinx.coroutines.withContext(Dispatchers.Main) { onDone(0) }
                return@launch
            }
            var ym    = java.time.YearMonth.from(earliest)
            val endYm = java.time.YearMonth.from(today).minusMonths(1)
            var added = 0
            while (!ym.isAfter(endYm)) {
                val monthEnd = ym.atEndOfMonth().format(fmt)
                if (monthEnd !in existingDates) {
                    val cashFlowSince = cashTxns
                        .filter { it.date > monthEnd }
                        .sumOf { if (it.type.equals("income", true)) it.amount else -it.amount }
                    val approxNW = currentNW - cashFlowSince
                    repository.saveNetWorthSnapshot(
                        app.fynlo.data.model.NetWorthSnapshot(
                            date             = monthEnd,
                            netWorth         = approxNW,
                            totalAssets      = approxNW.coerceAtLeast(0.0),
                            totalLiabilities = 0.0,
                            projectId        = pid,
                            createdAt        = System.currentTimeMillis()
                        )
                    )
                    added++
                }
                ym = ym.plusMonths(1)
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) { onDone(added) }
        }
    }

    val recurringTransactions: StateFlow<List<app.fynlo.data.model.RecurringTransaction>> = repository.getAllRecurringTransactions()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addRecurringTransaction(r: app.fynlo.data.model.RecurringTransaction) {
        viewModelScope.launch(Dispatchers.IO) { repository.insertRecurringTransaction(r) }
    }

    fun deleteRecurringTransaction(r: app.fynlo.data.model.RecurringTransaction) {
        viewModelScope.launch(Dispatchers.IO) { repository.deleteRecurringTransaction(r) }
    }

    // ── Recurring Auto-Logger ─────────────────────────────────────────────
    /** Call once on app start. Logs all overdue recurring transactions. */
    fun triggerDueRecurring() {
        viewModelScope.launch(Dispatchers.IO) {
            val today = LocalDate.now()
            val fmt   = DateTimeFormatter.ofPattern("dd-MM-yyyy")
            val all   = repository.getAllRecurringTransactions().first()

            var logged = 0
            for (r in all) {
                if (!r.isActive) continue

                // Calculate next due date from lastRun
                val lastRun = if (r.lastRun.isBlank()) null else
                    runCatching { LocalDate.parse(r.lastRun, fmt) }.getOrNull()

                val nextDue: java.time.LocalDate = when {
                    lastRun == null -> today // never run → due today
                    r.frequency == "Daily"   -> lastRun.plusDays(1)
                    r.frequency == "Weekly"  -> lastRun.plusWeeks(1)
                    r.frequency == "Monthly" -> lastRun.plusMonths(1)
                    r.frequency == "Yearly"  -> lastRun.plusYears(1)
                    else -> lastRun.plusMonths(1)
                }

                if (!today.isBefore(nextDue)) {
                    // Create the transaction
                    val txn = Transaction(
                        id       = app.fynlo.logic.Ids.newId(),
                        date     = today.format(fmt),
                        type     = r.type,
                        amount   = r.amount,
                        fromAcct = if (r.type == "Expense") r.fromAcct else "",
                        toAcct   = if (r.type == "Income")  r.toAcct  else "",
                        category = r.category,
                        notes    = "Auto: ${r.name}",
                        projectId = r.projectId
                    )
                    repository.insertTransaction(txn)

                    // Update lastRun
                    repository.insertRecurringTransaction(
                        r.copy(lastRun = today.format(fmt), updatedAt = System.currentTimeMillis())
                    )
                    logged++
                }
            }
            if (logged > 0) {
                android.util.Log.i("Recurring", "Auto-logged $logged recurring transactions")
            }
        }
    }

    fun populateDummyData() {
        viewModelScope.launch(Dispatchers.IO) {
            val cash = Account("1", "Personal Cash", "Cash",  5000.0,  projectId = pid)
            val bank = Account("2", "HDFC Bank",    "Bank",  45000.0, projectId = pid)
            repository.insertAccount(cash)
            repository.insertAccount(bank)

            val borrower1 = Borrower(
                "b1", "John Doe", amount = 10000.0, rate = 2.0,
                date = "2024-01-10", due = "2024-12-31", intType = "Simple Interest",
                sourceAccount = "Personal Cash",
                notes = "Personal loan for home renovation.", projectId = pid
            )
            repository.insertBorrowerWithSource(borrower1, "Personal Cash", pid)

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
                Transaction("t1", today, "Expense", 1200.0, fromAcct = "Personal Cash", category = "Food",     notes = "Dinner at Barbeque Nation.", projectId = pid),
                Transaction("t2", today, "Expense", 2500.0, fromAcct = "HDFC Bank",    category = "Fuel",     notes = "Full tank refill.",           projectId = pid),
                Transaction("t3", today, "Income",  60000.0, toAcct  = "HDFC Bank",    category = "Salary",   notes = "April 2024 Salary.",           projectId = pid),
                Transaction("t4", today, "Expense", 800.0,  fromAcct = "Personal Cash", category = "Shopping", notes = "New charger cable.",           projectId = pid)
            ).forEach { repository.insertTransaction(it) }
        }
    }
}

private inline fun <reified T> List<*>.requireTypedList(): List<T> = map { it as T }

/**
 * C02 step 4: result type for [FinanceViewModel.recalculateAllBalancesCapturingDelta].
 * `before == after` means the recalc was a no-op (the common case after C01 —
 * data is already structurally consistent). The Settings dialog inspects the
 * deltas on whichever metrics it surfaces and shows "No changes" when none move.
 */
@androidx.compose.runtime.Immutable
data class RecalcDelta(
    val before: app.fynlo.data.model.FinancialSummary,
    val after: app.fynlo.data.model.FinancialSummary,
) {
    /** Net-worth delta (after − before). Positive = value went up. */
    val netWorthChange: Double get() = after.netWorth - before.netWorth
    val receivablesChange: Double get() = after.totalReceivables - before.totalReceivables
    val cashChange: Double get() = after.totalCash - before.totalCash
    val investmentsChange: Double get() = after.totalInvestments - before.totalInvestments

    /** True iff all metrics the Settings dialog reports moved by less than half a rupee. */
    val isNoOp: Boolean get() =
        kotlin.math.abs(netWorthChange) < 0.5 &&
        kotlin.math.abs(receivablesChange) < 0.5 &&
        kotlin.math.abs(cashChange) < 0.5 &&
        kotlin.math.abs(investmentsChange) < 0.5
}
