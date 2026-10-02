package app.fynlo.logic

import app.fynlo.data.model.Borrower
import app.fynlo.data.model.Debt
import app.fynlo.data.model.Payment
import app.fynlo.data.model.DebtPayment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InterestStartDayRegressionTest {
    private val start = "2026-10-02"
    private val loan = Borrower(id = "start-day", name = "Test", amount = 1_000_000.0,
        rate = 18.0, date = start, intType = "Simple Interest")
    private val debt = Debt(id = "start-day", name = "Test", amount = loan.amount,
        rate = loan.rate, date = start, intType = loan.intType)
    private val dailyInterest = loan.amount * loan.rate / 100.0 / 365.0

    @Test fun borrowerStartingDayBreakdownMatchesDue() {
        val result = InterestPolicy.borrowerSnapshot(loan, emptyList(), start)
        assertEquals(dailyInterest, result.interestBreakdown.accrued, 0.01)
        assertEquals(result.interestDue, result.interestBreakdown.due, 0.01)
        assertEquals(loan.amount, result.principalOutstanding, 0.0)
        assertEquals(loan.amount + result.interestDue, result.totalReceivable, 0.0)
    }

    @Test fun debtStartingDayBreakdownMatchesDue() {
        val result = InterestPolicy.debtSnapshot(debt, emptyList(), start)
        assertEquals(dailyInterest, result.interestBreakdown.accrued, 0.01)
        assertEquals(result.interestDue, result.interestBreakdown.due, 0.01)
        assertEquals(debt.amount, result.principalOutstanding, 0.0)
        assertEquals(debt.amount + result.interestDue, result.totalPayable, 0.0)
    }

    @Test fun futureStartDoesNotAccrue() {
        assertEquals(0.0, InterestPolicy.borrowerBreakdown(loan, emptyList(), "2026-10-01").accrued, 0.0)
        assertEquals(0.0, InterestPolicy.debtBreakdown(debt, emptyList(), "2026-10-01").accrued, 0.0)
    }

    @Test fun sameDayPrincipalReturnStillCountsThatDayAndThenStops() {
        val payment = Payment(id = "principal", loanId = loan.id, name = loan.name,
            date = start, type = "Principal Only", amount = loan.amount, principal = loan.amount,
            interestAllocationType = InterestPolicy.PRINCIPAL_REPAYMENT)
        val debtPayment = DebtPayment(id = payment.id, debtId = debt.id, name = debt.name,
            date = start, type = payment.type, amount = debt.amount, principal = debt.amount,
            interestAllocationType = InterestPolicy.PRINCIPAL_REPAYMENT)
        for (day in listOf(start, "2026-10-03", "2026-11-01")) {
            assertEquals(dailyInterest, InterestPolicy.borrowerBreakdown(loan, listOf(payment), day).accrued, 0.01)
            assertEquals(dailyInterest, InterestPolicy.debtBreakdown(debt, listOf(debtPayment), day).accrued, 0.01)
        }
        assertEquals(loan.amount, payment.principal, 0.0)
        assertEquals(debt.amount, debtPayment.principal, 0.0)
    }

    @Test fun sameDayAdvanceDoesNotSuppressAccrualOrMutateRows() {
        val payment = Payment(id = "advance", loanId = loan.id, name = loan.name,
            date = start, type = "Interest Only", amount = 2_000.0, interest = 2_000.0,
            interestAllocationType = InterestPolicy.ADVANCE_INTEREST, interestPeriodStartDate = start)
        val debtPayment = DebtPayment(id = payment.id, debtId = debt.id, name = debt.name,
            date = start, type = payment.type, amount = payment.amount, interest = payment.interest,
            interestAllocationType = payment.interestAllocationType, interestPeriodStartDate = start)
        val borrowerFirst = InterestPolicy.borrowerBreakdown(loan, listOf(payment), start)
        val borrowerNext = InterestPolicy.borrowerBreakdown(loan, listOf(payment), "2026-10-03")
        val debtFirst = InterestPolicy.debtBreakdown(debt, listOf(debtPayment), start)
        val debtNext = InterestPolicy.debtBreakdown(debt, listOf(debtPayment), "2026-10-03")
        for ((first, next) in listOf(borrowerFirst to borrowerNext, debtFirst to debtNext)) {
            assertEquals(dailyInterest, first.accrued, 0.01)
            assertEquals(0.0, first.due, 0.0)
            assertTrue(next.accrued > first.accrued)
            assertTrue(next.paidAhead < first.paidAhead)
        }
        assertEquals(0.0, payment.principal, 0.0)
        assertEquals(0.0, debtPayment.principal, 0.0)
        assertEquals(borrowerFirst, InterestPolicy.borrowerBreakdown(loan, listOf(payment), start))
        assertEquals(debtFirst, InterestPolicy.debtBreakdown(debt, listOf(debtPayment), start))
    }

    @Test fun sameDayPartialPrincipalReturnKeepsFinalDayOnReturnedPortion() {
        val payment = Payment(id = "partial", loanId = loan.id, name = loan.name,
            date = start, type = "Principal Only", amount = 400_000.0, principal = 400_000.0,
            interestAllocationType = InterestPolicy.PRINCIPAL_REPAYMENT)
        val debtPayment = DebtPayment(id = payment.id, debtId = debt.id, name = debt.name,
            date = start, type = payment.type, amount = payment.amount, principal = payment.principal,
            interestAllocationType = InterestPolicy.PRINCIPAL_REPAYMENT)
        val expectedNextDay = dailyInterest + dailyInterest * 0.6
        assertEquals(expectedNextDay, InterestPolicy.borrowerBreakdown(loan, listOf(payment), "2026-10-03").accrued, 0.01)
        assertEquals(expectedNextDay, InterestPolicy.debtBreakdown(debt, listOf(debtPayment), "2026-10-03").accrued, 0.01)
        assertEquals(600_000.0, InterestPolicy.borrowerPrincipalOutstanding(loan, listOf(payment)), 0.0)
        assertEquals(600_000.0, InterestPolicy.debtPrincipalOutstanding(debt, listOf(debtPayment)), 0.0)
    }
}
