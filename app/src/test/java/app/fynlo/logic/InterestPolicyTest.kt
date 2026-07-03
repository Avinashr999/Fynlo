package app.fynlo.logic

import app.fynlo.data.model.Borrower
import app.fynlo.data.model.Debt
import app.fynlo.data.model.DebtPayment
import app.fynlo.data.model.Payment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InterestPolicyTest {
    @Test
    fun `borrower can stop interest after due date`() {
        val borrower = borrower(stopInterestAfterDue = true)

        val stopped = InterestPolicy.accruedForBorrower(borrower, asOf = "2026-01-01")
        val dueOnly = InterestEngine.calcIntAccrued(
            amount = borrower.amount,
            rate = borrower.rate,
            loanDate = borrower.date,
            intType = borrower.intType,
            dueDate = borrower.due,
            totalPaid = borrower.paidPrincipal,
            asOf = borrower.due,
        )
        val running = InterestEngine.calcIntAccrued(
            amount = borrower.amount,
            rate = borrower.rate,
            loanDate = borrower.date,
            intType = borrower.intType,
            dueDate = borrower.due,
            totalPaid = borrower.paidPrincipal,
            asOf = "2026-01-01",
        )

        assertEquals(dueOnly, stopped, 0.01)
        assertTrue(running > stopped)
    }

    @Test
    fun `borrower continues interest after due date when toggle is off`() {
        val borrower = borrower(stopInterestAfterDue = false)

        val policy = InterestPolicy.accruedForBorrower(borrower, asOf = "2026-01-01")
        val raw = InterestEngine.calcIntAccrued(
            amount = borrower.amount,
            rate = borrower.rate,
            loanDate = borrower.date,
            intType = borrower.intType,
            dueDate = borrower.due,
            totalPaid = borrower.paidPrincipal,
            asOf = "2026-01-01",
        )

        assertEquals(raw, policy, 0.01)
    }

    @Test
    fun `one lakh at eighteen percent for exact one year is eighteen thousand`() {
        val borrower = Borrower(
            id = "exact-year",
            name = "Exact Year Borrower",
            amount = 100_000.0,
            rate = 18.0,
            date = "2025-06-28",
            due = "2026-06-28",
            intType = "Simple Interest",
            stopInterestAfterDue = true,
        )

        assertEquals(365, InterestEngine.daysBetween("2025-06-28", "2026-06-28"))
        assertEquals(18_000.0, InterestPolicy.accruedForBorrower(borrower, asOf = "2026-07-01"), 0.01)
    }

    @Test
    fun `debt can stop interest after due date`() {
        val debt = debt(stopInterestAfterDue = true)

        val stopped = InterestPolicy.accruedForDebt(debt, asOf = "2026-01-01")
        val dueOnly = InterestEngine.calcIntAccrued(
            amount = debt.amount,
            rate = debt.rate,
            loanDate = debt.date,
            intType = debt.intType,
            dueDate = debt.due,
            totalPaid = debt.paidPrincipal,
            asOf = debt.due,
        )
        val running = InterestEngine.calcIntAccrued(
            amount = debt.amount,
            rate = debt.rate,
            loanDate = debt.date,
            intType = debt.intType,
            dueDate = debt.due,
            totalPaid = debt.paidPrincipal,
            asOf = "2026-01-01",
        )

        assertEquals(dueOnly, stopped, 0.01)
        assertTrue(running > stopped)
    }

    @Test
    fun `borrower paid ahead does not freeze accrual`() {
        val borrower = Borrower(
            id = "paid-ahead-loan",
            name = "Paid Ahead Borrower",
            amount = 100_000.0,
            rate = 18.0,
            date = "2026-02-01",
            intType = "Simple Interest",
            paid = 30_000.0,
            paidPrincipal = 0.0,
            paidInterest = 30_000.0,
        )
        val payments = listOf(
            Payment(
                id = "advance",
                loanId = borrower.id,
                name = borrower.name,
                date = "2026-02-01",
                type = "Interest Only",
                amount = 30_000.0,
                interest = 30_000.0,
                interestAllocationType = InterestPolicy.ADVANCE_INTEREST,
                interestPeriodStartDate = borrower.date,
            )
        )

        val early = InterestPolicy.borrowerBreakdown(borrower, payments, asOf = "2026-06-01")
        val later = InterestPolicy.borrowerBreakdown(borrower, payments, asOf = "2026-07-01")

        assertEquals(0.0, early.due, 0.01)
        assertTrue(early.paidAhead > 0.0)
        assertTrue(later.accrued > early.accrued)
        assertTrue(later.paidAhead < early.paidAhead)
    }

    @Test
    fun `borrower date edit does not keep old advance interest in new period`() {
        val editedBorrower = Borrower(
            id = "lakshmi-date-edit",
            name = "Lakshmi Devi",
            amount = 15_000.0,
            rate = 12.0,
            date = "2026-07-01",
            intType = "Simple Interest",
            paid = 64.0,
            paidPrincipal = 0.0,
            paidInterest = 64.0,
        )
        val oldAdvanceInterest = Payment(
            id = "old-advance",
            loanId = editedBorrower.id,
            name = editedBorrower.name,
            date = "2026-06-20",
            type = "Interest Only",
            amount = 64.0,
            interest = 64.0,
            interestAllocationType = InterestPolicy.ADVANCE_INTEREST,
            interestPeriodStartDate = "2026-06-01",
        )

        val breakdown = InterestPolicy.borrowerBreakdown(
            editedBorrower,
            listOf(oldAdvanceInterest),
            asOf = "2026-07-03",
        )

        assertEquals(0.0, breakdown.paid, 0.01)
        assertEquals(0.0, breakdown.paidAhead, 0.01)
        assertEquals(64.0, breakdown.unclearInterest, 0.01)
    }

    @Test
    fun `debt paid ahead does not freeze accrual`() {
        val debt = Debt(
            id = "paid-ahead-debt",
            name = "Paid Ahead Debt",
            amount = 100_000.0,
            rate = 18.0,
            date = "2026-02-01",
            intType = "Simple Interest",
            paid = 30_000.0,
            paidPrincipal = 0.0,
            paidInterest = 30_000.0,
        )
        val payments = listOf(
            DebtPayment(
                id = "advance",
                debtId = debt.id,
                name = debt.name,
                date = "2026-02-01",
                type = "Interest Only",
                amount = 30_000.0,
                interest = 30_000.0,
                interestAllocationType = InterestPolicy.ADVANCE_INTEREST,
                interestPeriodStartDate = debt.date,
            )
        )

        val early = InterestPolicy.debtBreakdown(debt, payments, asOf = "2026-06-01")
        val later = InterestPolicy.debtBreakdown(debt, payments, asOf = "2026-07-01")

        assertEquals(0.0, early.due, 0.01)
        assertTrue(early.paidAhead > 0.0)
        assertTrue(later.accrued > early.accrued)
        assertTrue(later.paidAhead < early.paidAhead)
    }

    @Test
    fun `debt date edit does not keep old advance interest in new period`() {
        val editedDebt = Debt(
            id = "debt-date-edit",
            name = "Edited Debt",
            amount = 15_000.0,
            rate = 12.0,
            date = "2026-07-01",
            intType = "Simple Interest",
            paid = 64.0,
            paidPrincipal = 0.0,
            paidInterest = 64.0,
        )
        val oldAdvanceInterest = DebtPayment(
            id = "old-debt-advance",
            debtId = editedDebt.id,
            name = editedDebt.name,
            date = "2026-06-20",
            type = "Interest Only",
            amount = 64.0,
            interest = 64.0,
            interestAllocationType = InterestPolicy.ADVANCE_INTEREST,
            interestPeriodStartDate = "2026-06-01",
        )

        val breakdown = InterestPolicy.debtBreakdown(
            editedDebt,
            listOf(oldAdvanceInterest),
            asOf = "2026-07-03",
        )

        assertEquals(0.0, breakdown.paid, 0.01)
        assertEquals(0.0, breakdown.paidAhead, 0.01)
        assertEquals(64.0, breakdown.unclearInterest, 0.01)
    }

    @Test
    fun `paid ahead example separates accrued paid due and advance`() {
        val borrower = Borrower(
            id = "example",
            name = "Example",
            amount = 100_000.0,
            rate = 73.0,
            date = "2026-02-01",
            intType = "Simple Interest",
            paid = 30_000.0,
            paidInterest = 30_000.0,
        )
        val payments = listOf(
            Payment(
                id = "current",
                loanId = borrower.id,
                name = borrower.name,
                date = "2026-05-12",
                type = "Interest Only",
                amount = 30_000.0,
                interest = 30_000.0,
                interestAllocationType = InterestPolicy.ADVANCE_INTEREST,
                interestPeriodStartDate = borrower.date,
            )
        )

        val breakdown = InterestPolicy.borrowerBreakdown(borrower, payments, asOf = "2026-05-12")

        assertEquals(20_000.0, breakdown.accrued, 0.01)
        assertEquals(30_000.0, breakdown.paid, 0.01)
        assertEquals(0.0, breakdown.due, 0.01)
        assertEquals(10_000.0, breakdown.paidAhead, 0.01)
    }

    @Test
    fun `blank due date never clamps interest`() {
        val borrower = borrower(stopInterestAfterDue = true).copy(due = "")

        val policy = InterestPolicy.accruedForBorrower(borrower, asOf = "2026-01-01")
        val raw = InterestEngine.calcIntAccrued(
            amount = borrower.amount,
            rate = borrower.rate,
            loanDate = borrower.date,
            intType = borrower.intType,
            dueDate = borrower.due,
            totalPaid = borrower.paidPrincipal,
            asOf = "2026-01-01",
        )

        assertEquals(raw, policy, 0.01)
    }

    @Test
    fun `old-period borrower interest does not reduce current-period due`() {
        val borrower = Borrower(
            id = "samanvi-125500",
            name = "Samanvi Travels",
            amount = 125_500.0,
            rate = 18.0,
            date = "2026-04-22",
            intType = "Simple Interest",
            paid = 39_492.0,
            paidInterest = 39_492.0,
        )
        val oldInterest = Payment(
            id = "old-interest",
            loanId = borrower.id,
            name = borrower.name,
            date = "2026-04-22",
            type = "Interest Only",
            amount = 39_492.0,
            interest = 39_492.0,
            interestAllocationType = InterestPolicy.OLD_PERIOD_INTEREST,
            interestPeriodStartDate = "2025-04-22",
            interestPeriodEndDate = "2026-04-22",
        )

        val breakdown = InterestPolicy.borrowerBreakdown(borrower, listOf(oldInterest), asOf = "2026-07-01")

        assertTrue(breakdown.accrued > 0.0)
        assertEquals(breakdown.accrued, breakdown.due, 0.01)
        assertEquals(0.0, breakdown.paid, 0.01)
        assertEquals(0.0, breakdown.paidAhead, 0.01)
        assertEquals(39_492.0, breakdown.oldPeriodInterest, 0.01)
    }

    @Test
    fun `current-period borrower interest reduces current due`() {
        val borrower = Borrower(
            id = "current-period",
            name = "Current Period",
            amount = 100_000.0,
            rate = 18.0,
            date = "2026-02-01",
            intType = "Simple Interest",
            paid = 5_000.0,
            paidInterest = 5_000.0,
        )
        val currentInterest = Payment(
            id = "current-interest",
            loanId = borrower.id,
            name = borrower.name,
            date = "2026-05-12",
            type = "Interest Only",
            amount = 5_000.0,
            interest = 5_000.0,
            interestAllocationType = InterestPolicy.CURRENT_PERIOD_INTEREST,
            interestPeriodStartDate = borrower.date,
            interestPeriodEndDate = "2026-05-12",
        )

        val breakdown = InterestPolicy.borrowerBreakdown(borrower, listOf(currentInterest), asOf = "2026-05-12")

        assertEquals(5_000.0, breakdown.paid, 0.01)
        assertEquals((breakdown.accrued - 5_000.0).coerceAtLeast(0.0), breakdown.due, 0.01)
    }

    @Test
    fun `unknown borrower interest needs review and does not reduce current due`() {
        val borrower = Borrower(
            id = "unknown-period",
            name = "Unknown Period",
            amount = 100_000.0,
            rate = 18.0,
            date = "2026-02-01",
            intType = "Simple Interest",
            paid = 10_000.0,
            paidInterest = 10_000.0,
        )
        val unclear = Payment(
            id = "unknown",
            loanId = borrower.id,
            name = borrower.name,
            date = "2026-04-01",
            type = "Interest Only",
            amount = 10_000.0,
            interest = 10_000.0,
        )

        val breakdown = InterestPolicy.borrowerBreakdown(borrower, listOf(unclear), asOf = "2026-05-12")

        assertEquals(0.0, breakdown.paid, 0.01)
        assertEquals(10_000.0, breakdown.unclearInterest, 0.01)
        assertEquals(breakdown.accrued, breakdown.due, 0.01)
    }

    @Test
    fun `review interest does not suppress fresh small borrower accrual after date edit`() {
        val borrower = Borrower(
            id = "lakshmi-devi",
            name = "Lakshmi Devi",
            amount = 500.0,
            rate = 18.0,
            date = "2026-07-01",
            intType = "Simple Interest",
            paid = 64.0,
            paidInterest = 64.0,
        )
        val oldReviewInterest = Payment(
            id = "old-review",
            loanId = borrower.id,
            name = borrower.name,
            date = "2026-06-20",
            type = "Interest Only",
            amount = 64.0,
            interest = 64.0,
            interestAllocationType = InterestPolicy.UNKNOWN_REVIEW,
            interestPeriodStartDate = "",
        )

        val breakdown = InterestPolicy.borrowerBreakdown(borrower, listOf(oldReviewInterest), asOf = "2026-07-03")

        assertTrue("Fresh interest should accrue after the edited loan date", breakdown.accrued > 0.0)
        assertEquals(0.0, breakdown.paid, 0.01)
        assertEquals(0.0, breakdown.paidAhead, 0.01)
        assertEquals(64.0, breakdown.unclearInterest, 0.01)
        assertEquals(breakdown.accrued, breakdown.due, 0.01)
    }

    @Test
    fun `old-period debt interest does not reduce current-period due`() {
        val debt = Debt(
            id = "old-debt",
            name = "Old Debt",
            amount = 125_500.0,
            rate = 18.0,
            date = "2026-04-22",
            intType = "Simple Interest",
            paid = 39_492.0,
            paidInterest = 39_492.0,
        )
        val oldInterest = DebtPayment(
            id = "old-interest",
            debtId = debt.id,
            name = debt.name,
            date = "2026-04-22",
            type = "Interest Only",
            amount = 39_492.0,
            interest = 39_492.0,
            interestAllocationType = InterestPolicy.OLD_PERIOD_INTEREST,
            interestPeriodStartDate = "2025-04-22",
            interestPeriodEndDate = "2026-04-22",
        )

        val breakdown = InterestPolicy.debtBreakdown(debt, listOf(oldInterest), asOf = "2026-07-01")

        assertTrue(breakdown.accrued > 0.0)
        assertEquals(breakdown.accrued, breakdown.due, 0.01)
        assertEquals(0.0, breakdown.paidAhead, 0.01)
        assertEquals(39_492.0, breakdown.oldPeriodInterest, 0.01)
    }

    @Test
    fun `review interest does not suppress fresh small debt accrual after date edit`() {
        val debt = Debt(
            id = "small-debt",
            name = "Small Debt",
            amount = 500.0,
            rate = 18.0,
            date = "2026-07-01",
            intType = "Simple Interest",
            paid = 64.0,
            paidInterest = 64.0,
        )
        val oldReviewInterest = DebtPayment(
            id = "old-review",
            debtId = debt.id,
            name = debt.name,
            date = "2026-06-20",
            type = "Interest Only",
            amount = 64.0,
            interest = 64.0,
            interestAllocationType = InterestPolicy.UNKNOWN_REVIEW,
            interestPeriodStartDate = "",
        )

        val breakdown = InterestPolicy.debtBreakdown(debt, listOf(oldReviewInterest), asOf = "2026-07-03")

        assertTrue("Fresh debt interest should accrue after the edited debt date", breakdown.accrued > 0.0)
        assertEquals(0.0, breakdown.paid, 0.01)
        assertEquals(0.0, breakdown.paidAhead, 0.01)
        assertEquals(64.0, breakdown.unclearInterest, 0.01)
        assertEquals(breakdown.accrued, breakdown.due, 0.01)
    }

    private fun borrower(stopInterestAfterDue: Boolean) = Borrower(
        id = "loan-1",
        name = "Test Borrower",
        amount = 100_000.0,
        rate = 12.0,
        date = "2025-01-01",
        due = "2025-06-01",
        stopInterestAfterDue = stopInterestAfterDue,
    )

    private fun debt(stopInterestAfterDue: Boolean) = Debt(
        id = "debt-1",
        name = "Test Debt",
        amount = 100_000.0,
        rate = 12.0,
        date = "2025-01-01",
        due = "2025-06-01",
        stopInterestAfterDue = stopInterestAfterDue,
    )
}
