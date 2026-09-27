package app.fynlo.logic

import app.fynlo.data.model.Borrower
import app.fynlo.data.model.Debt
import app.fynlo.data.model.DebtPayment
import app.fynlo.data.model.Payment
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTrailTest {
    @Test
    fun `borrower money trail ignores principal split before loan start`() {
        val borrower = Borrower(
            id = "rb",
            name = "RB",
            amount = 1_000_000.0,
            rate = 18.0,
            date = "2026-09-05",
            sourceAccount = "Family Cash",
        )
        val oldPayment = Payment(
            id = "old-payment",
            loanId = borrower.id,
            name = borrower.name,
            date = "2026-08-19",
            type = "Both",
            amount = 15_000.0,
            principal = 8_095.90,
            interest = 6_904.10,
        )

        val trail = MoneyTrail.borrower(
            borrower = borrower,
            payments = listOf(oldPayment),
            transactions = emptyList(),
            accountIdToName = emptyMap(),
        )

        assertEquals(0.0, trail.principalCollected, 0.01)
        assertEquals(1_000_000.0, trail.remainingPrincipal, 0.01)
    }

    @Test
    fun `debt money trail ignores principal split before debt start`() {
        val debt = Debt(
            id = "debt",
            name = "Debt",
            amount = 1_000_000.0,
            rate = 18.0,
            date = "2026-09-05",
        )
        val oldPayment = DebtPayment(
            id = "old-payment",
            debtId = debt.id,
            name = debt.name,
            date = "2026-08-19",
            type = "Both",
            amount = 15_000.0,
            principal = 8_095.90,
            interest = 6_904.10,
        )

        val trail = MoneyTrail.debt(
            debt = debt,
            payments = listOf(oldPayment),
            transactions = emptyList(),
            accountIdToName = emptyMap(),
        )

        assertEquals(0.0, trail.principalRepaid, 0.01)
        assertEquals(1_000_000.0, trail.remainingPrincipal, 0.01)
    }
}
