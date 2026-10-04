package app.fynlo.logic

import app.fynlo.data.model.Borrower
import app.fynlo.data.model.Debt
import app.fynlo.data.model.Payment
import app.fynlo.data.model.DebtPayment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentReviewRegressionTest {
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

    @Test fun `partial principal changes next day accrual for both flows`() {
        val payment = row("2026-01-10", 50000.0, 0.0).copy(type = "Principal Only")
        val b = InterestPolicy.borrowerSnapshot(loan, listOf(payment), "2026-01-20")
        val d = InterestPolicy.debtSnapshot(debt, listOf(payment.asDebt()), "2026-01-20")
        assertEquals(50000.0, b.principalOutstanding, 0.0)
        assertEquals(739.72, b.interestDue, 0.01)
        assertEquals(b.interestDue, b.interestBreakdown.accrued, 0.0)
        assertEquals(b.interestDue, d.interestDue, 0.0)
        assertEquals(b.totalReceivable, d.totalPayable, 0.0)
    }

    @Test fun `due cutoff still counts later principal and interest payments`() {
        val bLoan = loan.copy(due = "2026-01-31", stopInterestAfterDue = true)
        val dLoan = debt.copy(due = bLoan.due, stopInterestAfterDue = true)
        val payment = row("2026-02-05", 10000.0, 100.0)
        val b = InterestPolicy.borrowerSnapshot(bLoan, listOf(payment), "2026-02-10")
        val d = InterestPolicy.debtSnapshot(dLoan, listOf(payment.asDebt()), "2026-02-10")
        assertEquals(90000.0, b.principalOutstanding, 0.0)
        assertEquals(1528.76, b.interestBreakdown.accrued, 0.01)
        assertEquals(1428.76, b.interestDue, 0.01)
        assertEquals(b.interestDue, b.interestBreakdown.due, 0.0)
        assertEquals(b.interestDue, d.interestDue, 0.0)
        val preview = InterestPolicy.previewBorrowerPaymentPaise(bLoan, 50000L, "2026-02-10", listOf(payment))
        assertEquals(50000L, preview.towardInterest)
        assertEquals(0L, preview.towardPrincipal)
        assertEquals(9142876L, InterestPolicy.paiseBalancesForDebt(dLoan, "2026-02-10", listOf(payment.asDebt())).outstanding)
        assertTrue(InterestPolicy.borrowerSnapshot(bLoan.copy(stopInterestAfterDue = false), listOf(payment), "2026-02-10").interestDue > b.interestDue)
    }

    @Test fun `future interest does not settle earlier periods or use stale cached totals`() {
        val future = row("2026-02-03", 0.0, 1500.0).copy(type = "Interest Only",
            interestAllocationType = InterestPolicy.OLD_PERIOD_INTEREST,
            interestPeriodStartDate = "2026-01-01", interestPeriodEndDate = "2026-01-31")
        val cached = loan.copy(paid = 1500.0, paidPrincipal = 1500.0, paidInterest = 1500.0)
        val b = InterestPolicy.borrowerSnapshot(cached, listOf(future), "2026-01-20")
        assertEquals(100000.0, b.principalOutstanding, 0.0)
        assertEquals(986.30, b.interestDue, 0.01)
        assertEquals(0.0, b.interestBreakdown.totalInterestPaid, 0.0)
        assertEquals(b, InterestPolicy.borrowerSnapshot(cached, emptyList(), "2026-01-20"))
        val d = InterestPolicy.debtSnapshot(debt.copy(paidInterest = 1500.0), listOf(future.asDebt().copy(
            interestPeriodEndDate = future.interestPeriodEndDate)), "2026-01-20")
        assertEquals(b.interestDue, d.interestDue, 0.0)
    }

    @Test fun `freeze respects saved amount payments waivers and effective date including zero`() {
        val frozen = loan.copy(status = "Defaulted", defaultDate = "2026-01-15", frozenInterest = 200.0, interestWaived = 10.0)
        val before = row("2026-01-10", 0.0, 20.0).copy(type = "Interest Only")
        val after = row("2026-02-01", 0.0, 30.0).copy(id = "later", type = "Interest Only")
        val rows = listOf(before, after)
        val result = InterestPolicy.borrowerSnapshot(frozen, rows, "2026-02-10")
        assertEquals(200.0, result.interestBreakdown.accrued, 0.0)
        assertEquals(50.0, result.interestBreakdown.paid, 0.0)
        assertEquals(140.0, result.interestDue, 0.0)
        assertEquals(14000L, InterestPolicy.paiseBalancesForBorrower(frozen, "2026-02-10", rows).interestDue)
        assertEquals(10000L, InterestPolicy.previewBorrowerPaymentPaise(frozen, 10000L, "2026-02-10", rows).towardInterest)
        assertTrue(InterestPolicy.borrowerSnapshot(frozen, rows, "2026-01-11").interestBreakdown.accrued > 200.0)
        assertEquals(0.0, InterestPolicy.borrowerSnapshot(frozen.copy(frozenInterest = 0.0), emptyList(), "2026-02-10").interestDue, 0.0)
    }

    @Test fun `zero principal saved split and penalty never reduce borrower or debt principal`() {
        val paidInterest = row("2026-01-10", 0.0, 100.0)
        val penalty = paidInterest.copy(id = "penalty", interest = 0.0, penaltyPaise = 10000L)
        assertEquals(0.0, InterestPolicy.borrowerPrincipalAmount(penalty), 0.0)
        assertEquals(0.0, InterestPolicy.debtPrincipalAmount(penalty.asDebt().copy(penaltyPaise = 10000L)), 0.0)
        val b = InterestPolicy.borrowerSnapshot(loan, listOf(paidInterest, penalty), "2026-01-20")
        assertEquals(100000.0, b.principalOutstanding, 0.0)
        assertEquals(886.30, b.interestDue, 0.01)
    }

    @Test fun `same day principal payoff counts the payment day once only`() {
        val payment = row(loan.date, loan.amount, 0.0).copy(type = "Principal Only")
        val paid = InterestPolicy.borrowerSnapshot(loan, listOf(payment), loan.date)
        val later = InterestPolicy.borrowerSnapshot(loan, listOf(payment), "2026-02-01")
        assertEquals(49.31, paid.interestDue, 0.01)
        assertEquals(paid, later)
    }

    @Test fun `future principal is ignored by direct money trail helpers too`() {
        val future = row("2026-02-01", 10000.0, 0.0)
        assertEquals(0.0, InterestPolicy.borrowerPrincipalPaid(loan, listOf(future), "2026-01-15"), 0.0)
        assertEquals(0.0, InterestPolicy.debtPrincipalPaid(debt, listOf(future.asDebt()), "2026-01-15"), 0.0)
        assertEquals(90000.0, InterestPolicy.borrowerPrincipalOutstanding(loan, listOf(future), "2026-02-01"), 0.0)
    }
}
