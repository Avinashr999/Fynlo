package app.fynlo.data.model

data class FinancialSummary(
    val totalCash: Double = 0.0,
    val totalInvestments: Double = 0.0,
    val totalReceivables: Double = 0.0,
    val totalAssets: Double = 0.0,
    val totalDebtPrincipal: Double = 0.0,
    val totalDebtInterest: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val totalIncome: Double = 0.0,
    val netWorth: Double = 0.0,
    val investmentGrowth: Double = 0.0,
    val lendingYield: Double = 0.0,
    val debtBurden: Double = 0.0,
    val accountBreakdown: Map<String, Double> = emptyMap(),
    val accountGrowthMap: Map<String, Double> = emptyMap(),
    val totalInterestLoans: Double = 0.0,
    val totalHandLoans: Double = 0.0,
    val investmentTypeBreakdown: Map<String, Double> = emptyMap(), // type -> currentVal
    val interestLendingBreakdown: Map<String, Double> = emptyMap(), // person -> outstanding
    val handLendingBreakdown: Map<String, Double> = emptyMap() // person -> balance
) {
    val savingsRate: Double
        get() = if (totalIncome > 0) ((totalIncome - totalExpenses) / totalIncome) * 100 else 0.0
}
