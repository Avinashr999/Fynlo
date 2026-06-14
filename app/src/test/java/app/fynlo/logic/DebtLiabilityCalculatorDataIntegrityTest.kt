package app.fynlo.logic

import app.fynlo.data.model.Debt
import org.junit.Assert.assertEquals
import org.junit.Test

class DebtLiabilityCalculatorDataIntegrityTest {

    @Test
    fun `both payment reduces principal and paid interest separately`() {
        val debt = Debt(
            id = "debt-1",
            name = "Personal Loan",
            amount = 100_000.0,
            rate = 12.0,
            date = "2026-01-01",
            intType = "Simple Interest",
            paid = 15_774.0,
            paidPrincipal = 12_000.0,
            paidInterest = 3_774.0,
        )

        val liability = DebtLiabilityCalculator.outstanding(debt, asOf = "2026-07-01")

        assertEquals(88_000.0, liability.principal, 0.01)
        assertEquals(2_177.0, liability.interest, 0.01)
        assertEquals(90_177.0, liability.total, 0.01)
    }

    @Test
    fun `net worth stays stable when paying already accrued debt liability`() {
        val beforeCash = 347_003.0
        val beforeDebt = Debt(
            id = "debt-1",
            name = "Personal Loan",
            amount = 100_000.0,
            rate = 12.0,
            date = "2026-01-01",
            intType = "Simple Interest",
        )
        val afterDebt = beforeDebt.copy(
            paid = 15_774.0,
            paidPrincipal = 12_000.0,
            paidInterest = 3_774.0,
        )

        val beforeLiability = DebtLiabilityCalculator.outstanding(beforeDebt, asOf = "2026-07-01").total
        val afterLiability = DebtLiabilityCalculator.outstanding(afterDebt, asOf = "2026-07-01").total
        val beforeNetWorth = beforeCash - beforeLiability
        val afterNetWorth = (beforeCash - 15_774.0) - afterLiability

        assertEquals(15_774.0, beforeLiability - afterLiability, 0.01)
        assertEquals(0.0, afterNetWorth - beforeNetWorth, 0.01)
    }
}
