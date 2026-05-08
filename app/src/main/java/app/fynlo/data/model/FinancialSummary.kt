package app.fynlo.data.model

data class FinancialSummary(
    val totalCash: Double = 0.0,
    val totalInvestments: Double = 0.0,
    val totalReceivables: Double = 0.0, // Lending
    val totalAssets: Double = 0.0,
    val totalDebtPrincipal: Double = 0.0,
    val totalDebtInterest: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val totalIncome: Double = 0.0,
    val netWorth: Double = 0.0,
    val investmentGrowth: Double = 0.0, // Absolute gain
    val lendingYield: Double = 0.0, // Avg interest rate
    val debtBurden: Double = 0.0, // Debt to Networth ratio
    val accountBreakdown: Map<String, Double> = emptyMap() // NEW: For individual bank statements
) {
    val savingsRate: Double
        get() = if (totalIncome > 0) ((totalIncome - totalExpenses) / totalIncome) * 100 else 0.0
}