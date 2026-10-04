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
import app.fynlo.logic.isGeneratedJournalEntry
import app.fynlo.logic.isSpendingExpense
import app.fynlo.logic.isOperatingCashEntry
import app.fynlo.logic.NetWorthTotals
import app.fynlo.logic.InterestPolicy
import app.fynlo.logic.CagrCalculator
import app.fynlo.logic.XirrCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
        trans.filter { it.isSpendingExpense() }
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
        fun borrowerSnapshot(borrower: Borrower, asOf: String = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))) =
            InterestPolicy.borrowerSnapshot(borrower, paymentsByLoan[borrower.id].orEmpty(), asOf)
        fun debtSnapshot(debt: Debt, asOf: String = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))) =
            InterestPolicy.debtSnapshot(debt, paymentsByDebt[debt.id].orEmpty(), asOf)

        val balanceSheet = NetWorthTotals.calculate(accts, invs, brws, dbts, loanPayments, debtPaymentRows, ctx.today())
        val totalCashVal = balanceSheet.totalCash
        val totalInvestVal = balanceSheet.totalInvestments

        val activeBrws = brws.filter { it.status != "WrittenOff" }

        val totalReceivables = balanceSheet.totalReceivables
        val totalInterestLoans = balanceSheet.totalInterestLoans
        val totalHandLoans = balanceSheet.totalHandLoans

        val invTypeMap = invs.groupBy { it.type }
            .mapValues { it.value.sumOf { inv -> inv.currentVal } }

        val interestBrwMap = activeBrws.filter { it.rate > 0 }.associate { b ->
            b.name to borrowerSnapshot(b).totalReceivable
        }

        val handBrwMap = activeBrws.filter { it.rate <= 0 }.associate { b ->
            b.name to borrowerSnapshot(b).principalOutstanding
        }

        val totalAssets = balanceSheet.totalAssets
        val totalDebtPrincipal = balanceSheet.totalDebtPrincipal
        val totalDebtInterest = balanceSheet.totalDebtInterest
        val cashTrans     = trans.filter { it.isOperatingCashEntry() }
        val totalExpenses = cashTrans.filter { it.type.lowercase() == "expense" }.sumOf { it.amount }
        val totalIncome   = cashTrans.filter { it.type.lowercase() == "income"  }.sumOf { it.amount }
        val totalBadDebtWriteOffs = trans.filter { it.category == "Bad Debt" }.sumOf { it.amount }
        val totalInterestExpense  = trans.filter { it.category == "Interest Expense" }.sumOf { it.amount }
        val totalInterestIncome   = loanPayments.sumOf { InterestPolicy.paymentInterestAmount(it) }
        val invGrowth      = invs.sumOf { it.currentVal - (it.invested - it.withdrawn) }

        val interestBearing = activeBrws.filter { it.rate > 0 }
        val avgYield       = if (interestBearing.isNotEmpty()) interestBearing.map { it.rate }.average() else 0.0

        val net = balanceSheet.netWorth
        val accountsMap    = accts.associate { it.name to it.balance }

        val todayDate = LocalDate.now()
        val todayStr = todayDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val tomorrowStr = todayDate.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val borrowerDueToday = activeBrws.sumOf { b ->
            if (b.rate <= 0.0) 0.0 else borrowerSnapshot(b, todayStr).interestDue
        }
        val borrowerDueTomorrow = activeBrws.sumOf { b ->
            if (b.rate <= 0.0) 0.0 else borrowerSnapshot(b, tomorrowStr).interestDue
        }
        val debtDueToday = dbts.sumOf { debt ->
            if (debt.rate <= 0.0) 0.0 else debtSnapshot(debt, todayStr).interestDue
        }
        val debtDueTomorrow = dbts.sumOf { debt ->
            if (debt.rate <= 0.0) 0.0 else debtSnapshot(debt, tomorrowStr).interestDue
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
            val outstanding = borrowerSnapshot(b, todayStr).totalReceivable
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

    fun getNetWorthSnapshots() = ctx.repository.getNetWorthSnapshots(ctx.currentProjectId())

    fun saveSnapshotNow() {
        val projectId = ctx.currentProjectId()
        val date = ctx.today()
        ctx.scope.launch(Dispatchers.IO) {
            try {
                // Read every input within one Room transaction, never a partially loaded UI summary.
                ctx.repository.captureNetWorthSnapshot(projectId, date)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    ctx.showFeedback("History could not be saved. Existing history has been kept. Please try again.")
                }
            }
        }
    }
}
