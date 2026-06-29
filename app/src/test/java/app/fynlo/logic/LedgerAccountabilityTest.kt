package app.fynlo.logic

import app.fynlo.data.SyncStatus
import app.fynlo.data.model.Borrower
import app.fynlo.data.model.Debt
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LedgerAccountabilityTest {

    @Test
    fun `ledger health warns when loan waiver exceeds unpaid interest`() {
        val borrower = Borrower(
            id = "loan-over-waived",
            name = "Over Waived Loan",
            amount = 36500.0,
            rate = 10.0,
            date = "2026-06-29",
            intType = "Simple Interest",
            interestWaived = 500.0,
        )

        val report = LedgerAccountability.inspect(
            accounts = emptyList(),
            transactions = emptyList(),
            borrowers = listOf(borrower),
            debts = emptyList(),
            investments = emptyList(),
            payments = emptyList(),
            debtPayments = emptyList(),
            syncStatus = SyncStatus.Synced,
            today = LocalDate.parse("2026-06-29"),
        )

        assertTrue(report.issues.any { it.title == "Loan interest waiver exceeds unpaid interest" })
    }

    @Test
    fun `ledger health warns when debt waiver exceeds unpaid interest`() {
        val debt = Debt(
            id = "debt-over-waived",
            name = "Over Waived Debt",
            amount = 36500.0,
            rate = 10.0,
            date = "2026-06-29",
            intType = "Simple Interest",
            interestWaived = 500.0,
        )

        val report = LedgerAccountability.inspect(
            accounts = emptyList(),
            transactions = emptyList(),
            borrowers = emptyList(),
            debts = listOf(debt),
            investments = emptyList(),
            payments = emptyList(),
            debtPayments = emptyList(),
            syncStatus = SyncStatus.Synced,
            today = LocalDate.parse("2026-06-29"),
        )

        assertTrue(report.issues.any { it.title == "Debt interest waiver exceeds unpaid interest" })
    }
}
