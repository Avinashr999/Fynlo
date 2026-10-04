package app.fynlo.logic

import app.fynlo.data.model.Borrower
import app.fynlo.data.model.Debt
import app.fynlo.data.model.Payment
import app.fynlo.data.model.DebtPayment
import org.junit.Assert.assertEquals
import org.junit.Test

// Deliberately failing review reproductions; see README.md before running.
class ReviewDiagnosticTest {
    private val loan = Borrower(id = "review", name = "Synthetic", amount = 100000.0,
        rate = 18.0, date = "2026-01-01", intType = "Simple Interest")
    private val debt = Debt(id = "review", name = "Synthetic", amount = loan.amount,
        rate = loan.rate, date = loan.date, intType = loan.intType)
    private fun row(date: String, principal: Double, interest: Double) = Payment(
        id = "payment", loanId = loan.id, name = loan.name, date = date, type = "Both",
        amount = principal + interest, principal = principal, interest = interest,
        interestAllocationType = InterestPolicy.CURRENT_PERIOD_INTEREST,
        interestPeriodStartDate = loan.date)
    private fun Payment.asDebt() = DebtPayment(id = id, debtId = debt.id, name = name,
        date = date, type = type, amount = amount, principal = principal, interest = interest,
        interestAllocationType = interestAllocationType, interestPeriodStartDate = interestPeriodStartDate)

    @Test fun `saved zero principal in Both must not become principal`() {
        val payment = row("2026-01-10", 0.0, 100.0)
        assertEquals(0.0, InterestPolicy.borrowerPrincipalAmount(payment), 0.0)
    }
    @Test fun `saved zero principal in Both debt must not become principal`() {
        assertEquals(0.0, InterestPolicy.debtPrincipalAmount(row("2026-01-10", 0.0, 100.0).asDebt()), 0.0)
    }
    @Test fun `future principal must not reduce January snapshot`() {
        val payment = row("2026-02-01", 10000.0, 0.0)
        assertEquals(100000.0, InterestPolicy.borrowerSnapshot(loan, listOf(payment), "2026-01-15").principalOutstanding, 0.0)
    }
    @Test fun `future principal must not reduce January debt snapshot`() {
        assertEquals(100000.0, InterestPolicy.debtSnapshot(debt, listOf(row("2026-02-01", 10000.0, 0.0).asDebt()), "2026-01-15").principalOutstanding, 0.0)
    }
    @Test fun `stop after due must apply to amount payable too`() {
        val capped = loan.copy(due = "2026-01-31", stopInterestAfterDue = true)
        assertEquals(1528.76, InterestPolicy.borrowerSnapshot(capped, emptyList(), "2026-02-10").interestDue, 0.01)
    }
    @Test fun `stop after due must apply to debt payable too`() {
        val capped = debt.copy(due = "2026-01-31", stopInterestAfterDue = true)
        assertEquals(1528.76, InterestPolicy.debtSnapshot(capped, emptyList(), "2026-02-10").interestDue, 0.01)
    }
    @Test fun `full principal payment must stop simple interest from next day`() {
        val payment = row("2026-01-10", 100000.0, 0.0).copy(type = "Principal Only")
        assertEquals(493.15, InterestPolicy.borrowerSnapshot(loan, listOf(payment), "2026-01-20").interestDue, 0.01)
    }
    @Test fun `full debt principal payment must stop simple interest from next day`() {
        val payment = row("2026-01-10", 100000.0, 0.0).copy(type = "Principal Only").asDebt()
        assertEquals(493.15, InterestPolicy.debtSnapshot(debt, listOf(payment), "2026-01-20").interestDue, 0.01)
    }
    @Test fun `frozen interest must agree with snapshot`() {
        val frozen = loan.copy(status = "Defaulted", frozenInterest = 100.0)
        assertEquals(100.0, InterestPolicy.borrowerSnapshot(frozen, emptyList(), "2026-01-20").interestDue, 0.01)
    }
}
