package app.fynlo.data.model

@androidx.compose.runtime.Immutable
data class FinancialSummary(
    // ── Balance Sheet ──────────────────────────────────────────────────────────
    val totalCash: Double = 0.0,              // sum of all account balances
    val totalInvestments: Double = 0.0,       // sum of investment currentVal
    val totalReceivables: Double = 0.0,       // (principal - paidPrincipal) + (interest - paidInterest - waivedInterest)
    val totalAssets: Double = 0.0,            // cash + investments + receivables
    val totalDebtPrincipal: Double = 0.0,     // outstanding principal on all debts
    val totalDebtInterest: Double = 0.0,      // accrued interest on all debts
    val netWorth: Double = 0.0,               // assets - (debt principal + debt interest)

    // ── P&L (cash basis) ───────────────────────────────────────────────────────
    val totalIncome: Double = 0.0,            // actual cash income (excluding journal entries)
    val totalExpenses: Double = 0.0,          // actual cash expenses (excluding journal entries)
    val totalInterestIncome: Double = 0.0,    // interest collected from borrowers
    val totalInterestExpense: Double = 0.0,   // interest paid on own debts (P&L cost)
    val totalBadDebtWriteOffs: Double = 0.0,  // bad debt write-offs (economic loss)

    // ── Investment ─────────────────────────────────────────────────────────────
    val investmentGrowth: Double = 0.0,       // sum(currentVal - invested)
    val investmentCagr: Double = Double.NaN,
    val investmentXirr: Double = Double.NaN,
    val investmentTypeBreakdown: Map<String, Double> = emptyMap(),

    // ── Lending ────────────────────────────────────────────────────────────────
    val totalInterestLoans: Double = 0.0,     // total outstanding from interest-bearing loans
    val totalHandLoans: Double = 0.0,         // total outstanding from hand loans (0%)
    val lendingYield: Double = 0.0,           // average interest rate across active loans
    val lendingXirr: Double = Double.NaN,
    val interestLendingBreakdown: Map<String, Double> = emptyMap(),
    val handLendingBreakdown: Map<String, Double> = emptyMap(),

    // ── Portfolio ──────────────────────────────────────────────────────────────
    val portfolioXirr: Double = Double.NaN,

    // ── Misc ───────────────────────────────────────────────────────────────────
    val debtBurden: Double = 0.0,
    val accountBreakdown: Map<String, Double> = emptyMap(),
    val accountGrowthMap: Map<String, Double> = emptyMap()
) {
    val savingsRate: Double
        get() = if (totalIncome > 0) ((totalIncome - totalExpenses) / totalIncome) * 100 else 0.0

    val netProfitFromLending: Double
        get() = totalInterestIncome - totalBadDebtWriteOffs

    val costOfDebt: Double
        get() = totalInterestExpense
}
