package app.fynlo.logic

import app.fynlo.data.model.Borrower
import app.fynlo.data.model.Debt
import app.fynlo.data.model.DebtPayment
import app.fynlo.data.model.Payment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentPurposeRegressionTest {
    private val borrower = Borrower(id = "loan", name = "Test", amount = 1_400_000.0,
        rate = 24.0, date = "2026-09-01", intType = "Simple Interest")
    private val debt = Debt(id = "debt", name = "Test", amount = borrower.amount,
        rate = borrower.rate, date = borrower.date, intType = borrower.intType)

    private fun interest(id: String, date: String, amount: Double, start: String, end: String) = Payment(
        id = id, loanId = borrower.id, name = borrower.name, date = date,
        type = "Interest Only", amount = amount, interest = amount,
        interestAllocationType = InterestPolicy.OLD_PERIOD_INTEREST,
        interestPeriodStartDate = start, interestPeriodEndDate = end,
    )

    private fun Payment.asDebt() = DebtPayment(id = id, debtId = debt.id, name = name,
        date = date, type = type, amount = amount, principal = principal, interest = interest,
        interestAllocationType = interestAllocationType, interestPeriodStartDate = interestPeriodStartDate,
        interestPeriodEndDate = interestPeriodEndDate)

    @Test fun `new September interest cannot rewrite old interest as principal`() {
        val rows = listOf(
            interest("june", "2026-06-30", 137_162.0, "", "2026-06-30"),
            interest("july", "2026-08-03", 30_378.0, "2026-07-01", "2026-08-03"),
            interest("august", "2026-09-03", 30_378.0, "2026-08-01", "2026-08-31"),
            interest("september", "2026-10-04", 29_000.0, "2026-09-01", "2026-09-30"),
        )
        val saved = InterestPolicy.resplitBorrowerPayments(borrower, rows)
        assertEquals(rows, saved)
        assertEquals(saved, InterestPolicy.resplitBorrowerPayments(borrower, saved))
        assertEquals(rows.sumOf { it.amount }, saved.sumOf { it.amount }, 0.0)
        val snapshot = InterestPolicy.borrowerSnapshot(borrower, saved, "2026-10-04")
        assertEquals(1_400_000.0, snapshot.principalOutstanding, 0.0)
        assertEquals(3_682.19, snapshot.interestDue, 0.01)
        assertEquals(snapshot.principalOutstanding + snapshot.interestDue, snapshot.totalReceivable, 0.0)

        val debtRows = rows.map { it.asDebt() }
        assertEquals(debtRows, InterestPolicy.resplitDebtPayments(debt, debtRows))
        val debtSnapshot = InterestPolicy.debtSnapshot(debt, debtRows, "2026-10-04")
        assertEquals(snapshot.principalOutstanding, debtSnapshot.principalOutstanding, 0.0)
        assertEquals(snapshot.interestDue, debtSnapshot.interestDue, 0.0)
    }

    @Test fun `advance interest never reduces principal on posting or replay`() {
        val row = interest("advance", "2026-09-01", 29_000.0, "2026-09-01", "")
            .copy(interestAllocationType = InterestPolicy.ADVANCE_INTEREST)
        assertEquals(row, InterestPolicy.alignBorrowerPaymentToPaisePreview(borrower, row))
        assertEquals(row.asDebt(), InterestPolicy.alignDebtPaymentToPaisePreview(debt, row.asDebt()))
        val now = InterestPolicy.paiseBalancesForBorrower(borrower, "2026-09-02", listOf(row))
        val later = InterestPolicy.paiseBalancesForBorrower(borrower, "2026-10-10", listOf(row))
        assertEquals(140_000_000L, now.outstandingPrincipal)
        assertEquals(0L, now.interestDue)
        assertEquals(140_000_000L, later.outstandingPrincipal)
        assertEquals(782_191L, later.interestDue)
        assertEquals(later, InterestPolicy.paiseBalancesForDebt(debt, "2026-10-10", listOf(row.asDebt())))
        assertTrue(InterestPolicy.paiseBalancesForBorrower(borrower, "2026-10-11", listOf(row)).interestDue > later.interestDue)
    }

    @Test fun `explicit principal payment is not reclassified as interest`() {
        val row = Payment(id = "principal", loanId = borrower.id, name = "Test", date = "2026-09-10",
            type = "Principal Only", amount = 200_000.0, principal = 200_000.0,
            interestAllocationType = InterestPolicy.PRINCIPAL_REPAYMENT)
        assertEquals(row, InterestPolicy.alignBorrowerPaymentToPaisePreview(borrower, row))
        val balance = InterestPolicy.paiseBalancesForBorrower(borrower, "2026-09-10", listOf(row))
        assertEquals(120_000_000L, balance.outstandingPrincipal)
        assertEquals(920_547L, balance.interestDue)
    }
}
