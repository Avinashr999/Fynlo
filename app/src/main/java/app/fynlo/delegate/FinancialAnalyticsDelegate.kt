package app.fynlo.delegate

import app.fynlo.data.model.Account
import app.fynlo.data.model.Borrower
import app.fynlo.data.model.Debt
import app.fynlo.data.model.DebtPayment
import app.fynlo.data.model.FinancialSummary
import app.fynlo.data.model.Investment
import app.fynlo.data.model.InvestmentValuation
import app.fynlo.data.model.Payment
import app.fynlo.data.model.Transaction
import app.fynlo.data.model.NetWorthSnapshot
import app.fynlo.logic.isGeneratedJournalEntry
import app.fynlo.logic.NetWorthSnapshotSafety
import app.fynlo.logic.InterestPolicy
import app.fynlo.logic.CagrCalculator
import app.fynlo.logic.XirrCalculator
import app.fynlo.logic.DebtLiabilityCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private inline fun <reified T> List<*>.requireTypedList(): List<T> = map { it as T }

class FinancialAnalyticsDelegate(
    private val ctx: ViewModelContext,
    private val transactionsFlow: StateFlow<List<Transaction>>,
    private val accountsFlow: StateFlow<List<Account>>,
    private val investmentsFlow: StateFlow<List<Investment>>,
    private val borrowersFlow: StateFlow<List<Borrower>>,
    private val debtsFlow: StateFlow<List<Debt>>,
    private val valuationsFlow: StateFlow<List<InvestmentValuation>>,
    private val paymentsFlow: StateFlow<List<Payment>>,
    private val debtPaymentsFlow: StateFlow<List<DebtPayment>>,
) {
    val expenseAnalytics: StateFlow<Map<String, Double>> = transactionsFlow.map { trans ->
        trans.filter { it.type.lowercase() == "expense" && !it.isGeneratedJournalEntry() }
            .groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
    }.stateIn(ctx.scope, SharingStarted.Eagerly, emptyMap())

    val financialSummary: StateFlow<FinancialSummary> = combine(
        transactionsFlow, accountsFlow, investmentsFlow, borrowersFlow, debtsFlow, valuationsFlow, paymentsFlow, debtPaymentsFlow
    ) { args: Array<List<*>> ->
        val trans = args[0].requireTypedList<Transaction>()
        val accts = args[1].requireTypedList<Account>()
        val invs  = args[2].requireTypedList<Investment>()
        val brws  = args[3].requireTypedList<Borrower>()
        val dbts  = args[4].requireTypedList<Debt>()
        val vals  = args[5].requireTypedList<InvestmentValuation>()
        val loanPayments = args[6].requireTypedList<Payment>()
        val debtPaymentRows = args[7].requireTypedList<DebtPayment>()
        val paymentsByLoan = loanPayments.groupBy { it.loanId }
        val paymentsByDebt = debtPaymentRows.groupBy { it.debtId }

        val totalCashVal     = accts.sumOf { it.balance }
        val totalInvestVal   = invs.sumOf { it.currentVal }

        val activeBrws = brws.filter { it.status != "WrittenOff" }

        val totalReceivables = activeBrws.sumOf { b ->
            if (b.rate <= 0) (b.amount - b.paid).coerceAtLeast(0.0)
            else (b.amount - b.paidPrincipal).coerceAtLeast(0.0) +
                InterestPolicy.borrowerBreakdown(b, paymentsByLoan[b.id].orEmpty()).due
        }

        val totalInterestLoans = activeBrws.filter { it.rate > 0 }.sumOf { b ->
            (b.amount - b.paidPrincipal).coerceAtLeast(0.0) +
                InterestPolicy.borrowerBreakdown(b, paymentsByLoan[b.id].orEmpty()).due
        }
        val totalHandLoans = activeBrws.filter { it.rate <= 0 }.sumOf { b ->
            (b.amount - b.paid).coerceAtLeast(0.0)
        }

        val invTypeMap = invs.groupBy { it.type }
            .mapValues { it.value.sumOf { inv -> inv.currentVal } }

        val interestBrwMap = activeBrws.filter { it.rate > 0 }.associate { b ->
            b.name to ((b.amount - b.paidPrincipal).coerceAtLeast(0.0) +
                InterestPolicy.borrowerBreakdown(b, paymentsByLoan[b.id].orEmpty()).due)
        }

        val handBrwMap = activeBrws.filter { it.rate <= 0 }.associate { b ->
            b.name to (b.amount - b.paid).coerceAtLeast(0.0)
        }

        val totalAssets       = totalCashVal + totalInvestVal + totalInterestLoans + totalHandLoans
        val debtLiabilities = dbts.map { debt ->
            val principal = (debt.amount - debt.paidPrincipal).coerceAtLeast(0.0)
            val interest = InterestPolicy.debtBreakdown(debt, paymentsByDebt[debt.id].orEmpty()).due
            DebtLiabilityCalculator.Liability(principal = principal, interest = interest)
        }
        val totalDebtPrincipal = debtLiabilities.sumOf { it.principal }
        val totalDebtInterest  = debtLiabilities.sumOf { it.interest }
        val cashTrans     = trans.filterNot { it.isGeneratedJournalEntry() }
        val totalExpenses = cashTrans.filter { it.type.lowercase() == "expense" }.sumOf { it.amount }
        val totalIncome   = cashTrans.filter { it.type.lowercase() == "income"  }.sumOf { it.amount }
        val totalBadDebtWriteOffs = trans.filter { it.category == "Bad Debt" }.sumOf { it.amount }
        val totalInterestExpense  = trans.filter { it.category == "Interest Expense" }.sumOf { it.amount }
        val totalInterestIncome   = loanPayments.sumOf { InterestPolicy.paymentInterestAmount(it) }
        val invGrowth      = invs.sumOf { it.currentVal - (it.invested - it.withdrawn) }

        val interestBearing = activeBrws.filter { it.rate > 0 }
        val avgYield       = if (interestBearing.isNotEmpty()) interestBearing.map { it.rate }.average() else 0.0

        val net            = totalAssets - (totalDebtPrincipal + totalDebtInterest)
        val accountsMap    = accts.associate { it.name to it.balance }

        val todayDate = LocalDate.now()
        val todayStr = todayDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val tomorrowStr = todayDate.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val borrowerDueToday = activeBrws.sumOf { b ->
            if (b.rate <= 0.0) 0.0 else InterestPolicy.borrowerBreakdown(b, paymentsByLoan[b.id].orEmpty(), todayStr).due
        }
        val borrowerDueTomorrow = activeBrws.sumOf { b ->
            if (b.rate <= 0.0) 0.0 else InterestPolicy.borrowerBreakdown(b, paymentsByLoan[b.id].orEmpty(), tomorrowStr).due
        }
        val debtDueToday = dbts.sumOf { debt ->
            if (debt.rate <= 0.0) 0.0 else InterestPolicy.debtBreakdown(debt, paymentsByDebt[debt.id].orEmpty(), todayStr).due
        }
        val debtDueTomorrow = dbts.sumOf { debt ->
            if (debt.rate <= 0.0) 0.0 else InterestPolicy.debtBreakdown(debt, paymentsByDebt[debt.id].orEmpty(), tomorrowStr).due
        }
        val dailyBorrowerInterest = (borrowerDueTomorrow - borrowerDueToday).coerceAtLeast(0.0)
        val dailyDebtInterest = (debtDueTomorrow - debtDueToday).coerceAtLeast(0.0)
        val dailyNetWorthInterestEffect = dailyBorrowerInterest - dailyDebtInterest

        val invCagr = CagrCalculator.portfolio(
            invs.map { Triple(it.invested, it.currentVal, it.date) }
        )

        val invCashflows = mutableListOf<XirrCalculator.Cashflow>()
        val invIds = invs.map { it.id }.toSet()

        invs.forEach { inv ->
            if (inv.invested > 0) {
                invCashflows.add(XirrCalculator.Cashflow(-inv.invested, inv.date))
            }
            if (inv.currentVal > 0) {
                invCashflows.add(XirrCalculator.Cashflow(inv.currentVal, todayStr))
            }
        }

        trans.filter { it.type.lowercase() == "income" && (it.category == "Investment Returns" || invIds.contains(it.ref)) }
            .forEach { t ->
                invCashflows.add(XirrCalculator.Cashflow(t.amount, t.date))
            }

        val invXirr = XirrCalculator.calc(invCashflows)

        val lendCashflows = mutableListOf<XirrCalculator.Cashflow>()
        val brwIds = activeBrws.filter { it.rate > 0 }.map { it.id }.toSet()
        val lendTrans = trans.filter { it.category == "Lending" || it.category == "Loan Repayment" || brwIds.contains(it.ref) }
        lendTrans.forEach { t ->
            val amt = if (t.type.lowercase() == "expense") -t.amount else t.amount
            lendCashflows.add(XirrCalculator.Cashflow(amt, t.date))
        }
        activeBrws.filter { it.rate > 0 }.forEach { b ->
            val outstanding = (b.amount - b.paidPrincipal).coerceAtLeast(0.0) +
                InterestPolicy.borrowerBreakdown(b, paymentsByLoan[b.id].orEmpty(), todayStr).due
            if (outstanding > 0) {
                lendCashflows.add(XirrCalculator.Cashflow(outstanding, todayStr))
            }
        }
        val lendXirr = XirrCalculator.calc(lendCashflows)

        val portXirr = XirrCalculator.calc(invCashflows + lendCashflows)

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
            dailyBorrowerInterestAccrued = dailyBorrowerInterest,
            dailyDebtInterestAccrued = dailyDebtInterest,
            dailyNetWorthInterestEffect = dailyNetWorthInterestEffect,
            lendingYield           = avgYield,
            lendingXirr            = lendXirr,
            portfolioXirr          = portXirr,
            debtBurden             = if (net != 0.0) ((totalDebtPrincipal + totalDebtInterest) / net) * 100 else 0.0,
            totalInterestLoans     = totalInterestLoans,
            totalHandLoans         = totalHandLoans,
            accountBreakdown       = accountsMap,
            accountGrowthMap       = growthMap
        )
    }.stateIn(ctx.scope, SharingStarted.Eagerly, FinancialSummary())

    init {
        repairNetWorthHistoryPlaceholdersAfterLedgerLoad()
    }

    private fun repairNetWorthHistoryPlaceholdersAfterLedgerLoad() {
        ctx.scope.launch(Dispatchers.IO) {
            val readyFlow: kotlinx.coroutines.flow.Flow<Pair<Boolean, FinancialSummary>> = combine(
                combine(accountsFlow, transactionsFlow, borrowersFlow) { accountRows, transactionRows, borrowerRows ->
                    accountRows.isNotEmpty() || transactionRows.isNotEmpty() || borrowerRows.isNotEmpty()
                },
                combine(debtsFlow, investmentsFlow) { debtRows, investmentRows ->
                    debtRows.isNotEmpty() || investmentRows.isNotEmpty()
                },
                financialSummary,
            ) { hasPrimaryLedgerData, hasPortfolioData, loadedSummary ->
                (hasPrimaryLedgerData || hasPortfolioData) to loadedSummary
            }
            val result: Pair<Boolean, FinancialSummary> = readyFlow
                .filter { it.first && !NetWorthSnapshotSafety.shouldSkipSave(it.second, hasLedgerData = true) }
                .first()
            val summary = result.second

            ctx.repository.deleteEmptyNetWorthSnapshots(ctx.currentProjectId())
            saveNetWorthSnapshot(summary)
        }
    }

    fun getNetWorthSnapshots() = ctx.repository.getNetWorthSnapshots(ctx.currentProjectId())

    fun saveSnapshotNow() {
        ctx.scope.launch(Dispatchers.IO) {
            val s = financialSummary.value
            val hasLedgerData = accountsFlow.value.isNotEmpty() ||
                transactionsFlow.value.isNotEmpty() ||
                borrowersFlow.value.isNotEmpty() ||
                debtsFlow.value.isNotEmpty() ||
                investmentsFlow.value.isNotEmpty()
            if (hasLedgerData) ctx.repository.deleteEmptyNetWorthSnapshots(ctx.currentProjectId())
            if (NetWorthSnapshotSafety.shouldSkipSave(s, hasLedgerData)) return@launch
            saveNetWorthSnapshot(s)
        }
    }

    private suspend fun saveNetWorthSnapshot(s: FinancialSummary) {
        ctx.repository.saveNetWorthSnapshot(
            NetWorthSnapshot(
                date             = ctx.today(),
                netWorth         = s.netWorth,
                totalAssets      = s.totalAssets,
                totalLiabilities = s.totalDebtPrincipal + s.totalDebtInterest,
                projectId        = ctx.currentProjectId()
            )
        )
    }

    fun backfillNetWorthHistory(onDone: (Int) -> Unit = {}) {
        ctx.scope.launch(Dispatchers.IO) {
            val txns = transactionsFlow.value
            if (txns.isEmpty()) {
                withContext(Dispatchers.Main) { onDone(0) }
                return@launch
            }
            val financingCats = setOf(
                "Debt Received", "Debt Repayment", "Lending",
                "Loan Recovery", "Loan Repayment", "Investment", "Investment Returns"
            )
            val cashTxns = txns.filter { !it.isGeneratedJournalEntry() && it.category !in financingCats }
            if (cashTxns.isEmpty()) {
                withContext(Dispatchers.Main) { onDone(0) }
                return@launch
            }
            val fmt    = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val today  = LocalDate.now()
            val currentNW = financialSummary.value.netWorth
            val pid = ctx.currentProjectId()
            ctx.repository.deleteEmptyNetWorthSnapshots(pid)
            val existingDates = ctx.repository.getNetWorthSnapshots(pid).first()
                .filterNot(NetWorthSnapshotSafety::isEmptyPlaceholder)
                .map { it.date }
                .toSet()
            val earliest = runCatching { LocalDate.parse(cashTxns.minOf { it.date }) }.getOrNull() ?: run {
                withContext(Dispatchers.Main) { onDone(0) }
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
                    ctx.repository.saveNetWorthSnapshot(
                        NetWorthSnapshot(
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
            withContext(Dispatchers.Main) { onDone(added) }
        }
    }
}
