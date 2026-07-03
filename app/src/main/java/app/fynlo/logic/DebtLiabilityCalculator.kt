package app.fynlo.logic

import app.fynlo.data.model.Debt
import app.fynlo.data.model.DebtPayment
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object DebtLiabilityCalculator {
    data class Liability(
        val principal: Double,
        val interest: Double,
    ) {
        val total: Double get() = principal + interest
    }

    fun outstanding(
        debt: Debt,
        asOf: String = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
    ): Liability {
        return Liability(
            principal = (debt.amount - debt.paidPrincipal).coerceAtLeast(0.0),
            interest = InterestPolicy.debtInterestOutstanding(debt, asOf),
        )
    }

    fun outstanding(
        debt: Debt,
        payments: List<DebtPayment>,
        asOf: String = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
    ): Liability {
        return Liability(
            principal = (debt.amount - debt.paidPrincipal).coerceAtLeast(0.0),
            interest = InterestPolicy.debtBreakdown(debt, payments, asOf).due,
        )
    }
}
