package app.fynlo.logic

import app.fynlo.data.SyncStatus
import app.fynlo.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class WaiverReviewRegressionTest {
    @Test fun `old large waiver after due cutoff stays a review and never reduces principal`() {
        val loan = Borrower("loan", "Synthetic loan", amount = 2500000.0, rate = 36.0,
            date = "2024-01-01", due = "2024-01-02", intType = "Simple Interest",
            stopInterestAfterDue = true, interestWaived = 2327671.0)
        val snapshot = InterestPolicy.borrowerSnapshot(loan, emptyList(), "2026-10-04")
        assertEquals(4931.50, snapshot.interestBreakdown.accrued, 0.01)
        assertEquals(0.0, snapshot.interestDue, 0.0)
        assertEquals(2500000.0, snapshot.principalOutstanding, 0.0)
        val report = LedgerAccountability.inspect(emptyList(), emptyList(), listOf(loan), emptyList(),
            emptyList(), emptyList(), emptyList(), SyncStatus.Synced, LocalDate.parse("2026-10-04"))
        assertEquals(LedgerIssueSeverity.WARNING, report.issues.single { it.title == "Loan interest waiver exceeds unpaid interest" }.severity)
    }

    @Test fun `fully repaid debt retains historical waiver as review without negative debt or cash credit`() {
        val debt = Debt("debt", "Synthetic lender", amount = 500000.0, rate = 18.0,
            date = "2025-12-01", intType = "Simple Interest", paidPrincipal = 500000.0,
            paidInterest = 45000.0, paid = 545000.0, interestWaived = 15410.0)
        val payment = DebtPayment("p", debt.id, debt.name, "2026-07-01", "Both", 545000.0,
            principal = 500000.0, interest = 45000.0,
            interestAllocationType = InterestPolicy.CURRENT_PERIOD_INTEREST,
            interestPeriodStartDate = debt.date, interestPeriodEndDate = "2026-07-01")
        val snapshot = InterestPolicy.debtSnapshot(debt, listOf(payment), "2026-10-04")
        assertEquals(52520.54, snapshot.interestBreakdown.accrued, 0.01)
        assertEquals(45000.0, snapshot.interestBreakdown.paid, 0.0)
        assertEquals(0.0, snapshot.totalPayable, 0.0)
        assertEquals(snapshot, InterestPolicy.debtSnapshot(debt, listOf(payment), "2026-11-04"))
        val report = LedgerAccountability.inspect(emptyList(), emptyList(), emptyList(), listOf(debt),
            emptyList(), emptyList(), listOf(payment), SyncStatus.Synced, LocalDate.parse("2026-10-04"))
        assertEquals(LedgerIssueSeverity.WARNING, report.issues.single { it.title == "Debt interest waiver exceeds unpaid interest" }.severity)
    }
}
