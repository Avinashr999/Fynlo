package app.fynlo.logic

import app.fynlo.data.model.*

/** Shared balance-sheet totals for the dashboard and complete local-history captures. */
object NetWorthTotals {
    fun calculate(
        accounts: List<Account>, investments: List<Investment>, borrowers: List<Borrower>,
        debts: List<Debt>, payments: List<Payment>, debtPayments: List<DebtPayment>, asOf: String,
    ): FinancialSummary {
        val byLoan = payments.groupBy { it.loanId }
        val byDebt = debtPayments.groupBy { it.debtId }
        val loans = borrowers.filter { it.status != "WrittenOff" }.map {
            it to InterestPolicy.borrowerSnapshot(it, byLoan[it.id].orEmpty(), asOf)
        }
        val liabilities = debts.map { InterestPolicy.debtSnapshot(it, byDebt[it.id].orEmpty(), asOf) }
        val cash = accounts.sumOf { it.balance }
        val invested = investments.sumOf { it.currentVal }
        val interestLoans = loans.filter { it.first.rate > 0 }.sumOf { it.second.totalReceivable }
        val handLoans = loans.filter { it.first.rate <= 0 }.sumOf { it.second.principalOutstanding }
        val principal = liabilities.sumOf { it.principalOutstanding }
        val interest = liabilities.sumOf { it.interestDue }
        val assets = cash + invested + interestLoans + handLoans
        return FinancialSummary(
            totalCash = cash, totalInvestments = invested,
            totalReceivables = loans.sumOf { it.second.totalReceivable },
            totalInterestLoans = interestLoans, totalHandLoans = handLoans,
            totalAssets = assets, totalDebtPrincipal = principal, totalDebtInterest = interest,
            netWorth = assets - (principal + interest),
        )
    }
}
