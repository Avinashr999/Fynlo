package app.fynlo.logic

import app.fynlo.data.model.Debt
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
        val accruedInterest = InterestEngine.calcIntAccrued(
            debt.amount,
            debt.rate,
            debt.date,
            debt.intType,
            debt.due,
            totalPaid = debt.paidPrincipal,
            asOf = asOf,
        )
        return Liability(
            principal = (debt.amount - debt.paidPrincipal).coerceAtLeast(0.0),
            interest = (accruedInterest - debt.paidInterest).coerceAtLeast(0.0),
        )
    }
}
