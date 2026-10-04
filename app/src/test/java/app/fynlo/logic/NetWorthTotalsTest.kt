package app.fynlo.logic

import app.fynlo.data.model.*
import org.junit.Assert.assertEquals
import org.junit.Test

class NetWorthTotalsTest {
    @Test fun `shared totals preserve hand loans written off exclusion and negative cash`() {
        val borrowers = listOf(
            Borrower("interest", "Interest", amount = 1000.0, rate = 36.5, date = "2026-01-01", intType = "Simple Interest"),
            Borrower("hand", "Hand", amount = 500.0, rate = 0.0, date = "2026-01-01", intType = "Simple Interest"),
            Borrower("written", "Written off", amount = 999.0, rate = 36.5, date = "2026-01-01", status = "WrittenOff"),
        )
        val rows = listOf(Payment("p", "hand", "Hand", "2026-01-05", "Principal Only", 100.0, principal = 100.0))
        val totals = NetWorthTotals.calculate(
            listOf(Account("cash", "Cash", "Cash", -100.0)),
            listOf(Investment("inv", "Investment", "Other", invested = 100.0, currentVal = 200.0, date = "2026-01-01")),
            borrowers, listOf(Debt("debt", "Debt", amount = 400.0, rate = 0.0, date = "2026-01-01")),
            rows, emptyList(), "2026-01-10",
        )
        assertEquals(1010.0, totals.totalInterestLoans, 0.0)
        assertEquals(400.0, totals.totalHandLoans, 0.0)
        assertEquals(1410.0, totals.totalReceivables, 0.0)
        assertEquals(1510.0, totals.totalAssets, 0.0)
        assertEquals(400.0, totals.totalDebtPrincipal, 0.0)
        assertEquals(1110.0, totals.netWorth, 0.0)
    }

    @Test fun `empty ledger has genuine zero totals`() {
        val totals = NetWorthTotals.calculate(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), "2026-01-10")
        assertEquals(0.0, totals.totalAssets, 0.0)
        assertEquals(0.0, totals.totalDebtPrincipal + totals.totalDebtInterest, 0.0)
        assertEquals(0.0, totals.netWorth, 0.0)
    }
}
